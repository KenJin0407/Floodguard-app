# 🌊 FloodGuard FastAPI Backend Server

This is the standalone Python FastAPI REST API Backend Server for the **FloodGuard** Android application.

## 🚀 Quick Start Instructions

### 1. Prerequisites
- Python 3.9 or higher installed on your system.

### 2. Installation
Open a terminal in the `backend` folder and run:
```bash
cd backend
pip install -r requirements.txt
```

### 3. Run the Server
To start the backend server locally on `http://localhost:3000` (or `127.0.0.1:3000`):
```bash
uvicorn main:app --reload --port 3000
```

---

## 📡 API Endpoints

### 🔐 User Authentication
- `POST /api/auth/register` — Register a new user account.
- `POST /api/auth/login` — Authenticate user credentials.
- `PUT /api/auth/profile` — Update user profile details.
- `GET /api/users` — Fetch all registered users.

### 🌊 Flood Reports
- `GET /api/reports` — Fetch all community flood reports.
- `POST /api/reports` — Submit a new flood report.
- `PUT /api/reports/{id}` — Edit an existing flood report.
- `DELETE /api/reports/{id}` — Delete a flood report.

---

## 📱 Connecting Android App
On Android Emulator, `localhost` maps to `10.0.2.2`.
The Android app's `FloodGuardApiService.kt` is automatically configured to connect to `http://10.0.2.2:3000/api`.
