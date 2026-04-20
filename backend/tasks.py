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
from users import get_ai_config, get_all_users, get_default_location, get_environment_settings

LOGGER = logging.getLogger(__name__)

IST = timezone(timedelta(hours=5, minutes=30))
DEFAULT_LAT = 26.2183
DEFAULT_LON = 78.1828
CACHE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "weather_cache.json")
TRACKER_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "alert_tracker.json")

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


async def sync_weather_data() -> None:
    """
    Fetches the hourly weather data for today and tomorrow and saves it to a local cache.
    Runs at 7 AM. (Also acts as the daily trigger if in Normal mode).
    """
    try:
        lat, lon = _get_default_coordinates()
        
        async with httpx.AsyncClient(timeout=15.0) as client:
            response = await client.get(
                "https://api.open-meteo.com/v1/forecast",
                params={
                    "latitude": lat,
                    "longitude": lon,
                    "hourly": "temperature_2m,precipitation,wind_speed_10m",
                    "timezone": "Asia/Kolkata",
                    "forecast_days": 2, # Today and tomorrow to be safe for late night checks
                },
            )
            response.raise_for_status()
            data = response.json()
            
            # Save to cache
            with open(CACHE_FILE, "w") as f:
                json.dump(data, f)
                
            LOGGER.info("✅ Weather data successfully synced and cached.")
    except Exception as e:
        LOGGER.error(f"Failed to sync weather data: {e}", exc_info=True)

async def hourly_sentinel_check() -> None:
    """
    Runs every hour. Reads the cache.
    If mode is Normal, it only actually alerts at 7 AM (handled by only checking at 7 AM).
    If mode is Auto, checks the NEXT 3 hours for extreme data based on admin settings.
    """
    settings = get_environment_settings()
    if settings.get("sentinel_enabled", "1") == "0":
        LOGGER.debug("Weather Sentinel is disabled. Skipping hourly check.")
        return

    try:
        if not os.path.exists(CACHE_FILE):
            LOGGER.debug("No weather cache found. Skipping hourly check.")
            return

        mode = settings.get("env_mode", "auto")
        
        # If normal mode, and it's not 7 AM, do nothing. (Strictly 7 AM alerts only)
        now_ist = datetime.now(IST)
        if mode == "normal" and now_ist.hour != 7:
            return

        with open(CACHE_FILE, "r") as f:
            data = json.load(f)

        hourly = data.get("hourly", {})
        times = hourly.get("time", [])
        temps = hourly.get("temperature_2m", [])
        rains = hourly.get("precipitation", [])
        winds = hourly.get("wind_speed_10m", [])

        if not times:
            return

        # Find the index corresponding to the current hour
        current_time_str = now_ist.strftime("%Y-%m-%dT%H:00")
        
        try:
            start_index = times.index(current_time_str)
        except ValueError:
            # If exactly current hour is not found, we might have passed the forecast window. 
            # Re-sync could be needed, but we skip for now.
            return

        # Look at the next 3 hours (including current hour)
        end_index = start_index + 3
        
        slice_times = times[start_index:end_index]
        slice_temps = temps[start_index:end_index]
        slice_rains = rains[start_index:end_index]
        slice_winds = winds[start_index:end_index]

        # Check against thresholds
        alert_temp = settings.get("alert_temp", 40.0)
        alert_wind = settings.get("alert_wind", 40.0)
        alert_rain = settings.get("alert_rain", 5.0)

        trigger = False
        reasons = []

        max_t = max(slice_temps) if slice_temps else 0
        if max_t >= alert_temp:
            trigger = True
            reasons.append(f"Temperature hitting {max_t}°C")
            
        max_w = max(slice_winds) if slice_winds else 0
        if max_w >= alert_wind:
            trigger = True
            reasons.append(f"Wind speed hitting {max_w}km/h")
            
        sum_r = sum(slice_rains) if slice_rains else 0
        if sum_r >= alert_rain:
            trigger = True
            reasons.append(f"Heavy rain expected ({sum_r}mm)")

        if not trigger:
            return

        # ---------------------------------------------
        # SMART SPAM PREVENTION: True Rolling Window
        # ---------------------------------------------
        # Compare current time against last exact timestamp to prevent block-boundary race conditions.
        now_ts = now_ist.timestamp()
        reason_key = "|".join(sorted(reasons))
        
        tracker = {}
        if os.path.exists(TRACKER_FILE):
            try:
                with open(TRACKER_FILE, "r") as tf:
                    tracker = json.load(tf)
            except:
                pass
                
        # If we already sent this EXACT reason profile within the last 3 hours (10800 seconds), skip it.
        last_time = tracker.get(reason_key, 0)
        if now_ts - last_time < 10800:
            LOGGER.debug("Identical alert already sent within the last 3 hours. Skipping to prevent spam.")
            return

        # Prepare summary for AI
        summary_lines = []
        for i in range(len(slice_times)):
            t_fmt = slice_times[i].split("T")[1]
            summary_lines.append(f"At {t_fmt}: Temp {slice_temps[i]}°C, Wind {slice_winds[i]}km/h, Rain {slice_rains[i]}mm")
            
        json_slice = " | ".join(summary_lines)
        reason_str = ", ".join(reasons)

        hidden_prompt = (
            f"System Context: The Smart Environment Monitor detected dangerous weather conditions in the next 3 hours: {reason_str}. "
            f"Here is the exact data: {json_slice}. "
            f"Act like a caring family member and write a natural, urgent warning message for the Family Group based on this data. "
            f"Keep it under 25 words. Do NOT use markdown. Do NOT use placeholders."
        )

        ai_config = get_ai_config()
        generated_message = await asyncio.to_thread(
            ask_ai,
            prompt=hidden_prompt,
            config=ai_config,
            history=[],
            sender="System",
        )

        if generated_message:
            await _broadcast_family_warning(generated_message.strip())
            
            # Update tracker
            tracker[reason_key] = now_ts
            with open(TRACKER_FILE, "w") as tf:
                json.dump(tracker, tf)

    except Exception:
        LOGGER.error("Hourly sentinel check failed.", exc_info=True)

