package com.example.intra

import android.content.Context
import android.media.AudioManager
import android.util.Log
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

class WebRTCClient(
    private val context: Context,
    private val sendSignal: (String) -> Unit
) {

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    // Default Speaker State: Hum shuruat Speaker ON se karenge
    private var isSpeakerOn = false

    private var currentTarget: String = ""

    init {
        initWebRTC()
    }

    private fun initWebRTC() {
        // 1. WebRTC Initialization options
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        // 2. Audio Device Module (Hardware Echo Cancellation for J2/Modern phones)
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        // 3. Create Factory
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false
                disableNetworkMonitor = false
            })
            .createPeerConnectionFactory()

        // 4. Create Audio Source & Track
        val audioConstraints = MediaConstraints()
        audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio_track_101", audioSource)

        // 5. Initial Audio Setup (Focus request kar lo, par routing call ke waqt karenge)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.requestAudioFocus(
            null,
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )
        // Communication mode zaroori hai VoIP ke liye
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        Log.d("WebRTC", "✅ WebRTC Initialized & Audio Focus Requested")
    }

    // ----------------------------------------------------------------
    // 🎧 AUDIO MANAGEMENT (The "Clean" Way)
    // ----------------------------------------------------------------

    // Yeh function call start hone par audio set karega
    // Aur jab user toggle karega tab use hoga.
    private fun setAudioOutput(enableSpeaker: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Mode ensure karo
        if (audioManager.mode != AudioManager.MODE_IN_COMMUNICATION) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        }

        // Speaker ON/OFF set karo
        if (audioManager.isSpeakerphoneOn != enableSpeaker) {
            audioManager.isSpeakerphoneOn = enableSpeaker
        }

        // Mic mute hatao (just in case)
        audioManager.isMicrophoneMute = false

        this.isSpeakerOn = enableSpeaker
        Log.d("WebRTC", "🎧 Audio Output Set -> Speaker: $enableSpeaker")
    }

    // UI se Call hone wala function
    fun toggleSpeaker(shouldBeOn: Boolean) {
        setAudioOutput(shouldBeOn)
    }

    // UI se Call hone wala function
    fun toggleMute(shouldMute: Boolean) {
        localAudioTrack?.setEnabled(!shouldMute)
        Log.d("WebRTC", "🎙️ Mic Muted: $shouldMute")
    }

    // ----------------------------------------------------------------
    // 📞 CALL LOGIC
    // ----------------------------------------------------------------

    fun startCall(targetUsername: String) {
        currentTarget = targetUsername

        // 🔥 STEP 1: Default Speaker ON karo (Safety for J2)
        setAudioOutput(true)

        createPeerConnection()

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            // JSON fix (Gemini & ChatGPT both agree)
                            val json = JSONObject().apply {
                                put("type", "webrtc_offer")
                                put("sdp", it.description)
                                put("receiver", targetUsername)
                            }
                            sendSignal(json.toString())
                        }
                        override fun onSetFailure(error: String?) {}
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                    }, it)
                }
            }
            override fun onCreateFailure(error: String?) {}
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    fun answerCall(targetUsername: String, offerSdp: String) {
        currentTarget = targetUsername

        // 🔥 STEP 1: Default Speaker ON karo Answer karte waqt bhi
        setAudioOutput(true)

        createPeerConnection()

        val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                }
                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription?) {
                        sdp?.let {
                            peerConnection?.setLocalDescription(object : SdpObserver {
                                override fun onSetSuccess() {
                                    val json = JSONObject().apply {
                                        put("type", "webrtc_answer")
                                        put("sdp", it.description)
                                        put("receiver", targetUsername)
                                    }
                                    sendSignal(json.toString())
                                }
                                override fun onSetFailure(error: String?) {}
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onCreateFailure(p0: String?) {}
                            }, it)
                        }
                    }
                    override fun onCreateFailure(error: String?) {}
                    override fun onSetSuccess() {}
                    override fun onSetFailure(p0: String?) {}
                }, constraints)
            }
            override fun onSetFailure(error: String?) {}
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, remoteSdp)
    }

    fun onRemoteAnswer(answerSdp: String) {
        val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d("WebRTC", "✅ Remote Answer Set")
            }
            override fun onSetFailure(error: String?) {}
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, remoteSdp)
    }

    fun onRemoteIceCandidate(candidateStr: String, sdpMid: String, sdpMLineIndex: Int) {
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, candidateStr)
        peerConnection?.addIceCandidate(candidate)
    }

    // ----------------------------------------------------------------
    // 🔌 CONNECTION & CLEANUP
    // ----------------------------------------------------------------

    private fun createPeerConnection() {
        if (peerConnection != null) return

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let {
                        val json = JSONObject().apply {
                            put("type", "ice_candidate")
                            put("candidate", it.sdp)
                            put("sdpMid", it.sdpMid)
                            put("sdpMLineIndex", it.sdpMLineIndex)
                            put("receiver", currentTarget)
                        }
                        sendSignal(json.toString())
                    }
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

                override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                    val track = receiver?.track()
                    if (track is AudioTrack) {
                        track.setEnabled(true)
                        Log.d("WebRTC", "🔊 Remote Audio Track Enabled")

                        // 🔥 IMPORTANT CHANGE: Yahan hum Force Audio nahi kar rahe.
                        // ChatGPT sahi tha: Agar hum yahan force karenge, toh
                        // jab bhi thoda glitch hoga, phone wapas Speaker mode me chala jayega.
                        // Sirf Track enable karna kaafi hai.
                    }
                }

                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onDataChannel(dc: DataChannel?) {}
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                    Log.d("WebRTC", "❄️ ICE State: $newState")
                }
                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
                override fun onAddStream(stream: MediaStream?) {}
                override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
                override fun onRenegotiationNeeded() {}
            }
        )

        localAudioTrack?.setEnabled(true)
        val streamId = "local_audio_stream"
        peerConnection?.addTrack(localAudioTrack, listOf(streamId))
    }

    fun endCall() {
        // Cleanup Audio Mode
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
        audioManager.isMicrophoneMute = false

        // Abandon Focus (Optional but good practice)
        audioManager.abandonAudioFocus(null)

        localAudioTrack?.setEnabled(false)
        peerConnection?.close()
        peerConnection = null

        Log.d("WebRTC", "❌ Call Ended & Audio Cleaned")
    }
}