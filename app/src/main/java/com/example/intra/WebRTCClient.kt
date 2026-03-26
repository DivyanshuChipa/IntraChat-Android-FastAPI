package com.example.intra

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
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

    // Video components
    val eglBaseContext: EglBase.Context by lazy { EglBase.create().eglBaseContext }
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoCapturer: VideoCapturer? = null
    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var remoteRendererSaved: SurfaceViewRenderer? = null
    private var isVideoEnabled = true

    // Default Speaker State: Hum shuruat Speaker ON se karenge
    private var isSpeakerOn = false

    private var currentTarget: String = ""
    private var isVideoCall = false

    init {
        initWebRTC()
    }

    private fun initWebRTC() {
        // 1. WebRTC Initialization options
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        // 2. Audio Device Module (Hardware Echo Cancellation for J2/Modern phones)
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        // 3. Create Factory
        val videoEncoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, true)
        val videoDecoderFactory = DefaultVideoDecoderFactory(eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(videoEncoderFactory)
            .setVideoDecoderFactory(videoDecoderFactory)
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
        // 🔥 REMOVED: Audio Focus & Mode Setup moved to setupAudioForCall()
        // val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // audioManager.requestAudioFocus(...)
        // audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        Log.d("WebRTC", "✅ WebRTC Initialized")
    }

    // ----------------------------------------------------------------
    // 🎧 AUDIO MANAGEMENT (The "Clean" Way)
    // ----------------------------------------------------------------

    private fun setupAudioForCall() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.requestAudioFocus(
            null,
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        Log.d("WebRTC", "📞 Audio Focus Requested & Mode set to IN_COMMUNICATION")
    }

    // Yeh function call start hone par audio set karega
    // Aur jab user toggle karega tab use hoga.
    private fun setAudioOutput(enableSpeaker: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Mode ensure karo
        if (audioManager.mode != AudioManager.MODE_IN_COMMUNICATION) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Modern way (Android 12+)
            val devices = audioManager.availableCommunicationDevices
            val targetType = if (enableSpeaker) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            } else {
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            }
            val device = devices.find { it.type == targetType }
            if (device != null) {
                audioManager.setCommunicationDevice(device)
                Log.d("WebRTC", "🎧 Communication Device set to ${if (enableSpeaker) "Speaker" else "Earpiece"}")
            } else {
                // Fallback
                audioManager.isSpeakerphoneOn = enableSpeaker
            }
        } else {
            // Legacy way
            if (audioManager.isSpeakerphoneOn != enableSpeaker) {
                audioManager.isSpeakerphoneOn = enableSpeaker
            }
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
    // 📹 VIDEO MANAGEMENT
    // ----------------------------------------------------------------

    private fun createVideoCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        // Try to find front camera first
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        // Fallback to back camera
        for (deviceName in deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }

    fun setupLocalVideoRenderer(localRenderer: SurfaceViewRenderer) {
        localRenderer.setEnableHardwareScaler(true)
        localRenderer.setMirror(true) // Mirror for front camera
        localVideoTrack?.addSink(localRenderer)
    }

    fun removeLocalVideoRenderer(localRenderer: SurfaceViewRenderer) {
        localVideoTrack?.removeSink(localRenderer)
    }

    fun setupRemoteVideoRenderer(remoteRenderer: SurfaceViewRenderer) {
        remoteRenderer.setEnableHardwareScaler(true)
        remoteRendererSaved = remoteRenderer
        remoteVideoTrack?.addSink(remoteRenderer)
    }

    fun removeRemoteVideoRenderer(remoteRenderer: SurfaceViewRenderer) {
        remoteVideoTrack?.removeSink(remoteRenderer)
        if (remoteRendererSaved == remoteRenderer) {
            remoteRendererSaved = null
        }
    }

    private fun startLocalVideo() {
        if (surfaceTextureHelper == null) {
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext)
        }

        if (videoCapturer == null) {
            videoCapturer = createVideoCapturer()
        }

        if (localVideoSource == null) {
            localVideoSource = peerConnectionFactory?.createVideoSource(videoCapturer!!.isScreencast)
            videoCapturer?.initialize(surfaceTextureHelper, context, localVideoSource?.capturerObserver)
            videoCapturer?.startCapture(1024, 720, 30) // Resolution can be adjusted
        }

        if (localVideoTrack == null) {
            localVideoTrack = peerConnectionFactory?.createVideoTrack("video_track_101", localVideoSource)
            localVideoTrack?.setEnabled(true)
        }
    }

    fun toggleVideo(shouldBeOn: Boolean) {
        if (!isVideoCall) return
        isVideoEnabled = shouldBeOn
        localVideoTrack?.setEnabled(isVideoEnabled)
        Log.d("WebRTC", "📹 Video Track Enabled: $isVideoEnabled")
    }

    fun switchCamera() {
        if (!isVideoCall) return
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    // ----------------------------------------------------------------
    // 📞 CALL LOGIC
    // ----------------------------------------------------------------

    fun startCall(targetUsername: String, isVideoCall: Boolean = false) {
        currentTarget = targetUsername
        this.isVideoCall = isVideoCall

        // 🔥 STEP 0: Set Audio Mode for Call (Fix for J2 Camera & Earpiece Issue)
        setupAudioForCall()

        // 🔥 STEP 1: Default Speaker ON karo (Safety for J2)
        setAudioOutput(true)

        createPeerConnection()

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            if (isVideoCall) {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
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
                                put("is_video_call", isVideoCall) // Also let the remote know
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

    fun answerCall(targetUsername: String, offerSdp: String, isVideoCall: Boolean = false) {
        currentTarget = targetUsername
        this.isVideoCall = isVideoCall

        // 🔥 STEP 0: Set Audio Mode for Call (Fix for J2 Camera & Earpiece Issue)
        setupAudioForCall()

        // 🔥 STEP 1: Default Speaker ON karo Answer karte waqt bhi
        setAudioOutput(true)

        createPeerConnection()

        val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    if (isVideoCall) {
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                    }
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
                    } else if (track is VideoTrack) {
                        track.setEnabled(true)
                        remoteVideoTrack = track
                        remoteRendererSaved?.let { renderer ->
                            track.addSink(renderer)
                        }
                        Log.d("WebRTC", "📹 Remote Video Track Added")
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

        if (isVideoCall) {
            startLocalVideo()
            peerConnection?.addTrack(localVideoTrack, listOf("local_video_stream"))
        }
    }

    // 🔥 FIX 1: Call end karne par signal bhejo
    fun endCall() {
        // 📢 Signal bhejo dusre phone ko
        if (currentTarget.isNotEmpty()) {
            val json = JSONObject().apply {
                put("type", "call_ended")
                put("receiver", currentTarget)
            }
            sendSignal(json.toString())
            Log.d("WebRTC", "📞 Call ended signal sent to $currentTarget")
        }
        // Cleanup Audio Mode
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }

        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
        audioManager.isMicrophoneMute = false

        // Abandon Focus (Optional but good practice)
        audioManager.abandonAudioFocus(null)

        localAudioTrack?.setEnabled(false)

        try {
            videoCapturer?.stopCapture()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        videoCapturer?.dispose()
        videoCapturer = null

        localVideoSource?.dispose()
        localVideoSource = null

        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        remoteRendererSaved = null

        peerConnection?.close()
        peerConnection = null
        currentTarget = ""
        isVideoCall = false

        Log.d("WebRTC", "❌ Call Ended & Audio/Video Cleaned")
    }
}