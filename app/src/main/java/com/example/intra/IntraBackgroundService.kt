package com.example.intra

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import android.content.Context
import org.json.JSONObject

class IntraBackgroundService : Service() {

    companion object {
        const val CHANNEL_ID = "IntraBackgroundChannel"
        const val NOTIFICATION_ID = 1 // Persistent "Connected" notification ID
        const val CALL_NOTIFICATION_ID = 2 // Incoming Call notification ID

        const val ACTION_STOP = "STOP_SERVICE"
        const val ACTION_INCOMING_CALL = "INCOMING_CALL"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        // 1. Agar Stop button dabaya ya stop command aaya
        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 2. 🔥 NEW: Incoming Call Action (Triggered by EventPipe)
        if (action == ACTION_INCOMING_CALL) {
            val rawJson = intent?.getStringExtra("call_payload") ?: "{}"
            showIncomingCallNotification(rawJson)
            // Service ko zinda rakho taaki user notification pe tap kar sake
            return START_STICKY
        }

        // 3. Default Start: Service ko Foreground mein daalo (Persistent Notification)
        createNotificationChannel()
        val notification = createPersistentNotification()
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY // System maar de toh wapas zinda ho jaye
    }

    // ==========================================
    // 🔔 INCOMING CALL NOTIFICATION (Heads-Up)
    // ==========================================
    private fun showIncomingCallNotification(rawJson: String) {
        // Sender ka naam nikalo JSON se
        val senderName = try {
            val json = JSONObject(rawJson)
            json.optString("sender", "Unknown User")
        } catch (e: Exception) {
            "Unknown User"
        }

        // Tap karne par MainActivity khulegi
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Notification Build karo
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📞 Incoming Call")
            .setContentText("$senderName is calling you...")
            .setSmallIcon(android.R.drawable.sym_call_incoming) // Icon change kar sakte ho
            .setContentIntent(openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // High priority for Heads-up
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true) // Click karne pe hat jaye
            .setOngoing(true) // User swipe karke na hata sake jab tak ring ho raha hai
            .setColor(0xFF22C55E.toInt()) // Green color
            .setFullScreenIntent(openPendingIntent, true) // Lock screen pe bhi dikhe
            .build()

        // ✅ Ye Service class ka method hai, yahan red line nahi aayegi
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(CALL_NOTIFICATION_ID, notification)
    }

    // ==========================================
    // ⚓ PERSISTENT SERVICE NOTIFICATION
    // ==========================================
    private fun createPersistentNotification(): Notification {
        // Stop Action Intent
        val stopIntent = Intent(this, IntraBackgroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // Open App Intent
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // Settings se IP nikalo (Optional polish)
        val settingsManager = SettingsManager(this)
        val ip = settingsManager.getServerIp()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Intra Active")
            .setContentText("Connected via LAN: $ip")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // Icon change kar lena baad me
            .setContentIntent(openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Low priority (Silent)
            .setOngoing(true)
            .setColor(0xFF6741A8.toInt()) // Purple Theme Color
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Service", stopPendingIntent)
            .build()
    }

    // ==========================================
    // 🛠 CHANNEL CREATION
    // ==========================================
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Intra Background Service",
                NotificationManager.IMPORTANCE_HIGH // High importance taaki call dikhe
            ).apply {
                description = "Handles background connection and calls"
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Future cleanup code here
    }
}