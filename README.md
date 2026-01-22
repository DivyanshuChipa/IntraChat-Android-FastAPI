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
[![JavaScript](https://img.shields.io/badge/JavaScript-ES6+-yellow.svg)](https://developer.mozilla.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

## 📱 Overview

**Intra** is a modern LAN-based messaging application designed for local network communication without internet dependency. Perfect for homes, offices, and educational institutions where devices are on the same network.

### ✨ Key Features

- 🔐 **Secure Authentication** - JWT-based login/registration system
- 💬 **Real-time Messaging** - WebSocket-powered instant communication
- 📁 **File Sharing** - Share documents, images, and media files
- 👥 **Group Chat** - Broadcast messages to all users via "Family Group"
- ⌨️ **Typing Indicators** - See when others are typing
- 💾 **Offline Messages** - Receive messages when you reconnect
- 🎨 **Modern UI** - Beautiful Material Design 3 interface (Android) & Clean Web UI
- 🌙 **Dark/Light Mode** - Theme switching on both platforms
- 📱 **Multi-Platform** - Android App + Web Client + Desktop Access
- 🖼️ **Profile Photos** - Upload and display custom profile pictures

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Intra Ecosystem                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │   Android    │    │  Web Client  │    │   Desktop    │  │
│  │     App      │◄───┤  (Browser)   │◄───┤   Browser    │  │
│  │  (Kotlin)    │    │ (HTML/CSS/JS)│    │  (Any OS)    │  │
│  └──────────────┘    └──────────────┘    └──────────────┘  │
│         │                    │                    │         │
│         └────────────────────┼────────────────────┘         │
│                              │                              │
│                         WebSocket                           │
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

## 🚀 Quick Start

### Prerequisites

**Server Requirements:**
- Python 3.8 or higher
- pip (Python package manager)
- Ubuntu/Debian Linux (or any Linux distro)

**Client Requirements:**
- **Android App:** Android SDK 21+ (Android 5.0+)
- **Web Client:** Any modern browser (Chrome, Firefox, Safari, Edge)

---

## 🖥️ Server Setup

### 1️⃣ Install Dependencies

```bash
cd lan_server
pip install -r requirements.txt
```

### 2️⃣ Start Server

**Option A: Development Mode**
```bash
uvicorn server:app --host 0.0.0.0 --port 8000 --reload
```

**Option B: Production Mode (Systemd Service)**
```bash
# Create systemd service file
sudo nano /etc/systemd/system/lanserver.service

# Paste this content:
[Unit]
Description=LAN FastAPI Server

[Service]
User=YOUR_USERNAME
WorkingDirectory=/path/to/lan_server
ExecStart=/path/to/python -m uvicorn server:app --host 0.0.0.0 --port 8000
Restart=always

[Install]
WantedBy=multi-user.target

# Enable and start service
sudo systemctl daemon-reload
sudo systemctl enable lanserver
sudo systemctl start lanserver
```

### 3️⃣ Find Your Server IP

```bash
# On Linux
hostname -I | awk '{print $1}'

# Or
ip addr show | grep "inet " | grep -v 127.0.0.1
```

Note down your local IP (e.g., `192.168.1.100`)

---

## 🌐 Web Client Usage

### Access the Web App

1. **Start the server** (see above)
2. Open any browser on a device connected to the same network
3. Navigate to: `http://YOUR_SERVER_IP:8000`
4. **Register/Login** and start chatting!

### Features Available in Web Client

✅ **Register/Login** with username and password  
✅ **Real-time messaging** via WebSocket  
✅ **File sharing** with drag-and-drop support  
✅ **Profile photo upload**  
✅ **Dark/Light theme toggle**  
✅ **Typing indicators**  
✅ **Message history** loaded from server  
✅ **Group chat** via Family Group  
✅ **Responsive design** for mobile and desktop  

### Browser Compatibility

| Browser | Supported | Notes |
|---------|-----------|-------|
| Chrome | ✅ | Recommended |
| Firefox | ✅ | Fully supported |
| Safari | ✅ | MacOS/iOS |
| Edge | ✅ | Windows 10+ |
| Opera | ✅ | All features work |

---

## 📱 Android App Setup

### 1️⃣ Open in Android Studio

1. Open Android Studio
2. Select **"Open an Existing Project"**
3. Navigate to `intra/android_app` folder
4. Click **OK**

### 2️⃣ Configure Server IP

The app now has **Advanced Settings** in the login screen!

**Method 1: In-App Settings (Recommended)**
- Open the app
- On login screen, tap **"Advanced Options (IP/Port)"**
- Enter your server IP and port
- These settings are saved automatically

**Method 2: Hardcode (Optional)**
```kotlin
// In ApiClient.kt
private const val DEFAULT_IP = "192.168.1.100"
private const val DEFAULT_PORT = "8000"
```

### 3️⃣ Build & Run

1. Connect your Android device via USB (with USB Debugging enabled)
   - **OR** use an Android Emulator
2. Click the **Run** button (▶️) in Android Studio
3. Select your device/emulator
4. Wait for the app to install and launch

---

## 📖 Usage Guide

### First Time Setup

**Web Client:**
1. Open browser and go to `http://YOUR_SERVER_IP:8000`
2. Click **Register** and create an account
3. **Login** with your credentials
4. Start chatting!

**Android App:**
1. Install the app on your Android device
2. Open app and **Register** a new account
3. **Login** with your credentials
4. Grant necessary permissions (storage for file sharing)

### Chatting

- **One-on-One Chat:** 
  - Web: Click any user from the left sidebar
  - Android: Tap any user from the contact list
  
- **Group Chat:** 
  - Click/Tap "Family Group" to broadcast messages to all users

- **Send Files:** 
  - Web: Click 📎 icon or drag & drop files
  - Android: Tap 📎 attachment icon

- **Profile Photo:**
  - Web: Click ⚙️ settings, then "Change Photo"
  - Android: Open settings, tap profile photo

- **Theme Toggle:**
  - Web: Click 🌙 icon in top-right
  - Android: System theme auto-detected

### Cross-Platform Sync

✅ **Messages sync** across all devices in real-time  
✅ **File uploads** accessible from web and mobile  
✅ **Profile photos** visible on all platforms  
✅ **Typing indicators** work cross-platform  
✅ **Offline messages** delivered when any device reconnects

---

## 🗂️ Project Structure

```
intra/
├── lan_server/                 # Backend (FastAPI)
│   ├── server.py              # Main application
│   ├── chat.py                # WebSocket chat logic
│   ├── messages.py            # Message database operations
│   ├── users.py               # User authentication
│   ├── files.py               # File upload/download
│   ├── profiles.py            # Profile photo management
│   ├── calls.py               # Voice/video calls (future)
│   ├── requirements.txt       # Python dependencies
│   ├── chat_users.db          # User database (auto-created)
│   ├── chat_messages.db       # Messages database (auto-created)
│   ├── uploads/               # File storage folder (auto-created)
│   │   └── profiles/          # Profile photos (auto-created)
│   └── static/                # Web Client Files
│       ├── index.html         # Login/Register page
│       ├── chat.html          # Main chat interface
│       ├── app.js             # JavaScript logic (WebSocket, API calls)
│       └── style.css          # Styling (Dark/Light themes)
│
└── android_app/                # Frontend (Kotlin + Jetpack Compose)
    ├── app/src/main/
    │   ├── java/com/example/intra/
    │   │   ├── MainActivity.kt
    │   │   ├── AuthScreen.kt
    │   │   ├── AuthViewModel.kt
    │   │   ├── ContactListScreen.kt
    │   │   ├── ContactViewModel.kt
    │   │   ├── ChatScreen.kt
    │   │   ├── ChatViewModel.kt
    │   │   ├── SettingsScreen.kt
    │   │   ├── WsManager.kt
    │   │   ├── ApiClient.kt
    │   │   ├── ApiService.kt
    │   │   ├── SettingsManager.kt
    │   │   ├── MyApplication.kt
    │   │   └── database/
    │   │       ├── ChatDatabase.kt
    │   │       ├── ChatDao.kt
    │   │       └── ChatMessageEntity.kt
    │   └── res/
    └── build.gradle.kts
```

---



## 🗺️ Roadmap

### Upcoming Features
- [ ] 📞 Voice/Video calling support (WebRTC)
- [ ] 🔍 Message search functionality
- [ ] 🖼️ Image preview/gallery in chat
- [ ] 📌 Message pinning
- [ ] 🔔 Push notifications (Android)
- [ ] 💾 Message backup/export (JSON/CSV)
- [ ] 🎨 Custom theme colors
- [ ] 📊 File upload progress bars
- [ ] 🔐 End-to-end encryption
- [ ] 👀 Read receipts
- [ ] ⏰ Message scheduling

### Platform Expansions
- [ ] 🍎 iOS App (Swift/SwiftUI)
- [ ] 🖥️ Desktop App (Electron)
- [ ] 🐧 Linux Native App
- [ ] 🪟 Windows Native App

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

### Development Guidelines

- Follow existing code style (PEP 8 for Python, Kotlin conventions)
- Add comments for complex logic
- Test on both web and Android before submitting PR
- Update README if adding new features

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 👨‍💻 Author

**Divyanshu Chipa**
- GitHub: [@DivyanshuChipa](https://github.com/DivyanshuChipa)
- Email: Divyanshu6062015@gmail.com

---

## 🙏 Acknowledgments

- FastAPI team for the amazing framework
- Jetpack Compose for modern Android UI
- WebSocket protocol for real-time communication
- SQLite for reliable local storage
- All contributors and users of this project

---

## 📞 Support

If you encounter any issues or have questions:

1. Check the [Troubleshooting](#-troubleshooting) section
2. Review server logs: `journalctl -u lanserver -f` (if using systemd)
3. Check browser console (F12) for web client errors
4. Open an [Issue](https://github.com/DivyanshuChipa/IntraChat-Android-FastAPI/issues)
5. Reach out via email

---

## 📸 Screenshots

### Web Client
```
┌─────────────────────────────────────────────┐
│  Login Page (Dark/Light Theme)              │
│  • Clean Material Design                    │
│  • Password visibility toggle               │
│  • Register/Login switch                    │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│  Chat Interface                             │
│  • User list with profile photos            │
│  • Real-time messaging                      │
│  • File sharing with previews               │
│  • Typing indicators                        │
│  • Settings panel with photo upload         │
└─────────────────────────────────────────────┘
```

### Android App
```
┌─────────────────────────────────────────────┐
│  Login Screen (Material You)                │
│  • Advanced IP/Port settings                │
│  • Password visibility toggle               │
│  • Gradient background                      │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│  Contact List                               │
│  • Profile photos (circular avatars)        │
│  • Typing indicators                        │
│  • Family Group broadcast                   │
│  • Settings access                          │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│  Chat Screen                                │
│  • Message bubbles (sent/received)          │
│  • File attachments                         │
│  • Typing indicator animations              │
│  • Auto-scroll on new messages              │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│  Settings Screen                            │
│  • Collapsible sections                     │
│  • Profile photo upload                     │
│  • Server IP/Port configuration             │
│  • Logout & Delete Account options          │
└─────────────────────────────────────────────┘
```

---

<div align="center">

**Made with ❤️ for local network communication**

⭐ Star this repo if you find it useful!

🌐 **[View Live Demo](#)** | 📱 **[Download APK](#)** | 📖 **[Documentation](#)**

</div>

---

## 🎯 Use Cases

### 🏠 Home Network
- Family chat without internet bills
- Share photos and videos locally
- Privacy-focused communication

### 🏢 Office/Enterprise
- Internal team collaboration
- Secure file sharing within organization
- No cloud dependencies

### 🎓 Educational Institutions
- Student-teacher communication
- Lab/classroom messaging
- Offline assignment submission

### 🏥 Hospitals/Clinics
- HIPAA-compliant local messaging
- No PHI leaving the network
- Quick staff communication

---

<div align="center">

![Made with Love](https://img.shields.io/badge/Made%20with-❤️-red?style=for-the-badge)
![Powered by FastAPI](https://img.shields.io/badge/Powered%20by-FastAPI-009688?style=for-the-badge)
![Built with Kotlin](https://img.shields.io/badge/Built%20with-Kotlin-7F52FF?style=for-the-badge)

</div>
