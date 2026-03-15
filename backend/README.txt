Nutritrace Flask Backend
------------------------

Prerequisites:
- Python 3.x
- XAMPP (with MySQL running on port 3306)

Setup:
1. Open terminal or command prompt in this 'backend' folder.
2. (Optional) Create and activate a virtual environment:
   python -m venv venv
   venv\Scripts\activate
3. Install dependencies:
   pip install -r requirements.txt
4. Run the backend server:
   python main.py

The server will automatically try to create the 'nutritrace_db' database and 'users' table upon starting.
It will run locally on http://127.0.0.1:5000.
