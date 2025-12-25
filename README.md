# Intra - LAN-Based Messenger

<div align="center">

![Intra Logo](https://img.shields.io/badge/Intra-LAN%20Messenger-7A00FF?style=for-the-badge)

**A lightweight, secure, and fast local network messaging application**

[![Python](https://img.shields.io/badge/Python-3.8+-blue.svg)](https://www.python.org/downloads/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.101.0-009688.svg)](https://fastapi.tiangolo.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-5.0+-green.svg)](https://developer.android.com/)
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
- 🎨 **Modern UI** - Beautiful Material Design 3 interface
- 🌙 **Dark Mode** - Automatic theme switching
- 📱 **Android App** - Native mobile experience

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Intra Ecosystem                      │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌──────────────┐          ┌──────────────┐            │
│  │   Android    │◄────────►│   FastAPI    │            │
│  │     App      │ WebSocket│    Server    │            │
│  │  (Kotlin)    │          │   (Python)   │            │
│  └──────────────┘          └──────────────┘            │
│         │                         │                     │
│         │                         │                     │
│    ┌────▼────┐              ┌────▼────┐                │
│    │  Room   │              │ SQLite  │                │
│    │   DB    │              │   DBs   │                │
│    └─────────┘              └─────────┘                │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 🚀 Quick Start

### Prerequisites

**Server Requirements:**
- Python 3.8 or higher
- pip (Python package manager)
- Ubuntu/Debian Linux (or any Linux distro)

**Android App Requirements:**
- Android Studio (Latest version)
- Android SDK 21+ (Android 5.0 Lollipop or higher)
- Kotlin plugin

---

## 🖥️ Server Setup

### 1️⃣ Clone the Repository

```bash
git clone https://github.com/DivyanshuChipa/IntraChat-Android-FastAPI.git
```
### IMPORTANT THING: BACKEND NOT UPLOADED ON GIT HUB RIGHT NOW

### 2️⃣ Create Virtual Environment

```bash
python3 -m venv intra_env
source intra_env/bin/activate  # On Windows: intra_env\Scripts\activate
```

### 3️⃣ Install Dependencies

```bash
pip install -r requirements.txt
```

**requirements.txt:**
```txt
fastapi==0.101.0
uvicorn[standard]==0.22.0
python-multipart==0.0.6
aiofiles==23.2.1
passlib==1.7.4
python-jose[cryptography]==3.3.0
```

### 4️⃣ Configure Server

Edit `server.py` and change the secret key:

```python
SECRET_KEY = "YOUR_SUPER_SECRET_KEY_HERE_MAKE_IT_LONG_AND_RANDOM"
```

### 5️⃣ Run the Server

**Development Mode:**
```bash
uvicorn server:app --host 0.0.0.0 --port 8000 --reload
```

**Production Mode (with systemd):**

Create service file:
```bash
sudo nano /etc/systemd/system/lanserver.service
```

Paste this configuration:
```ini
[Unit]
Description=LAN FastAPI Server

[Service]
User=YOUR_USERNAME
WorkingDirectory=/path/to/lan_server
ExecStart=/path/to/intra_env/bin/uvicorn server:app --host 0.0.0.0 --port 8000
Restart=always

[Install]
WantedBy=multi-user.target
```

Enable and start:
```bash
sudo systemctl daemon-reload
sudo systemctl enable lanserver
sudo systemctl start lanserver
sudo systemctl status lanserver
```

### 6️⃣ Find Your Server IP

```bash
ip addr show | grep "inet "
# Or
hostname -I
```

Note down your local IP (e.g., `192.168.1.100`)

---

## 📱 Android App Setup

### 1️⃣ Open in Android Studio

1. Open Android Studio
2. Select **"Open an Existing Project"**
3. Navigate to `intra/android_app` folder
4. Click **OK**

### 2️⃣ Configure Server IP

Open `ApiClient.kt` and update the IP:

```kotlin
object ApiClient {
    private const val BASE_URL = "http://YOUR_SERVER_IP:8000/"
    // Example: "http://192.168.1.100:8000/"
}
```

### 3️⃣ Sync Gradle

Click **"Sync Now"** when prompted to download dependencies.

### 4️⃣ Build & Run

1. Connect your Android device via USB (with USB Debugging enabled)
   - **OR** use an Android Emulator
2. Click the **Run** button (▶️) in Android Studio
3. Select your device/emulator
4. Wait for the app to install and launch

---

## 📖 Usage Guide

### First Time Setup

1. **Start the Server** on your local network
2. **Launch the App** on your Android device
3. **Register** a new account with username and password
4. **Login** with your credentials

### Chatting

- **One-on-One Chat:** Tap any user from the contact list
- **Group Chat:** Tap "Family Group" to broadcast messages
- **Send Files:** Use the 📎 attachment icon in chat
- **Typing Indicator:** Start typing to let others know

### Features

✅ **Real-time messaging** with instant delivery  
✅ **File sharing** (images, documents, videos, etc.)  
✅ **Offline message queue** - messages delivered when user comes online  
✅ **Typing indicators** - see who's typing in real-time  
✅ **Message persistence** - chat history saved locally  
✅ **Dark mode** - automatic theme based on system settings

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
│   ├── calls.py               # Voice/video calls (future)
│   ├── requirements.txt       # Python dependencies
│   ├── chat_users.db          # User database (auto-created)
│   ├── chat_messages.db       # Messages database (auto-created)
│   └── uploads/               # File storage folder (auto-created)
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

## 🔧 API Endpoints

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/register` | Register new user |
| POST | `/login` | Login and get JWT token |

### Chat

| Method | Endpoint | Description |
|--------|----------|-------------|
| WebSocket | `/ws/{username}` | Real-time messaging |
| GET | `/messages` | Get recent messages |
| GET | `/users` | Get all registered users |

### Files

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/upload` | Upload a file |
| GET | `/uploads/{filename}` | Download/view file |

---

## 🛠️ Technologies Used

### Backend
- **FastAPI** - Modern, fast web framework
- **Uvicorn** - ASGI server
- **WebSockets** - Real-time communication
- **SQLite** - Lightweight database
- **JWT** - Secure authentication
- **Passlib** - Password hashing

### Frontend
- **Kotlin** - Modern Android development
- **Jetpack Compose** - Declarative UI
- **Room Database** - Local data persistence
- **Retrofit** - HTTP client
- **OkHttp** - WebSocket client
- **Coroutines** - Asynchronous programming
- **Material Design 3** - Modern UI components

---

## 🔒 Security Features

- 🔐 Password hashing with **PBKDF2-SHA256**
- 🎫 JWT token-based authentication
- 🛡️ CORS protection
- 📝 Input sanitization for file uploads
- 🔒 Secure WebSocket connections

---

## 🐛 Troubleshooting

### Server Issues

**Problem:** Server won't start
```bash
# Check if port 8000 is already in use
sudo lsof -i :8000

# Kill the process if needed
sudo kill -9 <PID>
```

**Problem:** Can't connect from Android app
- ✅ Ensure both devices are on the **same Wi-Fi network**
- ✅ Check firewall settings on server
- ✅ Verify server IP address is correct in `ApiClient.kt`

### Android App Issues

**Problem:** Build errors in Android Studio
```bash
# Clean and rebuild
./gradlew clean
./gradlew build
```

**Problem:** App crashes on launch
- ✅ Check `ApiClient.kt` for correct server IP
- ✅ Ensure server is running and reachable
- ✅ Check Logcat for detailed error messages

---

## 🗺️ Roadmap

- [ ] 📞 Voice/Video calling support
- [ ] 🔍 Message search functionality
- [ ] 🖼️ Image preview in chat
- [ ] 📌 Message pinning
- [ ] 🔔 Push notifications (local)
- [ ] 💾 Message backup/export
- [ ] 🎨 Custom themes
- [ ] 🌐 Web client (React/Vue)

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

**Your Name**
- GitHub: [@yourusername](https://github.com/yourusername)
- Email: your.email@example.com

---

## 🙏 Acknowledgments

- FastAPI team for the amazing framework
- Jetpack Compose for modern Android UI
- All contributors and users of this project

---

## 📞 Support

If you encounter any issues or have questions:

1. Check the [Troubleshooting](#-troubleshooting) section
2. Open an [Issue](https://github.com/yourusername/intra/issues)
3. Reach out via email

---

<div align="center">

**Made with ❤️ for local network communication**

⭐ Star this repo if you find it useful!

</div>
