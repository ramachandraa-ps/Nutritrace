# NutriTrace Backend Implementation Design

**Date:** 2026-03-15
**Status:** Approved

## Decisions Made

| Decision | Choice |
|----------|--------|
| OCR | Google Cloud Vision API |
| AI/LLM | Google Gemini 2.0 Flash |
| Email | Gmail SMTP |
| Database migration | Drop & recreate all tables |
| Architecture | Simplified flat structure (no Blueprints) |
| Image storage | Local filesystem (`uploads/`) |
| Scope | Backend + Frontend wiring together |
| Network setup | Local IP (`http://192.168.x.x:5000`) |
| Error handling | Minimal — get it working first |

## Architecture

Flat file structure:
```
backend/
├── main.py, database.py, config.py
├── auth.py, user.py, scan.py, compare.py, dashboard.py
├── middleware.py, ocr_service.py, ai_service.py, email_service.py
├── validators.py, requirements.txt, .env
└── uploads/
```

## Database: 8 Tables
users, health_profiles, user_health_conditions, user_sensitivities, scans, scan_ingredients, comparisons, password_reset_otps

## API: 16 Endpoints
Auth (6), User/Health (4), Scan (5), Compare (1), Dashboard (1)

## Frontend: ApiClient.kt + wire all 14 Activities to real API calls

## Key Flows
- JWT auth (30-day, PyJWT, bcrypt passwords)
- Scan pipeline: image → Vision OCR → Gemini AI → DB → response
- OTP: 4-digit, 10-min expiry, Gmail SMTP
- Health profile collected across 3 screens, sent as single POST
