from fastapi import APIRouter, UploadFile, File, Form, HTTPException
import os
import shutil
from users import update_user_photo
from PIL import Image, ImageOps # 👈 ImageOps add kiya

router = APIRouter()

# Store profiles in a specific folder
PROFILE_DIR = "uploads/profiles"
os.makedirs(PROFILE_DIR, exist_ok=True)

@router.post("/upload_profile")
async def upload_profile_photo(
    username: str = Form(...), 
    file: UploadFile = File(...)
):
    try:
        # 1. Standardize filename (username.png)
        filename = f"{username}.png"
        file_path = os.path.join(PROFILE_DIR, filename)
        
        # 2. Resize and Save using Pillow
        # (Open the uploaded file directly)
        with Image.open(file.file) as img:
            img = img.convert("RGB") # Convert to RGB to avoid alpha issues
            # Pehle: img.resize((256, 256)) -> Ye photo pichka deta tha (Squeeze) ❌
            # Ab: ImageOps.fit -> Ye center crop karega without distortion ✅
            # Ye photo ko zoom karke fit karega, shape kharab nahi karega.
            img = ImageOps.fit(img, (256, 256), method=Image.Resampling.LANCZOS)
            img.save(file_path, "PNG") # Save as PNG

        # 3. Update Database
        # URL format: /uploads/profiles/username.png
        db_url = f"/uploads/profiles/{filename}"
        update_user_photo(username, db_url)

        return {"success": True, "profile_photo": db_url}

    except Exception as e:
        print(f"Profile upload error: {e}")
        raise HTTPException(status_code=500, detail="Failed to upload profile photo")
