package com.example.intra

import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.foundation.isSystemInDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    sharedUri: Uri? = null,
    sharedText: String? = null,
    mimeType: String? = null,
    onBack: () -> Unit,
    onSend: (String) -> Unit
) {
    val contactViewModel: ContactViewModel = viewModel()
    val contacts = contactViewModel.contacts
    var selectedRecipient by remember { mutableStateOf<String?>(null) }

    val isDark = isSystemInDarkTheme()
    val colorScheme = MaterialTheme.colorScheme

    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val myUsername = remember { settingsManager.getUsername() ?: "" }

    // Get filename if URI is present
    val fileName = remember(sharedUri) {
        if (sharedUri != null) {
            var name: String? = null
            context.contentResolver.query(sharedUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) name = cursor.getString(index)
                }
            }
            name ?: "Unknown File"
        } else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share Content") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) colorScheme.primaryContainer else Color(0xFF6741A8),
                    titleContentColor = if (isDark) colorScheme.onPrimaryContainer else Color.White,
                    navigationIconContentColor = if (isDark) colorScheme.onPrimaryContainer else Color.White
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = { selectedRecipient?.let { onSend(it) } },
                    enabled = selectedRecipient != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) colorScheme.primary else Color(0xFF673AB7)
                    )
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Send Now", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Preview Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(12.dp),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isDark) Color(0xFF2A2B33) else Color(0xFFF5F5F5)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        // Image Preview
                        mimeType?.startsWith("image/") == true -> {
                            AsyncImage(
                                model = sharedUri,
                                contentDescription = "Image Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        // Video Preview (Icon + Name)
                        mimeType?.startsWith("video/") == true -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = if (isDark) Color.LightGray else Color.Gray
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    fileName ?: "Video",
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        // Text / Link Preview
                        sharedText != null -> {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = Color(0xFF673AB7)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    sharedText,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        // Document / Generic File
                        else -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = if (isDark) Color.LightGray else Color.Gray
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    fileName ?: "Document",
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Text(
                "Select Recipient",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = if (isDark) colorScheme.onSurface else Color.Black
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                // FAMILY GROUP
                item {
                    ContactItem(
                        name = "Family Group",
                        subtitle = "Broadcast to everyone",
                        isTyping = false,
                        icon = Icons.Filled.Group,
                        iconTint = Color(0xFF25BB4B),
                        isDark = isDark,
                        profilePhotoUrl = null,
                        unreadCount = 0,
                        isActive = selectedRecipient == "Family Group",
                        onClick = { selectedRecipient = "Family Group" }
                    )
                }

                // USERS
                items(contacts) { user ->
                    if (user.username != myUsername) {
                        ContactItem(
                            name = user.username,
                            subtitle = "Select to share",
                            isTyping = false,
                            icon = Icons.Filled.Person,
                            iconTint = Color(0xFFB39DDB),
                            isDark = isDark,
                            profilePhotoUrl = user.profilePhoto,
                            unreadCount = 0,
                            isActive = selectedRecipient == user.username,
                            onClick = { selectedRecipient = user.username }
                        )
                    }
                }
            }
        }
    }
}