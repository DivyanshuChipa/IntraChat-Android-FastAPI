<div align="center">
    
 ## Intra - LAN-Based Messenger
</div>

<div align="center">

![Intra Logo](https://img.shields.io/badge/Intra-LAN%20Messenger-7A00FF?style=for-the-badge)

**A lightweight, secure, and fast local network messaging application**

[![Python](https://img.shields.io/badge/Python-3.8+-blue.svg)](https://www.python.org/downloads/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.101.0-009688.svg)](https://fastapi.tiangolo.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-5.0+-green.svg)](https://developer.android.com/)
[![PySide6](https://img.shields.io/badge/PySide6-Desktop%20Admin-green.svg)](https://doc.qt.io/qtforpython/)
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

</div>

---
<div align="center">
<img width="1079" height="664" alt="Untitled15_20260309141519" src="https://github.com/user-attachments/assets/5a29da77-4718-44f9-a14d-5e5008ce8691" />
</div>    
---

## 📱 Overview

**Intra** is a modern LAN-based messaging application designed for local network communication without internet dependency. It includes:

- Android app (Jetpack Compose)
- FastAPI backend
- Web client (`backend/static`)
- Desktop admin utility

---

## 🚀 Quick Start (Current Project Workflow)

### 1) Backend start (recommended scripts)

> Run these from repo root (`IntraChat-Android-FastAPI`).

#### Windows
```bat
Start_Server.bat
```

Background mode:
```bat
Start_Server.bat --background
```

#### Linux/macOS shell
```bash
chmod +x start_server.sh
./start_server.sh
```

These scripts automatically:
- Check for and install external tools (**FFmpeg, Tesseract OCR, Ghostscript**)
- Create `backend/venv` if missing
- Install/update dependencies from `backend/requirements.txt`
- Start `backend/run_server.py`

### 2) Linux systemd service (optional)

Install and auto-start on boot:
```bash
chmod +x setup_systemd.sh
sudo ./setup_systemd.sh install
```

Uninstall service:
```bash
sudo ./setup_systemd.sh uninstall
```

Useful checks:
```bash
sudo systemctl status intra_backend.service
sudo journalctl -u intra_backend.service -f
```

### 3) Manual backend run (without helper scripts)

```bash
cd backend
python3 -m venv venv
source venv/bin/activate   # Windows: venv\Scripts\activate
python -m pip install -r requirements.txt
python run_server.py
```

`run_server.py` supports:
- `--host`
- `--port`
- `--reload` / `--no-reload`

### 4) Connect clients

- Android and server machine should be on same Wi-Fi/LAN.
- Start backend and note displayed local IP.
- In app/client, set server as `http://<SERVER_IP>:8000`.

---

## ✨ Features

- 🔐 JWT Authentication
- 💬 Real-time messaging (WebSocket)
- 📁 File/media sharing
- 🤖 Lumir AI assistant integration
- 👥 Group chat support
- ⌨️ Typing indicators
- 💾 Offline message handling
- 🌙 Dark/Light mode
- 📱 Android + Web + Desktop tooling

---

## 🌐 Web Client

Available from backend static host:

- `http://<SERVER_IP>:8000`

---

## 🗂️ Project Structure

```
IntraChat-Android-FastAPI/
├── backend/                    # FastAPI backend + web static files
│   ├── server.py              # Main ASGI app
│   ├── run_server.py          # Starter script (IP print + uvicorn launch)
│   ├── requirements.txt
│   ├── static/                # Web client
│   └── lumir/                 # AI logic
│
├── app/                        # Android app source
├── Start_Server.bat            # Windows starter
├── start_server.sh             # Linux starter
├── setup_systemd.sh            # Linux systemd installer/uninstaller
└── GUIDE.md                    # Beginner-friendly setup guide
```

---

## 🤝 Contributing

1. Fork the repository
2. Create a branch (`git checkout -b feature/AmazingFeature`)
3. Commit (`git commit -m 'Add some AmazingFeature'`)
4. Push (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

GPLv3 License. See [LICENSE](LICENSE).

---

<div align="center">
<img width="256" height="256" alt="Untitled16" src="https://github.com/user-attachments/assets/7e4c29d0-ae2e-408a-a8b2-bed2c8c71f8e" />
</div>

---
    
<div align="center">

**Made with ❤️ for local network communication**

⭐ Star this repo if you find it useful!

</div>
