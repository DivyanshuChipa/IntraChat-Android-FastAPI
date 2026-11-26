import asyncio
import base64
import io
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

from lumir.cloud_providers import get_api_key
from lumir.engine import lumir_engine
from messages import (
    create_delivery_entries,
    mark_message_delivered_for_user,
    save_message,
)
from users import get_ai_config

router = APIRouter(prefix="/api/lumir", tags=["Lumir Voice"])
IST = timezone(timedelta(hours=5, minutes=30))

# ================= SARVAM CLOUD VOICE (Bulbul v4-flash & Saaras v4) =================

def sarvam_transcribe(
    audio_bytes: bytes,
    language_code: str = "hi-IN",
    model: str = "saaras:v4",
    timeout: float = 8.0
) -> Optional[str]:
    """
    Speech-To-Text (STT) via Sarvam AI Saaras v4.
    Highly accurate Indian English, Hindi, and Hinglish vernacular transcription.
    """
    key = get_api_key("sarvam")
    if not key:
        return None
    try:
        headers = {"api-subscription-key": key}
        files = {"file": ("speech.wav", io.BytesIO(audio_bytes), "audio/wav")}
        data = {
            "model": model,
            "language_code": language_code,
            "mode": "transcribe"
        }
        resp = requests.post("https://api.sarvam.ai/speech-to-text", files=files, data=data, headers=headers, timeout=timeout)
        if resp.status_code == 200:
            transcript = resp.json().get("transcript", "").strip()
            if transcript:
                print(f"🎙️ [SARVAM STT]: '{transcript}'")
                return transcript
        else:
            print(f"⚠️ [SARVAM STT ERROR {resp.status_code}]: {resp.text[:120]}")
    except Exception as e:
        print(f"⚠️ [SARVAM STT EXCEPTION]: {e}")
    return None


def sarvam_tts(
    text: str,
    speaker: str = "simran_enhi_companion",
    target_lang: str = "hi-IN",
    model: str = "bulbul:v4-flash",
    timeout: float = 8.0
) -> Optional[bytes]:
    """
    Text-To-Speech (TTS) via Sarvam AI Bulbul v4-flash.
    Produces studio-quality, natural Indian female voice (simran_enhi_companion).
    """
    key = get_api_key("sarvam")
    if not key:
        return None
    try:
        headers = {"api-subscription-key": key, "Content-Type": "application/json"}
        payload = {
            "inputs": [text],
            "target_language_code": target_lang,
            "speaker": speaker,
            "model": model,
            "pace": 1.0,
            "speech_sample_rate": 24000
        }
        resp = requests.post("https://api.sarvam.ai/text-to-speech", json=payload, headers=headers, timeout=timeout)
        if resp.status_code == 200:
            audios = resp.json().get("audios", [])
            if audios and len(audios[0]) > 0:
                audio_bytes = base64.b64decode(audios[0])
                print(f"🔊 [SARVAM TTS]: Synthesized {len(audio_bytes)} bytes using speaker '{speaker}'")
                return audio_bytes
        else:
            print(f"⚠️ [SARVAM TTS ERROR {resp.status_code}]: {resp.text[:120]}")
    except Exception as e:
        print(f"⚠️ [SARVAM TTS EXCEPTION]: {e}")
    return None


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


HINDI_PROMPT = os.getenv("WHISPER_PROMPT", "Hindi aur Hinglish me baat-cheet. Namaste Lumir, kya haal hai, kaise ho, main theek hoon.")

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
            segments, _ = model.transcribe(audio_data, beam_size=1, initial_prompt=HINDI_PROMPT)
            return " ".join(seg.text for seg in segments).strip()

        # 3. Fallback to file path transcribe if numpy decoding failed
        tmp_path = ""
        try:
            with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
                tmp.write(audio_bytes)
                tmp_path = tmp.name
            segments, _ = model.transcribe(tmp_path, beam_size=1, initial_prompt=HINDI_PROMPT)
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


