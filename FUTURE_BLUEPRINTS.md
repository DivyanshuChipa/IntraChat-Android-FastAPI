# Future UI/UX Ideas & Blueprints

This document serves as a repository for visionary ideas and future feature plans for the application.

## ContactsScreen Top Bar Redesign (The "Hub" Concept) [COMPLETED]

**Concept:**
Replace the traditional and boring 3-dot settings menu in the `ContactsScreen` top bar with a highly interactive, futuristic "Hub" or "Grid" (Dice-like) icon. (Successfully implemented with slide/fade animations, Settings, SMB Network Browser, and Smart Home links).

## SMB Network Browser [COMPLETED]

**Concept:**
Integrate local network storage (Samba/SMB) sharing directly inside the app, allowing users to scan local IPs, connect (guest/registered), navigate folders, create directories, upload, and download files directly from/to their devices. (Successfully implemented using `jcifs-ng` with network scanning, FAB menu options, image/video local preview dialogs, and cache management).
- **Scalability:** It leaves room for integrating powerful features like IoT and Local Network management directly from the main hub of the app.

## Lumir Addon System (Backend/Bot Utilities)

**Concept:**
Instead of hardcoding every utility into Lumir, introduce a modular "Addon" system. Users/Admins can enable specific modules to give Lumir new capabilities. This keeps the core lightweight and adds immense value.

**Top Priority Addon Ideas:**

### 1. YouTube Downloader Addon (`/yt`) [COMPLETED]
- **How it works:** A user sends a YouTube link with a command like `/yt [link]`.
- **Backend Logic:** Lumir triggers an addon utilizing tools like `yt-dlp` to download the video or extract the audio.
- **Delivery:** Once downloaded, Lumir sends the file directly in the chat or saves it in the custom local download folder. (Successfully implemented and integrated on the backend!)
- **Why it's great:** Highly requested utility, saves users from visiting ad-ridden downloader websites.

### 2. Personal Financial Tracker Addon (`/money`)
- **How it works:** Users log expenses or income via natural language or simple commands. E.g., `/money -150 tea and snacks` or `/money +5000 freelance work`.
- **Backend Logic:** Lumir parses the amount and category. It saves this data into an isolated SQLite table (or exports to CSV).
- **Reporting:** Sending `/money report` triggers Lumir to summarize the month's spending, perhaps even generating a small chart or a neat tabular summary in the chat.
- **Future Expansion:** Smart OCR where users send a photo of a receipt, and Lumir automatically extracts the total and asks, "Add $15 to expenses?"

### 3. Tier 2 Memory Summary System (ChromaDB Optimization)
- **Current State:** Lumir uses ChromaDB to recall past facts.
- **The Problem:** Over time, exact chat logs get messy and context limits are hit.
- **The Upgrade:** Introduce a background worker (Summarizer). It periodically reads older chats, condenses them into solid, summarized facts (e.g., "User bought a new bike in Jan 2024"), and updates ChromaDB.
- **Benefit:** Gives Lumir a much sharper long-term memory without overloading the LLM's prompt window with raw, unstructured past conversations.

### 4. Cognitive Memory Architecture (Ebbinghaus Decay, Associative Graph & Personality Curves)
- **Concept:** Human beings do not remember everything equally forever. Instead of static database rows, Lumir's mind simulates human neuro-cognitive memory retention and emotional dynamics.
- **Key Mechanics:**
  1. **Ebbinghaus Forgetting Curve (Memory Decay):**
     - Each memory has an `importance_weight` (1-10) and timestamp.
     - Casual banter ("I had pasta for lunch") decays naturally over days ($R = e^{-t / S}$).
     - Core facts (birthdays, health, key personal goals, passwords/preferences) have near-zero decay.
  2. **Spaced Reinforcement (Memory Crystallization):**
     - Every time a user mentions a topic or Lumir recalls it, the memory's `reinforcement_count` increments (+1).
     - Frequently referenced details permanently crystallize into long-term identity nodes.
  3. **Associative Connectome Graph (Brain-Scan Concept Linking):**
     - Memories are interconnected like synapses: e.g., `Chai ➔ Evening ➔ Divya's Favorite ➔ Ginger`.
     - When a topic arises, related nodes light up automatically, allowing Lumir to make organic, proactive connections ("It's evening, did you brew your usual ginger tea, Divya?").
  4. **Dynamic Personality Bell Curve (Mood & Energy Arc):**
     - Rather than acting as a static text-generator, Lumir maintains an internal emotional state (e.g., Playful, Reflective, Caring, Focused) that moves along a bell curve based on conversation tone and time of day.
