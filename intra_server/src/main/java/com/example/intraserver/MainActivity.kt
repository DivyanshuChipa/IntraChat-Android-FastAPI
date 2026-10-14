package com.example.intraserver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.example.intraserver.service.ServerService
import com.example.intraserver.ui.ServerDashboard
import com.example.intraserver.util.NetworkUtils

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MaterialTheme {
                val isServerRunning by ServerService.isServerRunning.collectAsState()
                val serverIp by ServerService.serverIp.collectAsState()
                val serverPort by ServerService.serverPort.collectAsState()

                val displayIp = if (isServerRunning) serverIp else NetworkUtils.getLocalIpAddress(this)

                ServerDashboard(
                    isServerRunning = isServerRunning,
                    serverIp = displayIp,
                    serverPort = serverPort,
                    onToggleServer = { start ->
                        if (start) {
                            ServerService.start(this)
                        } else {
                            ServerService.stop(this)
                        }
                    }
                )
            }
        }
    }
}
