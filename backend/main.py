#!/usr/bin/env python3
import os
import math
import time
import json
import uuid
import logging
import urllib.request
import urllib.error
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
FREE_SMS_API_URL = os.getenv("SMS_API_URL", "https://textbelt.com/text")
FREE_SMS_API_KEY = os.getenv("SMS_API_KEY", "textbelt")

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
    authorUsername: Optional[str] = ""
    reporterName: Optional[str] = ""

class FloodReportUpdate(BaseModel):
    location: Optional[str] = None
    severity: Optional[str] = None
    notes: Optional[str] = None
    authorUsername: Optional[str] = None


def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6371.0
    d_lat = math.radians(lat2 - lat1)
    d_lon = math.radians(lon2 - lon1)
    a = (
        math.sin(d_lat / 2) ** 2
        + math.cos(math.radians(lat1))
        * math.cos(math.radians(lat2))
        * math.sin(d_lon / 2) ** 2
    )
    return 2 * r * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def normalize_location_tokens(loc: str) -> set:
    cleaned = (loc or "").lower().replace("auto detected -", "")
    for ch in (",", ".", "-", "/", "(", ")"):
        cleaned = cleaned.replace(ch, " ")
    stop_words = {"st", "street", "ave", "avenue", "rd", "road", "brgy", "barangay", "city", "province", "rizal"}
    return {t for t in cleaned.split() if len(t) >= 3 and t not in stop_words}


def are_reports_similar(r1: dict, r2: dict) -> bool:
    loc1 = (r1.get("location") or "").strip().lower()
    loc2 = (r2.get("location") or "").strip().lower()
    if loc1 and loc2 and (loc1 in loc2 or loc2 in loc1):
        return True

    tokens1 = normalize_location_tokens(loc1)
    tokens2 = normalize_location_tokens(loc2)
    specific_tokens1 = tokens1 - {"san", "mateo"}
    specific_tokens2 = tokens2 - {"san", "mateo"}
    if specific_tokens1 and specific_tokens2 and (specific_tokens1 & specific_tokens2):
        return True

    try:
        lat1 = float(r1.get("lat") or 14.6958)
        lon1 = float(r1.get("lon") or 121.1207)
        lat2 = float(r2.get("lat") or 14.6958)
        lon2 = float(r2.get("lon") or 121.1207)
        if haversine_km(lat1, lon1, lat2, lon2) <= 2.0:
            if (tokens1 & tokens2) or r1.get("severity") == r2.get("severity"):
                return True
    except (TypeError, ValueError):
        pass

    return False


def format_phone_e164(phone: str) -> str:
    digits = "".join(ch for ch in (phone or "") if ch.isdigit() or ch == "+")
    if not digits:
        return ""
    if digits.startswith("09") and len(digits) == 11:
        return "+63" + digits[1:]
    if digits.startswith("9") and len(digits) == 10:
        return "+63" + digits
    if not digits.startswith("+"):
        return "+" + digits
    return digits


