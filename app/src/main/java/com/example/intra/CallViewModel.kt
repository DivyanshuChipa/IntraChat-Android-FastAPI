package com.example.intra

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class CallViewModel : ViewModel() {

    var callState = mutableStateOf(CallState())
        private set

    var pendingOfferSdp: String? = null
        private set

    // 🔔 NEW: Ringtone bajani hai ya nahi?
    var isRinging = mutableStateOf(false)
        private set

    var callActive = false
        private set

    // --- Actions ---

    fun onIncomingCall(sender: String, profilePhotoUrl: String? = null) {
        if (callActive) return
        callActive = true
        isRinging.value = true
        callState.value = CallState(
            status = CallStatus.INCOMING,
            targetUser = sender,
            profilePhotoUrl = profilePhotoUrl
        )
        isRinging.value = true // 🔔 Start Ringing
    }

    fun onStartOutgoingCall(target: String, profilePhotoUrl: String? = null) {
        callActive = true
        callState.value = CallState(
            status = CallStatus.OUTGOING,
            targetUser = target,
            profilePhotoUrl = profilePhotoUrl,
            isSpeakerOn = true
        )
        // Outgoing me ringtone nahi bajti, tone bajti hai (wo baad me dekhenge)
    }

    fun onCallConnected() {
        callState.value = callState.value.copy(status = CallStatus.CONNECTED)
        isRinging.value = false // 🔕 Stop Ringing
    }

    fun onCallEnded() {
        callActive = false
        callState.value = CallState()
        pendingOfferSdp = null
        isRinging.value = false // 🔕 Stop Ringing
    }

    fun setIncomingOffer(sdp: String) {
        pendingOfferSdp = sdp
    }

    fun updateMuteState(isMuted: Boolean) {
        callState.value = callState.value.copy(isMuted = isMuted)
    }

    fun updateSpeakerState(isSpeakerOn: Boolean) {
        callState.value = callState.value.copy(isSpeakerOn = isSpeakerOn)
    }
}