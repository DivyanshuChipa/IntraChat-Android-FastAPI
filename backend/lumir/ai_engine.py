import requests
import json  # Debugging format ke liye

def ask_ai(prompt: str, config: dict, history: list = None, sender: str = "User"):
    if not config.get("ai_enabled", False):
        return "🤖 AI processing is currently disabled by the Admin."

    url = config.get("ollama_url", "http://localhost:11434")

    # 🌟 NAYE MODELS YAHAN SE FETCH HONGE
    main_model = config.get("ai_model", "gpt-oss:20b-cloud")
    fallback_model = config.get("ai_fallback", "gemma3:270m")

    # 🧬 LUMIR'S UPGRADED PERSONALITY
    system_prompt = f"""You are Lumir, a highly intelligent, friendly, and witty AI assistant. 
    You live inside the 'Intra' LAN messenger network. 
    Your creator is a engineer known as 'Divya'. 
    Always reply in a helpful and concise manner. 
    IMPORTANT: The user you are currently chatting with is named '{sender}'. Address them by their name naturally in conversation."""

    # Chat history format setup
    messages = [{"role": "system", "content": system_prompt}]

    if history:
        messages.extend(history)

    messages.append({"role": "user", "content": prompt})

    # 🛠️ HELPER FUNCTION: Request bhejne aur Debug log print karne ke liye
    def make_request(target_model, is_fallback=False):
        # Debugging: Terminal mein check karne ke liye ki AI ko kya bheja jaa raha hai
        print("\n" + "="*50)
        mode_text = "(FALLBACK MODE)" if is_fallback else "(MAIN MODE)"
        print(f"🤖 [LUMIR DEBUG] SENDING TO OLLAMA MODEL: {target_model} {mode_text}")
        print(f"👤 USER: {sender}")

        # Payload sirf pehli baar print karo, fallback mein terminal spam na ho
        if not is_fallback:
            print(json.dumps(messages, indent=2))
        print("="*50 + "\n")

        # Request Bhejo
        res = requests.post(
            f"{url}/api/chat",
            json={
                "model": target_model,
                "messages": messages,
                "stream": False
            },
            timeout=30 if not is_fallback else 100  # Main jaldi fail ho, Fallback pura time le!
        )

        if res.status_code == 200:
            return res.json().get("message", {}).get("content", "No response from AI.")
        else:
            raise Exception(f"HTTP Status {res.status_code}")

    # ==========================================
    # 🔄 THE FALLBACK LOGIC (Main -> Fallback)
    # ==========================================
    try:
        # 🟢 THE MAIN ATTEMPT (Badal/Cloud wala Model)
        return make_request(main_model, is_fallback=False)

    except Exception as e:
        print(f"⚠️ [LUMIR FALLBACK ALERT] Main model '{main_model}' failed: {e}. Shifting to '{fallback_model}'...")

        try:
            # 🟠 THE FALLBACK ATTEMPT (Local Baby Model)
            reply = make_request(fallback_model, is_fallback=True)

            # User ko batane ke liye ki ye baby model ka answer hai
            return f"*(Fallback Mode)*\n\n{reply}"

        except requests.exceptions.ConnectionError:
            return f"🔌 AI Connection Error: Could not reach Ollama at {url}. Is it running?"
        except Exception as fallback_e:
            return f"❌ Unexpected AI Error: Both Main and Fallback models failed! ({str(fallback_e)})"