def dispatch_free_sms_api(phone: str, message: str) -> dict:
    formatted_phone = format_phone_e164(phone)
    if not formatted_phone:
        return {"phone": phone, "sent": False, "provider": "Textbelt Free SMS API"}

    payload = json.dumps({
        "phone": formatted_phone,
        "message": message,
        "key": FREE_SMS_API_KEY,
    }).encode("utf-8")

    req = urllib.request.Request(
        FREE_SMS_API_URL,
        data=payload,
        headers={"Content-Type": "application/json", "User-Agent": "FloodGuardApp/1.0"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=4.0) as resp:
            body = resp.read().decode("utf-8", errors="ignore")
            parsed = json.loads(body) if body else {}
            return {
                "phone": formatted_phone,
                "sent": bool(parsed.get("success", False)),
                "apiReached": True,
                "provider": "Textbelt Free SMS API",
                "response": parsed,
            }
    except Exception as exc:
        logger.warning("Free SMS API call fallback for %s: %s", formatted_phone, exc)
        return {
            "phone": formatted_phone,
            "sent": False,
            "apiReached": False,
            "provider": "Textbelt Free SMS API (Simulated Fallback)",
            "error": str(exc),
        }


def evaluate_and_trigger_sms_broadcast(data: dict, new_report: dict) -> Optional[dict]:
    all_reports = data.get("reports", [])
    similar_reports = [
        r for r in all_reports
        if r.get("severityLevel") != "CLEARED" and are_reports_similar(new_report, r)
    ]

    if len(similar_reports) < 2:
        return None

    location = str(new_report.get("location") or "San Mateo, Rizal")
    severity = str(new_report.get("severity") or "Waist-Deep")
    status_text = str(new_report.get("statusText") or "Not Passable")
    notes = str(new_report.get("notes") or f"Water level reported: {severity}")
    lat = float(new_report.get("lat") or 14.6958)
    lon = float(new_report.get("lon") or 121.1207)
    report_count = len(similar_reports)

    reporters = []
    for r in similar_reports:
        name = (r.get("reporterName") or r.get("authorUsername") or "Community Reporter").strip()
        if name and name not in reporters:
            reporters.append(name)
    reporters_summary = ", ".join(reporters[:3])

    sms_message = (
        f"[FLOODGUARD SMS ALERT] URGENT: {report_count} similar flood reports verified near "
        f"{location}. Water Level: {severity} ({status_text}). {notes}. "
        f"Stay alert and avoid flooded roads."
    )

    users = data.get("users", [])
    recipient_phones = []
    sms_results = []
    for u in users:
        mobile = (u.get("mobile") or "").strip()
        formatted = format_phone_e164(mobile)
        if formatted and formatted not in recipient_phones:
            recipient_phones.append(formatted)
            sms_results.append(dispatch_free_sms_api(formatted, sms_message))

    broadcast_record = {
        "id": f"sms_bcast_{int(time.time() * 1000)}",
        "reportId": str(new_report.get("id") or ""),
        "location": location,
        "severity": severity,
        "statusText": status_text,
        "notes": notes,
        "reporterName": reporters_summary,
        "authorUsername": str(new_report.get("authorUsername") or ""),
        "timestamp": int(time.time() * 1000),
        "lat": lat,
        "lon": lon,
        "radiusKm": 5.0,
        "similarReportCount": report_count,
        "smsMessage": sms_message,
        "smsProvider": "Textbelt Free SMS API (https://textbelt.com/text)",
        "smsRecipientsCount": len(recipient_phones),
        "recipientPhones": recipient_phones,
        "smsDispatchResults": sms_results,
    }

    if "broadcasts" not in data:
        data["broadcasts"] = []
    data["broadcasts"].insert(0, broadcast_record)
    data["broadcasts"] = data["broadcasts"][:30]
    save_data(data)
    return broadcast_record


def send_report_notification(report: dict, sms_broadcast: Optional[dict] = None) -> dict:
    credentials_path = os.getenv("GOOGLE_APPLICATION_CREDENTIALS")
    project_id = os.getenv("FIREBASE_PROJECT_ID")
    if not credentials_path or not os.path.isfile(credentials_path) or not project_id:
        logger.info(
            "Flood report saved; using built-in Free SMS API & broadcast polling channel."
        )
        return {
            "configured": bool(sms_broadcast),
            "accepted": bool(sms_broadcast),
            "smsBroadcastTriggered": bool(sms_broadcast),
        }

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
        author_username = str(report.get("authorUsername") or "")
        reporter_name = str(report.get("reporterName") or author_username or "Community Reporter")
        similar_count = int(sms_broadcast.get("similarReportCount", 1)) if sms_broadcast else 1
        sms_message = str(sms_broadcast.get("smsMessage", "")) if sms_broadcast else ""
        message = messaging.Message(
            notification=messaging.Notification(
                title="FloodGuard SMS Flood Alert",
                body=sms_message or f"{location}: {severity}",
            ),
            data={
                "reportId": str(report.get("id", "")),
                "location": location,
                "severity": severity,
                "notes": str(report.get("notes") or ""),
                "reporterName": reporter_name,
                "authorUsername": author_username,
                "timestamp": str(timestamp),
                "similarReportCount": str(similar_count),
                "smsMessage": sms_message,
            },
            android=messaging.AndroidConfig(priority="high"),
            topic="floodguard",
        )
        messaging.send(message, app=firebase_app)
        return {
            "configured": True,
            "accepted": True,
            "smsBroadcastTriggered": bool(sms_broadcast),
        }
    except (FirebaseError, ValueError, OSError):
        logger.exception("Flood report was saved, but Firebase push delivery failed")
        return {
            "configured": True,
            "accepted": bool(sms_broadcast),
            "smsBroadcastTriggered": bool(sms_broadcast),
        }

@app.post("/api/auth/register")
def register_user(user: UserRegister):
    data = load_data()
    for u in data["users"]:
        if u["username"] == user.username:
            raise HTTPException(status_code=400, detail="Username already exists")
    
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
            save_data(data)
            return {"success": True, "user": user}
    raise HTTPException(status_code=404, detail="User not found")

@app.get("/api/reports")
def get_reports():
    data = load_data()
    return {"reports": data["reports"]}

@app.get("/api/broadcasts")
def get_broadcasts():
    data = load_data()
    return {"broadcasts": data.get("broadcasts", [])}

@app.post("/api/reports")
def create_report(report: FloodReportCreate):
    data = load_data()
    report_dict = report.model_dump() if hasattr(report, "model_dump") else report.dict()
    if not report_dict.get("id"):
        report_dict["id"] = str(uuid.uuid4())
    data["reports"].insert(0, report_dict)

    save_data(data)
    sms_broadcast = evaluate_and_trigger_sms_broadcast(data, report_dict)
    notification = send_report_notification(report_dict, sms_broadcast)
    return {
        "success": True,
        "report": report_dict,
        "notification": notification,
        "smsBroadcast": sms_broadcast,
    }

@app.put("/api/reports/{report_id}")
def update_report(report_id: str, update_data: FloodReportUpdate):
    data = load_data()
    for report in data["reports"]:
        if report.get("id") == report_id:
            existing_author = (report.get("authorUsername") or "").strip().lower()
            request_author = (update_data.authorUsername or "").strip().lower()
            if existing_author and request_author != existing_author:
                raise HTTPException(status_code=403, detail="Only the author who submitted this report can edit it")
            if update_data.location is not None:
                report["location"] = update_data.location
            if update_data.severity is not None:
                report["severity"] = update_data.severity
                if update_data.severity in ("Impassable", "Waist-Deep"):
                    report["statusText"] = "Not Passable"
                    report["severityLevel"] = "NOT_PASSABLE"
                elif update_data.severity == "Knee-Deep":
                    report["statusText"] = "Heavy vehicles only"
                    report["severityLevel"] = "HEAVY_VEHICLES_ONLY"
                else:
                    report["statusText"] = "Cleared"
                    report["severityLevel"] = "CLEARED"
            if update_data.notes is not None:
                report["notes"] = update_data.notes
            save_data(data)
            return {"success": True, "report": report}
    raise HTTPException(status_code=404, detail="Report not found")

@app.delete("/api/reports/{report_id}")
def delete_report(report_id: str, authorUsername: Optional[str] = None):
    data = load_data()
    for report in data["reports"]:
        if report.get("id") == report_id:
            existing_author = (report.get("authorUsername") or "").strip().lower()
            request_author = (authorUsername or "").strip().lower()
            if existing_author and request_author != existing_author:
                raise HTTPException(status_code=403, detail="Only the author who submitted this report can delete it")
            data["reports"] = [r for r in data["reports"] if r.get("id") != report_id]
            save_data(data)
            return {"success": True, "message": "Report deleted successfully"}
    raise HTTPException(status_code=404, detail="Report not found")

@app.get("/api/users")
def get_users():
    data = load_data()
    return {"users": data["users"]}

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=3000, reload=True)
