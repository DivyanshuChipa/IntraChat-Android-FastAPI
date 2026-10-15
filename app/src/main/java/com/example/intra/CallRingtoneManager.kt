package com.example.intra

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build

class CallRingtoneManager private constructor(private val context: Context) {
    private var ringtone: Ringtone? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // 🔥 Thread safety ke liye flag: Ye track karega ki ringtone sach me baj rahi hai ya nahi
    @Volatile
    private var isRinging = false

    companion object {
        @Volatile
        private var INSTANCE: CallRingtoneManager? = null

        fun getInstance(context: Context): CallRingtoneManager {
            return INSTANCE ?: synchronized(this) {
                val instance = CallRingtoneManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    // 🔔 START RINGTONE:
    // Synchronized banaya hai taaki Background Service aur Main UI dono ek hi millisecond
    // me call karein tab bhi do duplicate ringtone na bane (Double ring fix).
    @Synchronized
    fun start() {
        // Agar pehle se ringtone baj rahi hai toh dubara start mat karo!
        if (isRinging) {
            android.util.Log.d("RINGTONE", "🔔 Ringtone pehle se baj rahi hai, duplicate request ignore kar di.")
            return
        }

        try {
            // Safety: Agar memory me koi purani ringtone phasi ho toh pehle use completely release karo
            ringtone?.stop()
            ringtone = null

            val notification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val newRingtone = RingtoneManager.getRingtone(context, notification)

            // 🔥 Audio Routing for J2 & Modern Phones (Force Speakerphone taaki awaaz clear aaye)
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = true // Loudspeaker ON

            // Stream Type Ring set karo taaki volume rocker se ringtone volume control ho
            newRingtone?.streamType = AudioManager.STREAM_RING

            // Android 9 (Pie) aur higher par continuous loop karo jab tak user cut ya accept na kare
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                newRingtone?.isLooping = true
            }

            newRingtone?.play()
            ringtone = newRingtone
            isRinging = true
            android.util.Log.d("RINGTONE", "🔔 Ringtone successfully start ho gayi.")
        } catch (e: Exception) {
            isRinging = false
            android.util.Log.e("RINGTONE", "❌ Ringtone play karne me error aayi", e)
        }
    }

    // 🔕 STOP RINGTONE:
    // Call cut hone ya answer hone par ringtone ko 100% stop karega taaki background me loop na baje
    @Synchronized
    fun stop() {
        isRinging = false
        try {
            ringtone?.stop()
            android.util.Log.d("RINGTONE", "🔕 Ringtone successfully stop ho gayi.")
        } catch (e: Exception) {
            android.util.Log.e("RINGTONE", "❌ Ringtone stop karne me error aayi", e)
        } finally {
            // Memory release: Pointer null karo taaki Android audio server resources free ho jayein
            ringtone = null
        }
    }
}

