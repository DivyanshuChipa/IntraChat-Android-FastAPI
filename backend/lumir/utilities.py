import os
import time
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