import requests

def ask_ai(prompt: str, config: dict, history: list = None):
    if not config.get("ai_enabled", False):
        return "🤖 AI processing is currently disabled by the Admin."

    url = config.get("ollama_url", "http://localhost:11434")
    model = config.get("ai_model", "llama3:8b")

    # 🧬 LUMIR'S PERSONALITY
    system_prompt = """You are Lumir, a highly intelligent, friendly, and witty AI assistant. 
    You live inside the 'Intra' LAN messenger network. 
    Your creator is a brilliant engineer known Divya'. 
    Always reply in a helpful and concise manner. Use previous chat context to give better answers."""

    # Chat history format setup
    messages = [{"role": "system", "content": system_prompt}]

    # Purani baatein add karo
    if history:
        messages.extend(history)

    # Naya message add karo
    messages.append({"role": "user", "content": prompt})

    try:
        # ⚠️ Yahan humne /api/generate ki jagah /api/chat use kiya hai
        res = requests.post(
            f"{url}/api/chat",
            json={
                "model": model,
                "messages": messages, # Ab pura context ja raha hai
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