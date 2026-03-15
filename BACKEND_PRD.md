# NutriTrace Backend PRD (Product Requirements Document)

**Version:** 1.0
**Date:** 2026-03-15
**Status:** Draft
**App Platform:** Android (Kotlin) | **Backend:** Python (Flask)
**Database:** MySQL (via XAMPP)

---

## 1. Executive Summary

NutriTrace is a mobile health app that scans packaged food ingredient labels, analyzes them using AI, and provides personalized risk assessments based on a user's health profile (conditions, allergies, age group). The app currently has a fully built Android frontend with 25 Activities, but the backend only implements a basic `/signup` endpoint with plain-text password storage.

**This PRD defines the complete backend system needed to make NutriTrace fully functional** — covering authentication, user profiles, health data, AI-powered ingredient scanning, product comparison, and scan history management.

---

## 2. Current State Analysis

### 2.1 What Exists (Backend)
| Component | Status | Notes |
|-----------|--------|-------|
| Flask server | Done | Runs on port 5000 |
| MySQL connection | Done | `nutritrace_db` via XAMPP |
| `users` table | Done | id, fullname, phone, email, password, created_at |
| `POST /signup` | Done | Basic validation, duplicate email check |
| Password hashing | Missing | Stored in plain text |

### 2.2 What Exists (Frontend — Already Built)
| Screen | Backend Integration Status |
|--------|---------------------------|
| Splash → Login → Register | Register calls nothing (validation only client-side) |
| Forgot Password → OTP → New Password | Fully mocked, no backend |
| Age → Health → Sensitivity → Success | Data not persisted to backend |
| Home (dashboard, last scans, risk card) | Reads from SharedPreferences only |
| Scan (camera → review → analyze → results) | Mock data, no actual analysis |
| Compare (side-by-side products) | UI only, no backend logic |
| Scan History | SharedPreferences only |
| Profile (personal details, change password) | No backend calls |
| Delete Account | UI only |

### 2.3 Frontend Tech Context
- **HTTP Client:** OkHttp 4.12.0 (already in dependencies)
- **JSON:** Gson 2.10.1
- **Local Storage:** SharedPreferences (`NutriTracePrefs`)
- **Camera:** CameraX 1.5.3 (captures ingredient label images)
- **Data Model:** `ScanData(productName, brandName, score, riskLevel, time)`

---

## 3. Database Schema Design

