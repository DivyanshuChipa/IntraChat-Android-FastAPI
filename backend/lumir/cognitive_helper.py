import json
import logging
import os
import re
import time
from typing import Any, Dict, Optional
import requests

LOGGER = logging.getLogger("lumir.cognitive")


def parse_json_safely(raw_text: str) -> Optional[Dict[str, Any]]:
    """Cleans markdown wrappers and extracts valid JSON dictionary."""
    if not raw_text or not isinstance(raw_text, str):
        return None

    cleaned = raw_text.strip()
    # Strip markdown code blocks (e.g. ```json ... ```)
    cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\s*```$", "", cleaned)
    cleaned = cleaned.strip()

    # Direct parse attempt
    try:
        data = json.loads(cleaned)
        if isinstance(data, dict):
            return data
    except Exception:
        pass

    # Regex search for first outer {...} block
    match = re.search(r"(\{.*\})", cleaned, re.DOTALL)
    if match:
        try:
            data = json.loads(match.group(1))
            if isinstance(data, dict):
                return data
        except Exception:
            pass

    return None


def test_node2_connection(url: str, timeout: float = 3.0) -> Dict[str, Any]:
    """
    Pings a llama.cpp / OpenAI-compatible endpoint to check online status and latency.
    """
    if not url or not url.strip():
        return {"success": False, "message": "No URL provided."}

    clean_url = url.strip().rstrip("/")
    endpoint = f"{clean_url}/v1/models"

    start_t = time.time()
    try:
        resp = requests.get(endpoint, timeout=timeout)
        latency_ms = int((time.time() - start_t) * 1000)

        if resp.status_code == 200:
            data = resp.json().get("data", [])
            model_ids = [m.get("id") for m in data if isinstance(m, dict) and m.get("id")]
            active_model = model_ids[0] if model_ids else "llama.cpp active"
            return {
                "success": True,
                "latency_ms": latency_ms,
                "model": active_model,
                "models": model_ids,
                "message": f"Online ({latency_ms} ms) - Model: {active_model}"
            }
        else:
            return {
                "success": False,
                "status_code": resp.status_code,
                "message": f"HTTP {resp.status_code}: {resp.text[:100]}"
            }
    except requests.exceptions.ConnectionError:
        return {"success": False, "message": f"Target unreachable: {clean_url}"}
    except requests.exceptions.Timeout:
        return {"success": False, "message": f"Connection timed out (> {timeout}s)"}
    except Exception as e:
        return {"success": False, "message": f"{e.__class__.__name__}: {str(e)}"}


def query_cognitive_task(
    system_prompt: str,
    user_prompt: str,
    config: Optional[dict] = None,
    max_tokens: int = 200,
    timeout: float = 10.0,
    ask_ai_fn=None,
    sender: str = "User",
    task_name: str = "Cognitive Task"
) -> Optional[Dict[str, Any]]:
    """
    Unified Cognitive Gateway for micro-tasks (Amygdala, Intent Routing, Neocortex Sleep):
    1. Tier 1: Configured llama.cpp node (Node 2 / LAN / Local Qwen 0.5B) -> Fast, free, local.
    2. Tier 2: Seamless fallback to Main Brain (Gemini, Sarvam, or Ollama) with Markdown stripping.
    """
    config = config or {}
    node2_url = (config.get("node2_llama_url") or os.getenv("INTRA_NODE2_LLAMA_URL", "")).strip()

    # 1. Tier 1: Dedicated Cognitive Node (llama.cpp / Qwen 0.5B)
    if node2_url:
        try:
            endpoint = f"{node2_url.rstrip('/')}/v1/chat/completions"
            payload = {
                "messages": [
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_prompt}
                ],
                "temperature": 0.1,
                "max_tokens": max_tokens,
                "response_format": {"type": "json_object"}
            }
            resp = requests.post(endpoint, json=payload, timeout=timeout)
            if resp.status_code == 200:
                raw_json_str = resp.json().get("choices", [{}])[0].get("message", {}).get("content", "").strip()
                data = parse_json_safely(raw_json_str)
                if data is not None:
                    LOGGER.info(f"🧠 [{task_name} - NODE 2]: Successfully processed by llama.cpp")
                    return data
            else:
                LOGGER.warning(f"⚠️ [{task_name} - NODE 2 HTTP {resp.status_code}]: {resp.text[:100]}")
        except Exception as e:
            LOGGER.warning(f"⚠️ [{task_name} - NODE 2 FAILED]: {e.__class__.__name__}: {e}. Falling back to Main Model...")

    # 2. Tier 2: Main Model Fallback (Gemini / Sarvam / Ollama)
    if ask_ai_fn:
        try:
            raw_reply = ask_ai_fn(
                prompt=user_prompt,
                config=config,
                history=[],
                sender=sender,
                custom_system_prompt=system_prompt
            )
            data = parse_json_safely(raw_reply)
            if data is not None:
                LOGGER.info(f"✨ [{task_name} - FALLBACK]: Main AI successfully processed task")
                return data
            else:
                LOGGER.warning(f"⚠️ [{task_name} - FALLBACK]: Could not parse JSON from main AI reply: {raw_reply[:120]}")
        except Exception as e:
            LOGGER.error(f"❌ [{task_name} - FALLBACK ERROR]: {e}")

    return None
