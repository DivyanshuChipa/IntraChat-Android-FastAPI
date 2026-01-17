package com.example.intra

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.intra.database.ChatDao
import com.example.intra.database.ChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File

data class ChatMessage(
    val text: String,
    val isSelf: Boolean,
    val type: String = "text",
    val fileUrl: String? = null,
    val fileName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class ChatViewModel(
    private val chatDao: ChatDao,
    private val settingsManager: SettingsManager
) : ViewModel(), WsManager.Listener {

    private val TAG = "ChatViewModel"

    // ===============================
    // 🔔 UI STATE
    // ===============================

    val messages = mutableStateListOf<ChatMessage>()
    val inputMessage = mutableStateOf("")
    val connectionStatus = mutableStateOf("Disconnected")

    val typingStatuses = mutableStateMapOf<String, Boolean>()
    private val typingJobs = mutableMapOf<String, Job>()

    var activeChatUser by mutableStateOf<String?>(null)
        private set

    // Call signal callback (MainActivity set karegi)
    var onCallSignal: ((String) -> Unit)? = null

    private val TYPING_TIMEOUT = 3000L
    private var lastTypingSent = 0L
    private val TYPING_THROTTLE = 2000L

    // ===============================
    // 👤 CURRENT USER
    // ===============================

    val currentUsername: String
        get() = settingsManager.getUsername() ?: "Guest"

    // ===============================
    // 🔌 INIT / CLEANUP
    // ===============================

    init {
        WsManager.addListener(this)
        Log.d(TAG, "Listener attached to WsManager")
    }

    override fun onCleared() {
        WsManager.removeListener(this)
        super.onCleared()
    }

    // ===============================
    // 📩 WS CALLBACKS
    // ===============================

    override fun onMessage(text: String) {
        viewModelScope.launch {
            handleIncomingMessage(text)
        }
    }

    override fun onStatus(status: String) {
        viewModelScope.launch {
            connectionStatus.value = status
        }
    }

    // ===============================
    // 📂 CHAT OPEN / CLOSE
    // ===============================

    fun openChat(user: String) {
        if (activeChatUser == user) return

        activeChatUser = user
        messages.clear()
        loadMessagesFromDb(user)

        Log.d(TAG, "Opened chat with $user")
    }

    fun closeChat() {
        activeChatUser = null
        messages.clear()
        Log.d(TAG, "Chat closed")
    }

    // ===============================
    // 💬 SEND TEXT MESSAGE
    // ===============================

    fun sendMessage(receiver: String) {
        val text = inputMessage.value.trim()
        if (text.isEmpty()) return

        inputMessage.value = ""
        val ts = System.currentTimeMillis()

        val json = JSONObject().apply {
            put("type", "text")
            put("sender", currentUsername)
            put("receiver", receiver)
            put("text", text)
            put("timestamp", ts)
        }

        val msg = ChatMessage(
            text = text,
            isSelf = true,
            timestamp = ts
        )

        messages.add(msg)
        saveToDb(msg, currentUsername, receiver)

        WsManager.send(json.toString())
    }

    // ===============================
    // 📎 SEND FILE
    // ===============================

    fun uploadFile(file: File, receiver: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ts = System.currentTimeMillis()

            val part = MultipartBody.Part.createFormData(
                "file",
                file.name,
                file.asRequestBody()
            )

            val response = ApiClient.apiService.uploadFile(part)
            if (!response.isSuccessful) return@launch

            val raw = response.body()?.string() ?: return@launch
            val json = JSONObject(raw).apply {
                put("type", "file")
                put("sender", currentUsername)
                put("receiver", receiver)
                put("timestamp", ts)
            }

            val msg = ChatMessage(
                text = "Shared File: ${json.optString("filename")}",
                isSelf = true,
                type = "file",
                fileUrl = json.optString("url"),
                fileName = json.optString("filename"),
                timestamp = ts
            )

            withContext(Dispatchers.Main) {
                messages.add(msg)
            }

            saveToDb(msg, currentUsername, receiver)
            WsManager.send(json.toString())
        }
    }

    // ===============================
    // ⌨️ TYPING INDICATOR
    // ===============================

    fun sendTyping(receiver: String) {
        val now = System.currentTimeMillis()
        if (now - lastTypingSent < TYPING_THROTTLE) return

        lastTypingSent = now

        val json = JSONObject().apply {
            put("type", "typing")
            put("sender", currentUsername)
            put("receiver", receiver)
        }

        WsManager.send(json.toString())
    }
    // ===============================
// 📞 CALL REQUEST (Outgoing)
// ===============================
    fun sendCallRequest(receiver: String) {
        val myPhoto = settingsManager.getMyPhoto()
        val json = JSONObject().apply {
            put("type", "call_request")
            put("sender", currentUsername)
            put("receiver", receiver)
            // 🔥 FIX: Photo URL bhi bhejo taaki samne wale ko dikhe
            put("profile_photo", myPhoto)
        }
        WsManager.send(json.toString())
    }

    // ===============================
// 📡 RAW SIGNAL (WebRTC / Call)
// ===============================
    fun sendRawSignal(rawJson: String) {
        WsManager.send(rawJson)
    }

    // ===============================
    // 📥 HANDLE INCOMING DATA
    // ===============================

    private suspend fun handleIncomingMessage(raw: String) {
        try {
            val json = JSONObject(raw)
            val type = json.optString("type")
            val sender = json.optString("sender")
            val receiver = json.optString("receiver")
            val ts = json.optLong("timestamp", System.currentTimeMillis())

            // ⌨️ Typing
            if (type == "typing") {
                withContext(Dispatchers.Main) {
                    typingStatuses[sender] = true
                    typingJobs[sender]?.cancel()
                    typingJobs[sender] = viewModelScope.launch {
                        delay(TYPING_TIMEOUT)
                        typingStatuses[sender] = false
                    }
                }
                return
            }

            // 📞 Call / WebRTC signals
            if (
                type.startsWith("call_") ||
                type.startsWith("webrtc_") ||
                type == "ice_candidate"
            ) {
                withContext(Dispatchers.Main) {
                    onCallSignal?.invoke(raw)
                }
                return
            }

            // 💬 Message
            val isSelf = sender == currentUsername
            // 🔥 FIX 1: Check if message already exists in RAM (UI List)
            // J2 jaise slow phone par DB se load hone ke baad bhi Socket event fire ho sakta hai
            val alreadyExists = messages.any { it.timestamp == ts && it.text == json.optString("text") }

            if (alreadyExists) {
                Log.d(TAG, "🚫 Duplicate message prevented in UI: $ts")
                return // Yahi ruk jao, aage mat badho
            }

            val msg = if (type == "file") {
                ChatMessage(
                    text = "Shared File: ${json.optString("filename")}",
                    isSelf = isSelf,
                    type = "file",
                    fileUrl = json.optString("url"),
                    fileName = json.optString("filename"),
                    timestamp = ts
                )
            } else {
                ChatMessage(
                    text = json.optString("text"),
                    isSelf = isSelf,
                    timestamp = ts
                )
            }

            // 🔥 FIX 2: Save to DB only if necessary (DB logic niche step 2 me hai)
            saveToDb(msg, sender, json.optString("receiver"))

            val shouldShow = when {
                activeChatUser == null -> false
                receiver == "Family Group" -> activeChatUser == "Family Group"
                sender != currentUsername -> activeChatUser == sender
                else -> activeChatUser == receiver
            }

            if (shouldShow) {
                withContext(Dispatchers.Main) {
                    messages.add(msg)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}", e)
        }
    }

    // ===============================
    // 💾 DATABASE
    // ===============================

    private fun saveToDb(msg: ChatMessage, sender: String, receiver: String) {
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.insertMessage(
                ChatMessageEntity(
                    text = msg.text,
                    isSelf = msg.isSelf,
                    type = msg.type,
                    fileUrl = msg.fileUrl,
                    fileName = msg.fileName,
                    senderName = sender,
                    sender = sender,
                    receiver = receiver,
                    timestamp = msg.timestamp
                )
            )
        }
    }

    private fun loadMessagesFromDb(user: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val stored = if (user == "Family Group") {
                chatDao.getFamilyGroupMessages()
            } else {
                chatDao.getMessagesForUser(user, currentUsername)
            }

            val ui = stored.map {
                ChatMessage(
                    text = it.text,
                    isSelf = it.sender == currentUsername,
                    type = it.type,
                    fileUrl = it.fileUrl,
                    fileName = it.fileName,
                    timestamp = it.timestamp
                )
            }

            withContext(Dispatchers.Main) {
                messages.clear()
                messages.addAll(ui)
            }
        }
    }
}
