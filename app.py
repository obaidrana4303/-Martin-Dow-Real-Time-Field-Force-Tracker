import sqlite3
import os
from flask import Flask, request, jsonify
from flask_cors import CORS

app = Flask(__name__)
# Enable CORS so the frontend dashboard can access this API
CORS(app)

# Detect if running on local PC or PythonAnywhere
if os.path.exists('/home/obaid4303'):
    DB_PATH = '/home/obaid4303/locations.db'
else:
    DB_PATH = 'locations.db'
DASHBOARD_PIN = "1234"  # Default PIN

def get_db_connection():
    """Helper to get a fresh connection for each request (thread-safe)."""
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row  # Access columns by name
    return conn

def init_db():
    """Create the table if it doesn't exist and ensure username column exists."""
    conn = get_db_connection()
    # Create locations table
    conn.execute('''
        CREATE TABLE IF NOT EXISTS locations (
            device_id TEXT PRIMARY KEY,
            username TEXT,
            lat REAL,
            lon REAL,
            mode TEXT,
            timestamp TEXT
        )
    ''')

    # Check if username column exists
    cursor = conn.execute("PRAGMA table_info(locations)")
    columns = [row[1] for row in cursor.fetchall()]
    if 'username' not in columns:
        conn.execute("ALTER TABLE locations ADD COLUMN username TEXT")

    # Create objectives table
    conn.execute('''
        CREATE TABLE IF NOT EXISTS objectives (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            device_id TEXT,
            username TEXT,
            date TEXT,
            trip_type TEXT,
            trip_description TEXT,
            timestamp TEXT
        )
    ''')

    # Check if trip_description column exists
    cursor = conn.execute("PRAGMA table_info(objectives)")
    columns = [row[1] for row in cursor.fetchall()]
    if 'trip_description' not in columns:
        conn.execute("ALTER TABLE objectives ADD COLUMN trip_description TEXT")

    # Create doctor_visits table
    conn.execute('''
        CREATE TABLE IF NOT EXISTS doctor_visits (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            objective_id INTEGER,
            doctor_name TEXT,
            location_name TEXT,
            lat REAL,
            lon REAL,
            FOREIGN KEY (objective_id) REFERENCES objectives(id)
        )
    ''')

    conn.commit()
    conn.close()

# Initialize database on startup
init_db()

@app.route('/')
def index():
    return jsonify({
        "status": "online",
        "message": "Real-time Location Tracker API is running"
    })

@app.route('/verify-pin', methods=['POST'])
def verify_pin():
    """Verify the PIN entered on the dashboard."""
    data = request.json
    if data and data.get('pin') == DASHBOARD_PIN:
        return jsonify({"status": "success", "message": "PIN verified"}), 200
    return jsonify({"status": "error", "message": "Invalid PIN"}), 401

@app.route('/update-location', methods=['POST'])
def update_location():
    """Receive location from Android app and save to SQLite."""
    data = request.json
    print(f"[*] [DEBUG] Incoming request: {data}")
    if not data:
        print("[-] [ERROR] No data received in request.")
        return jsonify({"error": "No data received"}), 400

    required_fields = ["device_id", "username", "lat", "lon", "mode", "timestamp"]
    for field in required_fields:
        if field not in data:
            return jsonify({"error": f"Missing field: {field}"}), 400

    try:
        conn = get_db_connection()
        # UPSERT: Insert or update if device_id already exists
        conn.execute('''
            INSERT OR REPLACE INTO locations (device_id, username, lat, lon, mode, timestamp)
            VALUES (?, ?, ?, ?, ?, ?)
        ''', (data['device_id'], data['username'], data['lat'], data['lon'], data['mode'], data['timestamp']))
        conn.commit()
        conn.close()
        print(f"[*] Update for user {data['username']} (Device: {data['device_id']})")
        return jsonify({"status": "ok"}), 200
    except Exception as e:
        print(f"[-] Database Error: {e}")
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/get-all-locations', methods=['GET'])
def get_all_locations():
    """Return all stored locations as a JSON list for the dashboard."""
    try:
        conn = get_db_connection()
        rows = conn.execute('SELECT * FROM locations').fetchall()
        conn.close()
        
        locations_list = [dict(row) for row in rows]
        return jsonify(locations_list), 200
    except Exception as e:
        print(f"[-] Database Error: {e}")
        return jsonify([]), 500

