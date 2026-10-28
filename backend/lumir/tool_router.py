import re
from typing import Optional, Dict, Any, Tuple

# Pre-compiled high-speed regex patterns for instant tool routing (Zero compute delay!)
YOUTUBE_REGEX = re.compile(
    r'(https?://(?:www\.)?(?:youtube\.com/watch\?v=|youtu\.be/|youtube\.com/shorts/)[a-zA-Z0-9_\-]+)',
    re.IGNORECASE
)

# Server status intent keywords (Hinglish + English)
SERVER_TRIGGERS = [
    "server status", "server kaisa hai", "cpu load", "ram check", "ram kitni",
    "disk usage", "tank status", "tank kaisa chal raha", "server pe load",
    "system status", "server health", "ram kitna use ho raha", "server check kar"
]

# Weather intent keywords
WEATHER_TRIGGERS = [
    "mausam kaisa hai", "aaj ka mausam", "weather kaisa hai", "barish hogi kya",
    "temperature kitna hai", "kitni garmi hai", "kitni sardi hai", "aaj barish hogi",
    "live weather", "aaj ka temperature", "weather batao", "mausam kaisa rahega"
]

# OCR intent keywords
OCR_TRIGGERS = [
    "text nikal", "ocr kar", "extract text", "photo me kya likha hai",
    "likha hua padh", "text padh ke bata", "read text", "is image ka text"
]

def resolve_autonomous_tool_intent(text: str, has_image_in_context: bool = False) -> Optional[Dict[str, Any]]:
    """
    Fast Intent Resolver for Autonomous Tool Calling:
    Analyzes natural speech in Hindi, English, and Hinglish.
    Returns matched tool action and arguments, or None if it's natural conversation.
    """
    clean_text = text.lower().strip()

    # 1. YouTube Link Auto-Detection
    # Agar user ne natural sentence me koi YouTube link bheja ho:
    # E.g. "bhai ye video download kar de https://youtu.be/xyz"
    yt_match = YOUTUBE_REGEX.search(text)
    if yt_match:
        link = yt_match.group(1)
        return {
            "tool": "youtube_options",
            "link": link
        }

    # 2. Server Status Natural Query
    if any(trigger in clean_text for trigger in SERVER_TRIGGERS):
        return {
            "tool": "server_status"
        }

    # 3. Weather Natural Query
    if any(trigger in clean_text for trigger in WEATHER_TRIGGERS):
        return {
            "tool": "weather"
        }

    # 4. OCR Natural Query (jab image available ho)
    if has_image_in_context and any(trigger in clean_text for trigger in OCR_TRIGGERS):
        return {
            "tool": "ocr"
        }

    return None
