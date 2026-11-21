# 🚀 Intra Server - Future Innovations & AI Roadmap

> **Core Philosophy:** Local-First, Privacy-Centric, Self-Evolving Private LAN Companion.

---

## 🧠 1. Lumir Self-Improving Brain (Teacher-Student Architecture)
**Concept:** Local models (Qwen 0.5B / Llama 3B) are fast and offline, but limited in reasoning and metacognition. To make Lumir truly adaptive and self-evolving, we leverage Frontier Cloud Models as an occasional "Teacher / Mentor".

### Architecture:
- **Daily Student (Local Edge):** Runs 100% offline via `llama.cpp` / `Ollama`. Handles instant responses, family chit-chat, and low-latency commands.
- **Nightly Mentor (Frontier Cloud - Gemini 1.5 Flash/Pro Free Tier):**
  - **Memory Consolidation:** Audits raw SQLite chat logs from the day, extracts high-value personal memories, relations, and emotional valence into long-term vector memory.
  - **Prompt & Persona Tuning:** Evaluates where Lumir gave dumb or hallucinated replies and suggests prompt/soul adjustments.
  - **Synthetic Data Generation:** Generates micro fine-tuning pairs from user interactions for future on-device model LoRA training.

---

## ⚡ 2. Cloud AI & Desi Voice Adapters (Zero-Hardware Tier)
**Problem:** Many users run Intra on potato laptops, low-end PCs, or old Android phones with 4GB RAM where heavy local LLMs choke or drain the battery.

### Proposed Solution:
- **Google AI Studio (Gemini 1.5 Flash):**
  - Free tier: 15 RPM, 1,500 requests/day, 1M context window.
  - 0.3s response latency with zero local CPU/GPU load.
  - Instant multimodal image & document vision understanding without local VLM overhead.
- **Sarvam AI (Indian Regional Languages & Desi Voice Engine):**
  - **Bulbul TTS:** Hyper-natural Hindi, Hinglish, and regional Indian language speech synthesis (far superior to Piper for Indian accents).
  - **Saaras STT:** 99% accuracy on mixed Indian English/Hindi audio transcripts.

### Intelligent Hybrid Routing:
```
                      [ User Input / Audio ]
                                │
                                ▼
                   ┌───────────────────────────┐
                   │  Provider Mode in Admin:  │
                   │   • Local Only (Ollama)   │
                   │   • Cloud (Gemini/Sarvam) │
                   │   • Smart Hybrid (Auto)   │
                   └────────────┬──────────────┘
                                │
             ┌──────────────────┴──────────────────┐
             ▼                                     ▼
    [ Internet Connected ]                 [ Offline / LAN Only ]
             │                                     │
             ▼                                     ▼
    Cloud Frontier API                    Local GGUF / llama-server
   (⚡ Fast, Rich Voice)                  (🛡️ 100% Offline & Private)
```

---

## 📦 3. Sadness Splitter 3000 (Native Android Media Engine)
- Standalone or embedded audio/video conversion and transcoding engine powered by FFmpeg.
- Ability for `intra_server` and `intra_client` to delegate heavy transcoding, audio splitting, and format conversion directly to this specialized native service.

---

*Document created on 2026-11-21 as a strategic backlog for Lumir AI and Intra ecosystem evolution.*