- **Why It's Great:** Transforms Lumir from a generic assistant into a truly personal, living digital companion who feels genuinely human.

---

## 📱 Intra Portable Node: Embedded Phone Server ("Host as Server Mode")

### Vision & Problem Statement
Currently, IntraChat requires an external server (PC, Raspberry Pi, or Cloud VPS) running Python FastAPI. If users are traveling, trekking, in an emergency outage, or anywhere without electricity/routers, communication stops.

With **Intra Portable Node**, any Android device can become the **Network Hub / Server** with a single toggle!

### Key Architecture (Native Kotlin Embedded Server via Ktor)
- **Engine:** Use **Ktor Embedded Server (CIO engine)** directly inside the Android app (adds only ~2-3 MB to APK size).
- **Zero Python/NDK Overhead:** Eliminates the battery drain, heavy binaries, and memory footprint of embedded Python (Chaquopy).
- **Background Persistence:** Runs inside an Android **Foreground Service** with a high-priority ongoing notification showing connected peers, server uptime, and IP address.

### User Experience (The "One-Tap Hotspot Hub" Flow)
1. **Host Setup:**
   - User enables Phone Wi-Fi Hotspot (or stays on existing Wi-Fi).
   - In IntraChat Hub/Settings, toggles **"Host Intra Server"**.
   - App automatically detects network interfaces (e.g., `192.168.43.1` or Wi-Fi IP) and launches Ktor on port `8000`.
2. **Instant QR Connect:**
   - The screen renders a connection card with server status and a large **QR Code**.
   - The hosting phone's own chat client automatically binds to `ws://127.0.0.1:8000/ws`, so the host chats like any other user.
3. **Peer / Client Connection:**
   - Friends connect to the host's Wi-Fi hotspot.
   - **Android Users:** Scan host's QR code in IntraChat (auto-fills Base URL) and join immediately.
   - **Non-Android Users (iPhone, Laptops, Mac):** Simply open any browser and visit `http://192.168.43.1:8000` to use the embedded Web Client served right from the host phone's assets!

### Embedded Capabilities
- **Real-Time WebSocket Router:** In-memory broadcast & direct message routing with peer status tracking.
- **Multipart Media Uploads (`/upload`):** Streams photos, videos, and documents directly to app-private cache/storage with progress tracking.
- **Embedded Web Client:** Serves the static `index.html`, `chat.html`, `style.css`, and `app.js` bundle straight from Android assets.
- **Future Mesh Extension:** Multiple phones can discover each other via Wi-Fi Direct or Multicast DNS (mDNS) to form decentralized mesh relays.

---

## 🎙️ Lumir Full-Duplex AI Voice Calling & Distributed Node Architecture

### 1. Vision & Core Concept
In IntraChat, users can already chat with Lumir via text. When a user taps the **Call Icon 📞** on Lumir's chat screen:
- Lumir automatically picks up the call (`call_accept`).
- The user speaks naturally into the phone's microphone.
- Lumir listens, thinks, and responds back in voice in near real-time, functioning like an offline, privacy-first Siri/Jarvis on your local network!

---

### 2. High-Speed Processing Pipeline (STT ➔ LLM ➔ TTS)

