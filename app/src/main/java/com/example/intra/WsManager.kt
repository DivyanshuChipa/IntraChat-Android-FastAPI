package com.example.intra

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit

class WsManager(
    private var serverIp: String = "192.168.31.104",
    private var port: Int = 8000,
    private val onMessageReceived: (String) -> Unit,
    private val onConnectionStatusChange: (String) -> Unit
) {

    fun updateServerDetails(settingsManager: SettingsManager) {
        serverIp = settingsManager.getServerIp()
        port = settingsManager.getServerPort()
        reconnect()
    }

    private fun reconnect() {
        disconnect()
        currentUsername?.let { connect(it) }
    }
    private val TAG = "WsManager"
    private var webSocket: WebSocket? = null
    private val client: OkHttpClient

    private val connectionScope = CoroutineScope(Dispatchers.IO + Job())
    private var reconnectJob: Job? = null

    // 💡 हम current user को save करेंगे ताकि reconnect करते समय याद रहे
    private var currentUsername: String? = null

    init {
        client = OkHttpClient.Builder()
            // 💡 यह हर 30 सेकंड में सर्वर को 'हार्टबीट' भेजेगा ताकि कनेक्शन न टूटे
            .pingInterval(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // 💡 connect फ़ंक्शन अब 'username' लेता है
    fun connect(username: String) {
        this.currentUsername = username

        // 💡 URL अब /ws/{username} पैटर्न फॉलो करेगा
        val url = "ws://$serverIp:$port/ws/$username"

        Log.d(TAG, "Connecting To: $url")
        onConnectionStatusChange("Connecting…")

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, SocketListener())
    }

    fun sendMessage(message: String) {
        if (webSocket?.send(message) == true) {
            Log.d(TAG, "Message sent: $message")
        } else {
            Log.e(TAG, "Send failed! Reconnecting…")
            onConnectionStatusChange("Reconnecting…")
            startReconnectLoop()
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "User closed")
        webSocket = null
        onConnectionStatusChange("Disconnected")
    }

    private fun startReconnectLoop() {
        reconnectJob?.cancel()

        reconnectJob = connectionScope.launch {
            while (isActive && webSocket == null) {
                Log.w(TAG, "Reconnecting in 5 seconds…")
                onConnectionStatusChange("Reconnecting…")
                delay(5000)

                // 💡 Reconnect करते समय उसी username का उपयोग करें
                currentUsername?.let {
                    connect(it)
                }
            }
        }
    }

    private inner class SocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "Connected ✔")
            onConnectionStatusChange("Connected")
            reconnectJob?.cancel()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Message: $text")
            onMessageReceived(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "Closing: $reason")
            this@WsManager.webSocket = null
            onConnectionStatusChange("Disconnected")
            startReconnectLoop()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "Failure: ${t.message}")
            this@WsManager.webSocket = null
            onConnectionStatusChange("Error: ${t.message}")
            startReconnectLoop()
        }
    }
}