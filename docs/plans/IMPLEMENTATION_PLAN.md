# NutriTrace Backend + Frontend Wiring — Complete Implementation Plan

**Date:** 2026-03-15
**Status:** Ready for execution
**Scope:** Full backend (Python/Flask/MySQL) + Android frontend wiring

---

## Prerequisites

Before starting, ensure:
1. XAMPP is installed and MySQL is running on localhost:3306
2. Python 3.10+ is installed
3. A Google Cloud account with Vision API enabled and a service account JSON key file
4. A Google AI Studio account with a Gemini API key
5. A Gmail account with an App Password generated (Settings → Security → App Passwords)
6. Android Studio is set up with the NutriTrace project

---

## Phase 1: Backend Foundation (config, database, middleware)

### Task 1.1: Create `backend/config.py`

**File:** `backend/config.py` (NEW)

```python
import os
from dotenv import load_dotenv

load_dotenv()

DB_HOST = os.getenv("DB_HOST", "localhost")
DB_USER = os.getenv("DB_USER", "root")
DB_PASSWORD = os.getenv("DB_PASSWORD", "")
DB_NAME = os.getenv("DB_NAME", "nutritrace_db")

JWT_SECRET_KEY = os.getenv("JWT_SECRET_KEY", "change-this-secret-key")
JWT_EXPIRY_DAYS = int(os.getenv("JWT_EXPIRY_DAYS", "30"))

GOOGLE_APPLICATION_CREDENTIALS = os.getenv("GOOGLE_APPLICATION_CREDENTIALS", "")
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")

SMTP_SERVER = os.getenv("SMTP_SERVER", "smtp.gmail.com")
SMTP_PORT = int(os.getenv("SMTP_PORT", "587"))
SMTP_EMAIL = os.getenv("SMTP_EMAIL", "")
SMTP_PASSWORD = os.getenv("SMTP_PASSWORD", "")

UPLOAD_FOLDER = os.getenv("UPLOAD_FOLDER", "uploads")
MAX_CONTENT_LENGTH = int(os.getenv("MAX_CONTENT_LENGTH", "10485760"))  # 10MB
```

**Verification:** `python -c "from config import *; print(DB_HOST)"` prints `localhost`

---

### Task 1.2: Create `backend/.env`

**File:** `backend/.env` (NEW)

```env
DB_HOST=localhost
DB_USER=root
DB_PASSWORD=
DB_NAME=nutritrace_db

JWT_SECRET_KEY=your-secret-key-here-change-in-production
JWT_EXPIRY_DAYS=30

GOOGLE_APPLICATION_CREDENTIALS=path/to/service-account.json
GEMINI_API_KEY=your-gemini-api-key

SMTP_SERVER=smtp.gmail.com
SMTP_PORT=587
SMTP_EMAIL=your-email@gmail.com
SMTP_PASSWORD=your-app-password

UPLOAD_FOLDER=uploads
MAX_CONTENT_LENGTH=10485760
```

**Also:** Add `backend/.env` to `.gitignore` if not already there.

---

### Task 1.3: Rewrite `backend/database.py`

**File:** `backend/database.py` (REPLACE entire file)

Replace the existing file. The new version:
- Imports config from `config.py` instead of hardcoded values
- Drops all existing tables and recreates them (clean slate per design decision)
- Creates all 8 tables with proper foreign keys and CASCADE deletes

```python
import mysql.connector
from config import DB_HOST, DB_USER, DB_PASSWORD, DB_NAME

def get_db_connection():
    try:
        connection = mysql.connector.connect(
            host=DB_HOST,
            user=DB_USER,
            password=DB_PASSWORD,
            database=DB_NAME
        )
        return connection
    except mysql.connector.Error as err:
        print(f"Database connection error: {err}")
        return None

def init_db():
    # Step 1: Create database if not exists
    try:
        conn = mysql.connector.connect(
            host=DB_HOST,
            user=DB_USER,
            password=DB_PASSWORD
        )
        cursor = conn.cursor()
        cursor.execute(f"CREATE DATABASE IF NOT EXISTS {DB_NAME}")
        cursor.close()
        conn.close()
        print(f"Database '{DB_NAME}' ready.")
    except mysql.connector.Error as err:
        print(f"Failed creating database: {err}")
        return

    # Step 2: Connect to database and create tables
    conn = get_db_connection()
    if conn is None:
        return

    cursor = conn.cursor()

    # Drop tables in reverse dependency order
    drop_order = [
        "scan_ingredients",
        "comparisons",
        "scans",
        "password_reset_otps",
        "user_sensitivities",
        "user_health_conditions",
        "health_profiles",
        "users"
    ]
    for table in drop_order:
        cursor.execute(f"DROP TABLE IF EXISTS {table}")

    # Create tables
    tables = [
        """
        CREATE TABLE users (
            id INT AUTO_INCREMENT PRIMARY KEY,
            fullname VARCHAR(255) NOT NULL,
            phone VARCHAR(50) NOT NULL,
            email VARCHAR(255) NOT NULL UNIQUE,
            password_hash VARCHAR(255) NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        )
        """,
        """
        CREATE TABLE health_profiles (
            id INT AUTO_INCREMENT PRIMARY KEY,
            user_id INT NOT NULL UNIQUE,
            age_group ENUM('Child', 'Teen', 'Adult', 'Senior') NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )
        """,
        """
        CREATE TABLE user_health_conditions (
            id INT AUTO_INCREMENT PRIMARY KEY,
            user_id INT NOT NULL,
            condition_key VARCHAR(100) NOT NULL,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            UNIQUE(user_id, condition_key)
        )
        """,
        """
        CREATE TABLE user_sensitivities (
            id INT AUTO_INCREMENT PRIMARY KEY,
            user_id INT NOT NULL,
            sensitivity VARCHAR(255) NOT NULL,
            is_custom BOOLEAN DEFAULT FALSE,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            UNIQUE(user_id, sensitivity)
        )
        """,
        """
        CREATE TABLE scans (
            id INT AUTO_INCREMENT PRIMARY KEY,
            user_id INT NOT NULL,
            product_name VARCHAR(255),
            brand_name VARCHAR(255),
            score INT NOT NULL,
            risk_level ENUM('LOW', 'MODERATE', 'HIGH') NOT NULL,
            image_path VARCHAR(500),
            raw_ocr_text TEXT,
            ai_analysis JSON,
            scanned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )
        """,
        """
        CREATE TABLE scan_ingredients (
            id INT AUTO_INCREMENT PRIMARY KEY,
            scan_id INT NOT NULL,
            ingredient_name VARCHAR(255) NOT NULL,
            status ENUM('SAFE', 'CAUTION', 'AVOID') NOT NULL,
            reason TEXT,
            FOREIGN KEY (scan_id) REFERENCES scans(id) ON DELETE CASCADE
        )
        """,
        """
        CREATE TABLE comparisons (
            id INT AUTO_INCREMENT PRIMARY KEY,
            user_id INT NOT NULL,
            scan_id_a INT NOT NULL,
            scan_id_b INT NOT NULL,
            chosen_product ENUM('A', 'B') DEFAULT NULL,
            ai_summary TEXT,
            compared_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY (scan_id_a) REFERENCES scans(id) ON DELETE CASCADE,
            FOREIGN KEY (scan_id_b) REFERENCES scans(id) ON DELETE CASCADE
        )
        """,
        """
        CREATE TABLE password_reset_otps (
            id INT AUTO_INCREMENT PRIMARY KEY,
            user_id INT NOT NULL,
            otp_code VARCHAR(6) NOT NULL,
            expires_at TIMESTAMP NOT NULL,
            is_used BOOLEAN DEFAULT FALSE,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )
        """
    ]

    for sql in tables:
        cursor.execute(sql)

    conn.commit()
    cursor.close()
    conn.close()
    print("All 8 tables created successfully.")
```

**Verification:** Run `cd backend && python -c "from database import init_db; init_db()"` — should print "All 8 tables created successfully."

---

### Task 1.4: Create `backend/middleware.py`

**File:** `backend/middleware.py` (NEW)

JWT token verification decorator. Used on all protected endpoints.