```
[User Mic] ➔ [Audio Stream] ➔ [STT: Whisper Node] ➔ [Text] ➔ [LLM: Gemma via Ollama] ➔ [Reply Text] ➔ [TTS: eSpeak / Piper] ➔ [Audio Stream] ➔ [Phone Speaker]
```

#### A. STT (Speech-to-Text - Listening)
- **Engine:** Whisper (`faster-whisper` or OpenAI-compatible Whisper API endpoint).
- **Flexibility:** Can run locally on the server or on an external GPU PC on the LAN.
- **Languages:** Supports English, Hindi, and natural code-switching (Hinglish).

#### B. LLM (Brain - Conversational Agent)
- **Engine:** Ollama running lightweight conversational models like **Gemma 2 / Gemma 3** (or free online API tier).
- **Voice System Prompt Tuning:**
  - *"You are Lumir in a live voice phone call with {sender}. Reply in 1 to 2 short, crisp, natural sentences. Avoid formatting, markdown, lists, or asterisks as your output will be directly read aloud."*
- **Memory Context:** Pulls user facts from `lumir/memory.py` so Lumir recognizes who is calling.

#### C. TTS (Text-to-Speech - Speaking)
- **Version 1 (V1 - Ultra-Lightweight):**
  - **`eSpeak-ng`**: Instantaneous generation (~1ms latency), zero GPU/RAM overhead, completely offline fallback.
- **Version 2 (V2 - Natural Humanoid Voice):**
  - **`Piper TTS` / `Kokoro`**: Highly realistic open-source neural voices running either locally or streamed from a distributed node.

---

### 3. Distributed Node Architecture (LAN Offloading)
Not every home server (e.g. Raspberry Pi) has a dedicated GPU for Whisper + LLM + TTS. To solve this, IntraChat introduces a **Distributed AI Node** system:

- **Modular Offloading:** The IntraChat backend acts as the orchestrator. It can delegate:
  1. STT to `http://<Node_IP>:9000/v1/audio/transcriptions` (e.g., Gaming PC running faster-whisper).
  2. LLM to `http://<Node_IP>:11434` (Ollama running Gemma / Llama).
  3. TTS to `http://<Node_IP>:5000/tts` (Piper Node) with local **eSpeak fallback** if the node goes down.

---

### 4. Admin Panel Settings (`/admin/ai_settings` & SQLite `config`)
Admins can customize and configure the entire voice & AI pipeline from the Web Admin Dashboard:

| Setting Key | Description | Example Value |
| :--- | :--- | :--- |
| `ai_voice_enabled` | Master toggle for Lumir voice calls | `true` / `false` |
| `whisper_url` | Address of local or remote Whisper node | `http://192.168.1.100:9000` or `local` |
| `whisper_model` | Whisper model size | `tiny`, `base`, `small` |
| `ollama_url` | Ollama LLM endpoint | `http://localhost:11434` |
| `ai_model` | Preferred model for conversation | `gemma2:2b`, `llama3.2:1b` |
| `tts_provider` | Active TTS Engine | `espeak` (V1) or `remote_url` |
| `tts_url` | Remote TTS node URL (with eSpeak fallback) | `http://192.168.1.101:5000/tts` |

---

### 5. Call Signaling & Transport Protocol
1. **Call Initiation:**
   - Android client sends: `{"type": "call_request", "receiver": "Lumir", "sender": "username"}`.
   - Server recognizes `receiver == "Lumir"` in `chat.py`, immediately returns:
     `{"type": "call_accept", "sender": "Lumir", "receiver": "username"}`.
   - Android app transitions to active call screen (`CallScreen.kt`) with running call timer and pulsing Lumir avatar.
2. **Audio Streaming:**
   - Phone mic streams speech chunks (via WebSocket binary packets or HTTP chunked POST `/calls/lumir/audio`).
   - Server buffers speech until Voice Activity Detection (VAD) detects end of sentence, then runs the STT ➔ LLM ➔ TTS pipeline.
   - Synthesized audio (WAV/Opus) streams back to the phone and plays over earpiece or loudspeaker.
