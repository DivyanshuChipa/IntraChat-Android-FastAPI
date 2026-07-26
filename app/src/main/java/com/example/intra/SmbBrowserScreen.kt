package com.example.intra

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

sealed class SmbUiState {
    object Scanning : SmbUiState()
    data class ServerList(val servers: List<String>) : SmbUiState()
    data class ShareList(val ip: String, val shares: List<String>) : SmbUiState()
    data class DirectoryBrowser(
        val ip: String,
        val shareName: String,
        val currentPath: String,
        val items: List<SmbFileItem>
    ) : SmbUiState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmbBrowserScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // UI State management
    var uiState by remember { mutableStateOf<SmbUiState>(SmbUiState.Scanning) }

    // Auth Dialog state
    var showAuthDialog by remember { mutableStateOf<String?>(null) } // holds IP of server being logged in
    var isAnonymous by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberCredentials by remember { mutableStateOf(true) }

    // Scanning state
    var scanningProgress by remember { mutableStateOf(false) }

    // Manual IP input state
    var manualIpAddress by remember { mutableStateOf("") }

    // File view state (for internal video/image play/display)
    var activeViewerFile by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // Pair(filePath/URI, isVideo)

    // Progress state for download/upload operations
    var activeDownloadProgress by remember { mutableStateOf<Float?>(null) }
    var activeDownloadName by remember { mutableStateOf("") }
    var isUploadingProgress by remember { mutableStateOf(false) } // true for uploading, false for downloading

    // FAB speed-dial and Folder Dialog states
    var isFabMenuExpanded by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    // SharedPreferences for SMB login credentials caching
    val sharedPrefs = remember { context.getSharedPreferences("smb_auth_cache", Context.MODE_PRIVATE) }

    // Helper to open/load directory
    fun openDirectory(shareName: String, path: String) {
        scope.launch {
            scanningProgress = true
            val items = SmbHelper.listDirectory(shareName, path)
            scanningProgress = false

            val ip = SmbHelper.currentConnectedIp ?: ""
            uiState = SmbUiState.DirectoryBrowser(ip, shareName, path, items)
        }
    }

