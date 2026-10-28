import json
import re
from typing import Optional, Dict

def extract_synthetic_amygdala_fact(text: str, ask_ai_fn, config: dict, sender: str = "User") -> Optional[Dict]:
    """
    Synthetic Amygdala & Hippocampus Feature Extractor:
    Analyzes user text to detect if there's any personal declaration, preference,
    emotional state, or core identity fact.
    
    Returns structured dict:
      {
        "fact": "Divya feels stressed about the Intra project deadline",
        "importance": 7.5,
        "valence": -0.6,       # Negative (dukh/stress) to Positive (sukhi/happiness)
        "arousal": 0.8,       # Intensity/excitement
        "category": "emotion", # preference, identity, emotion, goal, event
        "tags": ["intra", "deadline", "stress"]
      }
    Or None if message is generic banter / chit-chat.
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

    try:
        # Call the configured AI model for extraction
        raw_response = ask_ai_fn(
            prompt=user_prompt,
            config=config,
            history=[],
            sender=sender,
            custom_system_prompt=system_prompt
        )

        if not raw_response or "has_fact" not in raw_response:
            return None

        # Clean code block backticks if LLM wraps in ```json
        cleaned = raw_response.strip()
        cleaned = re.sub(r"^```json\s*", "", cleaned, flags=re.IGNORECASE)
        cleaned = re.sub(r"^```\s*", "", cleaned)
        cleaned = re.sub(r"```$", "", cleaned).strip()

        data = json.loads(cleaned)
        if not data.get("has_fact", False):
            return None

        # Sanitize values
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
        print(f"⚠️ [EXTRACTOR ERROR]: {e}")
        return None
