# NutriTrace — Complete Setup Guide

This guide covers setting up the NutriTrace backend and Android app on a new machine from scratch.

---

## Prerequisites

| Software | Version | Download |
|----------|---------|----------|
| Python | 3.10+ | https://www.python.org/downloads/ |
| XAMPP | Any recent | https://www.apachefriends.org/ |
| Android Studio | Latest | https://developer.android.com/studio |
| Git | Any | https://git-scm.com/ |

---

## Step 1: Clone the Repository

```bash
git clone <your-repo-url>
cd Nutritrace
```

---

## Step 2: Set Up MySQL (XAMPP)

1. Install XAMPP and open the XAMPP Control Panel
2. Start **MySQL** (click "Start" next to MySQL)
3. The database `nutritrace_db` and all 8 tables will be auto-created when the backend starts — no manual SQL needed

---

## Step 3: Google Cloud Vision API (OCR)

This is used to extract text from ingredient label photos.

1. Go to https://console.cloud.google.com/
2. Create a new project (or use existing)
3. Enable the **Cloud Vision API**: APIs & Services → Library → search "Cloud Vision API" → Enable
4. Create a service account: IAM & Admin → Service Accounts → Create
   - Name: `nutritrace-vision`
   - Role: `Cloud Vision API User` (or Project → Editor)
5. Create a JSON key: click the service account → Keys → Add Key → Create new key → JSON
6. Download the JSON file and place it in the `backend/` folder
7. Note the full path — you'll need it for `.env`

---

## Step 4: Google Gemini API Key (AI Analysis)

This powers the ingredient analysis and comparison features.

1. Go to https://aistudio.google.com/apikey
2. Click "Create API Key"
3. Select your Google Cloud project
4. Copy the API key — you'll need it for `.env`

---

## Step 5: Gmail App Password (OTP Emails)

This sends password reset OTP codes to users.

1. Go to https://myaccount.google.com/security
2. Enable 2-Step Verification (if not already)
3. Go to https://myaccount.google.com/apppasswords
4. Select app: "Mail", device: "Other" → name it "NutriTrace"
5. Copy the 16-character app password (e.g., `abcd efgh ijkl mnop`)
6. Note the Gmail address and app password — you'll need them for `.env`

---

## Step 6: Configure Backend `.env`

Create/edit the file `backend/.env`:

```env
# Database (XAMPP defaults)
DB_HOST=localhost
DB_USER=root
DB_PASSWORD=
DB_NAME=nutritrace_db

# JWT Authentication
JWT_SECRET_KEY=<generate-a-random-64-char-hex-string>
JWT_EXPIRY_DAYS=30

# Google Cloud Vision (OCR)
GOOGLE_APPLICATION_CREDENTIALS=D:/path/to/your/service-account-key.json

# Google Gemini AI
GEMINI_API_KEY=<your-gemini-api-key>

# Email (SMTP)
SMTP_SERVER=smtp.gmail.com
SMTP_PORT=587
SMTP_EMAIL=<your-gmail@gmail.com>
SMTP_PASSWORD=<your-16-char-app-password>

# App
UPLOAD_FOLDER=uploads
MAX_CONTENT_LENGTH=10485760
```

### Generating a JWT Secret Key

Run this in terminal:
```bash
python -c "import secrets; print(secrets.token_hex(32))"
```

### Important Notes

- Use **forward slashes** (`/`) in the `GOOGLE_APPLICATION_CREDENTIALS` path, even on Windows. Backslashes cause escape sequence issues.
- The `.env` file is gitignored — it will NOT be in the repo. You must create it manually on each machine.
- Never commit API keys or passwords to git.

---

## Step 7: Install Python Dependencies

```bash
cd backend
pip install -r requirements.txt
```

This installs: Flask, MySQL connector, bcrypt, PyJWT, Google Cloud Vision, Google GenAI, Pillow, python-dotenv, flask-cors.

---

## Step 8: Find Your Machine's IP Address

The Android app connects to the backend via your local network IP.

**Windows:**
```bash
ipconfig
```
Look for `IPv4 Address` under your WiFi adapter (e.g., `192.168.1.100` or `10.65.202.6`).

**Mac/Linux:**
```bash
ifconfig | grep "inet "
```

Note this IP — you need it for the next step.

---

## Step 9: Configure Android App IP

Two files need your machine's IP:

### File 1: `app/src/main/java/com/simats/nutritrace/ApiClient.kt`

Find line ~15:
```kotlin
private const val BASE_URL = "http://192.168.1.100:5000"
```
Replace `192.168.1.100` with your machine's IP.

### File 2: `app/src/main/res/xml/network_security_config.xml`

Find line ~4:
```xml
<domain includeSubdomains="true">192.168.1.100</domain>
```
Replace `192.168.1.100` with the same IP.

---

## Step 10: Start the Backend

```bash
cd backend
python main.py
```

You should see:
```
Database 'nutritrace_db' ready.
All 8 tables ready.
 * Running on all addresses (0.0.0.0)
 * Running on http://127.0.0.1:5000
 * Running on http://<your-ip>:5000
```

### Quick Verification

Open a browser and go to `http://<your-ip>:5000/` — you should see:
```json
{"message": "NutriTrace API is running", "version": "1.0"}
```

---

## Step 11: Build and Run the Android App

