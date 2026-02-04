
# lan_server/users.py

# ===== File: users.py =====
import sqlite3
from passlib.hash import pbkdf2_sha256
import os

DATABASE_NAME = "chat_users.db"

def init_db():
    conn = sqlite3.connect(DATABASE_NAME)
    cursor = conn.cursor()
    
    # Users Table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT UNIQUE NOT NULL,
            password_hash TEXT NOT NULL,
            profile_photo TEXT DEFAULT NULL,
            is_approved INTEGER DEFAULT 1
        )
    """)
    
    # Config Table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS config (
            key TEXT PRIMARY KEY,
            value TEXT
        )
    """)
    cursor.execute("INSERT OR IGNORE INTO config (key, value) VALUES ('require_approval', '0')")

    try:
        cursor.execute("ALTER TABLE users ADD COLUMN is_approved INTEGER DEFAULT 1")
    except sqlite3.OperationalError:
        pass 
        
    conn.commit()
    conn.close()

# --- Config Helpers ---
def set_require_approval(enabled: bool):
    conn = sqlite3.connect(DATABASE_NAME)
    cursor = conn.cursor()
    val = "1" if enabled else "0"
    cursor.execute("INSERT OR REPLACE INTO config (key, value) VALUES ('require_approval', ?)", (val,))
    conn.commit()
    conn.close()

def get_require_approval():
    conn = sqlite3.connect(DATABASE_NAME)
    cursor = conn.cursor()
    cursor.execute("SELECT value FROM config WHERE key='require_approval'")
    row = cursor.fetchone()
    conn.close()
    return row[0] == "1" if row else False

def approve_user_db(username):
    conn = sqlite3.connect(DATABASE_NAME)
    cursor = conn.cursor()
    cursor.execute("UPDATE users SET is_approved = 1 WHERE username = ?", (username,))
    conn.commit()
    conn.close()

# --- Security Check for WebSocket ---
def is_user_approved(username):
    conn = sqlite3.connect(DATABASE_NAME)
    cursor = conn.cursor()
    cursor.execute("SELECT is_approved FROM users WHERE username=?", (username,))
    result = cursor.fetchone()
    conn.close()
    # Agar user exist karta hai aur approved (1) hai, tabhi True
    return result is not None and result[0] == 1

# --- Modified Registration ---
def register_user(username, password):
    approval_needed = get_require_approval()
    is_approved = 0 if approval_needed else 1 

    hash = pbkdf2_sha256.hash(password)
    conn = sqlite3.connect(DATABASE_NAME)
    cursor = conn.cursor()
    try:
        cursor.execute(
            "INSERT INTO users (username, password_hash, is_approved) VALUES (?, ?, ?)", 
            (username, hash, is_approved)
        )
        conn.commit()
        
        if approval_needed:
             return {"success": True, "message": "Registration successful! Wait for Admin approval."}
        else:
             return {"success": True, "message": "User registered successfully"}
             
    except sqlite3.IntegrityError:
        return {"success": False, "message": "Username already taken"}
    except Exception as e:
        return {"success": False, "message": str(e)}
    finally:
        conn.close()
# --- 🔥 FINAL LOGIN LOGIC (PRO STYLE) ---

def verify_user(username, password):
    conn = sqlite3.connect(DATABASE_NAME)
    cursor = conn.cursor()
    cursor.execute("SELECT password_hash, is_approved FROM users WHERE username=?", (username,))
    result = cursor.fetchone()
    conn.close()
    
    if result:
        password_hash = result[0]
        is_approved = result[1]
        
        if pbkdf2_sha256.verify(password, password_hash):
            if is_approved == 0:
                # ✅ Dictionary Return (ChatGPT recommended)
                return {"status": "PENDING"}  
            return {"status": "OK"}
            
    return {"status": "INVALID"}


# (Baaki functions same rahenge: get_all_users, delete_user_data, etc...)
def get_all_users():
    conn = sqlite3.connect(DATABASE_NAME)
    cursor = conn.cursor()
    try:
        cursor.execute("SELECT id, username, profile_photo, is_approved FROM users")
        users = []
        for row in cursor.fetchall():
            users.append({
                "id": row[0],
                "username": row[1],
                "profile_photo": row[2],
                "is_approved": bool(row[3])
            })
        return users
    except:
        return []
    finally:
        conn.close()

def delete_user_data(username):
    conn = sqlite3.connect(DATABASE_NAME)
    cursor = conn.cursor()
    try:
        cursor.execute("SELECT profile_photo FROM users WHERE username=?", (username,))
        result = cursor.fetchone()
        if result and result[0]:
            photo_path = result[0]
            if photo_path.startswith("/"): photo_path = photo_path[1:]
            if os.path.exists(photo_path): os.remove(photo_path)
        cursor.execute("DELETE FROM users WHERE username=?", (username,))
        conn.commit()
        return {"success": True, "message": "Account deleted successfully"}
    except Exception as e:
        return {"success": False, "message": str(e)}
    finally:
        conn.close()

def reset_user_password(username, new_password):
    new_hash = pbkdf2_sha256.hash(new_password)
    conn = sqlite3.connect(DATABASE_NAME)
    cursor = conn.cursor()
    cursor.execute("UPDATE users SET password_hash = ? WHERE username = ?", (new_hash, username))
    conn.commit()
    conn.close()
    return {"success": True, "message": "Password reset successfully"}
    
def update_user_photo(username, file_path):
    conn = sqlite3.connect(DATABASE_NAME)
    cursor = conn.cursor()
    cursor.execute("UPDATE users SET profile_photo = ? WHERE username = ?", (file_path, username))
    conn.commit()
    conn.close()
