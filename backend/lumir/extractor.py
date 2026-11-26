from typing import Optional, Dict
from .cognitive_helper import query_cognitive_task

def extract_synthetic_amygdala_fact(
    text: str,
    ask_ai_fn,
    config: dict,
    sender: str = "User",
    node2_url: str = None
) -> Optional[Dict]:
    """
    Synthetic Amygdala & Hippocampus Feature Extractor:
    - Tier 1: Configured Cognitive Node (llama.cpp / Qwen 0.5B via LAN or Local).
    - Tier 2: Seamless fallback to Main Model (Gemini / Sarvam / Ollama).
    - Generous 10s safe timeout for local compute machines.
    """
    text_clean = text.strip()
    if not text_clean or len(text_clean.split()) < 2:
        return None

    # Fast heuristic filter: ignore pure bot utility commands or pure greetings
    skip_exact = ["hi", "hello", "hey", "bye", "ok", "acha", "hmm", "good morning", "good night", "kya chal raha hai"]
    if text_clean.lower() in skip_exact:
        return None

    system_prompt = """You are the Cognitive Emotion & Memory Analyzer for an AI companion named Lumir.
Analyze the user's message and determine if it contains any personal fact, preference, emotional state, goal, or life event about the user.

If it does, output a strictly valid JSON object with these exact keys:
{
  "has_fact": true,
  "fact": "<concise 3rd-person factual statement, e.g. 'Divya loves ginger tea in the evening'>",
  "importance": <float 1.0 to 10.0: 1-3 is trivial/casual, 4-7 is preference/work, 8-10 is critical identity/health/core goal>,
  "valence": <float -1.0 to +1.0: -1.0 is deep sadness/pain/dukh, 0.0 is neutral, +1.0 is extreme joy/love/sukhi>,
  "arousal": <float 0.0 to 1.0: 0.1 is calm/relaxed, 0.9 is frantic/excited/panicked>,
  "category": "<one of: preference, identity, emotion, goal, event, habit>",
  "tags": ["<short_keyword1>", "<short_keyword2>"]
}

If the message is just a general question, casual greeting, or contains NO personal info or emotion about the user, reply ONLY with:
{"has_fact": false}

Output ONLY the JSON object, nothing else."""

    user_prompt = f"Sender: {sender}\nMessage: \"{text_clean}\""

    # Use unified cognitive worker with 10s safety timeout
    cfg = dict(config or {})
    if node2_url:
        cfg["node2_llama_url"] = node2_url

    data = query_cognitive_task(
        system_prompt=system_prompt,
        user_prompt=user_prompt,
        config=cfg,
        max_tokens=150,
        timeout=10.0,
        ask_ai_fn=ask_ai_fn,
        sender=sender,
        task_name="AMYGDALA"
    )

    if not data or not isinstance(data, dict):
        return None

    if not data.get("has_fact", False):
        return None

    try:
        importance = float(data.get("importance", 5.0))
        importance = max(1.0, min(10.0, importance))

        valence = float(data.get("valence", 0.0))
        valence = max(-1.0, min(1.0, valence))

        arousal = float(data.get("arousal", 0.5))
        arousal = max(0.0, min(1.0, arousal))

        fact_text = str(data.get("fact", "")).strip()
        if not fact_text:
            return None

        tags = data.get("tags", [])
        if not isinstance(tags, list):
            tags = []

        return {
            "fact": fact_text,
            "importance": importance,
            "valence": valence,
            "arousal": arousal,
            "category": str(data.get("category", "general")),
            "tags": [str(t).lower() for t in tags[:5]]
        }
    except Exception as e:
        print(f"[EXTRACTOR PARSE ERROR]: {e}")
        return None
