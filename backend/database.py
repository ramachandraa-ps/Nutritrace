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

    # Create tables only if they don't exist
    tables = [
        """
        CREATE TABLE IF NOT EXISTS users (
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
        CREATE TABLE IF NOT EXISTS health_profiles (
            id INT AUTO_INCREMENT PRIMARY KEY,
            user_id INT NOT NULL UNIQUE,
            age_group ENUM('Child', 'Teen', 'Adult', 'Senior') NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS user_health_conditions (
            id INT AUTO_INCREMENT PRIMARY KEY,
            user_id INT NOT NULL,
            condition_key VARCHAR(100) NOT NULL,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            UNIQUE(user_id, condition_key)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS user_sensitivities (
            id INT AUTO_INCREMENT PRIMARY KEY,
            user_id INT NOT NULL,
            sensitivity VARCHAR(255) NOT NULL,
            is_custom BOOLEAN DEFAULT FALSE,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            UNIQUE(user_id, sensitivity)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS scans (
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
        CREATE TABLE IF NOT EXISTS scan_ingredients (
            id INT AUTO_INCREMENT PRIMARY KEY,
            scan_id INT NOT NULL,
            ingredient_name VARCHAR(255) NOT NULL,
            status ENUM('SAFE', 'CAUTION', 'AVOID') NOT NULL,
            reason TEXT,
            FOREIGN KEY (scan_id) REFERENCES scans(id) ON DELETE CASCADE
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS comparisons (
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
        CREATE TABLE IF NOT EXISTS password_reset_otps (
            id INT AUTO_INCREMENT PRIMARY KEY,
            user_id INT NOT NULL,
            otp_code VARCHAR(255) NOT NULL,
            expires_at TIMESTAMP NOT NULL,
            is_used BOOLEAN DEFAULT FALSE,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS signup_otps (
            id INT AUTO_INCREMENT PRIMARY KEY,
            email VARCHAR(255) NOT NULL,
            otp_code VARCHAR(10) NOT NULL,
            expires_at TIMESTAMP NOT NULL,
            is_used BOOLEAN DEFAULT FALSE,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        """
    ]

    for sql in tables:
        cursor.execute(sql)

    # Migration: fix otp_code column size if it was created with VARCHAR(6)
    try:
        cursor.execute("ALTER TABLE password_reset_otps MODIFY otp_code VARCHAR(255) NOT NULL")
    except Exception:
        pass  # Already correct size or table just created

    conn.commit()
    cursor.close()
    conn.close()
    print("All 9 tables ready.")