3. **Call Termination:**
   - User taps **End Call 🛑**: Phone sends `{"type": "call_end", "receiver": "Lumir"}`.
   - Server terminates the active audio pipeline session cleanly.

---

## 📺 IntraPlay: Smart TV & Fire TV Edition (Leanback Media Hub)

### 1. Vision & Core Philosophy
While IntraChat is tailored for personal mobile devices, **IntraPlay** brings the Intra ecosystem to the living room big screen (Android TV, Google TV, FireStick, Smart Boxes). It strips away mobile-specific typing keyboards and focuses 100% on a **remote-controlled (D-Pad), leanback entertainment and local home cinema experience**.

---

### 2. Key Architecture & Features

#### A. Free-To-Air GitHub IPTV Hub (.m3u / .m3u8 HLS)
- **Automatic Playlist Loader:** Ingests legal, public Free-To-Air streams from GitHub (`iptv-org` playlists).
- **Categories & Channel Grid:**
  - News (DD News, International, Regional feeds)
  - Music & Entertainment
  - Sports & Documentaries
  - Regional & Language filters
- **Engine:** Built upon Google Media3 / ExoPlayer with zero extra bloated players needed. Supports adaptive bitrate HLS streams.

#### B. Inbuilt SMB LAN Cinema (Direct 4K Home Theater)
- **Direct PC/NAS Streaming:** Connects to home computers, Linux Samba servers, or IntraDrive phone nodes using `jcifs-ng`.
- **Zero Buffering / Zero Internet:** Streams uncompressed 1080p and 4K MKV/MP4 files over 5GHz Wi-Fi with instant seeking.
- **Subtitle & Audio Switching:** Automatic `.srt` subtitle detection and multi-audio track selection via remote.

#### C. Big-Screen Family Slideshow & Ambient Frame
- **Ambient Mode:** Displays high-resolution photos and family memories pulling directly from shared LAN folders or WebDrop storage.
- **Music Jukebox:** Plays MP3/FLAC playlists in the background while browsing photos or idling.

#### D. Intra Living Room Bridge (Smart Caller ID & Casting)
- **Heads-Up Caller Overlay:** When someone rings your home phone via IntraChat, a sleek non-intrusive HUD appears in the top-right corner of the TV screen: *"Incoming Call from Rahul"*.
- **1-Click Local Casting:** Any phone in the house can push a video, photo, or YouTube link to IntraPlay with a single "Cast to TV" button.

---

## ⚡ IntraServer (`:intra_server` Android Host): Roadmap & Next Steps

### 1. Phase 1: Core Engine & Realtime Chat [COMPLETED]
- Multi-module Gradle configuration with `minSdk = 21` (supports Android 5.0+).
- Ktor CIO embedded engine on Port 8000 with CORS & WebSocket routing.
- Native SQLite database (`users` & `messages` tables).
- Realtime WebSocket messaging, presence broadcasts (`online`/`offline`), and typing indicator forwarding.
- 24/7 background stability with Foreground Service, `PARTIAL_WAKE_LOCK`, and `WifiLock`.
- Material 3 Dark Server Appliance Dashboard with live IP badge, dynamic QR code, and real-time terminal log console.

### 2. Phase 2: Media & Profile Synchronization [COMPLETED]
- **Profile Photo Upload (`POST /profile/upload_photo`)**:
  - Multipart avatar upload saved directly to internal app storage (`files/uploads`).
  - Automatic avatar URL association in SQLite `users` table and instant sync across client contacts.
- **File & Media Transfers (`POST /upload` & `GET /uploads/{filename}`)**:
  - High-speed local LAN image, video, audio note, and document sharing.
  - Efficient file streaming directly from Android storage to connected clients.
- **Zero-Friction WebDrop (`/drop`)**:
  - Instant LAN file and clipboard sharing with automatic 10-day retention cleanup.

