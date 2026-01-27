from fastapi import APIRouter, Depends
from server import verify_admin
from users import get_all_users, delete_user_data

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
