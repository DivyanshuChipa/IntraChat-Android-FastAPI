# ===== File: admin_api.py =====
import os
import time
from fastapi import APIRouter, Depends
from server import verify_admin
from users import get_all_users, delete_user_data, reset_user_password, set_require_approval, get_require_approval, approve_user_db, get_ai_config, set_ai_config
from messages import cleanup_old_messages
from chat import connected_clients   # 👈 SOURCE OF TRUTH

router = APIRouter(prefix="/admin", tags=["Admin"])

# ================= USERS LIST + ONLINE STATUS =================
@router.get("/users")
def admin_get_users(admin=Depends(verify_admin)):
    users = get_all_users()

    online_users = set(connected_clients.keys())
    # 👆 {"kartika", "diya", ...}

    for u in users:
        u["is_online"] = u["username"] in online_users

    return {
        "success": True,
        "users": users
    }

# ✅ Approve User
@router.post("/approve_user")
def admin_approve_user(username: str, admin=Depends(verify_admin)):
    approve_user_db(username)
    return {"success": True, "message": f"User {username} approved"}

# ✅ Get Settings
@router.get("/settings")
def admin_get_settings(admin=Depends(verify_admin)):
    return {"success": True, "require_approval": get_require_approval()}

# ✅ Toggle Settings
@router.post("/toggle_approval")
def admin_toggle_approval(enabled: bool, admin=Depends(verify_admin)):
    set_require_approval(enabled)
    return {"success": True, "message": "Settings updated"}

# ================= DELETE USER =================
@router.post("/delete_user")
def admin_delete_user(username: str, admin=Depends(verify_admin)):
    return delete_user_data(username)

# ================= RESET PASSWORD =================
@router.post("/reset_password")
def admin_reset_password(username: str, new_pass: str, admin=Depends(verify_admin)):
    return reset_user_password(username, new_pass)

# Yahan par apne upload folder ka exact path daalein
# Agar files 'uploads' folder mein save hoti hain toh yahi rehne dein
UPLOAD_DIR = "uploads"

# ================= CLEANUP =================
@router.post("/cleanup")
def admin_cleanup(days: int, admin=Depends(verify_admin)):
    deleted = cleanup_old_messages(days)
    return {
        "success": True,
        "deleted_messages": deleted,
        "message": f"Deleted {deleted} messages older than {days} days"
    }

# ================= UPLOAD FOLDER CLEANUP =================
@router.post("/cleanup_files")
def admin_cleanup_files(days: int, admin=Depends(verify_admin)):
    if not os.path.exists(UPLOAD_DIR):
        return {"success": False, "message": f"Directory '{UPLOAD_DIR}' not found!"}

    now = time.time()
    cutoff_time = now - (days * 86400) # 1 din mein 86400 seconds hote hain

    deleted_count = 0
    freed_space = 0

    for filename in os.listdir(UPLOAD_DIR):
        filepath = os.path.join(UPLOAD_DIR, filename)

        # Sirf files delete karein, sub-folders nahi
        if os.path.isfile(filepath):
            file_mtime = os.path.getmtime(filepath) # File ka modification time

            if file_mtime < cutoff_time:
                try:
                    size = os.path.getsize(filepath)
                    os.remove(filepath)
                    deleted_count += 1
                    freed_space += size
                except Exception as e:
                    print(f"Error deleting {filename}: {e}")

    freed_mb = round(freed_space / (1024 * 1024), 2)

    return {
        "success": True,
        "deleted_files": deleted_count,
        "freed_mb": freed_mb,
        "message": f"Deleted {deleted_count} files. Freed {freed_mb} MB space."
    }

# ================= AI SETTINGS =================
@router.get("/ai_settings")
def admin_get_ai_settings(admin=Depends(verify_admin)):
    return {"success": True, "config": get_ai_config()}

@router.post("/ai_settings")
def admin_set_ai_settings(
        enabled: bool,
        model: str,
        url: str,
        vision: str = "gemma:27b-cloud",
        fallback: str = "gemma3:270m",
        smart_models: str = "gpt-oss:20b-cloud",
        admin=Depends(verify_admin)
):
    set_ai_config(enabled, model, url, vision, fallback, smart_models)
    return {"success": True, "message": "AI settings updated successfully (with Vision and Fallback Engine)!"}

