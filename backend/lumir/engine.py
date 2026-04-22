from .utilities import generate_passport_layout, extract_text_from_image
from .ai_engine import ask_ai
import sys
import os
import re
import psutil
import requests
from .memory import search_facts_in_chroma, save_fact_to_chroma

# To access users.py and messages.py from the parent directory
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from users import get_ai_config
from messages import get_lumir_history

def get_server_status():
    try:
        # The Tank 🚜 ka current load check karo
        cpu = psutil.cpu_percent(interval=0.1)
        ram = psutil.virtual_memory().percent
        disk = psutil.disk_usage('/').percent

        return {
            "type": "utility_server",
            "cpu": f"{cpu}%",
            "ram": f"{ram}%",
            "disk": f"{disk}%",
            "status": "Online 🟢",
            "text": f"Server Status: CPU {cpu}%, RAM {ram}%" # Fallback
        }
    except Exception as e:
        return {"type": "text", "text": f"⚠️ Server status error: {str(e)}"}

def get_weather(lat=None, lon=None):
    try:
        if lat and lon:
            # 🌍 Real GPS Weather via Open-Meteo (No API Key needed!)
            url = f"https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current_weather=true"
            response = requests.get(url, timeout=5.0)
            data = response.json()
            temp = data['current_weather']['temperature']
            is_day = data['current_weather']['is_day']
            desc = "Clear/Sunny ☀️" if is_day else "Clear Night 🌙" 
            
            return {
                "type": "utility_weather",
                "location": "Live GPS Location 📍",
                "temp": f"{temp}°C",
                "condition": desc,
                "text": f"Live Weather: {temp}°C, {desc}"
            }
        else:
            # 🏠 Fallback default location (Maheshpura)
            location = "Maheshpura"
            url = f"https://wttr.in/{location}?format=j1"
            response = requests.get(url, timeout=5.0)
            data = response.json()
            temp = data['current_condition'][0]['temp_C']
            desc = data['current_condition'][0]['weatherDesc'][0]['value']

            return {
                "type": "utility_weather",
                "location": location.capitalize(),
                "temp": f"{temp}°C",
                "condition": desc,
                "text": f"Weather in {location}: {temp}°C, {desc}"
            }
    except Exception as e:
        print(f"Weather error: {e}")
        return {"type": "text", "text": "Bhai net nahi chal raha ya weather server down hai! 🌧️"}

