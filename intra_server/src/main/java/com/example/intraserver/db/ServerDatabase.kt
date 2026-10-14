package com.example.intraserver.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ServerUser(
    val username: String,
    val profilePhoto: String? = null,
    val createdAt: String = "",
    val status: String = "OK",
    val isOnline: Boolean = false
)

data class ServerMessage(
    val id: Long = 0,
    val sender: String,
    val receiver: String,
    val content: String,
    val mediaUrl: String? = null,
    val timestamp: String,
    val isRead: Boolean = false
)

class ServerDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "intraserver.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_USERS = "users"
        private const val TABLE_MESSAGES = "messages"

        @Volatile
        private var instance: ServerDatabase? = null

        fun getInstance(context: Context): ServerDatabase {
            return instance ?: synchronized(this) {
                instance ?: ServerDatabase(context.applicationContext).also { instance = it }
            }
        }

        fun hashPassword(password: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_USERS (
                username TEXT PRIMARY KEY,
                password_hash TEXT NOT NULL,
                profile_photo TEXT,
                created_at TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'OK'
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_MESSAGES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender TEXT NOT NULL,
                receiver TEXT NOT NULL,
                content TEXT NOT NULL,
                media_url TEXT,
                timestamp TEXT NOT NULL,
                is_read INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MESSAGES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        onCreate(db)
    }

    // ==========================================
    // USER METHODS
    // ==========================================

    @Synchronized
    fun registerUser(username: String, password: String):Pair<Boolean, String> {
        val cleanUser = username.trim()
        if (cleanUser.isEmpty() || password.isEmpty()) {
            return Pair(false, "Username and password cannot be empty")
        }

        val db = writableDatabase
        val cursor = db.query(TABLE_USERS, arrayOf("username"), "LOWER(username) = LOWER(?)", arrayOf(cleanUser), null, null, null)
        val exists = cursor.use { it.moveToFirst() }

        if (exists) {
            return Pair(false, "Username already exists")
        }

        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val values = ContentValues().apply {
            put("username", cleanUser)
            put("password_hash", hashPassword(password))
            put("created_at", now)
            put("status", "OK")
        }

        val rowId = db.insert(TABLE_USERS, null, values)
        return if (rowId != -1L) {
            Pair(true, "User registered successfully")
        } else {
            Pair(false, "Database insert failed")
        }
    }

    @Synchronized
    fun verifyUser(username: String, password: String): String {
        val cleanUser = username.trim()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_USERS,
            arrayOf("password_hash", "status"),
            "LOWER(username) = LOWER(?)",
            arrayOf(cleanUser),
            null, null, null
        )

        cursor.use {
            if (it.moveToFirst()) {
                val storedHash = it.getString(0)
                val status = it.getString(1)
                val inputHash = hashPassword(password)
                if (storedHash == inputHash) {
                    return status // "OK" or "PENDING"
                }
            }
        }
        return "INVALID"
    }

    @Synchronized
    fun getAllUsers(): List<ServerUser> {
        val list = mutableListOf<ServerUser>()
        val db = readableDatabase
        val cursor = db.query(TABLE_USERS, arrayOf("username", "profile_photo", "created_at", "status"), null, null, null, null, "username ASC")

        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    ServerUser(
                        username = it.getString(0),
                        profilePhoto = it.getString(1),
                        createdAt = it.getString(2),
                        status = it.getString(3)
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun updateProfilePhoto(username: String, photoUrl: String): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("profile_photo", photoUrl)
        }
        val count = db.update(TABLE_USERS, values, "LOWER(username) = LOWER(?)", arrayOf(username.trim()))
        return count > 0
    }

    @Synchronized
    fun deleteUser(username: String): Boolean {
        val db = writableDatabase
        val count = db.delete(TABLE_USERS, "LOWER(username) = LOWER(?)", arrayOf(username.trim()))
        return count > 0
    }

    // ==========================================
    // MESSAGE METHODS
    // ==========================================

    @Synchronized
    fun saveMessage(sender: String, receiver: String, content: String, mediaUrl: String? = null, timestamp: String = ""): Long {
        val db = writableDatabase
        val time = if (timestamp.isNotEmpty()) timestamp else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val values = ContentValues().apply {
            put("sender", sender.trim())
            put("receiver", receiver.trim())
            put("content", content)
            put("media_url", mediaUrl)
            put("timestamp", time)
            put("is_read", 0)
        }
        return db.insert(TABLE_MESSAGES, null, values)
    }

    @Synchronized
    fun getRecentMessages(limit: Int = 100): List<ServerMessage> {
        val list = mutableListOf<ServerMessage>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MESSAGES,
            arrayOf("id", "sender", "receiver", "content", "media_url", "timestamp", "is_read"),
            null, null, null, null, "id DESC", limit.toString()
        )

        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    ServerMessage(
                        id = it.getLong(0),
                        sender = it.getString(1),
                        receiver = it.getString(2),
                        content = it.getString(3),
                        mediaUrl = it.getString(4),
                        timestamp = it.getString(5),
                        isRead = it.getInt(6) == 1
                    )
                )
            }
        }
        return list.reversed()
    }
}
