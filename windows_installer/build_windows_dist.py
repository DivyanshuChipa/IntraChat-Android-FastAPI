"""
Intra Server Windows Distribution Builder
Bundles backend, systemctlmanger GUI, and runner script into dist/IntraServer/
"""

import os
import sys
import shutil
import subprocess

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(SCRIPT_DIR)
DIST_DIR = os.path.join(REPO_ROOT, "dist", "IntraServer")


def main():
    print("=" * 60)
    print("🚀 Building Intra Server Windows Distribution")
    print("=" * 60)

    # 1. Clean previous dist
    if os.path.exists(DIST_DIR):
        print("Cleaning previous dist directory...")
        shutil.rmtree(DIST_DIR)
    os.makedirs(DIST_DIR, exist_ok=True)

    # 2. Copy Backend
    print("Copying backend...")
    backend_dest = os.path.join(DIST_DIR, "backend")
    shutil.copytree(
        os.path.join(REPO_ROOT, "backend"),
        backend_dest,
        ignore=shutil.ignore_patterns("venv", ".venv", "__pycache__", "*.pyc", "chat_users.db")
    )

    # 3. Copy systemctlmanger (GUI + Capabilities)
    print("Copying systemctlmanger...")
    sys_dest = os.path.join(DIST_DIR, "systemctlmanger")
    shutil.copytree(
        os.path.join(REPO_ROOT, "systemctlmanger"),
        sys_dest,
        ignore=shutil.ignore_patterns("__pycache__", "*.pyc")
    )

    # 4. Copy icons if present
    for icon_name in ["icon.ico", "icon.png"]:
        src_icon = os.path.join(REPO_ROOT, icon_name)
        if os.path.exists(src_icon):
            shutil.copy(src_icon, DIST_DIR)

    # 5. Create Standalone Windows Launcher (IntraServer.bat)
    launcher_bat = os.path.join(DIST_DIR, "IntraServer.bat")
    with open(launcher_bat, "w", encoding="utf-8") as f:
        f.write("@echo off\n")
        f.write("cd /d \"%~dp0\"\n")
        f.write("start \"\" python systemctlmanger\\server_gui_windows.py\n")

    # 6. Create VBS Silent Launcher (No black CMD window flash!)
    launcher_vbs = os.path.join(DIST_DIR, "IntraServer.vbs")
    with open(launcher_vbs, "w", encoding="utf-8") as f:
        f.write('Set WshShell = CreateObject("WScript.Shell")\n')
        f.write('WshShell.Run "python """ & WshShell.CurrentDirectory & "\\systemctlmanger\\server_gui_windows.py""", 0, False\n')

    print("\n✅ Distribution prepared successfully!")
    print(f"📁 Output folder: {DIST_DIR}")
    print("Run with: python systemctlmanger/server_gui_windows.py or IntraServer.bat")


if __name__ == "__main__":
    main()
