import os
import json
import time
import requests
from typing import Optional, List, Dict
from .memory_brain import save_cognitive_memory

DEFAULT_NODE2_URL = "http://192.168.31.94:8080"


def query_summarizer_llm(
    conversation_text: str,
    node2_url: str = DEFAULT_NODE2_URL,
    main_ai_fn=None,
    config: Optional[dict] = None
) -> Optional[str]:
    """
    Summarizer LLM Gateway:
    Tries Node 2 (llama.cpp Qwen 0.5B) first.
    If Node 2 is offline/unreachable, gracefully falls back to Node 1 Main Model (Ollama).
    """
    system_prompt = """You are the Neocortex Memory Consolidator for an AI companion named Lumir.
Analyze the following recent chat conversation.
Extract 1 to 3 long-term facts, preferences, current projects, or emotional updates about the user.
Ignore trivial greetings, weather checks, or repetitive filler words.

Output strictly valid JSON with this structure:
{
  "facts": [
    {
      "fact": "<concise 3rd person factual statement>",
      "importance": <float 5.0 to 10.0>,
      "valence": <float -1.0 to 1.0>,
      "category": "<preference|identity|project|emotion>"
    }
  ]
}

If there are no new meaningful personal facts, output: {"facts": []}"""

    # 1. Try Node 2 (Qwen 0.5B on llama.cpp)
    if node2_url:
        try:
            url = f"{node2_url.rstrip('/')}/v1/chat/completions"
            payload = {
                "messages": [
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": f"Conversation:\n{conversation_text}"}
                ],
                "temperature": 0.2,
                "max_tokens": 250,
                "response_format": {"type": "json_object"}
            }
            resp = requests.post(url, json=payload, timeout=5.0)
            if resp.status_code == 200:
                content = resp.json().get("choices", [{}])[0].get("message", {}).get("content", "").strip()
                print("[NEOCORTEX CONSOLIDATION]: Node 2 (Qwen) successfully processed memory batch!")
                return content
        except Exception as e:
            print(f"[SUMMARIZER]: Node 2 unreachable ({e.__class__.__name__}). Falling back to Node 1 Main Brain...")

    # 2. Fallback to Node 1 Main AI (Ollama)
    if main_ai_fn and config:
        try:
            prompt = f"{system_prompt}\n\nConversation:\n{conversation_text}"
            res = main_ai_fn(
                prompt=prompt,
                config=config,
                history=[],
                sender="System",
                custom_system_prompt=system_prompt
            )
            print("[NEOCORTEX CONSOLIDATION]: Node 1 Main AI processed memory batch!")
            return res
        except Exception as fallback_err:
            print(f"[SUMMARIZER FALLBACK ERROR]: {fallback_err}")

    return None


def consolidate_recent_chats(
    username: str,
    recent_messages: List[Dict[str, str]],
    node2_url: str = DEFAULT_NODE2_URL,
    main_ai_fn=None,
    config: Optional[dict] = None
) -> int:
    """
    Consolidates a block of messages into permanent crystallized memories.
    Returns the count of new memories formed.
    """
    if not recent_messages or len(recent_messages) < 4:
        return 0

    conversation_lines = []
    for msg in recent_messages:
        role = msg.get("sender", "User")
        text = msg.get("text", "")
        conversation_lines.append(f"{role}: {text}")

    full_conv = "\n".join(conversation_lines)
    raw_json = query_summarizer_llm(
        conversation_text=full_conv,
        node2_url=node2_url,
        main_ai_fn=main_ai_fn,
        config=config
    )

    if not raw_json:
        return 0

    try:
        # Strip potential code fence markdown
        cleaned = raw_json.strip()
        if cleaned.startswith("```"):
            cleaned = cleaned.split("\n", 1)[-1]
            if cleaned.endswith("```"):
                cleaned = cleaned.rsplit("```", 1)[0]
        cleaned = cleaned.strip()

        data = json.loads(cleaned)
        facts = data.get("facts", [])
        saved_count = 0

        for item in facts:
            fact_text = item.get("fact", "").strip()
            if fact_text and len(fact_text) > 5:
                importance = float(item.get("importance", 7.0))
                valence = float(item.get("valence", 0.0))
                category = str(item.get("category", "general"))
                
                # Save into Cognitive Brain with Crystallization threshold
                save_cognitive_memory(
                    sender=username,
                    fact=fact_text,
                    importance=importance,
                    valence=valence,
                    category=category,
                    tags=["consolidated", category]
                )
                saved_count += 1
                print(f"[NEOCORTEX CRYSTAL]: Remembered '{fact_text}' for {username}")

        return saved_count
    except Exception as parse_err:
        print(f"[SUMMARIZER JSON PARSE ERROR]: {parse_err}")
        return 0
