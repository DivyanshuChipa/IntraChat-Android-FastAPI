# 🚀 Intra Server (Android Ktor) - Future Roadmap & Ideas Blueprint
**File Location**: `intra_server/intra_server-future_idea.md`
**Purpose**: Comparison of the Python FastAPI server (`backend/lumir/` and associated modules) with the Android Ktor server (`intra_server/`), detailing architectural blueprints, technical considerations, and implementation roadmaps for porting advanced Lumir AI and server features to the phone.

---

## 🌟 Executive Summary & Current Architecture

| Capability | Python Backend (`backend/`) | Current Ktor Android Server (`intra_server/`) | Future Recommendation for Phone Server |
| :--- | :--- | :--- | :--- |
| **Server Engine** | FastAPI + Uvicorn (Async Python) | Ktor CIO Engine (Kotlin Coroutines) | Keep Ktor CIO (lightweight, native) |
| **Image Utilities** | Pillow (`PIL.Image`, `ImageOps`) | Native Android Skia (`Bitmap`, `Canvas`) | Passport Layout & Compression active; Fast & low RAM |
| **PDF Tools** | `PyPDF2`, `reportlab` | Native Android `PdfDocument`, `PdfRenderer` | Merge PDFs & Convert Images-to-PDF active |
| **AI LLM Inference** | Ollama REST Client (`requests` + multi-turn) | `OllamaClient.kt` (HTTP single-turn) | Upgrade to multi-turn conversation memory |
| **Persona / Soul** | `soul.md` (Rich Desi Hinglish Lumir persona) | Minimal 2-line system prompt | Port full `soul.md` system prompt |
| **Agentic Tool Router** | Autonomous keyword & intent matching | Static prefix & regex matchers | Port autonomous intent mapper |
| **Vision AI** | Ollama Gemma / LLaVA Vision (`ask_ai`) | Text prompt only | Base64 image payload to Ollama Vision endpoint |
| **Context & Memory** | ChromaDB + SQLite Synaptic Memory | In-memory `userContext` (last 10 files) | Lightweight SQLite semantic/keyword memory |
| **Diagnostics & Weather**| `psutil` + Open-Meteo REST API | None | `ActivityManager` + `StatFs` + Open-Meteo |
| **Heavy Video / Audio**| `ffmpeg` + `yt-dlp` | Delegated notice to PC | Keep delegated or offload via LAN RPC |

---

## 1. 🧠 Lumir AI Engine Enhancements

### 1.1 Multi-Turn Chat History & Conversation Context
- **Python Implementation**:
  `get_lumir_history(sender, limit=6)` queries SQLite to build user/assistant message pairs, allowing contextual conversations ("Who was that?", "Tell me more about it").
- **Ktor Server Architecture**:
  - Add `db.getLumirChatHistory(sender: String, limit: Int = 6): List<Pair<String, String>>` to `ServerDatabase.kt`.
  - In `OllamaClient.kt`, construct the `messages` array in the JSON payload:
    ```json
    {
      "model": "model_name",
      "stream": false,
      "messages": [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": "pichla sawal"},
        {"role": "assistant", "content": "pichla jawab"},
        {"role": "user", "content": "naya sawal"}
      ]
    }
    ```
  - Exclude system commands (like `###passport###`, `###topdf###`) from the history prompt so the AI context remains focused on conversation.

### 1.2 Lumir Soul Persona Integration (`soul.md`)
- **Python Implementation**:
  Uses `soul.md` containing Lumir's identity: created by Divya, friendly, witty, warm, conversational Hinglish/English tone.
- **Ktor Server Architecture**:
  - Store `soul.md` inside `intra_server/src/main/assets/lumir_soul.txt`.
  - Load the persona string during `IntraServerApp` initialization or on-demand in `OllamaClient.kt`.
  - Inject the dynamic sender name into the persona template: `"You are talking with $sender"`.

### 1.3 Autonomous Agentic Intent Router (`tool_router.py`)
- **Python Implementation**:
  `resolve_autonomous_tool_intent` analyzes natural sentences like:
  - *"bhai iski passport size bana de"* -> maps to `passport`
  - *"size thoda chota kar do"* -> maps to `compress`
  - *"in sab photos ka pdf banao"* -> maps to `topdf`
  - *"aaj barish hogi kya"* -> maps to `weather`
- **Ktor Server Architecture**:
  - Implement `LumirIntentRouter.kt` using fast regex and keyword stem sets.
  - Intercept user messages before the Ollama fallback.
  - If a clear tool intent is detected and requirements are met (e.g., photo exists in context), invoke the tool directly.

### 1.4 Ollama Vision (Multimodal Image Understanding)
- **Python Implementation**:
  Encodes the image file to Base64 and attaches `images: [base64_str]` in the Ollama request body for models like `llama3.2-vision` or `llava`.
- **Ktor Server Architecture**:
  - When a user asks questions like *"ye kiska photo hai?"* or *"read this receipt"* while having an image in context:
  - Resize image to max 1024x1024 in background memory, encode to Base64 string via `android.util.Base64.encodeToString`.
  - Pass the Base64 string in `images: [ "..." ]` in `OllamaClient.kt`.
  - Allows fully local, privacy-respecting OCR and visual scene understanding via the PC Ollama server.

---

## 2. 📱 Native Device Diagnostics & Utilities

