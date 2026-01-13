package com.example.intra

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class IntraBackgroundService : Service() {

    companion object {
        const val CHANNEL_ID = "IntraBackgroundChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "STOP_SERVICE"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // 🔥 FIX: SettingsManager ko batao ki service band ho gayi
            SettingsManager(this).setBackgroundService(false) // 👈 YE LINE ADD KARO

            stopSelf()
            return START_NOT_STICKY
        }

        // Notification Channel banao (Android 8+ ke liye zaroori)
        createNotificationChannel()

        // ✨ THE SEXY NOTIFICATION
        val notification = createNotification()

        // Service ko Foreground me start karo
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY // Agar Android maar de, to wapas zinda ho jaye
    }

    private fun createNotification(): Notification {
        // 1. Stop Action Intent
        val stopIntent = Intent(this, IntraBackgroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Open App Intent (Jab notification pe click kare)
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Current IP (Settings se nikalo - Optional polish)
        val settingsManager = SettingsManager(this)
        val ip = settingsManager.getServerIp()

        // 🎨 DESIGNING THE NOTIFICATION (Jewelry Logic)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Intra Active") // Title
            .setContentText("Connected via LAN: $ip") // Subtitle
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // ⚡ Replace with your App Icon later
            .setContentIntent(openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Low priority = No annoying sound, just visual
            .setOngoing(true) // User swipe karke hata nahi sakta (Chipak jayega)
            .setColor(0xFF6741A8.toInt()) // 🔥 App ka Purple Theme Color

            // 👇 ACTION BUTTONS
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Service", stopPendingIntent)

            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Intra Background Service",
                NotificationManager.IMPORTANCE_LOW // Low importance taaki baar baar ghanti na baje
            ).apply {
                description = "Keeps connection alive for calls and messages"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Yahan future me WebSocket disconnect logic aayega
    }
}