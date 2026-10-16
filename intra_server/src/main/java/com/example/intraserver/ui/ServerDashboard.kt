package com.example.intraserver.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.intraserver.server.ConnectionManager
import com.example.intraserver.service.ServerService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDashboard(
    isServerRunning: Boolean,
    serverIp: String,
    serverPort: Int,
    onToggleServer: (Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val fullUrl = "http://$serverIp:$serverPort"

    // Live logs state
    val logs = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        ConnectionManager.onLogEvent = { logLine ->
            logs.add(logLine)
            if (logs.size > 200) logs.removeAt(0)
        }
        onDispose {
            ConnectionManager.onLogEvent = null
        }
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    // QR Code Bitmap
    val qrBitmap = remember(serverIp, serverPort, isServerRunning) {
        if (isServerRunning) QrCodeGenerator.generateQrBitmap(fullUrl, 400) else null
    }

    val statusColor by animateColorAsState(
        targetValue = if (isServerRunning) Color(0xFF10B981) else Color(0xFFEF4444),
        label = "statusColor"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚡ IntraServer", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .border(1.dp, statusColor, RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isServerRunning) "ONLINE" else "OFFLINE",
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Dummy click - future me settings open karne ke liye
                        Toast.makeText(context, "Settings coming soon", Toast.LENGTH_SHORT).show()
                        onOpenSettings()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ){
            // ==========================================
            // 1. MAIN SERVER CONTROLLER CARD
            // ==========================================
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    FilledIconButton(
                        onClick = { onToggleServer(!isServerRunning) },
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isServerRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Toggle Server",
                            modifier = Modifier.size(36.dp),
                            tint = Color.White
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = if (isServerRunning) "Server is Running" else "Server is Stopped",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isServerRunning) fullUrl else "Ready to start",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isServerRunning) MaterialTheme.colorScheme.primary else Color.Gray
                            )

                            if (isServerRunning) {
                                Spacer(Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Server IP", fullUrl))
                                        Toast.makeText(context, "Copied IP to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                }

                                Spacer(Modifier.width(4.dp))

                                IconButton(
                                    onClick = {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
                                        context.startActivity(browserIntent)
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = "Open", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ==========================================
            // 2. QR CODE PAIRING
            // ==========================================
            AnimatedVisibility(visible = isServerRunning && qrBitmap != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        qrBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Pairing QR Code",
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }

                        Spacer(Modifier.width(16.dp))

                        Column {
                            Text("Fast Client Pairing", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Other phones can scan this QR code or type $fullUrl in IntraChat Settings.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 4. LIVE LOG TERMINAL
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side: Text
                Text(
                    text = "Live Activity Logs",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                // Right side: Log Cleaner (Delete) Icon
                IconButton(
                    onClick = { logs.clear() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Clear Logs",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = if (isServerRunning) "Waiting for connections..." else "Press Start to launch server",
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(logs) { log ->
                            Text(
                                text = log,
                                color = if (log.contains("❌") || log.contains("⚠️")) Color(0xFFF87171)
                                else if (log.contains("🚀") || log.contains("✨")) Color(0xFF34D399)
                                else Color(0xFFE2E8F0),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
