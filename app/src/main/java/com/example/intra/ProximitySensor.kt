package com.example.intra

import android.app.Activity
import android.content.Context
import android.os.PowerManager
import android.view.WindowManager

class ProximitySensor(private val activity: Activity) {

    private val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val wakeLock: PowerManager.WakeLock? = try {
        powerManager.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            "Intra:ProximityLock"
        )
    } catch (e: Exception) {
        null
    }

    // 👂 Call Active (Earpiece Mode) -> Screen should turn OFF
    fun activate() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock.acquire(30 * 60 * 1000L) // 30 min max timeout
            }
        } catch (_: Exception) {}

        // 🔥 FIX: Screen ON rakhne wala flag HATAO, taaki screen band ho sake
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // 🔊 Speaker Mode / Call End -> Screen should stay ON
    fun deactivate() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
        } catch (_: Exception) {}

        // Agar user speaker pe hai, to screen ON rakho (Video call style)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}