import os
import uuid
import time

# 🪄 THE TOGGLE: Yahan se Vector DB ko On/Off karo
USE_VECTOR_MEMORY = True

# Global variables
VECTOR_DB_ACTIVE = False
chroma_client = None
memory_collection = None

try:
    if not USE_VECTOR_MEMORY:
        raise Exception("Vector DB is manually disabled by Admin.")

    import chromadb

    # DB ko lumir folder ke andar hi 'chroma_storage' naam se save karenge
    db_path = os.path.join(os.path.dirname(__file__), "chroma_storage")
    chroma_client = chromadb.PersistentClient(path=db_path)

    # Purana wala (Media ke liye)
    memory_collection = chroma_client.get_or_create_collection(name="lumir_long_term_memory")

    # 🌟 NAYA WALA (User Facts ke liye)
    facts_collection = chroma_client.get_or_create_collection(name="lumir_user_facts")

    VECTOR_DB_ACTIVE = True
    print("🧠 Lumir Vector Memory: ACTIVATED (Raphael Mode On 🌟)")

except Exception as e:
    VECTOR_DB_ACTIVE = False
    print(f"⚠️ Lumir Vector Memory: DISABLED ({e}). Running in Standard Chat Mode.")

def is_memory_active():
    return VECTOR_DB_ACTIVE

# ==========================================
# 🧠 MEMORY FUNCTIONS (Save & Search)
# ==========================================

def save_media_to_memory(sender, file_url, file_name, description="Shared media"):
    """Jab bhi koi photo/video aaye, usko DB mein yaad rakho"""
    if not is_memory_active():
        return False

    try:
        doc_id = str(uuid.uuid4()) # Ek unique ID

        # Ye wo text hai jisko AI semantic search se dhoondhega
        searchable_text = f"Media uploaded by {sender}. File name: {file_name}. Context: {description}"

        memory_collection.add(
            documents=[searchable_text],
            metadatas=[{
                "sender": sender,
                "file_url": file_url,
                "file_name": file_name,
                "timestamp": str(int(time.time()))
            }],
            ids=[doc_id]
        )
        print(f"💾 Memory Updated: Lumir remembered {file_name} for {sender}")
        return True
    except Exception as e:
        print(f"❌ Memory Save Error: {e}")
        return False

def find_media_in_memory(sender, query_text):
    """Purani files dhoondhne ke liye (e.g., 'recent passport photo')"""
    if not is_memory_active():
        return None

    try:
        # ChromaDB apna magic chalayega aur meaning match karega
        results = memory_collection.query(
            query_texts=[query_text],
            n_results=1, # Sirf sabse best match laao
            where={"sender": sender} # 🔒 PRIVACY: Sirf usi user ki file dhoondho jisne maangi hai!
        )

        if results['documents'] and len(results['documents'][0]) > 0:
            match_metadata = results['metadatas'][0][0]
            print(f"🔍 Memory Found: {match_metadata['file_name']} for {sender}")
            return match_metadata # Isme file_url aur file_name hoga

        print("🤷‍♀️ Memory Miss: Koi match nahi mila.")
        return None
    except Exception as e:
        print(f"❌ Memory Search Error: {e}")
        return None

def save_fact_to_chroma(sender: str, fact: str):
    if not is_memory_active(): return False
    try:
        doc_id = str(uuid.uuid4())
        facts_collection.add(
            documents=[fact],
            metadatas=[{"sender": sender, "timestamp": str(int(time.time()))}],
            ids=[doc_id]
        )
        return True
    except Exception as e:
        print(f"❌ Chroma Fact Save Error: {e}")
        return False

def search_facts_in_chroma(sender: str, query_text: str, n_results: int = 2):
    if not is_memory_active(): return []
    try:
        # User ke current message (query) ke hisaab se sabse relevant 2 facts dhoondho!
        results = facts_collection.query(
            query_texts=[query_text],
            n_results=n_results,
            where={"sender": sender},
            include=["documents", "distances", "metadatas"]
        )

        valid_facts = []
        if results['documents'] and results['documents'][0]:
            # Distance threshold for relevance (lower distance = higher similarity)
            # Threshold of 1.2 is a good starting point for L2 distance in typical embedding models.
            threshold = 1.2

            for doc, distance in zip(results['documents'][0], results['distances'][0]):
                print(f"🧠 [CHROMA RECALL]: Evaluated fact: '{doc}' with distance: {distance}")
                if distance < threshold:
                    valid_facts.append(doc)
                else:
                    print(f"🤷‍♂️ [CHROMA RECALL]: Fact ignored due to high distance (not relevant enough).")

            if valid_facts:
                print(f"🎯 [CHROMA RECALL]: Returning {len(valid_facts)} relevant facts.")
            return valid_facts
        return []
    except Exception as e:
        print(f"❌ Chroma Fact Search Error: {e}")
        return []