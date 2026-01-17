package com.example.intra

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.intra.MyApplication.AppState
import com.example.intra.database.ChatDatabase
import com.example.intra.ui.theme.IntraTheme
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private lateinit var proximitySensor: ProximitySensor
    private lateinit var ringtoneManager: CallRingtoneManager

    private var currentUploadViewModel: ChatViewModel? = null
    private var currentUploadReceiver: String? = null

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val file = uriToTempFile(this, it)
                if (file != null) {
                    currentUploadViewModel?.uploadFile(file, currentUploadReceiver!!)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ 🆕 NEW GEMINI FIX - STEP 1: Intent se sender aur photo extract karo
        // Yeh tab kaam karega jab app completely band thi aur notification se khuli
        val intentSender = intent?.getStringExtra("incoming_sender")
        val intentPhoto = intent?.getStringExtra("incoming_photo")

        // 📝 Log karo debug ke liye
        if (intentSender != null) {
            Log.d("MAIN", "🔥 Recovered call from intent: sender=$intentSender, photo=$intentPhoto")
        }

        // ✅ PREVIOUS PATCH 3 - STEP 1: Intent handling CLEAN (before Compose)
        // Yeh notification se incoming call detect karega (purana approach)
        val incomingCallSender: String? =
            if (intent?.action == "OPEN_CALL_SCREEN") {
                intent.getStringExtra("sender")
            } else null

        // ✅ PREVIOUS PATCH 3 - STEP 1: Reject action detect karega
        val rejectFromNotification =
            intent?.action == "REJECT_CALL"

        proximitySensor = ProximitySensor(this)
        ringtoneManager = CallRingtoneManager(this)

        val chatDao = ChatDatabase.getDatabase(applicationContext).chatDao()
        val settingsManager = SettingsManager(this)
        val chatViewModelFactory = ChatViewModelFactory(chatDao, settingsManager)

        setContent {
            IntraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    val authViewModel: AuthViewModel = viewModel()
                    val chatViewModel: ChatViewModel =
                        viewModel(factory = chatViewModelFactory)
                    val callViewModel: CallViewModel = viewModel()
                    val contactViewModel: ContactViewModel = viewModel()

                    // ✅ 🆕 NEW GEMINI FIX - STEP 2: Intent se aayi call ko handle karo
                    // Yeh LaunchedEffect tab trigger hoga jab intentSender null nahi hoga
                    LaunchedEffect(intentSender) {
                        if (intentSender != null) {
                            // 🔥 Photo URL banao (agar relative path hai to base URL add karo)
                            val fullPhotoUrl = if (intentPhoto != null && !intentPhoto.startsWith("http")) {
                                settingsManager.getBaseUrl().removeSuffix("/") + intentPhoto
                            } else intentPhoto

                            // 📞 Call Screen dikhao with photo
                            Log.d("MAIN", "📞 Opening call screen for: $intentSender with photo: $fullPhotoUrl")
                            callViewModel.onIncomingCall(intentSender, fullPhotoUrl)

                            // 🔥 Pending offer check karo (agar Service ne store kiya hai)
                            if (MyApplication.AppState.pendingCallOffer != null) {
                                Log.d("MAIN", "✅ Restoring pending offer from AppState")
                                callViewModel.setIncomingOffer(MyApplication.AppState.pendingCallOffer!!)
                                MyApplication.AppState.pendingCallOffer = null
                            }
                        }
                    }

                    // ✅ PREVIOUS PATCH 3 - STEP 2: Compose-safe incoming call handler
                    // Jab notification se call open ho tab yeh trigger hoga (purana approach)
                    LaunchedEffect(incomingCallSender) {
                        if (incomingCallSender != null) {
                            if (!callViewModel.callActive) {
                                // 1. Call Screen dikhao
                                val photoUrl = null // Abhi ke liye null, ya AppState se le sakte ho
                                callViewModel.onIncomingCall(incomingCallSender, photoUrl)

                                // 🔥 FIX: Check karo agar Service ne Offer pakad rakha hai kya?
                                if (MyApplication.AppState.pendingCallOffer != null) {
                                    Log.d("MAIN", "Restoring pending offer from Service")
                                    callViewModel.setIncomingOffer(MyApplication.AppState.pendingCallOffer!!)

                                    // Use karne ke baad clear kar do taaki dubara use na ho
                                    MyApplication.AppState.pendingCallOffer = null
                                }
                            }
                        }
                    }

                    // ✅ PREVIOUS PATCH 3 - STEP 2: Notification se reject handle karega
                    LaunchedEffect(rejectFromNotification) {
                        if (rejectFromNotification == true) {
                            val sender = intent?.getStringExtra("sender")
                            if (sender != null) {
                                // Reject signal bhejo
                                val json = JSONObject().apply {
                                    put("type", "call_rejected")
                                    put("receiver", sender)
                                }
                                chatViewModel.sendRawSignal(json.toString())
                                callViewModel.onCallEnded()

                                // Notification cancel karo
                                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                nm.cancel(CALL_NOTIFICATION_ID)
                            }
                        }
                    }

                    // ✅ 🆕 NEW GEMINI FIX - STEP 3: Stuck Notification Fix
                    // Jab call end/reject ho to notification zarur hatao
                    LaunchedEffect(callViewModel.callActive) {
                        if (!callViewModel.callActive) {
                            Log.d("MAIN", "🗑️ Call ended, removing notification")
                            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            nm.cancel(CALL_NOTIFICATION_ID) // Force remove notification
                        }
                    }

                    val webRTCClient = remember {
                        WebRTCClient(
                            context = applicationContext,
                            sendSignal = { json ->
                                chatViewModel.sendRawSignal(json)
                            }
                        )
                    }

                    val isAuthenticated by authViewModel.isAuthenticated
                    var showSettings by rememberSaveable { mutableStateOf(false) }
                    var showAbout by rememberSaveable { mutableStateOf(false) }
                    var currentChatReceiver by rememberSaveable { mutableStateOf<String?>(null) }

                    // 🔔 Ringtone lifecycle
                    LaunchedEffect(callViewModel.isRinging.value) {
                        if (callViewModel.isRinging.value) ringtoneManager.start()
                        else ringtoneManager.stop()
                    }

                    // 🔌 Call / WebRTC signal bridge
                    DisposableEffect(chatViewModel) {
                        chatViewModel.onCallSignal = { raw ->
                            try {
                                val json = JSONObject(raw)
                                when (json.optString("type")) {

                                    "call_request" -> {
                                        val sender = json.optString("sender")
                                        val rawPhoto = json.optString("profile_photo")
                                        val fullPhotoUrl = if (!rawPhoto.isNullOrEmpty() && rawPhoto != "null") {
                                            settingsManager.getBaseUrl().removeSuffix("/") + rawPhoto
                                        } else null

                                        callViewModel.onIncomingCall(sender, fullPhotoUrl)
                                    }

                                    "call_rejected", "call_ended" -> {
                                        cleanupCall(callViewModel, webRTCClient)
                                    }

                                    "webrtc_offer" -> {
                                        // 🔥 Offer set karo (Foreground case ke liye)
                                        callViewModel.setIncomingOffer(json.optString("sdp"))
                                    }

                                    // ... (Answer aur Ice Candidate same rahenge)
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
                                Log.e("CALL_SIGNAL", "Error", e)
                            }
                        }
                        onDispose { chatViewModel.onCallSignal = null }
                    }

                    // 🧭 UI navigation
                    if (!isAuthenticated) {
                        AuthScreen(viewModel = authViewModel, onAuthenticated = {})
                    } else {
                        when {

                            showAbout -> AboutScreen { showAbout = false }

                            showSettings -> SettingsScreen(
                                onLogoutConfirmed = {
                                    authViewModel.logout()
                                    showSettings = false
                                    currentChatReceiver = null
                                },
                                onBack = { showSettings = false },
                                onNavigateToAbout = { showAbout = true }
                            )

                            callViewModel.callState.value.status != CallStatus.IDLE -> {
                                CallScreen(
                                    state = callViewModel.callState.value,

                                    onRejectCall = {
                                        val target = callViewModel.callState.value.targetUser
                                        if (target.isNotEmpty()) {
                                            val json = JSONObject().apply {
                                                put("type", "call_rejected")
                                                put("receiver", target)
                                            }
                                            chatViewModel.sendRawSignal(json.toString())
                                        }
                                        cleanupCall(callViewModel, webRTCClient)
                                    },

                                    onEndCall = {
                                        cleanupCall(callViewModel, webRTCClient)
                                    },

                                    onAcceptCall = {
                                        val offer = callViewModel.pendingOfferSdp

                                        // 🔥 FINAL CHECK: Agar abhi bhi offer null hai to log karo
                                        if (offer == null) {
                                            Log.w("CALL", "Accept pressed but offer NOT FOUND in ViewModel")
                                            return@CallScreen
                                        }

                                        // ✅ Fix for Double Ringtone: Stop immediately
                                        ringtoneManager.stop()

                                        webRTCClient.answerCall(
                                            callViewModel.callState.value.targetUser,
                                            offer
                                        )
                                        callViewModel.onCallConnected()
                                        proximitySensor.deactivate()
                                    },


                                    onToggleMute = {
                                        val newMute = !callViewModel.callState.value.isMuted
                                        webRTCClient.toggleMute(newMute)
                                        callViewModel.updateMuteState(newMute)
                                    },

                                    onToggleSpeaker = {
                                        val newState =
                                            !callViewModel.callState.value.isSpeakerOn
                                        webRTCClient.toggleSpeaker(newState)
                                        callViewModel.updateSpeakerState(newState)
                                        if (newState) proximitySensor.deactivate()
                                        else proximitySensor.activate()
                                    }
                                )
                            }

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
                                    val target = currentChatReceiver!!
                                    val contact =
                                        contactViewModel.contacts.find { it.username == target }

                                    val photo =
                                        contact?.profilePhoto?.let {
                                            settingsManager.getBaseUrl()
                                                .removeSuffix("/") + it
                                        }

                                    chatViewModel.sendCallRequest(target)
                                    callViewModel.onStartOutgoingCall(target, photo)
                                    webRTCClient.startCall(target)
                                    proximitySensor.deactivate()
                                }
                            )

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

        // ✅ PREVIOUS PATCH 4: Service start with connection check
        // Service sirf tab start hogi jab enabled ho AUR already connected na ho
        val settings = SettingsManager(this)
        if (settings.isBackgroundServiceEnabled() && !WsManager.isConnected) {
            val intent = Intent(this, IntraBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(intent)
            else startService(intent)
        }
    }

    // ✅ 🆕 NEW GEMINI FIX - STEP 4: onNewIntent Override
    // Yeh tab kaam karega jab app background me thi aur notification se naya intent aaya
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // ⚠️ IMPORTANT: Naya intent set karo taaki LaunchedEffect trigger ho

        // 🔥 Intent se sender aur photo extract karo
        val intentSender = intent.getStringExtra("incoming_sender")
        val intentPhoto = intent.getStringExtra("incoming_photo")

        if (intentSender != null) {
            Log.d("MAIN", "🔄 onNewIntent: New call from $intentSender")

            // 📝 NOTE: Yahan direct callViewModel.onIncomingCall() call mat karo
            // Kyunki setIntent() ke baad LaunchedEffect automatically trigger hoga
            // aur woh handle kar lega
        }
    }

    override fun onResume() {
        super.onResume()
        AppState.isForeground = true
    }

    override fun onPause() {
        super.onPause()
        AppState.isForeground = false
    }

    override fun onDestroy() {
        proximitySensor.deactivate()
        ringtoneManager.stop()
        super.onDestroy()
    }

    private fun cleanupCall(
        callViewModel: CallViewModel,
        webRTCClient: WebRTCClient
    ) {
        try {
            webRTCClient.endCall()
        } catch (_: Exception) {}
        callViewModel.onCallEnded()
        proximitySensor.deactivate()
        ringtoneManager.stop()
    }

    private fun uriToTempFile(context: Context, uri: Uri): File? {
        val resolver = context.contentResolver
        var fileName: String? = null

        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) fileName = cursor.getString(index)
            }
        }

        if (fileName == null) fileName = "upload_${System.currentTimeMillis()}"
        val tempFile = File(context.cacheDir, fileName!!)

        return try {
            resolver.openInputStream(uri)?.use { input ->
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

    // ✅ Constant definition (notification ID ke liye)
    companion object {
        const val CALL_NOTIFICATION_ID = 1001
    }
}