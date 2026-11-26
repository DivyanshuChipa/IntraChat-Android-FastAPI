import json
from typing import Optional, List, Dict
from .memory_brain import save_cognitive_memory
from .cognitive_helper import query_cognitive_task


def query_summarizer_llm(
    conversation_text: str,
    node2_url: str = None,
    main_ai_fn=None,
    config: Optional[dict] = None
) -> Optional[dict]:
    """
    Summarizer LLM Gateway:
    Tries Node 2 (llama.cpp Qwen 0.5B) first.
    If Node 2 is offline/unreachable, gracefully falls back to Main Brain (Gemini/Sarvam/Ollama).
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

    cfg = dict(config or {})
    if node2_url:
        cfg["node2_llama_url"] = node2_url

    user_prompt = f"Conversation:\n{conversation_text}"

    return query_cognitive_task(
        system_prompt=system_prompt,
        user_prompt=user_prompt,
        config=cfg,
        max_tokens=300,
        timeout=10.0,
        ask_ai_fn=main_ai_fn,
        sender="System",
        task_name="NEOCORTEX_SLEEP"
    )


def consolidate_recent_chats(
    username: str,
    recent_messages: List[Dict[str, str]],
    node2_url: str = None,
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
    data = query_summarizer_llm(
        conversation_text=full_conv,
        node2_url=node2_url,
        main_ai_fn=main_ai_fn,
        config=config
    )

    if not data or not isinstance(data, dict):
        return 0

    try:
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
                print(f"✨ [NEOCORTEX CRYSTAL]: Remembered '{fact_text}' for {username}")

        return saved_count
    except Exception as parse_err:
        print(f"[SUMMARIZER PARSE ERROR]: {parse_err}")
        return 0
