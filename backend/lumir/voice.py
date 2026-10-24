import asyncio
import json
import os
import shutil
import subprocess
import tempfile
import urllib.parse
from datetime import datetime, timezone, timedelta
from typing import Optional

import requests
from fastapi import APIRouter, File, Form, HTTPException, Response, UploadFile

from lumir.engine import lumir_engine
from messages import create_delivery_entries, save_message
from users import get_ai_config

router = APIRouter(prefix="/api/lumir", tags=["Lumir Voice"])
IST = timezone(timedelta(hours=5, minutes=30))

# ================= NODE 1 LOCAL FALLBACK ENGINE =================
_local_whisper_model = None


def get_local_whisper():
    """Lazy load Faster-Whisper (tiny) on Node 1 only if Node 2 is offline."""
    global _local_whisper_model
    if _local_whisper_model is None:
        try:
            from faster_whisper import WhisperModel

            print("🔄 [FALLBACK] Loading Faster-Whisper (tiny) on Node 1 CPU (int8)...")
            _local_whisper_model = WhisperModel(
                "tiny", device="cpu", compute_type="int8"
            )
            print("✅ [FALLBACK] Faster-Whisper (tiny) ready on Node 1!")
        except Exception as e:
            print(f"⚠️ [FALLBACK] faster-whisper not available on Node 1: {e}")
            return None
    return _local_whisper_model


def local_transcribe(audio_bytes: bytes) -> str:
    """Fallback STT: Runs faster-whisper tiny on Node 1 safely avoiding PyAV metadata_errors bug."""
    model = get_local_whisper()
    if not model:
        return ""
    try:
        import numpy as np

        audio_data = None
        # 1. Try reading via soundfile if present
        try:
            import io
            import soundfile as sf

            audio_data, sr = sf.read(io.BytesIO(audio_bytes), dtype="float32")
            if audio_data.ndim > 1:
                audio_data = audio_data.mean(axis=1)
            if sr != 16000:
                duration = len(audio_data) / sr
                target_samples = int(duration * 16000)
                audio_data = np.interp(
                    np.linspace(0, len(audio_data), target_samples, endpoint=False),
                    np.arange(len(audio_data)),
                    audio_data,
                ).astype(np.float32)
        except Exception:
            pass

        # 2. Direct PCM16 16kHz WAV byte decoding (100% immune to PyAV bugs)
        if audio_data is None and len(audio_bytes) > 44:
            try:
                raw_samples = audio_bytes[44:]
                int16_arr = np.frombuffer(raw_samples, dtype=np.int16)
                audio_data = int16_arr.astype(np.float32) / 32768.0
            except Exception:
                pass

        if audio_data is not None and len(audio_data) > 0:
            segments, _ = model.transcribe(audio_data, beam_size=1)
            return " ".join(seg.text for seg in segments).strip()

        # 3. Fallback to file path transcribe if numpy decoding failed
        tmp_path = ""
        try:
            with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
                tmp.write(audio_bytes)
                tmp_path = tmp.name
            segments, _ = model.transcribe(tmp_path, beam_size=1)
            return " ".join(seg.text for seg in segments).strip()
        finally:
            if tmp_path and os.path.exists(tmp_path):
                try:
                    os.remove(tmp_path)
                except Exception:
                    pass
    except Exception as e:
        print(f"⚠️ [FALLBACK] Local STT error: {e}")
        return ""


def local_tts(text: str) -> bytes:
    """Fallback TTS: Uses native espeak-ng / espeak on Node 1 (0ms startup, 0MB RAM)."""
    cmd_name = (
        "espeak-ng"
        if shutil.which("espeak-ng")
        else ("espeak" if shutil.which("espeak") else None)
    )
    if not cmd_name:
        print("⚠️ No local espeak / espeak-ng found on Node 1.")
        return b""
    tmp_path = ""
    try:
        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
            tmp_path = tmp.name
        clean_text = text.replace('"', '\\"').replace("\n", " ").strip()
        subprocess.run([cmd_name, "-w", tmp_path, clean_text], check=True, timeout=8)
        with open(tmp_path, "rb") as f:
            return f.read()
    except Exception as e:
        print(f"⚠️ [FALLBACK] Local TTS error: {e}")
        return b""
    finally:
        if tmp_path and os.path.exists(tmp_path):
            try:
                os.remove(tmp_path)
            except Exception:
                pass


