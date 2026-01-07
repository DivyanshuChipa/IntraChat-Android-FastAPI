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

// ---------------- UI MODEL ----------------
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

    // ✅ STEP-3 FIX: ChatViewModel ko signal forwarder banana hoga
    // 1️⃣ ye property add karo (top me)
    var onCallSignal: ((String) -> Unit)? = null

    val typingStatuses = mutableStateMapOf<String, Boolean>()
    private val typingJobs = mutableMapOf<String, Job>()

    private var lastTypingSent = 0L
    private val TYPING_THROTTLE = 2000L // 2 seconds

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

    private val wsManager = WsManager(
        onMessageReceived = { handleIncomingMessage(it) },
        onConnectionStatusChange = { connectionStatus.value = it }
    )

    init {
        wsManager.connect(currentUsername)
    }

    // ✅ Send typing event with throttle
    fun sendTyping(receiver: String) {
        val now = System.currentTimeMillis()
        if (now - lastTypingSent < TYPING_THROTTLE) {
            return // Skip if sent recently
        }

        lastTypingSent = now

        val json = JSONObject().apply {
            put("type", "typing")
            put("sender", currentUsername)
            put("receiver", receiver)
        }
        wsManager.sendMessage(json.toString())
    }

    // 📞 NEW: Send Call Request
    // ChatViewModel.kt ke andar

    fun sendCallRequest(receiver: String) {
        // 1. Khud ki photo nikalo settings se
        val myPhoto = settingsManager.getMyPhoto() // ye relative path dega (/uploads/...)

        val json = JSONObject().apply {
            put("type", "call_request")
            put("sender", currentUsername)
            put("receiver", receiver)
            // 🔥 NEW: Apni photo bhi bhejo
            put("profile_photo", myPhoto)
        }
        wsManager.sendMessage(json.toString())
        Log.d(TAG, "📞 Call request sent to $receiver with photo: $myPhoto")
    }

    // ---------------- CHAT OPEN / CLOSE ----------------
    fun openChat(user: String) {
        if (activeChatUser != user) {
            activeChatUser = user
            messages.clear()
            loadMessagesForUser(user)
        }
    }

    fun closeChat() {
        activeChatUser = null
        messages.clear()
    }

    // ---------------- LOAD FROM DB ----------------
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
        wsManager.sendMessage(json)
    }
    // ---------------- SEND TEXT ----------------
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

        wsManager.sendMessage(json.toString())
    }

    // ---------------- SEND FILE ----------------
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
            wsManager.sendMessage(json.toString())
        }
    }

    // ---------------- RECEIVE ----------------
    private fun handleIncomingMessage(raw: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject(raw)
                val type = json.optString("type")

                // 🔥 Handle Typing
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

                // 📞 2️⃣ handleIncomingMessage() me change 👇
                // 💡 Ab ViewModel bolega: “Mujhe call ka matlab nahi pata, MainActivity tu dekh le”
                if (
                    type == "call_request" ||
                    type == "call_ended" ||  // yeah end call signal forward hoga
                    type == "webrtc_offer" ||
                    type == "webrtc_answer" ||
                    type == "ice_candidate"
                ) {
                    withContext(Dispatchers.Main) {
                        onCallSignal?.invoke(raw)
                    }
                    return@launch
                }


                // --- Existing Message Logic ---
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

                val show = activeChatUser?.let {
                    receiver == "Family Group" || sender == it || receiver == it
                } ?: false

                if (show) {
                    withContext(Dispatchers.Main) {
                        messages.add(msg)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Parse error", e)
            }
        }
    }

    // ---------------- SAVE TO DB ----------------
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

    override fun onCleared() {
        wsManager.disconnect()
        super.onCleared()
    }
}