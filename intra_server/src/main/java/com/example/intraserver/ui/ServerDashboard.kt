package com.example.intraserver.ui

import android.app.Activity
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.intraserver.server.ConnectionManager
import com.example.intraserver.service.ServerService
import com.example.intraserver.ui.theme.IntraPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDashboard(
    isServerRunning: Boolean,
    serverIp: String,
    serverPort: Int,
    onToggleServer: (Boolean) -> Unit,
    onOpenAdmin: () -> Unit = {}
) {
    val context = LocalContext.current
    val fullUrl = "http://$serverIp:$serverPort"
    val adminKey = remember { com.example.intraserver.db.ServerDatabase.getInstance(context).getAdminKey() }

    val isDark = isSystemInDarkTheme()
    val colorScheme = MaterialTheme.colorScheme

    // Signature Intra Purple TopBar (1:1 parity with Intra Client ContactListScreen & WebDropScreen)
    val topBarColor = if (isDark) colorScheme.primaryContainer else IntraPurple
    val topBarTextColor = if (isDark) colorScheme.onPrimaryContainer else Color.White

    // Sync Status Bar Color with TopBar (matching ContactListScreen)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = topBarColor.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    // Navigation Tabs: 0 -> Dashboard, 1 -> Live Console Logs
    var selectedTab by remember { mutableIntStateOf(0) }

    // Live logs state initialized with recent logs from ConnectionManager
    val logs = remember {
        mutableStateListOf<String>().apply {
            addAll(ConnectionManager.recentLogs)
        }
    }
    val listState = rememberLazyListState()

    // Log filter in Console tab: "ALL", "ERRORS", "TRAFFIC", "USERS"
    var logFilter by remember { mutableStateOf("ALL") }

    DisposableEffect(Unit) {
        ConnectionManager.onLogEvent = { logLine ->
            logs.add(logLine)
            if (logs.size > 500) logs.removeAt(0)
        }
        onDispose {
            ConnectionManager.onLogEvent = null
        }
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty() && selectedTab == 1) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    // QR Code Bitmap (kept ready for when client QR pairing is active)
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
                        Text(
                            text = "⚡ IntraServer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = topBarTextColor
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(statusColor.copy(alpha = 0.25f))
                                .border(1.dp, statusColor.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isServerRunning) "ONLINE" else "OFFLINE",
                                color = if (isServerRunning) Color(0xFF6EE7B7) else Color(0xFFFCA5A5),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAdmin) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "Admin Panel",
                            tint = topBarTextColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    titleContentColor = topBarTextColor,
                    actionIconContentColor = topBarTextColor
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ==========================================
            // 🚀 SEGMENTED NAVIGATION TABS (Dashboard vs Console Logs)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDark) Color(0xFF1E1F2B) else Color(0xFFE9EAEF))
                    .padding(4.dp)
            ) {
                // Tab 0: Dashboard
                val isDash = selectedTab == 0
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDash) (if (isDark) colorScheme.primary else IntraPurple) else Color.Transparent)
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Dashboard,
                            contentDescription = null,
                            tint = if (isDash) Color.White else (if (isDark) Color.LightGray else Color.DarkGray),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Dashboard",
                            fontSize = 13.sp,
                            fontWeight = if (isDash) FontWeight.Bold else FontWeight.Medium,
                            color = if (isDash) Color.White else (if (isDark) Color.LightGray else Color.DarkGray)
                        )
                    }
                }

                // Tab 1: Console Logs
                val isConsole = selectedTab == 1
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isConsole) (if (isDark) colorScheme.primary else IntraPurple) else Color.Transparent)
                        .clickable { selectedTab = 1 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = if (isConsole) Color.White else (if (isDark) Color.LightGray else Color.DarkGray),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Console Logs (${logs.size})",
                            fontSize = 13.sp,
                            fontWeight = if (isConsole) FontWeight.Bold else FontWeight.Medium,
                            color = if (isConsole) Color.White else (if (isDark) Color.LightGray else Color.DarkGray)
                        )
                    }
                }
            }

            // ==========================================
            // TAB CONTENT
            // ==========================================
            if (selectedTab == 0) {
                // ==========================================
                // 1. DASHBOARD VIEW (Scrollable)
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    // 1.1 SERVER CONTROLLER CARD
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
                                .padding(vertical = 24.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            FilledIconButton(
                                onClick = { onToggleServer(!isServerRunning) },
                                modifier = Modifier.size(68.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (isServerRunning) Color(0xFF10B981) else (if (isDark) colorScheme.primary else IntraPurple)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PowerSettingsNew,
                                    contentDescription = "Toggle Server",
                                    modifier = Modifier.size(34.dp),
                                    tint = Color.White
                                )
                            }

                            Spacer(Modifier.height(10.dp))

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
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isServerRunning) (if (isDark) colorScheme.primary else IntraPurple) else Color.Gray
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
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(15.dp))
                                        }

                                        Spacer(Modifier.width(4.dp))

                                        IconButton(
                                            onClick = {
                                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
                                                context.startActivity(browserIntent)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Open", modifier = Modifier.size(15.dp))
                                        }
                                    }
                                }
                            }

                            // Admin Key Row
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Admin Key: ",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                    Text(
                                        text = adminKey,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF9E2AF)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Admin Key", adminKey))
                                            Toast.makeText(context, "Admin key copied! 📋", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Admin Key", modifier = Modifier.size(13.dp))
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(
                                        onClick = onOpenAdmin,
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Tune,
                                            contentDescription = "Open Admin Panel",
                                            tint = if (isDark) colorScheme.primary else IntraPurple,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    /* ==========================================
                     * 2. FAST CLIENT PAIRING (QR CODE)
                     * [TEMPORARILY COMMENTED OUT AS REQUESTED]
                     * Client currently connects via auto-discovery (mDNS / UDP).
                     * Can be re-enabled whenever QR scan pairing is added to client.
                     * ==========================================
                    AnimatedVisibility(visible = isServerRunning && qrBitmap != null) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
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
                    ========================================== */

                    Spacer(Modifier.height(14.dp))

                    // 1.2 QUICK ACCESS SERVICES
                    Text(
                        text = "Active LAN Services",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )

                    // WebDrop Card
                    val dropUrl = "$fullUrl/drop"
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🚀", fontSize = 22.sp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("WebDrop (LAN Files & Clipboard)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(
                                        if (isServerRunning) dropUrl else "Start server to access",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = if (isServerRunning) (if (isDark) colorScheme.primary else IntraPurple) else Color.Gray
                                    )
                                }
                            }

                            if (isServerRunning) {
                                Row {
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("WebDrop URL", dropUrl))
                                            Toast.makeText(context, "WebDrop link copied! 📋", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Drop URL", modifier = Modifier.size(15.dp))
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(
                                        onClick = {
                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(dropUrl))
                                            context.startActivity(browserIntent)
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Open WebDrop", modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }

                    // IntraDrive Card
                    val isDriveEnabled = remember(isServerRunning) {
                        com.example.intraserver.db.ServerDatabase.getInstance(context).getIntraDriveEnabled()
                    }
                    if (isDriveEnabled) {
                        val driveUrl = "$fullUrl/drive"
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🗄️", fontSize = 22.sp)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("IntraDrive (Network Storage)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(
                                            if (isServerRunning) driveUrl else "Start server to access",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = if (isServerRunning) (if (isDark) colorScheme.primary else IntraPurple) else Color.Gray
                                        )
                                        Text(
                                            "Map network drive in Windows as Z:",
                                            fontSize = 10.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }

                                if (isServerRunning) {
                                    Row {
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("IntraDrive URL", driveUrl))
                                                Toast.makeText(context, "IntraDrive link copied! 📋", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Drive URL", modifier = Modifier.size(15.dp))
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        IconButton(
                                            onClick = {
                                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(driveUrl))
                                                context.startActivity(browserIntent)
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Open IntraDrive", modifier = Modifier.size(15.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // 1.3 MINI LIVE ACTIVITY TICKER (Sneak peek with quick jump to Console tab)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Activity Sneak-Peek",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { selectedTab = 1 },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "Full Console ↗",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) colorScheme.primary else IntraPurple
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                            .clickable { selectedTab = 1 }
                            .padding(12.dp)
                    ) {
                        if (logs.isEmpty()) {
                            Text(
                                text = if (isServerRunning) "Waiting for connections... Tap to open console" else "Press Start to launch server",
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val recentSlice = logs.takeLast(3)
                                recentSlice.forEach { log ->
                                    Text(
                                        text = log,
                                        color = if (log.contains("❌") || log.contains("⚠️")) Color(0xFFF87171)
                                        else if (log.contains("🚀") || log.contains("✨")) Color(0xFF34D399)
                                        else Color(0xFFCBD5E1),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                }
            } else {
                // ==========================================
                // 2. DEDICATED FULL-HEIGHT CONSOLE LOGS TERMINAL
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    // Filters & Actions Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Filter Pills
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val filters = listOf("ALL" to "All", "ERRORS" to "⚠️ Errors", "TRAFFIC" to "🚀 Traffic", "USERS" to "👤 Users")
                            filters.forEach { (key, label) ->
                                val isSelected = logFilter == key
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) (if (isDark) colorScheme.primary else IntraPurple) else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { logFilter = key }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Terminal Actions (Copy & Clear)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Copy All
                            IconButton(
                                onClick = {
                                    if (logs.isNotEmpty()) {
                                        val allLogsText = logs.joinToString("\n")
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Server Logs", allLogsText))
                                        Toast.makeText(context, "Copied all logs to clipboard! 📋", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No logs to copy", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Logs", modifier = Modifier.size(16.dp))
                            }

                            Spacer(Modifier.width(4.dp))

                            // Clear
                            IconButton(
                                onClick = {
                                    logs.clear()
                                    ConnectionManager.clearLogs()
                                    Toast.makeText(context, "Logs cleared 🧹", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Clear Logs", modifier = Modifier.size(18.dp), tint = Color(0xFFF87171))
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Full Height Terminal Window
                    val filteredLogs = remember(logs.size, logFilter) {
                        when (logFilter) {
                            "ERRORS" -> logs.filter { it.contains("❌") || it.contains("⚠️") || it.contains("fail", ignoreCase = true) || it.contains("error", ignoreCase = true) }
                            "TRAFFIC" -> logs.filter { it.contains("🚀") || it.contains("WebDrop") || it.contains("drive", ignoreCase = true) || it.contains("HTTP") }
                            "USERS" -> logs.filter { it.contains("👤") || it.contains("connected", ignoreCase = true) || it.contains("offline", ignoreCase = true) }
                            else -> logs
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0D1117))
                            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        if (filteredLogs.isEmpty()) {
                            Text(
                                text = if (isServerRunning) "Waiting for connections... Logs will stream here live." else "Press Start to launch server",
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(filteredLogs) { log ->
                                    Text(
                                        text = log,
                                        color = if (log.contains("❌") || log.contains("⚠️")) Color(0xFFF87171)
                                        else if (log.contains("🚀") || log.contains("✨") || log.contains("connected")) Color(0xFF34D399)
                                        else if (log.contains("📋") || log.contains("WebDrop")) Color(0xFFA5B4FC)
                                        else if (log.contains("🗄️") || log.contains("drive")) Color(0xFF38BDF8)
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
    }
}
