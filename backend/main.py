from flask import Flask, request, jsonify
from database import init_db, get_db_connection
import re

app = Flask(__name__)

# Initialize the database and tables when the app starts up
init_db()

def is_valid_email(email):
    """Simple regex for validating an email address format."""
    pattern = r'^[\w\.-]+@[\w\.-]+\.\w+$'
    return re.match(pattern, email) is not None

@app.route('/signup', methods=['POST'])
def signup():
    # 1. Receive JSON data from the frontend
    data = request.get_json()
    if not data:
        return jsonify({"success": False, "message": "No data provided"}), 400

    # 2. Extract fields
    fullname = data.get('fullname')
    phone = data.get('phone')
    email = data.get('email')
    password = data.get('password')
    confirm_password = data.get('confirm_password')

    # 3. Validate ALL fields are required
    if not all([fullname, phone, email, password, confirm_password]):
        return jsonify({"success": False, "message": "All fields are required"}), 400

    # 4. Validate email format
    if not is_valid_email(email):
        return jsonify({"success": False, "message": "Invalid email format"}), 400

    # 5. Validate Password and Confirm Password match
    if password != confirm_password:
        return jsonify({"success": False, "message": "Passwords do not match"}), 400

    # 6. Save user to MySQL database
    conn = get_db_connection()
    if conn is None:
        return jsonify({"success": False, "message": "Database connection error"}), 500
        
    cursor = conn.cursor()
    
    try:
        # Check if email is already registered (unique email validator)
        cursor.execute("SELECT email FROM users WHERE email = %s", (email,))
        if cursor.fetchone():
            return jsonify({"success": False, "message": "Email is already registered"}), 400

        # Insert new user into the database
        insert_query = """
        INSERT INTO users (fullname, phone, email, password)
        VALUES (%s, %s, %s, %s)
        """
        # NOTE: For beginner simplicity, we are saving password in plain text.
        # In a real-world secure app, you should hash the password before saving!
        cursor.execute(insert_query, (fullname, phone, email, password))
        conn.commit()
        
        # Return success response exactly matched to expectations
        return jsonify({
            "success": True, 
            "message": "Account created successfully!"
        }), 201
        
    except Exception as e:
        return jsonify({"success": False, "message": f"An error occurred: {str(e)}"}), 500
    finally:
        # Always close connection to avoid memory leaks
        cursor.close()
        conn.close()

if __name__ == '__main__':
    # Start the Flask app
    app.run(host='0.0.0.0', port=5000, debug=True)
