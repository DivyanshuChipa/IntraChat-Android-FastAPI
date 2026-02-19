from .utilities import generate_passport_layout

class LumirEngine:
    def __init__(self):
        # 🧠 Bot ki Memory: Sender -> Last Image URL
        self.user_context = {}

    def process(self, text: str, file_url: str = None, sender: str = "Unknown"):
        text = text.lower().strip()

        # 📸 1. Agar koi image aayi hai, usko bot ki memory me save kar lo
        if file_url:
            self.user_context[sender] = file_url
            if not text:
                return {"type": "text", "text": "📸 Image received! Reply with `###passport###` to generate a 6-on-A6 layout."}

        # 2. Basic Commands
        if text in ["hi", "hello", "hey"]:
            return {"type": "text", "text": "👋 Hello! I am Lumir, your LAN assistant. Type /help to see what I can do."}

        if text == "/help" or text == "/hrlp":
            return {"type": "text", "text": "🛠️ **Available Commands:**\n1. Send an image first, then reply `###passport###` to generate a layout.\n2. Ask me any question (AI mode coming soon!)"}

        # 🛠️ 3. PASSPORT GENERATOR LOGIC
        if "###passport###" in text:
            # Check karo kya sath me image aayi hai, ya memory me koi purani image padi hai
            target_image = file_url or self.user_context.get(sender)

            if not target_image:
                return {"type": "text", "text": "⚠️ No image found! Please send an image first, then type `###passport###`."}

            # Process the image
            res = generate_passport_layout(target_image)

            # Processing ke baad memory clear kar do taaki same image baar baar process na ho
            if sender in self.user_context:
                del self.user_context[sender]

            if res["success"]:
                return {"type": "file", "text": res["message"], "file_url": res["file_url"], "file_name": res["file_name"]}
            else:
                return {"type": "text", "text": res["message"]}

        # 4. Default Fallback
        return {"type": "text", "text": f"🤖 I received: '{text}'. (AI processing is currently disabled)."}

lumir_engine = LumirEngine()