def sanitize_for_tts(text: str) -> str:
    """Strips markdown formatting, asterisks, and emojis for clean, natural voice playback."""
    import re

    # 1. Strip markdown symbols (*, _, #, `, ~, >)
    clean = re.sub(r"[\*_#`~>]", "", text)
    # 2. Strip emojis and unicode pictorial symbols (including sparkles, stars, etc.)
    clean = re.sub(r"[\U00010000-\U0010ffff\u2600-\u27bf]", "", clean)
    # 3. Collapse multiple spaces and clean quotes
    clean = re.sub(r"\s+", " ", clean).strip()
    return clean


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
    1. STT Hierarchy:
       - Tier 1: Sarvam Saaras v4 (Cloud - Indian Vernacular Hindi/Hinglish)
       - Tier 2: Node 2 Faster-Whisper (Port 8005)
       - Tier 3: Node 1 Local Faster-Whisper (Tiny)
    2. Lumir Engine: Conversation & Persona Response
    3. TTS Hierarchy:
       - Tier 1: Sarvam Bulbul v4-flash (simran_enhi_companion)
       - Tier 2: Node 2 Piper TTS (Port 8005)
       - Tier 3: Node 1 Local eSpeak
    """
    ai_cfg = get_ai_config()
    voice_provider = (ai_cfg.get("voice_provider") or "auto").strip().lower()
    voice_speaker = ai_cfg.get("voice_speaker") or "simran_enhi_companion"
    voice_node_url = ai_cfg.get("voice_node_url", "http://localhost:8005").rstrip("/")
    voice_node_enabled = ai_cfg.get("voice_node_enabled", True)
    has_sarvam_key = bool(get_api_key("sarvam"))

    # 1. Read uploaded audio
    audio_bytes = await file.read()
    if not audio_bytes:
        raise HTTPException(status_code=400, detail="Empty audio received.")

    # 2. STT: Multi-Tier Hierarchy
    user_text = ""
    stt_engine_used = "Unknown"

    # Tier 1: Sarvam Saaras v4 (if explicitly selected or auto with key present)
    use_sarvam = (voice_provider == "sarvam") or (voice_provider == "auto" and has_sarvam_key)
    if use_sarvam:
        try:
            user_text = await asyncio.to_thread(sarvam_transcribe, audio_bytes, "hi-IN", "saaras:v4", 7.0)
            if user_text:
                stt_engine_used = "Sarvam-Saaras"
        except Exception as e:
            print(f"⚠️ Sarvam STT failed: {e}, falling back to next tier...")

    # Tier 2: Node 2 Whisper (if user_text still empty)
    if not user_text and voice_node_enabled and voice_node_url:
        try:
            files = {
                "file": (
                    file.filename or "speech.wav",
                    audio_bytes,
                    file.content_type or "audio/wav",
                )
            }
            stt_res = requests.post(f"{voice_node_url}/api/stt", files=files, timeout=7)
            if stt_res.status_code == 200:
                user_text = stt_res.json().get("text", "").strip()
                if user_text:
                    stt_engine_used = "Node-2-Whisper"
            else:
                print(f"⚠️ Node 2 STT returned status {stt_res.status_code}: {stt_res.text}")
        except Exception as e:
            print(f"⚠️ Node 2 STT unreachable ({e}), falling back to Node 1 local...")

    # Tier 3: Node 1 Local Tiny Whisper fallback
    if not user_text:
        stt_engine_used = "Node-1-Local-Whisper"
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
                "X-Voice-Engine": stt_engine_used,
            },
        )

    print(f"🎙️ [VOICE CALL] ({stt_engine_used}) {sender} said: '{user_text}'")

    # 3. Process with Lumir Brain (History + Memory + LLM in Voice Mode)
    bot_res = await asyncio.to_thread(
        lumir_engine.process,
        text=user_text,
        file_url=None,
        sender=sender,
        lat=lat,
        lon=lon,
        is_voice_mode=True,
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

        async def _deliver_voice_reply():
            sent = await send_to_user(sender, ws_payload)
            if sent:
                mark_message_delivered_for_user(reply_id, sender)

        asyncio.create_task(_deliver_voice_reply())
    except Exception as e:
        print(f"⚠️ Failed to save voice call messages to DB: {e}")

    # 5. Clean text for natural spoken TTS (strip emojis & markdown)
    clean_tts_text = sanitize_for_tts(reply_text) or reply_text

    # 6. TTS: Multi-Tier Hierarchy
    wav_bytes = b""
    tts_engine_used = "Unknown"

    # Tier 1: Sarvam Bulbul v4-flash (High fidelity Indian female companion voice)
    if use_sarvam:
        try:
            wav_bytes = await asyncio.to_thread(
                sarvam_tts,
                clean_tts_text,
                voice_speaker,
                "hi-IN",
                "bulbul:v4-flash",
                8.0
            )
            if wav_bytes:
                tts_engine_used = f"Sarvam-Bulbul ({voice_speaker})"
        except Exception as e:
            print(f"⚠️ Sarvam TTS failed: {e}, falling back to next tier...")

    # Tier 2: Node 2 Piper TTS
    if not wav_bytes and voice_node_enabled and voice_node_url:
        try:
            tts_res = requests.post(
                f"{voice_node_url}/api/tts", data={"text": clean_tts_text}, timeout=8
            )
            if tts_res.status_code == 200:
                wav_bytes = tts_res.content
                tts_engine_used = "Node-2-Piper"
            else:
                print(f"⚠️ Node 2 TTS error {tts_res.status_code}: {tts_res.text}")
        except Exception as e:
            print(f"⚠️ Node 2 TTS unreachable ({e}), falling back to local espeak...")

    # Tier 3: Node 1 Local eSpeak fallback
    if not wav_bytes:
        tts_engine_used = "Node-1-Local-eSpeak"
        print("🔄 [FALLBACK] Synthesizing speech via Node 1 local espeak...")
        wav_bytes = await asyncio.to_thread(local_tts, clean_tts_text)

    if not wav_bytes:
        raise HTTPException(
            status_code=500, detail="Voice synthesis failed across all available tiers."
        )

    # 7. Stream speech audio back to phone/browser
    return Response(
        content=wav_bytes,
        media_type="audio/wav",
        headers={
            "X-User-Text": urllib.parse.quote(user_text),
            "X-Reply-Text": urllib.parse.quote(reply_text),
            "X-Voice-Engine": f"{stt_engine_used} -> {tts_engine_used}",
            "Content-Disposition": "inline; filename=lumir_voice.wav",
        },
    )