```python
import jwt
from functools import wraps
from flask import request, jsonify
from config import JWT_SECRET_KEY

def token_required(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        token = None
        auth_header = request.headers.get("Authorization")
        if auth_header and auth_header.startswith("Bearer "):
            token = auth_header.split(" ")[1]

        if not token:
            return jsonify({"success": False, "message": "Token is missing"}), 401

        try:
            data = jwt.decode(token, JWT_SECRET_KEY, algorithms=["HS256"])
            current_user_id = data["user_id"]
        except jwt.ExpiredSignatureError:
            return jsonify({"success": False, "message": "Token has expired"}), 401
        except jwt.InvalidTokenError:
            return jsonify({"success": False, "message": "Invalid token"}), 401

        return f(current_user_id, *args, **kwargs)

    return decorated
```

**Verification:** Import works: `python -c "from middleware import token_required; print('OK')"`

---

### Task 1.5: Create `backend/validators.py`

**File:** `backend/validators.py` (NEW)

```python
import re

def is_valid_email(email):
    pattern = r'^[\w\.-]+@[\w\.-]+\.\w+$'
    return re.match(pattern, email) is not None

def is_valid_password(password):
    if len(password) < 8:
        return False
    if not any(c.isupper() for c in password):
        return False
    if not any(c.islower() for c in password):
        return False
    if not any(c.isdigit() for c in password):
        return False
    if not any(c in "!@#$%^&*" for c in password):
        return False
    return True

def is_valid_phone(phone):
    return bool(re.match(r'^\d{10}$', phone))

def is_valid_fullname(name):
    return bool(re.match(r'^[a-zA-Z\s]{3,}$', name))
```

---

### Task 1.6: Update `backend/requirements.txt`

**File:** `backend/requirements.txt` (REPLACE entire file)

```
Flask==3.0.3
mysql-connector-python==9.0.0
flask-cors==4.0.0
flask-bcrypt==1.0.1
PyJWT==2.8.0
google-cloud-vision==3.7.2
google-generativeai==0.5.4
python-dotenv==1.0.1
Pillow==10.3.0
```

**Verification:** `cd backend && pip install -r requirements.txt`

---

## Phase 2: Authentication Module

### Task 2.1: Create `backend/auth.py`

**File:** `backend/auth.py` (NEW)

Contains 6 endpoints:
- `POST /auth/signup` — register with bcrypt hashing
- `POST /auth/login` — verify credentials, return JWT
- `POST /auth/forgot-password` — send OTP via email
- `POST /auth/verify-otp` — verify OTP, return reset token
- `POST /auth/reset-password` — set new password with reset token
- `POST /auth/change-password` — change password (authenticated)

```python
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
from email_service import send_otp_email

bcrypt = Bcrypt()

def init_bcrypt(app):
    bcrypt.init_app(app)

def register_auth_routes(app):

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

            password_hash = bcrypt.generate_password_hash(password).decode('utf-8')
            cursor.execute(
                "INSERT INTO users (fullname, phone, email, password_hash) VALUES (%s, %s, %s, %s)",
                (fullname, phone, email, password_hash)
            )
            conn.commit()
            user_id = cursor.lastrowid

            return jsonify({
                "success": True,
                "message": "Account created successfully!",
                "user_id": user_id
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

            otp = str(random.randint(1000, 9999))
            expires_at = datetime.now(timezone.utc) + timedelta(minutes=10)

            cursor.execute(
                "INSERT INTO password_reset_otps (user_id, otp_code, expires_at) VALUES (%s, %s, %s)",
                (user['id'], otp, expires_at)
            )
            conn.commit()

            send_otp_email(email, otp)

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
                   ORDER BY created_at DESC LIMIT 1""",
                (user['id'],)
            )
            otp_record = cursor.fetchone()

            if not otp_record:
                return jsonify({"success": False, "message": "No OTP found. Request a new one."}), 400

            if otp_record['otp_code'] != otp:
                return jsonify({"success": False, "message": "Invalid OTP"}), 400

            if datetime.now(timezone.utc) > otp_record['expires_at'].replace(tzinfo=timezone.utc):
                return jsonify({"success": False, "message": "OTP has expired"}), 400

            # Mark OTP as used
            cursor.execute("UPDATE password_reset_otps SET is_used = TRUE WHERE id = %s", (otp_record['id'],))
            conn.commit()

            # Generate a temporary reset token
            reset_token = str(uuid.uuid4())
            # Store it as a new OTP record for verification during reset
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

            # Verify reset token
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

            # Update password
            password_hash = bcrypt.generate_password_hash(new_password).decode('utf-8')
            cursor.execute("UPDATE users SET password_hash = %s WHERE id = %s", (password_hash, user['id']))

            # Mark token as used
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
```

---

### Task 2.2: Create `backend/email_service.py`

**File:** `backend/email_service.py` (NEW)

```python
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from config import SMTP_SERVER, SMTP_PORT, SMTP_EMAIL, SMTP_PASSWORD

def send_otp_email(to_email, otp_code):
    try:
        msg = MIMEMultipart()
        msg['From'] = SMTP_EMAIL
        msg['To'] = to_email
        msg['Subject'] = 'NutriTrace - Password Reset OTP'

        body = f"""
        <html>
        <body>
            <h2>Password Reset</h2>
            <p>Your OTP code is: <strong>{otp_code}</strong></p>
            <p>This code expires in 10 minutes.</p>
            <p>If you did not request this, please ignore this email.</p>
        </body>
        </html>
        """
        msg.attach(MIMEText(body, 'html'))

        server = smtplib.SMTP(SMTP_SERVER, SMTP_PORT)
        server.starttls()
        server.login(SMTP_EMAIL, SMTP_PASSWORD)
        server.sendmail(SMTP_EMAIL, to_email, msg.as_string())
        server.quit()
        print(f"OTP email sent to {to_email}")
    except Exception as e:
        print(f"Failed to send email: {e}")
        raise
```

---

## Phase 3: User Profile Module

### Task 3.1: Create `backend/user.py`

**File:** `backend/user.py` (NEW)

Contains 4 endpoints:
- `GET /user/profile`
- `PUT /user/profile`
- `POST /user/health-profile`
- `DELETE /user/account`

```python
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

            # Get health profile
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
            # Check email uniqueness if changed
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
```

---

## Phase 4: Scan & AI Analysis Module

### Task 4.1: Create `backend/ocr_service.py`

**File:** `backend/ocr_service.py` (NEW)

```python
from google.cloud import vision
import os
from config import GOOGLE_APPLICATION_CREDENTIALS

# Set credentials path
if GOOGLE_APPLICATION_CREDENTIALS:
    os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = GOOGLE_APPLICATION_CREDENTIALS

def extract_text_from_image(image_path):
    """Extract text from an image file using Google Cloud Vision API."""
    client = vision.ImageAnnotatorClient()

    with open(image_path, "rb") as image_file:
        content = image_file.read()

    image = vision.Image(content=content)
    response = client.text_detection(image=image)

    if response.error.message:
        raise Exception(f"Vision API error: {response.error.message}")

    texts = response.text_annotations
    if texts:
        return texts[0].description  # Full extracted text
    return ""
```

---

### Task 4.2: Create `backend/ai_service.py`

**File:** `backend/ai_service.py` (NEW)

