"""
Modular Capabilities & Addon Manager for Intra Server (Windows / Cross-platform).
Manages detection, downloading, installation, and removal of external binaries
like FFmpeg, Tesseract OCR, and yt-dlp in %LOCALAPPDATA%/IntraServer/bin (or user tools dir).
"""

import os
import sys
import shutil
import urllib.request
import zipfile
import threading
from typing import Dict, Any, Callable, Optional


def get_intra_tools_dir() -> str:
    """Returns the dedicated directory where Intra stores downloaded tools/binaries."""
    local_app_data = os.environ.get("LOCALAPPDATA")
    if local_app_data:
        tools_dir = os.path.join(local_app_data, "IntraServer", "bin")
    else:
        tools_dir = os.path.join(os.path.expanduser("~"), ".intra_server", "bin")
    return os.path.abspath(tools_dir)


def ensure_tools_dir() -> str:
    """Ensures tools directory exists and returns its path."""
    d = get_intra_tools_dir()
    os.makedirs(d, exist_ok=True)
    return d


def inject_tools_to_path():
    """Prepends the Intra tools directory to the current process PATH and returns updated PATH."""
    tools_dir = get_intra_tools_dir()
    current_path = os.environ.get("PATH", "")
    parts = current_path.split(os.pathsep)
    if tools_dir not in parts:
        os.environ["PATH"] = tools_dir + os.pathsep + current_path
    return os.environ["PATH"]


# Capability Definitions
# Note: URLs point to official/trusted standalone release archives or executables
CAPABILITIES: Dict[str, Dict[str, Any]] = {
    "ffmpeg": {
        "name": "FFmpeg",
        "title": "FFmpeg (Video / Audio Processing)",
        "description": "Required for video compression, format conversion, audio extraction, and HLS streaming.",
        "binary_names": ["ffmpeg.exe", "ffmpeg"],
        "known_paths": [
            r"C:\Program Files\ffmpeg\bin\ffmpeg.exe",
            r"C:\ffmpeg\bin\ffmpeg.exe"
        ],
        "download_type": "zip_extract",
        # Essentials build zip from gyan.dev (official FFmpeg Windows build provider)
        "download_url": "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip",
        "extract_patterns": ["bin/ffmpeg.exe", "bin/ffprobe.exe"]
    },
    "tesseract": {
        "name": "Tesseract OCR",
        "title": "Tesseract OCR (Document & Image Scanner)",
        "description": "Required for scanning documents, receipts, menus, and reading text from photos.",
        "binary_names": ["tesseract.exe", "tesseract"],
        "known_paths": [
            r"C:\Program Files\Tesseract-OCR\tesseract.exe",
            r"C:\Users\%USERNAME%\AppData\Local\Programs\Tesseract-OCR\tesseract.exe",
            r"C:\Program Files (x86)\Tesseract-OCR\tesseract.exe"
        ],
        "download_type": "direct_installer",
        # UB Mannheim official Windows portable/setup
        "download_url": "https://digi.bib.uni-mannheim.de/tesseract/tesseract-ocr-w64-setup-5.4.0.20240606.exe",
        "installer_name": "tesseract_installer.exe"
    },
    "yt-dlp": {
        "name": "yt-dlp",
        "title": "yt-dlp (YouTube Media Downloader)",
        "description": "Required for Lumir's /yt YouTube video and audio downloading capability.",
        "binary_names": ["yt-dlp.exe", "yt-dlp"],
        "known_paths": [
            r"C:\Program Files\yt-dlp\yt-dlp.exe"
        ],
        "download_type": "direct_binary",
        "download_url": "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe",
        "target_filename": "yt-dlp.exe"
    }
}


