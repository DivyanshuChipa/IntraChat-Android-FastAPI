package com.example.intra

import android.content.Context
import android.os.PowerManager

class ProximitySensor(context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    // 💡 Ye magic lock hai: Jab sensor dhak jayega, screen OFF ho jayegi.
    // Jab hat jayega, screen wapas ON ho jayegi. Sab automatic.
    private val wakeLock: PowerManager.WakeLock? = try {
        powerManager.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            "Intra:ProximitySensor"
        )
    } catch (e: Exception) {
        null // Kuch phones me sensor nahi hota
    }

    // Call shuru hone par ya Speaker OFF hone par activate karo
    fun activate() {
        if (wakeLock?.isHeld == false) {
            try {
                wakeLock.acquire(30 * 60 * 1000L /* 30 minutes max */)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Speaker ON hone par ya Call katne par deactivate karo
    fun deactivate() {
        if (wakeLock?.isHeld == true) {
            try {
                wakeLock.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}