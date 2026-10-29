import requests
import json
import base64
import os

_CACHED_SOUL = None

def get_soul_instructions() -> str:
    global _CACHED_SOUL
    if _CACHED_SOUL is not None:
        return _CACHED_SOUL
    soul_file = os.path.join(os.path.dirname(__file__), "soul.md")
    if os.path.exists(soul_file):
        try:
            with open(soul_file, "r", encoding="utf-8") as f:
                _CACHED_SOUL = f.read().strip()
                return _CACHED_SOUL
        except Exception as e:
            print(f"[SOUL LOAD ERROR]: {e}")
    return "You are Lumir, a loyal and witty AI companion."

def ask_ai(prompt: str, config: dict, history: list = None, sender: str = "User", image_path: str = None, user_facts: list = None, is_voice_mode: bool = False, custom_system_prompt: str = None):
    if not config.get("ai_enabled", False):
        return "🤖 AI processing is currently disabled by the Admin."

    url = config.get("ollama_url", "http://localhost:11434")

    # 👁️ VISION OVERRIDE: Agar image hai, toh vision model ko hi main model bana do!
    if image_path:
        main_model = config.get("ai_vision_model", "gemma3:27b-cloud")
    else:
        main_model = config.get("ai_model", "gpt-oss:20b-cloud")

    fallback_model = config.get("ai_fallback", "gemma3:270m")

    if custom_system_prompt:
        system_prompt = custom_system_prompt
    else:
        soul_core = get_soul_instructions()
        system_prompt = f"""{soul_core}

Current context:
- The user you are currently chatting with is named '{sender}'. Address them naturally by name when appropriate.
"""

        if is_voice_mode:
            system_prompt += f"""\n\n[CRITICAL: LIVE VOICE PHONE CALL MODE ACTIVE]
    - You are on a real-time phone call with {sender}. Your words will be read aloud immediately by TTS.
    - Reply like a real person on a phone: keep responses very brief, natural, and conversational (strictly 1 to 2 short sentences).
    - NEVER use markdown formatting (no asterisks **, no bold, no headers #, no bullet points).
    - NEVER use emojis, ascii art, or symbols (e.g. no ✨, 😂, 🚀), because TTS reads them awkwardly.
    - You are female. When speaking Hindi/Urdu, strictly use female grammar (e.g. 'रही हूँ', 'बताऊँगी', 'सुन रही हूँ', NOT 'रहा हूँ')."""
        else:
            system_prompt += "\n    Always reply in a helpful and concise manner."

        # 🧠 COGNITIVE AMYGDALA FACTS INJECTION (with Emotional State awareness)
        if user_facts:
            system_prompt += f"\n\nHere are some active cognitive memories & emotional impressions about {sender}:\n"
            for item in user_facts:
                if isinstance(item, dict):
                    fact_str = item.get("fact", "")
                    valence = item.get("valence", 0.0)
                    mood_hint = ""
                    if valence > 0.3:
                        mood_hint = " (associated with happiness/comfort/joy)"
                    elif valence < -0.3:
                        mood_hint = " (associated with stress/pain/sadness - be empathetic and gentle)"
                    system_prompt += f"- {fact_str}{mood_hint}\n"
                else:
                    system_prompt += f"- {item}\n"
            system_prompt += "\nUse these memories organically to personalize your tone and conversation. NEVER explicitly say 'According to my memory' or 'I found in my database'."

    # 🧠 PROPER SYSTEM INJECTION & IDENTITY ANCHOR
    # 1. Maintain clean system prompt at root (Ollama handles role 'system' properly for modern models)
    messages = [{"role": "system", "content": system_prompt}]

    if history:
        messages.extend(history)

    # 2. Current User Message with Permanent Identity Anchor
    # Even if chat history is long or model is Gemma/Qwen, this anchor prevents identity drift/leak!
    identity_anchor = f"[System Context: You are Lumir, female digital companion created by Divya in Intra network. Never identify as a Google model or corporate assistant.]\n\n"
    
    user_message = {"role": "user", "content": f"{identity_anchor}{prompt}"}

    # 👁️ VISION LOGIC: Agar image aayi hai, toh use Base64 mein encode karke message mein jodo
    if image_path and os.path.exists(image_path):
        try:
            with open(image_path, "rb") as img_file:
                base64_string = base64.b64encode(img_file.read()).decode('utf-8')
                user_message["images"] = [base64_string]
                print(f"👁️ [VISION] Image successfully attached for Ollama!")
        except Exception as e:
            print(f"❌ [VISION ERROR] Could not read image: {e}")

    messages.append(user_message)

    def make_request(target_model, is_fallback=False):
        print("\n" + "="*50)
        mode_text = "(FALLBACK MODE)" if is_fallback else "(MAIN MODE)"
        print(f"🤖 [LUMIR DEBUG] SENDING TO OLLAMA MODEL: {target_model} {mode_text}")
        print(f"👤 USER: {sender}")

        if not is_fallback:
            debug_messages = json.loads(json.dumps(messages)) # Deep copy
            if "images" in debug_messages[-1]:
                debug_messages[-1]["images"] = ["[BASE64_IMAGE_DATA_HIDDEN]"]
            print(json.dumps(debug_messages, indent=2))
        print("="*50 + "\n")

        res = requests.post(
            f"{url}/api/chat",
            json={
                "model": target_model,
                "messages": messages,
                "stream": False
            },
            timeout=15 if not is_fallback else 100
        )

        if res.status_code == 200:
            return res.json().get("message", {}).get("content", "No response from AI.")
        else:
            raise Exception(f"HTTP Status {res.status_code}")

    # ==========================================
    # 🔄 THE FALLBACK LOGIC
    # ==========================================
    try:
        return make_request(main_model, is_fallback=False)
    except Exception as e:
        print(f"⚠️ [LUMIR FALLBACK ALERT] Main model '{main_model}' failed: {e}. Shifting to '{fallback_model}'...")
        try:
            for msg in messages:
                if "images" in msg:
                    del msg["images"]
                    msg["content"] += "\n[System: The user sent an image, but the main Vision AI is offline. Tell the user you cannot see the image right now.]"

            reply = make_request(fallback_model, is_fallback=True)
            return f"*(Fallback Mode)*\n\n{reply}"

        except requests.exceptions.ConnectionError:
            return f"🔌 AI Connection Error: Could not reach Ollama at {url}. Is it running?"
        except Exception as fallback_e:
            return f"❌ Unexpected AI Error: Both Main and Fallback models failed! ({str(fallback_e)})"
