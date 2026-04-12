# lan_server/server.py

# lan_server/server.py
import os
import secrets
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi import Header, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles  # 👈 Add this import
from pydantic import BaseModel
from jose import jwt
from datetime import datetime, timedelta, timezone
import chat, files, calls, messages
import profiles  # 👈 1. Import profiles module
from users import init_db, register_user, verify_user, get_admin_key_db, set_admin_key_db
from messages import init_msg_db
from users import get_all_users
from messages import get_recent_messages
from users import delete_user_data # 👈 Import the new function
from fastapi.staticfiles import StaticFiles

# ================= JWT CONFIG =================
SECRET_KEY = os.getenv("JWT_SECRET_KEY", "CHANGE_THIS_TO_SOMETHING_RANDOM_AND_LONG")
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_DAYS = 30

# ================= ADMIN CONFIG (Persistent) =================
# We must ensure DB is initialized before fetching the key
init_db()
init_msg_db()

ADMIN_SECRET = os.getenv("INTRA_ADMIN_KEY")
if not ADMIN_SECRET:
    # Try to get from Database
    ADMIN_SECRET = get_admin_key_db()

    if not ADMIN_SECRET:
        # Generate new and save
        ADMIN_SECRET = secrets.token_urlsafe(32)
        set_admin_key_db(ADMIN_SECRET)
        print("✨ Generated new persistent Admin Secret.")

print(f"🔐 Admin Secret: {ADMIN_SECRET}")
print("👉 Use this key for Desktop Admin Panel or set INTRA_ADMIN_KEY to override.")

def verify_admin(x_admin_key: str = Header(None)):
    # secrets.compare_digest raises TypeError for non-ASCII strings.
    # Normalizing both to bytes ensures clean rejection for invalid/non-ASCII keys.
    if not x_admin_key:
        print("🚫 Admin Access Denied: No X-Admin-Key header provided.")
        raise HTTPException(status_code=403, detail="Admin access denied")

    # Strip potential whitespace from accidental copy-paste
    clean_key = x_admin_key.strip()
    clean_secret = ADMIN_SECRET.strip()

    try:
        is_valid = secrets.compare_digest(
            clean_key.encode("utf-8"),
            clean_secret.encode("utf-8")
        )
    except (TypeError, UnicodeEncodeError):
        is_valid = False

    if not is_valid:
        # Diagnostic log (masked key for security)
        masked_key = clean_key[:4] + "..." + clean_key[-4:] if len(clean_key) > 8 else "****"
        print(f"🚫 Admin Access Denied: Key mismatch. Received length: {len(clean_key)} (Expected: {len(clean_secret)}), Masked input: {masked_key}")
        print("💡 TIP: Copy the EXACT random secret from the server logs above.")
        raise HTTPException(status_code=403, detail="Admin access denied")

# ================= BACKGROUND TASKS & SCHEDULER =================
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from tasks import sync_weather_data, hourly_sentinel_check

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Setup Scheduler
    scheduler = AsyncIOScheduler(timezone='Asia/Kolkata')

    # Run data sync daily at 07:00 AM
    scheduler.add_job(sync_weather_data, 'cron', hour=7, minute=0)

    # Run smart environment monitor every hour at minute 5 (offset to avoid race condition with sync)
    scheduler.add_job(hourly_sentinel_check, 'cron', minute=5)

    scheduler.start()
    print("🌅 Level 5 Smart Environment Monitor scheduler started.")
    yield
    scheduler.shutdown()
    print("🌅 Smart Environment Monitor scheduler stopped.")

# ================= FASTAPI APP =================
app = FastAPI(title="LAN Chat Server (modular)", lifespan=lifespan)

# CORS: allow origins (disabled credentials for security)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 👈 2. Mount StaticFiles - uploads folder ko accessible banao

app.mount("/uploads", StaticFiles(directory="uploads"), name="uploads")


# ================= Pydantic MODELS =================
class UserAuth(BaseModel):
    username: str
    password: str

# ================= AUTH ROUTES =================
@app.post("/register")
async def handle_register(user: UserAuth):
    if not user.username or not user.password:
        return JSONResponse(
            status_code=400,
            content={"success": False, "message": "Username and password required"},
        )
    result = register_user(user.username, user.password)
    if result["success"]:
        return JSONResponse(
            status_code=201,
            content={"success": True, "message": "User registered successfully"},
        )
    else:
        return JSONResponse(
            status_code=409,
            content={"success": False, "message": result["message"]},
        )

@app.post("/login")
async def handle_login(user: UserAuth):
    if not user.username or not user.password:
        return JSONResponse(
            status_code=400,
            content={"success": False, "message": "Username and password required"},
        )
    
    # 🔥 Updated Logic based on ChatGPT feedback
    result = verify_user(user.username, user.password)
    status = result["status"]
    
    if status == "OK":
        expire = datetime.now(timezone.utc) + timedelta(days=ACCESS_TOKEN_EXPIRE_DAYS)
        to_encode = {"sub": user.username, "exp": expire}
        encoded_jwt = jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)
        return {
            "success": True,
            "token": encoded_jwt,
            "username": user.username,
        }
        
    elif status == "PENDING":
        return JSONResponse(
            status_code=403,
            content={"success": False, "message": "Account pending approval from Admin."},
        )
        
    else: # INVALID
        return JSONResponse(
            status_code=401,
            content={"success": False, "message": "Invalid credentials"},
        )
@app.get("/users")
async def get_users_list():
    users = get_all_users()
    return {"success": True, "users": users}

@app.get("/messages")
async def get_chat_history():
    msgs = get_recent_messages(limit=100)
    return msgs

# ================== DELETE ACOUNT ENDPOINTS=========
# ✅ NEW: Delete Account Endpoint
@app.post("/delete_account")
async def handle_delete_account(user: UserAuth):
    # 1. Verify Password first (Security Check)
    if not verify_user(user.username, user.user_password if hasattr(user, 'user_password') else user.password):
        # Note: Check logic matches your Pydantic model field name (usually just 'password')
        return JSONResponse(
            status_code=401,
            content={"success": False, "message": "Incorrect password. Cannot delete account."},
        )

    # 2. Proceed to Delete
    result = delete_user_data(user.username)
    
    if result["success"]:
        return JSONResponse(
            status_code=200,
            content={"success": True, "message": "Account deleted permanently."},
        )
    else:
        return JSONResponse(
            status_code=500,
            content={"success": False, "message": result["message"]},
        )


# ================= EXISTING MODULES =================

import admin_api
app.include_router(admin_api.router)
app.include_router(chat.router)
app.include_router(files.router)
app.include_router(calls.router, prefix="/calls")
app.include_router(profiles.router, prefix="/profile", tags=["Profile"])  # 👈 3. Add Profile Router

# ================= STATIC FILES (WEB CLIENT) =================
# ⚠️ Yeh line SABSE NEECHE honi chahiye!
# Agar yeh upar hui toh WS aur Login kaam nahi karenge.
app.mount("/", StaticFiles(directory="static", html=True), name="static")
