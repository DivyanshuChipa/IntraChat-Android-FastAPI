import httpx
import json
import asyncio
from datetime import datetime
from users import get_default_location, get_ai_config, get_all_users
from messages import save_message
from chat import send_to_user
from lumir.ai_engine import ask_ai

async def check_morning_weather():
    """
    Scheduled job that checks the morning weather and triggers the AI to automatically
    warn the 'Family Group' if extreme conditions exist.
    """
    lat, lon = get_default_location()

    # Open-Meteo API endpoint and parameters
    url = "https://api.open-meteo.com/v1/forecast"
    params = {
        "latitude": lat,
        "longitude": lon,
        "daily": "precipitation_sum,temperature_2m_max",
        "timezone": "auto"
    }

    try:
        async with httpx.AsyncClient() as client:
            response = await client.get(url, params=params, timeout=10.0)
            response.raise_for_status()
            data = response.json()

        daily = data.get("daily", {})
        precipitation_sum = daily.get("precipitation_sum", [0])[0]
        temperature_2m_max = daily.get("temperature_2m_max", [0])[0]

        # Strict filter for alert
        alert_reason = None
        if precipitation_sum > 2.0:
            alert_reason = f"heavy rain ({precipitation_sum}mm)"
        elif temperature_2m_max > 40.0:
            alert_reason = f"extreme heat ({temperature_2m_max}°C)"

        if alert_reason:
            config = get_ai_config()
            system_prompt = (
                f"System Context: Aaj {lat},{lon} mein {alert_reason} hone wali hai. "
                f"Act like a caring family member and write a warning message for the Family Group. "
                f"Keep it under 15 words. Do NOT use markdown. Say good morning."
            )

            # Trigger AI in background (we use the main loop or asyncio thread if needed)
            ai_reply = await asyncio.to_thread(ask_ai, prompt=system_prompt, config=config, history=[], sender="System")

            if ai_reply:
                ai_reply = ai_reply.strip()

                # Save to DB
                msg_id = save_message(
                    text=ai_reply,
                    sender="Lumir",
                    receiver="Family Group",
                    msg_type="text"
                )

                # Broadcast payload
                bot_reply = {
                    "type": "text",
                    "text": ai_reply,
                    "sender": "Lumir",
                    "receiver": "Family Group",
                    "timestamp": int(datetime.now().timestamp() * 1000)
                }

                payload_str = json.dumps(bot_reply)

                all_users = get_all_users()
                for u in all_users:
                    username = u["username"]
                    # Call send_to_user asynchronously
                    await send_to_user(username, payload_str)

    except Exception as e:
        # Silently log network or processing errors
        print(f"Weather Sentinel Error: {e}")
