import sqlite3
import json
from datetime import datetime, timezone, timedelta

DB_NAME = "chat_messages.db"

# ✅ IST Timezone (UTC + 5:30)
IST = timezone(timedelta(hours=5, minutes=30))

def init_msg_db():
    conn = sqlite3.connect(DB_NAME)
    cur = conn.cursor()

    cur.execute("""
    CREATE TABLE IF NOT EXISTS messages (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        text TEXT NOT NULL,
        sender TEXT NOT NULL,
        receiver TEXT NOT NULL,
        msg_type TEXT NOT NULL,
        file_url TEXT,
        file_name TEXT,
        options TEXT,
        ts INTEGER NOT NULL
    )
    """)

    try:
        cur.execute("ALTER TABLE messages ADD COLUMN options TEXT")
    except sqlite3.OperationalError:
        pass # Column already exists

    cur.execute("""
    CREATE TABLE IF NOT EXISTS delivery_status (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        msg_id INTEGER NOT NULL,
        recipient TEXT NOT NULL,
        delivered INTEGER DEFAULT 0,
        delivered_at INTEGER,
        FOREIGN KEY (msg_id) REFERENCES messages(id)
    )
    """)

    conn.commit()
    conn.close()

def save_message(text, sender, receiver, msg_type="text", file_url=None, file_name=None, options=None):
    # ✅ IST timestamp
    ts = int(datetime.now(IST).timestamp() * 1000)
    
    conn = sqlite3.connect(DB_NAME)
    cur = conn.cursor()

    options_json = json.dumps(options) if options else None

    cur.execute("""
    INSERT INTO messages (text, sender, receiver, msg_type, file_url, file_name, options, ts)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """, (text, sender, receiver, msg_type, file_url, file_name, options_json, ts))

    msg_id = cur.lastrowid
    conn.commit()
    conn.close()
    return msg_id

def create_delivery_entries(msg_id, recipients: list):
    conn = sqlite3.connect(DB_NAME)
    cur = conn.cursor()

    for user in recipients:
        cur.execute("""
        INSERT INTO delivery_status (msg_id, recipient, delivered)
        VALUES (?, ?, 0)
        """, (msg_id, user))

    conn.commit()
    conn.close()

def get_undelivered_messages(username: str):
    conn = sqlite3.connect(DB_NAME)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()

    cur.execute("""
    SELECT m.*, d.id AS delivery_id
    FROM messages m
    JOIN delivery_status d ON m.id = d.msg_id
    WHERE d.recipient = ? AND d.delivered = 0
    ORDER BY m.ts ASC
    """, (username,))

    rows = cur.fetchall()
    conn.close()
    return [dict(row) for row in rows]

def mark_delivered(delivery_id: int):
    ts = int(datetime.now(IST).timestamp() * 1000)
    conn = sqlite3.connect(DB_NAME)
    cur = conn.cursor()

    cur.execute("""
    UPDATE delivery_status
    SET delivered = 1, delivered_at = ?
    WHERE id = ?
    """, (ts, delivery_id))

    conn.commit()
    conn.close()

def mark_message_delivered_for_user(msg_id: int, recipient: str):
    ts = int(datetime.now(IST).timestamp() * 1000)
    conn = sqlite3.connect(DB_NAME)
    cur = conn.cursor()

    cur.execute("""
    UPDATE delivery_status
    SET delivered = 1, delivered_at = ?
    WHERE msg_id = ? AND recipient = ?
    """, (ts, msg_id, recipient))

    conn.commit()
    conn.close()

def get_recent_messages(limit: int = 200):
    conn = sqlite3.connect(DB_NAME)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()
    
    cur.execute("""
        SELECT * FROM messages ORDER BY ts DESC LIMIT ?
    """, (limit,))
    
    rows = cur.fetchall()
    conn.close()

    return [
        {
            "text": row["text"],
            "sender": row["sender"],
            "receiver": row["receiver"],
            "type": row["msg_type"],
            "fileUrl": row["file_url"],
            "fileName": row["file_name"],
            "options": json.loads(row["options"]) if row["options"] else None,
            "timestamp": row["ts"]
        }
        for row in rows
    ]


# ===== Add this at the end of messages.py =====

def cleanup_old_messages(days: int):
    # Calculate cutoff time (Current Time - Days) in Milliseconds
    cutoff_time = datetime.now(IST) - timedelta(days=days)
    cutoff_ts = int(cutoff_time.timestamp() * 1000)

    conn = sqlite3.connect(DB_NAME)
    cur = conn.cursor()

    # Delete messages older than cutoff
    cur.execute("DELETE FROM messages WHERE ts < ?", (cutoff_ts,))
    deleted_count = cur.rowcount

    # Optional: Delete delivery status for deleted messages
    cur.execute("DELETE FROM delivery_status WHERE msg_id NOT IN (SELECT id FROM messages)")

    conn.commit()
    conn.close()
    return deleted_count

# ===== Add this at the end of messages.py =====
def get_lumir_history(username: str, limit: int = 6):
    conn = sqlite3.connect(DB_NAME)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()

    # 🔥 STRICT FILTERING: Yahan check karo ki (sender = username AND receiver = 'Lumir')
    # YA (sender = 'Lumir' AND receiver = username)
    cur.execute("""
        SELECT sender, text FROM messages 
        WHERE (sender = ? AND receiver = 'Lumir') 
           OR (sender = 'Lumir' AND receiver = ?)
        ORDER BY ts DESC LIMIT ?
    """, (username, username, limit))
    # 👆 (username, username, limit) bhejna bohot zaroori hai
    
    rows = cur.fetchall()
    conn.close()

    # Ollama ko history ek specific format (role: user/assistant) me chahiye hoti hai
    history = []
    # Reverse the list so chronological order is maintained (oldest first, newest last)
    for row in reversed(rows):
        role = "assistant" if row["sender"] == "Lumir" else "user"
        history.append({"role": role, "content": row["text"]})
        
    return history