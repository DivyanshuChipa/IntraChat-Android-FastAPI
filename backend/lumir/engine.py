from .utilities import generate_passport_layout
from .ai_engine import ask_ai
import sys
import os

# To access users.py from the parent directory
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from users import get_ai_config

class LumirEngine:
    def __init__(self):
        # 🧠 Bot ki Memory: Sender -> Last Image URL
        self.user_context = {}

    def process(self, text: str, file_url: str = None, sender: str = "Unknown"):
        text = text.lower().strip()

        # 📸 1. Image Memory Handling
        if file_url:
            self.user_context[sender] = file_url
            if not text:
                return {"type": "text", "text": "📸 Image received! Reply with `###passport###` to generate a 6-on-A6 layout, or ask me to analyze it (soon)."}

        # 2. Basic Utility Commands
        if text in ["hi", "hello", "hey"]:
            return {"type": "text", "text": "👋 Hello! I am Lumir, your LAN assistant. Type /help to see what I can do."}

        if text == "/help" or text == "/hrlp":
            return {"type": "text", "text": "🛠️ **Available Commands:**\n1. `###passport###` (Send an image first)\n2. Just chat with me! (If Admin has enabled AI mode)"}

        # 3. PASSPORT GENERATOR LOGIC
        if "###passport###" in text:
            target_image = file_url or self.user_context.get(sender)
            if not target_image:
                return {"type": "text", "text": "⚠️ No image found! Please send an image first, then type `###passport###`."}

            res = generate_passport_layout(target_image)
            if sender in self.user_context:
                del self.user_context[sender]

            if res["success"]:
                return {"type": "file", "text": res["message"], "file_url": res["file_url"], "file_name": res["file_name"]}
            else:
                return {"type": "text", "text": res["message"]}

        # 🧠 4. THE AI FALLBACK (Llama3 integration)
        # Agar koi command match nahi hui, toh AI se poocho!
        try:
            config = get_ai_config()
            if config.get("ai_enabled"):
                # Call Ollama
                ai_reply = ask_ai(prompt=text, config=config)
                return {"type": "text", "text": ai_reply}
            else:
                return {"type": "text", "text": "🤖 AI is currently offline. Enable it from the Admin Panel to chat with me!"}
        except Exception as e:
            return {"type": "text", "text": f"⚠️ AI System Error: {str(e)}"}

lumir_engine = LumirEngine()