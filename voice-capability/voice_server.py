import argparse
import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from typing import Optional

import requests
import uvicorn
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, Response, StreamingResponse

# ==============================================================================
# 🎙️ Voice-Capability Engine (Node 2 Microservice)
#
# Dedicated AI Voice Node for IntraChat / Lumir:
# - STT: Faster-Whisper (int8 quantization on CPU, ~150ms latency)
# - TTS: Piper TTS (natural human voice, <50ms) with espeak-ng fallback
# - Pipeline: End-to-end voice question ➔ LLM response ➔ synthesized speech
# ==============================================================================

app = FastAPI(
    title="Intra Voice-Capability Engine",
    description="Dedicated Node 2 Voice Microservice for IntraChat",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Global lazy-loaded models
whisper_model = None
piper_voice = None

DEFAULT_WHISPER_MODEL = os.getenv("WHISPER_MODEL", "base")
DEFAULT_PIPER_MODEL_NAME = os.getenv("PIPER_MODEL", "en_US-lessac-medium")


def get_stt_model():
    """Lazy load Faster-Whisper model on CPU with int8 quantization."""
    global whisper_model
    if whisper_model is None:
        try:
            from faster_whisper import WhisperModel

            print(f"🔄 Loading Faster-Whisper ({DEFAULT_WHISPER_MODEL}) on CPU int8...")
            whisper_model = WhisperModel(
                DEFAULT_WHISPER_MODEL, device="cpu", compute_type="int8"
            )
            print("✅ Faster-Whisper loaded successfully!")
        except Exception as e:
            print(f"⚠️ Faster-Whisper load failed: {e}")
            whisper_model = None
    return whisper_model


VOICES_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "voices")


def get_piper_model(voice_name: Optional[str] = None) -> Optional[str]:
    """Finds matching .onnx model in voices/ directory."""
    if not os.path.isdir(VOICES_DIR):
        return None

    if voice_name:
        for fname in os.listdir(VOICES_DIR):
            if fname.endswith(".onnx") and voice_name.lower() in fname.lower():
                return os.path.join(VOICES_DIR, fname)

    # Priority defaults for Lumir: Amy (young 16yo EN) -> Priyamvada (young HI) -> Lessac -> any .onnx
    for preferred in ["amy", "priyamvada", "lessac"]:
        for fname in os.listdir(VOICES_DIR):
            if preferred in fname.lower() and fname.endswith(".onnx"):
                return os.path.join(VOICES_DIR, fname)

    for fname in os.listdir(VOICES_DIR):
        if fname.endswith(".onnx"):
            return os.path.join(VOICES_DIR, fname)

    return None


