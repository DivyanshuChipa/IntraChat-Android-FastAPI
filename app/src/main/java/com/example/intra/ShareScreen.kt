package com.example.intra

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.foundation.isSystemInDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    imageUri: Uri,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share Photo") },
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
                    Text("Send Photo", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Photo Preview
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(12.dp),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
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