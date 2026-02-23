import requests
import json  # Debugging format ke liye

def ask_ai(prompt: str, config: dict, history: list = None, sender: str = "User"):
    if not config.get("ai_enabled", False):
        return "🤖 AI processing is currently disabled by the Admin."

    url = config.get("ollama_url", "http://localhost:11434")
    model = config.get("ai_model", "llama3:8b")

    # 🧬 LUMIR'S UPGRADED PERSONALITY (Ab isko user ka naam bhi pata hai!)
    system_prompt = f"""You are Lumir, a highly intelligent, friendly, and witty AI assistant. 
    You live inside the 'Intra' LAN messenger network. 
    Your creator is a  engineer known as 'Divya'. 
    Always reply in a helpful and concise manner. 
    IMPORTANT: The user you are currently chatting with is named '{sender}'. Address them by their name naturally in conversation."""

    # Chat history format setup
    messages = [{"role": "system", "content": system_prompt}]

    if history:
        messages.extend(history)

    messages.append({"role": "user", "content": prompt})

    # 🛠️ DEBUGGING: Terminal mein check karne ke liye ki AI ko kya bheja jaa raha hai
    print("\n" + "="*50)
    print(f"🤖 [LUMIR DEBUG] SENDING TO OLLAMA MODEL: {model}")
    print(f"👤 USER: {sender}")
    print(json.dumps(messages, indent=2))  # Ye terminal me saare 6 messages print karega!
    print("="*50 + "\n")

    try:
        res = requests.post(
            f"{url}/api/chat",
            json={
                "model": model,
                "messages": messages,
                "stream": False
            },
            timeout=40
        )

        if res.status_code == 200:
            return res.json().get("message", {}).get("content", "No response from AI.")
        else:
            return f"⚠️ AI Error: Check if model '{model}' is installed on {url}."

    except requests.exceptions.ConnectionError:
        return f"🔌 AI Connection Error: Could not reach Ollama at {url}. Is it running?"
    except Exception as e:
        return f"❌ Unexpected AI Error: {str(e)}"