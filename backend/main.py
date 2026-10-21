import sys
try:
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    if hasattr(sys.stderr, 'reconfigure'):
        sys.stderr.reconfigure(encoding='utf-8', errors='replace')
except Exception:
    pass
from fastapi import FastAPI, HTTPException, Depends
from sqlalchemy import create_engine, Column, Integer, String, Boolean, ForeignKey, DateTime, Float, func
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, Session
from datetime import datetime
from pydantic import BaseModel
from typing import Optional, List
import random
import time
import httpx
import uuid
import os

from fastapi.middleware.cors import CORSMiddleware

app = FastAPI(title="FloodGuard Backend", version="2.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# SQLite Database setup
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_FILE = os.path.join(BASE_DIR, "floodguard.db")
SQLALCHEMY_DATABASE_URL = f"sqlite:///{DB_FILE}"
engine = create_engine(SQLALCHEMY_DATABASE_URL, connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

# --- DATABASE MODELS ---
class User(Base):
    __tablename__ = "users"
    id = Column(Integer, primary_key=True, index=True)
    fullName = Column(String, nullable=False)
    gender = Column(String, nullable=True)
    mobile = Column(String, nullable=True)
    email = Column(String, unique=True, index=True, nullable=False)
    username = Column(String, unique=True, index=True, nullable=False)
    password = Column(String, nullable=False)
    isVerified = Column(Boolean, default=True)

class Road(Base):
    __tablename__ = "roads"
    id = Column(Integer, primary_key=True, index=True)
    name = Column(String, nullable=False, unique=True)
    passable = Column(Boolean, default=True)

class FloodReport(Base):
    __tablename__ = "reports"
    id = Column(String, primary_key=True, index=True) # String to support UUIDs from Android app
    location = Column(String, nullable=False)
    severity = Column(String, nullable=False)
    statusText = Column(String, default="Active")
    notes = Column(String, nullable=True)
    timestamp = Column(Integer, default=lambda: int(datetime.now().timestamp() * 1000))
    severityLevel = Column(String, default="NOT_PASSABLE")
    photoUri = Column(String, nullable=True)
    lat = Column(Float, default=14.6958)
    lon = Column(Float, default=121.1207)
    reporterUsername = Column(String, default="anonymous")
    reporterName = Column(String, default="")

Base.metadata.create_all(engine)

# --- PYDANTIC SCHEMAS ---
class UserRegister(BaseModel):
    fullName: str
    gender: Optional[str] = None
    mobile: Optional[str] = None
    email: Optional[str] = None
    username: str
    password: str
    isVerified: Optional[bool] = True

class SendOtpRequest(BaseModel):
    mobile: str
    purpose: Optional[str] = "registration"

class VerifyOtpRequest(BaseModel):
    mobile: str
    code: str

class FindAccountRequest(BaseModel):
    identifier: str

class ResetPasswordRequest(BaseModel):
    identifier: str
    newPassword: str
    otpCode: Optional[str] = None

# In-memory OTP storage: mobile -> {"code": "123456", "expires_at": timestamp}
ACTIVE_OTPS = {}

class UserLogin(BaseModel):
    username: str
    password: str

class RoadCreate(BaseModel):
    name: str
    passable: bool = True

class RoadResponse(BaseModel):
    id: int
    name: str
    passable: bool
    class Config:
        from_attributes = True

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
    username: Optional[str] = "anonymous"
    authorUsername: Optional[str] = None
    reporterName: Optional[str] = None

MAX_DAILY_REPORTS = 4
daily_user_reports = {}  # key: "username:YYYY-MM-DD" -> count

PROFANITY_WORDS = [
    "putangina", "putang ina", "tangina", "taena", "puta", "gago", "gaga",
    "tanga", "bobo", "ulol", "tarantado", "kupal", "kantot", "iyot", "tite",
    "titi", "puke", "pepe", "pekpek", "burat", "tamod", "jakol", "jabol",
    "pokpok", "punyeta", "leche", "pakshet", "buwisit", "bwisit", "hindot",
    "inutil", "hayop ka", "kingina", "shunga", "engot", "supot", "bayag",
    "fuck", "fck", "fuk", "shit", "bitch", "asshole", "dick", "cock",
    "pussy", "porn", "sex", "nude", "boobs", "bastard", "slut", "whore", "cunt"
]

MASH_PATTERNS = [
    "asdf", "qwerty", "zxcv", "hjkl", "bnm", "uiop",
    "hahaha", "hehehe", "hihihi", "huhuhu", "lololol",
    "walang kwenta", "trip lang", "joke lang", "testing lang", "test lang", "blablabla", "blahblah"
]

def is_gibberish_text(text: str) -> bool:
    import re
    cleaned = (text or "").strip()
    if not cleaned:
        return False
    letters_only = "".join(c.lower() for c in cleaned if c.isalpha())
    if len(letters_only) < 3:
        return True
    if re.search(r"(.)\1{3,}", cleaned, re.IGNORECASE):
        return True
    lower_text = cleaned.lower()
    if any(p in lower_text for p in MASH_PATTERNS):
        return True
    if re.search(r"[bcdfghjklmnpqrstvwxyz]{5,}", letters_only):
        return True
    vowels = sum(1 for c in letters_only if c in "aeiou")
    ratio = vowels / len(letters_only)
    if len(letters_only) >= 5 and (ratio < 0.18 or ratio > 0.85):
        return True
    return False

def validate_report_content(location: str, notes: str):
    combined = f"{location} {notes}".lower()
    for bad in PROFANITY_WORDS:
        if bad in combined:
            raise HTTPException(status_code=400, detail="Inappropriate report: Profanity or vulgar language is not allowed.")
    if len(location.strip()) < 3 or is_gibberish_text(location):
        raise HTTPException(status_code=400, detail="Invalid location: Please enter a valid road or barangay in San Mateo.")
    if notes and notes.strip() and (len(notes.strip()) < 4 or is_gibberish_text(notes)):
        raise HTTPException(status_code=400, detail="Inappropriate report: Notes contain nonsensical or invalid text.")

class FloodReportUpdate(BaseModel):
    location: str
    severity: str
    notes: Optional[str] = ""

class UserProfileUpdate(BaseModel):
    username: str
    fullName: str
    gender: Optional[str] = None
    mobile: Optional[str] = None
    email: str

# --- DEPENDENCY ---
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

# --- ROUTES ---
@app.get("/")
def root():
    return {"message": "KAYA MO YAN! FloodGuard API is running."}

# AUTHENTICATION ROUTES (Required by Android App)
def hash_password(plain_pw: str) -> str:
    import hashlib
    return hashlib.sha256(plain_pw.encode("utf-8")).hexdigest()

VALID_TELCO_PREFIXES = {
    # Globe / TM
    "0905", "0906", "0915", "0916", "0917", "0926", "0927", "0935", "0936", "0937",
    "0945", "0953", "0954", "0955", "0956", "0965", "0966", "0967", "0975", "0976",
    "0977", "0978", "0979", "0995", "0996", "0997",
    # Smart / TNT
    "0907", "0908", "0909", "0910", "0912", "0913", "0914", "0918", "0919", "0920",
    "0921", "0928", "0929", "0930", "0931", "0938", "0939", "0940", "0946", "0947",
    "0948", "0949", "0950", "0951", "0961", "0963", "0964", "0968", "0969", "0970",
    "0971", "0981", "0982", "0985", "0989", "0992", "0998", "0999",
    # Sun Cellular
    "0922", "0923", "0924", "0925", "0932", "0933", "0934", "0942", "0943",
    # DITO
    "0895", "0896", "0897", "0898", "0991", "0992", "0993", "0994"
}

REJECTED_DUMMIES = {
    "09123456789", "09876543210", "09000000000", "09111111111",
    "09222222222", "09333333333", "09444444444", "09555555555",
    "09666666666", "09777777777", "09888888888", "09999999999",
    "09123123123", "09987654321"
}

def clean_phone_number(phone: str) -> str:
    digits = "".join(c for c in (phone or "") if c.isdigit())
    if digits.startswith("63") and len(digits) == 12:
        return "0" + digits[2:]
    elif (digits.startswith("9") or digits.startswith("8")) and len(digits) == 10:
        return "0" + digits
    return digits

def validate_philippine_number(raw: str) -> tuple[bool, str, str]:
    phone = clean_phone_number(raw)
    if len(phone) != 11 or not (phone.startswith("09") or phone.startswith("08")):
        return False, phone, "Invalid mobile number. Must be an 11-digit Philippine mobile number starting with 09 or 08 (e.g. 09171234567)."
    
    if phone in REJECTED_DUMMIES or len(set(phone[4:])) <= 1:
        return False, phone, f"Invalid number: Dummy or repetitive phone numbers ({phone}) are rejected."
        
    prefix = phone[:4]
    if prefix not in VALID_TELCO_PREFIXES:
        return False, phone, f"Invalid network prefix ({prefix}). Must belong to a registered Philippine mobile operator (Globe, TM, Smart, TNT, Sun, DITO)."
        
    return True, phone, "Valid"

@app.post("/api/auth/send-otp")
async def send_otp(req: SendOtpRequest):
    is_valid, phone, err_msg = validate_philippine_number(req.mobile)
    if not is_valid:
        raise HTTPException(status_code=400, detail=err_msg)
    
    # Generate 6-digit random code
    code = f"{random.randint(100000, 999999)}"
    expires_at = int(time.time()) + 300  # 5 minutes
    ACTIVE_OTPS[phone] = {"code": code, "expires_at": expires_at}
    print(f"[SMS] [OTP Generated] Mobile: {phone} | Code: {code} | Expires: 5 mins")

    # Attempt to dispatch real SMS via Textbelt free tier (best-effort)
    sms_dispatched = False
    sms_details = "Free In-App Simulated Fallback"
    try:
        int_phone = "+63" + phone[1:] if phone.startswith("0") else ("+" + phone if phone.startswith("63") else phone)
        sms_msg = f"[FloodGuard] Your security verification code is {code}. Valid for 5 minutes. Do not share this code."
        async with httpx.AsyncClient(timeout=4.0) as client:
            resp = await client.post("https://textbelt.com/text", json={
                "phone": int_phone,
                "message": sms_msg,
                "key": "textbelt"
            })
            if resp.status_code == 200:
                data = resp.json()
                sms_dispatched = data.get("success", False)
                if sms_dispatched:
                    sms_details = "SMS sent via Textbelt Public API"
    except Exception as e:
        print(f"[WARN] SMS gateway attempt error (will use demo fallback): {e}")

    return {
        "success": True,
        "message": f"Verification code sent to {phone}",
        "mobile": phone,
        "smsSent": sms_dispatched,
        "smsProvider": sms_details,
        # We also return the code in response so Android app can show instant heads-up toast/banner in demo mode!
        "demoOtpCode": code
    }

@app.post("/api/auth/verify-otp")
def verify_otp(req: VerifyOtpRequest, db: Session = Depends(get_db)):
    phone = clean_phone_number(req.mobile)
    record = ACTIVE_OTPS.get(phone)
    if not record:
        raise HTTPException(status_code=400, detail="No active verification code found for this number. Please request a new code.")
    
    if int(time.time()) > record["expires_at"]:
        ACTIVE_OTPS.pop(phone, None)
        raise HTTPException(status_code=400, detail="Verification code has expired. Please request a new code.")
    
    if record["code"] != req.code.strip():
        raise HTTPException(status_code=400, detail="Incorrect verification code. Please check and try again.")
    
    # Successfully verified! Remove OTP so it cannot be reused
    ACTIVE_OTPS.pop(phone, None)
    print(f"[OK] [OTP Verified] Mobile: {phone} is successfully verified!")

    # Mark user as verified in database if they already exist
    user = db.query(User).filter(User.mobile.like(f"%{phone[-10:]}%")).first()
    if user:
        user.isVerified = True
        db.commit()

    return {
        "success": True,
        "message": "Mobile number verified successfully!",
        "mobile": phone,
        "isVerified": True
    }

@app.post("/api/auth/register")
def register_user(user: UserRegister, db: Session = Depends(get_db)):
    print(f"[IN] Registration Request: username='{user.username}'")
    existing = db.query(User).filter(User.username == user.username).first()
    if existing:
        print(f"[WARN] User '{user.username}' already exists in SQLite database!")
        return {"success": True, "message": "User already registered", "user": user.dict()}
    
    user_data = user.dict()
    # Ensure a non-empty unique email exists for SQLite constraint
    if not user_data.get("email"):
        clean_uname = "".join(c for c in user.username if c.isalnum()).lower()
        user_data["email"] = f"{clean_uname}_{uuid.uuid4().hex[:6]}@floodguard.local"

    # Hash password if not already SHA-256 (64 hex characters)
    if len(user_data["password"]) != 64:
        user_data["password"] = hash_password(user_data["password"])

    new_user = User(**user_data)
    db.add(new_user)
    db.commit()
    db.refresh(new_user)
    print(f"[OK] User '{user.username}' successfully saved to SQLite database (id={new_user.id})!")
    return {"success": True, "message": "User registered successfully", "user": user_data}

@app.post("/api/auth/login")
def login_user(credentials: UserLogin, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.username == credentials.username).first()
    if not user:
        raise HTTPException(status_code=401, detail="Invalid username or password")

    hashed_attempt = hash_password(credentials.password)
    # Support both hashed and legacy plain-text match
    if user.password != credentials.password and user.password != hashed_attempt:
        raise HTTPException(status_code=401, detail="Invalid username or password")

    # If it was plain text, upgrade to hash
    if user.password == credentials.password and user.password != hashed_attempt:
        user.password = hashed_attempt
        db.commit()
    
    user_dict = {
        "id": str(user.id),
        "fullName": user.fullName,
        "gender": user.gender,
        "mobile": user.mobile,
        "email": user.email,
        "username": user.username
    }
    return {"success": True, "message": "Login successful", "user": user_dict}

@app.put("/api/auth/profile")
def update_profile(profile: UserProfileUpdate, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.username == profile.username).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    user.fullName = profile.fullName
    user.gender = profile.gender
    user.mobile = profile.mobile
    user.email = profile.email
    db.commit()
    db.refresh(user)
    return {"success": True, "message": "Profile updated successfully"}

@app.post("/api/auth/find-account")
def find_account(req: FindAccountRequest, db: Session = Depends(get_db)):
    ident = req.identifier.strip()
    digits = clean_phone_number(ident)
    
    user = None
    if len(digits) >= 10:
        user = db.query(User).filter(User.mobile.like(f"%{digits[-10:]}%")).first()
    if not user:
        user = db.query(User).filter((User.username.ilike(ident)) | (User.email.ilike(ident))).first()
        
    if not user:
        raise HTTPException(status_code=404, detail="No account found for this username or mobile number.")
        
    raw_mobile = user.mobile or ""
    masked = raw_mobile
    if len(raw_mobile) >= 7:
        masked = raw_mobile[:4] + "***" + raw_mobile[-3:]
        
    return {
        "success": True,
        "username": user.username,
        "fullName": user.fullName,
        "mobile": raw_mobile,
        "maskedMobile": masked
    }

@app.post("/api/auth/reset-password")
def reset_password(req: ResetPasswordRequest, db: Session = Depends(get_db)):
    ident = req.identifier.strip()
    digits = clean_phone_number(ident)
    
    user = None
    if len(digits) >= 10:
        user = db.query(User).filter(User.mobile.like(f"%{digits[-10:]}%")).first()
    if not user:
        user = db.query(User).filter((User.username.ilike(ident)) | (User.email.ilike(ident))).first()
        
    if not user:
        raise HTTPException(status_code=404, detail="Account not found.")
        
    new_pw = req.newPassword.strip()
    if len(new_pw) < 6:
        raise HTTPException(status_code=400, detail="Password must be at least 6 characters long.")
        
    user.password = hash_password(new_pw)
    db.commit()
    print(f"[KEY] [Password Reset] User '{user.username}' successfully reset their password!")
    return {
        "success": True,
        "message": "Password reset successfully. You can now log in.",
        "username": user.username
    }

@app.get("/api/users")
def get_users(db: Session = Depends(get_db)):
    users = db.query(User).all()
    user_list = [{
        "id": str(u.id),
        "fullName": u.fullName,
        "gender": u.gender,
        "mobile": u.mobile,
        "email": u.email,
        "username": u.username,
        "isVerified": bool(getattr(u, "isVerified", True))
    } for u in users]
    return {"users": user_list}

# FLOOD REPORTS ROUTES (Android App & Web Compatible)
@app.get("/api/reports")
def get_all_reports(db: Session = Depends(get_db)):
    reports = db.query(FloodReport).order_by(FloodReport.timestamp.desc()).all()
    report_list = []
    for r in reports:
        report_list.append({
            "id": r.id,
            "location": r.location,
            "severity": r.severity,
            "statusText": r.statusText,
            "notes": r.notes,
            "timestamp": r.timestamp,
            "severityLevel": r.severityLevel,
            "photoUri": r.photoUri,
            "lat": r.lat,
            "lon": r.lon,
            "reporterUsername": getattr(r, "reporterUsername", "") or ""
        })
    return {"reports": report_list}

@app.post("/api/reports")
def create_report(report: FloodReportCreate, db: Session = Depends(get_db)):
    # 1. Validate against profanity and gibberish/nonsense
    validate_report_content(report.location, report.notes or "")

    # 2. Enforce 4 reports per day per user limit (resets daily)
    today_str = datetime.now().strftime("%Y-%m-%d")
    user_key = f"{(report.username or 'anonymous').lower().strip()}:{today_str}"
    used_today = daily_user_reports.get(user_key, 0)
    if used_today >= MAX_DAILY_REPORTS:
        raise HTTPException(
            status_code=429,
            detail="Daily report limit reached (4/4). Your quota resets tomorrow."
        )

    report_id = report.id or str(uuid.uuid4())
    timestamp = report.timestamp or int(datetime.now().timestamp() * 1000)
    reporter = (report.authorUsername or report.username or "anonymous").strip()
    disp_name = (report.reporterName or reporter).strip()
    
    existing = db.query(FloodReport).filter(FloodReport.id == report_id).first()
    if existing:
        existing.location = report.location
        existing.severity = report.severity
        existing.statusText = report.statusText or "Active"
        existing.notes = report.notes or ""
        existing.severityLevel = report.severityLevel or "NOT_PASSABLE"
        existing.lat = report.lat or 14.6958
        existing.lon = report.lon or 121.1207
        if reporter and reporter != "anonymous":
            existing.reporterUsername = reporter
        db.commit()
        db.refresh(existing)
        return {"success": True, "report": report.dict(), "remainingToday": max(0, MAX_DAILY_REPORTS - used_today)}

    db_report = FloodReport(
        id=report_id,
        location=report.location,
        severity=report.severity,
        statusText=report.statusText or "Active",
        notes=report.notes or "",
        timestamp=timestamp,
        severityLevel=report.severityLevel or "NOT_PASSABLE",
        photoUri=report.photoUri,
        lat=report.lat or 14.6958,
        lon=report.lon or 121.1207,
        reporterUsername=reporter,
        reporterName=disp_name
    )
    db.add(db_report)
    db.commit()
    db.refresh(db_report)

    daily_user_reports[user_key] = used_today + 1
    remaining_today = max(0, MAX_DAILY_REPORTS - (used_today + 1))
    
    return {"success": True, "report": report.dict(), "remainingToday": remaining_today}

@app.put("/api/reports/{report_id}")
def update_report(report_id: str, payload: FloodReportUpdate, db: Session = Depends(get_db)):
    report = db.query(FloodReport).filter(FloodReport.id == report_id).first()
    if not report:
        raise HTTPException(status_code=404, detail="Report not found")

    report.location = payload.location
    report.severity = payload.severity
    report.notes = payload.notes or ""
    if payload.severity in ("Impassable", "Waist-Deep"):
        report.statusText = "Not Passable"
        report.severityLevel = "NOT_PASSABLE"
    elif payload.severity == "Knee-Deep":
        report.statusText = "Heavy vehicles only"
        report.severityLevel = "HEAVY_VEHICLES_ONLY"
    else:
        report.statusText = "Cleared"
        report.severityLevel = "CLEARED"

    db.commit()
    db.refresh(report)
    return {"success": True, "message": "Report updated"}

@app.delete("/api/reports/{report_id}")
def delete_report(report_id: str, db: Session = Depends(get_db)):
    report = db.query(FloodReport).filter(FloodReport.id == report_id).first()
    if not report:
        raise HTTPException(status_code=404, detail="Report not found")
    db.delete(report)
    db.commit()
    return {"success": True, "message": "Report deleted"}

# EMERGENCY BROADCASTS ROUTES (For Floating Alert & Emergency SMS Sync)
@app.get("/api/broadcasts")
@app.get("/broadcasts")
def get_active_broadcasts(db: Session = Depends(get_db)):
    """
    Returns active emergency flood broadcasts.
    Automatically converts severe reports from the past 24 hours into active emergency broadcast cards.
    """
    now_ms = int(datetime.now().timestamp() * 1000)
    day_ago = now_ms - (24 * 60 * 60 * 1000)
    
    severe_reports = db.query(FloodReport).filter(
        FloodReport.timestamp >= day_ago,
        (FloodReport.severity.ilike("%impassable%") | FloodReport.severity.ilike("%waist%") | FloodReport.severityLevel.ilike("%not_passable%"))
    ).order_by(FloodReport.timestamp.desc()).all()
    
    broadcasts = []
    for r in severe_reports:
        broadcasts.append({
            "id": f"bcast_{r.id}",
            "location": r.location,
            "severity": r.severity,
            "notes": r.notes or "Severe flood alert broadcast",
            "reporterName": getattr(r, "reporterUsername", "") or "FloodGuard Alert",
            "timestamp": r.timestamp,
            "statusText": r.statusText or "Active Warning",
            "similarReportCount": 2,
            "lat": r.lat or 14.6958,
            "lon": r.lon or 121.1207,
            "radiusKm": 5.0,
            "smsMessage": f"[FloodGuard Emergency] Flood reported in {r.location}: {r.severity}. Caution advised.",
            "smsProvider": "FloodGuard Municipal Network",
            "smsRecipientsCount": 1,
            "recipientPhones": []
        })
        
    return {"broadcasts": broadcasts}

@app.post("/api/broadcasts")
@app.post("/broadcasts")
def create_broadcast(payload: dict):
    return {"success": True, "message": "Broadcast received"}

# WEATHER API ROUTE
@app.get("/weather/")
def get_weather():
    try:
        response = httpx.get("https://api.open-meteo.com/v1/forecast?latitude=14.6958&longitude=121.1207&current=temperature_2m,precipitation")
        data = response.json()
        return {
            "temperature": data["current"]["temperature_2m"],
            "precipitation": data["current"]["precipitation"]
        }
    except Exception:
        return {"temperature": 28.0, "precipitation": 0.0}

def get_severity_style(severity_str: str):
    s = (severity_str or "").lower()
    if "impassable" in s:
        return 4, "#8B0000"  # Dark Red
    elif "waist" in s:
        return 3, "#D32F2F"  # Red
    elif "knee" in s:
        return 2, "#FFA000"  # Yellow/Orange
    else:
        return 1, "#388E3C"  # Green (Ankle-Deep)

# SATELLITE & GEOJSON FLOOD MAPPING ROUTES (FLOMPY & Leaflet Compatible)
@app.get("/api/mapping/flood-geojson")
@app.get("/api/api/mapping/flood-geojson")
@app.get("/mapping/flood-geojson")
def get_flood_geojson(db: Session = Depends(get_db)):
    """
    Returns FLOMPY-compatible GeoJSON features dynamically generated from active
    flood reports in San Mateo, Rizal.
    Color is based on flood severity; opacity/thickness scales with report count.
    """
    reports = db.query(FloodReport).order_by(FloodReport.timestamp.asc()).all()
    if not reports:
        return {
            "type": "FeatureCollection",
            "features": []
        }

    grouped = {}
    for r in reports:
        lat = r.lat or 14.6958
        lon = r.lon or 121.1207
        key = f"{lat:.4f},{lon:.4f}"
        rank, color = get_severity_style(r.severity)

        if key not in grouped:
            grouped[key] = {
                "lat": lat,
                "lon": lon,
                "location": r.location,
                "severity": r.severity,
                "notes": r.notes or "",
                "count": 1,
                "rank": rank,
                "color": color,
            }
        else:
            grouped[key]["count"] += 1
            if rank >= grouped[key]["rank"]:
                grouped[key]["rank"] = rank
                grouped[key]["color"] = color
                grouped[key]["severity"] = r.severity
                grouped[key]["notes"] = r.notes or ""

    features = []
    for g in grouped.values():
        count = g["count"]
        fill_opacity = min(0.22 + (count - 1) * 0.16, 0.78)
        stroke_weight = min(1.5 + (count - 1) * 1.2, 6.0)

        features.append({
            "type": "Feature",
            "properties": {
                "name": g["location"],
                "severity": g["severity"],
                "notes": g["notes"],
                "reportCount": count,
                "color": g["color"],
                "fillOpacity": round(fill_opacity, 2),
                "strokeWeight": round(stroke_weight, 2),
                "radius": 220,
                "source": "FLOMPY Flood Inundation & Community SAR Mapping"
            },
            "geometry": {
                "type": "Point",
                "coordinates": [g["lon"], g["lat"]]
            }
        })

    return {
        "type": "FeatureCollection",
        "features": features
    }
