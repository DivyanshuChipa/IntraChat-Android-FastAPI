import requests

def ask_ai(prompt: str, config: dict):
    # Check if admin disabled AI
    if not config.get("ai_enabled", False):
        return "🤖 AI processing is currently disabled by the Admin."

    url = config.get("ollama_url", "http://localhost:11434")
    model = config.get("ai_model", "llama3:8b")

    try:
        # Request to Ollama
        res = requests.post(
            f"{url}/api/generate",
            json={
                "model": model,
                "prompt": prompt,
                "stream": False
            },
            timeout=40 # Heavy models take time to reply
        )
        
        if res.status_code == 200:
            return res.json().get("response", "No response from AI.")
        else:
            return f"⚠️ AI Error: Check if model '{model}' is installed on {url}."
            
    except requests.exceptions.ConnectionError:
        return f"🔌 AI Connection Error: Could not reach Ollama at {url}. Is it running?"
    except Exception as e:
        return f"❌ Unexpected AI Error: {str(e)}"