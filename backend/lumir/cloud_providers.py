import os
import json
import base64
import requests
import sqlite3

def load_dotenv_fallback():
    """Simple parser to load .env from workspace, installed Program Files, or %LOCALAPPDATA%."""
    local_app = os.environ.get("LOCALAPPDATA", "")
    possible_paths = [
        os.path.join(local_app, "IntraServer", ".env") if local_app else "",
        os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), ".env"),
        os.path.join(os.path.dirname(os.path.dirname(__file__)), ".env"),
        os.path.join(os.path.dirname(__file__), ".env"),
        ".env"
    ]
    for p in possible_paths:
        if p and os.path.exists(p):
            try:
                with open(p, "r", encoding="utf-8") as f:
                    for line in f:
                        line = line.strip()
                        if line and not line.startswith("#") and "=" in line:
                            k, v = line.split("=", 1)
                            k = k.strip()
                            v = v.strip().strip("'\"")
                            if k and k not in os.environ:
                                os.environ[k] = v
                break
            except Exception as e:
                print(f"[DOTENV LOAD NOTICE]: {e}")

load_dotenv_fallback()


def get_db_config_key(key_name: str) -> str:
    """Check if key exists in SQLite config table (Admin Panel state)."""
    try:
        from paths import get_db_path
        db_path = get_db_path()
    except Exception:
        db_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), "chat_users.db")

    if os.path.exists(db_path):
        try:
            conn = sqlite3.connect(db_path)
            cursor = conn.cursor()
            cursor.execute("SELECT value FROM config WHERE key = ?", (key_name,))
            row = cursor.fetchone()
            conn.close()
            if row and row[0] and row[0].strip():
                return row[0].strip()
        except Exception:
            pass
    return ""


def get_api_key(provider: str, *args, **kwargs) -> str:
    """
    Priority Check:
    1. SQLite Database config table (Admin UI setting)
    2. Environment Variable / .env file
    """
    key_name = f"{provider.upper()}_API_KEY"
    
    # 1. DB config check (Admin Setting)
    db_val = get_db_config_key(key_name.lower())
    if db_val:
        return db_val
    
    # 2. Env check
    return os.getenv(key_name, "").strip()


# =========================================================================
# 🌟 GOOGLE GEMINI FLASH API CALLER
# =========================================================================
def call_gemini(
    prompt: str,
    system_prompt: str = "",
    history: list = None,
    image_path: str = None,
    model: str = "gemini-flash-lite-latest",
    api_key: str = None,
    **kwargs
) -> str:
    """
    Calls Google Gemini REST API directly using lightweight requests (zero heavy SDK).
    Supports Gemini Flash Lite, Multimodal Images & System instructions.
    """
    key = api_key or get_api_key("gemini")
    if not key:
        raise ValueError("Missing GEMINI_API_KEY. Please set it in .env or Admin Settings.")

    clean_model = model.strip() if model else "gemini-flash-lite-latest"
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{clean_model}:generateContent?key={key}"

    # Build Contents
    contents = []

    # History (if provided)
    if history:
        for msg in history:
            role = msg.get("role", "user")
            # Gemini expects 'user' or 'model'
            gemini_role = "model" if role == "assistant" else "user"
            content = msg.get("content", "")
            if content:
                contents.append({
                    "role": gemini_role,
                    "parts": [{"text": content}]
                })

    # Current User Turn
    user_parts = []
    
    # Optional image attachment
    if image_path and os.path.exists(image_path):
        try:
            with open(image_path, "rb") as f:
                img_data = base64.b64encode(f.read()).decode("utf-8")
                ext = os.path.splitext(image_path)[1].lower().replace(".", "")
                mime_type = "image/png" if ext == "png" else "image/jpeg"
                user_parts.append({
                    "inline_data": {
                        "mime_type": mime_type,
                        "data": img_data
                    }
                })
        except Exception as e:
            print(f"[GEMINI VISION ENCODE ERROR]: {e}")

    user_parts.append({"text": prompt})
    contents.append({
        "role": "user",
        "parts": user_parts
    })

    payload = {
        "contents": contents,
        "generationConfig": {
            "temperature": 0.7,
            "maxOutputTokens": 1000
        }
    }

    if system_prompt:
        payload["system_instruction"] = {
            "parts": [{"text": system_prompt}]
        }

    headers = {"Content-Type": "application/json"}
    res = requests.post(url, headers=headers, json=payload, timeout=30)

    if res.status_code == 200:
        data = res.json()
        candidates = data.get("candidates", [])
        if candidates:
            parts = candidates[0].get("content", {}).get("parts", [])
            if parts:
                return parts[0].get("text", "").strip()
        return "🤖 (Gemini gave an empty response)"
    else:
        err_msg = res.text[:200]
        raise Exception(f"Gemini API Error (HTTP {res.status_code}): {err_msg}")


# =========================================================================
# 🇮🇳 SARVAM AI API CALLER
# =========================================================================
def call_sarvam(
    prompt: str,
    system_prompt: str = "",
    history: list = None,
    model: str = "sarvam-105b-conversations",
    api_key: str = None,
    **kwargs
) -> str:
    """
    Calls Sarvam AI Chat Completion API.
    Optimized for Indian languages (Sarvam-105B Conversations).
    """
    key = api_key or get_api_key("sarvam")
    if not key:
        raise ValueError("Missing SARVAM_API_KEY. Please set it in .env or Admin Settings.")

    clean_model = model.strip() if model else "sarvam-105b-conversations"
    url = "https://api.sarvam.ai/v1/chat/completions"

    messages = []
    if system_prompt:
        messages.append({"role": "system", "content": system_prompt})

    if history:
        for msg in history:
            role = msg.get("role", "user")
            content = msg.get("content", "")
            if content:
                messages.append({"role": role, "content": content})

    messages.append({"role": "user", "content": prompt})

    payload = {
        "model": clean_model,
        "messages": messages,
        "temperature": 0.7
    }

    headers = {
        "Content-Type": "application/json",
        "api-subscription-key": key,
        "Authorization": f"Bearer {key}"
    }

    res = requests.post(url, headers=headers, json=payload, timeout=35)

    if res.status_code == 200:
        data = res.json()
        choices = data.get("choices", [])
        if choices:
            return choices[0].get("message", {}).get("content", "").strip()
        return "🤖 (Sarvam gave an empty response)"
    else:
        err_msg = res.text[:200]
        raise Exception(f"Sarvam API Error (HTTP {res.status_code}): {err_msg}")
