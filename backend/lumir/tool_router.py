import re
import json
import requests
from typing import Optional, Dict, Any

# Pre-compiled high-speed regex patterns for instant tool routing
YOUTUBE_REGEX = re.compile(
    r'(https?://(?:www\.)?(?:youtube\.com/watch\?v=|youtu\.be/|youtube\.com/shorts/)[a-zA-Z0-9_\-]+)',
    re.IGNORECASE
)

# Common typo-tolerant regex / triggers for weather
WEATHER_REGEX = re.compile(r'\b(m[ao]usam|mosam|weather|barish|barsat|temperature|hawa|humidity)\b', re.IGNORECASE)

# Server status triggers
SERVER_REGEX = re.compile(r'\b(server|tank|cpu|ram|disk|system status|health)\b', re.IGNORECASE)

# OCR triggers
OCR_REGEX = re.compile(r'\b(ocr|text nikal|extract text|padh|padho|read text)\b', re.IGNORECASE)

# Node 2 llama.cpp default endpoint
DEFAULT_NODE2_URL = "http://192.168.31.94:8080"


def query_node2_qwen(text: str, node2_url: str = DEFAULT_NODE2_URL, timeout_sec: float = 1.2) -> Optional[Dict[str, Any]]:
    """
    Corpus Callosum: Queries Node 2 (Qwen 0.5B via llama.cpp) to classify complex/ambiguous intent.
    Ultra-low latency timeout (1.2s max). If Node 2 is offline, fails gracefully to None.
    """
    if not node2_url:
        return None

    system_prompt = """You are a zero-delay intent classifier for an AI assistant.
Classify the user message into ONE of these actions:
- "weather" (asking about weather, rain, heat, temperature, mosam)
- "server_status" (asking about CPU, RAM, server load, tank health)
- "ocr" (asking to extract/read text from image)
- "chat" (regular conversation, feelings, questions, chit-chat)

Reply strictly in JSON: {"action": "weather"|"server_status"|"ocr"|"chat"}"""

    payload = {
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": text}
        ],
        "temperature": 0.1,
        "max_tokens": 20,
        "response_format": {"type": "json_object"}
    }

    try:
        url = f"{node2_url.rstrip('/')}/v1/chat/completions"
        resp = requests.post(url, json=payload, timeout=timeout_sec)
        if resp.status_code == 200:
            content = resp.json().get("choices", [{}])[0].get("message", {}).get("content", "").strip()
            data = json.loads(content)
            action = data.get("action", "chat")
            if action in ["weather", "server_status", "ocr"]:
                print(f"🧠 [CORPUS CALLOSUM - NODE 2]: Qwen classified intent -> {action}")
                return {"tool": action}
    except Exception as e:
        # Node 2 ping fail / offline: silent fallback
        pass

    return None


def resolve_autonomous_tool_intent(
    text: str,
    has_image_in_context: bool = False,
    node2_url: str = DEFAULT_NODE2_URL
) -> Optional[Dict[str, Any]]:
    """
    Fast Intent Resolver for Autonomous Tool Calling:
    1. Instant Regex / Pattern Match (0ms latency, handles typos like 'mosam', 'hawa')
    2. YouTube link extractor
    3. Corpus Callosum fallback: Query Node 2 (Qwen 0.5B) for natural nuances
    4. If Node 2 is offline -> Safely falls back to Normal Chat.
    """
    clean_text = text.lower().strip()

    # 1. YouTube Link Auto-Detection
    yt_match = YOUTUBE_REGEX.search(text)
    if yt_match:
        return {
            "tool": "youtube_options",
            "link": yt_match.group(1)
        }

    # 2. High-Confidence Local Heuristics (Instant 0ms)
    # Weather check (handles mausam, mosam, barish, hawa, etc.)
    if WEATHER_REGEX.search(clean_text) and any(w in clean_text for w in ["kaisa", "kya", "bata", "aaj", "kitn", "hogi", "chal"]):
        return {"tool": "weather"}

    # Server check (handles server, tank, cpu, ram)
    if SERVER_REGEX.search(clean_text) and any(w in clean_text for w in ["status", "load", "check", "kaisa", "kitn", "health", "usage"]):
        return {"tool": "server_status"}

    # OCR check
    if has_image_in_context and OCR_REGEX.search(clean_text):
        return {"tool": "ocr"}

    # 3. Corpus Callosum (Node 2 Qwen Decision Engine)
    # If text is non-trivial and didn't match strict keywords, let Qwen evaluate
    if len(clean_text.split()) >= 3 and not clean_text.startswith("/"):
        qwen_decision = query_node2_qwen(text, node2_url=node2_url)
        if qwen_decision:
            if qwen_decision["tool"] == "ocr" and not has_image_in_context:
                return None
            return qwen_decision

    return None
