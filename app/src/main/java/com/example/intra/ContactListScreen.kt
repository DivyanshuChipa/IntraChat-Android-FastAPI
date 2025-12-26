package com.example.intra

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    username: String,
    // 🔥 NEW: Pass the live typing map
    typingStatuses: Map<String, Boolean>,
    onChatClick: (String) -> Unit,
    onLogout: () -> Unit
) {
    val contactViewModel: ContactViewModel = viewModel()
    val contacts = contactViewModel.contacts

    val isDark = isSystemInDarkTheme()

    // 🎨 CONTROLLED COLORS
    val bgColor = if (isDark) Color(0xFF0E0F14) else Color(0xFFFCFCFC)
    val topBarColor = if (isDark) Color(0xFF1A1B22) else Color(0xFF6741A8)
    val titleColor = Color.White
    val dividerColor = if (isDark) Color(0xFF2A2B33) else Color(0xFFE0E0E0)

    Scaffold(
        containerColor = bgColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Intra Chats",
                        color = titleColor,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor
                ),
                actions = {
                    IconButton(onClick = { contactViewModel.fetchContacts() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Filled.ExitToApp, contentDescription = null, tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            // 🔥 FAMILY GROUP
            item {
                ContactItem(
                    name = "Family Group",
                    // Group typing logic is complex, keeping static for now
                    subtitle = "Broadcast to everyone",
                    isTyping = false,
                    icon = Icons.Filled.Group,
                    iconTint = Color(0xFF25BB4B),
                    isDark = isDark,
                    onClick = { onChatClick("Family Group") }
                )
                Divider(color = dividerColor, thickness = 0.6.dp)
            }

            // 👤 USERS
            items(contacts) { user ->
                if (user.username != username) {

                    // 🔥 Check if this specific user is typing
                    val isUserTyping = typingStatuses[user.username] == true

                    ContactItem(
                        name = user.username,
                        subtitle = "Tap to chat", // Future: Replace with Last Message
                        isTyping = isUserTyping,  // Pass status
                        icon = Icons.Filled.Person,
                        iconTint = if (isDark) Color(0xFFB39DDB) else Color(0xFF5C6BC0),
                        isDark = isDark,
                        onClick = { onChatClick(user.username) }
                    )
                }
            }
        }
    }
}

@Composable
fun ContactItem(
    name: String,
    subtitle: String,
    isTyping: Boolean, // 🔥 New param
    icon: ImageVector,
    iconTint: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val nameColor = if (isDark) Color.White else Color.Black
    val subColor = if (isDark) Color.LightGray else Color.DarkGray

    // WhatsApp Green for typing
    val typingColor = Color(0xFF8741E7)

    val avatarBg = if (isDark) Color(0xFF2A2B33) else Color(0xFFEDE7F6)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // Avatar
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(avatarBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(Modifier.width(16.dp))

        Column {
            Text(
                text = name,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = nameColor
            )

            // 🔥 Logic: Show "typing..." OR Subtitle
            if (isTyping) {
                Text(
                    text = "typing...",
                    fontSize = 13.sp,
                    color = typingColor, // Green
                    fontStyle = FontStyle.Italic // Italic style
                )
            } else {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = subColor
                )
            }
        }
    }
}