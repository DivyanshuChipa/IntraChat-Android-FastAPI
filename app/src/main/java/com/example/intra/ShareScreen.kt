package com.example.intra

import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    sharedUris: List<Uri> = emptyList(),
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

    val primaryGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF673AB7), Color(0xFF9575CD))
    )

    // Get filenames for URIs
    val filesInfo = remember(sharedUris) {
        sharedUris.map { uri ->
            var name: String? = null
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) name = cursor.getString(index)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            uri to (name ?: "Unknown File")
        }
    }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.background(primaryGradient)) {
                TopAppBar(
                    title = {
                        Text(
                            if (sharedText != null) "Share Text" else "Share Files (${sharedUris.size})",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = { selectedRecipient?.let { onSend(it) } },
                        enabled = selectedRecipient != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = if (isDark) Color.DarkGray else Color.LightGray
                        ),
                        contentPadding = PaddingValues()
                    ) {
                        val buttonBrush = if (selectedRecipient != null) primaryGradient
                                          else Brush.linearGradient(listOf(Color.Gray, Color.Gray))

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(buttonBrush),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Send to ${selectedRecipient ?: "..."}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(if (isDark) Color(0xFF121212) else Color(0xFFF8F9FA))
        ) {
            // Preview Section
            if (sharedText != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF1E1E1E) else Color.White
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF673AB7))
                            Spacer(Modifier.width(8.dp))
                            Text("Text Content", fontWeight = FontWeight.SemiBold, color = Color(0xFF673AB7))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            sharedText,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isDark) Color.LightGray else Color.DarkGray
                        )
                    }
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filesInfo) { (uri, name) ->
                        FilePreviewItem(uri, name, mimeType, isDark)
                    }
                }
            }

            Text(
                "Select Recipient",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = if (isDark) Color.White else Color.Black
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
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

@Composable
fun FilePreviewItem(uri: Uri, name: String, mimeType: String?, isDark: Boolean) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .height(200.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF252525) else Color.White
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(if (isDark) Color(0xFF333333) else Color(0xFFF1F1F1)),
                contentAlignment = Alignment.Center
            ) {
                val currentMime = if (name.lowercase().endsWith(".jpg") || name.lowercase().endsWith(".png")) "image/"
                                 else if (name.lowercase().endsWith(".mp4")) "video/"
                                 else mimeType

                when {
                    currentMime?.startsWith("image/") == true -> {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    currentMime?.startsWith("video/") == true -> {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    else -> {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = if (isDark) Color.Gray else Color.LightGray
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Color.LightGray else Color.DarkGray
                )
            }
        }
    }
}
