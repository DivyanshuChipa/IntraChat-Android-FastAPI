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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    val timestamp: Long = System.currentTimeMillis(),
    val isLoading: Boolean = false,
    val localUri: String? = null
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
    // 🆕 STEP 5A: REAL-TIME UPDATE EVENT
    // ===============================

    // Private mutable flow (internal use)
    private val _contactUpdateEvent = MutableSharedFlow<String>()

    // Public read-only flow (MainActivity subscribe karega)
    val contactUpdateEvent: SharedFlow<String> = _contactUpdateEvent.asSharedFlow()

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

        // 🔥 FIXED: Mark messages as read when chat opens
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.markMessagesAsRead(user, currentUsername)

            // Log for debugging
            val unreadCount = chatDao.getUnreadCount(user, currentUsername)
            Log.d(TAG, "✅ Chat opened: $user, Unread after mark: $unreadCount")

            // Emit event to refresh contact list
            _contactUpdateEvent.emit(user)
        }

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
        saveToDb(msg, currentUsername, receiver, isRead = true) // My messages are auto-read

        WsManager.send(json.toString())

        viewModelScope.launch {
            _contactUpdateEvent.emit(receiver)
        }
    }

    // ===============================
    // 📎 SEND FILE
    // ===============================

    /**
     * Internal suspend function to handle sequential file uploads
     */
    private suspend fun uploadFileInternal(file: File, receiver: String) {
        val ts = System.currentTimeMillis()

        // 🔥 OPTIMISTIC UI: Show uploading state
        val tempMsg = ChatMessage(
            text = "Uploading: ${file.name}",
            isSelf = true,
            type = "file",
            fileName = file.name,
            timestamp = ts,
            isLoading = true,
            localUri = file.absolutePath
        )

        withContext(Dispatchers.Main) {
            messages.add(tempMsg)
        }

        try {
            val part = MultipartBody.Part.createFormData(
                "file",
                file.name,
                file.asRequestBody()
            )

            val response = ApiClient.apiService.uploadFile(part)
            if (!response.isSuccessful) {
                Log.e(TAG, "❌ Upload failed for ${file.name}: ${response.code()}")
                withContext(Dispatchers.Main) { messages.remove(tempMsg) }
                return
            }

            val raw = response.body()?.string() ?: return
            val json = JSONObject(raw).apply {
                put("type", "file")
                put("sender", currentUsername)
                put("receiver", receiver)
                put("timestamp", ts)
            }

            val finalMsg = ChatMessage(
                text = "Shared File: ${json.optString("filename")}",
                isSelf = true,
                type = "file",
                fileUrl = json.optString("url"),
                fileName = json.optString("filename"),
                timestamp = ts,
                isLoading = false
            )

            withContext(Dispatchers.Main) {
                // Replace temp message with final message
                val index = messages.indexOf(tempMsg)
                if (index != -1) {
                    messages[index] = finalMsg
                } else {
                    messages.add(finalMsg)
                }
            }

            saveToDb(finalMsg, currentUsername, receiver, isRead = true)
            WsManager.send(json.toString())
            _contactUpdateEvent.emit(receiver)

            Log.d(TAG, "✅ File uploaded and signal sent: ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error uploading file ${file.name}", e)
            withContext(Dispatchers.Main) { messages.remove(tempMsg) }
        }
    }

    /**
     * Single file upload (maintains compatibility)
     */
    fun uploadFile(file: File, receiver: String) {
        viewModelScope.launch(Dispatchers.IO) {
            uploadFileInternal(file, receiver)
        }
    }

    /**
     * Multiple file upload - Sequential
     */
    fun uploadMultipleFiles(files: List<File>, receiver: String) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "🚀 Starting sequential upload of ${files.size} files")
            files.forEachIndexed { index, file ->
                Log.d(TAG, "📤 Uploading file ${index + 1}/${files.size}: ${file.name}")
                uploadFileInternal(file, receiver)
            }
            Log.d(TAG, "🏁 All files processed")
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
    // 🔥 HANDLE INCOMING DATA
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

            val alreadyExists = messages.any {
                it.timestamp == ts && it.text == json.optString("text")
            }

            if (alreadyExists) {
                Log.d(TAG, "🚫 Duplicate message prevented in UI: $ts")
                return
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

           // saveToDb(msg, sender, json.optString("receiver"))

            val isAlreadyRead = (activeChatUser == sender)
            saveToDb(msg, sender, json.optString("receiver"), isRead = isAlreadyRead)

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

            if (!isSelf) {
                _contactUpdateEvent.emit(sender)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}", e)
        }
    }

    // ===============================
    // 💾 DATABASE
    // ===============================

    private fun saveToDb(
        msg: ChatMessage,
        sender: String,
        receiver: String,
        isRead: Boolean = false // 🔥 NEW PARAMETER
    ) {
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
                    timestamp = msg.timestamp,
                    isRead = isRead  // 🔥 NEW FIELD
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