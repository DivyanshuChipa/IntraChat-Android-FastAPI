"""
Intra Server Windows Distribution Builder
Bundles backend, systemctlmanger GUI, and compiles native IntraServer.exe into dist/IntraServer/
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

    # 5. Create Standalone Windows Launcher (IntraServer.bat) for debugging
    launcher_bat = os.path.join(DIST_DIR, "IntraServer.bat")
    with open(launcher_bat, "w", encoding="utf-8") as f:
        f.write("@echo off\n")
        f.write("cd /d \"%~dp0\"\n")
        f.write("start \"\" python systemctlmanger\\server_gui_windows.py\n")

    # 6. Create VBS Silent Launcher
    launcher_vbs = os.path.join(DIST_DIR, "IntraServer.vbs")
    with open(launcher_vbs, "w", encoding="utf-8") as f:
        f.write('Set WshShell = CreateObject("WScript.Shell")\n')
        f.write('WshShell.Run "python """ & WshShell.CurrentDirectory & "\\systemctlmanger\\server_gui_windows.py""", 0, False\n')

    # 7. Compile Native Windows Launcher (IntraServer.exe) via built-in Windows csc.exe
    print("Compiling native IntraServer.exe launcher via csc.exe...")
    csc_candidates = [
        r"C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe",
        r"C:\Windows\Microsoft.NET\Framework\v4.0.30319\csc.exe",
    ]
    csc_path = next((p for p in csc_candidates if os.path.exists(p)), None)
    cs_source = os.path.join(SCRIPT_DIR, "launcher.cs")
    exe_target = os.path.join(DIST_DIR, "IntraServer.exe")

    if csc_path and os.path.exists(cs_source):
        cmd = [csc_path, "/nologo", "/target:winexe", f"/out:{exe_target}", cs_source]
        res = subprocess.run(cmd, capture_output=True, text=True)
        if res.returncode == 0:
            print("  ✅ IntraServer.exe (native 4 KB binary) compiled successfully!")
        else:
            print(f"  ⚠️ csc warning: {res.stderr}")
    else:
        print("  ⚠️ csc.exe not found. Falling back to batch launcher.")

    print("\n" + "=" * 60)
    print("✅ Distribution prepared successfully!")
    print(f"📁 Output folder: {DIST_DIR}")
    print("=" * 60)
    print("Next step: Open windows_installer/IntraServerSetup.iss in Inno Setup and click Compile!")


if __name__ == "__main__":
    main()
