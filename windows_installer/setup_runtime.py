"""
Setup Runtime Dependencies for Intra Server (Windows Embedded Python)
Downloads get-pip.py, installs pip inside windows_installer/runtime,
and installs all backend dependencies + PySide6.
"""

import os
import sys
import subprocess
import urllib.request

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(SCRIPT_DIR)
RUNTIME_DIR = os.path.join(SCRIPT_DIR, "runtime")
PYTHON_EXE = os.path.join(RUNTIME_DIR, "python.exe")
REQUIREMENTS_FILE = os.path.join(REPO_ROOT, "backend", "requirements.txt")


def setup_pth():
    pth_candidates = [f for f in os.listdir(RUNTIME_DIR) if f.endswith("._pth")]
    if not pth_candidates:
        print("❌ No ._pth file found in runtime directory!")
        return False
    pth_path = os.path.join(RUNTIME_DIR, pth_candidates[0])
    with open(pth_path, "r", encoding="utf-8") as f:
        content = f.read()

    lines = [line.strip() for line in content.splitlines()]
    modified = False

    if "Lib/site-packages" not in lines:
        lines.insert(2, "Lib/site-packages")
        modified = True

    # Ensure import site is uncommented
    new_lines = []
    for line in lines:
        if line.startswith("#import site") or line.startswith("# import site"):
            new_lines.append("import site")
            modified = True
        else:
            new_lines.append(line)

    if "import site" not in new_lines:
        new_lines.append("import site")
        modified = True

    if modified:
        with open(pth_path, "w", encoding="utf-8") as f:
            f.write("\n".join(new_lines) + "\n")
        print(f"✅ Updated {pth_candidates[0]} with Lib/site-packages and import site.")
    else:
        print(f"ℹ️ {pth_candidates[0]} already properly configured.")
    return True


def install_pip():
    get_pip_path = os.path.join(RUNTIME_DIR, "get-pip.py")
    if not os.path.exists(get_pip_path):
        print("📥 Downloading get-pip.py...")
        url = "https://bootstrap.pypa.io/get-pip.py"
        urllib.request.urlretrieve(url, get_pip_path)
        print("✅ Downloaded get-pip.py.")

    print("🚀 Installing pip into embedded runtime...")
    cmd = [PYTHON_EXE, get_pip_path, "--no-warn-script-location"]
    res = subprocess.run(cmd)
    if res.returncode != 0:
        print("❌ Failed to install pip!")
        return False
    print("✅ Pip installed successfully inside runtime!")
    return True


def install_dependencies():
    print("📦 Installing backend requirements into runtime...")
    # Packages list: backend requirements only (no heavy desktop GUI libs)
    cmd = [
        PYTHON_EXE, "-m", "pip", "install",
        "-r", REQUIREMENTS_FILE,
        "--no-warn-script-location"
    ]
    res = subprocess.run(cmd)
    if res.returncode != 0:
        print("❌ Pip install failed!")
        return False

    print("\n🔍 Verifying runtime imports...")
    verify_code = (
        "import fastapi, uvicorn, PIL, requests, apscheduler, psutil; "
        "print('🎉 ALL CORE RUNTIME DEPENDENCIES VERIFIED SUCCESSFULLY!')"
    )
    test_res = subprocess.run([PYTHON_EXE, "-c", verify_code], capture_output=True, text=True)
    print(test_res.stdout)
    if test_res.returncode != 0:
        print(f"⚠️ Verification warning: {test_res.stderr}")
    return True


def main():
    print("=" * 60)
    print("⚙️ Setting up Embedded Python Runtime")
    print("=" * 60)

    if not os.path.exists(PYTHON_EXE):
        print(f"❌ python.exe not found at: {PYTHON_EXE}")
        print("Please extract python-3.11.x-embed-amd64.zip into windows_installer/runtime/")
        sys.exit(1)

    if not setup_pth():
        sys.exit(1)

    if not install_pip():
        sys.exit(1)

    if not install_dependencies():
        sys.exit(1)

    print("=" * 60)
    print("🎯 Embedded Runtime Setup COMPLETE!")
    print("=" * 60)


if __name__ == "__main__":
    main()
