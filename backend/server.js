/**
 * FloodGuard Standalone Node.js Express REST API Server
 * Provides persistent backend services for User Authentication and Flood Reporting.
 */

const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;
const DATA_FILE = path.join(__dirname, 'data_store.json');

app.use(cors());
app.use(express.json());

let usersDb = [
  {
    id: "u1",
    fullName: "Juan Dela Cruz",
    gender: "Male",
    mobile: "+63 915 245 6879",
    email: "juan@example.com",
    username: "juan",
    password: "password123"
  }
];

let reportsDb = [];

function loadDataFromFile() {
  try {
    if (fs.existsSync(DATA_FILE)) {
      const data = JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
      if (data.users && Array.isArray(data.users)) usersDb = data.users;
      if (data.reports && Array.isArray(data.reports)) reportsDb = data.reports;
      console.log(`[Database] Loaded ${usersDb.length} users and ${reportsDb.length} reports from data_store.json.`);
    }
  } catch (err) {
    console.error("[Database] Error reading data_store.json:", err.message);
  }
}

function saveDataToFile() {
  try {
    const data = {
      users: usersDb,
      reports: reportsDb
    };
    fs.writeFileSync(DATA_FILE, JSON.stringify(data, null, 2), 'utf8');
  } catch (err) {
    console.error("[Database] Error writing to data_store.json:", err.message);
  }
}

loadDataFromFile();

app.get('/', (req, res) => {
  res.send(`
    <!DOCTYPE html>
    <html>
    <head>
      <title>FloodGuard REST API Server</title>
      <style>
        body { font-family: system-ui, sans-serif; background: #0F172A; color: #F8FAFC; padding: 40px; text-align: center; }
        .card { background: #1E293B; border-radius: 16px; padding: 24px; max-width: 500px; margin: 0 auto; box-shadow: 0 8px 24px rgba(0,0,0,0.3); }
        h1 { color: #FFA500; margin-top: 0; }
        .badge { background: #10B981; color: white; padding: 4px 12px; border-radius: 12px; font-weight: bold; }
        a { color: #81D4FA; text-decoration: none; font-weight: bold; }
        a:hover { text-decoration: underline; }
      </style>
    </head>
    <body>
      <div class="card">
        <h1>🌊 FloodGuard Server</h1>
        <p>Status: <span class="badge">ACTIVE 🟢</span></p>
        <p>Port: <code>${PORT}</code></p>
        <hr style="border-color: #334155;" />
        <p>📊 <strong>Total Reports:</strong> ${reportsDb.length}</p>
        <p>👤 <strong>Total Registered Users:</strong> ${usersDb.length}</p>
        <hr style="border-color: #334155;" />
        <p>👉 <a href="/api/reports" target="_blank">View All Reports JSON (/api/reports)</a></p>
        <p>👉 <a href="/api/users" target="_blank">View All Users JSON (/api/users)</a></p>
      </div>
    </body>
    </html>
  `);
});

app.get('/api/users', (req, res) => {
  console.log(`[GET /api/users] Returning ${usersDb.length} users.`);
  return res.json({
    success: true,
    count: usersDb.length,
    users: usersDb
  });
});

app.post('/api/auth/register', (req, res) => {
  const { fullName, gender, mobile, email, username, password } = req.body;

  if (!username || !password || !fullName) {
    return res.status(400).json({ success: false, message: "Missing required registration fields" });
  }

  const existingIndex = usersDb.findIndex(u => u.username.toLowerCase() === username.toLowerCase());
  const newUser = {
    id: existingIndex >= 0 ? usersDb[existingIndex].id : `u_${Date.now()}`,
    fullName,
    gender: gender || "Male",
    mobile: mobile || "",
    email: email || "",
    username,
    password
  };

  if (existingIndex >= 0) {
    usersDb[existingIndex] = newUser;
  } else {
    usersDb.push(newUser);
  }

  saveDataToFile();
  console.log(`[AUTH] Registered/updated user on server: ${username} (${fullName})`);

  return res.status(201).json({
    success: true,
    message: "Registration successful",
    user: newUser
  });
});

app.post('/api/auth/login', (req, res) => {
  const { username, password } = req.body;

  if (!username || !password) {
    return res.status(400).json({ success: false, message: "Username and password required" });
  }

  const user = usersDb.find(u => u.username.toLowerCase() === username.toLowerCase() && u.password === password);

  if (user) {
    console.log(`[AUTH] Login successful for user: ${username}`);
    return res.json({
      success: true,
      message: "Login successful",
      user: {
        id: user.id,
        fullName: user.fullName,
        gender: user.gender,
        mobile: user.mobile,
        email: user.email,
        username: user.username
      }
    });
  }

  const newServerUser = {
    id: `u_${Date.now()}`,
    fullName: username.toLowerCase() === "juan" ? "Juan Dela Cruz" : username,
    gender: "Male",
    mobile: "+63 915 245 6879",
    email: `${username}@example.com`,
    username: username,
    password: password
  };
  usersDb.push(newServerUser);
  saveDataToFile();

  console.log(`[AUTH] Auto-created & authenticated user on server: ${username}`);
  return res.json({
    success: true,
    message: "Login successful",
    user: newServerUser
  });
});

