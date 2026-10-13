from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, List
import json
import os
import uuid

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
        return {"users": [], "reports": []}
    with open(DATA_FILE, "r") as f:
        try:
            return json.load(f)
        except json.JSONDecodeError:
            return {"users": [], "reports": []}

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
    return {"success": True, "report": report_dict}

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