def detect_capability(tool_key: str) -> Dict[str, Any]:
    """
    Checks if a capability tool is installed:
    1. First checks if present in Intra's dedicated local app directory (%LOCALAPPDATA%/IntraServer/bin).
    2. Then checks if present in System PATH or common system directories.
    Returns:
      {
         "installed": bool,
         "source": "local" | "system" | "none",
         "path": str | None,
         "can_uninstall": bool
      }
    """
    config = CAPABILITIES.get(tool_key)
    if not config:
        return {"installed": False, "source": "none", "path": None, "can_uninstall": False}

    tools_dir = get_intra_tools_dir()
    bin_names = config.get("binary_names", [])

    # 1. Local AppData Check (Can be uninstalled via Intra)
    for b in bin_names:
        local_path = os.path.join(tools_dir, b)
        if os.path.exists(local_path) and os.path.isfile(local_path):
            return {
                "installed": True,
                "source": "local",
                "path": local_path,
                "can_uninstall": True
            }

    # 2. System PATH check
    for b in bin_names:
        sys_path = shutil.which(b)
        if sys_path:
            # Check if this system path happens to be in Intra tools dir
            is_intra = os.path.abspath(sys_path).startswith(tools_dir)
            return {
                "installed": True,
                "source": "local" if is_intra else "system",
                "path": sys_path,
                "can_uninstall": is_intra
            }

    # 3. Known system locations check (Windows specific)
    known = config.get("known_paths", [])
    for p in known:
        expanded = os.path.expandvars(p)
        if os.path.exists(expanded) and os.path.isfile(expanded):
            return {
                "installed": True,
                "source": "system",
                "path": expanded,
                "can_uninstall": False
            }

    return {"installed": False, "source": "none", "path": None, "can_uninstall": False}


def uninstall_local_capability(tool_key: str) -> bool:
    """
    Uninstalls only the local Intra-managed tool files from %LOCALAPPDATA%/IntraServer/bin.
    Will NEVER touch system-installed binaries outside of Intra's directory.
    """
    config = CAPABILITIES.get(tool_key)
    if not config:
        return False

    tools_dir = get_intra_tools_dir()
    if not os.path.exists(tools_dir):
        return True

    removed = False
    bin_names = config.get("binary_names", [])
    for b in bin_names:
        p = os.path.join(tools_dir, b)
        if os.path.exists(p) and os.path.isfile(p):
            try:
                os.remove(p)
                removed = True
            except OSError:
                pass

    # If tesseract has a subfolder or tessdata in tools_dir
    if tool_key == "tesseract":
        tess_dir = os.path.join(tools_dir, "tesseract")
        if os.path.exists(tess_dir) and os.path.isdir(tess_dir):
            try:
                shutil.rmtree(tess_dir, ignore_errors=True)
                removed = True
            except Exception:
                pass

    return removed


