import asyncio
import json
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


@router.post("/voice_turn")
async def lumir_voice_turn(
    file: UploadFile = File(...),
    sender: str = Form("User"),
    lat: Optional[float] = Form(None),
    lon: Optional[float] = Form(None),
):
    """
    Handles a single voice turn in a live Lumir call:
    1. Sends user audio to Node 2 (Voice-Capability) for Faster-Whisper STT
    2. Passes transcribed text to Lumir Engine (History + Memory + LLM)
    3. Sends Lumir reply to Node 2 for Piper/eSpeak TTS
    4. Returns synthesized voice audio stream (WAV) with text headers
    """
    ai_cfg = get_ai_config()
    voice_node_url = ai_cfg.get("voice_node_url", "http://localhost:8005").rstrip("/")

    # 1. Read uploaded audio
    audio_bytes = await file.read()
    if not audio_bytes:
        raise HTTPException(status_code=400, detail="Empty audio received.")

    # 2. STT via Node 2
    user_text = ""
    try:
        files = {
            "file": (
                file.filename or "speech.wav",
                audio_bytes,
                file.content_type or "audio/wav",
            )
        }
        stt_res = requests.post(f"{voice_node_url}/api/stt", files=files, timeout=15)
        if stt_res.status_code == 200:
            user_text = stt_res.json().get("text", "").strip()
        else:
            print(f"⚠️ Node 2 STT returned status {stt_res.status_code}: {stt_res.text}")
    except Exception as e:
        print(f"❌ Failed to reach Node 2 STT ({voice_node_url}): {e}")
        raise HTTPException(
            status_code=502, detail=f"Voice Node (Node 2) unreachable: {e}"
        )

    if not user_text:
        return Response(
            content=b"",
            status_code=204,
            headers={
                "X-User-Text": "",
                "X-Reply-Text": urllib.parse.quote("No speech detected."),
            },
        )

    print(f"🎙️ [VOICE CALL] {sender} said: '{user_text}'")

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

    # 5. Synthesize voice via Node 2 TTS
    wav_bytes = b""
    try:
        tts_res = requests.post(
            f"{voice_node_url}/api/tts", data={"text": reply_text}, timeout=15
        )
        if tts_res.status_code == 200:
            wav_bytes = tts_res.content
        else:
            print(f"⚠️ Node 2 TTS error {tts_res.status_code}: {tts_res.text}")
    except Exception as e:
        print(f"❌ Node 2 TTS failed: {e}")

    if not wav_bytes:
        raise HTTPException(
            status_code=500, detail="Voice synthesis failed on Node 2."
        )

    # 6. Stream speech audio back to phone
    return Response(
        content=wav_bytes,
        media_type="audio/wav",
        headers={
            "X-User-Text": urllib.parse.quote(user_text),
            "X-Reply-Text": urllib.parse.quote(reply_text),
            "Content-Disposition": "inline; filename=lumir_voice.wav",
        },
    )
