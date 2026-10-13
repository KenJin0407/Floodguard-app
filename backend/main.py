from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Optional, List
import json
import os
import uuid

app = FastAPI(title="FloodGuard FastAPI Backend", version="1.0.0")

DATA_FILE = "data_store.json"

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
    gender: Optional[str] = None
    mobile: Optional[str] = None
    email: str
    username: str
    password: str

class UserLogin(BaseModel):
    username: str
    password: str

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

@app.post("/api/auth/register")
def register_user(user: UserRegister):
    data = load_data()
    for u in data["users"]:
        if u["username"] == user.username or u["email"] == user.email:
            raise HTTPException(status_code=400, detail="Username or email already exists")
    
    user_dict = user.dict()
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

@app.get("/api/reports")
def get_reports():
    data = load_data()
    return {"reports": data["reports"]}

@app.post("/api/reports")
def create_report(report: FloodReportCreate):
    data = load_data()
    report_dict = report.dict()
    if not report_dict.get("id"):
        report_dict["id"] = str(uuid.uuid4())
    data["reports"].insert(0, report_dict)
    save_data(data)
    return {"success": True, "report": report_dict}

@app.get("/api/users")
def get_users():
    data = load_data()
    return {"users": data["users"]}