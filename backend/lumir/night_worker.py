import asyncio
import logging
from typing import List
from datetime import datetime, timezone, timedelta

from users import get_all_users, get_ai_config
from messages import get_lumir_history
from lumir.ai_engine import ask_ai
from lumir.memory_brain import prune_decayed_memories
from lumir.summarizer import consolidate_recent_chats, DEFAULT_NODE2_URL

LOGGER = logging.getLogger(__name__)

async def run_nightly_neocortex_consolidation():
    """
    Biological Sleep Memory Worker (Runs at 3:00 AM every night):
    1. Scans all registered active users.
    2. Fetches recent un-consolidated chat history with Lumir.
    3. Tries Node 2 (llama.cpp Qwen 0.5B at 192.168.31.94:8080).
       If Node 2 is offline -> Falls back to Node 1 Main Model (Ollama).
    4. Extracts long-term personal facts and crystallizes them into cognitive memory.
    5. Runs Synaptic Pruning (deletes obsolete dead memories faded by Ebbinghaus decay).
    """
    LOGGER.info("🌙 [NIGHT CONSOLIDATION]: Starting biological sleep cycle for Lumir's brain...")
    try:
        users = await asyncio.to_thread(get_all_users)
        config = await asyncio.to_thread(get_ai_config)
        node2_url = config.get("node2_llama_url", DEFAULT_NODE2_URL)

        total_crystallized = 0

        for user_row in users:
            # get_all_users returns tuples or dicts (id, username, profile_photo)
            username = user_row[1] if isinstance(user_row, tuple) else user_row.get("username")
            if not username or username == "Lumir":
                continue

            recent_msgs = await asyncio.to_thread(get_lumir_history, username, 20)
            if recent_msgs and len(recent_msgs) >= 4:
                LOGGER.info(f"🧠 [NIGHT CONSOLIDATION]: Processing {len(recent_msgs)} memories for {username}...")
                count = await asyncio.to_thread(
                    consolidate_recent_chats,
                    username=username,
                    recent_messages=recent_msgs,
                    node2_url=node2_url,
                    main_ai_fn=ask_ai,
                    config=config
                )
                total_crystallized += count

        # Synaptic Pruning: Clean dead synapses across the system
        pruned = await asyncio.to_thread(prune_decayed_memories, None, 0.05)
        LOGGER.info(f"✨ [NIGHT CONSOLIDATION COMPLETE]: Crystallized {total_crystallized} permanent memories | Pruned {pruned} obsolete synapses.")

    except Exception as e:
        LOGGER.error(f"❌ [NIGHT CONSOLIDATION ERROR]: {e}", exc_info=True)
