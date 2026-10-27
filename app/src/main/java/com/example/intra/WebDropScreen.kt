package com.example.intra

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

    val isDark = isSystemInDarkTheme()
    val colorScheme = MaterialTheme.colorScheme
    val topBarColor = if (isDark) colorScheme.primaryContainer else Color(0xFF6741A8)
    val topBarTextColor = if (isDark) colorScheme.onPrimaryContainer else Color.White

    // State
    var files by remember { mutableStateOf<List<WebDropFileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadStatusText by remember { mutableStateOf("") }
    var showQrDialog by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<WebDropFileItem?>(null) }
    var activeViewerFile by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // Pair(filePath/URI, isVideo)

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
                } else {
                    Toast.makeText(context, "Failed to load files", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Network error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
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
                        val senderBody = "Android User".toRequestBody("text/plain".toMediaTypeOrNull())

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

        val fullUrl = if (fileItem.url.startsWith("http")) {
            fileItem.url
        } else {
            "${settingsManager.getBaseUrl().removeSuffix("/")}${fileItem.url}"
        }

        if (isImage || isVideo) {
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
        fetchFiles()
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
                            text = if (files.isNotEmpty()) "${files.size} active shared file(s)" else "Zero-Friction LAN Share",
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
                    // 1. Refresh Button
                    IconButton(onClick = { fetchFiles() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = topBarTextColor
                        )
                    }
                    // 2. 📲 QR Code / Share Dialog Button (As requested by user!)
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Upload Banner when uploading
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
                    // Empty state
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
        }
    }

    // Confirmation Dialog for Delete
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

    // 📲 QR Code Dialog (Connected to existing WebDropDialog!)
    if (showQrDialog) {
        WebDropDialog(
            baseUrl = settingsManager.getBaseUrl(),
            isDark = isDark,
            onDismiss = { showQrDialog = false }
        )
    }

    // Media Viewer dialogs
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