### Option A: Run in Android Studio
1. Open the project in Android Studio
2. Connect your Android device via USB (or use emulator)
3. Ensure the device is on the **same WiFi network** as your laptop
4. Click Run (green play button)

### Option B: Build APK
```bash
./gradlew assembleRelease
```
APK will be at: `app/build/outputs/apk/release/app-release.apk`

Transfer to phone and install. You may need to enable "Install from unknown sources".

### Keystore Info (for signing)
- File: `nutritrace-release.jks` (project root)
- Alias: `nutritrace`
- Store password: `nutritrace123`
- Key password: `nutritrace123`

---

## Troubleshooting

### "Connection refused" on Android app
- Ensure backend is running (`python main.py`)
- Ensure phone and laptop are on the **same WiFi network**
- Verify the IP in `ApiClient.kt` matches your laptop's current IP
- Check Windows Firewall isn't blocking port 5000

### "Analysis failed" on scan
- Ensure `GOOGLE_APPLICATION_CREDENTIALS` path is correct and uses forward slashes
- Ensure the Cloud Vision API is enabled in your Google Cloud project
- Ensure `GEMINI_API_KEY` is valid
- Check the backend terminal for detailed error messages

### "OTP email not sent"
- Ensure 2-Step Verification is enabled on the Gmail account
- Ensure the App Password is correct (16 chars, no spaces needed in `.env`)
- Check if Gmail is blocking: https://myaccount.google.com/lesssecureapps

### Database gets wiped
- This should NOT happen with the current code (`CREATE TABLE IF NOT EXISTS`)
- If it does, check if someone ran `init_db()` with the old DROP TABLE code

### IP changed (WiFi reconnect)
- Your IP may change when you reconnect to WiFi
- Re-run `ipconfig`, update `ApiClient.kt` and `network_security_config.xml`
- Rebuild the app

---

## Project Structure

```
Nutritrace/
├── backend/
│   ├── main.py              # Flask app entry point
│   ├── config.py             # Loads .env variables
│   ├── database.py           # MySQL connection + table creation
│   ├── auth.py               # Auth endpoints (signup, login, OTP, passwords)
│   ├── user.py               # Profile endpoints
│   ├── scan.py               # Scan & AI analysis endpoints
│   ├── compare.py            # Product comparison endpoint
│   ├── dashboard.py          # Home dashboard endpoint
│   ├── middleware.py          # JWT @token_required decorator
│   ├── validators.py         # Input validation
│   ├── ocr_service.py        # Google Cloud Vision OCR
│   ├── ai_service.py         # Google Gemini AI analysis
│   ├── email_service.py      # Gmail SMTP for OTP
│   ├── requirements.txt      # Python dependencies
│   ├── .env                  # Secrets (NOT in git)
│   └── uploads/              # Stored scan images
├── app/                      # Android app source
│   └── src/main/java/com/simats/nutritrace/
│       ├── ApiClient.kt      # HTTP client (BASE_URL is here)
│       ├── HomeActivity.kt
│       ├── CompareActivity.kt
│       ├── ...               # 25 Activity files
│       └── ScanData.kt
├── nutritrace-release.jks    # Signing keystore (NOT in git)
├── SETUP_GUIDE.md            # This file
└── BACKEND_PRD.md            # Product requirements document
```

---

## API Endpoints Reference

| Method | Endpoint | Auth | Purpose |
|--------|----------|------|---------|
| GET | `/` | No | Health check |
| POST | `/auth/signup` | No | Register + auto-login |
| POST | `/auth/login` | No | Login, returns JWT |
| POST | `/auth/forgot-password` | No | Send OTP email |
| POST | `/auth/verify-otp` | No | Verify OTP |
| POST | `/auth/reset-password` | No | Set new password |
| POST | `/auth/change-password` | Yes | Change password |
| GET | `/user/profile` | Yes | Get user + health profile |
| PUT | `/user/profile` | Yes | Update name/phone/email |
| POST | `/user/health-profile` | Yes | Save health conditions |
| DELETE | `/user/account` | Yes | Delete account |
| POST | `/scan/analyze` | Yes | Upload image → OCR → AI |
| GET | `/scan/history` | Yes | All scans (with ?search=) |
| GET | `/scan/latest` | Yes | Last 3 scans |
| GET | `/scan/{id}` | Yes | Full scan detail |
| PUT | `/scan/{id}` | Yes | Update product/brand name |
| DELETE | `/scan/{id}` | Yes | Delete scan |
| POST | `/compare` | Yes | Compare two scans via AI |
| GET | `/dashboard` | Yes | Home screen data |
| GET | `/uploads/{filename}` | No | Serve scan images |

---

## Quick Start Checklist

- [ ] XAMPP MySQL running
- [ ] `backend/.env` configured with all keys
- [ ] Google Cloud Vision API enabled + service account JSON downloaded
- [ ] Gemini API key from AI Studio
- [ ] Gmail App Password generated
- [ ] `pip install -r requirements.txt` completed
- [ ] `python main.py` starts without errors
- [ ] `http://<ip>:5000/` returns JSON in browser
- [ ] `ApiClient.kt` BASE_URL updated with machine IP
- [ ] `network_security_config.xml` updated with machine IP
- [ ] Android device on same WiFi as laptop
- [ ] App builds and connects to backend