def synthesize_with_piper(
    text: str, output_path: str, voice_name: Optional[str] = None
) -> bool:
    """Attempts to synthesize voice using Piper TTS CLI with matching .onnx model."""
    try:
        piper_exe = shutil.which("piper") or shutil.which("piper-tts")
        if not piper_exe:
            # Check inside local virtual environment bin
            venv_piper = os.path.join(
                os.path.dirname(os.path.abspath(__file__)), "venv", "bin", "piper"
            )
            if os.path.exists(venv_piper):
                piper_exe = venv_piper

        if not piper_exe:
            print("⚠️ Piper executable not found in PATH or venv.")
            return False

        # Auto-detect Hindi script (Devanagari)
        selected_voice = voice_name
        import re

        if not selected_voice and re.search(r"[\u0900-\u097F]", text):
            selected_voice = "priyamvada"

        model_path = get_piper_model(selected_voice)
        if not model_path:
            print(f"⚠️ No Piper .onnx model found in {VOICES_DIR}")
            return False

        print(
            f"🎙️ [PIPER] Synthesizing speech using model: {os.path.basename(model_path)}"
        )
        proc = subprocess.Popen(
            [piper_exe, "--model", model_path, "--output_file", output_path],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        _, stderr = proc.communicate(input=text)
        if (
            proc.returncode == 0
            and os.path.exists(output_path)
            and os.path.getsize(output_path) > 100
        ):
            return True
        else:
            print(f"⚠️ Piper returned code {proc.returncode}: {stderr}")
    except Exception as e:
        print(f"⚠️ Piper synthesis error: {e}")
    return False


def synthesize_with_espeak(text: str, output_path: str) -> bool:
    """Fallback TTS using espeak-ng or espeak CLI."""
    try:
        espeak_exe = shutil.which("espeak-ng") or shutil.which("espeak")
        if espeak_exe:
            cmd = [espeak_exe, "-w", output_path, text]
            res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            if (
                res.returncode == 0
                and os.path.exists(output_path)
                and os.path.getsize(output_path) > 100
            ):
                return True
    except Exception as e:
        print(f"⚠️ espeak fallback error: {e}")
    return False


def generate_speech_wav(text: str, voice: Optional[str] = None) -> bytes:
    """Synthesizes speech to WAV bytes using Piper with espeak-ng fallback."""
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp_file:
        tmp_path = tmp_file.name

    try:
        # 1. Try Piper first (High quality natural voice from voices/ folder)
        success = synthesize_with_piper(text, tmp_path, voice_name=voice)

        # 2. Fallback to espeak / espeak-ng
        if not success:
            success = synthesize_with_espeak(text, tmp_path)

        # 3. Ultimate minimal WAV generator fallback (if neither is installed)
        if not success:
            import struct
            import wave

            with wave.open(tmp_path, "w") as wav_file:
                wav_file.setnchannels(1)
                wav_file.setsampwidth(2)
                wav_file.setframerate(16000)
                # Brief silent tone
                data = struct.pack("<" + ("h" * 1600), *([0] * 1600))
                wav_file.writeframes(data)

        with open(tmp_path, "rb") as f:
            wav_bytes = f.read()
        return wav_bytes
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)


# ==============================================================================
# 📡 Health & Discovery Endpoints
# ==============================================================================


@app.get("/")
@app.get("/health")
@app.get("/api/ping")
def ping_voice_node():
    """Health check endpoint for Node 1 to test connectivity."""
    has_whisper = False
    try:
        import faster_whisper
        has_whisper = True
    except ImportError:
        pass

    has_piper = bool(shutil.which("piper") or shutil.which("piper-tts"))
    has_espeak = bool(shutil.which("espeak-ng") or shutil.which("espeak"))

    return {
        "status": "ok",
        "service": "voice-capability",
        "node": "Node 2 (High Performance AI Worker)",
        "capabilities": {
            "stt": "faster-whisper" if has_whisper else "unavailable",
            "tts_primary": "piper" if has_piper else "not installed",
            "tts_fallback": "espeak-ng" if has_espeak else "not installed",
        },
        "time": time.time(),
    }


# ==============================================================================
# 🎙️ STT: Speech to Text (/api/stt)
# ==============================================================================


def safe_transcribe(stt, audio_path: str):
    """
    Safely transcribes audio using soundfile/numpy float32 directly,
    avoiding PyAV 14.0 metadata_errors keyword bugs.
    """
    try:
        import soundfile as sf
        import numpy as np

        audio_data, sr = sf.read(audio_path, dtype="float32")
        if audio_data.ndim > 1:
            audio_data = audio_data.mean(axis=1)  # stereo to mono

        if sr == 16000:
            return stt.transcribe(audio_data, beam_size=1)
        else:
            # Resample linearly if needed
            duration = len(audio_data) / sr
            target_samples = int(duration * 16000)
            resampled = np.interp(
                np.linspace(0, len(audio_data), target_samples, endpoint=False),
                np.arange(len(audio_data)),
                audio_data,
            ).astype(np.float32)
            return stt.transcribe(resampled, beam_size=1)
    except Exception:
        # Fallback to standard transcribe on file path
        return stt.transcribe(audio_path, beam_size=1)


