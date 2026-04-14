import asyncio
import json
import logging
import os
from datetime import datetime, timedelta, timezone
from typing import Tuple

import httpx

from chat import connected_clients, send_to_user
from lumir.ai_engine import ask_ai
from messages import create_delivery_entries, save_message
from users import get_ai_config, get_all_users, get_default_location

LOGGER = logging.getLogger(__name__)

IST = timezone(timedelta(hours=5, minutes=30))
DEFAULT_LAT = 26.2183
DEFAULT_LON = 78.1828
WEATHER_CACHE_FILE = os.path.join(os.path.dirname(__file__), "weather_cache.json")

# Default thresholds (future-ready for admin-config driven values)
TEMP_ALERT_THRESHOLD_C = 42.0
WIND_ALERT_THRESHOLD_KMH = 40.0
RAIN_ALERT_THRESHOLD_MM = 10.0



def _get_default_coordinates() -> Tuple[float, float]:
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


def _load_weather_cache() -> dict:
    try:
        if not os.path.exists(WEATHER_CACHE_FILE):
            return {}
        with open(WEATHER_CACHE_FILE, "r", encoding="utf-8") as fp:
            return json.load(fp)
    except Exception:
        LOGGER.debug("Could not read weather cache.", exc_info=True)
        return {}


def _save_weather_cache(cache: dict) -> None:
    try:
        with open(WEATHER_CACHE_FILE, "w", encoding="utf-8") as fp:
            json.dump(cache, fp, ensure_ascii=False)
    except Exception:
        LOGGER.debug("Could not write weather cache.", exc_info=True)


def _find_hourly_index_for_now(hourly_times: list, now_ist: datetime) -> int:
    now_hour = now_ist.replace(minute=0, second=0, microsecond=0).isoformat()
    for idx, ts in enumerate(hourly_times):
        if ts == now_hour:
            return idx
    return -1


def _compose_contextual_prompt(location: str, reasons: list, start_hour: int, end_hour: int) -> str:
    reasons_text = ", ".join(reasons)
    return (
        "System Context: Aaj {location} mein {start_hour}:00 se {end_hour}:00 ke beech "
        "{reasons} expected hai. Act like a caring family member and write a concise warning "
        "for the Family Group in natural Hinglish. Keep it under 20 words. Do NOT use markdown."
    ).format(
        location=location,
        start_hour=start_hour,
        end_hour=end_hour,
        reasons=reasons_text,
    )


async def refresh_weather_cache(force: bool = False) -> None:
    lat, lon = _get_default_coordinates()
    location = f"{lat:.4f}, {lon:.4f}"
    now_ist = datetime.now(IST)

    cache = _load_weather_cache()
    cache_date = cache.get("cache_date")
    today = now_ist.date().isoformat()
    if not force and cache_date == today and cache.get("hourly"):
        return

    async with httpx.AsyncClient(timeout=15.0) as client:
        response = await client.get(
            "https://api.open-meteo.com/v1/forecast",
            params={
                "latitude": lat,
                "longitude": lon,
                "hourly": "temperature_2m,precipitation,wind_speed_10m,uv_index",
                "daily": "precipitation_sum,temperature_2m_max,wind_speed_10m_max",
                "timezone": "Asia/Kolkata",
                "forecast_days": 1,
            },
        )
        response.raise_for_status()
        payload = response.json()

    daily = payload.get("daily", {})
    precipitation_sum = float((daily.get("precipitation_sum") or [0.0])[0] or 0.0)
    max_temp = float((daily.get("temperature_2m_max") or [0.0])[0] or 0.0)
    max_wind = float((daily.get("wind_speed_10m_max") or [0.0])[0] or 0.0)
    danger_day = (
        precipitation_sum >= RAIN_ALERT_THRESHOLD_MM
        or max_temp >= TEMP_ALERT_THRESHOLD_C
        or max_wind >= WIND_ALERT_THRESHOLD_KMH
    )

    _save_weather_cache(
        {
            "cache_date": today,
            "cached_at": now_ist.isoformat(),
            "location": location,
            "danger_day": danger_day,
            "hourly": payload.get("hourly", {}),
            "daily": daily,
        }
    )


async def run_intraday_weather_refresh() -> None:
    try:
        cache = _load_weather_cache()
        if not cache.get("danger_day"):
            return
        await refresh_weather_cache(force=True)
    except Exception:
        LOGGER.debug("Intraday weather cache refresh failed.", exc_info=True)


async def run_hourly_weather_guard() -> None:
    try:
        await refresh_weather_cache(force=False)
        cache = _load_weather_cache()
        hourly = cache.get("hourly") or {}
        times = hourly.get("time") or []
        if not times:
            return

        now_ist = datetime.now(IST)
        start_idx = _find_hourly_index_for_now(times, now_ist)
        if start_idx < 0:
            return

        end_idx = min(start_idx + 3, len(times))
        temperatures = hourly.get("temperature_2m") or []
        precipitations = hourly.get("precipitation") or []
        wind_speeds = hourly.get("wind_speed_10m") or []

        peak_temp = max([float(v or 0.0) for v in temperatures[start_idx:end_idx]] or [0.0])
        peak_rain = max([float(v or 0.0) for v in precipitations[start_idx:end_idx]] or [0.0])
        peak_wind = max([float(v or 0.0) for v in wind_speeds[start_idx:end_idx]] or [0.0])

        reasons = []
        if peak_temp >= TEMP_ALERT_THRESHOLD_C:
            reasons.append(f"temperature {peak_temp:.1f}°C")
        if peak_wind >= WIND_ALERT_THRESHOLD_KMH:
            reasons.append(f"wind {peak_wind:.1f} km/h")
        if peak_rain >= RAIN_ALERT_THRESHOLD_MM:
            reasons.append(f"rain {peak_rain:.1f} mm")

        if not reasons:
            return

        # Anti-spam: one alert per 3-hour window with same reason signature.
        reason_key = "|".join(sorted(reasons))
        window_key = f"{now_ist.date().isoformat()}-{now_ist.hour}-{reason_key}"
        if cache.get("last_alert_window") == window_key:
            return

        prompt = _compose_contextual_prompt(
            location=cache.get("location", "your area"),
            reasons=reasons,
            start_hour=now_ist.hour,
            end_hour=(now_ist + timedelta(hours=3)).hour,
        )
        ai_config = get_ai_config()
        generated_message = await asyncio.to_thread(
            ask_ai,
            prompt=prompt,
            config=ai_config,
            history=[],
            sender="Family Group",
        )
        await _broadcast_family_warning((generated_message or "Please stay safe. Weather may worsen soon.").strip())

        cache["last_alert_window"] = window_key
        _save_weather_cache(cache)
    except Exception:
        LOGGER.debug("Hourly weather guard failed.", exc_info=True)


async def run_proactive_weather_sentinel() -> None:
    try:
        await refresh_weather_cache(force=True)
        await run_hourly_weather_guard()

    except Exception:
        LOGGER.debug("Weather sentinel failed during scheduled execution.", exc_info=True)
