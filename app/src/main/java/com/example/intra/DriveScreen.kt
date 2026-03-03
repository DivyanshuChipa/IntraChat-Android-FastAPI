package com.example.intra

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveScreen(
    onBackClick: () -> Unit,
    onUploadClick: () -> Unit
) {
    // Dummy Data (Baad me isko ViewModel aur Samba se replace karenge)
    val files = listOf(
        "Images" to true,       // true means Folder
        "Videos" to true,
        "Documents" to true,
        "project_report.pdf" to false, // false means File
        "IMG_2026.jpg" to false
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Intra Drive 📁", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(files) { (name, isFolder) ->
                    DriveItem(name = name, isFolder = isFolder) {
                        // Folder click logic yahan aayega
                    }
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@Composable
fun DriveItem(name: String, isFolder: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon Logic
        val icon = when {
            isFolder -> Icons.Default.Folder
            name.lowercase().endsWith(".jpg") || name.lowercase().endsWith(".png") -> Icons.Default.Image
            name.lowercase().endsWith(".mp4") -> Icons.Default.VideoLibrary
            else -> Icons.Default.Description
        }
        
        val iconTint = if (isFolder) Color(0xFFFFC107) else Color(0xFF2196F3) // Yellow for folder, Blue for files

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(32.dp)
        )

        Spacer(Modifier.width(16.dp))

        Text(
            text = name,
            fontSize = 16.sp,
            fontWeight = if (isFolder) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
