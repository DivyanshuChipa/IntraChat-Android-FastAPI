import asyncio
import json
import logging
from datetime import datetime, timedelta, timezone

import httpx

from chat import connected_clients, send_to_user
from lumir.ai_engine import ask_ai
from messages import create_delivery_entries, save_message
from users import get_ai_config, get_all_users, get_default_location

LOGGER = logging.getLogger(__name__)

IST = timezone(timedelta(hours=5, minutes=30))
DEFAULT_LAT = 26.2183
DEFAULT_LON = 78.1828


def _get_default_coordinates() -> tuple[float, float]:
    try:
        return get_default_location()
    except Exception:
        LOGGER.debug("Weather sentinel could not read configured coordinates.", exc_info=True)
        return DEFAULT_LAT, DEFAULT_LON


async def _broadcast_family_warning(message_text: str) -> None:
    timestamp = int(datetime.now(IST).timestamp() * 1000)
    payload = {
        "type": "text",
        "text": message_text,
        "sender": "Lumir",
        "receiver": "Family Group",
        "timestamp": timestamp,
    }

    msg_id = save_message(
        text=message_text,
        sender="Lumir",
        receiver="Family Group",
        msg_type="text",
    )

    recipients = [u["username"] for u in get_all_users() if u.get("username") != "Lumir"]
    if recipients:
        create_delivery_entries(msg_id, recipients)

    payload_json = json.dumps(payload)
    for username in list(connected_clients.keys()):
        await send_to_user(username, payload_json)


async def check_morning_weather() -> None:
    try:
        lat, lon = _get_default_coordinates()
        location = f"{lat:.4f}, {lon:.4f}"

        async with httpx.AsyncClient(timeout=15.0) as client:
            response = await client.get(
                "https://api.open-meteo.com/v1/forecast",
                params={
                    "latitude": lat,
                    "longitude": lon,
                    "daily": "precipitation_sum,temperature_2m_max",
                    "timezone": "Asia/Kolkata",
                    "forecast_days": 1,
                },
            )
            response.raise_for_status()
            forecast = response.json().get("daily", {})

        precipitation_values = forecast.get("precipitation_sum") or []
        max_temp_values = forecast.get("temperature_2m_max") or []

        precipitation_sum = float(precipitation_values[0]) if precipitation_values else 0.0
        temperature_2m_max = float(max_temp_values[0]) if max_temp_values else 0.0

        alert_reason = None
        if precipitation_sum > 2.0:
            alert_reason = f"{precipitation_sum:.1f}mm rain"
        elif temperature_2m_max > 40.0:
            alert_reason = f"{temperature_2m_max:.1f}°C heat"

        if not alert_reason:
            return

        hidden_prompt = (
            "System Context: Aaj {location} mein {alert_reason} hone wali hai. "
            "Act like a caring family member and write a warning message for the Family Group. "
            "Keep it under 15 words. Do NOT use markdown. Say good morning."
        ).format(location=location, alert_reason=alert_reason)

        ai_config = get_ai_config()
        generated_message = await asyncio.to_thread(
            ask_ai,
            prompt=hidden_prompt,
            config=ai_config,
            history=[],
            sender="System",
        )

        await _broadcast_family_warning((generated_message or "Good morning. Please stay safe today.").strip())

    except Exception:
        LOGGER.debug("Weather sentinel failed during scheduled execution.", exc_info=True)
