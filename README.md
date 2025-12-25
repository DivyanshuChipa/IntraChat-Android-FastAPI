# 📱 IntraConnect: Secure LAN Messenger

**IntraConnect** is a robust, real-time messaging application designed for local area networks (LAN). It allows users to communicate, share files, and view typing status without requiring an active internet connection. Perfect for offices, campuses, or secure home networks where privacy and data sovereignty are paramount.

## ✨ Key Features

* **⚡ Real-Time Messaging:** Instant communication using WebSockets.
* **📂 File Sharing:** Seamlessly upload and download files/images within chats.
* **📶 Offline-First Architecture:** Messages are queued when offline and auto-delivered when connected.
* **✍️ WhatsApp-Style Typing Indicators:** See when other users are typing in real-time.
* **🔒 Privacy Focused:** All data stays on your local network; zero data leakage to the cloud.
* **👥 Group Broadcasting:** Family/Team group support for broadcast messaging.
* **💾 Local Persistence:** Messages are stored locally on the device using Room Database.

## 🛠️ Tech Stack

### Android Client (App)
* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Modern UI)
* **Architecture:** MVVM (Model-View-ViewModel)
* **Networking:** Retrofit & OkHttp (WebSockets)
* **Database:** Room Database (SQLite abstraction)
* **Concurrency:** Kotlin Coroutines & Flow

### Backend Server
* **Language:** Python
* **Framework:** FastAPI (High performance async framework)
* **Database:** SQLite (Server-side storage)
* **Protocol:** WebSockets (for real-time events) & HTTP (for file uploads)

## 🚀 Getting Started

### Prerequisites
* Android Studio (for the client)
* Python 3.9+ (for the server)
* Both devices must be on the same WiFi/LAN network.

### 1. Server Setup
Navigate to the server directory and install dependencies:

```bash
cd lan_server
pip install fastapi uvicorn python-multipart
# Run the server
uvicorn server:app --host 0.0.0.0 --port 8000
