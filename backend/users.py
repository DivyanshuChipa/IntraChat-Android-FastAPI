# lan_server/users.py

import sqlite3
from passlib.hash import pbkdf2_sha256
import os # Ensure os is imported

DATABASE_NAME = "chat_users.db" # [cite: 263]

def init_db():
    conn = sqlite3.connect(DATABASE_NAME)
    cursor = conn.cursor()
    
    # ✅ Update: Added profile_photo column
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT UNIQUE NOT NULL,
            password_hash TEXT NOT NULL,
            profile_photo TEXT DEFAULT NULL
        )
    """)
    
    # Migration check: If table exists but column doesn't, add it
    try:
        cursor.execute("ALTER TABLE users ADD COLUMN profile_photo TEXT DEFAULT NULL")
    except sqlite3.OperationalError:
        pass # Column already exists
        
    conn.commit()
    conn.close()

# ... (register_user and verify_user remain exactly the same as your code) ...

def register_user(username, password):
    hash = pbkdf2_sha256.hash(password)
    conn = sqlite3.connect(DATABASE_NAME)
    cursor = conn.cursor()
    try:
        cursor.execute(
            "INSERT INTO users (username, password_hash) VALUES (?, ?)", 
            (username, hash)
        )
        conn.commit()
        return {"success": True, "message": "User registered successfully"}
    except sqlite3.IntegrityError:
        return {"success": False, "message": "Username already taken"}
    except Exception as e:
        return {"success": False, "message": str(e)}
    finally:
        conn.close()

def verify_user(username, password):
    conn = sqlite3.connect(DATABASE_NAME)
    cursor = conn.cursor()
    cursor.execute("SELECT password_hash FROM users WHERE username=?", (username,))
    result = cursor.fetchone()
    conn.close()
    if result:
        return pbkdf2_sha256.verify(password, result[0])
    return False

# ✅ Update: Return profile_photo in the list
def get_all_users():
    conn = sqlite3.connect(DATABASE_NAME)
    cursor = conn.cursor()
    try:
        cursor.execute("SELECT id, username, profile_photo FROM users")
        users = []
        for row in cursor.fetchall():
            users.append({
                "id": row[0],
                "username": row[1],
                "profile_photo": row[2] # Will be None if no photo
            })
        return users
    except Exception as e:
        return []
    finally:
        conn.close()

# ✅ New Function: Update photo path in DB
def update_user_photo(username, file_path):
    conn = sqlite3.connect(DATABASE_NAME)
    cursor = conn.cursor()
    cursor.execute("UPDATE users SET profile_photo = ? WHERE username = ?", (file_path, username))
    conn.commit()
    conn.close()

# ✅ NEW: Function to delete user and their profile photo
def delete_user_data(username):
    conn = sqlite3.connect(DATABASE_NAME)
    cursor = conn.cursor()
    try:
        # 1. Get Profile Photo Path
        cursor.execute("SELECT profile_photo FROM users WHERE username=?", (username,))
        result = cursor.fetchone()
        
        # 2. Delete Photo File if exists
        if result and result[0]:
            photo_path = result[0] # e.g., /uploads/profiles/user.png
            # Remove leading slash for local path check
            if photo_path.startswith("/"):
                photo_path = photo_path[1:]
            
            if os.path.exists(photo_path):
                os.remove(photo_path)
                print(f"🗑️ Deleted photo: {photo_path}")

        # 3. Delete User from DB
        cursor.execute("DELETE FROM users WHERE username=?", (username,))
        conn.commit()
        return {"success": True, "message": "Account deleted successfully"}

    except Exception as e:
        return {"success": False, "message": str(e)}
    finally:
        conn.close()

        # ===== Add this at the end of users.py =====
def reset_user_password(username, new_password):
            # Hash the new password using existing logic
            new_hash = pbkdf2_sha256.hash(new_password)

            conn = sqlite3.connect(DATABASE_NAME)
            cursor = conn.cursor()

            # Update password_hash, NOT 'password'
            cursor.execute(
                "UPDATE users SET password_hash = ? WHERE username = ?",
                (new_hash, username)
            )

            if cursor.rowcount == 0:
                conn.close()
                return {"success": False, "message": "User not found"}

            conn.commit()
            conn.close()
            return {"success": True, "message": "Password reset successfully"}
