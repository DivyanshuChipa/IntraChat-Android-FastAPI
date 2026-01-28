# ===== File: admin_api.py =====
from fastapi import APIRouter, Depends
from server import verify_admin
from users import get_all_users, delete_user_data, reset_user_password
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

# ================= DELETE USER =================
@router.post("/delete_user")
def admin_delete_user(username: str, admin=Depends(verify_admin)):
    return delete_user_data(username)

# ================= RESET PASSWORD =================
@router.post("/reset_password")
def admin_reset_password(username: str, new_pass: str, admin=Depends(verify_admin)):
    return reset_user_password(username, new_pass)

# ================= CLEANUP =================
@router.post("/cleanup")
def admin_cleanup(days: int, admin=Depends(verify_admin)):
    deleted = cleanup_old_messages(days)
    return {
        "success": True,
        "deleted_messages": deleted,
        "message": f"Deleted {deleted} messages older than {days} days"
    }

