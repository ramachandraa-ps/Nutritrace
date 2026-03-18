import os
import uuid
import json
from flask import request, jsonify, send_from_directory
from database import get_db_connection
from middleware import token_required
from ocr_service import extract_text_from_image
from ai_service import analyze_ingredients, detect_product_name
from config import UPLOAD_FOLDER


def register_scan_routes(app):

    # Ensure uploads directory exists
    os.makedirs(UPLOAD_FOLDER, exist_ok=True)

    @app.route('/uploads/<filename>')
    def serve_upload(filename):
        return send_from_directory(UPLOAD_FOLDER, filename)

    @app.route('/scan/analyze', methods=['POST'])
    @token_required
    def analyze_scan(current_user_id):
        if 'image' not in request.files:
            return jsonify({"success": False, "message": "No image file provided"}), 400

        image = request.files['image']
        if image.filename == '':
            return jsonify({"success": False, "message": "No image selected"}), 400

        # Validate file type
        allowed_extensions = {'jpg', 'jpeg', 'png'}
        ext = image.filename.rsplit('.', 1)[-1].lower() if '.' in image.filename else ''
        if ext not in allowed_extensions:
            return jsonify({"success": False, "message": "Only JPEG and PNG images are allowed"}), 400

        product_name = request.form.get('product_name', 'Unknown Product')
        brand_name = request.form.get('brand_name', '')

        # Save image
        filename = f"{current_user_id}_{uuid.uuid4().hex}.{ext}"
        image_path = os.path.join(UPLOAD_FOLDER, filename)
        image.save(image_path)

        conn = None
        cursor = None
        try:
            # Step 1: OCR
            raw_text = extract_text_from_image(image_path)
            if not raw_text.strip():
                return jsonify({"success": False, "message": "Could not extract text from image. Try a clearer photo."}), 400

            # Step 1.5: Auto-detect product name if not provided
            if not product_name or product_name == 'Unknown Product':
                detected_name, detected_brand = detect_product_name(raw_text)
                if detected_name:
                    product_name = detected_name
                if detected_brand and not brand_name:
                    brand_name = detected_brand

            # Step 2: Get user health profile
            conn = get_db_connection()
            cursor = conn.cursor(dictionary=True)

            cursor.execute("SELECT age_group FROM health_profiles WHERE user_id = %s", (current_user_id,))
            health = cursor.fetchone()
            age_group = health['age_group'] if health else 'Adult'

            cursor.execute("SELECT condition_key FROM user_health_conditions WHERE user_id = %s", (current_user_id,))
            conditions = [r['condition_key'] for r in cursor.fetchall()]

            cursor.execute("SELECT sensitivity FROM user_sensitivities WHERE user_id = %s", (current_user_id,))
            sensitivities = [r['sensitivity'] for r in cursor.fetchall()]

            # Step 3: AI Analysis
            ai_result = analyze_ingredients(raw_text, age_group, conditions, sensitivities)

            score = ai_result.get('overall_score', 50)
            risk_level = ai_result.get('risk_level', 'MODERATE')
            ingredients = ai_result.get('ingredients', [])
            overview = ai_result.get('overview', '')
            guidance = ai_result.get('guidance', [])

            # Step 4: Save to database
            cursor.execute(
                """INSERT INTO scans (user_id, product_name, brand_name, score, risk_level, image_path, raw_ocr_text, ai_analysis)
                   VALUES (%s, %s, %s, %s, %s, %s, %s, %s)""",
                (current_user_id, product_name, brand_name, score, risk_level, filename, raw_text, json.dumps(ai_result))
            )
            scan_id = cursor.lastrowid

            for ing in ingredients:
                cursor.execute(
                    """INSERT INTO scan_ingredients (scan_id, ingredient_name, status, reason)
                       VALUES (%s, %s, %s, %s)""",
                    (scan_id, ing.get('ingredient_name', ''), ing.get('status', 'SAFE'), ing.get('reason', ''))
                )

            conn.commit()

            # Get the actual scanned_at timestamp
            cursor.execute("SELECT scanned_at FROM scans WHERE id = %s", (scan_id,))
            scan_row = cursor.fetchone()
            scanned_at = str(scan_row['scanned_at']) if scan_row else ''

            # Build response
            risk_breakdown = {
                "avoid_count": sum(1 for i in ingredients if i.get('status') == 'AVOID'),
                "caution_count": sum(1 for i in ingredients if i.get('status') == 'CAUTION'),
                "safe_count": sum(1 for i in ingredients if i.get('status') == 'SAFE')
            }

            return jsonify({
                "success": True,
                "scan": {
                    "id": scan_id,
                    "product_name": product_name,
                    "brand_name": brand_name,
                    "score": score,
                    "risk_level": risk_level,
                    "scanned_at": scanned_at,
                    "overview": overview,
                    "sugar_estimate": ai_result.get('sugar_estimate', '--'),
                    "additives_count": ai_result.get('additives_count', 0),
                    "allergens_found": ai_result.get('allergens_found', []),
                    "ingredients": ingredients,
                    "risk_breakdown": risk_breakdown,
                    "guidance": guidance
                }
            }), 200

        except Exception as e:
            import traceback
            traceback.print_exc()
            return jsonify({"success": False, "message": f"Analysis failed: {str(e)}"}), 500
        finally:
            if cursor:
                cursor.close()
            if conn:
                conn.close()

    @app.route('/scan/history', methods=['GET'])
    @token_required
    def scan_history(current_user_id):
        search = request.args.get('search', '').strip()

        conn = get_db_connection()
        if conn is None:
            return jsonify({"success": False, "message": "Database connection error"}), 500

        cursor = conn.cursor(dictionary=True)
        try:
            if search:
                cursor.execute(
                    """SELECT id, product_name, brand_name, score, risk_level, scanned_at, image_path
                       FROM scans WHERE user_id = %s AND (product_name LIKE %s OR brand_name LIKE %s)
                       ORDER BY scanned_at DESC""",
                    (current_user_id, f"%{search}%", f"%{search}%")
                )
            else:
                cursor.execute(
                    """SELECT id, product_name, brand_name, score, risk_level, scanned_at, image_path
                       FROM scans WHERE user_id = %s ORDER BY scanned_at DESC""",
                    (current_user_id,)
                )

            scans = cursor.fetchall()
            for scan in scans:
                scan['scanned_at'] = str(scan['scanned_at'])

            return jsonify({"success": True, "scans": scans}), 200

        except Exception as e:
            return jsonify({"success": False, "message": f"An error occurred: {str(e)}"}), 500
        finally:
            cursor.close()
            conn.close()

    @app.route('/scan/latest', methods=['GET'])
    @token_required
    def scan_latest(current_user_id):
        conn = get_db_connection()
        if conn is None:
            return jsonify({"success": False, "message": "Database connection error"}), 500

        cursor = conn.cursor(dictionary=True)
        try:
            cursor.execute(
                """SELECT id, product_name, brand_name, score, risk_level, scanned_at
                   FROM scans WHERE user_id = %s ORDER BY scanned_at DESC LIMIT 3""",
                (current_user_id,)
            )
            scans = cursor.fetchall()
            for scan in scans:
                scan['scanned_at'] = str(scan['scanned_at'])

            return jsonify({"success": True, "scans": scans}), 200

        except Exception as e:
            return jsonify({"success": False, "message": f"An error occurred: {str(e)}"}), 500
        finally:
            cursor.close()
            conn.close()

    @app.route('/scan/<int:scan_id>', methods=['GET'])
    @token_required
    def get_scan(current_user_id, scan_id):
        conn = get_db_connection()
        if conn is None:
            return jsonify({"success": False, "message": "Database connection error"}), 500

        cursor = conn.cursor(dictionary=True)
        try:
            cursor.execute(
                "SELECT * FROM scans WHERE id = %s AND user_id = %s",
                (scan_id, current_user_id)
            )
            scan = cursor.fetchone()
            if not scan:
                return jsonify({"success": False, "message": "Scan not found"}), 404

            cursor.execute(
                "SELECT ingredient_name, status, reason FROM scan_ingredients WHERE scan_id = %s",
                (scan_id,)
            )
            ingredients = cursor.fetchall()

            # Parse AI analysis for overview and guidance
            ai_analysis = json.loads(scan['ai_analysis']) if scan['ai_analysis'] else {}

            risk_breakdown = {
                "avoid_count": sum(1 for i in ingredients if i['status'] == 'AVOID'),
                "caution_count": sum(1 for i in ingredients if i['status'] == 'CAUTION'),
                "safe_count": sum(1 for i in ingredients if i['status'] == 'SAFE')
            }

            return jsonify({
                "success": True,
                "scan": {
                    "id": scan['id'],
                    "product_name": scan['product_name'],
                    "brand_name": scan['brand_name'],
                    "score": scan['score'],
                    "risk_level": scan['risk_level'],
                    "scanned_at": str(scan['scanned_at']),
                    "image_path": scan['image_path'],
                    "overview": ai_analysis.get('overview', ''),
                    "sugar_estimate": ai_analysis.get('sugar_estimate', '--'),
                    "additives_count": ai_analysis.get('additives_count', 0),
                    "allergens_found": ai_analysis.get('allergens_found', []),
                    "ingredients": ingredients,
                    "risk_breakdown": risk_breakdown,
                    "guidance": ai_analysis.get('guidance', [])
                }
            }), 200

        except Exception as e:
            return jsonify({"success": False, "message": f"An error occurred: {str(e)}"}), 500
        finally:
            cursor.close()
            conn.close()

    @app.route('/scan/<int:scan_id>', methods=['PUT'])
    @token_required
    def update_scan(current_user_id, scan_id):
        data = request.get_json()
        if not data:
            return jsonify({"success": False, "message": "No data provided"}), 400

        conn = get_db_connection()
        if conn is None:
            return jsonify({"success": False, "message": "Database connection error"}), 500

        cursor = conn.cursor()
        try:
            cursor.execute("SELECT id FROM scans WHERE id = %s AND user_id = %s", (scan_id, current_user_id))
            if not cursor.fetchone():
                return jsonify({"success": False, "message": "Scan not found"}), 404

            updates = []
            values = []
            if 'product_name' in data:
                updates.append("product_name = %s")
                values.append(data['product_name'])
            if 'brand_name' in data:
                updates.append("brand_name = %s")
                values.append(data['brand_name'])

            if not updates:
                return jsonify({"success": False, "message": "No fields to update"}), 400

            values.extend([scan_id, current_user_id])
            cursor.execute(
                f"UPDATE scans SET {', '.join(updates)} WHERE id = %s AND user_id = %s",
                tuple(values)
            )
            conn.commit()

            return jsonify({"success": True, "message": "Scan updated successfully"}), 200

        except Exception as e:
            return jsonify({"success": False, "message": f"An error occurred: {str(e)}"}), 500
        finally:
            cursor.close()
            conn.close()

    @app.route('/scan/<int:scan_id>', methods=['DELETE'])
    @token_required
    def delete_scan(current_user_id, scan_id):
        conn = get_db_connection()
        if conn is None:
            return jsonify({"success": False, "message": "Database connection error"}), 500

        cursor = conn.cursor()
        try:
            cursor.execute("SELECT id FROM scans WHERE id = %s AND user_id = %s", (scan_id, current_user_id))
            if not cursor.fetchone():
                return jsonify({"success": False, "message": "Scan not found"}), 404

            cursor.execute("DELETE FROM scans WHERE id = %s AND user_id = %s", (scan_id, current_user_id))
            conn.commit()

            return jsonify({"success": True, "message": "Scan deleted successfully"}), 200

        except Exception as e:
            return jsonify({"success": False, "message": f"An error occurred: {str(e)}"}), 500
        finally:
            cursor.close()
            conn.close()
