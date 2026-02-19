from .utilities import generate_passport_layout

class LumirEngine:
    def process(self, text: str, file_url: str = None):
        text = text.lower().strip()

        if text in ["hi", "hello", "hey"]:
            return {"type": "text", "text": "👋 Hello! I am Lumir, your LAN assistant. Type /help to see what I can do."}

        if text == "/help" or text == "/hrlp":
            return {"type": "text", "text": "🛠️ **Available Commands:**\n1. `###passport###` (Attach a photo with this tag to generate a 6-on-A6 layout)\n2. Ask me any question (AI mode coming soon!)"}

        # 📸 PASSPORT GENERATOR LOGIC
        if "###passport###" in text:
            if not file_url:
                return {"type": "text", "text": "⚠️ Please attach an image with the `###passport###` tag!"}

            res = generate_passport_layout(file_url)
            if res["success"]:
                return {"type": "file", "text": res["message"], "file_url": res["file_url"], "file_name": res["file_name"]}
            else:
                return {"type": "text", "text": res["message"]}

        # Handle empty text (when user just sends a file)
        if not text and file_url:
            return {"type": "text", "text": "I received a file, but no command. Use /help to see available commands."}

        return {"type": "text", "text": f"🤖 I received: '{text}'. (AI processing is currently disabled)."}

lumir_engine = LumirEngine()