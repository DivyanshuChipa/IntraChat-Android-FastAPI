from .utilities import generate_passport_layout, extract_text_from_image
from .ai_engine import ask_ai
import sys
import os
import re

# To access users.py from the parent directory
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from users import get_ai_config

class LumirEngine:
    def __init__(self):
        # 🧠 Bot ki Memory: Sender -> Last Image URL
        self.user_context = {}

    def process(self, text: str, file_url: str = None, sender: str = "Unknown"):
        text = text.lower().strip()

        # 📁 1. File/Image Memory Handling
        if file_url:
            if sender not in self.user_context:
                self.user_context[sender] = []

            self.user_context[sender].append(file_url)
            file_count = len(self.user_context[sender])

            if not text:
                return {
                    "type": "utility_options",
                    "text": f"📁 {file_count} File(s) received! Add more or choose an option:",
                    "options": [
                        "🛂 Passport A6 (6 Photos)",
                        "🛂 Passport A6 (9 Photos)",
                        "📄 Extract Text (OCR)",
                        "📅 Passport + Date",
                        "🗜️ Compress Image",
                        "📄 Convert to PDF",
                        "🔗 Merge PDFs"  # 👈 NAYA BUTTON
                    ]
                }

        # 2. Basic Utility Commands
        if text in ["hi", "hello", "hey"]:
            return {"type": "text", "text": "👋 Hello! I am Lumir, your LAN assistant. Type /help to see what I can do."}

        if text == "/help" or text == "/hrlp":
            return {"type": "text", "text": "🛠️ **Available Commands:**\n1. `###passport###` (Send an image first)\n2. Just chat with me! (If Admin has enabled AI mode)"}

        # 🗜️ 3.6 SMART COMPRESS IMAGE LOGIC
        # Regex to find commands like ###compress<60>###
        import re
        compress_match = re.search(r'###compress<(\d+)>###', text)

        if compress_match:
            target_kb = int(compress_match.group(1))
            target_image = file_url or (self.user_context.get(sender)[-1] if self.user_context.get(sender) else None)

            if not target_image:
                return {"type": "text", "text": "⚠️ No image found to compress! Please send an image first."}

            from .utilities import compress_image_to_target
            res = compress_image_to_target(target_image, target_kb)

            if sender in self.user_context:
                del self.user_context[sender]

            if res["success"]:
                return {"type": "file", "text": res["message"], "file_url": res["file_url"], "file_name": res["file_name"]}
            else:
                return {"type": "text", "text": res["message"]}

        # 🔗 3.8 MERGE PDFs LOGIC
        if "###mergepdfs###" in text:
            file_urls = self.user_context.get(sender, [])

            from .utilities import merge_multiple_pdfs
            res = merge_multiple_pdfs(file_urls)

            # 🧹 Kaam hone ke baad memory clear kar do
            if sender in self.user_context:
                del self.user_context[sender]

            if res["success"]:
                return {"type": "file", "text": res["message"], "file_url": res["file_url"], "file_name": res["file_name"]}
            else:
                return {"type": "text", "text": res["message"]}

        # 📄 3.7 MULTI-IMAGE TO PDF LOGIC
        if "###topdf###" in text:
            # Pura ka pura array uthao
            image_urls = self.user_context.get(sender, [])

            if not image_urls:
                return {"type": "text", "text": "⚠️ No images found! Please send at least one image first."}

            from .utilities import convert_images_to_pdf
            res = convert_images_to_pdf(image_urls) # Array bhej diya

            # 🧹 PDF banne ke baad memory saaf kar do taaki agli baar zero se shuru ho
            if sender in self.user_context:
                del self.user_context[sender]

            if res["success"]:
                return {"type": "file", "text": res["message"], "file_url": res["file_url"], "file_name": res["file_name"]}
            else:
                return {"type": "text", "text": res["message"]}

        # 3. PASSPORT GENERATOR LOGIC
        if "###passport" in text:
            target_image = file_url or (self.user_context.get(sender)[-1] if self.user_context.get(sender) else None)
            if not target_image:
                return {"type": "text", "text": "⚠️ No image found! Please send an image first."}

            # Detect 9 grid
            grid_size = 9 if "###passport9###" in text else 6

            # Detect Date using Regex (Reads whatever is inside < >)
            date_text = None
            date_match = re.search(r'###passportdate<(.*?)>###', text)
            if date_match:
                date_text = date_match.group(1).strip()

            # Process the image
            res = generate_passport_layout(target_image, grid_size=grid_size, date_text=date_text)

            if sender in self.user_context:
                del self.user_context[sender]

            if res["success"]:
                return {"type": "file", "text": res["message"], "file_url": res["file_url"], "file_name": res["file_name"]}
            else:
                return {"type": "text", "text": res["message"]}

        # 📝 3.5 OCR LOGIC
        if "###ocr###" in text:
            target_image = file_url or (self.user_context.get(sender)[-1] if self.user_context.get(sender) else None)
            if not target_image:
                return {"type": "text", "text": "⚠️ No image found! Please send an image first, then type `###ocr###`."}

            # Extract text
            res = extract_text_from_image(target_image)

            # Memory clear karo
            if sender in self.user_context:
                del self.user_context[sender]

            if res["success"]:
                return {"type": "text", "text": f"📄 **Extracted Text:**\n\n{res['text']}"}
            else:
                return {"type": "text", "text": res["message"]}

        # 🧠 4. THE AI FALLBACK (Smart Routing)
        try:
            config = get_ai_config()
            if config.get("ai_enabled"):

                # Model ka naam check karo
                current_model = config.get("ai_model", "")

                # 🛑 GEMMA 270M BYPASS LOGIC: Baby model ko history mat do
                if current_model == "gemma3:270m":
                    chat_history = []  # Empty memory
                else:
                    # ✅ BIG MODELS: Baki smart models ke liye history uthao
                    from messages import get_lumir_history
                    chat_history = get_lumir_history(sender, limit=6)

                    # 🛑 FIX: Remove current prompt from history if it appears
                    # Since we save the message BEFORE processing, history might include it
                    if chat_history and chat_history[-1]["role"] == "user" and chat_history[-1]["content"].lower().strip() == text.lower().strip():
                        chat_history.pop()

                # AI ko prompt, config, history, aur sender name bhejo
                ai_reply = ask_ai(prompt=text, config=config, history=chat_history, sender=sender)

                # 🛠️ THE FIX: Remove all newlines and make it a single safe line
                safe_reply = ai_reply.replace("\n", " ").replace("\r", " ").strip()

                # Agar multiple spaces ban gaye hain toh unhe single space kar do
                #import re
                safe_reply = re.sub(' +', ' ', safe_reply)

                return {"type": "text", "text": safe_reply}
            else:
                return {"type": "text", "text": "🤖 AI is currently offline. Enable it from the Admin Panel to chat with me!"}
        except Exception as e:
            return {"type": "text", "text": f"⚠️ AI System Error: {str(e)}"}
lumir_engine = LumirEngine()
