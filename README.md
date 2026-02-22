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
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

## 📱 Overview

**Intra** is a modern LAN-based messaging application designed for local network communication without internet dependency. It features a robust Android app, a web client, and a dedicated desktop administration tool.

### ✨ Key Features

- 🔐 **Secure Authentication** - JWT-based login/registration system
- 💬 **Real-time Messaging** - WebSocket-powered instant communication
- 📁 **File Sharing** - Share documents, images, and media files
- 🤖 **Lumir AI Assistant** - Integrated AI for chat and utility tasks (Passport Layouts)
- 👥 **Group Chat** - Broadcast messages to all users via "Family Group"
- ⌨️ **Typing Indicators** - See when others are typing
- 💾 **Offline Messages** - Receive messages when you reconnect
- 🎨 **Modern UI** - Beautiful Material Design 3 interface (Android) & Clean Web UI
- 🌙 **Dark/Light Mode** - Theme switching on all platforms
- 📱 **Multi-Platform** - Android App + Web Client + Desktop Admin Tool
- 🖼️ **Advanced Media** - Custom Video Player (Gestures) & Image Viewer (Zoom)
- 🔗 **Share Intent** - Share files/text directly from other Android apps to Intra

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Intra Ecosystem                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐   │
│  │   Android    │    │  Web Client  │    │ Desktop Admin│   │
│  │     App      │◄───┤  (Browser)   │◄───┤     Tool     │   │
│  │  (Kotlin)    │    │ (HTML/CSS/JS)│    │  (PySide6)   │   │
│  └──────────────┘    └──────────────┘    └──────────────┘   │
│         │                    │                    │         │
│         └────────────────────┼────────────────────┘         │
│                              │                              │
│                         WebSocket/HTTP                      │
│                              │                              │
│                    ┌─────────▼─────────┐                    │
│                    │   FastAPI Server  │                    │
│                    │     (Python)      │                    │
│                    └─────────┬─────────┘                    │
│                              │                              │
│                    ┌─────────▼─────────┐                    │
│                    │   SQLite DBs      │                    │
│                    │ • Users           │                    │
│                    │ • Messages        │                    │
│                    │ • Files/Photos    │                    │
│                    └───────────────────┘                    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 🚀 Quick Start & Installation

### 1️⃣ Server Setup (Required)

The backend server is written in Python and must run on a machine within your LAN.

**Requirements:**
- Python 3.8 or higher
- pip (Python package manager)

**Installation:**

```bash
cd backend
pip install -r requirements.txt
```

**Start Server:**

```bash
# Development Mode
uvicorn server:app --host 0.0.0.0 --port 8000 --reload
```

### 2️⃣ Android App

**Download the latest APK from Releases:**
[![Download APK](https://img.shields.io/badge/Download-APK-3DDC84?style=for-the-badge&logo=android)](https://github.com/DivyanshuChipa/IntraChat-Android-FastAPI/releases)

**Features:**
- **Share Intent:** Share files from Gallery/File Manager directly to Intra.
- **Custom Video Player:** Brightness/Volume gestures, Resize modes.
- **Image Viewer:** Pinch-to-zoom, Pan, Swipe-to-dismiss.
- **Background Service:** Reliable message delivery.

### 3️⃣ Desktop Admin Panel (Beta)

A powerful desktop tool to manage your Intra server. Available as a standalone executable (no Python required for end users).

**Download:**
- Check the **Releases** section for the `IntraAdmin.exe` (Windows) or binary.

**Features:**
- **User Management:** Approve/Block users, Reset Passwords.
- **AI Configuration:** Enable/Disable Lumir, Set Ollama URL & Model.
- **System Settings:** Toggle "Require Admin Approval" for new users.
- **Database Cleanup:** Remove old messages.

---

## 🤖 Lumir AI Assistant

**Lumir** is Intra's built-in AI assistant, powered by local LLMs (Ollama) and custom logic.

**Capabilities:**
- **Chat:** Natural language conversation (requires Ollama running).
- **Passport Photo Layout:**
    - Send an image to Lumir.
    - Reply `###passport###` for a 6-on-A6 layout.
    - Reply `###passport9###` for a 9-on-A6 layout.
    - Add date: `###passportdate<dd/mm/yyyy>###`.

---

## 🌐 Web Client

Accessible at `http://YOUR_SERVER_IP:8000`

- **Native Video Player:** Now supports playback without external dependencies.
- **Cross-Platform:** Works on any modern browser.

---

## 🗂️ Project Structure

```
intra/
├── backend/                    # Backend (FastAPI)
│   ├── server.py              # Main application
│   ├── lumir/                 # AI Assistant Logic
│   │   ├── engine.py          # Command processing
│   │   └── ai_engine.py       # Ollama Integration
│   ├── chat.py                # WebSocket chat logic
│   ├── static/                # Web Client (HTML/JS/CSS)
│   └── ...
│
├── intra_admin/                # Desktop Admin Tool
│   ├── admin.py               # PySide6 GUI Application
│   └── ...
│
└── app/                        # Android App (Kotlin + Jetpack Compose)
    ├── src/main/java/com/example/intra/
    │   ├── MainActivity.kt    # Main Entry & Share Intent Handling
    │   ├── VideoPlayer.kt     # Custom Video Player
    │   ├── ImageViewer.kt     # Custom Image Viewer
    │   └── ...
```

---

## 🗺️ Roadmap

### Upcoming Features
- [ ] 📄 **OCR & PDF Tools:** Extract text from images, merge PDFs via Lumir.
- [ ] 🏢 **Office Utilities:** Integration of essential office tools.
- [ ] 🧠 **Context-Aware AI:** Lumir to understand chat history for better responses.
- [ ] 📞 **Video Calling:** (Planned) WebRTC-based video calls.
- [ ] 🔍 **Message Search:** Full-text search for message history.

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 👨‍💻 Author

**Divyanshu Chipa**
- GitHub: [@DivyanshuChipa](https://github.com/DivyanshuChipa)
- Email: Divyanshu6062015@gmail.com

---

<div align="center">

**Made with ❤️ for local network communication**

⭐ Star this repo if you find it useful!

</div>
