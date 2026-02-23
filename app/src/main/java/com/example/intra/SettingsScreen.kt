package com.example.intra

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import android.content.Intent
import com.example.intra.util.FileUtils
import coil.request.CachePolicy
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import android.os.Build

// ==========================================
// 🔹 ENUM FOR SECTION MANAGEMENT
// ==========================================
enum class SettingsSection {
    NONE,
    GENERAL,
    CONNECTION,
    ACCOUNT,
    ABOUT
}

// ==========================================
// 🔹 REUSABLE PREMIUM COLLAPSIBLE SECTION
// ==========================================
@Composable
fun CollapsibleSection(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .shadow(4.dp, RoundedCornerShape(20.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(16.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 20.dp)
                ) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                    content()
                }
            }
        }
    }
}

// ==========================================
// 🔹 MAIN SETTINGS SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel = viewModel(),
    onLogoutConfirmed: () -> Unit,
    onBack: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope() // For Shake Animation

    // --- STATES ---
    var ipInput by remember { mutableStateOf(settingsManager.getServerIp()) }
    var portInput by remember { mutableStateOf(settingsManager.getServerPort()) }
    var showSaveConfirm by remember { mutableStateOf(false) }

    // 🔥 BACKGROUND SERVICE STATE
    var isBackgroundEnabled by remember { mutableStateOf(settingsManager.isBackgroundServiceEnabled()) }

    // 🤖 LUMIR STATE
    var isLumirEnabled by remember { mutableStateOf(settingsManager.isShowLumirEnabled()) }

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var savedPhotoUrl by remember { mutableStateOf(settingsManager.getMyPhoto()) }
    var uploadedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val username = settingsManager.getUsername() ?: "User"

    var expandedSection by remember { mutableStateOf(SettingsSection.NONE) }

    // 🔥 SHAKE ANIMATION STATE
    val deleteButtonOffset = remember { Animatable(0f) }

    // 📸 IMAGE PICKER
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val file = FileUtils.uriToTempFile(context, it)
            if (file != null) {
                authViewModel.uploadProfilePhoto(file) { success ->
                    if (success) {
                        uploadedPhotoUri = it
                        savedPhotoUrl = settingsManager.getMyPhoto()
                    }
                }
            }
        }
    }

    // Set Status Bar Color
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()
    val backgroundColor = MaterialTheme.colorScheme.background
    val primaryDarkColor = Color(0xFF512DA8) // Dark Purple (Aapka theme color)

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // ✅ CASE 1: Modern Android (6.0+)
                // Sab kuch waisa hi chalega jaisa abhi F62 me chal raha hai
                window.statusBarColor = backgroundColor.toArgb()
                insetsController.isAppearanceLightStatusBars = !isDark
            } else {
                // ⚠️ CASE 2: Old Android (Lollipop 5.0/5.1)
                // Yahan hum icons Black nahi kar sakte.
                // Isliye agar User Light mode mein hai, toh Status Bar ko Dark Color de do
                // taaki White icons saaf dikhein.

                if (!isDark) {
                    // Light Mode me bhi Status bar Dark rakho (Black ya Dark Purple)
                    window.statusBarColor = Color.Black.toArgb() // Ya primaryDarkColor.toArgb() use kar sakte ho
                } else {
                    // Dark mode me toh waise hi Dark hai
                    window.statusBarColor = backgroundColor.toArgb()
                }
                // Lollipop pe ye flag kaam nahi karta, isliye ise ignore karo
                // insetsController.isAppearanceLightStatusBars = false
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ==========================================
            // 1. PROFILE PHOTO (FIXED & POLISHED) ✅
            // ==========================================
            Box(
                contentAlignment = Alignment.BottomEnd, // Align Icon to bottom-right
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 24.dp)
                    .size(130.dp) // Total size area
                    .clickable { photoPickerLauncher.launch("image/*") }
            ) {
                // 1. The Main Image (Clipped inside Circle)
                Box(
                    modifier = Modifier
                        .matchParentSize() // Fill the 130dp
                        .padding(4.dp) // Little gap for border effect
                        .clip(CircleShape) // Clip ONLY the image
                        .border(4.dp, MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                ) {
                    if (uploadedPhotoUri != null) {
                        AsyncImage(
                            model = uploadedPhotoUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (savedPhotoUrl != null) {
                        val fullUrl = settingsManager.getBaseUrl().removeSuffix("/") + savedPhotoUrl
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(fullUrl).crossfade(true).build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(listOf(Color(0xFF6A11CB), Color(0xFF2575FC)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = username.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 2. The Floating Camera Icon (OUTSIDE the clip) ✅
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        // No offset needed if alignment is BottomEnd, but let's nudge it slightly
                        .offset(x = 0.dp, y = 0.dp)
                        .shadow(4.dp, CircleShape)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Text("@$username", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Tap photo to change", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(30.dp))

            // ==========================================
            // 🔥 NEW: GENERAL SETTINGS (Background Service)
            // ==========================================
            CollapsibleSection(
                title = "General",
                icon = Icons.Default.Settings,
                iconColor = Color(0xFF4CAF50), // 🟢 Green
                isExpanded = expandedSection == SettingsSection.GENERAL, // Default open rakh sakte ho ya toggle
                onToggle = { expandedSection = if (expandedSection == SettingsSection.GENERAL) SettingsSection.NONE else SettingsSection.GENERAL
                    // Logic thoda adjust kar lena enum ke hisab se
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Keep Intra Running", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Receive calls & messages even when app is closed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isBackgroundEnabled,
                        onCheckedChange = { enabled ->
                            isBackgroundEnabled = enabled
                            settingsManager.setBackgroundService(enabled)

                            val serviceIntent = Intent(context, IntraBackgroundService::class.java)
                            if (enabled) {
                                // Start Service
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    context.startForegroundService(serviceIntent)
                                } else {
                                    context.startService(serviceIntent)
                                }
                            } else {
                                // Stop Service
                                context.stopService(serviceIntent)
                            }
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // 🤖 LUMIR TOGGLE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show Lumir AI", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Enable the AI assistant in your chat list.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isLumirEnabled,
                        onCheckedChange = { enabled ->
                            isLumirEnabled = enabled
                            settingsManager.setShowLumir(enabled)
                        }
                    )
                }
            }

            Spacer(Modifier.height(0.dp))

            // ==========================================
            // 2. CONNECTION
            // ==========================================
            CollapsibleSection(
                title = "Connection",
                icon = Icons.Default.Wifi,
                iconColor = Color(0xFF2196F3),
                isExpanded = expandedSection == SettingsSection.CONNECTION,
                onToggle = { expandedSection = if (expandedSection == SettingsSection.CONNECTION) SettingsSection.NONE else SettingsSection.CONNECTION }
            ) {
                OutlinedTextField(value = ipInput, onValueChange = { ipInput = it }, label = { Text("Server IP") }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = portInput, onValueChange = { portInput = it }, label = { Text("Port") }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Button(onClick = { settingsManager.saveServerConfig(ipInput, portInput); showSaveConfirm = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text("Save Configuration")
                }
                AnimatedVisibility(showSaveConfirm) { Text("✅ Connection updated successfully", color = Color(0xFF4CAF50), modifier = Modifier.padding(top = 12.dp)) }
            }

            // ==========================================
            // 3. ACCOUNT (WITH SHAKE ANIMATION) 🔥
            // ==========================================
            CollapsibleSection(
                title = "Account",
                icon = Icons.Default.ManageAccounts,
                iconColor = Color(0xFFFF9800),
                isExpanded = expandedSection == SettingsSection.ACCOUNT,
                onToggle = { expandedSection = if (expandedSection == SettingsSection.ACCOUNT) SettingsSection.NONE else SettingsSection.ACCOUNT }
            ) {
                Button(
                    onClick = { showLogoutDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Logout", color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(12.dp))

                // 🔥 BUTTON WITH SHAKE
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            // Shake Animation Logic (Left -> Right -> Center)
                            deleteButtonOffset.animateTo(-10f, animationSpec = tween(50))
                            deleteButtonOffset.animateTo(10f, animationSpec = tween(50))
                            deleteButtonOffset.animateTo(-5f, animationSpec = tween(50))
                            deleteButtonOffset.animateTo(5f, animationSpec = tween(50))
                            deleteButtonOffset.animateTo(0f, animationSpec = tween(50))
                            // Open Dialog after shake
                            authViewModel.passwordInput.value = ""
                            authViewModel.clearError()
                            showDeleteDialog = true
                        }
                    },
                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(x = deleteButtonOffset.value.dp) // 👈 Applying the Shake
                ) {
                    Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Account Permanently")
                }
            }

            // ==========================================
            // 4. ABOUT
            // ==========================================
            CollapsibleSection(
                title = "About Intra",
                icon = Icons.Default.Info,
                iconColor = Color(0xFF9C27B0),
                isExpanded = expandedSection == SettingsSection.ABOUT,
                onToggle = { expandedSection = if (expandedSection == SettingsSection.ABOUT) SettingsSection.NONE else SettingsSection.ABOUT }
            ) {
                Text("Intra is a secure LAN-based communication tool.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onNavigateToAbout, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = RoundedCornerShape(12.dp)) {
                    Text("View App Info & Licenses", color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }

        // ==========================================
        // DIALOGS
        // ==========================================
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                icon = { Icon(Icons.Default.Logout, null) },
                title = { Text("Logout") },
                text = { Text("Are you sure you want to sign out?") },
                confirmButton = { TextButton(onClick = { showLogoutDialog = false; onLogoutConfirmed() }) { Text("Yes, Logout", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") } }
            )
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                icon = { Icon(Icons.Default.Warning, null, tint = Color.Red) },
                title = { Text("Delete Account", color = Color.Red) },
                text = {
                    Column {
                        Text("This action is irreversible. Enter password to confirm:")
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = authViewModel.passwordInput.value,
                            onValueChange = { authViewModel.passwordInput.value = it },
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            isError = authViewModel.errorMessage.value != null,
                            shape = RoundedCornerShape(12.dp)
                        )
                        authViewModel.errorMessage.value?.let { Text(it, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
                    }
                },
                confirmButton = {
                    Button(onClick = { authViewModel.deleteAccount { showDeleteDialog = false; onLogoutConfirmed() } }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red), shape = RoundedCornerShape(20.dp)) {
                        if (authViewModel.isLoading.value) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp)) else Text("DELETE ACCOUNT")
                    }
                },
                dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
            )
        }
    }
}