class LumirEngine:
    def __init__(self):
        # 🧠 Bot ki Memory: Sender -> Last Image URL
        self.user_context = {}

    def process(self, text: str, file_url: str = None, sender: str = "Unknown", lat: float = None, lon: float = None):
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
                            "🛂 Master Passport",
                            "📄 Extract Text (OCR)",
                            "🗜️ Compress Image",
                            "📄 Convert to PDF",
                            "🧠 Analyze Image (AI)"
                        ]
                    }

        # 2. Basic Utility Commands
        if text in ["hi", "hello", "hey"]:
            return {"type": "text", "text": "👋 Hello! I am Lumir, your LAN assistant. Type /help to see what I can do."}

        # 🟢 NAYA CODE: Commands ko yahan pakdo!
        if text == "/server":
            return get_server_status()

        if text == "/weather":
            return get_weather(lat, lon)

        # Update Help Menu
        if text == "/help" or text == "/hrlp":
            return {"type": "text", "text": "🛠️ **Available Commands:**\n1. `/server` (Tank Status)\n2. `/weather` (Live Weather)\n3. `###passport###` (Send an image first)\n4. Just chat with me!"}

        # 🗜️ 3.6 SMART COMPRESS IMAGE LOGIC
        # Regex to find commands like ###compress<60>###
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

            if sender in self.user_context:
                del self.user_context[sender]
            if res["success"]:
                return {"type": "file", "text": res["message"], "file_url": res["file_url"], "file_name": res["file_name"]}
            else:
                return {"type": "text", "text": res["message"]}

        # 🔄 3.13 ROTATE VIDEO LOGIC
        if text.startswith("###rotatevideo:"):
            file_urls = self.user_context.get(sender, [])
            if not file_urls:
                return {"type": "text", "text": "⚠️ No file found!"}

            rot_type = text.replace("###", "").split(":")[1]

            from .utilities import rotate_video
            res = rotate_video(file_urls[-1], rot_type)

            if sender in self.user_context:
                del self.user_context[sender]
            if res["success"]:
                return {"type": "file", "text": res["message"], "file_url": res["file_url"], "file_name": res["file_name"]}
            else:
                return {"type": "text", "text": res["message"]}

        # 🎞️ 3.14 CONVERT TO MP4 LOGIC
        if "###convertmp4###" in text:
            file_urls = self.user_context.get(sender, [])
            if not file_urls:
                return {"type": "text", "text": "⚠️ No file found!"}

            from .utilities import convert_to_mp4
            res = convert_to_mp4(file_urls[-1])

            if sender in self.user_context:
                del self.user_context[sender]
            if res["success"]:
                return {"type": "file", "text": res["message"], "file_url": res["file_url"], "file_name": res["file_name"]}
            else:
                return {"type": "text", "text": res["message"]}

        # 👁️ 3.15 AI VISION LOGIC (Button Click)
        if "###analyzeimage###" in text:
            target_image = file_url or (self.user_context.get(sender)[-1] if self.user_context.get(sender) else None)
            if not target_image:
                return {"type": "text", "text": "⚠️ No image found! Please send an image first."}

            try:
                config = get_ai_config()
                if not config.get("ai_enabled"):
                    return {"type": "text", "text": "🤖 AI is currently offline. Enable it from the Admin Panel."}

                local_path = target_image.lstrip('/') # '/uploads/pic.jpg' -> 'uploads/pic.jpg'

                # Hum AI ko khud ek background prompt bhejenge
                vision_prompt = "Please analyze this image carefully. Describe what you see in detail, including objects, people, environment, and any written text."

                # ask_ai ko call karo image_path ke sath! (History khali rakhenge taaki jaldi process ho)
                ai_reply = ask_ai(prompt=vision_prompt, config=config, history=[], sender=sender, image_path=local_path)

                # Memory clear karo
                if sender in self.user_context:
                    del self.user_context[sender]

                # Format karke bhej do

                safe_reply = ai_reply.replace("\n", "  ").strip()
                safe_reply = re.sub(' +', ' ', safe_reply)

                return {"type": "text", "text": f"👁️ **Gemma Vision Analysis:**\n\n{safe_reply}"}

            except Exception as e:
                print(f"❌ Vision Tool Error: {e}")
                return {"type": "text", "text": f"⚠️ Vision AI Error: {str(e)}"}

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

            # Detect Name using Regex
            name_text = None
            name_match = re.search(r'###passportname<(.*?)>###', text)
            if name_match:
                name_text = name_match.group(1).strip()

            # Detect Page Size
            page_size = "A6"
            page_match = re.search(r'###passportpage<(.*?)>###', text)
            if page_match:
                page_size = page_match.group(1).strip().upper()

            # Detect Layout (e.g. 3x1, 3x2, 3x3)
            layout = None
            layout_match = re.search(r'###passportlayout<(.*?)>###', text)
            if layout_match:
                layout = layout_match.group(1).strip().lower()

            # Process the image
            res = generate_passport_layout(
                target_image,
                grid_size=grid_size,
                date_text=date_text,
                name_text=name_text,
                page_size=page_size,
                layout=layout
            )

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
                current_model = config.get("ai_model", "")

                # 🛑 DYNAMIC BABY LOCK
                smart_models_str = config.get("ai_smart_models", "gpt-oss:20b-cloud, gemma3:27b-cloud")
                smart_models_list = [m.strip().lower() for m in smart_models_str.split(",")]
                is_smart = current_model.lower() in smart_models_list

                # ==========================================
                # 🕵️‍♂️ THE FACT DIGGER (Background Memory Extractor)
                # ==========================================
                trigger_words = ["i like", "mera naam", "mujhe pasand", "i am", "remember", "mera plan", "mai", "mera", "mujhe", "love", "hate"]
                
                # Sirf tab extract karo jab Smart Model ho aur trigger word use hua ho
                if is_smart and any(tw in text.lower() for tw in trigger_words):
                    try:
                        print(f"🕵️‍♂️ [FACT DIGGER] Triggered for {sender}...")
                        extract_prompt = f"Extract a single, concise factual statement about the user from this message: '{text}'. If no clear personal fact exists, reply with exactly 'NONE'."
                        fact = ask_ai(prompt=extract_prompt, config=config, history=[], sender=sender)
                        
                        if fact and "NONE" not in fact.upper() and len(fact) < 150:
                            # 🌟 SAVE TO CHROMA DB INSTEAD OF SQLITE
                            save_fact_to_chroma(sender, fact)
                            print(f"💾 [CHROMA FACT SAVED]: {fact}")
                    except Exception as e:
                        print(f"⚠️ [FACT DIGGER ERROR]: {e}")

                # 🌟 THE MAGIC: Ab saare facts nahi nikalenge!
                # Sirf wo facts nikalenge jo user ke current question ('text') se match karte hain!
                if is_smart:
                    user_facts_list = search_facts_in_chroma(sender, text)
                else:
                    user_facts_list = None

                if not is_smart:
                    print("👶 Baby Model Detected! Disabling Long Term Memory.")
                    chat_history = []
                else:
                    chat_history = get_lumir_history(sender, limit=6)
                    if chat_history and chat_history[-1]["role"] == "user" and chat_history[-1]["content"].lower().strip() == text.lower().strip():
                        chat_history.pop()

                # ==========================================
                # 👁️ VISION CHECK LOGIC (The Sniper Eyes)
                # ==========================================
                target_image_path = None
                image_trigger_words = ["dekh", "image", "photo", "pic", "kya hai", "analyze", "read", "isme", "vision"]

                # Agar user ne abhi image bheji hai, YA context mein image hai aur trigger word use kiya hai
                if sender in self.user_context and self.user_context[sender]:
                    if file_url or any(tw in text for tw in image_trigger_words):
                        last_file = self.user_context[sender][-1]

                        # Check karo ki extension image ka hi ho
                        if last_file.lower().endswith(('.png', '.jpg', '.jpeg', '.webp')):
                            # URL se local path nikalo (e.g. '/uploads/pic.jpg' -> 'uploads/pic.jpg')
                            local_path = last_file.lstrip('/')
                            if os.path.exists(local_path):
                                target_image_path = local_path
                                print(f"👁️ [ENGINE] Sending image to AI: {target_image_path}")

                # AI ko prompt, config, history, sender name, aur AANKHEIN (image_path) bhejo
                ai_reply = ask_ai(prompt=text, config=config, history=chat_history, sender=sender, image_path=target_image_path, user_facts=user_facts_list)

                # 🧹 MEMORY CLEANUP: Image process hone ke baad context clear karo
                # Taaki agli chat mein bina matlab wapas itni heavy Base64 image na jaye!
                if target_image_path and sender in self.user_context:
                    del self.user_context[sender]

                safe_reply = ai_reply.replace("\n", "  ").strip()
                safe_reply = re.sub(' +', ' ', safe_reply)

                return {"type": "text", "text": safe_reply}
            else:
                return {"type": "text", "text": "🤖 AI is currently offline. Enable it from the Admin Panel to chat with me!"}

        except Exception as e:
            print(f"❌ AI Router Error: {e}")
            return {"type": "text", "text": f"⚠️ AI System Error: {str(e)}"}

lumir_engine = LumirEngine()
