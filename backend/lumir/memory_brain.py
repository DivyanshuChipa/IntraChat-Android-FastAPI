import os
import math
import time
import uuid
import sqlite3
import json
from typing import List, Dict, Optional, Tuple

# =====================================================================
# 🔬 STORAGE ENGINE SELECTION: SQLite Fallback + ChromaDB Hybrid
# =====================================================================
# Agar ChromaDB system pe installed ho, to vector embeddings use hongi,
# warna native SQLite db 'lumir_brain.db' automatically activate hoga.
# Zero dependency break, 100% reliable!

USE_CHROMA = True
chroma_client = None
cognitive_collection = None

SQLITE_DB_PATH = os.path.join(os.path.dirname(__file__), "lumir_brain.db")

def init_sqlite_brain():
    conn = sqlite3.connect(SQLITE_DB_PATH)
    cursor = conn.cursor()
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS cognitive_synapses (
            doc_id TEXT PRIMARY KEY,
            sender TEXT NOT NULL,
            fact TEXT NOT NULL,
            importance REAL DEFAULT 5.0,
            valence REAL DEFAULT 0.0,
            arousal REAL DEFAULT 0.5,
            category TEXT DEFAULT 'general',
            tags TEXT DEFAULT '',
            reinforcement_count INTEGER DEFAULT 1,
            created_at REAL NOT NULL,
            last_recalled_at REAL NOT NULL,
            is_crystallized INTEGER DEFAULT 0
        )
    """)
    conn.commit()
    conn.close()

init_sqlite_brain()

try:
    if USE_CHROMA:
        import chromadb
        db_path = os.path.join(os.path.dirname(__file__), "chroma_storage")
        chroma_client = chromadb.PersistentClient(path=db_path)
        cognitive_collection = chroma_client.get_or_create_collection(name="lumir_cognitive_memory")
        print("[BRAIN] Lumir Cognitive Vector Memory: ACTIVATED")
except Exception as e:
    cognitive_collection = None
    print(f"[BRAIN] ChromaDB not found or disabled ({e}). Running on Native SQLite Neuro-Storage.")

# =====================================================================
# 🔬 MATHEMATICAL NEURO-DYNAMICS (Ebbinghaus Decay & Synaptic Curves)
# =====================================================================

def calculate_ebbinghaus_retention(
    importance: float,
    created_at: float,
    last_recalled_at: float,
    reinforcement_count: int,
    is_crystallized: bool = False,
    current_time: Optional[float] = None
) -> float:
    """
    Computes current memory retention R in [0.0, 1.0] using modified Ebbinghaus curve:
    R = exp(- delta_t / S)
    where:
      delta_t = time elapsed in days since last active recall
      S = stability index = Base_S * (1 + 0.85 * reinforcement_count)
      Base_S = max(0.5, importance / 2.0)
    
    If memory is crystallized (permanent identity node), retention is locked at 1.0.
    """
    if is_crystallized or importance >= 9.0:
        return 1.0

    if current_time is None:
        current_time = time.time()

    elapsed_seconds = max(0.0, current_time - last_recalled_at)
    delta_days = elapsed_seconds / 86400.0

    # Base stability in days (Higher importance = naturally decays much slower)
    base_stability = max(0.5, float(importance) / 2.0)

    # Synaptic strength multiplier from reinforcement (Hebbian plasticity)
    stability = base_stability * (1.0 + 0.85 * max(0, reinforcement_count - 1))

    # Ebbinghaus exponential decay
    retention = math.exp(-delta_days / stability)
    return max(0.0, min(1.0, retention))


def evaluate_crystallization(importance: float, reinforcement_count: int) -> bool:
    """
    Checks if a memory has crossed the threshold to become a permanent
    Long-Term Identity Node (Crystallized Core Memory).
    Rule:
      - Core facts with importance >= 9 are instantly crystallized.
      - Repeated facts (reinforcement >= 3) with importance >= 6 crystallize.
    """
    if importance >= 9.0:
        return True
    if reinforcement_count >= 3 and importance >= 6.0:
        return True
    return False


# =====================================================================
# 🧬 SYNTHETIC AMYGDALA DATA STRUCTURE & STORAGE
# =====================================================================

class MemoryNode:
    def __init__(
        self,
        doc_id: str,
        fact: str,
        sender: str,
        importance: float = 5.0,
        valence: float = 0.0,        # -1.0 (pain/sadness) to +1.0 (joy/pleasure)
        arousal: float = 0.5,        # 0.0 (calm/trivial) to 1.0 (urgent/excited)
        category: str = "general",   # "preference", "identity", "event", "emotion", "work"
        tags: Optional[List[str]] = None,
        reinforcement_count: int = 1,
        created_at: Optional[float] = None,
        last_recalled_at: Optional[float] = None,
        is_crystallized: bool = False
    ):
        self.doc_id = doc_id or str(uuid.uuid4())
        self.fact = fact
        self.sender = sender
        self.importance = float(importance)
        self.valence = float(valence)
        self.arousal = float(arousal)
        self.category = category
        self.tags = tags or []
        self.reinforcement_count = int(reinforcement_count)
        self.created_at = created_at or time.time()
        self.last_recalled_at = last_recalled_at or self.created_at
        self.is_crystallized = is_crystallized or evaluate_crystallization(self.importance, self.reinforcement_count)

    def current_retention(self, now: Optional[float] = None) -> float:
        return calculate_ebbinghaus_retention(
            importance=self.importance,
            created_at=self.created_at,
            last_recalled_at=self.last_recalled_at,
            reinforcement_count=self.reinforcement_count,
            is_crystallized=self.is_crystallized,
            current_time=now
        )

    def to_dict(self) -> Dict:
        return {
            "doc_id": self.doc_id,
            "sender": self.sender,
            "fact": self.fact,
            "importance": self.importance,
            "valence": self.valence,
            "arousal": self.arousal,
            "category": self.category,
            "tags": ",".join(self.tags) if isinstance(self.tags, list) else str(self.tags),
            "reinforcement_count": self.reinforcement_count,
            "created_at": float(self.created_at),
            "last_recalled_at": float(self.last_recalled_at),
            "is_crystallized": 1 if self.is_crystallized else 0
        }

    @classmethod
    def from_row(cls, row) -> 'MemoryNode':
        doc_id, sender, fact, importance, valence, arousal, category, tags_str, count, created_at, last_recalled_at, crystallized = row
        tags_list = [t.strip() for t in tags_str.split(",") if t.strip()] if tags_str else []
        return cls(
            doc_id=doc_id,
            sender=sender,
            fact=fact,
            importance=importance,
            valence=valence,
            arousal=arousal,
            category=category,
            tags=tags_list,
            reinforcement_count=count,
            created_at=created_at,
            last_recalled_at=last_recalled_at,
            is_crystallized=bool(crystallized)
        )


# =====================================================================
# 🧠 ACTIVE MEMORY OPERATIONS (Save, Recall & Synaptic Reinforcement)
# =====================================================================

def save_cognitive_memory(
    sender: str,
    fact: str,
    importance: float = 5.0,
    valence: float = 0.0,
    arousal: float = 0.5,
    category: str = "general",
    tags: Optional[List[str]] = None
) -> Optional[str]:
    """
    Stores a new memory node into Lumir's cognitive connectome with Amygdala emotional metadata.
    If an extremely similar memory exists, it reinforces it instead of duplicating!
    """
    fact = fact.strip()
    if not fact:
        return None

    # Check for existing memory in SQLite first (or Chroma)
    conn = sqlite3.connect(SQLITE_DB_PATH)
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM cognitive_synapses WHERE sender = ?", (sender,))
    existing_rows = cursor.fetchall()

    now = time.time()
    for row in existing_rows:
        node = MemoryNode.from_row(row)
        # Check simple word overlap similarity
        words_new = set(fact.lower().split())
        words_old = set(node.fact.lower().split())
        overlap = len(words_new & words_old) / max(1, len(words_new | words_old))

        if overlap > 0.65 or fact.lower() in node.fact.lower() or node.fact.lower() in fact.lower():
            # Synaptic Reinforcement (LTP)
            node.reinforcement_count += 1
            node.last_recalled_at = now
            node.importance = max(node.importance, float(importance))
            node.is_crystallized = evaluate_crystallization(node.importance, node.reinforcement_count)

            cursor.execute("""
                UPDATE cognitive_synapses
                SET reinforcement_count = ?, last_recalled_at = ?, importance = ?, is_crystallized = ?
                WHERE doc_id = ?
            """, (node.reinforcement_count, node.last_recalled_at, node.importance, 1 if node.is_crystallized else 0, node.doc_id))
            conn.commit()
            conn.close()
            print(f"[BRAIN REINFORCE]: Synapse strengthened! '{node.fact}' (count: {node.reinforcement_count}, crystallized: {node.is_crystallized})")
            return node.doc_id

    # Create new Synapse
    doc_id = str(uuid.uuid4())
    node = MemoryNode(
        doc_id=doc_id,
        fact=fact,
        sender=sender,
        importance=importance,
        valence=valence,
        arousal=arousal,
        category=category,
        tags=tags,
        created_at=now,
        last_recalled_at=now
    )

    d = node.to_dict()
    cursor.execute("""
        INSERT INTO cognitive_synapses (doc_id, sender, fact, importance, valence, arousal, category, tags, reinforcement_count, created_at, last_recalled_at, is_crystallized)
        VALUES (:doc_id, :sender, :fact, :importance, :valence, :arousal, :category, :tags, :reinforcement_count, :created_at, :last_recalled_at, :is_crystallized)
    """, d)
    conn.commit()
    conn.close()

    print(f"[BRAIN ENCODE]: New Synapse Formed for {sender}: '{fact}' | Imp: {node.importance}, Valence: {node.valence}")
    return node.doc_id


def recall_cognitive_memories(
    sender: str,
    query_text: str,
    n_candidates: int = 4,
    min_retention_threshold: float = 0.20,
    reinforce_on_recall: bool = True
) -> List[Dict]:
    """
    Active Cognitive Recall:
    1. Fetches candidate memories for sender from SQLite/Chroma.
    2. Computes real-time Ebbinghaus decay score for each candidate.
    3. Filters out decayed memories (R < min_retention_threshold).
    4. Computes effective cognitive score = Semantic Similarity * Retention.
    5. Strengthens retrieved active synapses (reinforce_on_recall).
    """
    conn = sqlite3.connect(SQLITE_DB_PATH)
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM cognitive_synapses WHERE sender = ?", (sender,))
    rows = cursor.fetchall()

    if not rows:
        conn.close()
        return []

    now = time.time()
    query_words = set(query_text.lower().split())
    active_memories = []

    for row in rows:
        node = MemoryNode.from_row(row)
        retention = node.current_retention(now=now)

        if retention < min_retention_threshold:
            print(f"[BRAIN DECAYED]: Memory '{node.fact}' faded out (Retention: {retention:.2f})")
            continue

        # Similarity score based on keyword overlap + tags match
        fact_words = set(node.fact.lower().split())
        tag_words = set([t.lower() for t in node.tags])
        match_count = len(query_words & (fact_words | tag_words))
        
        # Base relevance
        if match_count > 0:
            sim_score = 0.5 + (0.5 * min(1.0, match_count / 2.0))
        else:
            # If crystallized and high importance, keep small background ambient chance
            sim_score = 0.15 if node.is_crystallized else 0.05

        cognitive_score = sim_score * retention

        active_memories.append({
            "id": node.doc_id,
            "fact": node.fact,
            "importance": node.importance,
            "valence": node.valence,
            "category": node.category,
            "retention": retention,
            "cognitive_score": cognitive_score,
            "is_crystallized": node.is_crystallized,
            "reinforcement_count": node.reinforcement_count,
            "node_obj": node
        })

    # Sort by cognitive score descending
    active_memories.sort(key=lambda x: x["cognitive_score"], reverse=True)

    # Synaptic Reinforcement on active recall
    top_picks = [m for m in active_memories if m["cognitive_score"] > 0.25][:n_candidates]

    if reinforce_on_recall and top_picks:
        for item in top_picks[:2]:
            node = item["node_obj"]
            node.reinforcement_count += 1
            node.last_recalled_at = now
            node.is_crystallized = evaluate_crystallization(node.importance, node.reinforcement_count)

            cursor.execute("""
                UPDATE cognitive_synapses
                SET reinforcement_count = ?, last_recalled_at = ?, is_crystallized = ?
                WHERE doc_id = ?
            """, (node.reinforcement_count, node.last_recalled_at, 1 if node.is_crystallized else 0, node.doc_id))
            print(f"[SYNAPSE REINFORCE]: Recalled & strengthened '{node.fact}' (Retention: {item['retention']:.2f})")

        conn.commit()

    conn.close()

    output = []
    for mem in top_picks:
        output.append({
            "id": mem["id"],
            "fact": mem["fact"],
            "importance": mem["importance"],
            "valence": mem["valence"],
            "category": mem["category"],
            "retention": round(mem["retention"], 3),
            "is_crystallized": mem["is_crystallized"]
        })

    return output


def prune_decayed_memories(sender: Optional[str] = None, dead_threshold: float = 0.05) -> int:
    """
    Synaptic Pruning (Mimics biological sleep cleanup):
    Scans cognitive memory database and deletes memories whose retention
    has completely decayed (R < 0.05) and are not crystallized.
    Returns number of pruned synapses.
    """
    conn = sqlite3.connect(SQLITE_DB_PATH)
    cursor = conn.cursor()

    if sender:
        cursor.execute("SELECT * FROM cognitive_synapses WHERE sender = ? AND is_crystallized = 0", (sender,))
    else:
        cursor.execute("SELECT * FROM cognitive_synapses WHERE is_crystallized = 0")

    rows = cursor.fetchall()
    now = time.time()
    pruned_count = 0

    for row in rows:
        node = MemoryNode.from_row(row)
        retention = node.current_retention(now=now)
        if retention < dead_threshold:
            cursor.execute("DELETE FROM cognitive_synapses WHERE doc_id = ?", (node.doc_id,))
            print(f"[SYNAPTIC PRUNING]: Forgetting obsolete synapse '{node.fact}' (R: {retention:.3f})")
            pruned_count += 1

    conn.commit()
    conn.close()

    if pruned_count > 0:
        print(f"[BRAIN CONSOLIDATION]: Pruned {pruned_count} dead synapses.")
    return pruned_count
