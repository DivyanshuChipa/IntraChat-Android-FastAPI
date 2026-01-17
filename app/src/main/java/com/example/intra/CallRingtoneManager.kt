package com.example.intra

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri

class CallRingtoneManager(context: Context) {

    private var ringtone: Ringtone? = null

    init {
        try {
            val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, uri)

            // Audio Attributes set karte hain taaki Call Volume use ho, Media Volume nahi
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun start() {
        // 🔥 MAGIC FIX: Agar pehle se baj raha hai, to kuch mat karo (Rotation Safe)
        if (ringtone?.isPlaying == true) return

        try {
            // J2 Specific: Stream Type Set karo
            ringtone?.streamType = android.media.AudioManager.STREAM_RING
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    fun stop() {
        try {
            if (ringtone?.isPlaying == true) {
                ringtone?.stop()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}