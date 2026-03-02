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
        # ==========================================
        # 🧠 RAPHAEL'S AGENT ROUTER (Vector DB Search)
        # ==========================================
        trigger_words = ["recent", "last", "purana", "purani", "pichli", "pichla", "pehle", "is", "isko", "ise", "usko"]
        media_words = ["pic", "photo", "image", "video", "file", "pdf", "document"]

        # Thoda broad checking taaki natural language pakad sake
        if not file_url and (any(tw in text for tw in trigger_words) or any(mw in text for mw in media_words)):

            target_command = None

            # 1. OCR (Text Extractor)
            if "ocr" in text or "text nikal" in text or "read" in text:
                target_command = "###ocr###"

            # 2. PASSPORT MAKER
            elif "passport" in text:
                if "9" in text:
                    target_command = "###passport9###"
                else:
                    target_command = "###passport###"

            # 3. EXTRACT AUDIO (MP3)
            elif "audio" in text or "mp3" in text or "gaana" in text:
                target_command = "###extractaudio###"

            # 4. COMPRESS (Smart Size Detection 🗜️)
            elif "compress" in text or "size kam" in text or "chota" in text:

                # Check karo agar user ne size bola hai (e.g., "60kb compress")
                size_match = re.search(r'(\d+)\s*(kb|mb)?', text)
                target_size = size_match.group(1) if size_match else "50" # Default 50kb

                # Hum temporary flag banate hain, file milne ke baad exact command decide karenge
                target_command = f"COMPRESS_PENDING_{target_size}"

            # Agar koi valid command match hua
            if target_command:
                try:
                    from .memory import find_media_in_memory
                    print(f"🧠 [RAPHAEL AGENT] Triggered! Intent mapped to '{target_command}'")

                    # Vector DB se purani file dhoondho
                    db_match = find_media_in_memory(sender, text)

                    if db_match and "file_url" in db_match:
                        matched_file = db_match["file_url"]
                        ext = matched_file.split('.')[-1].lower()

                        # 🪄 SMART RESOLUTION: File ka type dekh kar command badlo!
                        if target_command.startswith("COMPRESS_PENDING"):
                            size = target_command.split("_")[-1]
                            if ext in ['mp4', 'mkv', 'avi', 'mov']:
                                target_command = "###compressvideo:28:mp4###" # Video compression
                            elif ext == 'pdf':
                                target_command = "###compresspdf###" # PDF compression
                            else:
                                target_command = f"###compress<{size}>###" # Image compression

                        # 🪄 MAGIC: File ko temporary RAM mein load karo
                        if sender not in self.user_context:
                            self.user_context[sender] = []
                        self.user_context[sender].append(matched_file)

                        # User ki chat ko system command se hijack kar do
                        text = target_command
                        print(f"✨ [RAPHAEL AGENT] Success! Hijacked text to '{text}' using file {matched_file}")
                    else:
                        return {"type": "text", "text": "🤷‍♀️ Sorry, mujhe tumhari wo purani file DB mein nahi mili. Kya wapas bhej sakte ho?"}
                except Exception as e:
                    print(f"❌ Raphael Router Error: {e}")

        # 📁 1. Smart File/Image Memory Handling
        if file_url:
            if sender not in self.user_context:
                self.user_context[sender] = []

            self.user_context[sender].append(file_url)
            file_count = len(self.user_context[sender])

            if not text:
                # File ka extension nikalte hain (jaise 'mp4', 'jpg', 'pdf')
                ext = file_url.split('.')[-1].lower()

                # 🎬 VIDEO OPTIONS
                if ext in ['mp4', 'mkv', 'avi', 'mov', '3gp']:
                    return {
                        "type": "utility_options",
                        "text": f"🎬 {file_count} Video(s) received! Choose a video tool:",
                        "options": [
                            "🎵 Extract Audio (MP3)",
                            "🗜️ Compress Video",
                            "🔄 Rotate Video",
                            "🎞️ Convert to MP4"
                        ]
                    }

                # 📄 PDF OPTIONS
                elif ext in ['pdf']:
                    return {
                        "type": "utility_options",
                        "text": f"📄 {file_count} PDF(s) received! Choose a document tool:",
                        "options": [
                            "📄 Extract PDF Text",
                            "🔗 Merge PDFs",
                            "🗜️ Compress PDF"
                        ]
                    }

                # 📸 IMAGE OPTIONS (Default)
                else:
                    return {
                        "type": "utility_options",
                        "text": f"📸 {file_count} Image(s) received! Choose an image tool:",
                        "options": [
                            "🛂 Passport A6 (6 Photos)",
                            "🛂 Passport A6 (9 Photos)",
                            "📄 Extract Text (OCR)",
                            "📅 Passport + Date",
                            "🗜️ Compress Image",
                            "📄 Convert to PDF"
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

            if res["success"]:
                # 🧹 Kaam hone ke baad memory clear kar do
                if sender in self.user_context:
                    del self.user_context[sender]

                return {"type": "file", "text": res["message"], "file_url": res["file_url"], "file_name": res["file_name"]}
            else:
                return {"type": "text", "text": res["message"]}
        # 📄 3.9 EXTRACT PDF TEXT LOGIC
        if "###pdf2text###" in text:
            file_urls = self.user_context.get(sender, [])
            if not file_urls:
                return {"type": "text", "text": "⚠️ No file found! Please send a PDF first."}

            target_pdf = file_urls[-1] # List ki aakhiri PDF uthao

            from .utilities import extract_text_from_pdf
            res = extract_text_from_pdf(target_pdf)

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
        # 🗜️ 3.10 COMPRESS PDF LOGIC
        if "###compresspdf###" in text:
            file_urls = self.user_context.get(sender, [])
            if not file_urls:
                return {"type": "text", "text": "⚠️ No file found! Please send a PDF first."}

            target_pdf = file_urls[-1]

            from .utilities import compress_pdf
            res = compress_pdf(target_pdf)

            if sender in self.user_context:
                del self.user_context[sender]

            if res["success"]:
                return {"type": "file", "text": res["message"], "file_url": res["file_url"], "file_name": res["file_name"]}
            else:
                return {"type": "text", "text": res["message"]}

        # 🎵 3.11 EXTRACT AUDIO (MP3) LOGIC
        if "###extractaudio###" in text:
            file_urls = self.user_context.get(sender, [])
            if not file_urls:
                return {"type": "text", "text": "⚠️ No file found! Please send a video first."}

            target_video = file_urls[-1]

            from .utilities import extract_audio_from_video
            res = extract_audio_from_video(target_video)

            if sender in self.user_context:
                del self.user_context[sender]

            if res["success"]:
                return {"type": "file", "text": res["message"], "file_url": res["file_url"], "file_name": res["file_name"]}
            else:
                return {"type": "text", "text": res["message"]}

        # 🗜️ 3.12 COMPRESS VIDEO LOGIC (With dynamic parameters)
        if text.startswith("###compressvideo:"):
            file_urls = self.user_context.get(sender, [])
            if not file_urls:
                return {"type": "text", "text": "⚠️ No file found!"}

            # Extract CRF and Format from command string
            parts = text.replace("###", "").split(":")
            crf_val = int(parts[1]) if len(parts) > 1 else 28
            fmt_val = parts[2] if len(parts) > 2 else "mp4"

            from .utilities import compress_video
            res = compress_video(file_urls[-1], crf_val, fmt_val)

            if sender in self.user_context: del self.user_context[sender]
            if res["success"]: return {"type": "file", "text": res["message"], "file_url": res["file_url"], "file_name": res["file_name"]}
            else: return {"type": "text", "text": res["message"]}

        # 🔄 3.13 ROTATE VIDEO LOGIC
        if text.startswith("###rotatevideo:"):
            file_urls = self.user_context.get(sender, [])
            if not file_urls:
                return {"type": "text", "text": "⚠️ No file found!"}

            rot_type = text.replace("###", "").split(":")[1]

            from .utilities import rotate_video
            res = rotate_video(file_urls[-1], rot_type)

            if sender in self.user_context: del self.user_context[sender]
            if res["success"]: return {"type": "file", "text": res["message"], "file_url": res["file_url"], "file_name": res["file_name"]}
            else: return {"type": "text", "text": res["message"]}

        # 🎞️ 3.14 CONVERT TO MP4 LOGIC
        if "###convertmp4###" in text:
            file_urls = self.user_context.get(sender, [])
            if not file_urls:
                return {"type": "text", "text": "⚠️ No file found!"}

            from .utilities import convert_to_mp4
            res = convert_to_mp4(file_urls[-1])

            if sender in self.user_context: del self.user_context[sender]
            if res["success"]: return {"type": "file", "text": res["message"], "file_url": res["file_url"], "file_name": res["file_name"]}
            else: return {"type": "text", "text": res["message"]}

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