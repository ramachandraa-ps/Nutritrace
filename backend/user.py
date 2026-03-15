from flask import request, jsonify
from flask_bcrypt import Bcrypt
from database import get_db_connection
from middleware import token_required
from validators import is_valid_email, is_valid_phone, is_valid_fullname

bcrypt = Bcrypt()


def init_user_bcrypt(app):
    bcrypt.init_app(app)


def register_user_routes(app):

    @app.route('/user/profile', methods=['GET'])
    @token_required
    def get_profile(current_user_id):
        conn = get_db_connection()
        if conn is None:
            return jsonify({"success": False, "message": "Database connection error"}), 500

        cursor = conn.cursor(dictionary=True)
        try:
            cursor.execute("SELECT id, fullname, phone, email FROM users WHERE id = %s", (current_user_id,))
            user = cursor.fetchone()
            if not user:
                return jsonify({"success": False, "message": "User not found"}), 404

            cursor.execute("SELECT age_group FROM health_profiles WHERE user_id = %s", (current_user_id,))
            health = cursor.fetchone()

            cursor.execute("SELECT condition_key FROM user_health_conditions WHERE user_id = %s", (current_user_id,))
            conditions = [row['condition_key'] for row in cursor.fetchall()]

            cursor.execute("SELECT sensitivity, is_custom FROM user_sensitivities WHERE user_id = %s", (current_user_id,))
            sensitivities = [row['sensitivity'] for row in cursor.fetchall()]

            health_profile = None
            if health:
                health_profile = {
                    "age_group": health['age_group'],
                    "conditions": conditions,
                    "sensitivities": sensitivities
                }

            return jsonify({
                "success": True,
                "user": user,
                "health_profile": health_profile
            }), 200

        except Exception as e:
            return jsonify({"success": False, "message": f"An error occurred: {str(e)}"}), 500
        finally:
            cursor.close()
            conn.close()

    @app.route('/user/profile', methods=['PUT'])
    @token_required
    def update_profile(current_user_id):
        data = request.get_json()
        if not data:
            return jsonify({"success": False, "message": "No data provided"}), 400

        fullname = data.get('fullname', '').strip()
        phone = data.get('phone', '').strip()
        email = data.get('email', '').strip().lower()

        if fullname and not is_valid_fullname(fullname):
            return jsonify({"success": False, "message": "Invalid full name"}), 400
        if phone and not is_valid_phone(phone):
            return jsonify({"success": False, "message": "Invalid phone number"}), 400
        if email and not is_valid_email(email):
            return jsonify({"success": False, "message": "Invalid email format"}), 400

        conn = get_db_connection()
        if conn is None:
            return jsonify({"success": False, "message": "Database connection error"}), 500

        cursor = conn.cursor(dictionary=True)
        try:
            if email:
                cursor.execute("SELECT id FROM users WHERE email = %s AND id != %s", (email, current_user_id))
                if cursor.fetchone():
                    return jsonify({"success": False, "message": "Email is already in use"}), 400

            updates = []
            values = []
            if fullname:
                updates.append("fullname = %s")
                values.append(fullname)
            if phone:
                updates.append("phone = %s")
                values.append(phone)
            if email:
                updates.append("email = %s")
                values.append(email)

            if not updates:
                return jsonify({"success": False, "message": "No fields to update"}), 400

            values.append(current_user_id)
            cursor.execute(f"UPDATE users SET {', '.join(updates)} WHERE id = %s", tuple(values))
            conn.commit()

            return jsonify({"success": True, "message": "Profile updated successfully"}), 200

        except Exception as e:
            return jsonify({"success": False, "message": f"An error occurred: {str(e)}"}), 500
        finally:
            cursor.close()
            conn.close()

    @app.route('/user/health-profile', methods=['POST'])
    @token_required
    def save_health_profile(current_user_id):
        data = request.get_json()
        if not data:
            return jsonify({"success": False, "message": "No data provided"}), 400

        age_group = data.get('age_group')
        conditions = data.get('conditions', [])
        sensitivities = data.get('sensitivities', [])
        custom_sensitivities = data.get('custom_sensitivities', [])

        if not age_group:
            return jsonify({"success": False, "message": "Age group is required"}), 400

        valid_age_groups = ['Child', 'Teen', 'Adult', 'Senior']
        if age_group not in valid_age_groups:
            return jsonify({"success": False, "message": f"Age group must be one of: {valid_age_groups}"}), 400

        conn = get_db_connection()
        if conn is None:
            return jsonify({"success": False, "message": "Database connection error"}), 500

        cursor = conn.cursor()
        try:
            # Upsert health profile
            cursor.execute("SELECT id FROM health_profiles WHERE user_id = %s", (current_user_id,))
            if cursor.fetchone():
                cursor.execute(
                    "UPDATE health_profiles SET age_group = %s WHERE user_id = %s",
                    (age_group, current_user_id)
                )
            else:
                cursor.execute(
                    "INSERT INTO health_profiles (user_id, age_group) VALUES (%s, %s)",
                    (current_user_id, age_group)
                )

            # Replace conditions
            cursor.execute("DELETE FROM user_health_conditions WHERE user_id = %s", (current_user_id,))
            for condition in conditions:
                cursor.execute(
                    "INSERT INTO user_health_conditions (user_id, condition_key) VALUES (%s, %s)",
                    (current_user_id, condition)
                )

            # Replace sensitivities
            cursor.execute("DELETE FROM user_sensitivities WHERE user_id = %s", (current_user_id,))
            for sens in sensitivities:
                cursor.execute(
                    "INSERT INTO user_sensitivities (user_id, sensitivity, is_custom) VALUES (%s, %s, FALSE)",
                    (current_user_id, sens)
                )
            for sens in custom_sensitivities:
                cursor.execute(
                    "INSERT INTO user_sensitivities (user_id, sensitivity, is_custom) VALUES (%s, %s, TRUE)",
                    (current_user_id, sens)
                )

            conn.commit()
            return jsonify({"success": True, "message": "Health profile saved successfully"}), 200

        except Exception as e:
            return jsonify({"success": False, "message": f"An error occurred: {str(e)}"}), 500
        finally:
            cursor.close()
            conn.close()

    @app.route('/user/account', methods=['DELETE'])
    @token_required
    def delete_account(current_user_id):
        data = request.get_json()
        if not data:
            return jsonify({"success": False, "message": "No data provided"}), 400

        password = data.get('password', '')
        if not password:
            return jsonify({"success": False, "message": "Password is required"}), 400

        conn = get_db_connection()
        if conn is None:
            return jsonify({"success": False, "message": "Database connection error"}), 500

        cursor = conn.cursor(dictionary=True)
        try:
            cursor.execute("SELECT password_hash FROM users WHERE id = %s", (current_user_id,))
            user = cursor.fetchone()
            if not user or not bcrypt.check_password_hash(user['password_hash'], password):
                return jsonify({"success": False, "message": "Incorrect password"}), 401

            cursor.execute("DELETE FROM users WHERE id = %s", (current_user_id,))
            conn.commit()

            return jsonify({"success": True, "message": "Account deleted successfully"}), 200

        except Exception as e:
            return jsonify({"success": False, "message": f"An error occurred: {str(e)}"}), 500
        finally:
            cursor.close()
            conn.close()
