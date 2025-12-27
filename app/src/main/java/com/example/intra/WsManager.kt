package com.example.intra

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit

class WsManager(
    // ❌ Constructor se serverIp hata diya, ab dynamic lenge
    private val onMessageReceived: (String) -> Unit,
    private val onConnectionStatusChange: (String) -> Unit
) {
    private val TAG = "WsManager"
    private var webSocket: WebSocket? = null
    private val client: OkHttpClient

    private val connectionScope = CoroutineScope(Dispatchers.IO + Job())
    private var reconnectJob: Job? = null
    private var currentUsername: String? = null

    init {
        client = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun connect(username: String) {
        this.currentUsername = username

        // ✅ NEW: Dynamic URL from ApiClient/Settings
        val url = ApiClient.getWsUrl(username)

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
                currentUsername?.let { connect(it) }
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