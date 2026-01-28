# ===== File: admin_api.py =====
from fastapi import APIRouter, Depends, HTTPException
from server import verify_admin
from users import get_all_users, delete_user_data, reset_user_password # 👈 Added import
from messages import cleanup_old_messages # 👈 Added import
from chat import connected_clients # 👈 Ye line upar add karo

router = APIRouter(prefix="/admin", tags=["Admin"])

@router.get("/users")
def admin_get_users(admin=Depends(verify_admin)):
    return {
        "success": True,
        "users": get_all_users()
    }

@router.post("/delete_user")
def admin_delete_user(username: str, admin=Depends(verify_admin)):
    return delete_user_data(username)

# ✅ NEW: Password Reset Endpoint
@router.post("/reset_password")
def admin_reset_password(username: str, new_pass: str, admin=Depends(verify_admin)):
    return reset_user_password(username, new_pass)

# ✅ NEW: Cleanup Endpoint
@router.post("/cleanup")
def admin_cleanup(days: int, admin=Depends(verify_admin)):
    deleted = cleanup_old_messages(days)
    return {
        "success": True,
        "deleted_messages": deleted,
        "message": f"Deleted {deleted} messages older than {days} days"
    }
@router.get("/users")
def admin_get_users(admin=Depends(verify_admin)):
    users = get_all_users()
    for u in users:
        u["is_online"] = u["username"] in connected_clients
    return {"success": True, "users": users}