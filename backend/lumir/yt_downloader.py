import os
import re
import uuid
import yt_dlp
from datetime import datetime

DOWNLOAD_DIR = "uploads/yt_downloads"

def ensure_dir_exists():
    if not os.path.exists(DOWNLOAD_DIR):
        os.makedirs(DOWNLOAD_DIR)

def download_youtube_video(url: str, format_choice: str):
    """
    Downloads a youtube video or extracts audio.
    format_choice can be: 'mp3', '480p', '720p', 'best'
    """
    ensure_dir_exists()
    
    unique_id = uuid.uuid4().hex[:8]
    output_template = f"{DOWNLOAD_DIR}/yt_{unique_id}_temp.%(ext)s"
    
    ydl_opts = {
        'outtmpl': output_template,
        'noplaylist': True,
        'quiet': True,
        'no_warnings': True,
        'extractor_args': {
            'youtube': {
                'player_client': ['default', 'web_safari'],
            }
        }
    }
    
    try:
        if format_choice == 'mp3':
            ydl_opts.update({
                'format': 'bestaudio/best',
                'postprocessors': [{
                    'key': 'FFmpegExtractAudio',
                    'preferredcodec': 'mp3',
                    'preferredquality': '192',
                }],
            })
        elif format_choice == '480p':
            ydl_opts.update({
                'format': 'bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[height<=480][ext=mp4]/best',
                'merge_output_format': 'mp4',
            })
        elif format_choice == '720p':
            ydl_opts.update({
                'format': 'bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720][ext=mp4]/best',
                'merge_output_format': 'mp4',
            })
        elif format_choice == 'best':
            ydl_opts.update({
                'format': 'bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best',
                'merge_output_format': 'mp4',
            })
        else:
            return {"success": False, "message": "⚠️ Invalid format choice."}

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info_dict = ydl.extract_info(url, download=True)
            
            # Get filename after processing
            if format_choice == 'mp3':
                # yt-dlp changes the extension to .mp3 after FFmpeg processing
                file_name = ydl.prepare_filename(info_dict).rsplit('.', 1)[0] + '.mp3'
            else:
                # Merge output format ensures it's mp4
                file_name = ydl.prepare_filename(info_dict).rsplit('.', 1)[0] + '.mp4'
                
            # Fallback if the expected file doesn't exist (e.g. video was already mp4 so merge didn't rename)
            if not os.path.exists(file_name):
                 file_name = ydl.prepare_filename(info_dict)
                 
            # Sanitize the video title to be extremely safe (ASCII only, no special characters, replace spaces with underscores)
            title = info_dict.get('title', 'video')
            clean_title = re.sub(r'[^a-zA-Z0-9]', ' ', title)
            clean_title = ' '.join(clean_title.split())
            clean_title = clean_title.replace(' ', '_')
            if not clean_title:
                clean_title = "video"
                
            # Extract actual extension and rename the temp file to the safe filename
            ext = file_name.rsplit('.', 1)[-1]
            final_file_name = f"{DOWNLOAD_DIR}/yt_{unique_id}_{clean_title}.{ext}"
            
            try:
                os.rename(file_name, final_file_name)
            except Exception:
                final_file_name = file_name  # Fallback to temp file name if rename fails
                
            # Convert to relative URL like /uploads/yt_downloads/...
            relative_url = "/" + final_file_name.replace('\\', '/')
            just_name = os.path.basename(final_file_name)
            
            return {
                "success": True, 
                "file_url": relative_url, 
                "file_name": just_name,
                "message": f"✅ Downloaded: {info_dict.get('title', 'Video')}"
            }
            
    except yt_dlp.utils.DownloadError as e:
        return {"success": False, "message": f"⚠️ YouTube Download Error: {str(e)}"}
    except Exception as e:
        return {"success": False, "message": f"⚠️ Unexpected Error: {str(e)}"}

