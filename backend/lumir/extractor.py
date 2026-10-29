import json
import re
import requests
from typing import Optional, Dict

DEFAULT_NODE2_URL = "http://192.168.31.94:8080"

def extract_synthetic_amygdala_fact(
    text: str,
    ask_ai_fn,
    config: dict,
    sender: str = "User",
    node2_url: str = DEFAULT_NODE2_URL
) -> Optional[Dict]:
    """
    Synthetic Amygdala & Hippocampus Feature Extractor:
    Orchestrated by Corpus Callosum:
    - 1st Priority: Node 2 (Qwen 0.5B via llama.cpp) -> Ultra fast (~200ms JSON), zero load on cloud model.
    - Fallback: Node 1 (Main Ollama) if Node 2 is offline.
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

    raw_response = None

    # 1. First priority: Offload to Node 2 (Qwen 0.5B on llama.cpp)
    target_node2 = config.get("node2_llama_url", node2_url) if config else node2_url
    if target_node2:
        try:
            url = f"{target_node2.rstrip('/')}/v1/chat/completions"
            payload = {
                "messages": [
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_prompt}
                ],
                "temperature": 0.1,
                "max_tokens": 150,
                "response_format": {"type": "json_object"}
            }
            resp = requests.post(url, json=payload, timeout=2.5)
            if resp.status_code == 200:
                raw_response = resp.json().get("choices", [{}])[0].get("message", {}).get("content", "").strip()
                print(f"[AMYGDALA - NODE 2]: Qwen extracted memory/emotion for {sender}")
        except Exception as e:
            # Node 2 offline or timed out, silent fallback
            pass

    # 2. Fallback to Node 1 Main Model if Node 2 did not respond
    if not raw_response and ask_ai_fn:
        try:
            raw_response = ask_ai_fn(
                prompt=user_prompt,
                config=config,
                history=[],
                sender=sender,
                custom_system_prompt=system_prompt
            )
            print(f"[AMYGDALA - NODE 1 FALLBACK]: Main AI extracted memory for {sender}")
        except Exception as e:
            print(f"[EXTRACTOR ERROR]: {e}")
            return None

    if not raw_response or "has_fact" not in raw_response:
        return None

    try:
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
        print(f"[EXTRACTOR JSON PARSE ERROR]: {e}")
        return None
