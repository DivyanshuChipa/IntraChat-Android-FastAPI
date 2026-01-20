# lan_server/server.py

# lan_server/server.py
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles  # 👈 Add this import
from pydantic import BaseModel
from jose import jwt
from datetime import datetime, timedelta, timezone
import chat, files, calls, messages
import profiles  # 👈 1. Import profiles module
from users import init_db, register_user, verify_user
from messages import init_msg_db
from users import get_all_users
from messages import get_recent_messages
from users import delete_user_data # 👈 Import the new function

# ================= JWT CONFIG =================
SECRET_KEY = "CHANGE_THIS_TO_SOMETHING_RANDOM_AND_LONG"
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_DAYS = 30

# ================= FASTAPI APP =================
app = FastAPI(title="LAN Chat Server (modular)")

# CORS: sabko allow (LAN ke liye thik hai)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 👈 2. Mount StaticFiles - uploads folder ko accessible banao
app.mount("/uploads", StaticFiles(directory="uploads"), name="uploads")

# DB init (users.db for auth)
init_db()
init_msg_db()

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
    if verify_user(user.username, user.password):
        expire = datetime.now(timezone.utc) + timedelta(days=ACCESS_TOKEN_EXPIRE_DAYS)
        to_encode = {"sub": user.username, "exp": expire}
        encoded_jwt = jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)
        return {
            "success": True,
            "token": encoded_jwt,
            "username": user.username,
        }
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
app.include_router(chat.router)
app.include_router(files.router)
app.include_router(calls.router, prefix="/calls")
app.include_router(profiles.router, prefix="/profile", tags=["Profile"])  # 👈 3. Add Profile Router