@app.post("/api/stt")
async def speech_to_text(file: UploadFile = File(...)):
    """Transcribes an uploaded audio file (WAV, MP3, M4A, OGG) to text."""
    stt = get_stt_model()
    if not stt:
        raise HTTPException(
            status_code=500, detail="Faster-Whisper is not initialized on this node."
        )

    with tempfile.NamedTemporaryFile(suffix=Path(file.filename or "audio.wav").suffix, delete=False) as tmp_file:
        content = await file.read()
        tmp_file.write(content)
        tmp_path = tmp_file.name

    try:
        start_time = time.time()
        segments, info = safe_transcribe(stt, tmp_path)
        text = " ".join([seg.text.strip() for seg in segments]).strip()
        elapsed = round((time.time() - start_time) * 1000, 2)

        return {
            "success": True,
            "text": text,
            "language": getattr(info, "language", "en"),
            "language_probability": round(getattr(info, "language_probability", 1.0), 2),
            "latency_ms": elapsed,
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Transcription failed: {str(e)}")
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)


# ==============================================================================
# 🔊 TTS: Text to Speech (/api/tts)
# ==============================================================================


@app.post("/api/tts")
async def text_to_speech(text: str = Form(...), voice: Optional[str] = Form(None)):
    """Converts input text into spoken audio WAV stream."""
    if not text.strip():
        raise HTTPException(status_code=400, detail="Text cannot be empty.")

    start_time = time.time()
    wav_bytes = generate_speech_wav(text, voice=voice)
    elapsed = round((time.time() - start_time) * 1000, 2)

    return Response(
        content=wav_bytes,
        media_type="audio/wav",
        headers={
            "X-Latency-Ms": str(elapsed),
            "Content-Disposition": "inline; filename=speech.wav",
        },
    )


# ==============================================================================
# ⚡ Full Voice Call Pipeline (/api/process_speech)
# ==============================================================================


@app.post("/api/process_speech")
async def process_speech_pipeline(
    file: UploadFile = File(...),
    ollama_url: str = Form("http://localhost:11434"),
    model: str = Form("llama3:8b"),
    system_prompt: str = Form(
        "You are Lumir, a friendly and concise AI assistant on IntraChat voice call. Reply in 1-2 short, conversational sentences."
    ),
):
    """
    End-to-End Voice Dialogue Pipeline:
    1. STT: Transcribe user audio
    2. LLM: Query Ollama for reply
    3. TTS: Synthesize reply into spoken audio
    """
    stt = get_stt_model()
    if not stt:
        raise HTTPException(status_code=500, detail="STT model unavailable.")

    # 1. Transcribe
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp_file:
        tmp_file.write(await file.read())
        tmp_path = tmp_file.name

    try:
        segments, _ = safe_transcribe(stt, tmp_path)
        user_text = " ".join([seg.text.strip() for seg in segments]).strip()
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)

    if not user_text:
        return JSONResponse(
            content={"success": False, "message": "No speech detected."}, status_code=400
        )

    # 2. Query LLM
    reply_text = ""
    try:
        payload = {
            "model": model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_text},
            ],
            "stream": False,
        }
        res = requests.post(f"{ollama_url.rstrip('/')}/api/chat", json=payload, timeout=20)
        if res.status_code == 200:
            reply_text = res.json().get("message", {}).get("content", "").strip()
    except Exception as e:
        reply_text = f"I heard you, but could not reach Ollama: {e}"

    if not reply_text:
        reply_text = "I received your message, but the AI response was empty."

    # 3. Synthesize Voice
    wav_bytes = generate_speech_wav(reply_text)

    # Return speech audio with metadata in headers
    import urllib.parse
    return Response(
        content=wav_bytes,
        media_type="audio/wav",
        headers={
            "X-User-Text": urllib.parse.quote(user_text),
            "X-Reply-Text": urllib.parse.quote(reply_text),
            "Content-Disposition": "inline; filename=reply.wav",
        },
    )


# ==============================================================================
# 🚀 Entrypoint
# ==============================================================================

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Voice-Capability Microservice (Node 2)")
    parser.add_argument("--host", default="0.0.0.0", help="Host interface to bind")
    parser.add_argument("--port", type=int, default=8005, help="Port to listen on")
    args = parser.parse_args()

    print(f"🎙️ Starting Voice-Capability Node on http://{args.host}:{args.port}")
    uvicorn.run(app, host=args.host, port=args.port)
