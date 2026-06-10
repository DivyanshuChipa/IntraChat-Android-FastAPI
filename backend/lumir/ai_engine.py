import requests
import json
import base64 # 👈 NAYA IMPORT AANKHON KE LIYE
import os

def ask_ai(prompt: str, config: dict, history: list = None, sender: str = "User", image_path: str = None, user_facts: list = None):
    if not config.get("ai_enabled", False):
        return "🤖 AI processing is currently disabled by the Admin."

    url = config.get("ollama_url", "http://localhost:11434")

    # 👁️ VISION OVERRIDE: Agar image hai, toh vision model ko hi main model bana do!
    if image_path:
        main_model = config.get("ai_vision_model", "gemma3:27b-cloud")
    else:
        main_model = config.get("ai_model", "gpt-oss:20b-cloud")

    fallback_model = config.get("ai_fallback", "gemma3:270m")

    system_prompt = f"""You are Lumir, a highly intelligent, friendly, and witty AI assistant. 
    You live inside the 'Intra' LAN messenger network. 
    Your creator is an engineer known as 'Divya'. 
    Always reply in a helpful and concise manner. 
    IMPORTANT: The user you are currently chatting with is named '{sender}'. Address them by their name naturally in conversation."""

    # 🧠 NEW: INJECT FACTS INVISIBLY
    if user_facts:
        system_prompt += f"\n\nHere are some important facts you must remember about {sender}:\n"
        for fact in user_facts:
            system_prompt += f"- {fact}\n"
        system_prompt += "\nUse these facts to personalize your responses, but DO NOT mention that you are reading from a memory list."

    # 🧠 GEMMA-SPECIFIC HISTORY FIX
    # Gemma models sometimes ignore the 'system' role. 
    # For Gemma, we merge the system prompt into the first user message for better context/history injection.
    is_gemma = "gemma" in main_model.lower()
    
    if is_gemma:
        messages = [] # Start empty, we'll merge system prompt into the first user msg
    else:
        messages = [{"role": "system", "content": system_prompt}]

    if history:
        messages.extend(history)

    # Prepare the current user message
    user_message = {"role": "user", "content": prompt}

    if is_gemma and len(messages) == 0:
        # No history, merge system prompt directly into this first message
        user_message["content"] = f"{system_prompt}\n\nUser: {prompt}"
    elif is_gemma and len(messages) > 0:
        # History exists, merge system prompt into the very first message of the history
        if messages[0]["role"] == "user":
            messages[0]["content"] = f"{system_prompt}\n\n{messages[0]['content']}"
        elif messages[0]["role"] == "system":
            # If history already started with a system msg, just keep it, but Gemma prefers user
            messages[0]["role"] = "user"
            messages[0]["content"] = f"{system_prompt}\n\n{messages[0]['content']}"

    # 👁️ VISION LOGIC: Agar image aayi hai, toh use Base64 mein encode karke message mein jodo

    if image_path and os.path.exists(image_path):
        try:
            with open(image_path, "rb") as img_file:
                # Image ko text (Base64) mein convert karo
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

        # Base64 string bohot lambi hoti hai, isliye terminal pe spam na ho uske liye usko hata kar print karenge
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
            # 🛑 SAFETY NET: Baby model (Fallback) text-only hota hai.
            # Agar image payload mein hai, toh usko delete kar do warna baby crash ho jayega!
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