### 2.1 Phone Server Status (`/server` or "system status")
- **Python Server**: Uses `psutil.cpu_percent()`, `psutil.virtual_memory()`, `psutil.disk_usage()`.
- **Ktor Android Server Blueprint**:
  - **RAM**: Query `ActivityManager.MemoryInfo`:
    ```kotlin
    val mi = ActivityManager.MemoryInfo()
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    activityManager.getMemoryInfo(mi)
    val availMB = mi.availMem / (1024 * 1024)
    val totalMB = mi.totalMem / (1024 * 1024)
    ```
  - **Storage**: Query `android.os.StatFs(context.filesDir.absolutePath)`:
    ```kotlin
    val stat = StatFs(context.filesDir.absolutePath)
    val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
    val totalBytes = stat.blockCountLong * stat.blockSizeLong
    ```
  - **Battery**: Read `BatteryManager.EXTRA_LEVEL` and `EXTRA_SCALE` from sticky broadcast `Intent.ACTION_BATTERY_CHANGED`.
  - **Thermal / CPU**: Read `/sys/class/thermal/thermal_zone0/temp` where accessible or report CPU core count and load state.
  - Output format: Return a formatted system status bubble with battery level, free RAM, and storage health.

### 2.2 Live GPS & IP Weather (`/weather` or "mausam kaisa hai")
- **Python Server**: Calls Open-Meteo REST API (`https://api.open-meteo.com/v1/forecast`) or wttr.in fallback.
- **Ktor Android Server Blueprint**:
  - Native Kotlin coroutine using standard `HttpURLConnection` or Ktor HTTP Client.
  - Query `https://api.open-meteo.com/v1/forecast?latitude=...&longitude=...&current_weather=true`.
  - Return formatted temperature, weather icon, day/night indicator, and wind speed without requiring any API keys.

---

## 3. 🧠 Synaptic Memory Brain & Fact Retention

### 3.1 SQLite Fact Store (Lightweight Vector Alternative)
- **Python Server**: Uses ChromaDB vector database for semantic similarity and Ebbinghaus forgetting curve (`memory_brain.py`).
- **Mobile Considerations**:
  - Running ChromaDB or ONNX vector embeddings directly on legacy Android devices consumes excessive RAM and battery.
- **Ktor Alternative Blueprint**:
  - Create a `user_facts` table in `ServerDatabase.kt`:
    ```sql
    CREATE TABLE IF NOT EXISTS user_facts (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        username TEXT NOT NULL,
        fact TEXT NOT NULL,
        category TEXT DEFAULT 'general',
        created_at INTEGER NOT NULL
    );
    ```
  - Trigger word scanning: Detect phrases like *"my favorite...", "mujhe pasand hai...", "mera birthday..."*.
  - When user chats with Lumir, query the top 3 latest facts for that user and append them to the system prompt:
    ```
    Context about user $sender:
    - Likes coding in Kotlin
    - Birthday is 15th August
    ```
  - Gives Lumir a "memory" without any heavy ML libraries on the device.

---

## 4. 🌙 Night Worker & Maintenance Sentinel

### 4.1 Scheduled Background Tasks
- **Python Server**: APScheduler runs hourly cleanup routines and a 3 AM memory prune (`night_worker.py`).
- **Ktor Server Blueprint**:
  - Use `WorkManager` or a background coroutine loop inside `ServerService.kt`.
  - **Temporary Media Prune**: Delete temporary generated PDF/compressed files in `uploads/` older than 7 days.
  - **Database VACUUM**: Run SQLite `VACUUM` and `PRAGMA optimize` once every Sunday night to keep database fast on low-end flash storage.

---

## 5. ⚡ Heavy Media Delegation Strategy (PC Server Offloading)

### 5.1 Remote Procedure Call (RPC) to PC Server
- Video transcoding (`ffmpeg`) and YouTube downloading (`yt-dlp`) are intensive and drain phone battery quickly.
- **Future Integration Plan**:
  - When the phone server receives a YouTube URL or a `/transcode` request:
  - If a PC FastAPI server is detected on the LAN, forward the task payload via REST:
    `POST http://<PC_IP>:8000/api/heavy_task`
  - When complete, download the result back to phone `uploads/` or stream the PC download link directly to the chat client.
  - If no PC is available, present the user with a clean notification explaining battery conservation.

---

## 📋 Implementation Milestones Summary

1. **Phase 1 (Completed)**:
   - ✅ Passport Photo generation (A6/A4, 6/9 grid, custom name & date) via Android Skia.
   - ✅ Smart Image Compression (target KB loop + auto-scaling).
   - ✅ PDF Merging (`PdfUtils.mergePdfs`).
   - ✅ Multi-Images to PDF conversion (`PdfUtils.convertImagesToPdf` & LumirLite options).
   - ✅ LAN Ollama Bridge (`OllamaClient.kt`).

2. **Phase 2 (Upcoming - Low Overhead, High Value)**:
   - [ ] Multi-turn chat history buffer for Ollama.
   - [ ] Port `soul.md` system prompt for rich Lumir Hinglish personality.
   - [ ] Phone System Diagnostics (`/server` with RAM, Storage, Battery).
   - [ ] Live Weather query (`/weather` via Open-Meteo).

3. **Phase 3 (Medium Complexity)**:
   - [ ] Autonomous natural language router (detecting commands without slashes).
   - [ ] Ollama Vision support (base64 image dispatch to PC Ollama).
   - [ ] SQLite user facts memory.

4. **Phase 4 (Advanced)**:
   - [ ] LAN PC delegation for heavy ffmpeg / YouTube downloads.
   - [ ] WorkManager night maintenance sentinel.
