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

## 📺 IntraTV: Smart Media Center & Free-To-Air IPTV Integration

### 1. Vision & Dual-Target Strategy
To turn Intra from a pure messenger into a comprehensive **LAN Home Ecosystem**, media playback is expanded into two tailored surfaces:

1. **IntraChat Mobile (Phone Integration):**
   - In `ContactListScreen.kt`, the Top Bar "Hub" (Dice) menu contains a vacant 4th slot.
   - We introduce **"Live TV / IPTV Hub"** here, letting mobile users watch free-to-air news, sports, music, and stream SMB media directly on their phones.
2. **Dedicated IntraTV App (Android TV / FireStick Leanback Edition):**
   - A standalone, remote-controlled (D-Pad friendly) media player app.
   - Stripped of heavy typing/chat features, focusing 100% on **Leanback Entertainment, Local Cinema, and Intercom Notifications**.

---

### 2. Core Capabilities

#### A. Free-To-Air IPTV (.m3u / .m3u8 HLS Streams)
- **Engine:** Built upon the existing **`ExoPlayer` (`VideoPlayer.kt`)** which natively parses HLS live streams without additional dependencies.
- **Playlist Management:**
  - Ingests public, legal Free-To-Air IPTV playlists (e.g. GitHub `iptv-org` feeds: Doordarshan, regional channels, news, music, international).
  - Users can input custom M3U playlist URLs or local files.
  - Channel Grid / Electronic Program Guide (EPG) categorized by Language and Genre.

#### B. SMB Local Home Theater (Direct LAN 4K Streaming)
- Uses the battle-tested **`SmbHelper.kt`** engine to browse home PCs, NAS, and router hard drives.
- Streams high-bitrate 1080p/4K MKV/MP4 files over local Wi-Fi with zero internet consumption and zero buffering.
- Subtitle (.srt) detection and audio track switching support.

#### C. Music Jukebox & LAN Slideshow
- Background music player for MP3/FLAC collections hosted on the LAN.
- Ambient photo slideshow pulling from shared family folders on the SMB server.

#### D. Intra Ecosystem Bridge (TV Caller ID & Casting)
- **Incoming Call Overlay:** When a phone calls via IntraChat, the TV displays a sleek, non-intrusive Heads-Up Display (HUD) in the top-right corner: *"Incoming Intra Call from Home"*.
- **Local Casting:** Phones can push a video or photo to the TV with one tap ("Cast to IntraTV").



