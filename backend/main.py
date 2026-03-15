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
