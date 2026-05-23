from fastapi import APIRouter, UploadFile, HTTPException
from fastapi.responses import FileResponse
import os
from pathlib import Path

router = APIRouter()

UPLOAD_DIR = Path("uploads")
UPLOAD_DIR.mkdir(exist_ok=True)

def _safe_filename(filename: str) -> str:
    # basic sanitization — keep only base name
    name = os.path.basename(filename).strip()
    if name in ("..", ".", ""):
        return ""
    return name

@router.post("/upload")
async def upload_file(file: UploadFile):
    filename = _safe_filename(file.filename)
    if not filename:
        raise HTTPException(status_code=400, detail="Invalid filename")
    dest = UPLOAD_DIR / filename

    # write file asynchronously
    try:
        import aiofiles
        async with aiofiles.open(dest, "wb") as out_file:
            content = await file.read()
            await out_file.write(content)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Write error: {e}")

    # ❌ DO NOT broadcast here
    # ✅ Just return file info; WebSocket (chat.py) will handle routing
    return {"url": f"/uploads/{filename}", "filename": filename}

@router.get("/uploads/{filename}")
async def get_uploaded_file(filename: str):
    filename = _safe_filename(filename)
    if not filename:
        raise HTTPException(status_code=400, detail="Invalid filename")

    path = (UPLOAD_DIR / filename).resolve()
    base = UPLOAD_DIR.resolve()

    if not path.is_relative_to(base) or path == base:
        raise HTTPException(status_code=403, detail="Access denied")

    if not path.exists() or not path.is_file():
        raise HTTPException(status_code=404, detail="Not found")

    return FileResponse(path)
