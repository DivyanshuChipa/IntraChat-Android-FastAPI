package com.example.intra

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.intra.database.ChatDao
import com.example.intra.database.ChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
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
) : ViewModel() {

    private val TAG = "ChatVM"

    var onCallSignal: ((String) -> Unit)? = null

    val typingStatuses = mutableStateMapOf<String, Boolean>()
    private val typingJobs = mutableMapOf<String, Job>()

    private var lastTypingSent = 0L
    private val TYPING_THROTTLE = 2000L

    val messages = mutableStateListOf<ChatMessage>()
    val inputMessage = mutableStateOf("")
    val connectionStatus = mutableStateOf("Connecting…")

    var activeChatUser by mutableStateOf<String?>(null)
        private set

    val currentUsername: String
        get() {
            val username = settingsManager.getUsername()
            if (username.isNullOrEmpty() || username == "Guest") {
                Log.e(TAG, "⚠️ No valid username found!")
            }
            return username ?: "Guest"
        }

    // 🔥 FIX 1: WebSocket ko nullable banaya aur reconnect logic add kiya
    private var wsManager: WsManager? = null

    init {
        connectWebSocket()
    }

    // 🔥 NEW: WebSocket connection function with retry
    private fun connectWebSocket() {
        try {
            wsManager = WsManager(
                onMessageReceived = { handleIncomingMessage(it) },
                onConnectionStatusChange = { status ->
                    connectionStatus.value = status

                    // 🔥 Agar disconnected ho gaya, 3 seconds baad retry
                    if (status.contains("Error") || status == "Disconnected") {
                        Log.w(TAG, "🔄 WebSocket disconnected, retrying in 3s...")
                        viewModelScope.launch {
                            delay(3000)
                            if (wsManager == null || connectionStatus.value != "Connected") {
                                reconnectWebSocket()
                            }
                        }
                    }
                }
            )
            wsManager?.connect(currentUsername)
            Log.d(TAG, "✅ WebSocket connection initiated")
        } catch (e: Exception) {
            Log.e(TAG, "❌ WebSocket init failed: ${e.message}")
            connectionStatus.value = "Connection Failed"
        }
    }

    // 🔥 NEW: Reconnect function
    private fun reconnectWebSocket() {
        Log.d(TAG, "🔄 Attempting to reconnect WebSocket...")
        wsManager?.disconnect()
        wsManager = null
        connectWebSocket()
    }

    fun sendTyping(receiver: String) {
        val now = System.currentTimeMillis()
        if (now - lastTypingSent < TYPING_THROTTLE) {
            return
        }

        lastTypingSent = now

        val json = JSONObject().apply {
            put("type", "typing")
            put("sender", currentUsername)
            put("receiver", receiver)
        }

        // 🔥 Safe send with null check
        try {
            wsManager?.sendMessage(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send typing: ${e.message}")
        }
    }

    fun sendCallRequest(receiver: String) {
        val myPhoto = settingsManager.getMyPhoto()

        val json = JSONObject().apply {
            put("type", "call_request")
            put("sender", currentUsername)
            put("receiver", receiver)
            put("profile_photo", myPhoto)
        }

        try {
            wsManager?.sendMessage(json.toString())
            Log.d(TAG, "📞 Call request sent to $receiver with photo: $myPhoto")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send call request: ${e.message}")
        }
    }

    fun openChat(user: String) {
        if (activeChatUser != user) {
            activeChatUser = user
            messages.clear()
            loadMessagesForUser(user)
            Log.d(TAG, "📂 Opened chat with: $user")
        }
    }

    fun closeChat() {
        activeChatUser = null
        messages.clear()
        Log.d(TAG, "📪 Closed chat")
    }

    private fun loadMessagesForUser(user: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val stored = if (user == "Family Group") {
                chatDao.getFamilyGroupMessages()
            } else {
                chatDao.getMessagesForUser(user, currentUsername)
            }

            val uiMessages = stored.map {
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
                messages.addAll(uiMessages)
            }
        }
    }

    fun sendRawSignal(json: String) {
        try {
            wsManager?.sendMessage(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send signal: ${e.message}")
        }
    }

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

        try {
            wsManager?.sendMessage(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message: ${e.message}")
        }
    }

    fun uploadFile(file: File, receiver: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ts = System.currentTimeMillis()

            val body = MultipartBody.Part.createFormData(
                "file",
                file.name,
                file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            )

            val response = ApiClient.apiService.uploadFile(body)
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

            try {
                wsManager?.sendMessage(json.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send file message: ${e.message}")
            }
        }
    }

    private fun handleIncomingMessage(raw: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject(raw)
                val type = json.optString("type")

                // Handle Typing
                if (type == "typing") {
                    val sender = json.optString("sender")

                    withContext(Dispatchers.Main) {
                        typingStatuses[sender] = true
                        typingJobs[sender]?.cancel()

                        typingJobs[sender] = viewModelScope.launch {
                            delay(3000)
                            typingStatuses[sender] = false
                        }
                    }
                    return@launch
                }

                // Handle Call Signals
                if (
                    type == "call_request" ||
                    type == "call_ended" ||
                    type == "call_rejected" ||
                    type == "webrtc_offer" ||
                    type == "webrtc_answer" ||
                    type == "ice_candidate"
                ) {
                    withContext(Dispatchers.Main) {
                        onCallSignal?.invoke(raw)
                    }
                    return@launch
                }

                // --- Message Logic ---
                val sender = json.optString("sender")
                val receiver = json.optString("receiver")
                val ts = json.optLong("timestamp", System.currentTimeMillis())
                val isSelfMsg = sender == currentUsername

                val msg = if (type == "file") {
                    ChatMessage(
                        text = "Shared File: ${json.optString("filename")}",
                        isSelf = isSelfMsg,
                        type = "file",
                        fileUrl = json.optString("url"),
                        fileName = json.optString("filename"),
                        timestamp = ts
                    )
                } else {
                    ChatMessage(
                        text = json.optString("text"),
                        isSelf = isSelfMsg,
                        timestamp = ts
                    )
                }

                saveToDb(msg, sender, receiver)

                // 🔥 FIX 2: Family Group message filtering logic improved
                val currentChat = activeChatUser

                val shouldShowMessage = when {
                    currentChat == null -> false // No chat open

                    // Case 1: Family Group message should only show in Family Group chat
                    receiver == "Family Group" -> currentChat == "Family Group"

                    // Case 2: Private message from sender
                    sender != currentUsername -> currentChat == sender

                    // Case 3: Private message sent by me
                    sender == currentUsername -> currentChat == receiver

                    else -> false
                }

                Log.d(TAG, """
                    📨 Message received:
                    - Type: $type
                    - Sender: $sender
                    - Receiver: $receiver
                    - Current Chat: $currentChat
                    - Should Show: $shouldShowMessage
                """.trimIndent())

                if (shouldShowMessage) {
                    withContext(Dispatchers.Main) {
                        messages.add(msg)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Parse error: ${e.message}", e)
            }
        }
    }

    private fun saveToDb(msg: ChatMessage, sender: String, receiver: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
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
            } catch (e: Exception) {
                Log.e(TAG, "DB save error: ${e.message}", e)
            }
        }
    }

    override fun onCleared() {
        wsManager?.disconnect()
        wsManager = null
        reconnectJob?.cancel()
        super.onCleared()
    }

    // 🔥 NEW: Manual reconnect function (optional, for UI button)
    fun forceReconnect() {
        reconnectWebSocket()
    }

    private var reconnectJob: Job? = null
}
