package com.example.intra

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDropScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    val apiService = remember { ApiClient.apiService }
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    val isDark = isSystemInDarkTheme()
    val colorScheme = MaterialTheme.colorScheme
    val topBarColor = if (isDark) colorScheme.primaryContainer else Color(0xFF6741A8)
    val topBarTextColor = if (isDark) colorScheme.onPrimaryContainer else Color.White

    // Tabs: 0 -> Files, 1 -> LAN Clipboard
    var selectedTab by remember { mutableIntStateOf(0) }

    // Files State
    var files by remember { mutableStateOf<List<WebDropFileItem>>(emptyList()) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadStatusText by remember { mutableStateOf("") }
    var fileToDelete by remember { mutableStateOf<WebDropFileItem?>(null) }
    var activeViewerFile by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var activeAudioPlayer by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Clipboard / Text State
    var texts by remember { mutableStateOf<List<WebDropTextItem>>(emptyList()) }
    var clipboardInputText by remember { mutableStateOf("") }
    var isPostingText by remember { mutableStateOf(false) }
    var snippetToDelete by remember { mutableStateOf<WebDropTextItem?>(null) }
    var showClearAllSnippetsDialog by remember { mutableStateOf(false) }

    // General State
    var isLoading by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }

    // Helper: Fetch files
    fun fetchFiles() {
        scope.launch {
            isLoading = true
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.getWebDropFiles()
                }
                if (response.isSuccessful && response.body() != null) {
                    files = response.body()?.files ?: emptyList()
                }
            } catch (e: Exception) {
                // Toast only if explicitly refreshing or error
            } finally {
                isLoading = false
            }
        }
    }

    // Helper: Fetch clipboard texts
    fun fetchTexts() {
        scope.launch {
            isLoading = true
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.getWebDropTexts()
                }
                if (response.isSuccessful && response.body() != null) {
                    texts = response.body()?.texts ?: emptyList()
                }
            } catch (e: Exception) {
                // Ignore network error gracefully
            } finally {
                isLoading = false
            }
        }
    }

    fun refreshAll() {
        fetchFiles()
        fetchTexts()
    }

    // Helper: Delete file
    fun deleteFile(fileItem: WebDropFileItem) {
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.deleteWebDropFile(WebDropDeleteRequest(fileItem.name))
                }
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(context, "Deleted ${fileItem.name}", Toast.LENGTH_SHORT).show()
                    fetchFiles()
                } else {
                    Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Helper: Post clipboard snippet
    fun postTextSnippet(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            Toast.makeText(context, "Please enter some text or link to drop", Toast.LENGTH_SHORT).show()
            return
        }

        val senderName = settingsManager.getUsername() ?: "Android User"
        scope.launch {
            isPostingText = true
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.postWebDropText(WebDropTextRequest(trimmed, senderName))
                }
                if (response.isSuccessful && response.body()?.success == true) {
                    clipboardInputText = ""
                    Toast.makeText(context, "Dropped to LAN Clipboard! 📋", Toast.LENGTH_SHORT).show()
                    fetchTexts()
                } else {
                    Toast.makeText(context, "Failed to share snippet", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                isPostingText = false
            }
        }
    }

    // Helper: Delete single text snippet
    fun deleteSnippet(snippet: WebDropTextItem) {
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.deleteWebDropText(WebDropDeleteTextRequest(snippet.id))
                }
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(context, "Snippet deleted", Toast.LENGTH_SHORT).show()
                    fetchTexts()
                } else {
                    Toast.makeText(context, "Failed to delete snippet", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Helper: Clear all snippets
    fun clearAllSnippets() {
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService.clearWebDropTexts()
                }
                if (response.isSuccessful && response.body()?.success == true) {
                    Toast.makeText(context, "Cleared all clipboard snippets! 🧹", Toast.LENGTH_SHORT).show()
                    fetchTexts()
                } else {
                    Toast.makeText(context, "Failed to clear clipboard", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Helper: Copy text to system clipboard
    fun copyTextToDeviceClipboard(text: String) {
        val clip = ClipData.newPlainText("Intra WebDrop", text)
        clipboardManager.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to device clipboard! 📋", Toast.LENGTH_SHORT).show()
    }

    // Helper: Quick Paste & Drop from device clipboard directly
    fun quickPasteAndDrop() {
        val primaryClip = clipboardManager.primaryClip
        if (primaryClip != null && primaryClip.itemCount > 0) {
            val text = primaryClip.getItemAt(0).text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                postTextSnippet(text)
            } else {
                Toast.makeText(context, "Device clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Device clipboard is empty", Toast.LENGTH_SHORT).show()
        }
    }

    // Helper: Convert Uri to temp file for upload
    fun uriToTempFile(uri: Uri): Pair<File, String>? {
        return try {
            val contentResolver = context.contentResolver
            var fileName = "drop_${System.currentTimeMillis()}"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            val tempFile = File(context.cacheDir, fileName)
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(tempFile)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            Pair(tempFile, fileName)
        } catch (e: Exception) {
            null
        }
    }

    // File Picker Launcher (Multiple files support)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult

        scope.launch {
            isUploading = true
            var uploadedCount = 0
            val total = uris.size

            for ((index, uri) in uris.withIndex()) {
                val filePair = uriToTempFile(uri)
                if (filePair != null) {
                    val (tempFile, displayName) = filePair
                    uploadStatusText = "Uploading (${index + 1}/$total) $displayName..."

                    try {
                        val requestFile = tempFile.asRequestBody("multipart/form-data".toMediaTypeOrNull())
                        val part = MultipartBody.Part.createFormData("files", displayName, requestFile)
                        val senderBody = (settingsManager.getUsername() ?: "Android User").toRequestBody("text/plain".toMediaTypeOrNull())

                        val response = withContext(Dispatchers.IO) {
                            apiService.uploadWebDropFile(part, senderBody)
                        }

                        if (response.isSuccessful && response.body()?.success == true) {
                            uploadedCount++
                        }
                    } catch (e: Exception) {
                        // ignore and continue
                    } finally {
                        tempFile.delete()
                    }
                }
            }

            isUploading = false
            uploadStatusText = ""
            if (uploadedCount > 0) {
                Toast.makeText(context, "$uploadedCount file(s) dropped to WebDrop! 🚀", Toast.LENGTH_SHORT).show()
                fetchFiles()
            } else {
                Toast.makeText(context, "Upload failed. Check connection.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Helper: Download file to Downloads/Intra_WebDrop
    fun downloadFile(fileItem: WebDropFileItem) {
        val extension = fileItem.name.substringAfterLast('.', "").lowercase()
        val isImage = extension in listOf("jpg", "jpeg", "png", "webp", "gif")
        val isVideo = extension in listOf("mp4", "mkv", "avi", "mov", "3gp")
        val isAudio = extension in listOf("mp3", "wav", "m4a", "flac", "aac", "ogg", "opus", "wma")

        val fullUrl = if (fileItem.url.startsWith("http")) {
            fileItem.url
        } else {
            "${settingsManager.getBaseUrl().removeSuffix("/")}${fileItem.url}"
        }

        if (isAudio) {
            activeAudioPlayer = Pair(fullUrl, fileItem.name)
        } else if (isImage || isVideo) {
            activeViewerFile = Pair(fullUrl, isVideo)
        } else {
            scope.launch {
                try {
                    Toast.makeText(context, "Downloading ${fileItem.name}...", Toast.LENGTH_SHORT).show()
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val dropFolder = File(downloadsDir, "Intra_WebDrop").apply { if (!exists()) mkdirs() }
                    val destFile = File(dropFolder, fileItem.name)

                    withContext(Dispatchers.IO) {
                        val url = java.net.URL(fullUrl)
                        url.openStream().use { input ->
                            FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    Toast.makeText(context, "Saved to Downloads/Intra_WebDrop! 📁", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Initial load
    LaunchedEffect(Unit) {
        refreshAll()
    }

    BackHandler {
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Intra WebDrop",
                            fontWeight = FontWeight.Bold,
                            color = topBarTextColor,
                            fontSize = 18.sp
                        )
                        Text(
                            text = if (selectedTab == 0) {
                                if (files.isNotEmpty()) "${files.size} active shared file(s)" else "Zero-Friction LAN Share"
                            } else {
                                if (texts.isNotEmpty()) "${texts.size} shared clipboard snippet(s)" else "Instant LAN Clipboard"
                            },
                            fontSize = 12.sp,
                            color = topBarTextColor.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = topBarTextColor
                        )
                    }
                },
                actions = {
                    // Clipboard tab: Clear All button
                    if (selectedTab == 1 && texts.isNotEmpty()) {
                        IconButton(onClick = { showClearAllSnippetsDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Clear All Snippets",
                                tint = topBarTextColor
                            )
                        }
                    }
                    // Refresh Button
                    IconButton(onClick = { refreshAll() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = topBarTextColor
                        )
                    }
                    // QR Code / Share Dialog Button
                    IconButton(onClick = { showQrDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.QrCode,
                            contentDescription = "Scan to Open WebDrop",
                            tint = topBarTextColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarColor)
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!isUploading) {
                            filePickerLauncher.launch("*/*")
                        }
                    },
                    containerColor = Color(0xFF6741A8),
                    contentColor = Color.White,
                    icon = {
                        if (isUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Add, contentDescription = "Drop Files")
                        }
                    },
                    text = {
                        Text(if (isUploading) "Dropping..." else "Drop Files 🚀", fontWeight = FontWeight.Bold)
                    }
                )
            } else {
                ExtendedFloatingActionButton(
                    onClick = { quickPasteAndDrop() },
                    containerColor = Color(0xFF6741A8),
                    contentColor = Color.White,
                    icon = {
                        Icon(Icons.Filled.ContentPaste, contentDescription = "Paste & Drop")
                    },
                    text = {
                        Text("Paste & Drop 📋", fontWeight = FontWeight.Bold)
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Segmented Pill Navigation Bar (Files vs LAN Clipboard)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDark) Color(0xFF1E1F2B) else Color(0xFFE9EAEF))
                    .padding(4.dp)
            ) {
                // Tab 0: Files
                val filesSelected = selectedTab == 0
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (filesSelected) Color(0xFF6741A8) else Color.Transparent)
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            tint = if (filesSelected) Color.White else (if (isDark) Color.LightGray else Color.DarkGray),
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Shared Files (${files.size})",
                            fontSize = 13.sp,
                            fontWeight = if (filesSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (filesSelected) Color.White else (if (isDark) Color.LightGray else Color.DarkGray)
                        )
                    }
                }

                // Tab 1: Clipboard
                val clipSelected = selectedTab == 1
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (clipSelected) Color(0xFF6741A8) else Color.Transparent)
                        .clickable {
                            selectedTab = 1
                            fetchTexts()
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentPaste,
                            contentDescription = null,
                            tint = if (clipSelected) Color.White else (if (isDark) Color.LightGray else Color.DarkGray),
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "LAN Clipboard (${texts.size})",
                            fontSize = 13.sp,
                            fontWeight = if (clipSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (clipSelected) Color.White else (if (isDark) Color.LightGray else Color.DarkGray)
                        )
                    }
                }
            }

            // Tab Content
            if (selectedTab == 0) {
                // ================= TAB 0: FILES =================
                if (isUploading) {
                    Surface(
                        color = Color(0xFF6741A8).copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = uploadStatusText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = { fetchFiles() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (files.isEmpty() && !isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.NearMe,
                                    contentDescription = null,
                                    tint = Color(0xFF6741A8).copy(alpha = 0.4f),
                                    modifier = Modifier.size(80.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No files dropped yet",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.LightGray else Color.DarkGray
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Tap 'Drop Files' below or scan the QR code to drop files from any phone or PC on this Wi-Fi!",
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedButton(
                                    onClick = { showQrDialog = true }
                                ) {
                                    Icon(Icons.Filled.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Show Guest QR Code")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(files, key = { it.name }) { item ->
                                WebDropFileCard(
                                    item = item,
                                    isDark = isDark,
                                    onOpenOrDownload = { downloadFile(item) },
                                    onDelete = { fileToDelete = item }
                                )
                            }
                        }
                    }
                }
            } else {
                // ================= TAB 1: LAN CLIPBOARD =================
                PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = { fetchTexts() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Interactive Input Card
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) Color(0xFF252634) else Color(0xFFF6F7FA)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        text = "Share Text, Link or Password across LAN",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDark) Color.LightGray else Color.DarkGray
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = clipboardInputText,
                                        onValueChange = { clipboardInputText = it },
                                        placeholder = {
                                            Text(
                                                "Type or paste any text, Wi-Fi password, or URL...",
                                                fontSize = 13.sp,
                                                color = Color.Gray
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 84.dp, max = 150.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF6741A8),
                                            unfocusedBorderColor = if (isDark) Color(0xFF3B3C4F) else Color(0xFFD1D5DB)
                                        )
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Quick Paste from Phone's Clipboard
                                        OutlinedButton(
                                            onClick = {
                                                val clip = clipboardManager.primaryClip
                                                if (clip != null && clip.itemCount > 0) {
                                                    val clipText = clip.getItemAt(0).text?.toString() ?: ""
                                                    if (clipText.isNotEmpty()) {
                                                        clipboardInputText = clipText
                                                        Toast.makeText(context, "Pasted from device clipboard! 📋", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ContentPaste,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text("Paste Clipboard", fontSize = 12.sp)
                                        }

                                        // Share Button
                                        Button(
                                            onClick = { postTextSnippet(clipboardInputText) },
                                            enabled = !isPostingText && clipboardInputText.isNotBlank(),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6741A8)),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            if (isPostingText) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    color = Color.White,
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Filled.Send,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text("Drop to LAN", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Section Header
                        if (texts.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Active LAN Snippets (${texts.size})",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color(0xFFA5B4FC) else Color(0xFF4338CA)
                                    )
                                    TextButton(
                                        onClick = { showClearAllSnippetsDialog = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.DeleteSweep,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = Color(0xFFF38BA8)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Clear All", fontSize = 12.sp, color = Color(0xFFF38BA8))
                                    }
                                }
                            }
                        }

                        // 3. Snippets or Empty State
                        if (texts.isEmpty() && !isLoading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ContentPaste,
                                            contentDescription = null,
                                            tint = Color(0xFF6741A8).copy(alpha = 0.35f),
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = "No shared snippets yet",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDark) Color.LightGray else Color.DarkGray
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Paste text or links above to share them instantly with all devices connected to this Wi-Fi network!",
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            items(texts, key = { it.id }) { snippet ->
                                WebDropSnippetCard(
                                    snippet = snippet,
                                    isDark = isDark,
                                    onCopy = { copyTextToDeviceClipboard(snippet.text) },
                                    onOpenUrl = { url ->
                                        val validUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(validUrl)))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onShare = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, snippet.text)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share text"))
                                    },
                                    onDelete = { snippetToDelete = snippet }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Delete File
    fileToDelete?.let { fileItem ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete Dropped File?") },
            text = { Text("Are you sure you want to delete \"${fileItem.name}\" from WebDrop?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteFile(fileItem)
                        fileToDelete = null
                    }
                ) {
                    Text("Delete", color = Color(0xFFF38BA8), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation Dialog for Delete Snippet
    snippetToDelete?.let { snippet ->
        AlertDialog(
            onDismissRequest = { snippetToDelete = null },
            title = { Text("Delete Snippet?") },
            text = { Text("Delete this clipboard snippet from LAN?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteSnippet(snippet)
                        snippetToDelete = null
                    }
                ) {
                    Text("Delete", color = Color(0xFFF38BA8), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { snippetToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation Dialog for Clear All Snippets
    if (showClearAllSnippetsDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllSnippetsDialog = false },
            title = { Text("Clear All Clipboard Snippets?") },
            text = { Text("This will permanently remove all shared text snippets from the LAN clipboard.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearAllSnippets()
                        showClearAllSnippetsDialog = false
                    }
                ) {
                    Text("Clear All", color = Color(0xFFF38BA8), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllSnippetsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // QR Code Dialog
    if (showQrDialog) {
        WebDropDialog(
            baseUrl = settingsManager.getBaseUrl(),
            isDark = isDark,
            onDismiss = { showQrDialog = false }
        )
    }

    // Media Viewer dialogs (Video / Image / Audio)
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

    activeAudioPlayer?.let { audio ->
        AudioPlayerDialog(
            audioUrl = audio.first,
            fileName = audio.second,
            onDismiss = { activeAudioPlayer = null }
        )
    }
}

/**
 * 📋 WebDropSnippetCard - Modern Glassmorphism Card for Shared LAN Clipboard Texts
 */
@Composable
fun WebDropSnippetCard(
    snippet: WebDropTextItem,
    isDark: Boolean,
    onCopy: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val cardBg = if (isDark) Color(0xFF232432) else Color(0xFFF8F9FA)
    val timeFormatted = remember(snippet.timestamp) {
        if (snippet.timestamp > 0) {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            sdf.format(Date(snippet.timestamp))
        } else ""
    }

    // Check if the snippet contains or is a valid URL
    val trimmed = snippet.text.trim()
    val isUrl = remember(trimmed) {
        trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        (trimmed.contains(".") && !trimmed.contains(" ") && trimmed.length > 4)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Sender Badge + Time + Delete Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color(0xFF6741A8).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = Color(0xFF6741A8),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = snippet.sender ?: "Guest",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (isDark) Color.White else Color(0xFF1F2937)
                    )
                    if (timeFormatted.isNotEmpty()) {
                        Text(
                            text = " • $timeFormatted",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete",
                        tint = Color(0xFFF38BA8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Body: Selectable text in background container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDark) Color(0xFF1B1C27) else Color(0xFFEEEEF2))
                    .padding(12.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = snippet.text,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                        fontFamily = if (isUrl) FontFamily.Monospace else FontFamily.Default
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Bottom Actions Bar (Open URL if applicable + Copy + Share)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isUrl) {
                    OutlinedButton(
                        onClick = { onOpenUrl(trimmed) },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Open Link", fontSize = 11.sp)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Share Sheet Button
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Copy Button
                    Button(
                        onClick = onCopy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color(0xFF33354A) else Color(0xFFE5E7EB),
                            contentColor = if (isDark) Color.White else Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Copy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun WebDropFileCard(
    item: WebDropFileItem,
    isDark: Boolean,
    onOpenOrDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val cardBg = if (isDark) Color(0xFF252630) else Color(0xFFF7F7F9)
    val iconInfo = getFileIconInfo(item.name, item.category)
    val timeFormatted = remember(item.uploadedAt) {
        if (item.uploadedAt > 0) {
            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            sdf.format(Date(item.uploadedAt))
        } else ""
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenOrDownload() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(iconInfo.second.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconInfo.first,
                    contentDescription = null,
                    tint = iconInfo.second,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.formattedSize,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                    if (timeFormatted.isNotEmpty()) {
                        Text(text = "•", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            text = timeFormatted,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            // Action Buttons (Download + Delete)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onOpenOrDownload, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Download",
                        tint = Color(0xFF6741A8),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete",
                        tint = Color(0xFFF38BA8),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

fun getFileIconInfo(filename: String, category: String?): Pair<ImageVector, Color> {
    val ext = filename.substringAfterLast(".", "").lowercase()
    return when {
        category == "image" || ext in listOf("jpg", "jpeg", "png", "webp", "gif", "svg") ->
            Pair(Icons.Filled.Image, Color(0xFF4CAF50))
        category == "video" || ext in listOf("mp4", "mkv", "avi", "mov", "webm", "3gp") ->
            Pair(Icons.Filled.Movie, Color(0xFFE91E63))
        category == "audio" || ext in listOf("mp3", "wav", "m4a", "ogg", "aac", "flac") ->
            Pair(Icons.Filled.Audiotrack, Color(0xFFFF9800))
        category == "document" || ext in listOf("pdf", "doc", "docx", "txt", "xlsx", "pptx") ->
            Pair(Icons.Filled.Description, Color(0xFF2196F3))
        category == "archive" || ext in listOf("zip", "rar", "7z", "tar", "gz") ->
            Pair(Icons.Filled.Archive, Color(0xFF9C27B0))
        else ->
            Pair(Icons.Filled.InsertDriveFile, Color(0xFF607D8B))
    }
}
