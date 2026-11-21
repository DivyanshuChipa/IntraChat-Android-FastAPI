# ===== File: admin_api.py =====
import os
import time
import psutil
from fastapi import APIRouter, Depends

router = APIRouter(prefix="/admin", tags=["Admin"])

from server import verify_admin
from users import get_all_users, delete_user_data, reset_user_password, set_require_approval, get_require_approval, approve_user_db, get_ai_config, set_ai_config, get_default_location, set_default_location, get_environment_settings, set_environment_settings, set_server_discovery, get_server_discovery
from messages import cleanup_old_messages
from chat import connected_clients   # 👈 SOURCE OF TRUTH

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
    return {
        "success": True,
        "require_approval": get_require_approval(),
        "discovery_enabled": get_server_discovery(),
    }

# ✅ Toggle Approval Setting
@router.post("/toggle_approval")
def admin_toggle_approval(enabled: bool, admin=Depends(verify_admin)):
    set_require_approval(enabled)
    return {"success": True, "message": "Settings updated"}

# ✅ Toggle LAN Server Discovery Setting
@router.post("/toggle_discovery")
def admin_toggle_discovery(enabled: bool, admin=Depends(verify_admin)):
    set_server_discovery(enabled)
    return {
        "success": True,
        "message": f"Server discovery set to {'ON' if enabled else 'OFF (Stealth)'}",
        "discovery_enabled": enabled,
    }

# ================= DELETE USER =================
@router.post("/delete_user")
def admin_delete_user(username: str, admin=Depends(verify_admin)):
    return delete_user_data(username)

# ================= RESET PASSWORD =================
@router.post("/reset_password")
def admin_reset_password(username: str, new_pass: str, admin=Depends(verify_admin)):
    return reset_user_password(username, new_pass)

from paths import get_uploads_dir

# Yahan par apne upload folder ka exact path daalein
# Agar files 'uploads' folder mein save hoti hain toh yahi rehne dein
UPLOAD_DIR = get_uploads_dir()

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


# ================= WEATHER LOCATION SETTINGS =================
@router.get("/weather_location")
def admin_get_weather_location(admin=Depends(verify_admin)):
    lat, lon = get_default_location()
    return {"success": True, "default_lat": lat, "default_lon": lon}


@router.post("/weather_location")
def admin_set_weather_location(lat: float, lon: float, admin=Depends(verify_admin)):
    set_default_location(lat, lon)
    return {"success": True, "message": "Weather location updated", "default_lat": lat, "default_lon": lon}

# ================= ENVIRONMENT MONITOR SETTINGS =================
@router.get("/environment_settings")
def admin_get_environment_settings(admin=Depends(verify_admin)):
    settings = get_environment_settings()
    return {"success": True, "settings": settings}

@router.post("/environment_settings")
def admin_set_environment_settings(
    enabled: bool, mode: str, temp: float, wind: float, rain: float, admin=Depends(verify_admin)
):
    set_environment_settings(enabled, mode, temp, wind, rain)
    return {"success": True, "message": "Smart Environment settings updated successfully!"}

@router.post("/test_weather_sync")
async def admin_test_weather_sync(admin=Depends(verify_admin)):
    import tasks
    await tasks.sync_weather_data()
    return {"success": True, "message": "Weather sync executed and cache updated!"}

@router.post("/test_sentinel_alert")
async def admin_test_sentinel_alert(admin=Depends(verify_admin)):
    import tasks
    await tasks.hourly_sentinel_check()
    return {"success": True, "message": "Sentinel alert check executed successfully!"}

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
        voice_node_url: str = "http://localhost:8005",
        voice_node_enabled: bool = False,
        ai_provider: str = "auto",
        admin=Depends(verify_admin)
):
    set_ai_config(enabled, model, url, vision, fallback, smart_models, voice_node_url, voice_node_enabled, ai_provider)
    return {"success": True, "message": "AI settings updated successfully (including Node 2 Voice Engine & AI Provider)!"}

# ================= TEST VOICE NODE (NODE 2) =================
@router.get("/test_voice_node")
def admin_test_voice_node(url: str = None, admin=Depends(verify_admin)):
    import requests
    if not url:
        config = get_ai_config()
        url = config.get("voice_node_url", "http://localhost:8005")

    try:
        start_time = time.time()
        res = requests.get(f"{url.rstrip('/')}/health", timeout=3)
        latency = round((time.time() - start_time) * 1000, 2)
        if res.status_code == 200:
            data = res.json()
            return {
                "success": True,
                "latency_ms": latency,
                "node_info": data,
                "message": f"Connected to Node 2 Voice Engine in {latency}ms!"
            }
        else:
            return {
                "success": False,
                "message": f"Node 2 responded with status code {res.status_code}"
            }
    except Exception as e:
        return {
            "success": False,
            "message": f"Could not reach Voice Node at {url}: {str(e)}"
        }

