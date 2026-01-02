package com.example.intra

import android.content.Context
import android.media.AudioManager
import org.webrtc.audio.JavaAudioDeviceModule
import android.util.Log
import org.webrtc.*
import org.json.JSONObject

class WebRTCClient(
    private val context: Context,
    private val sendSignal: (String) -> Unit
) {

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private val eglBaseContext = EglBase.create().eglBaseContext

    private var currentTarget: String = ""

    init {
        initWebRTC()
    }

    private fun initWebRTC() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false
                disableNetworkMonitor = false
            })
            .createPeerConnectionFactory()

        val audioConstraints = MediaConstraints()
        audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio_track_101", audioSource)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.requestAudioFocus(
            null,
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
        audioManager.isMicrophoneMute = false
        Log.d("WebRTC", "🔊 Audio focus + call mode forced")
    }

    fun startCall(targetUsername: String) {
        currentTarget = targetUsername
        createPeerConnection()

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            // ✅ FIX 1: Type must be OFFER here
                            val json = JSONObject().apply {
                                put("type", "webrtc_offer")
                                put("sdp", it.description)
                                put("receiver", targetUsername)
                            }
                            sendSignal(json.toString())
                        }

                        override fun onSetFailure(error: String?) {
                            Log.e("WebRTC", "setLocalDescription failed: $error")
                        }
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                    }, it)
                }
            }

            override fun onCreateFailure(error: String?) {
                Log.e("WebRTC", "createOffer failed: $error")
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    fun answerCall(targetUsername: String, offerSdp: String) {
        currentTarget = targetUsername
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
                                    // ✅ FIX 2: Used JSONObject instead of String
                                    val json = JSONObject().apply {
                                        put("type", "webrtc_answer")
                                        put("sdp", it.description)
                                        put("receiver", targetUsername)
                                    }
                                    sendSignal(json.toString())
                                }

                                override fun onSetFailure(error: String?) {
                                    Log.e("WebRTC", "setLocalDescription failed: $error")
                                }
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onCreateFailure(p0: String?) {}
                            }, it)
                        }
                    }

                    override fun onCreateFailure(error: String?) {
                        Log.e("WebRTC", "createAnswer failed: $error")
                    }
                    override fun onSetSuccess() {}
                    override fun onSetFailure(p0: String?) {}
                }, constraints)
            }

            override fun onSetFailure(error: String?) {
                Log.e("WebRTC", "setRemoteDescription failed: $error")
            }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, remoteSdp)
    }

    fun onRemoteAnswer(answerSdp: String) {
        val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d("WebRTC", "✅ Remote answer set successfully")
            }
            override fun onSetFailure(error: String?) {
                Log.e("WebRTC", "setRemoteDescription failed: $error")
            }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, remoteSdp)
    }

    fun onRemoteIceCandidate(candidateStr: String, sdpMid: String, sdpMLineIndex: Int) {
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, candidateStr)
        peerConnection?.addIceCandidate(candidate)
    }

    fun endCall() {
        localAudioTrack?.setEnabled(false)
        peerConnection?.close()
        peerConnection = null
    }

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

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                    peerConnection?.removeIceCandidates(candidates)
                }

                override fun onDataChannel(dc: DataChannel?) {}
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                    Log.d("WebRTC", "ICE Connection State: $newState")
                }
                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
                override fun onAddStream(stream: MediaStream?) {}

                override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                    val track = receiver?.track()
                    if (track is AudioTrack) {
                        track.setEnabled(true)
                        Log.d("WebRTC", "🔊 Remote audio track ENABLED")
                    }
                }

                override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onRenegotiationNeeded() {}
            }
        )

        localAudioTrack?.setEnabled(true)
        val streamId = "local_audio_stream"
        peerConnection?.addTrack(localAudioTrack, listOf(streamId))
    }
}