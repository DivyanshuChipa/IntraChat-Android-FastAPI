import os
import time
import pytesseract
from PIL import Image, ImageOps, ImageDraw, ImageFont

def generate_passport_layout(image_url: str, grid_size: int = 6, date_text: str = None):
    try:
        file_path = image_url.lstrip("/")
        if not os.path.exists(file_path):
            return {"success": False, "message": "File not found on server."}

        with Image.open(file_path) as img:
            img = ImageOps.exif_transpose(img)
            img = img.convert("RGB")

            # 1. Size Decide karo (9 ke liye thoda chhota taaki fit aaye)
            if grid_size == 9:
                pass_w, pass_h = 350, 450
                cols, rows = 3, 3
            else:
                pass_w, pass_h = 413, 531
                cols, rows = 2, 3

            img = ImageOps.fit(img, (pass_w, pass_h), method=Image.Resampling.LANCZOS)

            # 2. Add White Strip and Date (Agar user ne date bheji hai)
            if date_text:
                strip_height = 50
                new_img = Image.new('RGB', (pass_w, pass_h + strip_height), 'white')
                new_img.paste(img, (0, 0))

                draw = ImageDraw.Draw(new_img)
                try:
                    # Ubuntu me ye font usually hota hai
                    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 24)
                except:
                    font = ImageFont.load_default() # Fallback

                # Center the text
                text_bbox = draw.textbbox((0, 0), date_text, font=font)
                text_w = text_bbox[2] - text_bbox[0]
                text_x = (pass_w - text_w) / 2
                text_y = pass_h + 10

                draw.text((text_x, text_y), date_text, fill="black", font=font)

                pass_h += strip_height
                img = new_img

            # 3. Add Black Border
            border_width = 3
            img = ImageOps.expand(img, border=border_width, fill='black')
            pass_w += (border_width * 2)
            pass_h += (border_width * 2)

            # 4. A6 Canvas create karo
            a6_w, a6_h = 1240, 1748
            canvas = Image.new('RGB', (a6_w, a6_h), 'white')

            # 5. Margins calculate karo
            margin_x = (a6_w - (pass_w * cols)) // (cols + 1)
            margin_y = (a6_h - (pass_h * rows)) // (rows + 1)

            # 6. Grid pe paste karo
            for row in range(rows):
                for col in range(cols):
                    x = margin_x + col * (pass_w + margin_x)
                    y = margin_y + row * (pass_h + margin_y)
                    canvas.paste(img, (x, y))

        # 7. Output save karo
        out_filename = f"passport_{int(time.time())}.jpg"
        out_path = os.path.join("uploads", out_filename)
        canvas.save(out_path, "JPEG", quality=95)

        return {
            "success": True,
            "message": f"Generated {grid_size} photos successfully! 🖨️",
            "file_url": f"/uploads/{out_filename}",
            "file_name": out_filename
        }

    except Exception as e:
        return {"success": False, "message": f"Error processing image: {str(e)}"}

def extract_text_from_image(image_url: str):
    try:
        # Path theek karo
        file_path = image_url.lstrip("/")
        
        if not os.path.exists(file_path):
            return {"success": False, "message": "File not found on server."}

        # Original image open karo
        with Image.open(file_path) as img:
            # Phone ki tedi photo ko seedha karo
            img = ImageOps.exif_transpose(img)
            
            # Tesseract se text extract karo
            extracted_text = pytesseract.image_to_string(img)
            
            # Agar image mein text nahi mila
            if not extracted_text.strip():
                return {"success": False, "message": "⚠️ I couldn't find any readable text in this image."}
                
            return {
                "success": True, 
                "text": extracted_text.strip()
            }

    except Exception as e:
        return {"success": False, "message": f"OCR Error: {str(e)}"}

def compress_image(image_url: str):
    try:
        file_path = image_url.lstrip("/")
        if not os.path.exists(file_path):
            return {"success": False, "message": "File not found on server."}

        # Original size calculate karo
        orig_size = os.path.getsize(file_path)

        # Naya file name aur path banao
        base, ext = os.path.splitext(file_path)
        new_file_path = f"{base}_compressed.jpg" # Compressed ko hamesha JPG banayenge
        new_file_url = f"/{new_file_path}"

        with Image.open(file_path) as img:
            # Agar image PNG (transparent) hai, toh usko RGB mein convert karo taaki JPG ban sake
            if img.mode in ("RGBA", "P"):
                img = img.convert("RGB")

            # Dimensions ko limit karo (Agar 4K photo hai toh use normal HD ke aas paas le aao)
            img.thumbnail((1920, 1920), Image.Resampling.LANCZOS)

            # Compress karke save karo (quality=50 normally 2MB ko 200KB bana deta hai)
            img.save(new_file_path, "JPEG", quality=50, optimize=True)

        new_size = os.path.getsize(new_file_path)

        # Size ko KB/MB mein format karne ka chhota function
        def format_size(size):
            return f"{size/1024/1024:.2f} MB" if size > 1024*1024 else f"{size/1024:.0f} KB"

        message = (
            f"🗜️ **Image Compressed Successfully!**\n\n"
            f"📉 Original Size: {format_size(orig_size)}\n"
            f"⚡ New Size: {format_size(new_size)}"
        )

        return {
            "success": True,
            "message": message,
            "file_url": new_file_url,
            "file_name": os.path.basename(new_file_path)
        }

    except Exception as e:
        return {"success": False, "message": f"⚠️ Compression Error: {str(e)}"}
