package com.example.intraserver.server

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object ConnectionManager {

    private val sessions = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()
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
            val userSessions = sessions.getOrPut(cleanUser) { ConcurrentHashMap.newKeySet() }
            userSessions.add(session)
        }
        log("👤 User '$cleanUser' connected (Total users online: ${sessions.size})")

        // Broadcast presence
        val presence = JSONObject().apply {
            put("type", "user_status")
            put("username", cleanUser)
            put("status", "online")
        }.toString()

        broadcast(presence, excludeUser = cleanUser)
    }

    suspend fun disconnect(username: String, session: DefaultWebSocketServerSession) {
        val cleanUser = username.trim()
        var userFullyOffline = false
        mutex.withLock {
            val userSessions = sessions[cleanUser]
            if (userSessions != null) {
                userSessions.remove(session)
                if (userSessions.isEmpty()) {
                    sessions.remove(cleanUser)
                    userFullyOffline = true
                }
            }
        }
        log("👋 User '$cleanUser' disconnected (Total users online: ${sessions.size})")

        if (userFullyOffline) {
            val presence = JSONObject().apply {
                put("type", "user_status")
                put("username", cleanUser)
                put("status", "offline")
            }.toString()

            broadcast(presence, excludeUser = cleanUser)
        }
    }

    private fun findSessionsForUser(username: String): MutableSet<DefaultWebSocketServerSession>? {
        val clean = username.trim()
        val exact = sessions[clean]
        if (exact != null) return exact
        return sessions.entries.firstOrNull { it.key.equals(clean, ignoreCase = true) }?.value
    }

    suspend fun sendToUser(username: String, messageText: String, excludeSession: DefaultWebSocketServerSession? = null): Boolean {
        val userSessions = findSessionsForUser(username) ?: return false
        var sentCount = 0
        for (session in userSessions) {
            if (session == excludeSession) continue
            try {
                session.send(Frame.Text(messageText))
                sentCount++
            } catch (e: Exception) {
                log("⚠️ Failed to send to '$username': ${e.message}")
            }
        }
        return sentCount > 0
    }

    suspend fun broadcast(messageText: String, excludeUser: String? = null, excludeSession: DefaultWebSocketServerSession? = null) {
        sessions.forEach { (user, userSessions) ->
            if (excludeUser == null || !user.equals(excludeUser, ignoreCase = true)) {
                for (session in userSessions) {
                    if (session == excludeSession) continue
                    try {
                        session.send(Frame.Text(messageText))
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun isUserOnline(username: String): Boolean {
        val clean = username.trim()
        return sessions.keys.any { it.equals(clean, ignoreCase = true) }
    }

    fun getOnlineUsers(): Set<String> {
        return sessions.keys.toSet()
    }

    fun getOnlineCount(): Int {
        return sessions.size
    }

    // 👑 ADMIN: User ko forcefully disconnect karne ke liye
    suspend fun kickUser(username: String, reason: String = "Disconnected by admin") {
        val clean = username.trim()
        val userSessions = findSessionsForUser(clean) ?: return
        for (session in userSessions) {
            try {
                session.close(io.ktor.websocket.CloseReason(io.ktor.websocket.CloseReason.Codes.VIOLATED_POLICY, reason))
            } catch (_: Exception) {}
        }
        sessions.remove(clean)
        log("🚫 User '$clean' was kicked by Admin")
        val presence = JSONObject().apply {
            put("type", "user_status")
            put("username", clean)
            put("status", "offline")
        }.toString()
        broadcast(presence, excludeUser = clean)
    }

    fun clearAll() {
        sessions.clear()
    }
}
