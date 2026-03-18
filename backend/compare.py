import json
from flask import request, jsonify
from database import get_db_connection
from middleware import token_required
from ai_service import compare_products


def register_compare_routes(app):

    @app.route('/compare', methods=['POST'])
    @token_required
    def compare_scans(current_user_id):
        data = request.get_json()
        if not data:
            return jsonify({"success": False, "message": "No data provided"}), 400

        scan_id_a = data.get('scan_id_a')
        scan_id_b = data.get('scan_id_b')

        if not scan_id_a or not scan_id_b:
            return jsonify({"success": False, "message": "Both scan_id_a and scan_id_b are required"}), 400

        conn = get_db_connection()
        if conn is None:
            return jsonify({"success": False, "message": "Database connection error"}), 500

        cursor = conn.cursor(dictionary=True)
        try:
            # Fetch both scans
            cursor.execute("SELECT * FROM scans WHERE id = %s AND user_id = %s", (scan_id_a, current_user_id))
            scan_a = cursor.fetchone()
            cursor.execute("SELECT * FROM scans WHERE id = %s AND user_id = %s", (scan_id_b, current_user_id))
            scan_b = cursor.fetchone()

            if not scan_a or not scan_b:
                return jsonify({"success": False, "message": "One or both scans not found"}), 404

            # Get user health profile
            cursor.execute("SELECT age_group FROM health_profiles WHERE user_id = %s", (current_user_id,))
            health = cursor.fetchone()
            age_group = health['age_group'] if health else 'Adult'

            cursor.execute("SELECT condition_key FROM user_health_conditions WHERE user_id = %s", (current_user_id,))
            conditions = [r['condition_key'] for r in cursor.fetchall()]

            cursor.execute("SELECT sensitivity FROM user_sensitivities WHERE user_id = %s", (current_user_id,))
            sensitivities = [r['sensitivity'] for r in cursor.fetchall()]

            # AI comparison
            ai_result = compare_products(
                scan_a['product_name'] or 'Product A',
                scan_a['raw_ocr_text'] or '',
                scan_b['product_name'] or 'Product B',
                scan_b['raw_ocr_text'] or '',
                age_group, conditions, sensitivities
            )

            recommendation = ai_result.get('recommendation', 'NEITHER')
            summary = ai_result.get('summary', '')

            # Override recommendation when scores are equal or very close (diff <= 5)
            score_a = scan_a['score'] or 0
            score_b = scan_b['score'] or 0
            if abs(score_a - score_b) <= 5:
                recommendation = 'EQUAL'
                summary = ai_result.get('summary', 'Both products have a similar health impact based on their ingredients.')

            # Save comparison
            cursor.execute(
                """INSERT INTO comparisons (user_id, scan_id_a, scan_id_b, chosen_product, ai_summary)
                   VALUES (%s, %s, %s, %s, %s)""",
                (current_user_id, scan_id_a, scan_id_b,
                 recommendation if recommendation in ('A', 'B') else None,  # EQUAL stored as NULL
                 summary)
            )
            comparison_id = cursor.lastrowid
            conn.commit()

            return jsonify({
                "success": True,
                "comparison": {
                    "id": comparison_id,
                    "product_a": {
                        "id": scan_a['id'],
                        "name": scan_a['product_name'],
                        "brand": scan_a['brand_name'],
                        "score": scan_a['score'],
                        "risk_level": scan_a['risk_level']
                    },
                    "product_b": {
                        "id": scan_b['id'],
                        "name": scan_b['product_name'],
                        "brand": scan_b['brand_name'],
                        "score": scan_b['score'],
                        "risk_level": scan_b['risk_level']
                    },
                    "recommendation": recommendation,
                    "summary": summary,
                    "detailed_comparison": ai_result.get('detailed_comparison', {})
                }
            }), 200

        except Exception as e:
            return jsonify({"success": False, "message": f"Comparison failed: {str(e)}"}), 500
        finally:
            cursor.close()
            conn.close()
