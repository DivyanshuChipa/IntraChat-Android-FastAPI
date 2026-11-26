# ===== File: admin_api.py =====
import os
import time
import psutil
from fastapi import APIRouter, Depends

router = APIRouter(prefix="/admin", tags=["Admin"])

from server import verify_admin
from users import (
    get_all_users, delete_user_data, reset_user_password, set_require_approval, 
    get_require_approval, approve_user_db, get_ai_config, set_ai_config, 
    get_default_location, set_default_location, get_environment_settings, 
    set_environment_settings, set_server_discovery, get_server_discovery,
    set_config_value, get_config_value
)
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
    cfg = get_ai_config()
    try:
        from lumir.cloud_providers import get_api_key
        g_k = get_api_key("gemini")
        s_k = get_api_key("sarvam")
        cfg["has_gemini"] = bool(g_k)
        cfg["has_sarvam"] = bool(s_k)
        cfg["gemini_masked"] = f"{g_k[:6]}...{g_k[-4:]}" if g_k and len(g_k) > 10 else ("Configured" if g_k else "")
        cfg["sarvam_masked"] = f"{s_k[:6]}...{s_k[-4:]}" if s_k and len(s_k) > 10 else ("Configured" if s_k else "")
    except Exception:
        cfg["has_gemini"] = False
        cfg["has_sarvam"] = False
        cfg["gemini_masked"] = ""
        cfg["sarvam_masked"] = ""
    return {"success": True, "config": cfg}

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
        node2_llama_url: str = "",
        voice_provider: str = "auto",
        voice_speaker: str = "simran_enhi_companion",
        gemini_key: str = None,
        sarvam_key: str = None,
        admin=Depends(verify_admin)
):
    set_ai_config(enabled, model, url, vision, fallback, smart_models, voice_node_url, voice_node_enabled, ai_provider, node2_llama_url, voice_provider, voice_speaker)
    
    # Save API keys to DB if provided
    if gemini_key and gemini_key.strip():
        set_config_value("gemini_api_key", gemini_key.strip())
    if sarvam_key and sarvam_key.strip():
        set_config_value("sarvam_api_key", sarvam_key.strip())

    return {"success": True, "message": "AI settings and API keys updated successfully!"}

# ================= DYNAMIC MODEL FETCHER =================
@router.get("/fetch_models")
def admin_fetch_models(provider: str = "auto", url: str = None, admin=Depends(verify_admin)):
    """
    Dynamically discovers and lists models available for the selected inference provider.
    Zero load: 1 lightweight HTTP call only on demand.
    """
    clean_prov = provider.strip().lower()
    
    # 1. SARVAM AI
    if clean_prov == "sarvam":
        models = [
            {"id": "sarvam-105b-conversations", "name": "Sarvam 105B Conversations (Flagship Hindi ⚡ 0.38s)"},
            {"id": "sarvam-105b", "name": "Sarvam 105B Base"},
        ]
        return {"success": True, "provider": "sarvam", "models": models}

    # 2. GOOGLE GEMINI
    elif clean_prov == "gemini":
        from lumir.cloud_providers import get_api_key
        g_key = get_api_key("gemini")
        if not g_key:
            return {"success": False, "message": "Gemini API key not configured yet. Please enter key above."}
        try:
            import requests
            res = requests.get(f"https://generativelanguage.googleapis.com/v1beta/models?key={g_key}", timeout=8)
            if res.status_code == 200:
                raw_models = res.json().get("models", [])
                models = []
                for m in raw_models:
                    m_id = m.get("name", "").replace("models/", "")
                    methods = m.get("supportedGenerationMethods", [])
                    if "generateContent" in methods:
                        disp = m.get("displayName") or m_id
                        models.append({"id": m_id, "name": f"{disp} ({m_id})"})
                models.sort(key=lambda x: 0 if "flash" in x["id"].lower() else 1)
                return {"success": True, "provider": "gemini", "models": models}
            else:
                return {"success": False, "message": f"Google API error (HTTP {res.status_code})"}
        except Exception as e:
            return {"success": False, "message": f"Could not reach Google API: {e}"}

    # 3. OLLAMA
    elif clean_prov == "ollama":
        target_url = url or "http://localhost:11434"
        try:
            import requests
            res = requests.get(f"{target_url.rstrip('/')}/api/tags", timeout=5)
            if res.status_code == 200:
                raw_models = res.json().get("models", [])
                models = []
                for m in raw_models:
                    m_name = m.get("name", "")
                    size_mb = round(m.get("size", 0) / (1024 * 1024))
                    models.append({"id": m_name, "name": f"{m_name} ({size_mb} MB)"})
                return {"success": True, "provider": "ollama", "models": models}
            else:
                return {"success": False, "message": f"Ollama returned HTTP {res.status_code}"}
        except Exception as e:
            return {"success": False, "message": f"Could not reach Ollama at {target_url}. Is it running?"}

    # 4. LLAMACPP
    elif clean_prov == "llamacpp":
        target_url = url or "http://localhost:8080"
        try:
            import requests
            endpoint = f"{target_url.rstrip('/')}/v1/models"
            res = requests.get(endpoint, timeout=4)
            if res.status_code == 200:
                raw_models = res.json().get("data", [])
                models = [{"id": m.get("id", ""), "name": m.get("id", "")} for m in raw_models if m.get("id")]
                return {"success": True, "provider": "llamacpp", "models": models}
        except Exception:
            pass
        return {"success": True, "provider": "llamacpp", "models": [{"id": "default", "name": "llama.cpp default loaded model"}]}

    return {"success": False, "message": "Select a specific provider (Ollama, Gemini, Sarvam) to fetch models."}

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

@router.get("/test_cognitive_node")
def admin_test_cognitive_node(url: str = None, admin=Depends(verify_admin)):
    """Pings the llama.cpp / Cognitive worker endpoint to verify reachability and latency."""
    if not url:
        config = get_ai_config()
        url = config.get("node2_llama_url", "")
    
    from lumir.cognitive_helper import test_node2_connection
    return test_node2_connection(url, timeout=3.0)

@router.get("/test_sarvam_voice")
def admin_test_sarvam_voice(speaker: str = "simran_enhi_companion", admin=Depends(verify_admin)):
    """Tests Sarvam Bulbul v4-flash TTS with a sample sentence and reports latency."""
    from lumir.voice import sarvam_tts
    import time
    import base64
    start = time.time()
    try:
        audio = sarvam_tts("नमस्ते! मैं लूमिर हूँ।", speaker=speaker, timeout=7.0)
        latency = int((time.time() - start) * 1000)
        if audio and len(audio) > 1000:
            b64 = base64.b64encode(audio).decode("ascii")
            return {
                "success": True, 
                "latency_ms": latency, 
                "speaker": speaker, 
                "audio_base64": b64,
                "message": f"Online ({latency} ms) - Speaker: {speaker}"
            }
        else:
            return {"success": False, "message": "Failed to synthesize audio from Sarvam."}
    except Exception as e:
        return {"success": False, "message": str(e)}

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




