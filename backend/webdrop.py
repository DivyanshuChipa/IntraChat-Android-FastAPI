import os
import time
import shutil
from pathlib import Path
from typing import List, Optional
from fastapi import APIRouter, UploadFile, File, Form, HTTPException
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel

router = APIRouter(prefix="", tags=["WebDrop"])

import urllib.parse

BASE_DIR = Path(__file__).resolve().parent
WEBDROP_DIR = BASE_DIR / "uploads" / "webdrop"
WEBDROP_DIR.mkdir(parents=True, exist_ok=True)

# Also ensure relative uploads/webdrop exists if running from another CWD
if not WEBDROP_DIR.exists():
    Path("uploads/webdrop").mkdir(parents=True, exist_ok=True)

# In-memory shared clipboard snippets (Last 20)
shared_texts = []

class TextDropItem(BaseModel):
    text: str
    sender: Optional[str] = "Guest"

class DeleteFileRequest(BaseModel):
    filename: str

def _get_target_file(raw_name: str) -> Optional[Path]:
    """Finds the target file checking unquoted names and fallback directories."""
    unquoted = urllib.parse.unquote(raw_name).strip()
    safe_name = os.path.basename(unquoted)
    if not safe_name or safe_name in [".", ".."]:
        return None

    # Try both base_dir and current working dir uploads
    candidates = [
        WEBDROP_DIR / safe_name,
        Path("uploads/webdrop") / safe_name,
        WEBDROP_DIR / os.path.basename(raw_name),
        Path("uploads/webdrop") / os.path.basename(raw_name)
    ]
    for c in candidates:
        if c.exists() and c.is_file():
            return c
    return None

def _format_size(size_bytes: int) -> str:
    if size_bytes < 1024:
        return f"{size_bytes} B"
    elif size_bytes < 1024 * 1024:
        return f"{size_bytes / 1024:.1f} KB"
    elif size_bytes < 1024 * 1024 * 1024:
        return f"{size_bytes / (1024 * 1024):.1f} MB"
    else:
        return f"{size_bytes / (1024 * 1024 * 1024):.2f} GB"

def _get_file_category(filename: str) -> str:
    ext = filename.rsplit(".", 1)[-1].lower() if "." in filename else ""
    if ext in ["jpg", "jpeg", "png", "webp", "gif", "svg", "bmp"]:
        return "image"
    elif ext in ["mp4", "mkv", "avi", "mov", "webm", "3gp"]:
        return "video"
    elif ext in ["mp3", "wav", "m4a", "ogg", "aac", "flac"]:
        return "audio"
    elif ext in ["pdf", "doc", "docx", "txt", "xlsx", "pptx", "csv"]:
        return "document"
    elif ext in ["zip", "rar", "7z", "tar", "gz"]:
        return "archive"
    else:
        return "other"

@router.get("/drop")
async def get_drop_page():
    drop_html = Path("static/drop.html")
    if drop_html.exists():
        return FileResponse(drop_html)
    raise HTTPException(status_code=404, detail="WebDrop page not found")

@router.get("/api/drop/files")
async def list_drop_files():
    """Returns all files currently shared in WebDrop sorted by newest first."""
    files = []
    try:
        for entry in WEBDROP_DIR.iterdir():
            if entry.is_file():
                stat = entry.stat()
                name = entry.name
                files.append({
                    "name": name,
                    "size": stat.st_size,
                    "formatted_size": _format_size(stat.st_size),
                    "url": f"/uploads/webdrop/{name}",
                    "uploaded_at": int(stat.st_mtime * 1000),
                    "category": _get_file_category(name)
                })
        # Sort newest first
        files.sort(key=lambda x: x["uploaded_at"], reverse=True)
        return {"success": True, "count": len(files), "files": files}
    except Exception as e:
        return JSONResponse(status_code=500, content={"success": False, "message": str(e)})

@router.post("/api/drop/upload")
async def upload_drop_files(
    files: List[UploadFile] = File(...),
    sender: Optional[str] = Form("Guest")
):
    """Handles multi-file uploads into WebDrop folder without authentication."""
    saved_files = []
    try:
        for file in files:
            raw_name = os.path.basename(file.filename or "file").strip()
            if not raw_name or raw_name in [".", ".."]:
                raw_name = f"drop_{int(time.time())}.bin"

            # Avoid direct collisions by timestamping if already exists
            dest = WEBDROP_DIR / raw_name
            if dest.exists():
                name_part, dot, ext_part = raw_name.rpartition(".")
                if dot:
                    unique_name = f"{name_part}_{int(time.time())}.{ext_part}"
                else:
                    unique_name = f"{raw_name}_{int(time.time())}"
                dest = WEBDROP_DIR / unique_name
            else:
                unique_name = raw_name

            with open(dest, "wb") as buffer:
                shutil.copyfileobj(file.file, buffer)

            stat = dest.stat()
            saved_files.append({
                "name": unique_name,
                "size": stat.st_size,
                "formatted_size": _format_size(stat.st_size),
                "url": f"/uploads/webdrop/{unique_name}",
                "category": _get_file_category(unique_name)
            })

        return {"success": True, "message": f"{len(saved_files)} file(s) dropped successfully!", "files": saved_files}
    except Exception as e:
        return JSONResponse(status_code=500, content={"success": False, "message": str(e)})

def _perform_delete(filename: str):
    target = _get_target_file(filename)
    if target and target.is_file():
        try:
            target.unlink()
            return True, f"Deleted {target.name}", 200
        except Exception as e:
            return False, f"Delete failed: {e}", 500
    return False, f"File not found: {filename}", 404

@router.delete("/api/drop/file/{filename:path}")
async def delete_drop_file(filename: str):
    """Allows deleting a dropped file via DELETE method."""
    success, msg, status = _perform_delete(filename)
    if success:
        return {"success": True, "message": msg}
    return JSONResponse(status_code=status, content={"success": False, "message": msg})

@router.post("/api/drop/delete")
async def delete_drop_file_post(req: DeleteFileRequest):
    """Fallback POST endpoint for file deletion."""
    success, msg, status = _perform_delete(req.filename)
    if success:
        return {"success": True, "message": msg}
    return JSONResponse(status_code=status, content={"success": False, "message": msg})

@router.get("/api/drop/texts")
async def get_shared_texts():
    """Returns recent shared text/clipboard snippets."""
    return {"success": True, "texts": shared_texts}

@router.post("/api/drop/text")
async def add_shared_text(item: TextDropItem):
    """Adds a new text or link snippet to the LAN clipboard."""
    text = item.text.strip()
    if not text:
        raise HTTPException(status_code=400, detail="Text cannot be empty")
    
    entry = {
        "id": int(time.time() * 1000),
        "text": text,
        "sender": item.sender or "Guest",
        "timestamp": int(time.time() * 1000)
    }
    shared_texts.insert(0, entry)
    # Keep only last 20
    if len(shared_texts) > 20:
        shared_texts.pop()
    
    return {"success": True, "entry": entry}
