package com.example.intraserver.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.intraserver.db.ServerDatabase
import com.example.intraserver.db.ServerUser
import com.example.intraserver.server.ConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 👑 AdminScreen.kt - Android On-Device Admin Control Panel
 * Modular, clean, and complete controls for server owner (Users, Settings, Storage Cleanup).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onBack: () -> Unit,
    db: ServerDatabase,
    isServerRunning: Boolean,
    serverIp: String,
    serverPort: Int
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Users, 1: Settings & Key, 2: Storage
    var usersList by remember { mutableStateOf<List<ServerUser>>(emptyList()) }
    var onlineUsers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var adminKey by remember { mutableStateOf(db.getAdminKey()) }
    var requireApproval by remember { mutableStateOf(db.getRequireApproval()) }
    var discoveryEnabled by remember { mutableStateOf(db.getDiscoveryEnabled()) }
    var storageUsedMb by remember { mutableStateOf("0.00") }
    var uploadsCount by remember { mutableIntStateOf(0) }

    // AI & Lumir Bot State
    var aiEnabled by remember { mutableStateOf(db.getAiConfig().aiEnabled) }
    var ollamaUrlText by remember { mutableStateOf(db.getAiConfig().ollamaUrl) }
    var aiModelText by remember { mutableStateOf(db.getAiConfig().aiModel) }

    // Dialog States
    var showResetDialogFor by remember { mutableStateOf<String?>(null) }
    var newPasswordText by remember { mutableStateOf("") }
    var showDeleteConfirmFor by remember { mutableStateOf<String?>(null) }

    // Refresh data
    fun refreshData() {
        usersList = db.getAllUsers()
        onlineUsers = ConnectionManager.getOnlineUsers()
        adminKey = db.getAdminKey()
        requireApproval = db.getRequireApproval()
        discoveryEnabled = db.getDiscoveryEnabled()

        val aiCfg = db.getAiConfig()
        aiEnabled = aiCfg.aiEnabled
        ollamaUrlText = aiCfg.ollamaUrl
        aiModelText = aiCfg.aiModel

        val uploadDir = File(context.filesDir, "uploads")
        val files = uploadDir.listFiles()?.filter { it.isFile } ?: emptyList()
        uploadsCount = files.size
        val totalBytes = files.sumOf { it.length() }
        storageUsedMb = String.format(java.util.Locale.US, "%.2f", totalBytes / (1024.0 * 1024.0))
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("👑 Admin Panel", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isServerRunning) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isServerRunning) "PORT $serverPort" else "STOPPED",
                                color = if (isServerRunning) Color(0xFF10B981) else Color(0xFFEF4444),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        refreshData()
                        Toast.makeText(context, "Refreshed ✅", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
        ) {
            // Summary Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdminStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Users",
                    value = "${usersList.size}",
                    icon = Icons.Default.People,
                    color = Color(0xFF89B4FA)
                )
                AdminStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Online",
                    value = "${onlineUsers.size}",
                    icon = Icons.Default.Wifi,
                    color = Color(0xFFA6E3A1)
                )
                AdminStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Uploads",
                    value = "${storageUsedMb}M",
                    icon = Icons.Default.Folder,
                    color = Color(0xFFF9E2AF)
                )
            }

            // Tabs Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Users (${usersList.size})", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Group, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Key & Rules", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.VpnKey, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Storage", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.CleaningServices, contentDescription = null) }
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> {
                    // TAB 1: USERS LIST
                    if (usersList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No users registered yet.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(usersList, key = { it.username }) { user ->
                                val isOnline = onlineUsers.any { it.equals(user.username, ignoreCase = true) }
                                UserItemCard(
                                    user = user,
                                    isOnline = isOnline,
                                    onApprove = {
                                        db.approveUser(user.username)
                                        refreshData()
                                        Toast.makeText(context, "User '${user.username}' Approved ✅", Toast.LENGTH_SHORT).show()
                                    },
                                    onResetPassword = {
                                        showResetDialogFor = user.username
                                        newPasswordText = ""
                                    },
                                    onDelete = {
                                        showDeleteConfirmFor = user.username
                                    }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // TAB 2: ADMIN KEY & RULES
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Admin Key Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("🔑 Admin Secret Key", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(
                                    "Is key ko PC ke Desktop Admin Panel ya scripts me daal kar server manage kar sakte hain.",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E1E2E))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = adminKey,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color(0xFFF9E2AF)
                                    )
                                    Row {
                                        IconButton(onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Admin Key", adminKey))
                                            Toast.makeText(context, "Admin key copied! 📋", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Key", tint = Color.White)
                                        }
                                        IconButton(onClick = {
                                            adminKey = db.regenerateAdminKey()
                                            Toast.makeText(context, "New key generated! 🔄", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.Autorenew, contentDescription = "Regenerate", tint = Color(0xFF89B4FA))
                                        }
                                    }
                                }
                            }
                        }

                        // Rules Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("⚙️ Registration & Server Rules", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                                // Require Approval Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Require Admin Approval", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text(
                                            "Naye users tab tak chat nahi kar sakenge jab tak aap unhe approve na karo.",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Switch(
                                        checked = requireApproval,
                                        onCheckedChange = { checked ->
                                            requireApproval = checked
                                            db.setRequireApproval(checked)
                                            Toast.makeText(context, "Require approval: $checked", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }

                                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

                                // Server Discovery Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("LAN Discovery Broadcast", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text(
                                            "Network me server advertise kare ya stealth mode me rahe.",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Switch(
                                        checked = discoveryEnabled,
                                        onCheckedChange = { checked ->
                                            discoveryEnabled = checked
                                            db.setDiscoveryEnabled(checked)
                                            Toast.makeText(context, "LAN Discovery: $checked", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }

                        // 🤖 AI & Lumir Bot Settings Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("🤖 Lumir AI & LAN Ollama", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(
                                    "Network me kisi bhi PC par chal rahe Ollama se phone server ko connect karein.",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Enable Lumir AI Bot", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("User 'Lumir' ko message bhej kar AI se baatein kar sakein.", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Switch(
                                        checked = aiEnabled,
                                        onCheckedChange = { checked ->
                                            aiEnabled = checked
                                            db.setAiConfig(checked, aiModelText, ollamaUrlText)
                                            Toast.makeText(context, "AI Bot: ${if (checked) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }

                                OutlinedTextField(
                                    value = ollamaUrlText,
                                    onValueChange = { ollamaUrlText = it },
                                    label = { Text("Ollama URL (PC IP:Port)") },
                                    placeholder = { Text("http://192.168.1.35:11434") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = aiModelText,
                                    onValueChange = { aiModelText = it },
                                    label = { Text("Model Name") },
                                    placeholder = { Text("gemma2:2b, llama3:8b, etc.") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Button(
                                    onClick = {
                                        db.setAiConfig(aiEnabled, aiModelText, ollamaUrlText)
                                        Toast.makeText(context, "AI Settings Saved! 💾", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Save AI Settings")
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // TAB 3: STORAGE & SERVER CLEANUP
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("📁 Uploads Storage Stats", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Files in Uploads:", color = Color.Gray)
                                    Text("$uploadsCount files", fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Disk Space Consumed:", color = Color.Gray)
                                    Text("${storageUsedMb} MB", fontWeight = FontWeight.Bold, color = Color(0xFFF9E2AF))
                                }
                            }
                        }

                        // Cleanup Actions Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("🧹 Server Cleanup", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(
                                    "Purane messages aur files delete karke phone ki storage free karein.",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )

                                Spacer(Modifier.height(6.dp))

                                // Clean Messages
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF9E2AF)),
                                        onClick = {
                                            val count = db.cleanupOldMessages(7)
                                            refreshData()
                                            Toast.makeText(context, "Deleted $count messages (>7 days)", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text("Clean Msg >7D", color = Color(0xFF1E1E2E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF9E2AF)),
                                        onClick = {
                                            val count = db.cleanupOldMessages(30)
                                            refreshData()
                                            Toast.makeText(context, "Deleted $count messages (>30 days)", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text("Clean Msg >30D", color = Color(0xFF1E1E2E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Clean Files
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF38BA8)),
                                        onClick = {
                                            val uploadDir = File(context.filesDir, "uploads")
                                            val cutoffTime = System.currentTimeMillis() - (7L * 86400L * 1000L)
                                            var deleted = 0
                                            uploadDir.listFiles()?.forEach { file ->
                                                if (file.isFile && file.lastModified() < cutoffTime) {
                                                    if (file.delete()) deleted++
                                                }
                                            }
                                            refreshData()
                                            Toast.makeText(context, "Deleted $deleted files (>7 days)", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text("Clean Files >7D", color = Color(0xFF1E1E2E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF38BA8)),
                                        onClick = {
                                            val uploadDir = File(context.filesDir, "uploads")
                                            val cutoffTime = System.currentTimeMillis() - (30L * 86400L * 1000L)
                                            var deleted = 0
                                            uploadDir.listFiles()?.forEach { file ->
                                                if (file.isFile && file.lastModified() < cutoffTime) {
                                                    if (file.delete()) deleted++
                                                }
                                            }
                                            refreshData()
                                            Toast.makeText(context, "Deleted $deleted files (>30 days)", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text("Clean Files >30D", color = Color(0xFF1E1E2E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Reset Password
    if (showResetDialogFor != null) {
        val targetUser = showResetDialogFor!!
        AlertDialog(
            onDismissRequest = { showResetDialogFor = null },
            title = { Text("Reset Password") },
            text = {
                Column {
                    Text("User '$targetUser' ke liye naya password set karein:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPasswordText,
                        onValueChange = { newPasswordText = it },
                        label = { Text("New Password") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPasswordText.isNotBlank()) {
                            db.resetPassword(targetUser, newPasswordText)
                            Toast.makeText(context, "Password reset for $targetUser ✅", Toast.LENGTH_SHORT).show()
                            showResetDialogFor = null
                        } else {
                            Toast.makeText(context, "Password cannot be empty", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialogFor = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Delete User Confirmation
    if (showDeleteConfirmFor != null) {
        val targetUser = showDeleteConfirmFor!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmFor = null },
            title = { Text("Delete User '$targetUser'?") },
            text = {
                Text("Kya aap sach me '$targetUser' ko delete karna chahte hain? Iska account aur active session permanently terminate ho jayega.")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    onClick = {
                        scope.launch {
                            ConnectionManager.kickUser(targetUser, "Account deleted by Admin.")
                            withContext(Dispatchers.IO) {
                                db.deleteUser(targetUser)
                            }
                            refreshData()
                            Toast.makeText(context, "User '$targetUser' deleted! 🗑️", Toast.LENGTH_SHORT).show()
                            showDeleteConfirmFor = null
                        }
                    }
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmFor = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AdminStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(text = value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = title, fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun UserItemCard(
    user: ServerUser,
    isOnline: Boolean,
    onApprove: () -> Unit,
    onResetPassword: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Avatar with Online Dot
            Box {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF89B4FA).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.username.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF89B4FA)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) Color(0xFF10B981) else Color(0xFF6B7280))
                        .border(2.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .align(Alignment.BottomEnd)
                )
            }

            Spacer(Modifier.width(12.dp))

            // User Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(user.username, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.width(6.dp))
                    if (user.status != "OK") {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFF9E2AF).copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("PENDING", color = Color(0xFFF9E2AF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    text = if (isOnline) "🟢 Active now" else "⚪ Offline",
                    fontSize = 11.sp,
                    color = if (isOnline) Color(0xFF10B981) else Color.Gray
                )
            }

            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (user.status != "OK") {
                    IconButton(onClick = onApprove) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Approve", tint = Color(0xFFA6E3A1))
                    }
                }
                IconButton(onClick = onResetPassword) {
                    Icon(Icons.Default.LockReset, contentDescription = "Reset Password", tint = Color(0xFFF9E2AF))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete User", tint = Color(0xFFF38BA8))
                }
            }
        }
    }
}
