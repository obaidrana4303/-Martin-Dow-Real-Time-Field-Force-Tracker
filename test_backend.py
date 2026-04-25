import unittest
import json
import sqlite3
import os
from app import app

# Database path (same as in app.py)
DB_PATH = 'locations.db'

class FlaskBackendTest(unittest.TestCase):

    def setUp(self):
        # Create a test client
        self.client = app.test_client()
        self.client.testing = True
        
        # Reset the database before each test
        conn = sqlite3.connect(DB_PATH)
        conn.execute('DELETE FROM locations')
        conn.commit()
        conn.close()

    def test_get_all_locations_empty(self):
        # Call before any data is posted
        response = self.client.get('/get-all-locations')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.get_json(), [])

    def test_update_location_valid(self):
        # Send correct JSON with all fields
        payload = {
            "device_id": "abc123",
            "lat": 24.8607,
            "lon": 67.0011,
            "mode": "gps",
            "timestamp": "2026-03-27T10:30:00"
        }
        response = self.client.post('/update-location', 
                                   data=json.dumps(payload),
                                   content_type='application/json')
        
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.get_json()['status'], 'ok')

    def test_update_location_missing_fields(self):
        # Missing lat
        payload = {"device_id": "abc123", "lon": 67.0011, "mode": "gps", "timestamp": "..."}
        response = self.client.post('/update-location', 
                                   data=json.dumps(payload),
                                   content_type='application/json')
        self.assertEqual(response.status_code, 400)

        # Missing device_id
        payload = {"lat": 24.86, "lon": 67.00, "mode": "gps", "timestamp": "..."}
        response = self.client.post('/update-location', 
                                   data=json.dumps(payload),
                                   content_type='application/json')
        self.assertEqual(response.status_code, 400)

        # Empty JSON
        response = self.client.post('/update-location', 
                                   data=json.dumps({}),
                                   content_type='application/json')
        self.assertEqual(response.status_code, 400)

    def test_update_existing_device(self):
        # Post first location
        p1 = {"device_id": "phone_001", "lat": 1.0, "lon": 1.0, "mode": "gps", "timestamp": "t1"}
        self.client.post('/update-location', data=json.dumps(p1), content_type='application/json')

        # Post update for SAME device
        p2 = {"device_id": "phone_001", "lat": 2.2, "lon": 2.2, "mode": "network", "timestamp": "t2"}
        self.client.post('/update-location', data=json.dumps(p2), content_type='application/json')

        # Verify only one entry exists and it has updated values
        response = self.client.get('/get-all-locations')
        data = response.get_json()
        self.assertEqual(len(data), 1)
        self.assertEqual(data[0]['lat'], 2.2)
        self.assertEqual(data[0]['mode'], 'network')

    def test_multiple_devices(self):
        # Post locations for 3 different devices
        devices = ["d1", "d2", "d3"]
        for d in devices:
            p = {"device_id": d, "lat": 0, "lon": 0, "mode": "test", "timestamp": "t"}
            self.client.post('/update-location', data=json.dumps(p), content_type='application/json')

        response = self.client.get('/get-all-locations')
        data = response.get_json()
        self.assertEqual(len(data), 3)
        
        # Verify all device IDs are present
        returned_ids = [item['device_id'] for item in data]
        for d in devices:
            self.assertIn(d, returned_ids)

    def test_cors_headers(self):
        # Send request with Origin header
        response = self.client.get('/get-all-locations', headers={"Origin": "http://localhost:8000"})
        # Verify Access-Control-Allow-Origin is present
        self.assertIn('Access-Control-Allow-Origin', response.headers)

    def test_sequential_simulated_concurrent(self):
        # Send 10 POST requests for different devices
        for i in range(10):
            p = {"device_id": f"dev_{i}", "lat": float(i), "lon": float(i), "mode": "test", "timestamp": "t"}
            self.client.post('/update-location', data=json.dumps(p), content_type='application/json')
        
        response = self.client.get('/get-all-locations')
        self.assertEqual(len(response.get_json()), 10)

if __name__ == '__main__':
    unittest.main()
