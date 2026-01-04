package com.example.intra

import android.content.Context
import android.media.RingtoneManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.intra.database.ChatDatabase
import com.example.intra.ui.theme.IntraTheme
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private var currentUploadViewModel: ChatViewModel? = null
    private var currentUploadReceiver: String? = null

    // 📱 Proximity Sensor Instance
    private lateinit var proximitySensor: ProximitySensor

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

        // Initialize Proximity Sensor
        proximitySensor = ProximitySensor(this)

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

                    val webRTCClient = remember {
                        WebRTCClient(
                            context = applicationContext,
                            sendSignal = { json -> chatViewModel.sendRawSignal(json) }
                        )
                    }

                    val isAuthenticated by authViewModel.isAuthenticated
                    var showSettings by remember { mutableStateOf(false) }
                    var currentChatReceiver by remember { mutableStateOf<String?>(null) }

                    // 🔔 RINGTONE LOGIC
                    val context = LocalContext.current
                    LaunchedEffect(callViewModel.isRinging.value) {
                        if (callViewModel.isRinging.value) {
                            try {
                                val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                                val r = RingtoneManager.getRingtone(context, notification)
                                r.play()

                                // Jab tak ringing true hai, wait karo
                                // (Note: Simple implementation. Advanced me service lagti hai)
                                while (callViewModel.isRinging.value) {
                                    kotlinx.coroutines.delay(1000)
                                    if (!r.isPlaying) r.play() // Loop logic
                                }
                                r.stop()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    // 📶 SIGNAL HANDLER
                    LaunchedEffect(Unit) {
                        chatViewModel.onCallSignal = { raw ->
                            try {
                                val json = JSONObject(raw)
                                when (json.optString("type")) {
                                    "call_request" -> callViewModel.onIncomingCall(json.optString("sender"))
                                    "webrtc_offer" -> callViewModel.setIncomingOffer(json.optString("sdp"))
                                    "webrtc_answer" -> {
                                        webRTCClient.onRemoteAnswer(json.optString("sdp"))
                                        callViewModel.onCallConnected()
                                        // Answer milte hi proximity activate karo (kyunki default speaker ON hai, so check logic)
                                        // Default humne speaker ON rakha hai, isliye proximity OFF rahegi shuru me.
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
                                        proximitySensor.deactivate() // 🛑 Sensor Band
                                    },

                                    onAcceptCall = {
                                        val offer = callViewModel.pendingOfferSdp
                                        if (offer != null) {
                                            webRTCClient.answerCall(callViewModel.callState.value.targetUser, offer)
                                            callViewModel.onCallConnected()
                                            // 🛑 Default Speaker ON hai, isliye Proximity abhi band rakhenge
                                            proximitySensor.deactivate()
                                        }
                                    },

                                    onToggleMute = {
                                        val newMute = !callViewModel.callState.value.isMuted
                                        webRTCClient.toggleMute(newMute)
                                        callViewModel.updateMuteState(newMute)
                                    },

                                    // 👂 SPEAKER & PROXIMITY TOGGLE
                                    onToggleSpeaker = {
                                        val newSpeaker = !callViewModel.callState.value.isSpeakerOn
                                        webRTCClient.toggleSpeaker(newSpeaker)
                                        callViewModel.updateSpeakerState(newSpeaker)

                                        // 🔥 LOGIC:
                                        // Agar Speaker ON hai -> Screen ON rakho (Sensor DEACTIVATE)
                                        // Agar Speaker OFF hai (Kaan pe hai) -> Screen OFF karo (Sensor ACTIVATE)
                                        if (newSpeaker) {
                                            proximitySensor.deactivate()
                                        } else {
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
                                    val user = currentChatReceiver!!
                                    chatViewModel.sendCallRequest(user)
                                    callViewModel.onStartOutgoingCall(user)
                                    webRTCClient.startCall(user)

                                    // Outgoing me bhi default Speaker ON hai, to sensor OFF
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

    // App band hone par sensor release karo
    override fun onDestroy() {
        super.onDestroy()
        proximitySensor.deactivate()
    }

    fun uriToTempFile(context: Context, uri: Uri): File? {
        // ... (File helper same rahega) ...
        return null // (Apka purana code yahan copy karlena)
    }
}