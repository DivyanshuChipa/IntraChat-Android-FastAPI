package com.example.intra

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
// 📸 NEW IMPORTS: Profile Photo Display ke liye
import androidx.compose.ui.draw.clip
import coil.request.CachePolicy // 👈 Ye zaroori hai
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    username: String,
    typingStatuses: Map<String, Boolean>,
    onChatClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val contactViewModel: ContactViewModel = viewModel()
    val contacts = contactViewModel.contacts

    val isDark = isSystemInDarkTheme()

    val bgColor = if (isDark) Color(0xFF0E0F14) else Color(0xFFFCFCFC)
    val topBarColor = if (isDark) Color(0xFF1A1B22) else Color(0xFF6741A8)

    Scaffold(
        containerColor = bgColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Intra Chats",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor
                ),
                actions = {
                    IconButton(onClick = { contactViewModel.fetchContacts() }) {
                        Icon(Icons.Filled.Refresh, null, tint = Color.White)
                    }

                    // ⚙️ SETTINGS ICON
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, null, tint = Color.White)
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

            // FAMILY GROUP
            item {
                ContactItem(
                    name = "Family Group",
                    subtitle = "Broadcast to everyone",
                    isTyping = false,
                    icon = Icons.Filled.Group,
                    iconTint = Color(0xFF25BB4B),
                    isDark = isDark,
                    profilePhotoUrl = null, // ✅ No photo for group
                    onClick = { onChatClick("Family Group") }
                )
            }

            // ✅ UPDATED: User list mein profilePhoto pass kar rahe hain
            items(contacts) { user ->
                if (user.username != username) {
                    val isUserTyping = typingStatuses[user.username] == true

                    ContactItem(
                        name = user.username,
                        subtitle = "Tap to chat",
                        isTyping = isUserTyping,
                        icon = Icons.Filled.Person,
                        iconTint = Color(0xFFB39DDB),
                        isDark = isDark,
                        profilePhotoUrl = user.profilePhoto, // ✅ PASS THIS
                        onClick = { onChatClick(user.username) }
                    )
                }
            }
        }
    }
}

// ========================================
// ✅ UPDATED: ContactItem with Profile Photo Support
// ========================================
@Composable
fun ContactItem(
    name: String,
    subtitle: String,
    isTyping: Boolean,
    icon: ImageVector,
    iconTint: Color,
    isDark: Boolean,
    profilePhotoUrl: String? = null, // ✅ NEW PARAMETER
    onClick: () -> Unit
) {
    val nameColor = if (isDark) Color.White else Color.Black
    val subColor = if (isDark) Color.LightGray else Color.DarkGray
    val typingColor = Color(0xFF8741E7)
    val avatarBg = if (isDark) Color(0xFF2A2B33) else Color(0xFFEDE7F6)

    // ✅ Base URL construct karna padega (kyunki server relative path /uploads/... bhejta hai)
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }

    // Full URL: http://192.168.x.x:8000/uploads/profiles/user.png
    val fullPhotoUrl = if (profilePhotoUrl != null) {
        settingsManager.getBaseUrl().removeSuffix("/") + profilePhotoUrl
    } else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // ✅ AVATAR: Agar Photo hai to wo dikhao, nahi to Icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(avatarBg, CircleShape)
                .clip(CircleShape), // ✅ Clip image to circle
            contentAlignment = Alignment.Center
        ) {
            if (fullPhotoUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(fullPhotoUrl)
                        .crossfade(true)
                        // 🔥 FIX: Disk Cache band, taaki purani photo na chipki rahe
                        .diskCachePolicy(CachePolicy.DISABLED)
                        // Memory Cache chalu rakho taaki scroll smooth rahe
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // ✅ Photo nahi hai toh Icon dikhao
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }
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
                    color = typingColor,
                    fontStyle = FontStyle.Italic
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