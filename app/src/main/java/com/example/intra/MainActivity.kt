package com.example.intra

import android.os.Bundle
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
import java.io.File
import java.io.FileOutputStream
import android.net.Uri
import android.content.Context
import android.provider.OpenableColumns
import android.util.Log
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    // File Upload Variables
    private var currentUploadViewModel: ChatViewModel? = null
    private var currentUploadReceiver: String? = null

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

                    val webRTCClient = remember {
                        WebRTCClient(
                            context = applicationContext,
                            sendSignal = { json ->
                                chatViewModel.sendRawSignal(json)
                            }
                        )
                    }
                    val isAuthenticated by authViewModel.isAuthenticated

                    // State variables
                    var showSettings by remember { mutableStateOf(false) }
                    var currentChatReceiver by remember { mutableStateOf<String?>(null) }

                    // ✅ CALL STATE (Single Source)
                    var callState by remember { mutableStateOf(CallState()) }

                    // ✅ NEW: Pending Offer ko store karne ke liye state
                    var pendingOfferSdp by remember { mutableStateOf<String?>(null) }

                    // ✅ STEP-2: THE MASTER SIGNAL HANDLER
                    LaunchedEffect(Unit) {
                        chatViewModel.onCallSignal = { raw ->
                            val json = JSONObject(raw)
                            val type = json.optString("type")
                            val sender = json.optString("sender")

                            when (type) {
                                "call_request" -> {
                                    callState = CallState(
                                        status = CallStatus.INCOMING,
                                        targetUser = sender
                                    )
                                    Log.d("CALL_FLOW", "📲 Incoming call from $sender")
                                }

                                "webrtc_offer" -> {
                                    pendingOfferSdp = json.optString("sdp")
                                    Log.d("CALL_FLOW", "📥 Offer SDP STORED")
                                }

                                "webrtc_answer" -> {
                                    webRTCClient.onRemoteAnswer(json.optString("sdp"))
                                    Log.d("CALL_FLOW", "📥 Answer SDP received")
                                }

                                "ice_candidate" -> {
                                    webRTCClient.onRemoteIceCandidate(
                                        json.getString("candidate"),
                                        json.getString("sdpMid"),
                                        json.getInt("sdpMLineIndex")
                                    )
                                }
                            }
                        }
                    }

                    if (!isAuthenticated) {
                        AuthScreen(viewModel = authViewModel, onAuthenticated = { })
                    } else {
                        when {
                            showSettings -> {
                                SettingsScreen(
                                    onLogoutConfirmed = {
                                        authViewModel.logout()
                                        showSettings = false
                                        currentChatReceiver = null
                                    },
                                    onBack = { showSettings = false }
                                )
                            }

                            // 📞 3. CALL SCREEN FINAL FIX
                            callState.status != CallStatus.IDLE -> {
                                CallScreen(
                                    state = callState,

                                    onEndCall = {
                                        webRTCClient.endCall()
                                        callState = CallState()
                                        pendingOfferSdp = null
                                        Log.d("CALL_FLOW", "📵 Call ended")
                                    },

                                    onAcceptCall = {
                                        val offer = pendingOfferSdp
                                        if (offer != null) {
                                            webRTCClient.answerCall(callState.targetUser, offer)
                                            callState = callState.copy(status = CallStatus.CONNECTED)
                                            Log.d("CALL_FLOW", "✅ Call answered with Offer SDP")
                                        } else {
                                            Log.e("CALL_FLOW", "❌ Cannot accept: No Offer SDP!")
                                        }
                                    },

                                    onToggleMute = { /* later */ },
                                    onToggleSpeaker = { /* later */ }
                                )
                            }

                            currentChatReceiver != null -> {
                                ChatScreen(
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
                                        val user = currentChatReceiver!!

                                        // 1. WebSocket signal bhejo caller banke
                                        chatViewModel.sendCallRequest(user)

                                        // 2. UI update karo
                                        callState = CallState(
                                            status = CallStatus.OUTGOING,
                                            targetUser = user
                                        )

                                        // 3. WebRTC Offer create karo
                                        webRTCClient.startCall(user)
                                        Log.d("MainActivity", "📞 Outgoing call started to $user")
                                    }
                                )
                            }

                            else -> {
                                ContactListScreen(
                                    username = chatViewModel.currentUsername,
                                    typingStatuses = chatViewModel.typingStatuses,
                                    onChatClick = { selectedUser ->
                                        currentChatReceiver = selectedUser
                                    },
                                    onSettingsClick = { showSettings = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- File Helpers ---
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
            Log.e("MainActivity", "File error", e); null
        }
    }
}