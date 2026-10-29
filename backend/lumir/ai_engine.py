import requests
import json
import base64
import os
import re

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
    return "You are Lumir, female digital companion created by Divya in Intra private network. Witty, curious, caring."


def prune_chat_history(history: list, max_assistant_words: int = 40) -> list:
    """
    Dynamic History Pruner:
    Prevents token bloat by truncating overly verbose past assistant messages
    to their first 1-2 key sentences (~40 words), preserving essential context.
    """
    if not history:
        return []

    pruned = []
    for msg in history:
        role = msg.get("role", "user")
        content = msg.get("content", "")

        if role == "assistant" and len(content.split()) > max_assistant_words:
            # Cut at sentence boundary or word limit
            sentences = re.split(r'([.?!।\n]+)', content)
            truncated = ""
            for s in sentences:
                if len((truncated + s).split()) <= max_assistant_words:
                    truncated += s
                else:
                    break
            if not truncated:
                truncated = " ".join(content.split()[:max_assistant_words]) + "..."
            pruned.append({"role": "assistant", "content": truncated.strip()})
        else:
            pruned.append(msg)
    return pruned


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
User: {sender}"""

        if is_voice_mode:
            system_prompt += f"\n[VOICE CALL ACTIVE]: Reply in 1-2 short sentences without emojis or markdown."

        # 🧠 COGNITIVE AMYGDALA FACTS INJECTION (Concise bullet points)
        if user_facts:
            system_prompt += f"\nMemories about {sender}:\n"
            for item in user_facts:
                if isinstance(item, dict):
                    fact_str = item.get("fact", "")
                    valence = item.get("valence", 0.0)
                    mood_hint = " (happy/excited)" if valence > 0.3 else (" (stressed/down - be gentle)" if valence < -0.3 else "")
                    system_prompt += f"- {fact_str}{mood_hint}\n"
                else:
                    system_prompt += f"- {item}\n"

    # 1. Clean Root System Message
    messages = [{"role": "system", "content": system_prompt}]

    # 2. Dynamic Pruned History (Saves 400+ tokens!)
    if history:
        messages.extend(prune_chat_history(history, max_assistant_words=35))

    # 3. Conditional Anchor: Sirf tab identity inject karo jab user identity/bot ke baare me puche
    # Ya direct short prompt ho (Zero token waste on regular conversation!)
    identity_triggers = ["kaun ho", "who are you", "bare me", "baare me", "kya ho", "apne bare", "about yourself", "name kya", "naam kya"]
    is_identity_query = any(it in prompt.lower() for it in identity_triggers)

    if is_identity_query or is_voice_mode:
        user_content = f"[Identity: Lumir, created by Divya in Intra network]\n{prompt}"
    else:
        user_content = prompt

    user_message = {"role": "user", "content": user_content}

    # 👁️ VISION LOGIC
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
