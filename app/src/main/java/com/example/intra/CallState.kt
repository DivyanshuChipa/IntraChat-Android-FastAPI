package com.example.intra

enum class CallStatus {
    IDLE,       // Kuch nahi ho raha
    OUTGOING,   // Hum phone mila rahe hain
    INCOMING,   // Phone aa raha hai
    CONNECTED,  // Baat chal rahi hai
    ENDED       // Phone kat gaya
}

data class CallState(
    val status: CallStatus = CallStatus.IDLE,
    val targetUser: String = "", // Kisse baat ho rahi hai
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = true
)