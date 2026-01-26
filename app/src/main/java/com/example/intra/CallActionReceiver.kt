package com.example.intra

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.intra.MainActivity.Companion.CALL_NOTIFICATION_ID
import org.json.JSONObject

class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sender = intent.getStringExtra("sender") ?: return
        val photo = intent.getStringExtra("photo")
        val ringtoneManager = CallRingtoneManager.getInstance(context)

        when (intent.action) {

            "CALL_ACCEPT" -> {
                // Stop ringtone immediately
                ringtoneManager.stop()

                // Clear notification
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(CALL_NOTIFICATION_ID)

                val i = Intent(context, MainActivity::class.java).apply {
                    action = "OPEN_CALL_SCREEN"
                    putExtra("incoming_sender", sender) // Use incoming_sender for photo support in MainActivity
                    putExtra("incoming_photo", photo)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(i)
            }

            "CALL_REJECT" -> {
                // Stop ringtone immediately
                ringtoneManager.stop()

                // Clear notification
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(CALL_NOTIFICATION_ID)

                val json = JSONObject().apply {
                    put("type", "call_rejected")
                    put("receiver", sender)
                }
                WsManager.send(json.toString())
            }
        }
    }
}
