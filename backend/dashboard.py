from flask import jsonify
from database import get_db_connection
from middleware import token_required


def register_dashboard_routes(app):

    @app.route('/dashboard', methods=['GET'])
    @token_required
    def get_dashboard(current_user_id):
        conn = get_db_connection()
        if conn is None:
            return jsonify({"success": False, "message": "Database connection error"}), 500

        cursor = conn.cursor(dictionary=True)
        try:
            # User info
            cursor.execute("SELECT fullname FROM users WHERE id = %s", (current_user_id,))
            user = cursor.fetchone()

            cursor.execute("SELECT id FROM health_profiles WHERE user_id = %s", (current_user_id,))
            has_health_profile = cursor.fetchone() is not None

            # Latest 3 scans
            cursor.execute(
                """SELECT id, product_name, brand_name, score, risk_level, scanned_at, image_path
                   FROM scans WHERE user_id = %s ORDER BY scanned_at DESC LIMIT 3""",
                (current_user_id,)
            )
            latest_scans = cursor.fetchall()
            for scan in latest_scans:
                scan['scanned_at'] = str(scan['scanned_at'])

            # Stats
            cursor.execute("SELECT COUNT(*) as total, AVG(score) as avg_score FROM scans WHERE user_id = %s", (current_user_id,))
            stats = cursor.fetchone()

            return jsonify({
                "success": True,
                "user": {
                    "fullname": user['fullname'] if user else '',
                    "has_health_profile": has_health_profile
                },
                "latest_scans": latest_scans,
                "total_scans": stats['total'] or 0,
                "average_score": int(stats['avg_score']) if stats['avg_score'] else 0
            }), 200

        except Exception as e:
            return jsonify({"success": False, "message": f"An error occurred: {str(e)}"}), 500
        finally:
            cursor.close()
            conn.close()
