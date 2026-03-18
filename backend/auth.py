import jwt
import uuid
import random
from datetime import datetime, timedelta, timezone
from flask import request, jsonify
from flask_bcrypt import Bcrypt
from database import get_db_connection
from config import JWT_SECRET_KEY, JWT_EXPIRY_DAYS
from middleware import token_required
from validators import is_valid_email, is_valid_password, is_valid_phone, is_valid_fullname
from email_service import send_otp_email, send_signup_otp_email

bcrypt = Bcrypt()


def init_bcrypt(app):
    bcrypt.init_app(app)


def register_auth_routes(app):

    @app.route('/auth/send-signup-otp', methods=['POST'])
    def send_signup_otp():
        data = request.get_json()
        if not data:
            return jsonify({"success": False, "message": "No data provided"}), 400

        fullname = data.get('fullname', '').strip()
        phone = data.get('phone', '').strip()
        email = data.get('email', '').strip().lower()
        password = data.get('password', '')
        confirm_password = data.get('confirm_password', '')

        if not all([fullname, phone, email, password, confirm_password]):
            return jsonify({"success": False, "message": "All fields are required"}), 400

        if not is_valid_fullname(fullname):
            return jsonify({"success": False, "message": "Full name must contain only letters and spaces (min 3 chars)"}), 400

        if not is_valid_phone(phone):
            return jsonify({"success": False, "message": "Phone must be exactly 10 digits"}), 400

        if not is_valid_email(email):
            return jsonify({"success": False, "message": "Invalid email format"}), 400

        if not is_valid_password(password):
            return jsonify({"success": False, "message": "Password must be 8+ chars with uppercase, lowercase, digit, and special character"}), 400

        if password != confirm_password:
            return jsonify({"success": False, "message": "Passwords do not match"}), 400

        conn = get_db_connection()
        if conn is None:
            return jsonify({"success": False, "message": "Database connection error"}), 500

        cursor = conn.cursor()
        try:
            cursor.execute("SELECT id FROM users WHERE email = %s", (email,))
            if cursor.fetchone():
                return jsonify({"success": False, "message": "Email is already registered"}), 400

            # Invalidate any existing unused signup OTPs for this email
            cursor.execute(
                "UPDATE signup_otps SET is_used = TRUE WHERE email = %s AND is_used = FALSE",
                (email,)
            )

            otp = str(random.randint(1000, 9999))
            expires_at = datetime.now(timezone.utc) + timedelta(minutes=10)

            cursor.execute(
                "INSERT INTO signup_otps (email, otp_code, expires_at) VALUES (%s, %s, %s)",
                (email, otp, expires_at)
            )
            conn.commit()

            try:
                send_signup_otp_email(email, otp)
            except Exception:
                print(f"Warning: Could not send signup OTP email to {email}. OTP: {otp}")

            return jsonify({"success": True, "message": "OTP sent to your email"}), 200

        except Exception as e:
            return jsonify({"success": False, "message": f"An error occurred: {str(e)}"}), 500
        finally:
            cursor.close()
            conn.close()

    @app.route('/auth/signup', methods=['POST'])
    def signup():
        data = request.get_json()
        if not data:
            return jsonify({"success": False, "message": "No data provided"}), 400

        fullname = data.get('fullname', '').strip()
        phone = data.get('phone', '').strip()
        email = data.get('email', '').strip().lower()
        password = data.get('password', '')
        confirm_password = data.get('confirm_password', '')
        otp = data.get('otp', '').strip()

        if not all([fullname, phone, email, password, confirm_password, otp]):
            return jsonify({"success": False, "message": "All fields are required"}), 400

        if not is_valid_fullname(fullname):
            return jsonify({"success": False, "message": "Full name must contain only letters and spaces (min 3 chars)"}), 400

        if not is_valid_phone(phone):
            return jsonify({"success": False, "message": "Phone must be exactly 10 digits"}), 400

        if not is_valid_email(email):
            return jsonify({"success": False, "message": "Invalid email format"}), 400

        if not is_valid_password(password):
            return jsonify({"success": False, "message": "Password must be 8+ chars with uppercase, lowercase, digit, and special character"}), 400

        if password != confirm_password:
            return jsonify({"success": False, "message": "Passwords do not match"}), 400

        conn = get_db_connection()
        if conn is None:
            return jsonify({"success": False, "message": "Database connection error"}), 500

        cursor = conn.cursor(dictionary=True)
        try:
            # Verify OTP
            cursor.execute(
                """SELECT id, otp_code, expires_at FROM signup_otps
                   WHERE email = %s AND is_used = FALSE
                   ORDER BY created_at DESC""",
                (email,)
            )
            otp_records = cursor.fetchall()

            if not otp_records:
                return jsonify({"success": False, "message": "No OTP found. Request a new one."}), 400

            otp_record = None
            for record in otp_records:
                if record['otp_code'] == otp:
                    otp_record = record
                    break

            if not otp_record:
                return jsonify({"success": False, "message": "Invalid OTP"}), 400

            if datetime.now(timezone.utc) > otp_record['expires_at'].replace(tzinfo=timezone.utc):
                return jsonify({"success": False, "message": "OTP has expired. Request a new one."}), 400

            # Mark OTP as used
            cursor.execute("UPDATE signup_otps SET is_used = TRUE WHERE id = %s", (otp_record['id'],))

            # Check duplicate email again (race condition guard)
            cursor.execute("SELECT id FROM users WHERE email = %s", (email,))
            if cursor.fetchone():
                return jsonify({"success": False, "message": "Email is already registered"}), 400

            password_hash = bcrypt.generate_password_hash(password).decode('utf-8')
            cursor.execute(
                "INSERT INTO users (fullname, phone, email, password_hash) VALUES (%s, %s, %s, %s)",
                (fullname, phone, email, password_hash)
            )
            conn.commit()
            user_id = cursor.lastrowid

            # Auto-login: generate JWT so health profile can be saved during onboarding
            token = jwt.encode({
                "user_id": user_id,
                "email": email,
                "exp": datetime.now(timezone.utc) + timedelta(days=JWT_EXPIRY_DAYS)
            }, JWT_SECRET_KEY, algorithm="HS256")

            return jsonify({
                "success": True,
                "message": "Account created successfully!",
                "user_id": user_id,
                "token": token
            }), 201

        except Exception as e:
            return jsonify({"success": False, "message": f"An error occurred: {str(e)}"}), 500
        finally:
            cursor.close()
            conn.close()

    @app.route('/auth/login', methods=['POST'])
    def login():
        data = request.get_json()
        if not data:
            return jsonify({"success": False, "message": "No data provided"}), 400

        email = data.get('email', '').strip().lower()
        password = data.get('password', '')

        if not email or not password:
            return jsonify({"success": False, "message": "Email and password are required"}), 400

        conn = get_db_connection()
        if conn is None:
            return jsonify({"success": False, "message": "Database connection error"}), 500

        cursor = conn.cursor(dictionary=True)
        try:
            cursor.execute("SELECT * FROM users WHERE email = %s", (email,))
            user = cursor.fetchone()

            if not user or not bcrypt.check_password_hash(user['password_hash'], password):
                return jsonify({"success": False, "message": "Invalid email or password"}), 401

            # Check if user has a health profile
            cursor.execute("SELECT id FROM health_profiles WHERE user_id = %s", (user['id'],))
            has_health_profile = cursor.fetchone() is not None

            token = jwt.encode({
                "user_id": user['id'],
                "email": user['email'],
                "exp": datetime.now(timezone.utc) + timedelta(days=JWT_EXPIRY_DAYS)
            }, JWT_SECRET_KEY, algorithm="HS256")

            return jsonify({
                "success": True,
                "message": "Login successful",
                "token": token,
                "user": {
                    "id": user['id'],
                    "fullname": user['fullname'],
                    "email": user['email'],
                    "phone": user['phone'],
                    "has_health_profile": has_health_profile
                }
            }), 200

        except Exception as e:
            return jsonify({"success": False, "message": f"An error occurred: {str(e)}"}), 500
        finally:
            cursor.close()
            conn.close()

    @app.route('/auth/forgot-password', methods=['POST'])
    def forgot_password():
        data = request.get_json()
        if not data:
            return jsonify({"success": False, "message": "No data provided"}), 400

        email = data.get('email', '').strip().lower()
        if not email:
            return jsonify({"success": False, "message": "Email is required"}), 400

        conn = get_db_connection()
        if conn is None:
            return jsonify({"success": False, "message": "Database connection error"}), 500

        cursor = conn.cursor(dictionary=True)
        try:
            cursor.execute("SELECT id FROM users WHERE email = %s", (email,))
            user = cursor.fetchone()
            if not user:
                return jsonify({"success": False, "message": "Email not found"}), 404

            # Invalidate any existing unused OTPs for this user
            cursor.execute(
                "UPDATE password_reset_otps SET is_used = TRUE WHERE user_id = %s AND is_used = FALSE",
                (user['id'],)
            )

            otp = str(random.randint(1000, 9999))
            expires_at = datetime.now(timezone.utc) + timedelta(minutes=10)

            cursor.execute(
                "INSERT INTO password_reset_otps (user_id, otp_code, expires_at) VALUES (%s, %s, %s)",
                (user['id'], otp, expires_at)
            )
            conn.commit()

            try:
                send_otp_email(email, otp)
            except Exception:
                # Email sending failed but OTP is saved - log it but don't block
                print(f"Warning: Could not send OTP email to {email}. OTP: {otp}")

            return jsonify({"success": True, "message": "OTP sent to your email"}), 200

        except Exception as e:
            return jsonify({"success": False, "message": f"An error occurred: {str(e)}"}), 500
        finally:
            cursor.close()
            conn.close()

    @app.route('/auth/verify-otp', methods=['POST'])
    def verify_otp():
        data = request.get_json()
        if not data:
            return jsonify({"success": False, "message": "No data provided"}), 400

        email = data.get('email', '').strip().lower()
        otp = data.get('otp', '').strip()

        if not email or not otp:
            return jsonify({"success": False, "message": "Email and OTP are required"}), 400

        conn = get_db_connection()
        if conn is None:
            return jsonify({"success": False, "message": "Database connection error"}), 500

        cursor = conn.cursor(dictionary=True)
        try:
            cursor.execute("SELECT id FROM users WHERE email = %s", (email,))
            user = cursor.fetchone()
            if not user:
                return jsonify({"success": False, "message": "Email not found"}), 404

            cursor.execute(
                """SELECT id, otp_code, expires_at FROM password_reset_otps
                   WHERE user_id = %s AND is_used = FALSE
                   ORDER BY created_at DESC""",
                (user['id'],)
            )
            otp_records = cursor.fetchall()

            if not otp_records:
                return jsonify({"success": False, "message": "No OTP found. Request a new one."}), 400

            # Find matching OTP from any recent unused record
            otp_record = None
            for record in otp_records:
                if record['otp_code'] == otp:
                    otp_record = record
                    break

            if not otp_record:
                return jsonify({"success": False, "message": "Invalid OTP"}), 400

            if datetime.now(timezone.utc) > otp_record['expires_at'].replace(tzinfo=timezone.utc):
                return jsonify({"success": False, "message": "OTP has expired"}), 400

            # Mark OTP as used
            cursor.execute("UPDATE password_reset_otps SET is_used = TRUE WHERE id = %s", (otp_record['id'],))
            conn.commit()

            # Generate a temporary reset token
            reset_token = str(uuid.uuid4())
            cursor.execute(
                "INSERT INTO password_reset_otps (user_id, otp_code, expires_at) VALUES (%s, %s, %s)",
                (user['id'], reset_token, datetime.now(timezone.utc) + timedelta(minutes=15))
            )
            conn.commit()

            return jsonify({
                "success": True,
                "message": "OTP verified",
                "reset_token": reset_token
            }), 200

        except Exception as e:
            return jsonify({"success": False, "message": f"An error occurred: {str(e)}"}), 500
        finally:
            cursor.close()
            conn.close()

    @app.route('/auth/reset-password', methods=['POST'])
    def reset_password():
        data = request.get_json()
        if not data:
            return jsonify({"success": False, "message": "No data provided"}), 400

        email = data.get('email', '').strip().lower()
        reset_token = data.get('reset_token', '').strip()
        new_password = data.get('new_password', '')
        confirm_password = data.get('confirm_password', '')

        if not all([email, reset_token, new_password, confirm_password]):
            return jsonify({"success": False, "message": "All fields are required"}), 400

        if not is_valid_password(new_password):
            return jsonify({"success": False, "message": "Password must be 8+ chars with uppercase, lowercase, digit, and special character"}), 400

        if new_password != confirm_password:
            return jsonify({"success": False, "message": "Passwords do not match"}), 400

        conn = get_db_connection()
        if conn is None:
            return jsonify({"success": False, "message": "Database connection error"}), 500

        cursor = conn.cursor(dictionary=True)
        try:
            cursor.execute("SELECT id FROM users WHERE email = %s", (email,))
            user = cursor.fetchone()
            if not user:
                return jsonify({"success": False, "message": "Email not found"}), 404

            cursor.execute(
                """SELECT id, expires_at FROM password_reset_otps
                   WHERE user_id = %s AND otp_code = %s AND is_used = FALSE
                   ORDER BY created_at DESC LIMIT 1""",
                (user['id'], reset_token)
            )
            token_record = cursor.fetchone()

            if not token_record:
                return jsonify({"success": False, "message": "Invalid or expired reset token"}), 400

            if datetime.now(timezone.utc) > token_record['expires_at'].replace(tzinfo=timezone.utc):
                return jsonify({"success": False, "message": "Reset token has expired"}), 400

            password_hash = bcrypt.generate_password_hash(new_password).decode('utf-8')
            cursor.execute("UPDATE users SET password_hash = %s WHERE id = %s", (password_hash, user['id']))
            cursor.execute("UPDATE password_reset_otps SET is_used = TRUE WHERE id = %s", (token_record['id'],))
            conn.commit()

            return jsonify({"success": True, "message": "Password reset successfully"}), 200

        except Exception as e:
            return jsonify({"success": False, "message": f"An error occurred: {str(e)}"}), 500
        finally:
            cursor.close()
            conn.close()

    @app.route('/auth/change-password', methods=['POST'])
    @token_required
    def change_password(current_user_id):
        data = request.get_json()
        if not data:
            return jsonify({"success": False, "message": "No data provided"}), 400

        current_password = data.get('current_password', '')
        new_password = data.get('new_password', '')
        confirm_password = data.get('confirm_password', '')

        if not all([current_password, new_password, confirm_password]):
            return jsonify({"success": False, "message": "All fields are required"}), 400

        if not is_valid_password(new_password):
            return jsonify({"success": False, "message": "Password must be 8+ chars with uppercase, lowercase, digit, and special character"}), 400

        if new_password != confirm_password:
            return jsonify({"success": False, "message": "Passwords do not match"}), 400

        conn = get_db_connection()
        if conn is None:
            return jsonify({"success": False, "message": "Database connection error"}), 500

        cursor = conn.cursor(dictionary=True)
        try:
            cursor.execute("SELECT password_hash FROM users WHERE id = %s", (current_user_id,))
            user = cursor.fetchone()

            if not user or not bcrypt.check_password_hash(user['password_hash'], current_password):
                return jsonify({"success": False, "message": "Current password is incorrect"}), 401

            password_hash = bcrypt.generate_password_hash(new_password).decode('utf-8')
            cursor.execute("UPDATE users SET password_hash = %s WHERE id = %s", (password_hash, current_user_id))
            conn.commit()

            return jsonify({"success": True, "message": "Password changed successfully"}), 200

        except Exception as e:
            return jsonify({"success": False, "message": f"An error occurred: {str(e)}"}), 500
        finally:
            cursor.close()
            conn.close()