### 3.1 Table: `users` (Modify Existing)
```sql
CREATE TABLE users (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    fullname        VARCHAR(255) NOT NULL,
    phone           VARCHAR(50) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,          -- bcrypt hash (rename from password)
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 3.2 Table: `health_profiles`
```sql
CREATE TABLE health_profiles (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT NOT NULL UNIQUE,
    age_group       ENUM('Child', 'Teen', 'Adult', 'Senior') NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

### 3.3 Table: `user_health_conditions`
```sql
CREATE TABLE user_health_conditions (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT NOT NULL,
    condition_key   VARCHAR(100) NOT NULL,
    -- Values: 'blood_sugar', 'cardiovascular', 'hormonal', 'digestive',
    --         'allergy_immune', 'no_specific', 'not_sure'
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE(user_id, condition_key)
);
```

### 3.4 Table: `user_sensitivities`
```sql
CREATE TABLE user_sensitivities (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT NOT NULL,
    sensitivity     VARCHAR(255) NOT NULL,
    is_custom       BOOLEAN DEFAULT FALSE,
    -- Predefined: 'Dairy', 'Nuts', 'Gluten', 'Sulfites',
    --             'Artificial Colors', 'Artificial Sweeteners'
    -- Custom: any user-defined string
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE(user_id, sensitivity)
);
```

### 3.5 Table: `scans`
```sql
CREATE TABLE scans (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT NOT NULL,
    product_name    VARCHAR(255),
    brand_name      VARCHAR(255),
    score           INT NOT NULL,                   -- 0-100
    risk_level      ENUM('LOW', 'MODERATE', 'HIGH') NOT NULL,
    image_path      VARCHAR(500),                   -- path to stored image
    raw_ocr_text    TEXT,                            -- extracted text from image
    ai_analysis     JSON,                            -- full AI response stored
    scanned_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

### 3.6 Table: `scan_ingredients`
```sql
CREATE TABLE scan_ingredients (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    scan_id         INT NOT NULL,
    ingredient_name VARCHAR(255) NOT NULL,
    status          ENUM('SAFE', 'CAUTION', 'AVOID') NOT NULL,
    reason          TEXT,                            -- why this status for this user
    FOREIGN KEY (scan_id) REFERENCES scans(id) ON DELETE CASCADE
);
```

### 3.7 Table: `comparisons`
```sql
CREATE TABLE comparisons (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT NOT NULL,
    scan_id_a       INT NOT NULL,
    scan_id_b       INT NOT NULL,
    chosen_product  ENUM('A', 'B') DEFAULT NULL,
    ai_summary      TEXT,                            -- AI comparison summary
    compared_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (scan_id_a) REFERENCES scans(id) ON DELETE CASCADE,
    FOREIGN KEY (scan_id_b) REFERENCES scans(id) ON DELETE CASCADE
);
```

### 3.8 Table: `password_reset_otps`
```sql
CREATE TABLE password_reset_otps (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT NOT NULL,
    otp_code        VARCHAR(6) NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    is_used         BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

---

## 4. API Endpoints Specification

### 4.1 Authentication Module

#### `POST /auth/signup`
**Purpose:** Register a new user account.
**Request:**
```json
{
    "fullname": "John Doe",
    "phone": "9876543210",
    "email": "john@example.com",
    "password": "SecurePass1!",
    "confirm_password": "SecurePass1!"
}
```
**Validations:**
- All fields required
- Email format validation
- Password: min 8 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special char
- Phone: exactly 10 digits
- Fullname: letters and spaces only
- Email uniqueness check
- **Hash password with bcrypt before storing**

**Response (201):**
```json
{
    "success": true,
    "message": "Account created successfully!",
    "user_id": 1
}
```

---

#### `POST /auth/login`
**Purpose:** Authenticate user and return session token.
**Request:**
```json
{
    "email": "john@example.com",
    "password": "SecurePass1!"
}
```
**Logic:**
- Find user by email
- Verify password against bcrypt hash
- Generate JWT token (or session token)

**Response (200):**
```json
{
    "success": true,
    "message": "Login successful",
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "user": {
        "id": 1,
        "fullname": "John Doe",
        "email": "john@example.com",
        "phone": "9876543210",
        "has_health_profile": true
    }
}
```

---

#### `POST /auth/forgot-password`
**Purpose:** Send OTP to user's email for password reset.
**Request:**
```json
{
    "email": "john@example.com"
}
```
**Logic:**
- Check email exists
- Generate 4-digit OTP
- Store OTP with 10-minute expiry in `password_reset_otps`
- Send OTP via email (SMTP)

**Response (200):**
```json
{
    "success": true,
    "message": "OTP sent to your email"
}
```

---

#### `POST /auth/verify-otp`
**Purpose:** Verify the OTP code entered by user.
**Request:**
```json
{
    "email": "john@example.com",
    "otp": "1234"
}
```
**Logic:**
- Find latest unused OTP for email
- Check not expired
- Mark as used

**Response (200):**
```json
{
    "success": true,
    "message": "OTP verified",
    "reset_token": "temp-token-for-password-reset"
}
```

---

#### `POST /auth/reset-password`
**Purpose:** Set new password after OTP verification.
**Request:**
```json
{
    "email": "john@example.com",
    "reset_token": "temp-token-for-password-reset",
    "new_password": "NewSecure2!",
    "confirm_password": "NewSecure2!"
}
```
**Validations:** Same password complexity rules as signup.

**Response (200):**
```json
{
    "success": true,
    "message": "Password reset successfully"
}
```

---

#### `POST /auth/change-password`
**Purpose:** Change password from within the app (requires current password).
**Headers:** `Authorization: Bearer <token>`
**Request:**
```json
{
    "current_password": "OldPass1!",
    "new_password": "NewPass2!",
    "confirm_password": "NewPass2!"
}
```
**Logic:**
- Verify current password matches
- Validate new password complexity
- Update hash

**Response (200):**
```json
{
    "success": true,
    "message": "Password changed successfully"
}
```

---

### 4.2 User Profile Module

#### `GET /user/profile`
**Purpose:** Fetch user's personal details and health profile.
**Headers:** `Authorization: Bearer <token>`

**Response (200):**
```json
{
    "success": true,
    "user": {
        "id": 1,
        "fullname": "John Doe",
        "phone": "9876543210",
        "email": "john@example.com"
    },
    "health_profile": {
        "age_group": "Adult",
        "conditions": ["blood_sugar", "cardiovascular"],
        "sensitivities": ["Dairy", "Gluten", "Custom Ingredient"]
    }
}
```

---

#### `PUT /user/profile`
**Purpose:** Update personal details (fullname, phone, email).
**Headers:** `Authorization: Bearer <token>`
**Request:**
```json
{
    "fullname": "John Smith",
    "phone": "9876543211",
    "email": "john.new@example.com"
}
```
**Validations:** Same as signup fields. If email changes, check uniqueness.

**Response (200):**
```json
{
    "success": true,
    "message": "Profile updated successfully"
}
```

---

#### `POST /user/health-profile`
**Purpose:** Create or update the full health profile (age, conditions, sensitivities).
**Headers:** `Authorization: Bearer <token>`
**Request:**
```json
{
    "age_group": "Adult",
    "conditions": ["blood_sugar", "cardiovascular"],
    "sensitivities": ["Dairy", "Gluten"],
    "custom_sensitivities": ["Soy Lecithin"]
}
```
**Logic:**
- Upsert `health_profiles` record
- Delete existing conditions and sensitivities for user
- Insert new conditions and sensitivities
- Custom sensitivities stored with `is_custom = true`

**Response (200):**
```json
{
    "success": true,
    "message": "Health profile saved successfully"
}
```

---

#### `DELETE /user/account`
**Purpose:** Permanently delete user account and all associated data.
**Headers:** `Authorization: Bearer <token>`
**Request:**
```json
{
    "password": "CurrentPass1!"
}
```
**Logic:**
- Verify password
- CASCADE delete removes: health_profiles, conditions, sensitivities, scans, scan_ingredients, comparisons, OTPs
- Invalidate token

**Response (200):**
```json
{
    "success": true,
    "message": "Account deleted successfully"
}
```

---

### 4.3 Scan & Analysis Module

#### `POST /scan/analyze`
**Purpose:** Upload ingredient label image, extract text via OCR, analyze with AI based on user's health profile.
**Headers:** `Authorization: Bearer <token>`
**Content-Type:** `multipart/form-data`
**Request:**
```
image: <file>  (JPEG/PNG from camera or gallery)
product_name: "Maggi Noodles" (optional, user can set later)
brand_name: "Nestle" (optional)
```

**Backend Processing Pipeline:**
1. **Receive & store image** → Save to `uploads/` directory with unique filename
2. **OCR extraction** → Use Google Cloud Vision API / Tesseract to extract text from ingredient label image
3. **Ingredient parsing** → Parse the raw OCR text to identify individual ingredients
4. **Fetch user health profile** → Get user's age group, conditions, and sensitivities from DB
5. **AI analysis** → Send ingredients + health profile to AI (Gemini API / OpenAI) with a structured prompt:
   - Classify each ingredient as SAFE / CAUTION / AVOID for this specific user
   - Generate a reason for each classification
   - Calculate overall risk score (0-100)
   - Determine risk level (LOW ≥ 70, MODERATE 40-69, HIGH < 40)
   - Generate personalized guidance
6. **Store results** → Save scan and ingredients to DB
7. **Return response**

**Response (200):**
```json
{
    "success": true,
    "scan": {
        "id": 42,
        "product_name": "Maggi Noodles",
        "brand_name": "Nestle",
        "score": 45,
        "risk_level": "MODERATE",
        "scanned_at": "2026-03-15T10:30:00Z",
        "overview": "This product contains several ingredients that may affect your blood sugar levels...",
        "ingredients": [
            {
                "name": "Refined Wheat Flour (Maida)",
                "status": "CAUTION",
                "reason": "High glycemic index — may spike blood sugar levels, relevant for your blood sugar regulation condition."
            },
            {
                "name": "Palm Oil",
                "status": "AVOID",
                "reason": "High in saturated fats — associated with cardiovascular risks, relevant for your cardiovascular condition."
            },
            {
                "name": "Salt",
                "status": "CAUTION",
                "reason": "Excessive sodium intake linked to blood pressure issues."
            },
            {
                "name": "Turmeric",
                "status": "SAFE",
                "reason": "Natural spice with anti-inflammatory properties."
            }
        ],
        "risk_breakdown": {
            "avoid_count": 1,
            "caution_count": 2,
            "safe_count": 1
        },
        "guidance": [
            "Consider limiting consumption due to refined flour content.",
            "Look for alternatives with whole wheat or millet-based noodles.",
            "Monitor blood sugar after consumption if you choose to eat this."
        ]
    }
}
```

---

#### `PUT /scan/{scan_id}`
**Purpose:** Update product name or brand name of a saved scan.
**Headers:** `Authorization: Bearer <token>`
**Request:**
```json
{
    "product_name": "Maggi 2-Minute Noodles",
    "brand_name": "Nestle India"
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "Scan updated successfully"
}
```

---

#### `GET /scan/history`
**Purpose:** Fetch all scans for the authenticated user.
**Headers:** `Authorization: Bearer <token>`
**Query Params:** `?search=maggi` (optional, filter by product/brand name)

**Response (200):**
```json
{
    "success": true,
    "scans": [
        {
            "id": 42,
            "product_name": "Maggi Noodles",
            "brand_name": "Nestle",
            "score": 45,
            "risk_level": "MODERATE",
            "scanned_at": "2026-03-15T10:30:00Z"
        },
        {
            "id": 41,
            "product_name": "Lays Classic",
            "brand_name": "PepsiCo",
            "score": 32,
            "risk_level": "HIGH",
            "scanned_at": "2026-03-14T15:20:00Z"
        }
    ]
}
```

---

#### `GET /scan/{scan_id}`
**Purpose:** Fetch full details of a specific scan (for result page).
**Headers:** `Authorization: Bearer <token>`

**Response (200):** Same structure as the `scan` object in `/scan/analyze` response.

---

#### `DELETE /scan/{scan_id}`
**Purpose:** Delete a specific scan from history.
**Headers:** `Authorization: Bearer <token>`

**Response (200):**
```json
{
    "success": true,
    "message": "Scan deleted successfully"
}
```

---

#### `GET /scan/latest`
**Purpose:** Fetch the 3 most recent scans for the home screen.
**Headers:** `Authorization: Bearer <token>`

**Response (200):**
```json
{
    "success": true,
    "scans": [
        {
            "id": 42,
            "product_name": "Maggi Noodles",
            "brand_name": "Nestle",
            "score": 45,
            "risk_level": "MODERATE",
            "scanned_at": "2026-03-15T10:30:00Z"
        }
    ]
}
```

---

### 4.4 Compare Module

#### `POST /compare`
**Purpose:** Compare two scanned products and get AI recommendation.
**Headers:** `Authorization: Bearer <token>`
**Request:**
```json
{
    "scan_id_a": 42,
    "scan_id_b": 41
}
```
**Logic:**
- Fetch both scans with their ingredients
- Fetch user's health profile
- Send both products' data to AI with prompt: "Compare these two products for a user with [conditions]. Which is the better choice and why?"
- Store comparison result

**Response (200):**
```json
{
    "success": true,
    "comparison": {
        "id": 5,
        "product_a": {
            "id": 42,
            "name": "Maggi Noodles",
            "brand": "Nestle",
            "score": 45,
            "risk_level": "MODERATE"
        },
        "product_b": {
            "id": 41,
            "name": "Lays Classic",
            "brand": "PepsiCo",
            "score": 32,
            "risk_level": "HIGH"
        },
        "recommendation": "A",
        "summary": "Maggi Noodles is the better choice for your health profile. While both products have concerns, Lays Classic contains higher levels of sodium and trans fats which are particularly risky for your cardiovascular condition.",
        "detailed_comparison": {
            "calories": "Product A is moderate, Product B is high",
            "harmful_ingredients": "Product A: 1 AVOID, Product B: 3 AVOID",
            "overall": "Neither product is ideal, but Product A poses fewer risks for your specific health conditions."
        }
    }
}
```

---

#### `POST /compare/inline`
**Purpose:** Compare two products directly from images (without saving scans first — for the compare screen flow where both products are scanned fresh).
**Headers:** `Authorization: Bearer <token>`
**Content-Type:** `multipart/form-data`
**Request:**
```
image_a: <file>
image_b: <file>
product_name_a: "Product A" (optional)
product_name_b: "Product B" (optional)
```
**Logic:**
- Run OCR + AI analysis on both images
- Save both as scans
- Run comparison
- Return comparison result with both scan details

**Response:** Same structure as `POST /compare` but includes full ingredient analysis for both products.

---

### 4.5 Home Dashboard

#### `GET /dashboard`
**Purpose:** Single endpoint for home screen data (reduces multiple API calls).
**Headers:** `Authorization: Bearer <token>`

**Response (200):**
```json
{
    "success": true,
    "user": {
        "fullname": "John Doe",
        "has_health_profile": true
    },
    "latest_scans": [
        {
            "id": 42,
            "product_name": "Maggi Noodles",
            "brand_name": "Nestle",
            "score": 45,
            "risk_level": "MODERATE",
            "scanned_at": "2026-03-15T10:30:00Z"
        }
    ],
    "total_scans": 15,
    "average_score": 58
}
```

---

## 5. External Integrations Required

### 5.1 OCR (Optical Character Recognition)
**Purpose:** Extract text from ingredient label images captured by the phone camera.

**Options (pick one):**
| Option | Pros | Cons |
|--------|------|------|
| Google Cloud Vision API | Most accurate, handles multiple languages, handwriting | Paid after free tier (1000/month free) |
| Tesseract (pytesseract) | Free, open-source, runs locally | Less accurate on curved/blurry labels |
| AWS Textract | Good accuracy, pay-per-use | AWS account required |

**Recommendation:** Start with **Google Cloud Vision API** for accuracy. Ingredient labels can be curved, small-font, and multi-language — Vision API handles these best.

### 5.2 AI/LLM for Ingredient Analysis
**Purpose:** Classify ingredients as SAFE/CAUTION/AVOID based on the user's specific health conditions and generate personalized guidance.

**Options (pick one):**
| Option | Pros | Cons |
|--------|------|------|
| Google Gemini API | Free tier generous, good at structured output | Newer, less community resources |
| OpenAI GPT-4 API | Most capable, extensive documentation | Paid, can be expensive at scale |
| Claude API | Strong reasoning, good at health/science | Paid |

**Recommendation:** **Google Gemini API** — generous free tier, good structured JSON output, and the analysis task is well within its capabilities.

### 5.3 Email Service (for OTP)
**Purpose:** Send password reset OTP codes to users.

**Options:**
| Option | Pros | Cons |
|--------|------|------|
| SMTP (Gmail App Password) | Free, simple to set up | Rate limited, may hit spam |
| SendGrid | 100 emails/day free, reliable | Account setup required |
| AWS SES | Cheap at scale | AWS setup complexity |

**Recommendation:** Start with **SMTP (Gmail)** for simplicity, migrate to SendGrid if needed.

---

## 6. AI Prompt Engineering

### 6.1 Ingredient Analysis Prompt Template
```
You are a food safety and nutrition expert AI. Analyze the following ingredients
for a user with the given health profile.

USER HEALTH PROFILE:
- Age Group: {age_group}
- Health Conditions: {conditions_list}
- Food Sensitivities/Allergies: {sensitivities_list}

INGREDIENTS (extracted from product label):
{raw_ingredient_text}

For each ingredient, provide:
1. ingredient_name: The name of the ingredient
2. status: SAFE, CAUTION, or AVOID
   - SAFE: No known health concerns for this user's profile
   - CAUTION: May have mild effects or should be consumed in moderation
   - AVOID: Directly harmful or triggers known conditions/allergies for this user
3. reason: A short, clear explanation of why this status was assigned,
   specifically referencing the user's conditions if relevant

Also provide:
- overall_score: 0-100 (100 = perfectly safe, 0 = extremely harmful)
- risk_level: LOW (score >= 70), MODERATE (40-69), HIGH (< 40)
- overview: A 2-3 sentence summary of the product's safety for this user
- guidance: 3-5 actionable recommendations

Respond in JSON format only.
```

### 6.2 Comparison Prompt Template
```
You are a food safety and nutrition expert AI. Compare these two food products
for the user with the given health profile.

USER HEALTH PROFILE:
- Age Group: {age_group}
- Health Conditions: {conditions_list}
- Food Sensitivities/Allergies: {sensitivities_list}

PRODUCT A: {product_a_name}
Ingredients: {product_a_ingredients}

PRODUCT B: {product_b_name}
Ingredients: {product_b_ingredients}

Provide:
1. recommendation: "A", "B", or "NEITHER" (if both are equally bad)
2. summary: 2-3 sentence explanation of which is better and why
3. detailed_comparison: Key differences relevant to user's health
4. warnings: Any critical warnings for either product

Respond in JSON format only.
```

---

## 7. Authentication & Security

### 7.1 Password Hashing
- Use **bcrypt** via `flask-bcrypt` library
- Hash on signup, verify on login
- Never store plain-text passwords

### 7.2 JWT Token Authentication
- Use **PyJWT** library
- Token contains: `user_id`, `email`, `exp` (expiry)
- Token expiry: 30 days (mobile app — longer sessions)
- Include in all authenticated requests: `Authorization: Bearer <token>`

### 7.3 Middleware
- Create `@token_required` decorator for protected endpoints
- Extract user_id from token for all authenticated operations
- Return 401 for missing/invalid/expired tokens

### 7.4 OTP Security
- 4-digit numeric OTP
- 10-minute expiry
- Mark as used after verification
- Rate limit: max 3 OTP requests per email per hour

---

## 8. File/Image Storage

### 8.1 Image Upload Handling
- Store uploaded ingredient images in `uploads/` directory on server
- Generate unique filenames: `{user_id}_{timestamp}_{uuid}.jpg`
- Limit file size: 10MB max
- Accept formats: JPEG, PNG
- Serve images via `GET /uploads/{filename}` endpoint (for scan history display)

### 8.2 Directory Structure
```
backend/
├── main.py                 # Flask app entry point
├── database.py             # DB connection and init
├── config.py               # Configuration (secrets, API keys, DB creds)
├── requirements.txt        # Python dependencies
├── models/
│   ├── user.py             # User-related DB operations
│   ├── health_profile.py   # Health profile DB operations
│   ├── scan.py             # Scan DB operations
│   └── comparison.py       # Comparison DB operations
├── routes/
│   ├── auth.py             # Auth endpoints (signup, login, password reset)
│   ├── user.py             # Profile endpoints
│   ├── scan.py             # Scan & analysis endpoints
│   ├── compare.py          # Comparison endpoints
│   └── dashboard.py        # Home dashboard endpoint
├── services/
│   ├── ocr_service.py      # OCR integration (Google Vision / Tesseract)
│   ├── ai_service.py       # AI/LLM integration (Gemini / OpenAI)
│   └── email_service.py    # Email/OTP sending
├── middleware/
│   └── auth.py             # JWT token verification decorator
├── utils/
│   ├── validators.py       # Input validation helpers
│   └── helpers.py          # Misc utility functions
└── uploads/                # Uploaded ingredient images
```

---

## 9. Python Dependencies (Updated requirements.txt)

```
Flask==3.0.3
mysql-connector-python==9.0.0
flask-cors==4.0.0
flask-bcrypt==1.0.1
PyJWT==2.8.0
google-cloud-vision==3.7.2          # OCR (if using Google Vision)
# OR pytesseract==0.3.10            # OCR (if using Tesseract)
google-generativeai==0.5.4          # Gemini AI (if using Gemini)
# OR openai==1.30.0                 # OpenAI (if using GPT)
python-dotenv==1.0.1                # Environment variables
Pillow==10.3.0                      # Image processing
```

---

## 10. Implementation Priority & Phases

### Phase 1: Core Auth (Week 1)
| Task | Priority | Effort |
|------|----------|--------|
| Password hashing (bcrypt) — fix existing signup | P0 | Low |
| `POST /auth/login` with JWT | P0 | Medium |
| `@token_required` middleware | P0 | Low |
| `POST /auth/change-password` | P1 | Low |
| `POST /auth/forgot-password` (OTP + email) | P1 | Medium |
| `POST /auth/verify-otp` | P1 | Low |
| `POST /auth/reset-password` | P1 | Low |
| Wire frontend RegisterActivity → `/auth/signup` | P0 | Low |
| Wire frontend LoginActivity → `/auth/login` | P0 | Low |

### Phase 2: User Profile & Health Data (Week 2)
| Task | Priority | Effort |
|------|----------|--------|
| Create health_profiles, conditions, sensitivities tables | P0 | Low |
| `POST /user/health-profile` | P0 | Medium |
| `GET /user/profile` | P0 | Low |
| `PUT /user/profile` | P1 | Low |
| `DELETE /user/account` | P2 | Low |
| Wire frontend profile setup flow → backend | P0 | Medium |
| Wire frontend PersonalDetailsActivity → backend | P1 | Low |

### Phase 3: Scan & AI Analysis (Week 3-4)
| Task | Priority | Effort |
|------|----------|--------|
| Set up OCR service (Google Vision or Tesseract) | P0 | Medium |
| Set up AI service (Gemini or OpenAI) | P0 | Medium |
| Create scans and scan_ingredients tables | P0 | Low |
| `POST /scan/analyze` (full pipeline) | P0 | High |
| `GET /scan/history` | P0 | Low |
| `GET /scan/{scan_id}` | P0 | Low |
| `GET /scan/latest` | P1 | Low |
| `PUT /scan/{scan_id}` | P2 | Low |
| `DELETE /scan/{scan_id}` | P2 | Low |
| AI prompt engineering & testing | P0 | High |
| Wire frontend scan flow → backend | P0 | High |

### Phase 4: Compare & Dashboard (Week 4-5)
| Task | Priority | Effort |
|------|----------|--------|
| Create comparisons table | P1 | Low |
| `POST /compare` | P1 | Medium |
| `POST /compare/inline` | P1 | High |
| `GET /dashboard` | P1 | Low |
| Wire frontend CompareActivity → backend | P1 | Medium |
| Wire frontend HomeActivity → dashboard API | P1 | Medium |

### Phase 5: Polish & Hardening (Week 5-6)
| Task | Priority | Effort |
|------|----------|--------|
| Error handling across all endpoints | P1 | Medium |
| Input validation hardening | P1 | Low |
| Rate limiting | P2 | Low |
| Logging | P2 | Low |
| Config via environment variables (.env) | P1 | Low |
| CORS configuration for mobile | P1 | Low |

---

## 11. Frontend Wiring Checklist

For each Activity, what API call needs to be added:

| Activity | API Call | Data Flow |
|----------|----------|-----------|
| RegisterActivity | `POST /auth/signup` | Send form data → show success/error |
| LoginActivity | `POST /auth/login` | Send credentials → store token in SharedPreferences → navigate |
| ForgotPasswordActivity | `POST /auth/forgot-password` | Send email → navigate to OTP |
| VerifyOtpActivity | `POST /auth/verify-otp` | Send email + OTP → get reset_token |
| NewPasswordActivity | `POST /auth/reset-password` | Send new password + reset_token |
| AgeSelectionActivity | Collect locally, send at end of flow | — |
| HealthSelectionActivity | Collect locally, send at end of flow | — |
| SensitivitySelectionActivity | Collect locally, send at end of flow | — |
| ProfileSuccessActivity | `POST /user/health-profile` | Send all collected health data |
| HomeActivity | `GET /dashboard` | Populate latest scans, user info |
| ScanIngredientsActivity | Capture image only | — |
| ReviewImageActivity | Confirm image | — |
| AnalyzingActivity | `POST /scan/analyze` | Send image → receive analysis |
| AnalysisResultActivity | Display scan result, `PUT /scan/{id}` for edits | — |
| ScanHistoryActivity | `GET /scan/history?search=` | Populate list |
| CompareActivity | `POST /compare/inline` or `POST /compare` | Send images/scan IDs → display result |
| ProfileActivity | — | Navigation hub |
| PersonalDetailsActivity | `GET /user/profile`, `PUT /user/profile` | Load and save user data |
| ChangePasswordActivity | `POST /auth/change-password` | Send old + new password |
| DeleteAccount (in ProfileActivity) | `DELETE /user/account` | Confirm → delete → logout |

---

## 12. Key Technical Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Database | MySQL (keep existing) | Already set up with XAMPP, team familiarity |
| Auth tokens | JWT | Stateless, works well with mobile apps, no server-side session storage needed |
| Password storage | bcrypt | Industry standard, built-in salt |
| OCR | Google Cloud Vision API | Best accuracy for food labels (small text, curved surfaces) |
| AI/LLM | Google Gemini API | Free tier, good structured output, sufficient for ingredient analysis |
| Email | SMTP (Gmail) → SendGrid later | Start simple, scale when needed |
| Image storage | Local filesystem | Simple for MVP, migrate to cloud storage later if needed |
| API architecture | Flask Blueprints | Clean separation of concerns, modular |
| Config management | python-dotenv (.env file) | Keep secrets out of code |

---

## 13. Risk & Considerations

| Risk | Mitigation |
|------|------------|
| OCR accuracy on blurry/curved labels | Allow user to manually correct extracted text; use high-quality OCR API |
| AI hallucination in ingredient analysis | Validate AI output structure; maintain a known-ingredients database as fallback |
| Plain-text passwords in existing DB | Migration script to hash all existing passwords on first deploy |
| API costs at scale (Vision + Gemini) | Monitor usage; implement caching for repeated products; rate limiting per user |
| Large image uploads on slow networks | Compress images on Android before upload; show upload progress |
| OTP email delivery | Use reliable SMTP; add retry logic; consider SMS as backup |
| Token security on Android | Store JWT in EncryptedSharedPreferences, not regular SharedPreferences |

---

## 14. Environment Variables (.env)

```env
# Database
DB_HOST=localhost
DB_USER=root
DB_PASSWORD=
DB_NAME=nutritrace_db

# JWT
JWT_SECRET_KEY=your-secret-key-here
JWT_EXPIRY_DAYS=30

# Google Cloud Vision (OCR)
GOOGLE_APPLICATION_CREDENTIALS=path/to/service-account.json

# Google Gemini AI
GEMINI_API_KEY=your-gemini-api-key

# Email (SMTP)
SMTP_SERVER=smtp.gmail.com
SMTP_PORT=587
SMTP_EMAIL=your-email@gmail.com
SMTP_PASSWORD=your-app-password

# App
UPLOAD_FOLDER=uploads
MAX_CONTENT_LENGTH=10485760
```

---

## 15. Summary

**Total Endpoints:** 16
**Total Tables:** 8
**External Services:** 3 (OCR, AI/LLM, Email)
**Estimated Timeline:** 5-6 weeks

| Module | Endpoints | Tables |
|--------|-----------|--------|
| Auth | 6 | 2 (users, password_reset_otps) |
| Profile | 4 | 3 (health_profiles, user_health_conditions, user_sensitivities) |
| Scan | 5 | 2 (scans, scan_ingredients) |
| Compare | 2 | 1 (comparisons) |
| Dashboard | 1 | — (reads from existing) |

The backend transforms NutriTrace from a UI prototype into a fully functional, AI-powered health app. The core value proposition — **personalized ingredient analysis based on individual health conditions** — is delivered through the OCR → AI pipeline in the scan module, which is the highest-priority and highest-effort piece of this implementation.
