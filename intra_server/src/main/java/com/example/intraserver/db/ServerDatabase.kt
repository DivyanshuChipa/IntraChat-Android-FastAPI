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

data class AiConfig(
    val aiEnabled: Boolean = false,
    val aiModel: String = "gemma2:2b",
    val ollamaUrl: String = "http://192.168.1.35:11434",
    val aiVisionModel: String = "gemma:27b-cloud",
    val aiFallback: String = "gemma3:270m"
)

class ServerDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "intraserver.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_USERS = "users"
        private const val TABLE_MESSAGES = "messages"
        private const val TABLE_CONFIG = "config"

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

        ensureConfigTable(db)
    }

    // 🛠️ Safe helper taaki purane install me agar config table na ho toh crash na ho
    private fun ensureConfigTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_CONFIG (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CONFIG")
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
        val initialStatus = if (getRequireApproval()) "PENDING" else "OK"
        val values = ContentValues().apply {
            put("username", cleanUser)
            put("password_hash", hashPassword(password))
            put("created_at", now)
            put("status", initialStatus)
        }

        val rowId = db.insert(TABLE_USERS, null, values)
        return if (rowId != -1L) {
            val msg = if (initialStatus == "PENDING") "Registration submitted. Pending admin approval." else "User registered successfully"
            Pair(true, msg)
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

    // 🛡️ SECURITY CHECK: User registered aur approved hai ya nahi
    @Synchronized
    fun getUserStatus(username: String): String? {
        val cleanUser = username.trim()
        if (cleanUser.isEmpty()) return null
        val db = readableDatabase
        val cursor = db.query(
            TABLE_USERS,
            arrayOf("status"),
            "LOWER(username) = LOWER(?)",
            arrayOf(cleanUser),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0) // "OK" ya "PENDING"
            }
        }
        return null // User database me exist hi nahi karta!
    }

    @Synchronized
    fun isUserApproved(username: String): Boolean {
        return getUserStatus(username) == "OK"
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

    // 👑 ADMIN: User ko approve karne ke liye (status = "OK")
    @Synchronized
    fun approveUser(username: String): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("status", "OK")
        }
        val count = db.update(TABLE_USERS, values, "LOWER(username) = LOWER(?)", arrayOf(username.trim()))
        return count > 0
    }

    // 👑 ADMIN: Kisi user ka password reset karne ke liye
    @Synchronized
    fun resetPassword(username: String, newPass: String): Boolean {
        if (newPass.isEmpty()) return false
        val db = writableDatabase
        val values = ContentValues().apply {
            put("password_hash", hashPassword(newPass))
        }
        val count = db.update(TABLE_USERS, values, "LOWER(username) = LOWER(?)", arrayOf(username.trim()))
        return count > 0
    }

    // ==========================================
    // ⚙️ CONFIG & ADMIN KEY METHODS
    // ==========================================

    @Synchronized
    fun getConfig(key: String, default: String): String {
        val db = readableDatabase
        ensureConfigTable(db)
        val cursor = db.query(TABLE_CONFIG, arrayOf("value"), "key = ?", arrayOf(key), null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return default
    }

    @Synchronized
    fun setConfig(key: String, value: String) {
        val db = writableDatabase
        ensureConfigTable(db)
        val values = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        db.insertWithOnConflict(TABLE_CONFIG, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // 🔑 ADMIN KEY: Unique secret key for PC admin panel / API auth
    @Synchronized
    fun getAdminKey(): String {
        val existing = getConfig("admin_key", "")
        if (existing.isNotEmpty()) return existing

        // Nayi 16-character alphanumeric key generate karo
        val newKey = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        setConfig("admin_key", newKey)
        return newKey
    }

    @Synchronized
    fun regenerateAdminKey(): String {
        val newKey = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        setConfig("admin_key", newKey)
        return newKey
    }

    @Synchronized
    fun verifyAdminKey(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        val currentKey = getAdminKey().trim()
        return key.trim() == currentKey
    }

    // 🛡️ Registration approval toggle (FastAPI parity)
    @Synchronized
    fun getRequireApproval(): Boolean {
        return getConfig("require_approval", "false").toBoolean()
    }

    @Synchronized
    fun setRequireApproval(enabled: Boolean) {
        setConfig("require_approval", enabled.toString())
    }

    // 📡 Server Discovery toggle
    @Synchronized
    fun getDiscoveryEnabled(): Boolean {
        return getConfig("discovery_enabled", "true").toBoolean()
    }

    @Synchronized
    fun setDiscoveryEnabled(enabled: Boolean) {
        setConfig("discovery_enabled", enabled.toString())
    }

    // ==========================================
    // 🤖 AI & LUMIR BOT CONFIG (FastAPI Parity)
    // ==========================================

    @Synchronized
    fun getAiConfig(): AiConfig {
        return AiConfig(
            aiEnabled = getConfig("ai_enabled", "false").toBoolean(),
            aiModel = getConfig("ai_model", "gemma3:270m"),
            ollamaUrl = getConfig("ollama_url", "http://192.168.31.102:11434"),
            aiVisionModel = getConfig("ai_vision_model", "gemma:27b-cloud"),
            aiFallback = getConfig("ai_fallback", "gemma3:270m")
        )
    }

    @Synchronized
    fun setAiConfig(
        enabled: Boolean,
        model: String,
        url: String,
        visionModel: String = "gemma:27b-cloud",
        fallback: String = "gemma3:270m"
    ) {
        setConfig("ai_enabled", enabled.toString())
        setConfig("ai_model", model.trim())
        setConfig("ollama_url", url.trim())
        setConfig("ai_vision_model", visionModel.trim())
        setConfig("ai_fallback", fallback.trim())
    }

    // 🧹 Purane messages delete karna (e.g. 7 ya 30 din se purane)
    @Synchronized
    fun cleanupOldMessages(days: Int): Int {
        if (days <= 0) return 0
        val db = writableDatabase
        // SQLite date formatting: datetime('now', '-X days')
        val count = db.delete(
            TABLE_MESSAGES,
            "datetime(timestamp) < datetime('now', '-$days days')",
            null
        )
        return count
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

    @Synchronized
    fun getUndeliveredMessages(username: String): List<ServerMessage> {
        val list = mutableListOf<ServerMessage>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MESSAGES,
            arrayOf("id", "sender", "receiver", "content", "media_url", "timestamp", "is_read"),
            "LOWER(receiver) = LOWER(?) AND is_read = 0",
            arrayOf(username.trim()),
            null, null, "id ASC", "50"
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
        return list
    }

    @Synchronized
    fun markMessageAsRead(id: Long) {
        val db = writableDatabase
        val values = ContentValues().apply { put("is_read", 1) }
        db.update(TABLE_MESSAGES, values, "id = ?", arrayOf(id.toString()))
    }
}
