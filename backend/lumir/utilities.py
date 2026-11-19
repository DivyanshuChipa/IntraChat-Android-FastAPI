import os
import sys
import time
import io
import shutil
import pytesseract
from PIL import Image, ImageOps, ImageDraw, ImageFont
from PyPDF2 import PdfMerger, PdfReader
import subprocess

# Ensure backend root is in sys.path
backend_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if backend_root not in sys.path:
    sys.path.insert(0, backend_root)

from paths import get_uploads_dir

# --- Tesseract & Tools Path for Windows ---
if sys.platform == "win32":
    # Common installation paths for Tesseract on Windows, including Intra local tools dir
    possible_paths = [
        r"%LOCALAPPDATA%\IntraServer\bin\tesseract\tesseract.exe",
        r"%LOCALAPPDATA%\IntraServer\bin\tesseract.exe",
        r"C:\Program Files\Tesseract-OCR\tesseract.exe",
        r"C:\Users\%USERNAME%\AppData\Local\Programs\Tesseract-OCR\tesseract.exe",
        r"C:\Program Files (x86)\Tesseract-OCR\tesseract.exe"
    ]
    for path in possible_paths:
        expanded_path = os.path.expandvars(path)
        if os.path.exists(expanded_path):
            pytesseract.pytesseract.tesseract_cmd = expanded_path
            break

    # Ensure Intra local bin is also present in process PATH for FFmpeg & other child processes
    local_intra_bin = os.path.expandvars(r"%LOCALAPPDATA%\IntraServer\bin")
    if os.path.exists(local_intra_bin) and local_intra_bin not in os.environ.get("PATH", "").split(os.pathsep):
        os.environ["PATH"] = local_intra_bin + os.pathsep + os.environ.get("PATH", "")


def resolve_input_path(url_or_path: str) -> str | None:
    """
    Safely resolves any URL or relative file path to the actual absolute path on disk.
    Handles:
      - '/uploads/filename.ext'
      - 'uploads/filename.ext'
      - 'filename.ext'
      - absolute paths
      - %LOCALAPPDATA%/IntraServer/uploads/
      - local backend/uploads/ or cwd
    """
    if not url_or_path or not isinstance(url_or_path, str):
        return None

    clean = url_or_path.split("?")[0].split("#")[0].strip()
    if not clean:
        return None

    # If already an existing absolute path
    if os.path.isabs(clean) and os.path.exists(clean) and os.path.isfile(clean):
        return os.path.abspath(clean)

    uploads_dir = get_uploads_dir()
    bare_name = os.path.basename(clean.replace("\\", "/"))
    rel_clean = clean.lstrip("/\\")

    # Candidates to check in order:
    candidates = [
        os.path.join(uploads_dir, bare_name),
        os.path.join(uploads_dir, rel_clean),
        rel_clean,
        os.path.join("uploads", bare_name),
        bare_name
    ]

    for cand in candidates:
        if cand and os.path.exists(cand) and os.path.isfile(cand):
            return os.path.abspath(cand)

    return None


def get_output_paths(filename: str) -> tuple[str, str]:
    """
    Returns (abs_disk_path, public_url) for a generated output file.
    Always saves inside get_uploads_dir() and ensures public URL is /uploads/{filename}.
    """
    safe_name = os.path.basename(filename.replace("\\", "/"))
    uploads_dir = get_uploads_dir()
    os.makedirs(uploads_dir, exist_ok=True)
    disk_path = os.path.join(uploads_dir, safe_name)
    public_url = f"/uploads/{safe_name}"
    return disk_path, public_url


