from fastapi import FastAPI, HTTPException, Depends
from sqlalchemy import create_engine, Column, Integer, String, Boolean, ForeignKey, DateTime, Float, func
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, Session
from datetime import datetime
from pydantic import BaseModel
from typing import Optional, List
import httpx
import uuid

app = FastAPI(title="FloodGuard Backend", version="2.0.0")

# SQLite Database setup
SQLALCHEMY_DATABASE_URL = "sqlite:///./floodguard.db"
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

Base.metadata.create_all(engine)

# --- PYDANTIC SCHEMAS ---
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
    email: Optional[str] = ""

class UserPasswordUpdate(BaseModel):
    username: str
    currentPassword: str
    newPassword: str

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

def validate_contact_number(mobile: str) -> bool:
    clean = (mobile or "").strip().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
    if clean.startswith("09"):
        return len(clean) == 11 and clean.isdigit()
    elif clean.startswith("+63"):
        rest = clean[3:]
        return (len(rest) in (10, 11)) and rest.isdigit()
    return False

@app.post("/api/auth/register")
def register_user(user: UserRegister, db: Session = Depends(get_db)):
    print(f"📥 Registration Request: username='{user.username}', email='{user.email}'")
    if user.mobile and not validate_contact_number(user.mobile):
        raise HTTPException(
            status_code=400,
            detail="Contact number must start with +63 or 09 and consist of 11 digits (e.g. 09123456789 or +639123456789)"
        )
    existing = db.query(User).filter((User.username == user.username) | (User.email == user.email)).first()
    if existing:
        print(f"⚠️ User '{user.username}' already exists in SQLite database!")
        return {"success": True, "message": "User already registered", "user": user.dict()}
    
    user_data = user.dict()
    # Hash password if not already SHA-256 (64 hex characters)
    if len(user_data["password"]) != 64:
        user_data["password"] = hash_password(user_data["password"])

    new_user = User(**user_data)
    db.add(new_user)
    db.commit()
    db.refresh(new_user)
    print(f"✅ User '{user.username}' successfully saved to SQLite database (id={new_user.id})!")
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
    if profile.mobile and not validate_contact_number(profile.mobile):
        raise HTTPException(
            status_code=400,
            detail="Contact number must start with +63 or 09 and consist of 11 digits (e.g. 09123456789 or +639123456789)"
        )
    user.fullName = profile.fullName
    user.gender = profile.gender
    user.mobile = profile.mobile
    user.email = profile.email
    db.commit()
    db.refresh(user)
    return {"success": True, "message": "Profile updated successfully"}

@app.put("/api/auth/password")
def change_password(payload: UserPasswordUpdate, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.username == payload.username).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    hashed_current = hash_password(payload.currentPassword)
    if user.password != payload.currentPassword and user.password != hashed_current:
        raise HTTPException(status_code=400, detail="Current password is incorrect")

    new_hashed = hash_password(payload.newPassword) if len(payload.newPassword) != 64 else payload.newPassword
    user.password = new_hashed
    db.commit()
    db.refresh(user)
    return {"success": True, "message": "Password changed successfully"}

@app.get("/api/users")
def get_users(db: Session = Depends(get_db)):
    users = db.query(User).all()
    user_list = [{
        "id": str(u.id),
        "fullName": u.fullName,
        "gender": u.gender,
        "mobile": u.mobile,
        "email": u.email,
        "username": u.username
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

    # 2. Enforce mandatory flood photo attachment
    if not report.photoUri or not str(report.photoUri).strip():
        raise HTTPException(
            status_code=400,
            detail="A flood-related photo is required to submit a report."
        )

    # 3. Enforce 4 reports per day per user limit (resets daily)
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
    reporter = (report.username or "anonymous").strip()
    
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
        reporterUsername=reporter
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
