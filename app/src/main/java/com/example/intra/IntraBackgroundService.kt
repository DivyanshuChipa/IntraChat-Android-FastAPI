package com.example.intra

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.intra.MainActivity.Companion.CALL_NOTIFICATION_ID
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
    private val ringtoneManager by lazy { CallRingtoneManager.getInstance(this) }


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
                    val sender = json.optString("sender")
                    // 🔥 ये लाइन जोड़ो: फोटो का URL निकालो
                    val rawPhoto = json.optString("profile_photo")

                    MyApplication.AppState.pendingCallSender = sender

                    // 🔥 Start Ringtone
                    ringtoneManager.start()

                    // 🔥 यहाँ rawPhoto भी पास कर दो
                    showIncomingCallNotification(sender, rawPhoto)
                }

                "call_ended", "call_rejected", "call_accept" -> {
                    Log.d("SERVICE", "Call signal received: $type. Stopping ringtone.")
                    ringtoneManager.stop()
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(CALL_NOTIFICATION_ID)
                }

                // 🔥 NEW: Offer को पकड़ कर सेव करो (Green button fix)
                "webrtc_offer" -> {
                    val sdp = json.optString("sdp")
                    if (sdp.isNotEmpty()) {
                        Log.d("SERVICE", "Saved pending offer for UI")
                        MyApplication.AppState.pendingCallOffer = sdp
                    }
                }

                "text", "file" -> {
                    saveMessageSafely(json, sender, receiver, type, ts)
                    if (!MyApplication.AppState.isForeground) {
                        showMessageNotification(sender, json.optString("text", "Sent a file"))
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

    private fun showIncomingCallNotification(sender: String, photoUrl: String?) {

        // ये है वो Intent जो ऐप खोलेगा और फोटो का डेटा ले जाएगा
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            action = "OPEN_CALL_SCREEN"
            putExtra("incoming_sender", sender) // MainActivity में हमने यही नाम रखा है
            putExtra("incoming_photo", photoUrl) // यहाँ फोटो डाल दी
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val contentPI = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 🔥 ACCEPT Button
        val acceptIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = "CALL_ACCEPT"
            putExtra("sender", sender)
            putExtra("photo", photoUrl)
        }
        val acceptPI = PendingIntent.getBroadcast(
            this, 2, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 🔥 REJECT Button
        val rejectIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = "CALL_REJECT"
            putExtra("sender", sender)
        }

        val rejectPI = PendingIntent.getBroadcast(
            this, 1, rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_abouticon)
            .setContentTitle("Incoming Call")
            .setContentText("$sender is calling…")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(contentPI)
            .addAction(0, "Accept", acceptPI) // Added Accept
            .addAction(0, "Reject", rejectPI)
            .setFullScreenIntent(contentPI, true) // Makes it pop up
            .build()

        // 🔥 J2 + ALL ANDROID SAFE WAY
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(CALL_NOTIFICATION_ID, notification)
    }


    private fun showMessageNotification(sender: String, message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_abouticon)
            .setContentTitle(sender)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        // 🔥 J2 SAFE WAY
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createBaseNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Intra Service")
            .setContentText("Listening for LAN connections...")
            .setSmallIcon(R.drawable.ic_abouticon)
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
        ringtoneManager.stop()
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
                // Message Text Nikalo
                val messageText = if (type == "file") {
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
