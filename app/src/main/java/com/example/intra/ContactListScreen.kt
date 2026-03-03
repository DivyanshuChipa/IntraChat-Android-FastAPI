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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SmartToy
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.draw.clip
import coil.request.CachePolicy
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    username: String,
    typingStatuses: Map<String, Boolean>,
    activeChatUser: String? = null,
    onChatClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onDriveClick: () -> Unit // 📁 NAYA: Drive click handler
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }

    // Refresh Lumir state whenever screen appears
    var showLumir by remember { mutableStateOf(settingsManager.isShowLumirEnabled()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        showLumir = settingsManager.isShowLumirEnabled()
    }

    val contactViewModel: ContactViewModel = viewModel()
    val contacts = contactViewModel.contacts

    val isDark = isSystemInDarkTheme()
    val colorScheme = MaterialTheme.colorScheme

    val topBarColor = if (isDark) colorScheme.primaryContainer else Color(0xFF6741A8)
    val topBarTextColor = if (isDark) colorScheme.onPrimaryContainer else Color.White

    // Set Status Bar Color
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = topBarColor.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false // Always white icons
        }
    }

    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        containerColor = colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Intra Chats",
                        color = topBarTextColor,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor
                ),
                actions = {
                    // 📁 NAYA: Intra Drive Button
                    IconButton(onClick = onDriveClick) {
                        Icon(Icons.Filled.Folder, contentDescription = "Intra Drive", tint = topBarTextColor)
                    }
                    
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, null, tint = topBarTextColor)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search contacts...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = topBarColor,
                    cursorColor = topBarColor
                )
            )

            // Pull to Refresh & List
            // Note: Keeping the original PullToRefreshBox usage if available in the project's dependencies
            // If it causes errors, we might need to check the specific implementation or imports
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = contactViewModel.isRefreshing,
                onRefresh = { contactViewModel.fetchContacts() },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {

                    // FAMILY GROUP (Filtered)
                    if (searchQuery.isBlank() || "Family Group".contains(searchQuery, ignoreCase = true)) {
                        item {
                            ContactItem(
                                name = "Family Group",
                                subtitle = "Broadcast to everyone",
                                isTyping = false,
                                icon = Icons.Filled.Group,
                                iconTint = Color(0xFF25BB4B),
                                isDark = isDark,
                                profilePhotoUrl = null,
                                unreadCount = 0, // Family Group ke liye badge nahi dikhana abhi
                                isActive = activeChatUser == "Family Group", // 🆕 Active detection
                                onClick = { onChatClick("Family Group") }
                            )
                        }
                    }
                    // ✅ STEP 2: LUMIR BOT ITEM (Yahan add karo)
                    if (showLumir) { // 🤖 Check visibility setting
                        item {
                            ContactItem(
                                name = "Lumir",
                                subtitle = "AI Assistant & Utilities",
                                isTyping = false, // Abhi ke liye false
                                icon = Icons.Default.SmartToy, // Default fallback
                                iconTint = Color(0xFF1100FF), // Cyan/Neon Blue color
                                isDark = isDark,
                                profilePhotoUrl = null,
                                unreadCount = 0,
                                isActive = activeChatUser == "Lumir",
                                iconResourceId = R.drawable.lumir5, // 🤖 Use Custom Vector
                                onClick = { onChatClick("Lumir") }
                            )
                        }
                    }

                    // USER LIST (Filtered)
                    val filteredContacts = if (searchQuery.isBlank()) {
                        contacts
                    } else {
                        contacts.filter { it.username.contains(searchQuery, ignoreCase = true) }
                    }

                    items(filteredContacts) { user ->
                        if (user.username != username) {
                            val isUserTyping = typingStatuses[user.username] == true

                            // 🆕 STEP 6: Agar chat open hai toh badge hide karo
                            val displayUnreadCount = if (activeChatUser == user.username) {
                                0 // Chat open hai, badge mat dikhao
                            } else {
                                user.unreadCount // Chat closed hai, actual count dikhao
                            }

                            ContactItem(
                                name = user.username,
                                subtitle = "Tap to chat",
                                isTyping = isUserTyping,
                                icon = Icons.Filled.Person,
                                iconTint = Color(0xFFB39DDB),
                                isDark = isDark,
                                profilePhotoUrl = user.profilePhoto,
                                unreadCount = displayUnreadCount, // 🆕 Updated logic
                                isActive = activeChatUser == user.username, // 🆕 Active detection
                                onClick = { onChatClick(user.username) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactItem(
    name: String,
    subtitle: String,
    isTyping: Boolean,
    icon: ImageVector,
    iconTint: Color,
    isDark: Boolean,
    profilePhotoUrl: String? = null,
    unreadCount: Int = 0,
    isActive: Boolean = false, // 🆕 STEP 6: Active chat indicator
    iconResourceId: Int? = null, // 🆕 NEW: Optional Drawable Resource ID
    onClick: () -> Unit
) {
    val nameColor = if (isDark) Color.White else Color.Black
    val subColor = if (isDark) Color.LightGray else Color.DarkGray
    val typingColor = Color(0xFFD175FF)
    val avatarBg = if (isDark) Color(0xFF2A2B33) else Color(0xFFEDE7F6)

    // 🆕 Active chat ka background color
    val backgroundColor = if (isActive) {
        if (isDark) Color(0xFF1E1E2E) else Color(0xFFE8DEF8)
    } else {
        Color.Transparent
    }

    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }

    val fullPhotoUrl = if (profilePhotoUrl != null) {
        settingsManager.getBaseUrl().removeSuffix("/") + profilePhotoUrl
    } else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor) // 🆕 Active highlight
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // AVATAR
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(avatarBg, CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (fullPhotoUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(fullPhotoUrl)
                        .crossfade(true)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (iconResourceId != null) {
                // 🆕 Render Drawable (Lumir)
                Icon(
                    painter = painterResource(id = iconResourceId),
                    contentDescription = null,
                    tint = Color.Unspecified, // Keep original vector colors
                    modifier = Modifier.fillMaxSize().padding(2.dp) // Slight padding if needed
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        // NAME + TYPING/SUBTITLE
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = nameColor
            )

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

        // 🆕 UNREAD BADGE (Right Side)
        if (unreadCount > 0) {
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = unreadCount.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}