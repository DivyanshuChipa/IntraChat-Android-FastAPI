# 🎙️ Voice-Capability Engine (Node 2 Microservice)

High-performance, low-latency AI Voice Node dedicated to offloading Speech-to-Text (STT) and Text-to-Speech (TTS) from the main IntraChat server.

Designed to run on **Node 2** (e.g. Intel Core i3 12th Gen / Linux or Windows) to preserve 100% of Node 1's CPU resources for real-time chat, SQLite DB, and WebSockets.

---

## ⚡ Architecture & Latency Breakdown

```text
[Phone Mic] 🎙️
    │
    ▼ (Audio WAV/WebM)
[Node 1: Main Server] ──(Forward audio)──▶ [Node 2: Voice-Capability (Port 8005)]
                                                ├── 1. Faster-Whisper (CPU int8, ~150ms)
                                                ├── 2. Query Ollama / LLM
                                                └── 3. Piper TTS (<50ms natural voice)
                                                       └── Fallback: espeak-ng
    ▲                                           │
    └──────────────(Return Speech Audio)────────┘
```

---

## 🚀 Quick Start

### On Linux (Node 2 - Recommended):
```bash
cd voice-capability
chmod +x install.sh start.sh
./install.sh    # Installs python-venv, espeak-ng, ffmpeg & pip requirements
./start.sh      # Starts server on http://0.0.0.0:8005
```

### On Windows (Dev PC):
Double-click `install.bat`, then double-click `start.bat`.

---

## 📡 API Endpoints (Port 8005)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/health` / `/api/ping` | Node 2 health & capability discovery status |
| `POST` | `/api/stt` | Transcribe speech audio to text (`faster-whisper`) |
| `POST` | `/api/tts` | Convert text to speech WAV stream (`piper` / `espeak-ng`) |
| `POST` | `/api/process_speech` | Full dialogue loop (Audio In ➔ Transcribe ➔ LLM ➔ Audio Out) |

---

## 🔗 Pairing with Main Server (Node 1)

1. Open IntraChat Admin Panel (`http://<node1-ip>:8000/admin`).
2. Go to **AI & Lumir Settings**.
3. Set **Voice Node URL:** `http://<node2-ip>:8005` (e.g. `http://192.168.31.94:8005`).
4. Click **Test Connection** to verify green handshake status.


---

## 🔮 Future Blueprint: Dynamic STT Profiles & Admin Panel Toggle

To provide the ultimate flexibility between latency and accuracy without touching terminal code, we will introduce **Dynamic STT Quality Profiles** managed directly from the IntraChat Web Admin Panel.

### 1. Quality Profiles Matrix

| Profile | Model | RAM Usage | STT Latency | Recommended Use Case |
| :--- | :--- | :--- | :--- | :--- |
| **⚡ Fast (Default)** | `tiny` | ~75 MB | **~100ms - 200ms** | Casual daily calls, English, snappy conversational banter |
| **⚖️ Balanced** | `base` | ~140 MB | **~300ms - 400ms** | Standard multi-language & balanced accuracy |
| **🎯 High Precision** | `small` | ~460 MB | **~700ms - 900ms** | Heavy Hindi/regional accents, noisy background environments |

### 2. End-to-End Toggle Architecture

```text
[Web Admin Panel UI] (http://node1:8000/admin)
         │
         ▼ (Selects: Fast / Balanced / Precision)
[SQLite `config` Table] (Key: `voice_stt_profile` = 'tiny' | 'base' | 'small')
         │
         ▼ (Sent in /voice_turn request)
[Node 1: backend/lumir/voice.py]
         │
         ▼ (Passes query param: POST /api/stt?model=tiny)
[Node 2: voice-capability/voice_server.py]
         └── Lazy-caches model instances in `models_dict = {}`
             (Instant switch, 0-second re-download once cached)
```

### 3. Implementation Checklist:
1. **Node 2 (`voice_server.py`):**
   - Update `get_stt_model(model_name: str)` to maintain a dictionary of loaded Whisper models.
   - Accept optional `model` query parameter in `/api/stt`.
2. **Main Server (`backend/users.py` & `backend/lumir/voice.py`):**
   - Add `voice_stt_profile` to `config` DB defaults (`'tiny'`).
   - Forward chosen profile to Node 2 `/api/stt`.
3. **Web Admin UI (`admin.html`):**
   - Add a dropdown toggle under **AI & Lumir Voice Settings**:
     - `Fast (Tiny - Lowest Latency)`
     - `Balanced (Base - Recommended)`
     - `Precision (Small - Best Hindi Accuracy)`
