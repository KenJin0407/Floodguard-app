#!/usr/bin/env python3
import os
import time
import json
import uuid
import logging
import uvicorn
from typing import Optional
import firebase_admin
from firebase_admin import credentials, messaging
from firebase_admin.exceptions import FirebaseError
from pydantic import BaseModel
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

logger = logging.getLogger(__name__)
app = FastAPI(title="FloodGuard FastAPI Backend", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

DATA_FILE = os.path.join(os.path.dirname(__file__), "data_store.json")

def load_data():
    if not os.path.exists(DATA_FILE):
        return {"users": [], "reports": [], "broadcasts": []}
    with open(DATA_FILE, "r") as f:
        try:
            d = json.load(f)
            if "broadcasts" not in d:
                d["broadcasts"] = []
            return d
        except json.JSONDecodeError:
            return {"users": [], "reports": [], "broadcasts": []}

def save_data(data):
    with open(DATA_FILE, "w") as f:
        json.dump(data, f, indent=2)

class UserRegister(BaseModel):
    fullName: str
    gender: Optional[str] = "Male"
    mobile: Optional[str] = None
    email: str
    username: str
    password: str

class UserLogin(BaseModel):
    username: str
    password: str

class ProfileUpdate(BaseModel):
    username: str
    fullName: Optional[str] = None
    gender: Optional[str] = None
    mobile: Optional[str] = None
    email: Optional[str] = None

class FloodReportCreate(BaseModel):
    id: Optional[str] = None
    location: str
    severity: str
    statusText: Optional[str] = "Active"
    notes: Optional[str] = ""
    timestamp: Optional[int] = None
    severityLevel: Optional[str] = "NOT_PASSABLE"
    photoUri: Optional[str] = None
    lat: Optional[float] = 14.6958
    lon: Optional[float] = 121.1207

class FloodReportUpdate(BaseModel):
    location: Optional[str] = None
    severity: Optional[str] = None
    notes: Optional[str] = None

def send_report_notification(report: dict) -> dict:
    credentials_path = os.getenv("GOOGLE_APPLICATION_CREDENTIALS")
    project_id = os.getenv("FIREBASE_PROJECT_ID")
    if not credentials_path or not os.path.isfile(credentials_path) or not project_id:
        logger.error(
            "Flood report was saved, but push notifications are not configured. "
            "Set GOOGLE_APPLICATION_CREDENTIALS and FIREBASE_PROJECT_ID."
        )
        return {"configured": False, "accepted": False}

    try:
        try:
            firebase_app = firebase_admin.get_app()
        except ValueError:
            firebase_app = firebase_admin.initialize_app(
                credentials.Certificate(credentials_path),
                {"projectId": project_id},
            )

        location = str(report.get("location") or "Flood report")
        severity = str(report.get("severity") or "New flood report")
        timestamp = int(report.get("timestamp") or time.time() * 1000)
        message = messaging.Message(
            notification=messaging.Notification(
                title="FloodGuard flood alert",
                body=f"{location}: {severity}",
            ),
            data={
                "reportId": str(report.get("id", "")),
                "location": location,
                "severity": severity,
                "notes": str(report.get("notes") or ""),
                "reporterName": "Community Reporter",
                "timestamp": str(timestamp),
            },
            android=messaging.AndroidConfig(priority="high"),
            topic="floodguard",
        )
        messaging.send(message, app=firebase_app)
        return {"configured": True, "accepted": True}
    except (FirebaseError, ValueError, OSError):
        logger.exception("Flood report was saved, but Firebase push delivery failed")
        return {"configured": True, "accepted": False}

@app.post("/api/auth/register")
def register_user(user: UserRegister):
    data = load_data()
    for u in data["users"]:
        if u["username"] == user.username or u["email"] == user.email:
            raise HTTPException(status_code=400, detail="Username or email already exists")
    
    user_dict = user.model_dump() if hasattr(user, "model_dump") else user.dict()
    if not user_dict.get("id"):
        user_dict["id"] = str(uuid.uuid4())
    data["users"].append(user_dict)
    save_data(data)
    return {"success": True, "message": "User registered successfully", "user": user_dict}

@app.post("/api/auth/login")
def login_user(credentials: UserLogin):
    data = load_data()
    for u in data["users"]:
        if u["username"] == credentials.username and u["password"] == credentials.password:
            return {"success": True, "message": "Login successful", "user": u}
    raise HTTPException(status_code=401, detail="Invalid username or password")

@app.put("/api/auth/profile")
def update_profile(profile: ProfileUpdate):
    data = load_data()
    for user in data["users"]:
        if user["username"] == profile.username:
            if profile.fullName is not None:
                user["fullName"] = profile.fullName
            if profile.gender is not None:
                user["gender"] = profile.gender
            if profile.mobile is not None:
                user["mobile"] = profile.mobile
            if profile.email is not None:
                user["email"] = profile.email
            save_data(data)
            return {"success": True, "user": user}
    raise HTTPException(status_code=404, detail="User not found")

@app.get("/api/reports")
def get_reports():
    data = load_data()
    return {"reports": data["reports"]}

@app.post("/api/reports")
def create_report(report: FloodReportCreate):
    data = load_data()
    report_dict = report.model_dump() if hasattr(report, "model_dump") else report.dict()
    if not report_dict.get("id"):
        report_dict["id"] = str(uuid.uuid4())
    data["reports"].insert(0, report_dict)

    save_data(data)
    notification = send_report_notification(report_dict)
    return {"success": True, "report": report_dict, "notification": notification}

@app.put("/api/reports/{report_id}")
def update_report(report_id: str, update_data: FloodReportUpdate):
    data = load_data()
    for report in data["reports"]:
        if report.get("id") == report_id:
            if update_data.location is not None:
                report["location"] = update_data.location
            if update_data.severity is not None:
                report["severity"] = update_data.severity
            if update_data.notes is not None:
                report["notes"] = update_data.notes
            save_data(data)
            return {"success": True, "report": report}
    raise HTTPException(status_code=404, detail="Report not found")

@app.delete("/api/reports/{report_id}")
def delete_report(report_id: str):
    data = load_data()
    initial_len = len(data["reports"])
    data["reports"] = [r for r in data["reports"] if r.get("id") != report_id]
    if len(data["reports"]) == initial_len:
        raise HTTPException(status_code=404, detail="Report not found")
    save_data(data)
    return {"success": True, "message": "Report deleted successfully"}

@app.get("/api/users")
def get_users():
    data = load_data()
    return {"users": data["users"]}

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=3000, reload=True)
