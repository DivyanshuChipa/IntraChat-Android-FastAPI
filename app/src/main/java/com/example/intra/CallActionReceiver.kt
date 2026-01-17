package com.example.intra

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sender = intent.getStringExtra("sender") ?: return

        when (intent.action) {

            "CALL_ACCEPT" -> {
                val i = Intent(context, MainActivity::class.java).apply {
                    action = "OPEN_CALL_SCREEN"
                    putExtra("sender", sender)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(i)
            }

            "CALL_REJECT" -> {
                val json = JSONObject().apply {
                    put("type", "call_rejected")
                    put("receiver", sender)
                }
                WsManager.send(json.toString())
            }
        }
    }
}
