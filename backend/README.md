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
- `POST /api/reports` — Submit a new flood report and automatically send a Firebase push notification to the `floodguard` topic.
- `PUT /api/reports/{id}` — Edit an existing flood report.
- `DELETE /api/reports/{id}` — Delete a flood report.

## 🔔 Push Notifications

Push notifications are delivered through Firebase Cloud Messaging (FCM); they are not carrier SMS. Create a Firebase project, add the Android app with application ID `com.example.floodguard`, and place its `google-services.json` in the `app` folder. The Google Services Gradle plugin is enabled when that file is present.

Create a Firebase service-account key with permission to send FCM messages. Keep the key outside the repository and configure the backend environment:

```powershell
$env:GOOGLE_APPLICATION_CREDENTIALS = "C:\path\to\firebase-service-account.json"
$env:FIREBASE_PROJECT_ID = "your-firebase-project-id"
```

The Android app subscribes to the `floodguard` topic when a user opens the dashboard. While the app is open, a push appears as a floating alert; in the background, Android displays it as a system notification. Android notification permission must be granted.

Run the app against the same publicly reachable HTTPS backend for all devices. From the repository root, build it with:

```powershell
.\gradlew.bat :app:assembleDebug -PfloodguardApiUrl=https://your-host.example/api
```

The default API URL remains `http://10.0.2.2:3000/api` for Android Emulator development. If Firebase credentials are missing or sending fails, the report is still saved and the API response marks `notification.accepted` as `false`.

---

## 📱 Connecting Android App
On Android Emulator, `localhost` maps to `10.0.2.2`.
The Android app defaults to `http://10.0.2.2:3000/api`; use the `floodguardApiUrl` Gradle property above to configure a backend reachable by physical devices.