# ================= MAIN VOICE TURN ENDPOINT =================
@router.post("/voice_turn")
async def lumir_voice_turn(
    file: UploadFile = File(...),
    sender: str = Form("User"),
    lat: Optional[float] = Form(None),
    lon: Optional[float] = Form(None),
):
    """
    Handles a single voice turn in a live Lumir call:
    1. Primary: Sends audio to Node 2 (Voice-Capability) for Faster-Whisper Base + Piper TTS
    2. Fallback: If Node 2 is offline, automatically uses Node 1 local (Tiny Whisper + eSpeak)
    3. Passes transcribed text to Lumir Engine (History + Memory + LLM)
    4. Returns synthesized voice audio stream (WAV) with text headers
    """
    ai_cfg = get_ai_config()
    voice_node_url = ai_cfg.get("voice_node_url", "http://localhost:8005").rstrip("/")
    voice_node_enabled = ai_cfg.get("voice_node_enabled", True)

    # 1. Read uploaded audio
    audio_bytes = await file.read()
    if not audio_bytes:
        raise HTTPException(status_code=400, detail="Empty audio received.")

    # 2. STT: Try Node 2 first, then auto-failover to Node 1 local tiny
    user_text = ""
    engine_used = "Node-2"

    if voice_node_enabled and voice_node_url:
        try:
            files = {
                "file": (
                    file.filename or "speech.wav",
                    audio_bytes,
                    file.content_type or "audio/wav",
                )
            }
            stt_res = requests.post(
                f"{voice_node_url}/api/stt", files=files, timeout=7
            )
            if stt_res.status_code == 200:
                user_text = stt_res.json().get("text", "").strip()
            else:
                print(
                    f"⚠️ Node 2 STT returned status {stt_res.status_code}: {stt_res.text}"
                )
        except Exception as e:
            print(
                f"⚠️ Node 2 STT unreachable ({e}), switching to Node 1 local fallback..."
            )

    # Fallback to Node 1 local STT if Node 2 did not provide text
    if not user_text:
        engine_used = "Node-1-Fallback"
        print("🔄 [FALLBACK] Running local Faster-Whisper (tiny) on Node 1...")
        user_text = await asyncio.to_thread(local_transcribe, audio_bytes)
        if user_text:
            print(f"✅ [FALLBACK] Node 1 transcribed: '{user_text}'")

    if not user_text:
        return Response(
            content=b"",
            status_code=204,
            headers={
                "X-User-Text": "",
                "X-Reply-Text": urllib.parse.quote("No speech detected."),
                "X-Voice-Engine": engine_used,
            },
        )

    print(f"🎙️ [VOICE CALL] ({engine_used}) {sender} said: '{user_text}'")

    # 3. Process with Lumir Brain (History + Memory + LLM)
    bot_res = await asyncio.to_thread(
        lumir_engine.process,
        text=user_text,
        file_url=None,
        sender=sender,
        lat=lat,
        lon=lon,
    )
    reply_text = bot_res.get("text", "").strip() or "I received your message."
    print(f"🤖 [VOICE CALL] Lumir reply: '{reply_text}'")

    # 4. Save both user turn and Lumir reply to chat history
    try:
        save_message(text=user_text, sender=sender, receiver="Lumir", msg_type="text")
        reply_id = save_message(
            text=reply_text, sender="Lumir", receiver=sender, msg_type="text"
        )
        create_delivery_entries(reply_id, [sender])

        # Import send_to_user dynamically to avoid circular import
        from chat import send_to_user

        ws_payload = json.dumps(
            {
                "type": "text",
                "text": reply_text,
                "sender": "Lumir",
                "receiver": sender,
                "timestamp": int(datetime.now(IST).timestamp() * 1000),
            }
        )
        asyncio.create_task(send_to_user(sender, ws_payload))
    except Exception as e:
        print(f"⚠️ Failed to save voice call messages to DB: {e}")

    # 5. Synthesize voice: Try Node 2 first, then auto-failover to Node 1 local espeak
    wav_bytes = b""
    if engine_used == "Node-2" and voice_node_enabled and voice_node_url:
        try:
            tts_res = requests.post(
                f"{voice_node_url}/api/tts", data={"text": reply_text}, timeout=8
            )
            if tts_res.status_code == 200:
                wav_bytes = tts_res.content
            else:
                print(f"⚠️ Node 2 TTS error {tts_res.status_code}: {tts_res.text}")
        except Exception as e:
            print(f"⚠️ Node 2 TTS unreachable ({e}), falling back to local espeak...")

    # Fallback to local espeak on Node 1
    if not wav_bytes:
        engine_used = "Node-1-Fallback"
        print("🔄 [FALLBACK] Synthesizing speech via Node 1 local espeak...")
        wav_bytes = await asyncio.to_thread(local_tts, reply_text)

    if not wav_bytes:
        raise HTTPException(
            status_code=500, detail="Voice synthesis failed on both Node 2 and Node 1."
        )

    # 6. Stream speech audio back to phone
    return Response(
        content=wav_bytes,
        media_type="audio/wav",
        headers={
            "X-User-Text": urllib.parse.quote(user_text),
            "X-Reply-Text": urllib.parse.quote(reply_text),
            "X-Voice-Engine": engine_used,
            "Content-Disposition": "inline; filename=lumir_voice.wav",
        },
    )
