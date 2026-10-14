package com.example.intraserver.server

import io.ktor.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object ConnectionManager {

    private val sessions = ConcurrentHashMap<String, DefaultWebSocketServerSession>()
    private val mutex = Mutex()

    // Live log listener for Dashboard UI
    var onLogEvent: ((String) -> Unit)? = null

    fun log(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val formatted = "[$timestamp] $message"
        android.util.Log.d("IntraServer", formatted)
        onLogEvent?.invoke(formatted)
    }

    suspend fun connect(username: String, session: DefaultWebSocketServerSession) {
        val cleanUser = username.trim()
        mutex.withLock {
            sessions[cleanUser] = session
        }
        log("👤 User '$cleanUser' connected (Total online: ${sessions.size})")

        // Broadcast presence
        val presence = JSONObject().apply {
            put("type", "user_status")
            put("username", cleanUser)
            put("status", "online")
        }.toString()

        broadcast(presence, exclude = cleanUser)
    }

    suspend fun disconnect(username: String) {
        val cleanUser = username.trim()
        mutex.withLock {
            sessions.remove(cleanUser)
        }
        log("👋 User '$cleanUser' disconnected (Total online: ${sessions.size})")

        // Broadcast presence
        val presence = JSONObject().apply {
            put("type", "user_status")
            put("username", cleanUser)
            put("status", "offline")
        }.toString()

        broadcast(presence, exclude = cleanUser)
    }

    suspend fun sendToUser(username: String, messageText: String): Boolean {
        val session = sessions[username.trim()] ?: return false
        return try {
            session.send(Frame.Text(messageText))
            true
        } catch (e: Exception) {
            log("⚠️ Failed to send message to '$username': ${e.message}")
            false
        }
    }

    suspend fun broadcast(messageText: String, exclude: String? = null) {
        sessions.forEach { (user, session) ->
            if (exclude == null || !user.equals(exclude, ignoreCase = true)) {
                try {
                    session.send(Frame.Text(messageText))
                } catch (_: Exception) {}
            }
        }
    }

    fun isUserOnline(username: String): Boolean {
        return sessions.containsKey(username.trim())
    }

    fun getOnlineUsers(): Set<String> {
        return sessions.keys.toSet()
    }

    fun getOnlineCount(): Int {
        return sessions.size
    }

    fun clearAll() {
        sessions.clear()
    }
}
