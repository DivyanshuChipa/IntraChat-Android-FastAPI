package com.example.intra

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import java.util.concurrent.TimeUnit

object WsManager {

    private const val TAG = "WsManager"
    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var currentUsername: String? = null
    private var appContext: Context? = null

    // 🔔 Multiple listeners (Service + ViewModel)
    interface Listener {
        fun onMessage(text: String)
        fun onStatus(status: String)
    }

    private val listeners = mutableSetOf<Listener>()

    fun addListener(listener: Listener) {
        listeners.add(listener)
        listener.onStatus(if (isConnected) "Connected" else "Disconnected")
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    // 🔌 SINGLE connection point
    fun connect(context: Context, username: String) {
        if (webSocket != null) {
            Log.d(TAG, "Already connected, skipping")
            return
        }

        appContext = context.applicationContext
        currentUsername = username

        val settings = SettingsManager(appContext!!)
        val url = "ws://${settings.getServerIp()}:${settings.getServerPort()}/ws/$username"

        Log.d(TAG, "Connecting to $url")
        notifyStatus("Connecting…")

        val client = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, socketListener)
    }

    fun send(message: String) {
        if (webSocket?.send(message) != true) {
            Log.e(TAG, "Send failed")
            notifyStatus("Send Failed")
        }
    }

    // ❗ Call ONLY on logout
    fun disconnect() {
        webSocket?.close(1000, "Logout")
        webSocket = null
        isConnected = false
        notifyStatus("Disconnected")
    }

    // ============================
    // 🔁 Internal helpers
    // ============================

    private val socketListener = object : WebSocketListener() {

        override fun onOpen(ws: WebSocket, response: Response) {
            Log.d(TAG, "Connected ✔")
            isConnected = true
            notifyStatus("Connected")
        }

        override fun onMessage(ws: WebSocket, text: String) {
            notifyMessage(text)
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "Failure: ${t.message}")
            cleanupAndReconnect()
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "Closed: $reason")
            cleanupAndReconnect()
        }
    }

    private fun cleanupAndReconnect() {
        webSocket = null
        isConnected = false
        notifyStatus("Reconnecting…")

        Handler(Looper.getMainLooper()).postDelayed({
            val ctx = appContext
            val user = currentUsername
            if (ctx != null && user != null) {
                connect(ctx, user)
            }
        }, 3000)
    }

    private fun notifyMessage(text: String) {
        listeners.forEach { it.onMessage(text) }
    }

    private fun notifyStatus(status: String) {
        listeners.forEach { it.onStatus(status) }
    }
}
