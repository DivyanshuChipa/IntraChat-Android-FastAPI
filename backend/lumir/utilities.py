import os
import time
import pytesseract
from PIL import Image, ImageOps, ImageDraw, ImageFont
from PyPDF2 import PdfMerger, PdfReader
import subprocess

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

import io

def compress_image_to_target(image_url: str, target_kb: int):
    try:
        file_path = image_url.lstrip("/")
        if not os.path.exists(file_path):
            return {"success": False, "message": "File not found on server."}

        orig_size = os.path.getsize(file_path)
        base, ext = os.path.splitext(file_path)
        new_file_path = f"{base}_compressed_{target_kb}kb.jpg"
        new_file_url = f"/{new_file_path}"

        target_bytes = target_kb * 1024

        with Image.open(file_path) as img:
            if img.mode in ("RGBA", "P"):
                img = img.convert("RGB")

            # Initial optimization
            img.thumbnail((1920, 1920), Image.Resampling.LANCZOS)

            quality = 95
            temp_buffer = io.BytesIO()

            # 🔄 MAGIC LOOP: Quality dreere dreere kam karo
            while quality > 10:
                temp_buffer.seek(0)
                temp_buffer.truncate(0)
                img.save(temp_buffer, format="JPEG", quality=quality, optimize=True)

                if temp_buffer.tell() <= target_bytes:
                    break # Size mil gaya, loop roko!
                quality -= 5

            # Agar quality 10 par bhi size bada hai, toh dimensions chhote karo
            if temp_buffer.tell() > target_bytes:
                scale = 0.9
                while temp_buffer.tell() > target_bytes and scale > 0.3:
                    new_size = (int(img.width * scale), int(img.height * scale))
                    resized_img = img.resize(new_size, Image.Resampling.LANCZOS)

                    temp_buffer.seek(0)
                    temp_buffer.truncate(0)
                    resized_img.save(temp_buffer, format="JPEG", quality=15, optimize=True)
                    scale -= 0.1

            # Final file save
            with open(new_file_path, "wb") as f:
                f.write(temp_buffer.getvalue())

        new_size = os.path.getsize(new_file_path)

        def format_size(s):
            return f"{s/1024/1024:.2f} MB" if s > 1024*1024 else f"{s/1024:.0f} KB"

        message = (
            f"🗜️ **Target Achieved! (Limit: {target_kb} KB)**\n\n"
            f"📉 Original: {format_size(orig_size)}\n"
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

def convert_images_to_pdf(image_urls: list):
    images_to_close = []
    try:
        if not image_urls:
            return {"success": False, "message": "No images found to convert."}

        # Pehli image uthao base name banane ke liye
        first_file_path = image_urls[0].lstrip("/")
        if not os.path.exists(first_file_path):
            return {"success": False, "message": "File not found on server."}

        base, ext = os.path.splitext(first_file_path)

        # Agar 1 se zyada photos hain toh naam mein 'multipage' lagayenge
        new_file_path = f"{base}_multipage.pdf" if len(image_urls) > 1 else f"{base}_converted.pdf"
        new_file_url = f"/{new_file_path}"

        pdf_images = []

        # Pehli image ko open karo
        first_img = Image.open(first_file_path)
        images_to_close.append(first_img)
        if first_img.mode in ("RGBA", "P"):
            first_img = first_img.convert("RGB")
            images_to_close.append(first_img)

        pdf_images.append(first_img)

        # Baki saari images ko open karke list mein daalo
        for url in image_urls[1:]:
            path = url.lstrip("/")
            if os.path.exists(path):
                img = Image.open(path)
                images_to_close.append(img)
                if img.mode in ("RGBA", "P"):
                    img = img.convert("RGB")
                    images_to_close.append(img)
                pdf_images.append(img)

        # 🪄 MAGIC: PIL ka append_images feature sari photos ko ek PDF mein jod dega
        if len(pdf_images) > 1:
            first_img.save(new_file_path, "PDF", resolution=100.0, save_all=True, append_images=pdf_images[1:])
        else:
            first_img.save(new_file_path, "PDF", resolution=100.0)

        message = f"📄 **Successfully converted {len(image_urls)} image(s) to PDF!**"

        return {
            "success": True,
            "message": message,
            "file_url": new_file_url,
            "file_name": os.path.basename(new_file_path)
        }

    except Exception as e:
        return {"success": False, "message": f"⚠️ PDF Conversion Error: {str(e)}"}
    finally:
        for img in images_to_close:
            try:
                img.close()
            except Exception:
                pass

def merge_multiple_pdfs(file_urls: list):
    merger = PdfMerger()
    files_to_close = []  # Codex ka lesson: Files ko track karo taaki close kar sakein

    try:
        if not file_urls:
            return {"success": False, "message": "No files found to merge."}

        # Sirf .pdf files ko filter karo (agar galti se photo bhej di ho)
        valid_pdfs = [url for url in file_urls if url.lower().endswith('.pdf')]

        if len(valid_pdfs) < 2:
            return {"success": False, "message": "⚠️ Please send at least 2 PDF files to merge."}

        # Security: Designated uploads directory (Project root for now, or specific uploads/)
        # Assuming current working directory is the base
        base_dir = os.path.abspath(os.getcwd())

        # Pehli PDF ka naam uthao base name ke liye
        first_pdf_rel = valid_pdfs[0].lstrip("/")
        base, ext = os.path.splitext(first_pdf_rel)
        new_file_path = f"{base}_merged.pdf"

        # Verify output path security
        abs_new_file_path = os.path.abspath(new_file_path)
        if not abs_new_file_path.startswith(base_dir):
             return {"success": False, "message": "⚠️ Security Error: Output path traversal detected."}

        new_file_url = f"/{new_file_path}"

        for url in valid_pdfs:
            # Canonicalize path
            rel_path = url.lstrip("/")
            abs_path = os.path.abspath(rel_path)

            # Verify file is within base directory
            if not abs_path.startswith(base_dir):
                return {"success": False, "message": f"⚠️ Security Error: Path traversal detected for {rel_path}."}

            if os.path.exists(abs_path) and os.path.isfile(abs_path):
                # PDF ko 'rb' (read binary) mode mein open karo
                f = open(abs_path, 'rb')
                files_to_close.append(f)
                merger.append(f)
            else:
                # Codex ka lesson: Agar file missing hai toh turant error do
                return {"success": False, "message": f"⚠️ Error: PDF file missing or deleted ({rel_path})."}

        # Merged PDF ko save karo
        with open(abs_new_file_path, "wb") as f_out:
            merger.write(f_out)

        message = f"🔗 **Successfully merged {len(valid_pdfs)} PDFs into a single file!**"

        return {
            "success": True,
            "message": message,
            "file_url": new_file_url,
            "file_name": os.path.basename(new_file_path)
        }

    except Exception as e:
        return {"success": False, "message": f"⚠️ PDF Merge Error: {str(e)}"}

    finally:
        # 🧹 Jhadoo-Pocha: Saari open files ko close karo taaki server crash na ho
        for f in files_to_close:
            try:
                f.close()
            except Exception:
                pass
        merger.close()

def extract_text_from_pdf(file_url: str):
    try:
        file_path = file_url.lstrip("/")
        if not os.path.exists(file_path):
            return {"success": False, "message": "⚠️ File not found on server."}

        reader = PdfReader(file_path)
        extracted_text = ""
        
        # Har page se text nikalo
        for page in reader.pages:
            text = page.extract_text()
            if text:
                extracted_text += text + "\n\n"
        
        # Agar PDF image-based (scanned) hai jisme text nahi hai
        if not extracted_text.strip():
            return {"success": False, "message": "⚠️ No readable text found! (This PDF might contain only scanned images)."}

        # Text ko .txt file mein save karo
        base, ext = os.path.splitext(file_path)
        new_file_path = f"{base}_extracted.txt"
        new_file_url = f"/{new_file_path}"
        
        with open(new_file_path, "w", encoding="utf-8") as f:
            f.write(extracted_text)
        
        # Chat mein dikhane ke liye ek chhota sa preview
        preview_text = extracted_text[:150].replace("\n", " ") + "..." if len(extracted_text) > 150 else extracted_text

        message = (
            f"📄 **Text Extracted Successfully!**\n\n"
            f"**Preview:** _{preview_text}_"
        )

        return {
            "success": True,
            "message": message,
            "file_url": new_file_url,
            "file_name": os.path.basename(new_file_path)
        }

    except Exception as e:
        return {"success": False, "message": f"⚠️ Extraction Error: {str(e)}"}

def compress_pdf(file_url: str):
    try:
        file_path = file_url.lstrip("/")
        if not os.path.exists(file_path):
            return {"success": False, "message": "⚠️ File not found on server."}

        base, ext = os.path.splitext(file_path)
        new_file_path = f"{base}_compressed.pdf"
        new_file_url = f"/{new_file_path}"

        orig_size = os.path.getsize(file_path)

        # 🪄 MAGIC: Linux Ghostscript command for PDF compression
        # PDFSETTINGS=/ebook gives a great balance of size and readability for notes
        gs_cmd = [
            "gs", "-sDEVICE=pdfwrite", "-dCompatibilityLevel=1.4",
            "-dPDFSETTINGS=/ebook", "-dNOPAUSE", "-dQUIET", "-dBATCH",
            f"-sOutputFile={new_file_path}", file_path
        ]

        # Process ko run karo
        subprocess.run(gs_cmd, check=True)

        if not os.path.exists(new_file_path):
            return {"success": False, "message": "⚠️ Compression failed."}

        new_size = os.path.getsize(new_file_path)

        def format_size(s):
            return f"{s/1024/1024:.2f} MB" if s > 1024*1024 else f"{s/1024:.0f} KB"

        message = (
            f"🗜️ **PDF Compressed Successfully!**\n\n"
            f"📉 Original: {format_size(orig_size)}\n"
            f"⚡ New Size: {format_size(new_size)}"
        )

        return {
            "success": True,
            "message": message,
            "file_url": new_file_url,
            "file_name": os.path.basename(new_file_path)
        }

    except Exception as e:
        return {"success": False, "message": f"⚠️ PDF Compression Error: {str(e)}"}