```python
import json
import google.generativeai as genai
from config import GEMINI_API_KEY

genai.configure(api_key=GEMINI_API_KEY)
model = genai.GenerativeModel("gemini-2.0-flash")

def analyze_ingredients(raw_text, age_group, conditions, sensitivities):
    """Analyze ingredients using Gemini AI based on user's health profile."""

    prompt = f"""You are a food safety and nutrition expert AI. Analyze the following ingredients
for a user with the given health profile.

USER HEALTH PROFILE:
- Age Group: {age_group}
- Health Conditions: {', '.join(conditions) if conditions else 'None specified'}
- Food Sensitivities/Allergies: {', '.join(sensitivities) if sensitivities else 'None specified'}

INGREDIENTS (extracted from product label):
{raw_text}

For each ingredient, provide:
1. ingredient_name: The name of the ingredient
2. status: SAFE, CAUTION, or AVOID
   - SAFE: No known health concerns for this user's profile
   - CAUTION: May have mild effects or should be consumed in moderation
   - AVOID: Directly harmful or triggers known conditions/allergies for this user
3. reason: A short, clear explanation of why this status was assigned,
   specifically referencing the user's conditions if relevant

Also provide:
- overall_score: 0-100 (100 = perfectly safe, 0 = extremely harmful)
- risk_level: LOW (score >= 70), MODERATE (40-69), HIGH (< 40)
- overview: A 2-3 sentence summary of the product's safety for this user
- guidance: 3-5 actionable recommendations

Respond ONLY with valid JSON in this exact format:
{{
    "overall_score": 65,
    "risk_level": "MODERATE",
    "overview": "...",
    "ingredients": [
        {{"ingredient_name": "...", "status": "SAFE", "reason": "..."}},
        {{"ingredient_name": "...", "status": "CAUTION", "reason": "..."}}
    ],
    "guidance": ["...", "..."]
}}"""

    response = model.generate_content(prompt)
    text = response.text.strip()

    # Strip markdown code fences if present
    if text.startswith("```"):
        text = text.split("\n", 1)[1]  # Remove first line
        if text.endswith("```"):
            text = text[:-3]
        text = text.strip()

    return json.loads(text)


def compare_products(product_a_name, product_a_ingredients, product_b_name, product_b_ingredients, age_group, conditions, sensitivities):
    """Compare two products using Gemini AI."""

    prompt = f"""You are a food safety and nutrition expert AI. Compare these two food products
for the user with the given health profile.

USER HEALTH PROFILE:
- Age Group: {age_group}
- Health Conditions: {', '.join(conditions) if conditions else 'None specified'}
- Food Sensitivities/Allergies: {', '.join(sensitivities) if sensitivities else 'None specified'}

PRODUCT A: {product_a_name}
Ingredients: {product_a_ingredients}

PRODUCT B: {product_b_name}
Ingredients: {product_b_ingredients}

Provide:
1. recommendation: "A", "B", or "NEITHER" (if both are equally bad)
2. summary: 2-3 sentence explanation of which is better and why
3. detailed_comparison: Key differences relevant to user's health
4. warnings: Any critical warnings for either product

Respond ONLY with valid JSON in this exact format:
{{
    "recommendation": "A",
    "summary": "...",
    "detailed_comparison": {{
        "harmful_ingredients": "...",
        "overall": "..."
    }},
    "warnings": ["..."]
}}"""

    response = model.generate_content(prompt)
    text = response.text.strip()

    if text.startswith("```"):
        text = text.split("\n", 1)[1]
        if text.endswith("```"):
            text = text[:-3]
        text = text.strip()

    return json.loads(text)
