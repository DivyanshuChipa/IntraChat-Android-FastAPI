package com.example.intra

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable // 🔥 NEW IMPORT
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
    private lateinit var messageReceiver: BroadcastReceiver
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
                    var showSettings by rememberSaveable { mutableStateOf(false) }
                    var showAbout by rememberSaveable { mutableStateOf(false) } // ✅ NEW STATE FOR ABOUT SCREEN
                    var currentChatReceiver by rememberSaveable { mutableStateOf<String?>(null) }

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
                                        val rawPhoto = json.optString("profile_photo")
                                        val fullPhotoUrl =
                                            if (rawPhoto.isNotEmpty() && rawPhoto != "null") {
                                                val settingsManager =
                                                    SettingsManager(applicationContext)
                                                settingsManager.getBaseUrl()
                                                    .removeSuffix("/") + rawPhoto
                                            } else null

                                        callViewModel.onIncomingCall(sender, fullPhotoUrl)
                                        Log.d("CALL_FLOW", "📲 Incoming call from $sender")
                                    }

                                    // 🔥 NEW: Handle call_rejected signal
                                    "call_rejected" -> {
                                        Log.d("CALL_FLOW", "📵 Call was rejected by remote user")

                                        // WebRTC cleanup
                                        webRTCClient.endCall()

                                        // UI cleanup
                                        callViewModel.onCallEnded()

                                        // Audio cleanup
                                        proximitySensor.deactivate()
                                        ringtoneManager.stop()
                                    }

                                    "call_ended" -> {
                                        Log.d("CALL_FLOW", "📵 Call ended by remote user")
                                        webRTCClient.endCall()
                                        callViewModel.onCallEnded()
                                        proximitySensor.deactivate()
                                        ringtoneManager.stop()
                                    }

                                    "webrtc_offer" -> {
                                        callViewModel.setIncomingOffer(json.optString("sdp"))
                                    }

                                    "webrtc_answer" -> {
                                        webRTCClient.onRemoteAnswer(json.optString("sdp"))
                                        callViewModel.onCallConnected()
                                    }

                                    "ice_candidate" -> {
                                        webRTCClient.onRemoteIceCandidate(
                                            json.getString("candidate"),
                                            json.getString("sdpMid"),
                                            json.getInt("sdpMLineIndex")
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("Signal", "Error: $e")
                            }
                        }
                    }

                    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

                    DisposableEffect(lifecycleOwner) {
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                // Jab bhi App Resume ho, check karo koi call pending hai kya
                                CallEventBus.consume()?.let { raw ->
                                    Log.d("MainActivity", "⚡ Consuming Pending Call from EventBus")
                                    // Manually trigger the signal logic
                                    chatViewModel.onCallSignal?.invoke(raw)
                                }
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    //🔥 NEW: Check for pending calls (Notification tap)


                    if (!isAuthenticated) {
                        AuthScreen(viewModel = authViewModel, onAuthenticated = { })
                    } else {
                        // 👇 MAIN NAVIGATION LOGIC
                        when {
                            // ✅ 1. Show About Screen (Top Priority if true)
                            showAbout -> AboutScreen(
                                onBack = { showAbout = false }
                            )

                            // ✅ 2. Show Settings Screen
                            showSettings -> SettingsScreen(
                                onLogoutConfirmed = {
                                    authViewModel.logout()
                                    showSettings = false
                                    currentChatReceiver = null
                                },
                                onBack = { showSettings = false },
                                // 👇 YEH LINE ADD KARNI THI (Red line fix)
                                onNavigateToAbout = { showAbout = true }
                            )

                            // 📞 CALL SCREEN
                            callViewModel.callState.value.status != CallStatus.IDLE -> {
                                CallScreen(
                                    state = callViewModel.callState.value,

                                    // 🔥 NEW: Reject Call Logic (Incoming red button)
                                    onRejectCall = {
                                        val targetUser = callViewModel.callState.value.targetUser

                                        // 1️⃣ Signal bhejo dusre phone ko
                                        if (targetUser.isNotEmpty()) {
                                            val json = JSONObject().apply {
                                                put("type", "call_rejected")
                                                put("receiver", targetUser)
                                            }
                                            chatViewModel.sendRawSignal(json.toString())
                                            Log.d(
                                                "CALL_REJECT",
                                                "📵 Call rejected signal sent to $targetUser"
                                            )
                                        }

                                        // 2️⃣ UI & Audio cleanup (Same as End Call)
                                        callViewModel.onCallEnded()
                                        proximitySensor.deactivate()
                                        ringtoneManager.stop()

                                        // 3️⃣ WebRTC cleanup (Safe)
                                        try {
                                            webRTCClient.endCall()
                                        } catch (e: Exception) {
                                            Log.e("RejectCall", "Cleanup Error: ${e.message}")
                                        }
                                    },

                                    // ✅ End Call Logic (Connected/Outgoing wala - Same as before)
                                    onEndCall = {
                                        callViewModel.onCallEnded()
                                        proximitySensor.deactivate()
                                        ringtoneManager.stop()

                                        try {
                                            webRTCClient.endCall()
                                        } catch (e: Exception) {
                                            Log.e("EndCall", "WebRTC Cleanup Error: ${e.message}")
                                        }
                                    },

                                    // ✅ Accept Call Logic (Same)
                                    onAcceptCall = {
                                        val offer = callViewModel.pendingOfferSdp
                                        if (offer != null) {
                                            webRTCClient.answerCall(
                                                callViewModel.callState.value.targetUser,
                                                offer
                                            )
                                            callViewModel.onCallConnected()
                                            ringtoneManager.stop()
                                            proximitySensor.deactivate()
                                        }
                                    },

                                    // ✅ Toggle Mute (Same)
                                    onToggleMute = {
                                        val newMute = !callViewModel.callState.value.isMuted
                                        webRTCClient.toggleMute(newMute)
                                        callViewModel.updateMuteState(newMute)
                                    },

                                    // ✅ Toggle Speaker (Same)
                                    onToggleSpeaker = {
                                        val newState = !callViewModel.callState.value.isSpeakerOn
                                        webRTCClient.toggleSpeaker(newState)
                                        callViewModel.updateSpeakerState(newState)

                                        if (newState) {
                                            proximitySensor.deactivate()
                                        } else {
                                            proximitySensor.activate()
                                        }
                                    }
                                )
                            }

                            // 💬 CHAT SCREEN (🔥 Fixed - Ab rotation safe hai)
                            currentChatReceiver != null -> ChatScreen(
                                viewModel = chatViewModel,
                                receiverName = currentChatReceiver!!,

                                onAttachClick = {
                                    currentUploadViewModel = chatViewModel
                                    currentUploadReceiver = currentChatReceiver
                                    filePickerLauncher.launch("*/*")
                                },

                                onBackClick = {
                                    chatViewModel.closeChat()
                                    currentChatReceiver = null
                                },

                                onStartCall = {
                                    val targetUser = currentChatReceiver!!
                                    val contact =
                                        contactViewModel.contacts.find { it.username == targetUser }
                                    val fullPhotoUrl = if (contact?.profilePhoto != null) {
                                        settingsManager.getBaseUrl()
                                            .removeSuffix("/") + contact.profilePhoto
                                    } else {
                                        null
                                    }

                                    chatViewModel.sendCallRequest(targetUser)
                                    callViewModel.onStartOutgoingCall(targetUser, fullPhotoUrl)
                                    webRTCClient.startCall(targetUser)
                                    proximitySensor.deactivate()
                                }
                            )

                            // 👥 CONTACT LIST SCREEN
                            else -> ContactListScreen(
                                username = chatViewModel.currentUsername,
                                typingStatuses = chatViewModel.typingStatuses,
                                onChatClick = { currentChatReceiver = it },
                                onSettingsClick = { showSettings = true }
                            )
                        }
                    }
                }
            }
        }

    }
    override fun onDestroy() {
        super.onDestroy()
        proximitySensor.deactivate()
        ringtoneManager.stop()
    }
    override fun onStart() {
        super.onStart()
        // App samne aa gaya
        AppStateTracker.isForeground.value = true
    }

    override fun onStop() {
        super.onStop()
        // App hide ho gaya (Home button ya Lock)
        AppStateTracker.isForeground.value = false
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