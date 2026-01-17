package com.example.intra

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri

class CallRingtoneManager(private val context: Context) {
    private var ringtone: Ringtone? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun start() {
        if (ringtone?.isPlaying == true) return

        try {
            val notification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, notification)

            // 🔥 FIX 2: Audio Routing for J2 (Force Speaker)
            // Pehle mode normal karo, taaki earpiece se hate
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = true // Loudspeaker ON

            // Stream Type Ring set karo
            ringtone?.streamType = AudioManager.STREAM_RING

            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        try {
            ringtone?.stop()
            // Ringtone band hone ke baad speaker settings wapas normal kar sakte ho
            // ya Call connect hone par WebRTC khud handle karega
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}