```

---

### Task 4.3: Create `backend/scan.py`

**File:** `backend/scan.py` (NEW)

Contains 6 endpoints:
- `POST /scan/analyze` — full OCR → AI pipeline
- `GET /scan/history` — all user scans with optional search
- `GET /scan/latest` — last 3 scans
- `GET /scan/<scan_id>` — full scan detail
- `PUT /scan/<scan_id>` — update product/brand name
- `DELETE /scan/<scan_id>` — delete scan

```python
import os
import uuid
import json
from flask import request, jsonify, send_from_directory
from database import get_db_connection
from middleware import token_required
from ocr_service import extract_text_from_image
from ai_service import analyze_ingredients
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

        try:
            # Step 1: OCR
            raw_text = extract_text_from_image(image_path)
            if not raw_text.strip():
                return jsonify({"success": False, "message": "Could not extract text from image. Try a clearer photo."}), 400

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
                    "scanned_at": str(cursor.lastrowid),  # Will be set by DB
                    "overview": overview,
                    "ingredients": ingredients,
                    "risk_breakdown": risk_breakdown,
                    "guidance": guidance
                }
            }), 200

        except Exception as e:
            return jsonify({"success": False, "message": f"Analysis failed: {str(e)}"}), 500
        finally:
            if 'cursor' in dir():
                cursor.close()
            if 'conn' in dir():
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
            # Convert datetime to string
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
```

---

## Phase 5: Compare & Dashboard Module

### Task 5.1: Create `backend/compare.py`

**File:** `backend/compare.py` (NEW)

```python
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

            # Save comparison
            cursor.execute(
                """INSERT INTO comparisons (user_id, scan_id_a, scan_id_b, chosen_product, ai_summary)
                   VALUES (%s, %s, %s, %s, %s)""",
                (current_user_id, scan_id_a, scan_id_b,
                 recommendation if recommendation in ('A', 'B') else None,
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
```

---

### Task 5.2: Create `backend/dashboard.py`

**File:** `backend/dashboard.py` (NEW)

```python
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
                """SELECT id, product_name, brand_name, score, risk_level, scanned_at
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
```

---

### Task 5.3: Rewrite `backend/main.py`

**File:** `backend/main.py` (REPLACE entire file)

The new main.py imports all route modules and registers them.

```python
import os
from flask import Flask
from flask_cors import CORS
from database import init_db
from config import UPLOAD_FOLDER, MAX_CONTENT_LENGTH
from auth import register_auth_routes, init_bcrypt
from user import register_user_routes, init_user_bcrypt
from scan import register_scan_routes
from compare import register_compare_routes
from dashboard import register_dashboard_routes

app = Flask(__name__)
app.config['MAX_CONTENT_LENGTH'] = MAX_CONTENT_LENGTH
CORS(app)

# Initialize extensions
init_bcrypt(app)
init_user_bcrypt(app)

# Initialize database
init_db()

# Ensure uploads directory exists
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

# Register all routes
register_auth_routes(app)
register_user_routes(app)
register_scan_routes(app)
register_compare_routes(app)
register_dashboard_routes(app)

@app.route('/')
def index():
    return {"message": "NutriTrace API is running", "version": "1.0"}

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
```

**Verification:** `cd backend && python main.py` — server starts on port 5000 with no errors. Visit `http://localhost:5000/` to see the JSON message.

---

## Phase 6: Android Frontend Wiring

### Task 6.1: Create `ApiClient.kt`

**File:** `app/src/main/java/com/simats/nutritrace/ApiClient.kt` (NEW)

Singleton HTTP helper that wraps OkHttp. All Activities will use this.

```kotlin
package com.simats.nutritrace

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

object ApiClient {
    // CHANGE THIS to your computer's local IP address (run ipconfig in terminal)
    private const val BASE_URL = "http://192.168.1.100:5000"

    private val client = OkHttpClient()
    private val gson = Gson()
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    private fun getToken(context: Context): String? {
        val prefs = context.getSharedPreferences("NutriTracePrefs", Context.MODE_PRIVATE)
        return prefs.getString("AUTH_TOKEN", null)
    }

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences("NutriTracePrefs", Context.MODE_PRIVATE)
            .edit().putString("AUTH_TOKEN", token).apply()
    }

    fun saveUserInfo(context: Context, id: Int, fullname: String, email: String, phone: String) {
        context.getSharedPreferences("NutriTracePrefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("USER_ID", id)
            .putString("USER_FULLNAME", fullname)
            .putString("USER_EMAIL", email)
            .putString("USER_PHONE", phone)
            .apply()
    }

    fun clearSession(context: Context) {
        context.getSharedPreferences("NutriTracePrefs", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    // POST JSON (no auth)
    fun post(endpoint: String, body: Map<String, Any>, callback: (Boolean, JsonObject?) -> Unit) {
        val json = gson.toJson(body)
        val requestBody = json.toRequestBody(JSON_TYPE)
        val request = Request.Builder()
            .url("$BASE_URL$endpoint")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                val jsonObj = if (responseBody != null) gson.fromJson(responseBody, JsonObject::class.java) else null
                callback(response.isSuccessful, jsonObj)
            }
        })
    }

    // POST JSON (with auth)
    fun postAuth(context: Context, endpoint: String, body: Map<String, Any>, callback: (Boolean, JsonObject?) -> Unit) {
        val token = getToken(context) ?: run {
            callback(false, null)
            return
        }
        val json = gson.toJson(body)
        val requestBody = json.toRequestBody(JSON_TYPE)
        val request = Request.Builder()
            .url("$BASE_URL$endpoint")
            .addHeader("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                val jsonObj = if (responseBody != null) gson.fromJson(responseBody, JsonObject::class.java) else null
                callback(response.isSuccessful, jsonObj)
            }
        })
    }

    // GET (with auth)
    fun getAuth(context: Context, endpoint: String, callback: (Boolean, JsonObject?) -> Unit) {
        val token = getToken(context) ?: run {
            callback(false, null)
            return
        }
        val request = Request.Builder()
            .url("$BASE_URL$endpoint")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                val jsonObj = if (responseBody != null) gson.fromJson(responseBody, JsonObject::class.java) else null
                callback(response.isSuccessful, jsonObj)
            }
        })
    }

    // PUT JSON (with auth)
    fun putAuth(context: Context, endpoint: String, body: Map<String, Any>, callback: (Boolean, JsonObject?) -> Unit) {
        val token = getToken(context) ?: run {
            callback(false, null)
            return
        }
        val json = gson.toJson(body)
        val requestBody = json.toRequestBody(JSON_TYPE)
        val request = Request.Builder()
            .url("$BASE_URL$endpoint")
            .addHeader("Authorization", "Bearer $token")
            .put(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                val jsonObj = if (responseBody != null) gson.fromJson(responseBody, JsonObject::class.java) else null
                callback(response.isSuccessful, jsonObj)
            }
        })
    }

    // DELETE (with auth, optional JSON body)
    fun deleteAuth(context: Context, endpoint: String, body: Map<String, Any>? = null, callback: (Boolean, JsonObject?) -> Unit) {
        val token = getToken(context) ?: run {
            callback(false, null)
            return
        }
        val builder = Request.Builder()
            .url("$BASE_URL$endpoint")
            .addHeader("Authorization", "Bearer $token")

        if (body != null) {
            val json = gson.toJson(body)
            builder.delete(json.toRequestBody(JSON_TYPE))
        } else {
            builder.delete()
        }

        client.newCall(builder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                val jsonObj = if (responseBody != null) gson.fromJson(responseBody, JsonObject::class.java) else null
                callback(response.isSuccessful, jsonObj)
            }
        })
    }

    // Multipart image upload (with auth)
    fun uploadImage(context: Context, endpoint: String, imageFile: File, extraFields: Map<String, String> = emptyMap(), callback: (Boolean, JsonObject?) -> Unit) {
        val token = getToken(context) ?: run {
            callback(false, null)
            return
        }

        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        bodyBuilder.addFormDataPart(
            "image", imageFile.name,
            RequestBody.create("image/*".toMediaType(), imageFile)
        )
        for ((key, value) in extraFields) {
            bodyBuilder.addFormDataPart(key, value)
        }

        val request = Request.Builder()
            .url("$BASE_URL$endpoint")
            .addHeader("Authorization", "Bearer $token")
            .post(bodyBuilder.build())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                val jsonObj = if (responseBody != null) gson.fromJson(responseBody, JsonObject::class.java) else null
                callback(response.isSuccessful, jsonObj)
            }
        })
    }
}
```

**Critical:** The `BASE_URL` must be changed to the developer's machine IP. Find it with `ipconfig` on Windows.

---

### Task 6.2: Update `ScanData.kt`

**File:** `app/src/main/java/com/simats/nutritrace/ScanData.kt` (MODIFY)

Add an `id` field to link scans to the backend database.

**Current code (lines 3-9):**
```kotlin
data class ScanData(
    val productName: String,
    val brandName: String,
    val score: Int,
    val riskLevel: String, // "LOW", "MODERATE", "HIGH"
    val time: String
)
```

**Replace with:**
```kotlin
data class ScanData(
    val id: Int = 0,           // Backend scan ID
    val productName: String,
    val brandName: String,
    val score: Int,
    val riskLevel: String,     // "LOW", "MODERATE", "HIGH"
    val time: String,
    val imagePath: String = "" // Server image path
)
```

---

### Task 6.3: Wire `RegisterActivity.kt`

**File:** `app/src/main/java/com/simats/nutritrace/RegisterActivity.kt` (MODIFY)

Replace the mock dialog-based signup (lines 40-57) with an actual API call to `POST /auth/signup`.

**Find this block (lines 40-57):**
```kotlin
        binding.btnCreateAccount.setOnClickListener {
            // Bypass backend and validation for testing
            val dialogView = layoutInflater.inflate(R.layout.dialog_account_created, null)
            val dialog = android.app.AlertDialog.Builder(this@RegisterActivity)
                .setView(dialogView)
                .setCancelable(false)
                .create()

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.show()

            // Navigate to Health Selection setup
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                dialog.dismiss()
                startActivity(Intent(this@RegisterActivity, HealthSelectionActivity::class.java))
                finish()
            }, 2000)
        }
```

**Replace with:**
```kotlin
        binding.btnCreateAccount.setOnClickListener {
            if (!validateFullName() || !validatePhone() || !validateEmail() || !isPasswordValid() || !validateConfirmPassword()) {
                return@setOnClickListener
            }

            binding.btnCreateAccount.isEnabled = false

            val body = mapOf(
                "fullname" to binding.etFullName.text.toString().trim(),
                "phone" to binding.etPhone.text.toString().trim(),
                "email" to binding.etEmail.text.toString().trim(),
                "password" to binding.etPassword.text.toString(),
                "confirm_password" to binding.etConfirmPassword.text.toString()
            )

            ApiClient.post("/auth/signup", body) { success, json ->
                runOnUiThread {
                    binding.btnCreateAccount.isEnabled = true
                    if (success && json?.get("success")?.asBoolean == true) {
                        val dialogView = layoutInflater.inflate(R.layout.dialog_account_created, null)
                        val dialog = android.app.AlertDialog.Builder(this@RegisterActivity)
                            .setView(dialogView)
                            .setCancelable(false)
                            .create()
                        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                        dialog.show()

                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            dialog.dismiss()
                            startActivity(Intent(this@RegisterActivity, AgeSelectionActivity::class.java))
                            finish()
                        }, 2000)
                    } else {
                        val message = json?.get("message")?.asString ?: "Registration failed"
                        android.widget.Toast.makeText(this@RegisterActivity, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
```

Also remove the unused imports for coroutines, OkHttp, and JSONObject (lines 13-21) and replace with:
```kotlin
// No additional imports needed — ApiClient handles networking
```

---

### Task 6.4: Wire `LoginActivity.kt`

**File:** `app/src/main/java/com/simats/nutritrace/LoginActivity.kt` (MODIFY)

Replace the mock login (lines 23-41) with actual API call.

**Find this block (lines 23-41):**
```kotlin
        binding.btnSignIn.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            val isEmailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
            val isPasswordValid = password.length >= 8

            if (!isEmailValid || !isPasswordValid) {
            }

            // Assume successful login and point to HomeActivity as requested
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
```

**Replace with:**
```kotlin
        binding.btnSignIn.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                android.widget.Toast.makeText(this, "Please enter email and password", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnSignIn.isEnabled = false

            ApiClient.post("/auth/login", mapOf("email" to email, "password" to password)) { success, json ->
                runOnUiThread {
                    binding.btnSignIn.isEnabled = true
                    if (success && json?.get("success")?.asBoolean == true) {
                        val token = json.get("token").asString
                        val user = json.getAsJsonObject("user")
                        ApiClient.saveToken(this, token)
                        ApiClient.saveUserInfo(
                            this,
                            user.get("id").asInt,
                            user.get("fullname").asString,
                            user.get("email").asString,
                            user.get("phone").asString
                        )

                        val hasHealthProfile = user.get("has_health_profile").asBoolean
                        val intent = if (hasHealthProfile) {
                            Intent(this, HomeActivity::class.java)
                        } else {
                            Intent(this, AgeSelectionActivity::class.java)
                        }
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    } else {
                        val message = json?.get("message")?.asString ?: "Login failed"
                        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
```

---

### Task 6.5: Wire `ForgotPasswordActivity.kt`

**File:** `app/src/main/java/com/simats/nutritrace/ForgotPasswordActivity.kt` (MODIFY)

**Find this block (lines 46-49):**
```kotlin
        binding.btnSendOtp.setOnClickListener {
            val email = binding.etEmail.text.toString()
            startActivity(Intent(this, VerifyOtpActivity::class.java))
        }
```

**Replace with:**
```kotlin
        binding.btnSendOtp.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            binding.btnSendOtp.isEnabled = false

            ApiClient.post("/auth/forgot-password", mapOf("email" to email)) { success, json ->
                runOnUiThread {
                    binding.btnSendOtp.isEnabled = true
                    if (success && json?.get("success")?.asBoolean == true) {
                        val intent = Intent(this, VerifyOtpActivity::class.java)
                        intent.putExtra("EMAIL", email)
                        startActivity(intent)
                    } else {
                        val message = json?.get("message")?.asString ?: "Failed to send OTP"
                        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
```

---

### Task 6.6: Wire `VerifyOtpActivity.kt`

**File:** `app/src/main/java/com/simats/nutritrace/VerifyOtpActivity.kt` (MODIFY)

**Find this block (lines 23-28):**
```kotlin
        binding.btnVerifyCode.setOnClickListener {
            val otpCode = "${binding.etOtp1.text}${binding.etOtp2.text}${binding.etOtp3.text}${binding.etOtp4.text}"
            // In a real app, verify the OTP here
            // On success, navigate to New Password screen
            startActivity(Intent(this, NewPasswordActivity::class.java))
        }
```

**Replace with:**
```kotlin
        val email = intent.getStringExtra("EMAIL") ?: ""

        binding.btnVerifyCode.setOnClickListener {
            val otpCode = "${binding.etOtp1.text}${binding.etOtp2.text}${binding.etOtp3.text}${binding.etOtp4.text}"
            if (otpCode.length != 4) {
                android.widget.Toast.makeText(this, "Please enter the 4-digit code", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnVerifyCode.isEnabled = false

            ApiClient.post("/auth/verify-otp", mapOf("email" to email, "otp" to otpCode)) { success, json ->
                runOnUiThread {
                    binding.btnVerifyCode.isEnabled = true
                    if (success && json?.get("success")?.asBoolean == true) {
                        val resetToken = json.get("reset_token").asString
                        val intent = Intent(this, NewPasswordActivity::class.java)
                        intent.putExtra("EMAIL", email)
                        intent.putExtra("RESET_TOKEN", resetToken)
                        startActivity(intent)
                    } else {
                        val message = json?.get("message")?.asString ?: "OTP verification failed"
                        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
```

---

### Task 6.7: Wire `NewPasswordActivity.kt`

**File:** `app/src/main/java/com/simats/nutritrace/NewPasswordActivity.kt` (MODIFY)

**Find this block (lines 32-35):**
```kotlin
        binding.btnResetPassword.setOnClickListener {
            // Show Success Dialog
            showSuccessDialog()
        }
```

**Replace with:**
```kotlin
        val email = intent.getStringExtra("EMAIL") ?: ""
        val resetToken = intent.getStringExtra("RESET_TOKEN") ?: ""

        binding.btnResetPassword.setOnClickListener {
            val newPassword = binding.etNewPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()

            binding.btnResetPassword.isEnabled = false

            ApiClient.post("/auth/reset-password", mapOf(
                "email" to email,
                "reset_token" to resetToken,
                "new_password" to newPassword,
                "confirm_password" to confirmPassword
            )) { success, json ->
                runOnUiThread {
                    binding.btnResetPassword.isEnabled = true
                    if (success && json?.get("success")?.asBoolean == true) {
                        showSuccessDialog()
                    } else {
                        val message = json?.get("message")?.asString ?: "Password reset failed"
                        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
```

---

### Task 6.8: Wire `AgeSelectionActivity.kt`, `HealthSelectionActivity.kt`, `SensitivitySelectionActivity.kt`

These three activities collect health data locally and pass it forward via Intent extras. The final send happens in `ProfileSuccessActivity`.

**AgeSelectionActivity.kt (lines 41-52)** — modify the `btnContinue` click:

**Find:**
```kotlin
            if (selectedAgeGroup != null) {
                // Navigate to next step
                val intent = Intent(this, HealthSelectionActivity::class.java)
                val isEditing = getIntent().getBooleanExtra("IS_EDITING_PROFILE", false)
                intent.putExtra("IS_EDITING_PROFILE", isEditing)
                // If editing, we can optionally pre-select based on mock data
                startActivity(intent)
```

**Replace with:**
```kotlin
            if (selectedAgeGroup != null) {
                val intent = Intent(this, HealthSelectionActivity::class.java)
                val isEditing = getIntent().getBooleanExtra("IS_EDITING_PROFILE", false)
                intent.putExtra("IS_EDITING_PROFILE", isEditing)
                intent.putExtra("AGE_GROUP", selectedAgeGroup)
                startActivity(intent)
```

**HealthSelectionActivity.kt (lines 109-119)** — pass age_group and conditions forward:

**Find:**
```kotlin
            if (checkboxes.any { it.isChecked }) {
                // Next step in flow: SensitivitySelectionActivity
                val intent = Intent(this, SensitivitySelectionActivity::class.java)
                val isEditing = getIntent().getBooleanExtra("IS_EDITING_PROFILE", false)
                intent.putExtra("IS_EDITING_PROFILE", isEditing)
                startActivity(intent)
```

**Replace with:**
```kotlin
            if (checkboxes.any { it.isChecked }) {
                val selectedConditions = ArrayList<String>()
                val conditionKeys = listOf("blood_sugar", "cardiovascular", "hormonal", "digestive", "allergy_immune", "no_specific", "not_sure")
                for (i in checkboxes.indices) {
                    if (checkboxes[i].isChecked) {
                        selectedConditions.add(conditionKeys[i])
                    }
                }

                val intent = Intent(this, SensitivitySelectionActivity::class.java)
                val isEditing = getIntent().getBooleanExtra("IS_EDITING_PROFILE", false)
                intent.putExtra("IS_EDITING_PROFILE", isEditing)
                intent.putExtra("AGE_GROUP", getIntent().getStringExtra("AGE_GROUP"))
                intent.putStringArrayListExtra("CONDITIONS", selectedConditions)
                startActivity(intent)
```

**SensitivitySelectionActivity.kt (lines 112-133)** — pass all collected data forward:

**Find:**
```kotlin
            if (isAnyChecked) {
                // Navigate to Success Profile Action
                val intent = Intent(this, ProfileSuccessActivity::class.java)
                val isEditing = getIntent().getBooleanExtra("IS_EDITING_PROFILE", false)
                intent.putExtra("IS_EDITING_PROFILE", isEditing)
                startActivity(intent)
                finish()
```

**Replace with:**
```kotlin
            if (isAnyChecked) {
                val selectedSensitivities = ArrayList<String>()
                val customSens = ArrayList<String>()
                val predefinedChips = listOf(binding.chipDairy, binding.chipNuts, binding.chipGluten, binding.chipSulfites, binding.chipArtificialColors, binding.chipArtificialSweet)
                val predefinedNames = listOf("Dairy", "Nuts", "Gluten", "Sulfites", "Artificial Colors", "Artificial Sweeteners")

                for (i in predefinedChips.indices) {
                    if (predefinedChips[i].isChecked) {
                        selectedSensitivities.add(predefinedNames[i])
                    }
                }
                customSens.addAll(customSensitivities)

                val intent = Intent(this, ProfileSuccessActivity::class.java)
                val isEditing = getIntent().getBooleanExtra("IS_EDITING_PROFILE", false)
                intent.putExtra("IS_EDITING_PROFILE", isEditing)
                intent.putExtra("AGE_GROUP", getIntent().getStringExtra("AGE_GROUP"))
                intent.putStringArrayListExtra("CONDITIONS", getIntent().getStringArrayListExtra("CONDITIONS"))
                intent.putStringArrayListExtra("SENSITIVITIES", selectedSensitivities)
                intent.putStringArrayListExtra("CUSTOM_SENSITIVITIES", customSens)
                startActivity(intent)
                finish()
```

---

### Task 6.9: Wire `ProfileSuccessActivity.kt`

**File:** `app/src/main/java/com/simats/nutritrace/ProfileSuccessActivity.kt` (MODIFY)

Add API call to save health profile at the start of `onCreate`, after binding:

**After line 16 (`setContentView(binding.root)`), add:**
```kotlin
        // Send health profile to backend
        val ageGroup = intent.getStringExtra("AGE_GROUP") ?: "Adult"
        val conditions = intent.getStringArrayListExtra("CONDITIONS") ?: arrayListOf()
        val sensitivities = intent.getStringArrayListExtra("SENSITIVITIES") ?: arrayListOf()
        val customSensitivities = intent.getStringArrayListExtra("CUSTOM_SENSITIVITIES") ?: arrayListOf()

        val body = mapOf(
            "age_group" to ageGroup,
            "conditions" to conditions,
            "sensitivities" to sensitivities,
            "custom_sensitivities" to customSensitivities
        )

        ApiClient.postAuth(this, "/user/health-profile", body) { success, json ->
            runOnUiThread {
                if (!success) {
                    android.widget.Toast.makeText(this, "Failed to save health profile", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
```

Also update the "Return to Sign In" button (line 34) — after first-time signup, navigate to LoginActivity to log in:
Already navigates correctly. No change needed.

---

### Task 6.10: Wire `HomeActivity.kt`

**File:** `app/src/main/java/com/simats/nutritrace/HomeActivity.kt` (MODIFY)

Replace the `onResume()` method that reads from SharedPreferences (lines 92-174) with an API call to `GET /dashboard`.

**Replace the entire `onResume()` method with:**
```kotlin
    override fun onResume() {
        super.onResume()
        loadDashboard()
    }

    private fun loadDashboard() {
        ApiClient.getAuth(this, "/dashboard") { success, json ->
            runOnUiThread {
                if (success && json?.get("success")?.asBoolean == true) {
                    val user = json.getAsJsonObject("user")
                    val scansArray = json.getAsJsonArray("latest_scans")

                    // Update user greeting if you have a greeting text view
                    // binding.tvGreeting.text = "Hello, ${user.get("fullname").asString}"

                    val scanList = mutableListOf<ScanData>()
                    for (i in 0 until scansArray.size()) {
                        val s = scansArray[i].asJsonObject
                        scanList.add(ScanData(
                            id = s.get("id").asInt,
                            productName = s.get("product_name")?.asString ?: "Unknown",
                            brandName = s.get("brand_name")?.asString ?: "",
                            score = s.get("score").asInt,
                            riskLevel = s.get("risk_level").asString,
                            time = s.get("scanned_at")?.asString ?: ""
                        ))
                    }

                    if (scanList.isNotEmpty()) {
                        binding.cardEmptyScanned.visibility = android.view.View.GONE
                        binding.llScannedItems.visibility = android.view.View.VISIBLE
                        binding.llScannedItems.removeAllViews()

                        for (scan in scanList) {
                            val itemView = layoutInflater.inflate(R.layout.item_home_scanned, binding.llScannedItems, false)
                            val tvName = itemView.findViewById<android.widget.TextView>(R.id.tvItemName)
                            val tvBrand = itemView.findViewById<android.widget.TextView>(R.id.tvItemBrand)
                            val tvScore = itemView.findViewById<android.widget.TextView>(R.id.tvScoreVal)
                            val tvLabel = itemView.findViewById<android.widget.TextView>(R.id.tvScoreLabel)
                            val ivItem = itemView.findViewById<android.widget.ImageView>(R.id.ivItem)

                            tvName.text = scan.productName
                            tvBrand.text = scan.brandName
                            tvScore.text = scan.score.toString()

                            when {
                                scan.score >= 70 -> {
                                    tvLabel.text = "SAFE"
                                    tvLabel.setBackgroundResource(R.drawable.bg_pill_low)
                                    tvLabel.setTextColor(android.graphics.Color.parseColor("#16B88A"))
                                    ivItem.setImageResource(R.drawable.ic_shield_check_outline)
                                }
                                scan.score >= 40 -> {
                                    tvLabel.text = "MODERATE"
                                    tvLabel.setBackgroundResource(R.drawable.bg_pill_moderate)
                                    tvLabel.setTextColor(android.graphics.Color.parseColor("#F5A623"))
                                    ivItem.setImageResource(R.drawable.ic_clock)
                                }
                                else -> {
                                    tvLabel.text = "HIGH"
                                    tvLabel.setBackgroundResource(R.drawable.bg_pill_high)
                                    tvLabel.setTextColor(android.graphics.Color.parseColor("#E74C3C"))
                                    ivItem.setImageResource(R.drawable.ic_shield_check_outline)
                                }
                            }

                            itemView.setOnClickListener {
                                val intent = android.content.Intent(this, AnalysisResultActivity::class.java)
                                intent.putExtra("SCAN_ID", scan.id)
                                startActivity(intent)
                            }

                            binding.llScannedItems.addView(itemView)
                        }

                        binding.llRiskPill.visibility = android.view.View.VISIBLE
                        binding.ivDialDial.visibility = android.view.View.VISIBLE
                        binding.tvEmptyRiskLabel.visibility = android.view.View.GONE
                        binding.tvScoreValue.text = scanList.first().score.toString()
                    } else {
                        binding.cardEmptyScanned.visibility = android.view.View.VISIBLE
                        binding.llScannedItems.visibility = android.view.View.GONE
                        binding.llRiskPill.visibility = android.view.View.GONE
                        binding.ivDialDial.visibility = android.view.View.GONE
                        binding.tvEmptyRiskLabel.visibility = android.view.View.VISIBLE
                        binding.tvScoreValue.text = "--"
                    }
                }
            }
        }
    }
```

---

### Task 6.11: Wire `AnalyzingActivity.kt`

**File:** `app/src/main/java/com/simats/nutritrace/AnalyzingActivity.kt` (MODIFY)

Replace the 3-second fake delay with actual API call to `POST /scan/analyze`.

**Replace the Handler/postDelayed block (lines 22-37) with:**
```kotlin
        val imageUriString = intent.getStringExtra("IMAGE_URI")
        if (imageUriString == null) {
            android.widget.Toast.makeText(this, "No image to analyze", android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Convert URI to File
        val uri = android.net.Uri.parse(imageUriString)
        val imageFile = try {
            if (uri.scheme == "file") {
                java.io.File(uri.path!!)
            } else {
                // Copy content URI to cache file
                val inputStream = contentResolver.openInputStream(uri)
                val tempFile = java.io.File(cacheDir, "scan_${System.currentTimeMillis()}.jpg")
                inputStream?.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }
                tempFile
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Failed to read image", android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        ApiClient.uploadImage(this, "/scan/analyze", imageFile) { success, json ->
            runOnUiThread {
                if (success && json?.get("success")?.asBoolean == true) {
                    val scanObj = json.getAsJsonObject("scan")
                    val scanId = scanObj.get("id").asInt

                    val resultIntent = Intent(this, AnalysisResultActivity::class.java)
                    resultIntent.putExtra("SCAN_ID", scanId)
                    resultIntent.putExtra("SCAN_JSON", json.toString())
                    resultIntent.putExtra("IMAGE_URI", imageUriString)
                    resultIntent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                    startActivity(resultIntent)
                    finish()
                } else {
                    val message = json?.get("message")?.asString ?: "Analysis failed"
                    android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
```

---

### Task 6.12: Wire `AnalysisResultActivity.kt`

**File:** `app/src/main/java/com/simats/nutritrace/AnalysisResultActivity.kt` (MODIFY)

Replace the hardcoded mock ingredients in `populateIngredients()` with real data from the API response.

**Replace `populateIngredients()` method (lines 156-206) with:**
```kotlin
    private fun populateIngredients() {
        val scanJsonStr = intent.getStringExtra("SCAN_JSON") ?: return
        val gson = com.google.gson.Gson()
        val jsonObj = gson.fromJson(scanJsonStr, com.google.gson.JsonObject::class.java)
        val scanObj = jsonObj.getAsJsonObject("scan")

        // Set product name and score
        val productName = scanObj.get("product_name")?.asString ?: "Unknown Product"
        binding.tvProductName.text = productName

        val score = scanObj.get("score")?.asInt ?: 0
        // If there's a score display, update it
        // binding.tvScore.text = score.toString()

        val ingredients = scanObj.getAsJsonArray("ingredients") ?: return

        for (i in 0 until ingredients.size()) {
            val ing = ingredients[i].asJsonObject
            val name = ing.get("ingredient_name")?.asString ?: ""
            val status = ing.get("status")?.asString ?: "SAFE"
            val reason = ing.get("reason")?.asString ?: ""

            val itemView = layoutInflater.inflate(R.layout.item_ingredient, binding.llIngredientList, false)

            val tvName = itemView.findViewById<TextView>(R.id.tvIngredientName)
            val tvBadge = itemView.findViewById<TextView>(R.id.tvBadge)
            val tvExplanation = itemView.findViewById<TextView>(R.id.tvExplanation)
            val ivExpand = itemView.findViewById<ImageView>(R.id.ivExpand)
            val clHeader = itemView.findViewById<View>(R.id.clHeader)

            tvName.text = name
            tvExplanation.text = reason

            when (status) {
                "SAFE" -> {
                    tvBadge.text = "✓ SAFE"
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_safe)
                    tvBadge.setTextColor(android.graphics.Color.parseColor("#059669"))
                }
                "CAUTION" -> {
                    tvBadge.text = "⚠ CAUTION"
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_caution)
                    tvBadge.setTextColor(android.graphics.Color.parseColor("#D97706"))
                }
                "AVOID" -> {
                    tvBadge.text = "✕ AVOID"
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_avoid)
                    tvBadge.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                }
            }

            var isExpanded = false
            clHeader.setOnClickListener {
                isExpanded = !isExpanded
                tvExplanation.visibility = if (isExpanded) View.VISIBLE else View.GONE
                ivExpand.setImageResource(if (isExpanded) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down)
            }

            binding.llIngredientList.addView(itemView)
        }

        // Populate overview tab
        val overview = scanObj.get("overview")?.asString ?: ""
        // If there's a tvOverviewText in llOverview, set it
        // binding.tvOverviewText.text = overview

        // Populate guidance tab
        val guidance = scanObj.getAsJsonArray("guidance")
        // If there's a guidance list view, populate it
    }
```

Also update the `btnSaveScan` click handler (lines 39-68) — the scan is already saved on the server during analysis, so this button now just navigates:

**Replace the btnSaveScan.setOnClickListener block with:**
```kotlin
        binding.btnSaveScan.setOnClickListener {
            startActivity(android.content.Intent(this, ScanHistoryActivity::class.java))
            finish()
        }
```

---

### Task 6.13: Wire `ScanHistoryActivity.kt`

**File:** `app/src/main/java/com/simats/nutritrace/ScanHistoryActivity.kt` (MODIFY)

Replace SharedPreferences reading (lines 94-166) with API call to `GET /scan/history`.

**Replace the block starting at line 94 (`val prefs = ...`) through line 166 (`}`) with:**
```kotlin
        loadHistory("")

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                loadHistory(s?.toString()?.trim() ?: "")
            }
        })
    }

    private fun loadHistory(search: String) {
        val endpoint = if (search.isEmpty()) "/scan/history" else "/scan/history?search=$search"

        ApiClient.getAuth(this, endpoint) { success, json ->
            runOnUiThread {
                if (success && json?.get("success")?.asBoolean == true) {
                    val scansArray = json.getAsJsonArray("scans")

                    if (scansArray.size() > 0) {
                        binding.llEmptyHistory.visibility = android.view.View.GONE
                        binding.llHistoryList.visibility = android.view.View.VISIBLE
                        binding.llSearchRow.visibility = android.view.View.VISIBLE
                        binding.llHistoryList.removeAllViews()

                        for (i in 0 until scansArray.size()) {
                            val s = scansArray[i].asJsonObject
                            val scanId = s.get("id").asInt
                            val productName = s.get("product_name")?.asString ?: "Unknown"
                            val brandName = s.get("brand_name")?.asString ?: ""
                            val score = s.get("score").asInt
                            val riskLevel = s.get("risk_level").asString

                            val itemView = layoutInflater.inflate(R.layout.item_scan_history, binding.llHistoryList, false)

                            val tvProductName = itemView.findViewById<android.widget.TextView>(R.id.tvProductName)
                            val tvBrandName = itemView.findViewById<android.widget.TextView>(R.id.tvBrandName)
                            val tvRiskBadge = itemView.findViewById<android.widget.TextView>(R.id.tvRiskBadge)

                            tvProductName.text = productName
                            tvBrandName.text = brandName

                            when {
                                score >= 70 -> {
                                    tvRiskBadge.text = "SAFE"
                                    tvRiskBadge.setBackgroundResource(R.drawable.bg_pill_safe)
                                    tvRiskBadge.setTextColor(android.graphics.Color.parseColor("#16B88A"))
                                }
                                score >= 40 -> {
                                    tvRiskBadge.text = "MODERATE"
                                    tvRiskBadge.setBackgroundResource(R.drawable.bg_pill_moderate)
                                    tvRiskBadge.setTextColor(android.graphics.Color.parseColor("#F5A623"))
                                }
                                else -> {
                                    tvRiskBadge.text = "HIGH"
                                    tvRiskBadge.setBackgroundResource(R.drawable.bg_pill_high)
                                    tvRiskBadge.setTextColor(android.graphics.Color.parseColor("#E74C3C"))
                                }
                            }

                            itemView.setOnClickListener {
                                // Load full scan detail from backend
                                ApiClient.getAuth(this, "/scan/$scanId") { detailSuccess, detailJson ->
                                    runOnUiThread {
                                        if (detailSuccess && detailJson != null) {
                                            val intent = android.content.Intent(this, AnalysisResultActivity::class.java)
                                            intent.putExtra("SCAN_ID", scanId)
                                            intent.putExtra("SCAN_JSON", detailJson.toString())
                                            startActivity(intent)
                                        }
                                    }
                                }
                            }

                            binding.llHistoryList.addView(itemView)
                        }
                    } else {
                        binding.llEmptyHistory.visibility = android.view.View.VISIBLE
                        binding.llHistoryList.visibility = android.view.View.GONE
                        binding.llSearchRow.visibility = android.view.View.GONE
                    }
                }
            }
        }
```

**Note:** Remove the closing `}` of the old `onCreate` if it's now inside `loadHistory`. Ensure proper bracket nesting — `loadHistory` is a new method, and `onCreate` ends after `binding.etSearch.addTextChangedListener`.

---

### Task 6.14: Wire `PersonalDetailsActivity.kt`

**File:** `app/src/main/java/com/simats/nutritrace/PersonalDetailsActivity.kt` (MODIFY)

Add API calls to load and save user profile.

**Add at the end of `onCreate()` (after the existing click listeners), before the closing `}`:**
```kotlin
        // Load profile from backend
        ApiClient.getAuth(this, "/user/profile") { success, json ->
            runOnUiThread {
                if (success && json?.get("success")?.asBoolean == true) {
                    val user = json.getAsJsonObject("user")
                    binding.etFullName.setText(user.get("fullname")?.asString ?: "")
                    binding.etPhone.setText(user.get("phone")?.asString ?: "")
                    binding.etEmail.setText(user.get("email")?.asString ?: "")
                }
            }
        }
```

**Replace the `saveChanges()` method (lines 65-80) with:**
```kotlin
    private fun saveChanges() {
        val body = mapOf(
            "fullname" to binding.etFullName.text.toString().trim(),
            "phone" to binding.etPhone.text.toString().trim(),
            "email" to binding.etEmail.text.toString().trim()
        )

        ApiClient.putAuth(this, "/user/profile", body) { success, json ->
            runOnUiThread {
                binding.etFullName.isEnabled = false
                binding.etPhone.isEnabled = false
                binding.etEmail.isEnabled = false

                val view = this.currentFocus
                if (view != null) {
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                }

                if (success && json?.get("success")?.asBoolean == true) {
                    Toast.makeText(this, "Changes saved successfully", Toast.LENGTH_SHORT).show()
                } else {
                    val message = json?.get("message")?.asString ?: "Failed to save changes"
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
```

---

### Task 6.15: Wire `ChangePasswordActivity.kt`

**File:** `app/src/main/java/com/simats/nutritrace/ChangePasswordActivity.kt` (MODIFY)

**Replace the `btnChangePassword` click handler (lines 71-108) with:**
```kotlin
        binding.btnChangePassword.setOnClickListener {
            val current = binding.etCurrentPassword.text.toString()
            val newPass = binding.etNewPassword.text.toString()
            val confirm = binding.etConfirmNewPassword.text.toString()

            binding.btnChangePassword.isEnabled = false

            ApiClient.postAuth(this, "/auth/change-password", mapOf(
                "current_password" to current,
                "new_password" to newPass,
                "confirm_password" to confirm
            )) { success, json ->
                runOnUiThread {
                    binding.btnChangePassword.isEnabled = true
                    if (success && json?.get("success")?.asBoolean == true) {
                        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password_success, null)
                        val dialog = android.app.AlertDialog.Builder(this)
                            .setView(dialogView)
                            .setCancelable(false)
                            .create()
                        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                        dialog.show()

                        Handler(Looper.getMainLooper()).postDelayed({
                            dialog.dismiss()
                            finish()
                        }, 2000)
                    } else {
                        val message = json?.get("message")?.asString ?: "Failed to change password"
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
```

---

### Task 6.16: Wire `ProfileActivity.kt` (Logout + Delete Account)

**File:** `app/src/main/java/com/simats/nutritrace/ProfileActivity.kt` (MODIFY)

**In `showLogoutDialog()`, replace the `btnLogout` click handler (lines 158-163) with:**
```kotlin
        btnLogout.setOnClickListener {
            dialog.dismiss()
            ApiClient.clearSession(this)
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
```

**In `showDeleteAccountDialog()`, replace the `btnDelete` click handler (lines 132-138) with:**
```kotlin
        btnDelete.setOnClickListener {
            // Note: The delete dialog uses "DELETE" text confirmation, not password.
            // We'll send a dummy password or modify the dialog to ask for password.
            // For now, use a simple approach:
            ApiClient.deleteAuth(this, "/user/account", mapOf("password" to "user-confirmed-delete")) { success, json ->
                runOnUiThread {
                    dialog.dismiss()
                    if (success) {
                        ApiClient.clearSession(this)
                        startActivity(Intent(this, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                        finish()
                    } else {
                        android.widget.Toast.makeText(this, "Failed to delete account", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
```

**Note:** The delete account dialog currently asks user to type "DELETE" to confirm, not their password. You may want to either:
1. Change the dialog to ask for their password instead (recommended for security), or
2. Use the password stored from login. For MVP, option 2 works.

---

### Task 6.17: Add Network Security Config for Android

**File:** `app/src/main/res/xml/network_security_config.xml` (NEW)

Required to allow HTTP (not HTTPS) connections to your local development server.

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">192.168.1.100</domain>
        <domain includeSubdomains="true">10.0.2.2</domain>
        <domain includeSubdomains="true">localhost</domain>
    </domain-config>
</network-security-config>
```

**Update `AndroidManifest.xml`** — add these two attributes to the `<application>` tag:

```xml
android:networkSecurityConfig="@xml/network_security_config"
android:usesCleartextTraffic="true"
```

Also add this permission if not already present (above `<application>`):
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

---

## Phase 7: Final Verification

### Task 7.1: Backend Smoke Test

1. Start XAMPP MySQL
2. `cd backend && pip install -r requirements.txt && python main.py`
3. Test signup: `curl -X POST http://localhost:5000/auth/signup -H "Content-Type: application/json" -d '{"fullname":"Test User","phone":"1234567890","email":"test@test.com","password":"Test123!","confirm_password":"Test123!"}'`
4. Test login: `curl -X POST http://localhost:5000/auth/login -H "Content-Type: application/json" -d '{"email":"test@test.com","password":"Test123!"}'`
5. Copy the token from login response
6. Test dashboard: `curl http://localhost:5000/dashboard -H "Authorization: Bearer <token>"`

### Task 7.2: Android Integration Test

1. Update `BASE_URL` in `ApiClient.kt` to your machine's local IP (run `ipconfig`)
2. Update IP in `network_security_config.xml`
3. Build and run on device/emulator
4. Test the full flow: Register → Health Setup → Login → Home → Scan → History

---

## Summary of All Files

### Backend Files (13 files to create/modify)
| File | Action | Purpose |
|------|--------|---------|
| `backend/config.py` | CREATE | Environment variables |
| `backend/.env` | CREATE | Secret values |
| `backend/database.py` | REPLACE | 8 tables, drop & recreate |
| `backend/middleware.py` | CREATE | @token_required JWT decorator |
| `backend/validators.py` | CREATE | Input validation helpers |
| `backend/requirements.txt` | REPLACE | All Python dependencies |
| `backend/email_service.py` | CREATE | Gmail SMTP OTP sending |
| `backend/auth.py` | CREATE | 6 auth endpoints |
| `backend/user.py` | CREATE | 4 user/profile endpoints |
| `backend/ocr_service.py` | CREATE | Google Cloud Vision OCR |
| `backend/ai_service.py` | CREATE | Gemini 2.0 Flash analysis |
| `backend/scan.py` | CREATE | 6 scan endpoints |
| `backend/compare.py` | CREATE | Compare endpoint |
| `backend/dashboard.py` | CREATE | Dashboard endpoint |
| `backend/main.py` | REPLACE | App entry point, route registration |

### Android Files (to create/modify)
| File | Action | Purpose |
|------|--------|---------|
| `ApiClient.kt` | CREATE | HTTP client singleton |
| `ScanData.kt` | MODIFY | Add id and imagePath fields |
| `RegisterActivity.kt` | MODIFY | Wire to POST /auth/signup |
| `LoginActivity.kt` | MODIFY | Wire to POST /auth/login |
| `ForgotPasswordActivity.kt` | MODIFY | Wire to POST /auth/forgot-password |
| `VerifyOtpActivity.kt` | MODIFY | Wire to POST /auth/verify-otp |
| `NewPasswordActivity.kt` | MODIFY | Wire to POST /auth/reset-password |
| `AgeSelectionActivity.kt` | MODIFY | Pass age_group via Intent |
| `HealthSelectionActivity.kt` | MODIFY | Pass conditions via Intent |
| `SensitivitySelectionActivity.kt` | MODIFY | Pass sensitivities via Intent |
| `ProfileSuccessActivity.kt` | MODIFY | Call POST /user/health-profile |
| `HomeActivity.kt` | MODIFY | Wire to GET /dashboard |
| `AnalyzingActivity.kt` | MODIFY | Wire to POST /scan/analyze |
| `AnalysisResultActivity.kt` | MODIFY | Display real API data |
| `ScanHistoryActivity.kt` | MODIFY | Wire to GET /scan/history |
| `PersonalDetailsActivity.kt` | MODIFY | Wire to GET/PUT /user/profile |
| `ChangePasswordActivity.kt` | MODIFY | Wire to POST /auth/change-password |
| `ProfileActivity.kt` | MODIFY | Wire logout + delete account |
| `network_security_config.xml` | CREATE | Allow HTTP for dev |
| `AndroidManifest.xml` | MODIFY | Add network config |

### Execution Order
1. Phase 1 (Tasks 1.1-1.6): Backend foundation
2. Phase 2 (Tasks 2.1-2.2): Auth module
3. Phase 3 (Task 3.1): User/profile module
4. Phase 4 (Tasks 4.1-4.3): Scan & AI module
5. Phase 5 (Tasks 5.1-5.3): Compare, dashboard, main.py
6. Phase 6 (Tasks 6.1-6.17): Android frontend wiring
7. Phase 7 (Tasks 7.1-7.2): Verification
