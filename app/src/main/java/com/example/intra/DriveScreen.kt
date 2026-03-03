package com.example.intra

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveScreen(
    viewModel: DriveViewModel, // 👈 ViewModel Add kiya
    onBackClick: () -> Unit,
    onUploadClick: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val username = settingsManager.getUsername() ?: "Guest"
    val rootPath = "smb://${settingsManager.getServerIp()}/share_karo/IntraDrive/$username/"

    // Jaise hi screen khule, Samba se files load karna shuru karo
    LaunchedEffect(Unit) {
        viewModel.initializeUserDrive()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Intra Drive 📁", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        // Path dikhane ke liye (e.g. "Images/")
                        val displayPath = viewModel.currentPath.value.substringAfter("IntraDrive/$username/")
                        if (displayPath.isNotEmpty()) {
                            Text(displayPath, fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        // Agar root folder mein nahi hai, toh upar wale folder mein jao
                        if (viewModel.currentPath.value != rootPath && viewModel.currentPath.value.isNotEmpty()) {
                            viewModel.navigateUp()
                        } else {
                            onBackClick() // Agar root mein hai, toh screen band kar do
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onUploadClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Upload File")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 🔄 Loading State
            if (viewModel.isLoading.value) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            // ❌ Error State
            else if (viewModel.errorMessage.value != null) {
                Text(viewModel.errorMessage.value!!, color = Color.Red, modifier = Modifier.align(Alignment.Center))
            }
            // 📂 Empty Folder State
            else if (viewModel.files.isEmpty()) {
                Text("Folder is empty", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
            }
            // 📄 Files List
            else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(viewModel.files) { fileItem ->
                        DriveItem(file = fileItem) {
                            if (fileItem.isDirectory) {
                                // Agar folder hai, toh uske andar ghuso
                                viewModel.loadFilesFromPath(fileItem.path)
                            } else {
                                // TODO: Yahan baad mein file open/download ka logic lagayenge
                            }
                        }
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

@Composable
fun DriveItem(file: DriveFileItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when {
            file.isDirectory -> Icons.Default.Folder
            file.name.lowercase().endsWith(".jpg") || file.name.lowercase().endsWith(".png") -> Icons.Default.Image
            file.name.lowercase().endsWith(".mp4") || file.name.lowercase().endsWith(".mkv") -> Icons.Default.VideoLibrary
            else -> Icons.Default.Description
        }
        
        val iconTint = if (file.isDirectory) Color(0xFFFFC107) else Color(0xFF2196F3)

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(32.dp)
        )

        Spacer(Modifier.width(16.dp))

        Text(
            text = file.name,
            fontSize = 16.sp,
            fontWeight = if (file.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}