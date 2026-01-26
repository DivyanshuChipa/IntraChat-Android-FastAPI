package com.example.intra

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
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

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                Log.d("PERMISSIONS", "✅ All permissions granted")
            } else {
                Log.w("PERMISSIONS", "⚠️ Some permissions denied")
            }
        }

    private lateinit var proximitySensor: ProximitySensor
    private val ringtoneManager by lazy { CallRingtoneManager.getInstance(this) }

    private var incomingCallData = mutableStateOf<Pair<String?, String?>>(null to null)

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
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        checkAndRequestPermissions()

        handleIntent(intent)

        proximitySensor = ProximitySensor(this)

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

                    // ===============================
                    // 🆕 STEP 5E: EVENT LISTENER
                    // ===============================

                    // Jab message aaye, contact list ko refresh karo
                    LaunchedEffect(chatViewModel) {
                        chatViewModel.contactUpdateEvent.collect { updatedUser ->
                            Log.d("MAIN", "📱 Contact update event: $updatedUser")
                            contactViewModel.fetchContacts() // Refresh list
                        }
                    }

                    val (intentSender, intentPhoto) = incomingCallData.value
                    LaunchedEffect(intentSender, intentPhoto) {
                        if (intentSender != null) {
                            val fullPhotoUrl = if (intentPhoto != null && !intentPhoto.startsWith("http")) {
                                settingsManager.getBaseUrl().removeSuffix("/") + intentPhoto
                            } else intentPhoto

                            Log.d("MAIN", "📞 Opening call screen for: $intentSender with photo: $fullPhotoUrl")
                            callViewModel.onIncomingCall(intentSender, fullPhotoUrl)

                            if (MyApplication.AppState.pendingCallOffer != null) {
                                Log.d("MAIN", "✅ Restoring pending offer from AppState")
                                callViewModel.setIncomingOffer(MyApplication.AppState.pendingCallOffer!!)
                                MyApplication.AppState.pendingCallOffer = null
                            }
                            // Reset state after handling to avoid re-triggering on recomposition if not intended,
                            // though LaunchedEffect handles it via keys. Resetting is safer.
                            incomingCallData.value = null to null
                        }
                    }



                    LaunchedEffect(callViewModel.callActive) {
                        if (!callViewModel.callActive) {
                            Log.d("MAIN", "🗑️ Call ended, removing notification")
                            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            nm.cancel(CALL_NOTIFICATION_ID)
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

                    LaunchedEffect(callViewModel.isRinging.value) {
                        if (callViewModel.isRinging.value) ringtoneManager.start()
                        else ringtoneManager.stop()
                    }

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
                                Log.e("CALL_SIGNAL", "Error", e)
                            }
                        }
                        onDispose { chatViewModel.onCallSignal = null }
                    }

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

                                        if (offer == null) {
                                            Log.w("CALL", "Accept pressed but offer NOT FOUND in ViewModel")
                                            return@CallScreen
                                        }

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
                                activeChatUser = chatViewModel.activeChatUser, // 🆕 STEP 6: Pass active chat
                                onChatClick = { currentChatReceiver = it },
                                onSettingsClick = { showSettings = true }
                            )
                        }
                    }
                }
            }
        }

        val settings = SettingsManager(this)
        if (settings.isBackgroundServiceEnabled() && !WsManager.isConnected) {
            val intent = Intent(this, IntraBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(intent)
            else startService(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val sender = intent?.getStringExtra("incoming_sender")
        val photo = intent?.getStringExtra("incoming_photo")
        if (sender != null) {
            Log.d("MAIN", "🔥 handleIntent: sender=$sender, photo=$photo")
            incomingCallData.value = sender to photo
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

    private fun checkAndRequestPermissions() {
        // Runtime permissions were introduced in Android 6.0 (API 23)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val permissions = mutableListOf<String>()

        // Audio for Calls
        permissions.add(android.Manifest.permission.RECORD_AUDIO)

        // Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // Storage / Media
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(android.Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty()) {
            Log.d("PERMISSIONS", "Requesting: $toRequest")
            requestPermissionLauncher.launch(toRequest.toTypedArray())
        }
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

    companion object {
        const val CALL_NOTIFICATION_ID = 1001
    }
}