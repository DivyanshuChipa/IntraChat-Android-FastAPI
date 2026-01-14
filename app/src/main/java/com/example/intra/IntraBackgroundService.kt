package com.example.intra

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.intra.MyApplication.AppState
import com.example.intra.database.ChatDao
import com.example.intra.database.ChatDatabase
import com.example.intra.database.ChatMessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

class IntraBackgroundService : Service(), WsManager.Listener {
    private lateinit var chatDao: ChatDao

    // 🔥 ADD THIS (MISSING PART)
    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val CHANNEL_ID = "intra_service_channel"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        chatDao = ChatDatabase
            .getDatabase(applicationContext)
            .chatDao()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Notification dikhao taaki Service kill na ho
        startForeground(NOTIFICATION_ID, createBaseNotification())

        // 2. Settings se username lo
        val settings = SettingsManager(this)
        val username = settings.getUsername()

        if (!username.isNullOrEmpty()) {
            // 3. WsManager ko bolo connect karne ko
            WsManager.addListener(this) // Service khud sunega messages
            WsManager.connect(this, username)
        }

        return START_STICKY // Agar system kill kare, to wapas start ho
    }

    // --- Message Handling (Jab App Background me ho) ---
    override fun onMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type")
            val sender = json.optString("sender")
            val receiver = json.optString("receiver")
            val ts = json.optLong("timestamp", System.currentTimeMillis())

            when (type) {

                "call_request" -> {
                    showIncomingCallNotification(sender)
                }

                "text", "file" -> {

                    // ✅ 1️⃣ SAVE MESSAGE (CRASH-FREE)
                    saveMessageSafely(
                        json = json,
                        sender = sender,
                        receiver = receiver,
                        type = type,
                        timestamp = ts
                    )

                    // ✅ 2️⃣ NOTIFICATION ONLY IF APP BACKGROUND
                    if (!AppState.isForeground) {
                        showMessageNotification(
                            sender,
                            json.optString("text", "Sent a file")
                        )
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("SERVICE_MSG", "Parse error", e)
        }
    }


    override fun onStatus(status: String) {
        // Optional: Notification update kar sakte ho status ke hisab se
    }

    // --- Notifications ---

    private fun showIncomingCallNotification(sender: String) {
        // Call Screen kholne ka Intent
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            action = "INCOMING_CALL"
            putExtra("sender", sender)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Apna icon lagana
            .setContentTitle("Incoming Call")
            .setContentText("$sender is calling...")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true) // Screen Off pe chalega
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(999, notification)
    }

    private fun showMessageNotification(sender: String, message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(sender)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createBaseNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Intra Service")
            .setContentText("Listening for LAN connections...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Intra Background Service",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        WsManager.removeListener(this)
        serviceScope.cancel()   // 🔥 IMPORTANT
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun saveMessageSafely(
        json: JSONObject,
        sender: String,
        receiver: String,
        type: String,
        timestamp: Long
    ) {
        serviceScope.launch {
            try {
                val messageText =
                    if (type == "file") {
                        "Shared File: ${json.optString("filename", "File")}"
                    } else {
                        json.optString("text", "")
                    }

                val entity = ChatMessageEntity(
                    text = messageText,
                    isSelf = false,
                    type = type,
                    fileUrl = json.optString("url", null),
                    fileName = json.optString("filename", null),
                    senderName = sender,
                    sender = sender,
                    receiver = receiver,
                    timestamp = timestamp
                )

                chatDao.insertMessage(entity)

            } catch (e: Exception) {
                Log.e("DB_INSERT", "Room insert failed", e)
            }
        }
    }



}