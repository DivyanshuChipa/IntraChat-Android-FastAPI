import os
import time
from PIL import Image, ImageOps

def generate_passport_layout(image_url: str):
    try:
        # 1. Path theek karo (e.g. "/uploads/img.jpg" -> "uploads/img.jpg")
        file_path = image_url.lstrip("/")

        if not os.path.exists(file_path):
            return {"success": False, "message": "File not found on server."}

        # 2. Original image open karo
        with Image.open(file_path) as img:
            img = img.convert("RGB")

            # 3. Passport Size Target (413x531 pixels approx for 3.5x4.5 cm at 300dpi)
            pass_w, pass_h = 413, 531
            img = ImageOps.fit(img, (pass_w, pass_h), method=Image.Resampling.LANCZOS)

            # 4. A6 Canvas create karo (1240x1748 px at 300dpi)
            a6_w, a6_h = 1240, 1748
            canvas = Image.new('RGB', (a6_w, a6_h), 'white')

            # 5. Margins calculate karo (2 columns, 3 rows center karne ke liye)
            margin_x = (a6_w - (pass_w * 2)) // 3
            margin_y = (a6_h - (pass_h * 3)) // 4

            # 6. Grid pe paste karo
            for row in range(3):
                for col in range(2):
                    x = margin_x + col * (pass_w + margin_x)
                    y = margin_y + row * (pass_h + margin_y)
                    canvas.paste(img, (x, y))

        # 7. Output save karo
        out_filename = f"passport_{int(time.time())}.jpg"
        out_path = os.path.join("uploads", out_filename)
        canvas.save(out_path, "JPEG", quality=95)

        return {
            "success": True,
            "message": "Passport layout generated successfully! 🖨️",
            "file_url": f"/uploads/{out_filename}",
            "file_name": out_filename
        }

    except Exception as e:
        return {"success": False, "message": f"Error processing image: {str(e)}"}