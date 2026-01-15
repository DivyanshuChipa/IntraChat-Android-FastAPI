package com.example.intra

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
import androidx.lifecycle.ViewModelProvider
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

        proximitySensor = ProximitySensor(this)
        ringtoneManager = CallRingtoneManager(this)


        // =============================
        // Handle Incoming Call from Service
        // =============================
        if (intent?.action == "INCOMING_CALL") {
            val sender = intent.getStringExtra("sender")
            if (!sender.isNullOrEmpty()) {
                Log.d("MAIN", "Incoming call intent from $sender")
            }
        }

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

                    // =============================
                    // Ringtone lifecycle (SAFE)
                    // =============================
                    LaunchedEffect(callViewModel.isRinging.value) {
                        if (callViewModel.isRinging.value) {
                            ringtoneManager.start()
                        } else {
                            ringtoneManager.stop()
                        }
                    }

                    // =============================
                    // Call / WebRTC Signal Bridge
                    // =============================
                    DisposableEffect(chatViewModel) {
                        chatViewModel.onCallSignal = { raw ->
                            try {
                                val json = JSONObject(raw)
                                when (json.optString("type")) {

                                    "call_request" -> {
                                        val sender = json.optString("sender")
                                        val rawPhoto = json.optString("profile_photo")

                                        val fullPhotoUrl =
                                            if (!rawPhoto.isNullOrEmpty() && rawPhoto != "null") {
                                                settingsManager.getBaseUrl()
                                                    .removeSuffix("/") + rawPhoto
                                            } else null

                                        callViewModel.onIncomingCall(sender, fullPhotoUrl)
                                    }

                                    "call_rejected", "call_ended" -> {
                                        cleanupCall(callViewModel, webRTCClient)
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
                                Log.e("CALL_SIGNAL", "Error: ${e.message}")
                            }
                        }

                        onDispose {
                            chatViewModel.onCallSignal = null
                        }
                    }

                    // =============================
                    // UI Navigation
                    // =============================
                    if (!isAuthenticated) {
                        AuthScreen(viewModel = authViewModel, onAuthenticated = {})
                    } else {
                        when {

                            showAbout -> AboutScreen(
                                onBack = { showAbout = false }
                            )

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
                                        val target =
                                            callViewModel.callState.value.targetUser
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

                                    onToggleMute = {
                                        val newMute =
                                            !callViewModel.callState.value.isMuted
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
        // 🔥 AUTO-START SERVICE IF TOGGLE WAS ON
        val settings = SettingsManager(this)

        if (settings.isBackgroundServiceEnabled()) {
            Log.d("AUTO_START", "Keep Intra Running ON → starting service")

            val intent = Intent(this, IntraBackgroundService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
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

    // =============================
    // Helpers
    // =============================

    private fun cleanupCall(
        callViewModel: CallViewModel,
        webRTCClient: WebRTCClient
    ) {
        try {
            webRTCClient.endCall()
        } catch (_: Exception) {
        }
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

        if (fileName == null) {
            fileName = "upload_${System.currentTimeMillis()}"
        }

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
}
