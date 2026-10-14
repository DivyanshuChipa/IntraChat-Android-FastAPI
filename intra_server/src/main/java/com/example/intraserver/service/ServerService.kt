package com.example.intraserver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.intraserver.MainActivity
import com.example.intraserver.server.IntraServerEngine
import com.example.intraserver.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ServerService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        private const val CHANNEL_ID = "intraserver_channel"
        private const val NOTIFICATION_ID = 9001

        private val _isServerRunning = MutableStateFlow(false)
        val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

        private val _serverIp = MutableStateFlow("127.0.0.1")
        val serverIp: StateFlow<String> = _serverIp.asStateFlow()

        private val _serverPort = MutableStateFlow(8000)
        val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, ServerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ServerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var serverEngine: IntraServerEngine? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Acquire WakeLock to keep CPU active during screen off
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IntraServer::CpuWakeLock")
        wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours safe acquire

        // Acquire WifiLock to prevent Wi-Fi power-saving mode
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "IntraServer::WifiLock")
        wifiLock?.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServer()
                stopForeground(true)
                stopSelf()
            }
            else -> {
                startServer()
            }
        }
        return START_STICKY
    }

    private fun startServer() {
        val ip = NetworkUtils.getLocalIpAddress(this)
        val port = 8000
        _serverIp.value = ip
        _serverPort.value = port

        val notification = buildNotification(ip, port)
        startForeground(NOTIFICATION_ID, notification)

        serviceScope.launch {
            if (serverEngine == null) {
                serverEngine = IntraServerEngine(applicationContext, port)
            }
            serverEngine?.start(
                onStarted = {
                    _isServerRunning.value = true
                },
                onError = {
                    _isServerRunning.value = false
                }
            )
        }
    }

    private fun stopServer() {
        serviceScope.launch {
            serverEngine?.stop()
            serverEngine = null
            _isServerRunning.value = false
        }
    }

    private fun buildNotification(ip: String, port: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ServerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⚡ IntraServer Running")
            .setContentText("Listening on http://$ip:$port")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Server", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "IntraServer Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active IntraServer status"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        serviceScope.cancel()

        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