### 3. Phase 3: Embedded Web Chat & WebDrop Client [COMPLETED]
- Bundled static HTML5/CSS3/JS web client (`backend/static`) directly in `intra_server/src/main/assets/web/`.
- Served via Ktor on `http://<phone-ip>:8000/` and `http://<phone-ip>:8000/drop`.
- Any laptop, PC, iPhone, or smart TV on the Wi-Fi can chat or share files without downloading any app.

### 4. Phase 4: LAN Auto-Discovery & Stealth Mode [IN PROGRESS]
- **Python Backend:** Already implemented with `discovery_enabled` database toggle in `users.py` / `admin_api.py`. When enabled, `/api/ping` broadcasts presence to `ServerDiscoveryManager.kt`. When disabled, activates **Stealth Mode** (403 Forbidden).
- **IntraServer Parity:** Add `discovery_enabled` setting in `ServerDatabase.kt`, toggleable via Admin dashboard, controlling `/api/ping` responses on the phone server.

---

## 🗄️ IntraDrive: Turn Phone Server into a Virtual Network Hard Drive (WebDAV Engine)

### 1. Vision & Problem Statement
Users already have an SMB client in the Android app to browse existing Linux/Windows Samba shares. However, running a true SMB server on non-root Android is nearly impossible because standard SMB requires privileged port **445**, which Android kernel blocks for normal apps.

**The Solution:** Implement a native **WebDAV Engine** inside `IntraServer` on port `8000` (e.g. `http://<phone-ip>:8000/drive`).

### 2. Why WebDAV is the Ultimate Solution:
- **Zero Root / Zero High-Port Hack:** Runs directly on unprivileged user port `8000` alongside existing Ktor routes.
- **Native OS Integration (No extra software needed):**
  - **Windows 10 / 11:** Open File Explorer ➔ Right-click "This PC" ➔ **Map Network Drive** ➔ Enter `http://192.168.x.x:8000/drive`. The phone mounts directly as drive letter `Z:\` or `D:\`!
  - **macOS:** Finder ➔ `Go` ➔ `Connect to Server` (`Cmd + K`) ➔ Enter `http://192.168.x.x:8000/drive`.
  - **Linux:** Mountable via standard Nautilus, Dolphin, or `davfs2`.
  - **IntraChat Android Client:** Uses built-in WebDAV/SMB browser to access files.
- **Core HTTP Verbs:**
  - `PROPFIND` / `PROPPATCH` (List folders & file metadata)
  - `GET` & `PUT` (Read and write file streams)
  - `MKCOL` (Create new folders)
  - `DELETE` & `MOVE` / `COPY` (File management)

---

## 🧠 Node 2 Distributed AI Engine: Full-Duplex Lumir Calling

### 1. Hardware Split & Strategy
- **Node 1 (Lightweight / 24/7):** Handles core chat, WebDrop, and presence.
- **Node 2 (High-Performance GPU Machine):** Runs the heavy AI stack:
  1. **STT:** `faster-whisper` (medium/large-v3) with GPU CUDA acceleration for near-instant Hindi/English audio transcription.
  2. **LLM:** Ollama running high-capacity Gemma 2 (9B/27B) or Llama 3 for intelligent conversational reasoning.
  3. **TTS:** `Kokoro` / `Piper TTS` streaming high-fidelity natural humanoid speech.
- **Calling Flow:**
  - Phone mic audio is sent to Node 1 via WebSocket.
  - Node 1 proxies audio chunks to Node 2's AI pipeline.
  - Node 2 responds with streaming voice packets back to the phone earpiece/speaker.

---

## 📺 Local Media Casting (Phone ➔ PC / TV)
- **Concept:** Send any active video or photo from the phone to a connected PC browser or IntraTV screen with a single "Cast" tap.
- **Lower Priority:** Planned after IntraDrive and Node 2 AI setup.




