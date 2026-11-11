# 🪟 Intra Server — Windows Distribution & Installer

This folder contains the build and packaging tools for producing a zero-install, single-click Windows package for the Intra backend and desktop GUI.

---

## 📁 Contents

- **`build_windows_dist.py`**: Automated script that downloads the official embedded Python 3.11 runtime, installs the required wheels (`FastAPI`, `Uvicorn`, `PySide6`, `passlib`, etc.), bundles `backend/` and `systemctlmanger/`, and outputs a ready-to-run folder in `dist/IntraServer/`.
- **`IntraServerSetup.iss`**: Inno Setup script that packages `dist/IntraServer/` into an official `IntraServerSetup.exe` installer.
  - Automatically creates Desktop and Start Menu shortcuts.
  - Automatically adds the inbound Windows Firewall rule for Port 8000 (`netsh advfirewall firewall add rule name="IntraServer" dir=in action=allow protocol=TCP localport=8000`).
  - Cleans up firewall rules upon uninstallation.

---

## 🚀 How to Build the Windows Installer

### Step 1: Build the Standalone Distribution
Run:
```cmd
python windows_installer/build_windows_dist.py
```
This prepares `dist/IntraServer/` containing the standalone executable and embedded runtime.

### Step 2: Compile the Installer
1. Install [Inno Setup 6](https://jrsoftware.org/isdl.php) (free).
2. Right-click `windows_installer/IntraServerSetup.iss` and choose **"Compile"** (or run `iscc windows_installer/IntraServerSetup.iss`).
3. Output installer will be in `windows_installer/Output/IntraServerSetup.exe`.