@app.route('/submit-objective', methods=['POST'])
def submit_objective():
    """Receive day objective and doctor visits from Android app."""
    data = request.json
    if not data:
        return jsonify({"error": "No data received"}), 400

    required_fields = ["device_id", "username", "date", "trip_type", "timestamp", "doctors"]
    for field in required_fields:
        if field not in data:
            return jsonify({"error": f"Missing field: {field}"}), 400

    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        
        # Insert into objectives
        cursor.execute('''
            INSERT INTO objectives (device_id, username, date, trip_type, trip_description, timestamp)
            VALUES (?, ?, ?, ?, ?, ?)
        ''', (data['device_id'], data['username'], data['date'], data['trip_type'], data.get('trip_description', ''), data['timestamp']))
        
        objective_id = cursor.lastrowid
        
        # Insert into doctor_visits
        for doc in data['doctors']:
            cursor.execute('''
                INSERT INTO doctor_visits (objective_id, doctor_name, location_name, lat, lon)
                VALUES (?, ?, ?, ?, ?)
            ''', (objective_id, doc.get('name'), doc.get('location_name'), doc.get('lat'), doc.get('lon')))
            
        conn.commit()
        conn.close()
        return jsonify({"status": "ok", "objective_id": objective_id}), 201
    except Exception as e:
        print(f"[-] Database Error: {e}")
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/get-objectives', methods=['GET'])
def get_objectives():
    """Return filtered objectives with associated doctor visits."""
    date_filter = request.args.get('date')
    device_filter = request.args.get('device_id')
    
    try:
        conn = get_db_connection()
        query = "SELECT * FROM objectives WHERE 1=1"
        params = []
        if date_filter:
            query += " AND date = ?"
            params.append(date_filter)
        if device_filter:
            query += " AND device_id = ?"
            params.append(device_filter)
            
        rows = conn.execute(query, params).fetchall()
        
        objectives_list = []
        for row in rows:
            obj = dict(row)
            # Fetch doctors for this objective
            doc_rows = conn.execute('SELECT doctor_name as name, location_name, lat, lon FROM doctor_visits WHERE objective_id = ?', (obj['id'],)).fetchall()
            obj['doctors'] = [dict(dr) for dr in doc_rows]
            objectives_list.append(obj)
            
        conn.close()
        return jsonify(objectives_list), 200
    except Exception as e:
        print(f"[-] Database Error: {e}")
        return jsonify([]), 500

@app.route('/get-objectives-summary', methods=['GET'])
def get_objectives_summary():
    """Return a grouped summary of objectives by date."""
    try:
        conn = get_db_connection()
        # Get unique dates
        date_rows = conn.execute('SELECT DISTINCT date FROM objectives ORDER BY date DESC').fetchall()
        
        summary = []
        for dr in date_rows:
            date_val = dr['date']
            
            # Get total users (unique device_id or username)
            user_count = conn.execute('SELECT COUNT(DISTINCT device_id) FROM objectives WHERE date = ?', (date_val,)).fetchone()[0]
            
            # Get total doctors visited (count from doctor_visits joined with objectives)
            doc_count = conn.execute('''
                SELECT COUNT(*) FROM doctor_visits dv 
                JOIN objectives o ON dv.objective_id = o.id 
                WHERE o.date = ?
            ''', (date_val,)).fetchone()[0]
            
            # Get breakdown of users
            users_in_day = conn.execute('''
                SELECT username, trip_type, trip_description, (SELECT COUNT(*) FROM doctor_visits WHERE objective_id = o.id) as doctors_count
                FROM objectives o WHERE date = ?
            ''', (date_val,)).fetchall()
            
            summary.append({
                "date": date_val,
                "total_users": user_count,
                "total_doctors_visited": doc_count,
                "users": [dict(u) for u in users_in_day]
            })
            
        conn.close()
        return jsonify(summary), 200
    except Exception as e:
        print(f"[-] Database Error: {e}")
        return jsonify([]), 500

if __name__ == '__main__':
    print("[*] Starting Flash backend on port 5000...")
    app.run(host='0.0.0.0', port=5000, debug=True)