def generate_passport_layout(
    image_url: str,
    grid_size: int = 6,
    date_text: str = None,
    name_text: str = None,
    page_size: str = "A6",
    layout: str = None
):
    try:
        file_path = resolve_input_path(image_url)
        if not file_path:
            return {"success": False, "message": "File not found on server."}

        with Image.open(file_path) as img:
            img = ImageOps.exif_transpose(img)
            img = img.convert("RGB")

            # 1. Page + Layout Selection
            page_size = (page_size or "A6").strip().upper()
            if page_size == "A4":
                canvas_w, canvas_h = 2480, 3508
            else:
                canvas_w, canvas_h = 1240, 1748
                page_size = "A6"

            if layout and "x" in layout.lower():
                try:
                    rows, cols = [int(x) for x in layout.lower().split("x")]
                except Exception:
                    rows, cols = (3, 3) if grid_size == 9 else (3, 2)
            else:
                rows, cols = (3, 3) if grid_size == 9 else (3, 2)

            rows = max(1, rows)
            cols = max(1, cols)

            # 2. Add White Strip and Meta Text (Name/Date)
            meta_lines = []
            if name_text:
                meta_lines.append(name_text.strip())
            if date_text:
                meta_lines.append(date_text.strip())

            strip_height = 0
            if meta_lines:
                strip_height = 22 + (30 * len(meta_lines))
            border_width = 3

            # 3. Fit calc by selected page + rows/cols
            outer_margin_x = 36 if page_size == "A6" else 48
            outer_margin_y = 36 if page_size == "A6" else 48
            gap_x = 24 if page_size == "A6" else 30
            gap_y = 24 if page_size == "A6" else 30

            available_w = max(120, canvas_w - (outer_margin_x * 2) - ((cols - 1) * gap_x))
            available_h = max(140, canvas_h - (outer_margin_y * 2) - ((rows - 1) * gap_y))

            # Width/Height cap per tile for requested rows/cols
            tile_w_cap = max(100, available_w // cols)
            tile_h_cap = max(120, available_h // rows)

            usable_w = max(80, tile_w_cap - (border_width * 2))
            usable_h = max(100, tile_h_cap - strip_height - (border_width * 2))

            # Passport aspect ratio ~35x45
            ratio = 35 / 45
            pass_w = min(usable_w, int(usable_h * ratio))
            pass_h = int(pass_w / ratio)
            if pass_h > usable_h:
                pass_h = usable_h
                pass_w = int(pass_h * ratio)

            img = ImageOps.fit(img, (pass_w, pass_h), method=Image.Resampling.LANCZOS)

            if meta_lines:
                new_img = Image.new('RGB', (pass_w, pass_h + strip_height), 'white')
                new_img.paste(img, (0, 0))

                draw = ImageDraw.Draw(new_img)
                try:
                    if sys.platform == "win32":
                        font = ImageFont.truetype("arial.ttf", 24)
                    else:
                        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 24)
                except:
                    font = ImageFont.load_default()

                for i, line in enumerate(meta_lines):
                    text_bbox = draw.textbbox((0, 0), line, font=font)
                    text_w = text_bbox[2] - text_bbox[0]
                    text_x = (pass_w - text_w) / 2
                    text_y = pass_h + 8 + (i * 28)
                    draw.text((text_x, text_y), line, fill="black", font=font)

                pass_h += strip_height
                img = new_img

            # 4. Add Black Border + Canvas
            img = ImageOps.expand(img, border=border_width, fill='black')
            pass_w += (border_width * 2)
            pass_h += (border_width * 2)
            canvas = Image.new('RGB', (canvas_w, canvas_h), 'white')

            # 5. Packed grid placement (tight rows/cols, top aligned)
            total_w = (pass_w * cols) + (gap_x * (cols - 1))
            start_x = max(0, (canvas_w - total_w) // 2)
            start_y = outer_margin_y

            for row in range(rows):
                for col in range(cols):
                    x = start_x + col * (pass_w + gap_x)
                    y = start_y + row * (pass_h + gap_y)
                    canvas.paste(img, (x, y))

        # 6. Output save karo
        out_filename = f"passport_{int(time.time())}.jpg"
        out_disk_path, out_url = get_output_paths(out_filename)
        canvas.save(out_disk_path, "JPEG", quality=95)

        return {
            "success": True,
            "message": f"Generated {grid_size} photos successfully! 🖨️",
            "file_url": out_url,
            "file_name": out_filename
        }

    except Exception as e:
        return {"success": False, "message": f"Error processing image: {str(e)}"}


def extract_text_from_image(image_url: str):
    try:
        file_path = resolve_input_path(image_url)
        if not file_path:
            return {"success": False, "message": "File not found on server."}

        with Image.open(file_path) as img:
            img = ImageOps.exif_transpose(img)
            extracted_text = pytesseract.image_to_string(img)

            if not extracted_text.strip():
                return {"success": False, "message": "⚠️ I couldn't find any readable text in this image."}

            return {
                "success": True,
                "text": extracted_text.strip()
            }

    except Exception as e:
        return {"success": False, "message": f"OCR Error: {str(e)}"}


def compress_image_to_target(image_url: str, target_kb: int):
    try:
        file_path = resolve_input_path(image_url)
        if not file_path:
            return {"success": False, "message": "File not found on server."}

        orig_size = os.path.getsize(file_path)
        base_name = os.path.splitext(os.path.basename(file_path))[0]
        out_filename = f"{base_name}_compressed_{target_kb}kb.jpg"
        new_file_path, new_file_url = get_output_paths(out_filename)

        target_bytes = target_kb * 1024

        with Image.open(file_path) as img:
            if img.mode in ("RGBA", "P"):
                img = img.convert("RGB")

            # Initial optimization
            img.thumbnail((1920, 1920), Image.Resampling.LANCZOS)

            quality = 95
            temp_buffer = io.BytesIO()

            # 🔄 Quality loop
            while quality > 10:
                temp_buffer.seek(0)
                temp_buffer.truncate(0)
                img.save(temp_buffer, format="JPEG", quality=quality, optimize=True)

                if temp_buffer.tell() <= target_bytes:
                    break
                quality -= 5

            # If still larger, scale dimensions down
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
            "file_name": out_filename
        }

    except Exception as e:
        return {"success": False, "message": f"⚠️ Compression Error: {str(e)}"}


def convert_images_to_pdf(image_urls: list):
    images_to_close = []
    try:
        if not image_urls:
            return {"success": False, "message": "No images found to convert."}

        first_file_path = resolve_input_path(image_urls[0])
        if not first_file_path:
            return {"success": False, "message": "File not found on server."}

        base_name = os.path.splitext(os.path.basename(first_file_path))[0]
        out_filename = f"{base_name}_multipage.pdf" if len(image_urls) > 1 else f"{base_name}_converted.pdf"
        new_file_path, new_file_url = get_output_paths(out_filename)

        pdf_images = []

        first_img = Image.open(first_file_path)
        images_to_close.append(first_img)
        if first_img.mode in ("RGBA", "P"):
            first_img = first_img.convert("RGB")
            images_to_close.append(first_img)

        pdf_images.append(first_img)

        for url in image_urls[1:]:
            path = resolve_input_path(url)
            if path and os.path.exists(path):
                img = Image.open(path)
                images_to_close.append(img)
                if img.mode in ("RGBA", "P"):
                    img = img.convert("RGB")
                    images_to_close.append(img)
                pdf_images.append(img)

        if len(pdf_images) > 1:
            first_img.save(new_file_path, "PDF", resolution=100.0, save_all=True, append_images=pdf_images[1:])
        else:
            first_img.save(new_file_path, "PDF", resolution=100.0)

        message = f"📄 **Successfully converted {len(image_urls)} image(s) to PDF!**"

        return {
            "success": True,
            "message": message,
            "file_url": new_file_url,
            "file_name": out_filename
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
    files_to_close = []

    try:
        if not file_urls:
            return {"success": False, "message": "No files found to merge."}

        valid_pdfs = [url for url in file_urls if url.lower().split("?")[0].endswith('.pdf')]
        resolved_pdfs = []
        for url in valid_pdfs:
            p = resolve_input_path(url)
            if p and os.path.exists(p) and os.path.isfile(p):
                resolved_pdfs.append(p)

        if len(resolved_pdfs) < 2:
            return {"success": False, "message": "⚠️ Please send at least 2 valid PDF files to merge."}

        first_name = os.path.splitext(os.path.basename(resolved_pdfs[0]))[0]
        out_filename = f"{first_name}_merged.pdf"
        new_file_path, new_file_url = get_output_paths(out_filename)

        for abs_path in resolved_pdfs:
            f = open(abs_path, 'rb')
            files_to_close.append(f)
            merger.append(f)

        with open(new_file_path, "wb") as f_out:
            merger.write(f_out)

        message = f"🔗 **Successfully merged {len(resolved_pdfs)} PDFs into a single file!**"

        return {
            "success": True,
            "message": message,
            "file_url": new_file_url,
            "file_name": out_filename
        }

    except Exception as e:
        return {"success": False, "message": f"⚠️ PDF Merge Error: {str(e)}"}

    finally:
        for f in files_to_close:
            try:
                f.close()
            except Exception:
                pass
        merger.close()


def extract_text_from_pdf(file_url: str):
    try:
        file_path = resolve_input_path(file_url)
        if not file_path:
            return {"success": False, "message": "⚠️ File not found on server."}

        reader = PdfReader(file_path)
        extracted_text = ""

        for page in reader.pages:
            text = page.extract_text()
            if text:
                extracted_text += text + "\n\n"

        if not extracted_text.strip():
            return {"success": False, "message": "⚠️ No readable text found! (This PDF might contain only scanned images)."}

        base_name = os.path.splitext(os.path.basename(file_path))[0]
        out_filename = f"{base_name}_extracted.txt"
        new_file_path, new_file_url = get_output_paths(out_filename)

        with open(new_file_path, "w", encoding="utf-8") as f:
            f.write(extracted_text)

        preview_text = extracted_text[:150].replace("\n", " ") + "..." if len(extracted_text) > 150 else extracted_text

        message = (
            f"📄 **Text Extracted Successfully!**\n\n"
            f"**Preview:** _{preview_text}_"
        )

        return {
            "success": True,
            "message": message,
            "file_url": new_file_url,
            "file_name": out_filename
        }

    except Exception as e:
        return {"success": False, "message": f"⚠️ Extraction Error: {str(e)}"}


def compress_pdf(file_url: str):
    try:
        file_path = resolve_input_path(file_url)
        if not file_path:
            return {"success": False, "message": "⚠️ File not found on server."}

        base_name = os.path.splitext(os.path.basename(file_path))[0]
        out_filename = f"{base_name}_compressed.pdf"
        new_file_path, new_file_url = get_output_paths(out_filename)

        orig_size = os.path.getsize(file_path)

        gs_bin = "gs"
        if sys.platform == "win32":
            for cand in ["gswin64c", "gswin32c", "gs"]:
                if shutil.which(cand):
                    gs_bin = cand
                    break

        gs_cmd = [
            gs_bin, "-sDEVICE=pdfwrite", "-dCompatibilityLevel=1.4",
            "-dPDFSETTINGS=/ebook", "-dNOPAUSE", "-dQUIET", "-dBATCH",
            f"-sOutputFile={new_file_path}", file_path
        ]

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
            "file_name": out_filename
        }

    except Exception as e:
        return {"success": False, "message": f"⚠️ PDF Compression Error: {str(e)}"}


def extract_audio_from_video(file_url: str):
    try:
        file_path = resolve_input_path(file_url)
        if not file_path:
            return {"success": False, "message": "⚠️ Video file not found on server."}

        base_name = os.path.splitext(os.path.basename(file_path))[0]
        out_filename = f"{base_name}_audio.mp3"
        new_file_path, new_file_url = get_output_paths(out_filename)

        ffmpeg_cmd = [
            "ffmpeg", "-i", file_path,
            "-vn", "-q:a", "0", "-map", "a",
            "-threads", "2",
            new_file_path, "-y"
        ]

        subprocess.run(ffmpeg_cmd, stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT, check=True)

        if not os.path.exists(new_file_path):
            return {"success": False, "message": "⚠️ Audio extraction failed."}

        orig_size = os.path.getsize(file_path)
        new_size = os.path.getsize(new_file_path)

        def format_size(s):
            return f"{s/1024/1024:.2f} MB" if s > 1024*1024 else f"{s/1024:.0f} KB"

        message = (
            f"🎵 **Audio Extracted Successfully!**\n\n"
            f"🎬 Video Size: {format_size(orig_size)}\n"
            f"🎧 MP3 Size: {format_size(new_size)}"
        )

        return {
            "success": True,
            "message": message,
            "file_url": new_file_url,
            "file_name": out_filename
        }

    except subprocess.CalledProcessError:
        return {"success": False, "message": "⚠️ FFmpeg Error: Make sure ffmpeg is installed."}
    except Exception as e:
        return {"success": False, "message": f"⚠️ Extraction Error: {str(e)}"}


def compress_video(file_url: str, crf: int = 28, fmt: str = "mp4"):
    try:
        file_path = resolve_input_path(file_url)
        if not file_path:
            return {"success": False, "message": "⚠️ Video not found."}

        base_name = os.path.splitext(os.path.basename(file_path))[0]
        out_filename = f"{base_name}_compressed.{fmt}"
        new_file_path, new_file_url = get_output_paths(out_filename)

        ffmpeg_cmd = [
            "ffmpeg", "-i", file_path,
            "-vcodec", "libx264", "-crf", str(crf),
            "-preset", "veryfast", "-c:a", "aac",
            "-threads", "2",
            new_file_path, "-y"
        ]

        subprocess.run(ffmpeg_cmd, stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT, check=True)

        if not os.path.exists(new_file_path):
            return {"success": False, "message": "⚠️ Compression failed."}

        orig_size = os.path.getsize(file_path)
        new_size = os.path.getsize(new_file_path)

        def format_size(s):
            return f"{s/1024/1024:.2f} MB" if s > 1024*1024 else f"{s/1024:.0f} KB"

        message = (
            f"🗜️ **Video Compressed Successfully!**\n\n"
            f"⚙️ Setting: CRF {crf}, Format: .{fmt.upper()}\n"
            f"📉 Original: {format_size(orig_size)}\n"
            f"⚡ New Size: {format_size(new_size)}"
        )

        return {"success": True, "message": message, "file_url": new_file_url, "file_name": out_filename}
    except Exception as e:
        return {"success": False, "message": f"⚠️ Error: {str(e)}"}


def rotate_video(file_url: str, rotation_type: str):
    try:
        file_path = resolve_input_path(file_url)
        if not file_path:
            return {"success": False, "message": "⚠️ Video not found."}

        base_name, ext = os.path.splitext(os.path.basename(file_path))
        out_filename = f"{base_name}_rotated{ext}"
        new_file_path, new_file_url = get_output_paths(out_filename)

        filters = {
            "90_cw": "transpose=1",           # 90 Clockwise
            "90_ccw": "transpose=2",          # 90 Counter-Clockwise
            "180": "transpose=1,transpose=1", # 180 degrees
            "hflip": "hflip"                  # Horizontal Flip
        }

        vf_param = filters.get(rotation_type, "transpose=1")

        ffmpeg_cmd = [
            "ffmpeg", "-i", file_path,
            "-vf", vf_param, "-c:a", "copy",
            "-threads", "2",
            new_file_path, "-y"
        ]

        subprocess.run(ffmpeg_cmd, stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT, check=True)

        return {"success": True, "message": "🔄 **Video Rotated Successfully!**", "file_url": new_file_url, "file_name": out_filename}
    except Exception as e:
        return {"success": False, "message": f"⚠️ Error: {str(e)}"}


def convert_to_mp4(file_url: str):
    try:
        file_path = resolve_input_path(file_url)
        if not file_path:
            return {"success": False, "message": "⚠️ Video not found."}

        base_name, ext = os.path.splitext(os.path.basename(file_path))
        if ext.lower() == ".mp4":
            return {"success": False, "message": "⚠️ File is already an MP4!"}

        out_filename = f"{base_name}_converted.mp4"
        new_file_path, new_file_url = get_output_paths(out_filename)

        ffmpeg_cmd = [
            "ffmpeg", "-i", file_path,
            "-c:v", "libx264", "-preset", "veryfast",
            "-c:a", "aac",
            "-threads", "2",
            new_file_path, "-y"
        ]

        subprocess.run(ffmpeg_cmd, stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT, check=True)

        return {"success": True, "message": "🎞️ **Video Converted to MP4!**", "file_url": new_file_url, "file_name": out_filename}
    except Exception as e:
        return {"success": False, "message": f"⚠️ Error: {str(e)}"}