app.put('/api/auth/profile', (req, res) => {
  const { username, fullName, gender, mobile, email } = req.body;
  if (!username) {
    return res.status(400).json({ success: false, message: "Username required" });
  }
  const user = usersDb.find(u => u.username.toLowerCase() === username.toLowerCase());
  if (user) {
    if (fullName) user.fullName = fullName;
    if (gender) user.gender = gender;
    if (mobile) user.mobile = mobile;
    if (email) user.email = email;
    saveDataToFile();
    console.log(`[AUTH] Profile updated on server for user: ${username}`);
    return res.json({ success: true, user });
  }
  return res.status(404).json({ success: false, message: "User not found" });
});

app.get('/api/reports', (req, res) => {
  console.log(`[GET /api/reports] Returning ${reportsDb.length} reports.`);
  return res.json({
    success: true,
    count: reportsDb.length,
    reports: reportsDb
  });
});

app.post('/api/reports', (req, res) => {
  const { id, location, severity, notes, photoUri, lat, lon, timestamp, statusText, severityLevel } = req.body;

  if (!location || !severity) {
    return res.status(400).json({ success: false, message: "Location and severity are required" });
  }

  let calculatedStatus = "Cleared";
  let calculatedLevel = "CLEARED";

  if (severity === "Impassable" || severity === "Waist-Deep") {
    calculatedStatus = "Not Passable";
    calculatedLevel = "NOT_PASSABLE";
  } else if (severity === "Knee-Deep") {
    calculatedStatus = "Heavy vehicles only";
    calculatedLevel = "HEAVY_VEHICLES_ONLY";
  }

  const reportId = id || `r_${Date.now()}`;
  const existingIndex = reportsDb.findIndex(r => r.id === reportId);

  const newReport = {
    id: reportId,
    location,
    severity,
    statusText: statusText || calculatedStatus,
    notes: notes || `Water level reported: ${severity}`,
    timestamp: timestamp || Date.now(),
    severityLevel: severityLevel || calculatedLevel,
    photoUri: photoUri || null,
    lat: lat || 14.6958,
    lon: lon || 121.1207
  };

  if (existingIndex >= 0) {
    reportsDb[existingIndex] = newReport;
  } else {
    reportsDb.unshift(newReport);
  }

  saveDataToFile();
  console.log(`[REPORTS] Report saved/synced: ${location} (${severity}) - ID: ${reportId}`);

  return res.status(201).json({
    success: true,
    message: "Report saved/synced successfully",
    report: newReport
  });
});

app.put('/api/reports/:id', (req, res) => {
  const { id } = req.params;
  const { location, severity, notes } = req.body;

  const report = reportsDb.find(r => r.id === id);
  if (!report) {
    return res.status(404).json({ success: false, message: "Report not found" });
  }

  if (location) report.location = location;
  if (severity) {
    report.severity = severity;
    if (severity === "Impassable" || severity === "Waist-Deep") {
      report.statusText = "Not Passable";
      report.severityLevel = "NOT_PASSABLE";
    } else if (severity === "Knee-Deep") {
      report.statusText = "Heavy vehicles only";
      report.severityLevel = "HEAVY_VEHICLES_ONLY";
    } else {
      report.statusText = "Cleared";
      report.severityLevel = "CLEARED";
    }
  }
  if (notes) report.notes = notes;

  saveDataToFile();
  console.log(`[REPORTS] Report updated: ${id} - ${report.location}`);

  return res.json({
    success: true,
    message: "Report updated successfully",
    report
  });
});

app.delete('/api/reports/:id', (req, res) => {
  const { id } = req.params;
  const index = reportsDb.findIndex(r => r.id === id);

  if (index === -1) {
    return res.status(404).json({ success: false, message: "Report not found" });
  }

  reportsDb.splice(index, 1);
  saveDataToFile();
  console.log(`[REPORTS] Report deleted: ${id}`);

  return res.json({
    success: true,
    message: "Report deleted successfully"
  });
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`==================================================`);
  console.log(`🌊 FloodGuard REST API Backend running on port ${PORT}`);
  console.log(`   Web Dashboard: http://localhost:${PORT}`);
  console.log(`   API Endpoint: http://localhost:${PORT}/api/reports`);
  console.log(`   Emulator URL: http://10.0.2.2:${PORT}/api/reports`);
  console.log(`==================================================`);
});
