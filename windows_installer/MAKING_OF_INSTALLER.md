# 🛠️ The Making of Intra Server Windows Installer

A complete engineering log and technical breakdown of how the **Intra Server Desktop & Windows Installer** is designed, built, and packaged.

---

## 🎯 The Core Philosophy: "No-Friction Windows Deployment"

When building Python server software for Windows, the traditional developer path (*"install Python, create venv, run pip, run terminal"*) leads to 99% user drop-off. 

The goal of this installer is to provide a **commercial-grade, 1-click Windows experience**:
- ✅ Zero technical knowledge required from the user.
- ✅ No ugly black command-prompt window popping up.
- ✅ Automatic Windows Firewall configuration for LAN access.
- ✅ Seamless minimization to the Windows System Tray.
- ✅ Safe separation of user databases from application code.

---

## 🏗️ Architecture Overview

```
                      [ IntraServerSetup.exe (Inno Setup ~6MB) ]
                                         │
                                  Installs into
                                         ▼
                     C:\Program Files\Intra Server\
                     ├── IntraServer.exe  (4 KB native C# launcher)
                     ├── backend\         (FastAPI server & Lumir AI)
                     ├── systemctlmanger\ (PySide6 GUI & Addons)
                     └── runtime\         (Embedded Python - optional/isolated)

                                         │
                         Reads / Writes user data from
                                         ▼
                 %LOCALAPPDATA%\IntraServer\  (Safe User Space)
                 ├── data\chat_users.db
                 ├── uploads\ (Profiles, WebDrop, Media)
                 └── bin\     (FFmpeg, Tesseract, yt-dlp)
```

---

## ⚙️ Key Technical Innovations

### 1. The 4 KB Native C# Launcher (`launcher.cs`)
Instead of using heavy packers like **PyInstaller**:
- **Why NOT PyInstaller?**
  - PyInstaller packs everything into a virtual archive. Every double-click takes **3–7 seconds** to unpack temporary DLLs into `AppData\Local\Temp\_MEIxxxx\`.
  - Windows Defender frequently flags PyInstaller binaries as false-positive malware.
- **The C# Solution:**
  - Windows has its own native C# compiler (`csc.exe`) built right into every modern Windows installation (`C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe`).
  - `windows_installer/launcher.cs` compiles into a true **4 KB Windows PE executable (`IntraServer.exe`)**.
  - It uses `CreateNoWindow = true` so the launcher is **100% silent** (zero black CMD window flash) and launches in **10 milliseconds**!
  - It prioritizes `runtime\python.exe` (embedded), then `backend\venv`, then system `python.exe`.

### 2. Automated Windows Firewall Configuration
Connecting an Android phone to a PC on a local Wi-Fi router usually fails because Windows Firewall silently blocks incoming connections on port 8000.
- In `IntraServerSetup.iss`, Inno Setup executes an automated `netsh` rule during installation:
  ```cmd
  netsh advfirewall firewall add rule name="IntraServer" dir=in action=allow protocol=TCP localport=8000
  ```
- Upon uninstall, Inno Setup cleanly cleans up the firewall rule:
  ```cmd
  netsh advfirewall firewall delete rule name="IntraServer"
  ```
- **Result:** Android devices connect to `http://<PC_IP>:8000` on the very first try with zero firewall errors!

### 3. System Tray Background Mode (`server_gui_windows.py`)
- Standard desktop windows kill background server sockets when the user presses **[X]**.
- `server_gui_windows.py` overrides `closeEvent` and hooks into **`QSystemTrayIcon`**.
- Closing the window simply hides it into the Windows notification tray area and displays a brief balloon tip: *"Server is running in background"*.
- Right-clicking the tray icon gives quick controls: **Show Dashboard**, **Copy Android URL**, or **Stop Server & Exit**.

### 4. Code vs. Data Separation (`%LOCALAPPDATA%`)
- Windows prevents non-admin programs from writing into `C:\Program Files\`.
- If user databases live in `Program Files`, updates or reinstalls wipe out user chats and passwords!
- **Our Fix in `backend/users.py`:**
  - Database resolves to `%LOCALAPPDATA%\IntraServer\data\chat_users.db`.
  - Tools (FFmpeg, Tesseract, yt-dlp) resolve to `%LOCALAPPDATA%\IntraServer\bin\`.
  - When the app is updated, user data is **never touched or lost**.

---

## 🚀 How to Build the Installer from Scratch

1. **Build Distribution:**
   ```powershell
   python windows_installer/build_windows_dist.py
   ```
   *This copies backend/GUI files and compiles `IntraServer.exe` via `csc.exe`.*

2. **Compile Installer:**
   - Open `windows_installer/IntraServerSetup.iss` in [Inno Setup Compiler](https://jrsoftware.org/isdl.php).
   - Press **Ctrl + F9** (or Build ➔ Compile).
   - Output installer will be generated in `windows_installer/Output/IntraServerSetup.exe`.