    // Local File Picker for Uploading
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            var displayName = "upload_${System.currentTimeMillis()}"
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    displayName = cursor.getString(nameIndex)
                }
            }

            val state = uiState
            if (state is SmbUiState.DirectoryBrowser) {
                scope.launch {
                    isUploadingProgress = true
                    activeDownloadName = displayName
                    activeDownloadProgress = 0f

                    val success = SmbHelper.uploadFile(
                        shareName = state.shareName,
                        targetPath = state.currentPath,
                        fileName = displayName,
                        context = context,
                        fileUri = it,
                        onProgress = { activeDownloadProgress = it }
                    )

                    activeDownloadProgress = null

                    if (success) {
                        Toast.makeText(context, "Uploaded successfully!", Toast.LENGTH_SHORT).show()
                        openDirectory(state.shareName, state.currentPath)
                    } else {
                        Toast.makeText(context, "Upload failed.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Function to scan subnet
    fun startScan() {
        scope.launch {
           // scanningProgress = true
            uiState = SmbUiState.Scanning
            val servers = SmbHelper.scanSubnetForSmb(context)
            uiState = SmbUiState.ServerList(servers)
           // scanningProgress = false
        }
    }

    // Load cached credentials when server selected
    fun loadCachedCredentials(ip: String) {
        val cachedAnon = sharedPrefs.getBoolean("${ip}_anon", true)
        isAnonymous = cachedAnon
        username = sharedPrefs.getString("${ip}_user", "") ?: ""
        password = sharedPrefs.getString("${ip}_pass", "") ?: ""
    }

    // Save credentials to cache if checked
    fun saveCredentialsToCache(ip: String) {
        if (rememberCredentials) {
            sharedPrefs.edit()
                .putBoolean("${ip}_anon", isAnonymous)
                .putString("${ip}_user", username)
                .putString("${ip}_pass", password)
                .apply()
        } else {
            sharedPrefs.edit()
                .remove("${ip}_anon")
                .remove("${ip}_user")
                .remove("${ip}_pass")
                .apply()
        }
    }

    // Connect to server
    fun connectToServer(ip: String) {
        scope.launch {
            val auth = SmbAuth(isAnonymous, username, password)
            scanningProgress = true
            val success = SmbHelper.connect(ip, auth)
            scanningProgress = false

            if (success) {
                saveCredentialsToCache(ip)
                showAuthDialog = null
                val shares = SmbHelper.listShares()
                if (shares.isNotEmpty()) {
                    uiState = SmbUiState.ShareList(ip, shares)
                } else {
                    Toast.makeText(context, "No shares found on this server.", Toast.LENGTH_SHORT).show()
                    uiState = SmbUiState.ShareList(ip, emptyList())
                }
            } else {
                Toast.makeText(context, "Connection failed. Please check credentials or IP.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Format file size
    fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    // Download and handle file tap
    fun handleFileClick(shareName: String, fileItem: SmbFileItem) {
        val extension = fileItem.name.substringAfterLast('.', "").lowercase()
        val isImage = extension in listOf("jpg", "jpeg", "png", "webp", "gif")
        val isVideo = extension in listOf("mp4", "mkv", "avi", "mov", "3gp")

        scope.launch {
            isUploadingProgress = false
            activeDownloadName = fileItem.name
            activeDownloadProgress = 0f

            val destFile = if (isImage || isVideo) {
                File(context.cacheDir, "smb_temp_${fileItem.name}")
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val intraSmbDir = File(downloadsDir, "Intra_SMB").apply { if (!exists()) mkdirs() }
                File(intraSmbDir, fileItem.name)
            }

            val success = SmbHelper.downloadFile(
                shareName = shareName,
                filePath = fileItem.path,
                destFile = destFile,
                totalSize = fileItem.size,
                onProgress = { activeDownloadProgress = it }
            )

            activeDownloadProgress = null

            if (success && destFile.exists()) {
                if (isImage || isVideo) {
                    activeViewerFile = Pair(destFile.absolutePath, isVideo)
                } else {
                    Toast.makeText(context, "Downloaded to Downloads/Intra_SMB/${fileItem.name}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, "Download failed.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Back navigation helper
    fun handleBackPress() {
        when (val state = uiState) {
            is SmbUiState.Scanning -> {
                onBack()
            }
            is SmbUiState.ServerList -> {
                onBack()
            }
            is SmbUiState.ShareList -> {
                SmbHelper.disconnect()
                startScan()
            }
            is SmbUiState.DirectoryBrowser -> {
                if (state.currentPath.isEmpty()) {
                    uiState = SmbUiState.ShareList(state.ip, emptyList())
                    scope.launch {
                        val shares = SmbHelper.listShares()
                        uiState = SmbUiState.ShareList(state.ip, shares)
                    }
                } else {
                    val parentPath = if (state.currentPath.contains("/")) {
                        state.currentPath.substringBeforeLast("/")
                    } else {
                        ""
                    }
                    openDirectory(state.shareName, parentPath)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        startScan()
    }

    BackHandler {
        handleBackPress()
    }

    val colorScheme = MaterialTheme.colorScheme
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val topBarColor = if (isDark) colorScheme.primaryContainer else Color(0xFF6741A8)
    val topBarTextColor = if (isDark) colorScheme.onPrimaryContainer else Color.White

    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF6741A8),
            Color(0xFF3B1E6D)
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (val state = uiState) {
                            is SmbUiState.Scanning -> "Scanning Local Network..."
                            is SmbUiState.ServerList -> "Select SMB Server"
                            is SmbUiState.ShareList -> "Shares on ${state.ip}"
                            is SmbUiState.DirectoryBrowser -> {
                                if (state.currentPath.isEmpty()) state.shareName else state.currentPath.substringAfterLast("/")
                            }
                        },
                        color = topBarTextColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { handleBackPress() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = topBarTextColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarColor)
            )
        },
        floatingActionButton = {
            if (uiState is SmbUiState.DirectoryBrowser) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isFabMenuExpanded) {
                        // 1. Create Folder FAB
                        FloatingActionButton(
                            onClick = {
                                isFabMenuExpanded = false
                                showCreateFolderDialog = true
                            },
                            containerColor = Color(0xFF6741A8),
                            contentColor = Color.White,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = "New Folder")
                        }

                        // 2. Upload File FAB
                        FloatingActionButton(
                            onClick = {
                                isFabMenuExpanded = false
                                filePickerLauncher.launch("*/*")
                            },
                            containerColor = Color(0xFF6741A8),
                            contentColor = Color.White,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.Upload, contentDescription = "Upload File")
                        }
                    }

                    // Main Toggle FAB Button
                    FloatingActionButton(
                        onClick = { isFabMenuExpanded = !isFabMenuExpanded },
                        containerColor = Color(0xFF00E676),
                        contentColor = Color.White
                    ) {
                        Icon(
                            imageVector = if (isFabMenuExpanded) Icons.Filled.Close else Icons.Filled.Add,
                            contentDescription = "Actions"
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(gradient)
        ) {
            when (val state = uiState) {
                is SmbUiState.Scanning -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Scanning local subnet for active SMB servers (Port 445)...",
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            fontSize = 15.sp
                        )
                    }
                }

                is SmbUiState.ServerList -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Connect to Custom IP", color = Color.White, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = manualIpAddress,
                                        onValueChange = { manualIpAddress = it },
                                        placeholder = { Text("e.g. 192.168.31.106", color = Color.LightGray) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color.White,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            if (manualIpAddress.trim().isNotEmpty()) {
                                                loadCachedCredentials(manualIpAddress.trim())
                                                showAuthDialog = manualIpAddress.trim()
                                            } else {
                                                Toast.makeText(context, "Please enter a valid IP address", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.background(Color(0xFF25BB4B), RoundedCornerShape(8.dp))
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "Add IP", tint = Color.White)
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Discovered Servers",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { startScan() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.White)
                            }
                        }

                        if (state.servers.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    "No SMB servers found on this network.\nTap refresh or connect manually above.",
                                    color = Color.White.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(state.servers) { ip ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                            .clickable {
                                                loadCachedCredentials(ip)
                                                showAuthDialog = ip
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Filled.Computer, contentDescription = null, tint = Color(0xFF00E676))
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column {
                                                Text(ip, color = Color.White, fontWeight = FontWeight.Bold)
                                                Text("Tap to connect", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                is SmbUiState.ShareList -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text("Available Shares", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))
                        if (state.shares.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No disk shares found or permission denied.", color = Color.White.copy(alpha = 0.6f))
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(state.shares) { share ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                            .clickable { openDirectory(share, "") },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Filled.Folder, contentDescription = null, tint = Color(0xFFFFB300))
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(share, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                is SmbUiState.DirectoryBrowser -> {
                    if (state.items.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("This folder is empty.", color = Color.White.copy(alpha = 0.6f))
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            items(state.items) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (item.isDirectory) {
                                                openDirectory(state.shareName, item.path)
                                            } else {
                                                handleFileClick(state.shareName, item)
                                            }
                                        }
                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (item.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                                        contentDescription = null,
                                        tint = if (item.isDirectory) Color(0xFFFFB300) else Color(0xFF00B0FF),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = if (item.isDirectory) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!item.isDirectory) {
                                            Text(
                                                text = formatSize(item.size),
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            }
                        }
                    }
                }
            }

            if (scanningProgress) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }

    // ----------------------------------------------------
    // Create Folder Dialog
    // ----------------------------------------------------
    if (showCreateFolderDialog) {
        Dialog(onDismissRequest = {
            showCreateFolderDialog = false
            newFolderName = ""
        }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                ) {
                    Text(
                        text = "Create New Folder",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        placeholder = { Text("Folder Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            showCreateFolderDialog = false
                            newFolderName = ""
                        }) {
                            Text("Cancel", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val state = uiState
                                if (state is SmbUiState.DirectoryBrowser && newFolderName.trim().isNotEmpty()) {
                                    scope.launch {
                                        val success = SmbHelper.createFolder(
                                            shareName = state.shareName,
                                            parentPath = state.currentPath,
                                            folderName = newFolderName.trim()
                                        )
                                        showCreateFolderDialog = false
                                        if (success) {
                                            Toast.makeText(context, "Folder created!", Toast.LENGTH_SHORT).show()
                                            openDirectory(state.shareName, state.currentPath)
                                        } else {
                                            Toast.makeText(context, "Failed to create folder.", Toast.LENGTH_SHORT).show()
                                        }
                                        newFolderName = ""
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6741A8))
                        ) {
                            Text("Create", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // ----------------------------------------------------
    // Authentication Dialog
    // ----------------------------------------------------
    showAuthDialog?.let { ip ->
        Dialog(onDismissRequest = { showAuthDialog = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                ) {
                    Text(
                        text = "Authentication Required",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Connect to SMB server at $ip",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { isAnonymous = true }.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = isAnonymous, onClick = { isAnonymous = true })
                            Text("Connect as Anonymous / Guest", color = MaterialTheme.colorScheme.onSurface)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { isAnonymous = false }.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = !isAnonymous, onClick = { isAnonymous = false })
                            Text("Registered User", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    if (!isAnonymous) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { rememberCredentials = !rememberCredentials }
                    ) {
                        Checkbox(checked = rememberCredentials, onCheckedChange = { rememberCredentials = it })
                        Text("Remember details", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAuthDialog = null }) {
                            Text("Cancel", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { connectToServer(ip) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6741A8))
                        ) {
                            Text("Connect", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // ----------------------------------------------------
    // Live Download / Upload Progress Dialog
    // ----------------------------------------------------
    activeDownloadProgress?.let { progress ->
        Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isUploadingProgress) "Uploading File" else "Downloading File",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = activeDownloadName,
                        fontSize = 13.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = String.format(Locale.US, "%.0f%%", progress * 100),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // ----------------------------------------------------
    // Internal File Viewer dialogs (Image / Video player)
    // ----------------------------------------------------
    activeViewerFile?.let { viewer ->
        val fileUri = viewer.first
        val isVideo = viewer.second

        if (isVideo) {
            VideoPlayerDialog(
                videoUrl = fileUri,
                onDismiss = { activeViewerFile = null }
            )
        } else {
            ImageViewerDialog(
                imageUrl = fileUri,
                onDismiss = { activeViewerFile = null }
            )
        }
    }
}
