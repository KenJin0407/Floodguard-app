# 🌊 FloodGuard Backend Server

This is the standalone REST API Backend Server for the **FloodGuard** Android application.

## 🚀 Quick Start Instructions

### 1. Prerequisites
- Node.js (v14 or higher) installed on your system.

### 2. Installation
Open a terminal in the `backend` folder and run:
```bash
cd backend
npm install
```

### 3. Run the Server
To start the backend server locally on `http://localhost:3000`:
```bash
npm start
```

For development mode with auto-reload:
```bash
npm run dev
```

---

## 📡 API Endpoints

### 🔐 User Authentication
- `POST /api/auth/register` — Register a new user account.
  ```json
  {
    "fullName": "Juan Dela Cruz",
    "gender": "Male",
    "mobile": "+63 915 245 6879",
    "email": "juan@example.com",
    "username": "juan",
    "password": "password123"
  }
  ```
- `POST /api/auth/login` — Authenticate user credentials.
  ```json
  {
    "username": "juan",
    "password": "password123"
  }
  ```

### 🌊 Flood Reports
- `GET /api/reports` — Fetch all community flood reports.
- `POST /api/reports` — Submit a new flood report.
  ```json
  {
    "location": "España Blvd.",
    "severity": "Waist-Deep",
    "notes": "Water level exceeded critical threshold"
  }
  ```
- `PUT /api/reports/:id` — Edit an existing flood report.
- `DELETE /api/reports/:id` — Delete a flood report.

---

## 📱 Connecting Android App
On Android Emulator, `localhost` maps to `10.0.2.2`.
Set the base URL in `FloodGuardApiService.kt` to:
`http://10.0.2.2:3000/api`
