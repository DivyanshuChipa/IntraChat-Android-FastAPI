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
    val localUri: String? = null,
    val options: List<String>? = null,
    val senderName: String? = null,
    val receiver: String? = null,
    // Ephemeral Utility Fields (Not saved to DB)
    val cpu: String? = null,
    val ram: String? = null,
    val disk: String? = null,
    val status: String? = null,
    val location: String? = null,
    val temp: String? = null,
    val condition: String? = null
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

    fun sendMessage(
        receiver: String,
        lat: Double? = null,
        lon: Double? = null,
        messageText: String? = null
    ) {
        val text = (messageText ?: inputMessage.value).trim()
        if (text.isEmpty()) return

        if (messageText == null || inputMessage.value.trim() == text) {
            inputMessage.value = ""
        }
        val ts = System.currentTimeMillis()

        val json = JSONObject().apply {
            put("type", "text")
            put("sender", currentUsername)
            put("receiver", receiver)
            put("text", text)
            put("timestamp", ts)

            // 📍 NAYA: Dynamic GPS for Weather 2.0
            if (lat != null && lon != null) {
                put("lat", lat)
                put("lon", lon)
            }
        }

        val msg = ChatMessage(
            text = text,
            isSelf = true,
            timestamp = ts,
            senderName = currentUsername,
            receiver = receiver
        )

        messages.add(msg)
        saveToDb(msg, currentUsername, receiver, isRead = true) // My messages are auto-read

        WsManager.send(json.toString())

        viewModelScope.launch {
            _contactUpdateEvent.emit(receiver)
        }
    }

    fun sendOptionCommand(receiver: String, command: String, lat: Double? = null, lon: Double? = null) {
        val ts = System.currentTimeMillis()
        val json = JSONObject().apply {
            put("type", "text")
            put("sender", currentUsername)
            put("receiver", receiver)
            put("text", command)
            put("timestamp", ts)

            // 📍 NAYA: Dynamic GPS for Weather 2.0
            if (lat != null && lon != null) {
                put("lat", lat)
                put("lon", lon)
            }
        }

        val msg = ChatMessage(
            text = command,
            isSelf = true,
            timestamp = ts,
            senderName = currentUsername,
            receiver = receiver
        )

        messages.add(msg)
        saveToDb(msg, currentUsername, receiver, isRead = true)
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
            localUri = file.absolutePath,
            senderName = currentUsername,
            receiver = receiver
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
                isLoading = false,
                senderName = currentUsername,
                receiver = receiver
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
                    timestamp = ts,
                    senderName = sender,
                    receiver = receiver
                )
            } else {
                // Parse options if available
                val optionsList = mutableListOf<String>()
                val optionsJson = json.optJSONArray("options")
                if (optionsJson != null) {
                    for (i in 0 until optionsJson.length()) {
                        optionsList.add(optionsJson.getString(i))
                    }
                }

                ChatMessage(
                    text = json.optString("text"),
                    isSelf = isSelf,
                    timestamp = ts,
                    type = type, // Pass the type (e.g. utility_options)
                    options = if (optionsList.isNotEmpty()) optionsList else null,
                    senderName = sender,
                    receiver = receiver,
                    cpu = if (json.has("cpu")) json.optString("cpu") else null,
                    ram = if (json.has("ram")) json.optString("ram") else null,
                    disk = if (json.has("disk")) json.optString("disk") else null,
                    status = if (json.has("status")) json.optString("status") else null,
                    location = if (json.has("location")) json.optString("location") else null,
                    temp = if (json.has("temp")) json.optString("temp") else null,
                    condition = if (json.has("condition")) json.optString("condition") else null
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
            // Convert options list to JSON String for storage
            val optionsJson = if (msg.options != null) {
                org.json.JSONArray(msg.options).toString()
            } else null

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
                    isRead = isRead,  // 🔥 NEW FIELD
                    options = optionsJson
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

            val ui = stored.map { entity ->
                // Parse options from JSON String
                val optionsList = mutableListOf<String>()
                if (entity.options != null) {
                    try {
                        val jsonArr = org.json.JSONArray(entity.options)
                        for (i in 0 until jsonArr.length()) {
                            optionsList.add(jsonArr.getString(i))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing options", e)
                    }
                }

                ChatMessage(
                    text = entity.text,
                    isSelf = entity.sender == currentUsername,
                    type = entity.type,
                    fileUrl = entity.fileUrl,
                    fileName = entity.fileName,
                    timestamp = entity.timestamp,
                    options = if (optionsList.isNotEmpty()) optionsList else null,
                    senderName = entity.senderName,
                    receiver = entity.receiver
                )
            }

            withContext(Dispatchers.Main) {
                messages.clear()
                messages.addAll(ui)
            }
        }
    }
}
