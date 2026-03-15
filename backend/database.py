import mysql.connector

DB_HOST = "localhost"
DB_USER = "root"
DB_PASSWORD = ""
DB_NAME = "nutritrace_db"

def get_db_connection(include_db=True):
    """
    Connects to the MySQL server.
    If include_db is False, it connects without selecting a database 
    so we can create it if it doesn't exist.
    """
    try:
        connection = mysql.connector.connect(
            host=DB_HOST,
            user=DB_USER,
            password=DB_PASSWORD,
            database=DB_NAME if include_db else None
        )
        return connection
    except mysql.connector.Error as err:
        print(f"Database connection error: {err}")
        return None

def init_db():
    """
    Creates the database and the users table if they do not exist.
    """
    # 1. Connect without selecting database to create it if needed
    conn = get_db_connection(include_db=False)
    if conn is None:
        print("Failed to connect to MySQL server. Please ensure XAMPP MySQL is running.")
        return
        
    cursor = conn.cursor()
    try:
        # Create database if it does not exist
        cursor.execute(f"CREATE DATABASE IF NOT EXISTS {DB_NAME}")
        print(f"Database '{DB_NAME}' checked/created successfully.")
    except mysql.connector.Error as err:
        print(f"Failed creating database: {err}")
    finally:
        cursor.close()
        conn.close()

    # 2. Connect with the new database selected to create tables
    conn = get_db_connection(include_db=True)
    if conn is None:
        return
        
    cursor = conn.cursor()
    # Create users table if it does not exist
    create_table_query = """
    CREATE TABLE IF NOT EXISTS users (
        id INT AUTO_INCREMENT PRIMARY KEY,
        fullname VARCHAR(255) NOT NULL,
        phone VARCHAR(50) NOT NULL,
        email VARCHAR(255) NOT NULL UNIQUE,
        password VARCHAR(255) NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )
    """
    try:
        cursor.execute(create_table_query)
        conn.commit()
        print("Table 'users' checked/created successfully.")
    except mysql.connector.Error as err:
        print(f"Failed creating table: {err}")
    finally:
        cursor.close()
        conn.close()
