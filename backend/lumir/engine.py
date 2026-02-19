class LumirEngine:
    def process(self, text: str, file_url: str = None):
        text = text.lower().strip()

        # 1. Basic Commands
        if text in ["hi", "hello", "hey"]:
            return "👋 Hello! I am Lumir, your LAN assistant. Type /help to see what I can do."

        if text == "/help":
            return "🛠️ **Available Commands:**\n1. `###passport###` (Send an image with this tag to generate a layout)\n2. Ask me any question (AI mode coming soon!)"

        # 2. Utility Triggers (Dummy for now)
        if "###passport###" in text and file_url:
            return "📸 Image received! Passport generation logic will run here soon."

        # 3. Default Fallback (Future AI wrapper)
        return f"🤖 I received: '{text}'. (AI processing is currently disabled)."

# Singleton instance
lumir_engine = LumirEngine()