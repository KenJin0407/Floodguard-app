"""
FloodGuard Cloud Database Sync Tool
Downloads live users and reports from Render Cloud and synchronizes with local SQLite database (floodguard.db)
"""
import urllib.request
import json
import sqlite3
import os
import sys

BASE_URL = "https://floodguard-app-90dp.onrender.com/api"
DB_PATH = os.path.join(os.path.dirname(__file__), "floodguard.db")

def fetch_json(endpoint):
    url = f"{BASE_URL}{endpoint}"
    print(f"[>] Fetching live data from {url}...")
    req = urllib.request.Request(url, headers={"User-Agent": "FloodGuard-Sync/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            return json.loads(response.read().decode("utf-8"))
    except Exception as e:
        print(f"[X] Failed to reach cloud server: {e}")
        print("[!] Paalala: Kung kakagising lang ng Render server, subukan ulit pagkatapos ng 30 segundo.")
        return None

def sync_database():
    print("=" * 60)
    print(">>> FLOODGUARD CLOUD DATABASE SYNC <<<")
    print(f"Target local database: {DB_PATH}")
    print("=" * 60)

    # 1. Fetch live data
    users_data = fetch_json("/users")
    reports_data = fetch_json("/reports")

    if users_data is None or reports_data is None:
        print("[!] Sync aborted. Unable to download data from cloud.")
        return

    users_list = users_data.get("users", [])
    reports_list = reports_data.get("reports", [])

    print(f"[OK] Downloaded {len(users_list)} users and {len(reports_list)} flood reports from Render Cloud!")

    # 2. Update local SQLite DB
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()

    # Sync Users
    users_synced = 0
    for u in users_list:
        fullname = u.get("fullName", "")
        gender = u.get("gender", "Male")
        mobile = u.get("mobile", "")
        username = u.get("username", "")

        c.execute("SELECT id FROM users WHERE username = ? OR (mobile = ? AND mobile != '')", (username, mobile))
        existing = c.fetchone()
        if existing:
            c.execute("""
                UPDATE users SET fullName = ?, gender = ?, mobile = ? WHERE id = ?
            """, (fullname, gender, mobile, existing[0]))
        else:
            c.execute("""
                INSERT INTO users (fullName, gender, mobile, email, username, password, isVerified)
                VALUES (?, ?, ?, '', ?, 'SHA256_HASHED_SECURE', 1)
            """, (fullname, gender, mobile, username))
        users_synced += 1

    # Sync Reports
    reports_synced = 0
    for r in reports_list:
        rid = str(r.get("id"))
        loc = r.get("location", "")
        sev = r.get("severity", "")
        status = r.get("statusText", "")
        notes = r.get("notes", "")
        ts = r.get("timestamp", 0)
        sev_lvl = r.get("severityLevel", "NOT_PASSABLE")
        photo = r.get("photoUri", "")
        lat = r.get("lat", 14.6958)
        lon = r.get("lon", 121.1207)
        rep_user = r.get("reporterUsername", "")
        rep_name = r.get("reporterName", "")

        c.execute("SELECT id FROM reports WHERE id = ?", (rid,))
        if c.fetchone():
            c.execute("""
                UPDATE reports SET location=?, severity=?, statusText=?, notes=?, timestamp=?,
                severityLevel=?, photoUri=?, lat=?, lon=?, reporterUsername=?, reporterName=?
                WHERE id=?
            """, (loc, sev, status, notes, ts, sev_lvl, photo, lat, lon, rep_user, rep_name, rid))
        else:
            c.execute("""
                INSERT INTO reports (id, location, severity, statusText, notes, timestamp, severityLevel, photoUri, lat, lon, reporterUsername, reporterName)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (rid, loc, sev, status, notes, ts, sev_lvl, photo, lat, lon, rep_user, rep_name))
        reports_synced += 1

    conn.commit()
    conn.close()

    print(f"[SUCCESS] Tagumpay! Na-sync ang {users_synced} users at {reports_synced} reports sa inyong local 'floodguard.db'!")
    print("Pwede mo na itong buksan at i-refresh sa inyong SQLite Viewer!")
    print("=" * 60)

if __name__ == "__main__":
    sync_database()