# ================= ⚡ SYSTEM & HARDWARE TELEMETRY =================
@router.get("/system_status")
def admin_get_system_status(admin=Depends(verify_admin)):
    try:
        cpu = psutil.cpu_percent(interval=0.1)
        mem = psutil.virtual_memory()
        disk = psutil.disk_usage(os.path.abspath(os.sep))
        return {
            "success": True,
            "cpu_percent": round(cpu, 1),
            "ram_percent": round(mem.percent, 1),
            "ram_used_gb": round(mem.used / (1024**3), 2),
            "ram_total_gb": round(mem.total / (1024**3), 2),
            "disk_percent": round(disk.percent, 1),
            "disk_free_gb": round(disk.free / (1024**3), 2),
            "disk_total_gb": round(disk.total / (1024**3), 2),
            "active_clients": len(connected_clients)
        }
    except Exception as e:
        return {"success": False, "error": str(e)}


# ================= 🧩 CAPABILITIES & ADDONS (FFmpeg, Tesseract, yt-dlp) =================
import sys
import threading
from typing import Dict, Any
from capability_manager import (
    CAPABILITIES,
    detect_capability,
    download_and_install_capability,
    uninstall_local_capability,
    get_intra_tools_dir
)

# In-memory tracking for active installations
_install_tasks: Dict[str, Dict[str, Any]] = {}

@router.get("/capabilities")
def admin_get_capabilities(admin=Depends(verify_admin)):
    tools = []
    for key, config in CAPABILITIES.items():
        detection = detect_capability(key)
        tools.append({
            "key": key,
            "name": config.get("name", key),
            "title": config.get("title", key),
            "description": config.get("description", ""),
            "installed": detection.get("installed", False),
            "source": detection.get("source", "none"),
            "path": detection.get("path"),
            "can_uninstall": detection.get("can_uninstall", False)
        })
    return {
        "success": True,
        "platform": sys.platform,
        "tools_dir": get_intra_tools_dir(),
        "capabilities": tools,
        "active_tasks": _install_tasks
    }

@router.get("/capabilities/status")
def admin_get_capabilities_status(admin=Depends(verify_admin)):
    return {
        "success": True,
        "active_tasks": _install_tasks
    }

@router.post("/capabilities/install")
def admin_install_capability(tool_key: str, admin=Depends(verify_admin)):
    if tool_key not in CAPABILITIES:
        return {"success": False, "message": f"Unknown capability '{tool_key}'."}

    current = _install_tasks.get(tool_key)
    if current and current.get("status") == "in_progress":
        return {"success": True, "message": "Installation already in progress."}

    _install_tasks[tool_key] = {
        "status": "in_progress",
        "percent": 5,
        "message": f"Starting installation of {tool_key}..."
    }

    def _worker():
        def _cb(pct: int, msg: str):
            _install_tasks[tool_key] = {
                "status": "in_progress",
                "percent": pct,
                "message": msg
            }

        res = download_and_install_capability(tool_key, progress_callback=_cb)
        if res.get("success"):
            _install_tasks[tool_key] = {
                "status": "completed",
                "percent": 100,
                "message": res.get("message", "Installed successfully!"),
                "path": res.get("path")
            }
        else:
            _install_tasks[tool_key] = {
                "status": "failed",
                "percent": 0,
                "message": res.get("message", "Installation failed.")
            }

    t = threading.Thread(target=_worker, daemon=True)
    t.start()

    return {"success": True, "message": f"Installation started for {tool_key}."}

@router.post("/capabilities/uninstall")
def admin_uninstall_capability(tool_key: str, admin=Depends(verify_admin)):
    if tool_key not in CAPABILITIES:
        return {"success": False, "message": f"Unknown capability '{tool_key}'."}

    removed = uninstall_local_capability(tool_key)
    if tool_key in _install_tasks:
        del _install_tasks[tool_key]

    return {
        "success": True,
        "removed": removed,
        "message": f"{tool_key} local binaries uninstalled successfully."
    }




