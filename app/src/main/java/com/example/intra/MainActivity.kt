package com.example.intra

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.intra.database.ChatDatabase
import com.example.intra.ui.theme.IntraTheme
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private var currentUploadViewModel: ChatViewModel? = null
    private var currentUploadReceiver: String? = null

    // 🔥 Managers ko class level pe rakho (Compose ke andar nahi)
    private lateinit var proximitySensor: ProximitySensor
    private lateinit var ringtoneManager: CallRingtoneManager

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val file = uriToTempFile(this, it)
            if (file != null && currentUploadViewModel != null && currentUploadReceiver != null) {
                currentUploadViewModel?.uploadFile(file, currentUploadReceiver!!)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Initialize Managers
        proximitySensor = ProximitySensor(this)
        ringtoneManager = CallRingtoneManager(this)

        val chatDao = ChatDatabase.getDatabase(MyApplication.instance).chatDao()
        val settingsManager = SettingsManager(this)
        val chatViewModelFactory = ChatViewModelFactory(chatDao, settingsManager)

        setContent {
            IntraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val authViewModel: AuthViewModel = viewModel()
                    val chatViewModel: ChatViewModel = viewModel(factory = chatViewModelFactory)
                    val callViewModel: CallViewModel = viewModel()
                     // 🔥 CHANGE 1: ContactViewModel yahan chahiye taaki user ki detail mil sake
                    val contactViewModel: ContactViewModel = viewModel()
                    // WebRTC Client
                    val webRTCClient = remember {
                        WebRTCClient(
                            context = applicationContext,
                            sendSignal = { json -> chatViewModel.sendRawSignal(json) }
                        )
                    }

                    val isAuthenticated by authViewModel.isAuthenticated
                    var showSettings by remember { mutableStateOf(false) }
                    var currentChatReceiver by remember { mutableStateOf<String?>(null) }

                    // 🔔 RINGTONE LOGIC (Clean & Rotation Safe)
                    // LaunchedEffect jab bhi isRinging change hoga tab chalega.
                    // Rotation ke baad ye dobara chalega, lekin hamara Manager check kar lega ki sound baj rahi hai ya nahi.
                    LaunchedEffect(callViewModel.isRinging.value) {
                        if (callViewModel.isRinging.value) {
                            ringtoneManager.start()
                        } else {
                            ringtoneManager.stop()
                        }
                    }

                    // 📶 SIGNAL HANDLER
                    LaunchedEffect(Unit) {
                        chatViewModel.onCallSignal = { raw ->
                            try {
                                val json = JSONObject(raw)
                                when (json.optString("type")) {
                                    "call_request" -> {
                                        val sender = json.optString("sender")

                                        // 1. JSON se raw path nikalo (e.g., "/uploads/photo.png")
                                        val rawPhoto = json.optString("profile_photo")

                                        // 2. Full URL banao
                                        val fullPhotoUrl = if (rawPhoto.isNotEmpty() && rawPhoto != "null") {
                                            val settingsManager = SettingsManager(applicationContext)
                                            settingsManager.getBaseUrl().removeSuffix("/") + rawPhoto
                                        } else {
                                            null
                                        }

                                        // 3. CallViewModel ko pass karo (Naam + Photo)
                                        callViewModel.onIncomingCall(sender, fullPhotoUrl)

                                        Log.d("CALL_FLOW", "📲 Incoming call from $sender, Photo: $fullPhotoUrl")
                                    }
                                    "webrtc_offer" -> callViewModel.setIncomingOffer(json.optString("sdp"))
                                    "webrtc_answer" -> {
                                        webRTCClient.onRemoteAnswer(json.optString("sdp"))
                                        callViewModel.onCallConnected()
                                    }
                                    "ice_candidate" -> webRTCClient.onRemoteIceCandidate(
                                        json.getString("candidate"), json.getString("sdpMid"), json.getInt("sdpMLineIndex")
                                    )
                                }
                            } catch (e: Exception) { Log.e("Signal", "Error: $e") }
                        }
                    }

                    if (!isAuthenticated) {
                        AuthScreen(viewModel = authViewModel, onAuthenticated = { })
                    } else {
                        when {
                            showSettings -> SettingsScreen(
                                onLogoutConfirmed = { authViewModel.logout(); showSettings = false; currentChatReceiver = null },
                                onBack = { showSettings = false }
                            )

                            // 📞 CALL SCREEN
                            callViewModel.callState.value.status != CallStatus.IDLE -> {
                                CallScreen(
                                    state = callViewModel.callState.value,

                                    onEndCall = {
                                        webRTCClient.endCall()
                                        callViewModel.onCallEnded()
                                        // Cleanup
                                        proximitySensor.deactivate()
                                        ringtoneManager.stop()
                                    },

                                    onAcceptCall = {
                                        val offer = callViewModel.pendingOfferSdp
                                        if (offer != null) {
                                            webRTCClient.answerCall(callViewModel.callState.value.targetUser, offer)
                                            callViewModel.onCallConnected()
                                            // Connected -> Ringtone Stop
                                            ringtoneManager.stop()

                                            // Speaker Default ON hai, isliye Proximity DEACTIVATE (Screen ON rahegi)
                                            proximitySensor.deactivate()
                                        }
                                    },

                                    onToggleMute = {
                                        val newMute = !callViewModel.callState.value.isMuted
                                        webRTCClient.toggleMute(newMute)
                                        callViewModel.updateMuteState(newMute)
                                    },

                                    // 👂 SPEAKER TOGGLE
                                    onToggleSpeaker = {
                                        val newState = !callViewModel.callState.value.isSpeakerOn

                                        webRTCClient.toggleSpeaker(newState)
                                        callViewModel.updateSpeakerState(newState)

                                        if (newState) {
                                            // 🔊 Speaker ON: Screen ON rakho
                                            proximitySensor.deactivate()
                                        } else {
                                            // 👂 Earpiece: Sensor ON karo (Kaan pe lagate hi screen OFF)
                                            proximitySensor.activate()
                                        }
                                    }
                                )
                            }

                            currentChatReceiver != null -> ChatScreen(
                                viewModel = chatViewModel, receiverName = currentChatReceiver!!,
                                onAttachClick = { currentUploadViewModel = chatViewModel; currentUploadReceiver = currentChatReceiver; filePickerLauncher.launch("*/*") },
                                onBackClick = { chatViewModel.closeChat(); currentChatReceiver = null },
                                onStartCall = {
                                    val targetUser = currentChatReceiver!!

                                    // 1. Contact list me se user dhundo
                                    val contact = contactViewModel.contacts.find { it.username == targetUser }

                                    // 2. Agar photo hai, to full URL banao
                                    val fullPhotoUrl = if (contact?.profilePhoto != null) {
                                        settingsManager.getBaseUrl().removeSuffix("/") + contact.profilePhoto
                                    } else {
                                        null
                                    }

                                    // 3. Call request me photo bhejo (optional logic inside VM)
                                    chatViewModel.sendCallRequest(targetUser)

                                    // 4. View Model ko photo URL pass karo
                                    callViewModel.onStartOutgoingCall(targetUser, fullPhotoUrl)

                                    webRTCClient.startCall(targetUser)
                                    proximitySensor.deactivate()
                                }
                            )

                            else -> ContactListScreen(
                                username = chatViewModel.currentUsername, typingStatuses = chatViewModel.typingStatuses,
                                onChatClick = { currentChatReceiver = it }, onSettingsClick = { showSettings = true }
                            )
                        }
                    }
                }
            }
        }
    }

    // ✅ IMPORTANT: Agar app puri tarah band ho jaye to cleanup karo
    override fun onDestroy() {
        super.onDestroy()
        proximitySensor.deactivate()
        ringtoneManager.stop()
    }

    fun uriToTempFile(context: Context, uri: Uri): File? {
        val contentResolver = context.contentResolver
        var fileName: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) fileName = cursor.getString(index)
            }
        }
        if (fileName == null) fileName = "upload_${System.currentTimeMillis()}"
        val tempFile = File(context.cacheDir, fileName!!)
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.e("MainActivity", "File error", e)
            null
        }
    }
}