import re
from typing import Optional, Dict, Any
from .cognitive_helper import query_cognitive_task

# Pre-compiled high-speed regex patterns for instant tool routing (0ms latency)
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

# ================= THALAMUS SENSORY GATEKEEPER (0ms Latency) =================
# Broad sensory cues (English, Hinglish, and Devanagari Hindi).
# If NONE of these cues appear, Thalamus fast-bypasses the Corpus Callosum LLM call.
THALAMUS_WEATHER_CUES = re.compile(
    r'\b(m[ao]usam|mosam|weather|barish|barsat|rain|temp|temperature|hawa|humidity|garmi|sardi|thandi|dhoop|aandhi|toofan|fog|kohra|climate|forecast)\b'
    r'|(?:मौसम|बारिश|बरसात|गर्मी|सर्दी|ठंड|धूप|तापमान|हवा|आंधी|तूफान)',
    re.IGNORECASE,
)

THALAMUS_SERVER_CUES = re.compile(
    r'\b(server|tank|cpu|ram|disk|memory|storage|system|health|load|usage|uptime|process|battery|pc|laptop|machine|node)\b'
    r'|(?:सर्वर|सिस्टम|लोड|मेमोरी|सीपीयू|रैम|स्टोरेज|टैंक|हेल्थ)',
    re.IGNORECASE,
)

THALAMUS_OCR_CUES = re.compile(
    r'\b(ocr|text|extract|padh|padho|read|likha|scan|transcribe|translate)\b'
    r'|(?:लिखा|पढ़|पढ़ो|टेक्स्ट|निकाल)',
    re.IGNORECASE,
)


def thalamus_needs_tool_routing(clean_text: str, has_image_in_context: bool = False) -> bool:
    """
    Thalamus Sensory Filter:
    Returns True ONLY if the input contains a sensory cue that might require
    tool routing (weather, server_status, or ocr when an image is present).
    Pure conversational turns return False in 0ms, saving an LLM round-trip.
    """
    if THALAMUS_WEATHER_CUES.search(clean_text):
        return True
    if THALAMUS_SERVER_CUES.search(clean_text):
        return True
    if has_image_in_context and THALAMUS_OCR_CUES.search(clean_text):
        return True
    return False


def resolve_autonomous_tool_intent(
    text: str,
    has_image_in_context: bool = False,
    node2_url: str = None,
    config: Optional[dict] = None,
    ask_ai_fn=None
) -> Optional[Dict[str, Any]]:
    """
    Fast Intent Resolver for Autonomous Tool Calling:
    1. Instant Regex / Pattern Match (0ms latency, handles typos like 'mosam', 'hawa')
    2. YouTube link extractor
    3. Corpus Callosum (Node 2 / llama.cpp / Cognitive Worker) for nuanced questions
    4. If Cognitive Node is offline -> Gracefully falls back to normal conversation
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

    # 3. Thalamus Gatekeeper: Skip Corpus Callosum LLM call for pure conversation (0ms)
    if len(clean_text.split()) >= 3 and not clean_text.startswith("/"):
        if not thalamus_needs_tool_routing(clean_text, has_image_in_context=has_image_in_context):
            print("⚡ [THALAMUS]: Pure conversation detected, bypassing Corpus Callosum (0ms).")
            return None

        # 4. Corpus Callosum (Cognitive Worker for ambiguous tool-cue classification)
        system_prompt = """You are a zero-delay intent classifier for an AI assistant.
Classify the user message into ONE of these actions:
- "weather" (asking about weather, rain, heat, temperature, mosam)
- "server_status" (asking about CPU, RAM, server load, tank health)
- "ocr" (asking to extract/read text from image)
- "chat" (regular conversation, feelings, questions, chit-chat)

Reply strictly in JSON: {"action": "weather"|"server_status"|"ocr"|"chat"}"""

        cfg = dict(config or {})
        if node2_url:
            cfg["node2_llama_url"] = node2_url

        data = query_cognitive_task(
            system_prompt=system_prompt,
            user_prompt=text,
            config=cfg,
            max_tokens=60,
            timeout=8.0,
            ask_ai_fn=ask_ai_fn,
            task_name="INTENT_CLASSIFIER"
        )

        if data and isinstance(data, dict):
            action = data.get("action", "chat")
            if action in ["weather", "server_status", "ocr"]:
                if action == "ocr" and not has_image_in_context:
                    return None
                print(f"🧠 [CORPUS CALLOSUM]: Cognitive worker classified intent -> {action}")
                return {"tool": action}

    return None