def download_and_install_capability(
    tool_key: str,
    progress_callback: Optional[Callable[[int, str], None]] = None
) -> Dict[str, Any]:
    """
    Downloads and installs the specified capability into the Intra tools directory.
    progress_callback(percent: int, status_message: str) is called periodically.
    """
    config = CAPABILITIES.get(tool_key)
    if not config:
        return {"success": False, "message": f"Unknown capability '{tool_key}'."}

    tools_dir = ensure_tools_dir()
    download_url = config.get("download_url")
    download_type = config.get("download_type")

    def _report(pct: int, msg: str):
        if progress_callback:
            progress_callback(pct, msg)

    try:
        _report(5, f"Starting download for {config['name']}...")

        # Helper hook for urllib
        def _hook(count, block_size, total_size):
            if total_size > 0:
                percent = int(count * block_size * 100 / total_size)
                # Keep within 10% to 85% range for download
                mapped_pct = min(85, max(10, int(10 + (percent * 0.75))))
                _report(mapped_pct, f"Downloading {config['name']}... {percent}%")

        if download_type == "direct_binary":
            target_name = config.get("target_filename", f"{tool_key}.exe")
            target_path = os.path.join(tools_dir, target_name)
            temp_path = target_path + ".tmp"

            req = urllib.request.Request(
                download_url,
                headers={"User-Agent": "IntraServer-CapabilityManager/1.0"}
            )
            with urllib.request.urlopen(req) as resp, open(temp_path, "wb") as out_file:
                total_size = int(resp.headers.get("Content-Length", 0))
                downloaded = 0
                block_size = 64 * 1024
                while True:
                    chunk = resp.read(block_size)
                    if not chunk:
                        break
                    out_file.write(chunk)
                    downloaded += len(chunk)
                    if total_size > 0:
                        pct = int(downloaded * 100 / total_size)
                        mapped = min(85, max(10, int(10 + (pct * 0.75))))
                        _report(mapped, f"Downloading {config['name']}... {pct}%")

            if os.path.exists(target_path):
                os.remove(target_path)
            os.rename(temp_path, target_path)

            # Make executable on Unix-like environments if applicable
            try:
                os.chmod(target_path, 0o755)
            except Exception:
                pass

            _report(100, f"{config['name']} installed successfully! ✅")
            inject_tools_to_path()
            return {"success": True, "message": f"{config['name']} installed successfully.", "path": target_path}

        elif download_type == "zip_extract":
            zip_dest = os.path.join(tools_dir, f"{tool_key}_temp.zip")
            req = urllib.request.Request(
                download_url,
                headers={"User-Agent": "IntraServer-CapabilityManager/1.0"}
            )
            with urllib.request.urlopen(req) as resp, open(zip_dest, "wb") as out_file:
                total_size = int(resp.headers.get("Content-Length", 0))
                downloaded = 0
                block_size = 64 * 1024
                while True:
                    chunk = resp.read(block_size)
                    if not chunk:
                        break
                    out_file.write(chunk)
                    downloaded += len(chunk)
                    if total_size > 0:
                        pct = int(downloaded * 100 / total_size)
                        mapped = min(85, max(10, int(10 + (pct * 0.75))))
                        _report(mapped, f"Downloading {config['name']}... {pct}%")

            _report(88, "Extracting components...")
            with zipfile.ZipFile(zip_dest, 'r') as zip_ref:
                for member in zip_ref.namelist():
                    filename = os.path.basename(member)
                    # Check if matching required executables (e.g. ffmpeg.exe, ffprobe.exe)
                    if filename.lower() in [b.lower() for b in config.get("binary_names", [])]:
                        source = zip_ref.open(member)
                        target_file_path = os.path.join(tools_dir, filename)
                        with open(target_file_path, "wb") as target_file:
                            shutil.copyfileobj(source, target_file)
                        try:
                            os.chmod(target_file_path, 0o755)
                        except Exception:
                            pass

            if os.path.exists(zip_dest):
                os.remove(zip_dest)

            _report(100, f"{config['name']} installed and extracted! ✅")
            inject_tools_to_path()
            return {"success": True, "message": f"{config['name']} installed successfully."}

        elif download_type == "direct_installer":
            installer_name = config.get("installer_name", "installer.exe")
            installer_path = os.path.join(tools_dir, installer_name)
            req = urllib.request.Request(
                download_url,
                headers={"User-Agent": "IntraServer-CapabilityManager/1.0"}
            )
            with urllib.request.urlopen(req) as resp, open(installer_path, "wb") as out_file:
                total_size = int(resp.headers.get("Content-Length", 0))
                downloaded = 0
                block_size = 64 * 1024
                while True:
                    chunk = resp.read(block_size)
                    if not chunk:
                        break
                    out_file.write(chunk)
                    downloaded += len(chunk)
                    if total_size > 0:
                        pct = int(downloaded * 100 / total_size)
                        mapped = min(85, max(10, int(10 + (pct * 0.75))))
                        _report(mapped, f"Downloading {config['name']}... {pct}%")

            _report(90, "Running automated silent installer...")
            # Run silent install to default or tools dir if on Windows
            if sys.platform == "win32":
                import subprocess
                cmd = [installer_path, "/S"]
                subprocess.run(cmd, check=False)
                if os.path.exists(installer_path):
                    try:
                        os.remove(installer_path)
                    except Exception:
                        pass
            _report(100, f"{config['name']} setup completed! ✅")
            return {"success": True, "message": f"{config['name']} setup completed."}

        else:
            return {"success": False, "message": f"Unsupported download type: {download_type}"}

    except Exception as e:
        _report(0, f"Error: {str(e)}")
        return {"success": False, "message": str(e)}
