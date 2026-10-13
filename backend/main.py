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
@app.post("/api/auth/register")
def register_user(user: UserRegister, db: Session = Depends(get_db)):
    existing = db.query(User).filter((User.username == user.username) | (User.email == user.email)).first()
    if existing:
        raise HTTPException(status_code=400, detail="Username or email already exists")
    
    new_user = User(**user.dict())
    db.add(new_user)
    db.commit()
    db.refresh(new_user)
    return {"success": True, "message": "User registered successfully", "user": user.dict()}

@app.post("/api/auth/login")
def login_user(credentials: UserLogin, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.username == credentials.username, User.password == credentials.password).first()
    if not user:
        raise HTTPException(status_code=401, detail="Invalid username or password")
    
    user_dict = {
        "fullName": user.fullName,
        "gender": user.gender,
        "mobile": user.mobile,
        "email": user.email,
        "username": user.username
    }
    return {"success": True, "message": "Login successful", "user": user_dict}

@app.get("/api/users")
def get_users(db: Session = Depends(get_db)):
    users = db.query(User).all()
    user_list = [{
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
            "lon": r.lon
        })
    return {"reports": report_list}

@app.post("/api/reports")
def create_report(report: FloodReportCreate, db: Session = Depends(get_db)):
    report_id = report.id or str(uuid.uuid4())
    timestamp = report.timestamp or int(datetime.now().timestamp() * 1000)
    
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
        lon=report.lon or 121.1207
    )
    db.add(db_report)
    db.commit()
    db.refresh(db_report)
    
    return {"success": True, "report": report.dict()}

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