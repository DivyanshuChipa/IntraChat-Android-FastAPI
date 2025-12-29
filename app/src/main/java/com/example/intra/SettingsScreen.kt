package com.example.intra

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel = viewModel(),
    onLogoutConfirmed: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }

    // States
    var showLogoutDialog by remember { mutableStateOf(false) }
    var ipInput by remember { mutableStateOf(settingsManager.getServerIp()) }
    var portInput by remember { mutableStateOf(settingsManager.getServerPort()) }
    var showSaveConfirm by remember { mutableStateOf(false) }

    // Current User Info
    val username = settingsManager.getUsername() ?: "User"

    // ✅ NEW STATE: Saved Photo URL from SettingsManager
    // App restart karne par ye value persist karegi
    var savedPhotoUrl by remember { mutableStateOf(settingsManager.getMyPhoto()) }

    // Temp URI for immediate preview after upload
    var uploadedPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // 📸 IMAGE PICKER LAUNCHER
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val mainActivity = context as? MainActivity
            val file = mainActivity?.uriToTempFile(context, it)

            if (file != null) {
                authViewModel.uploadProfilePhoto(file) { success ->
                    if (success) {
                        uploadedPhotoUri = it // Immediate local preview
                        // ✅ Refresh saved URL from SettingsManager after upload
                        savedPhotoUrl = settingsManager.getMyPhoto()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // 📸 PROFILE PICTURE SECTION
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier
                    .size(120.dp) // Thoda size badha diya better look ke liye
                    .clickable { photoPickerLauncher.launch("image/*") }
            ) {
                // LOGIC: Priority 1: Just Uploaded (Local) -> Priority 2: Saved (Server) -> Priority 3: Default

                if (uploadedPhotoUri != null) {
                    // 1. Agar abhi taaza upload kiya hai to local file dikhao
                    AsyncImage(
                        model = uploadedPhotoUri,
                        contentDescription = "Profile",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else if (savedPhotoUrl != null) {
                    // 2. Agar pehle se saved hai to Server wali photo dikhao
                    // Full URL construct karna padega: http://ip:port/uploads/...
                    val fullUrl = settingsManager.getBaseUrl().removeSuffix("/") + savedPhotoUrl
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(fullUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Profile",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // 3. Kuch nahi hai to Gray Default Icon
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.LightGray, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, null, modifier = Modifier.size(60.dp), tint = Color.White)
                    }
                }

                // Camera Icon Overlay
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(8.dp)
                ) {
                    Icon(Icons.Filled.CameraAlt, null, tint = Color.White)
                }
            }

            Text(
                text = "Change Profile Photo",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clickable { photoPickerLauncher.launch("image/*") }
            )

            Text(
                text = "@$username",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )

            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            // --- OLD SETTINGS (IP/Port) ---

            Text("Connection Settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = ipInput,
                onValueChange = { ipInput = it },
                label = { Text("Server IP") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = portInput,
                onValueChange = { portInput = it },
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    settingsManager.saveServerConfig(ipInput, portInput)
                    showSaveConfirm = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Update Connection")
            }
            if (showSaveConfirm) {
                Text("✅ Connection settings saved!", color = Color(0xFF25BB4B), modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // 🔴 LOGOUT
            Button(
                onClick = { showLogoutDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Logout", color = Color.White)
            }
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Logout") },
                text = { Text("Are you sure you want to logout?") },
                confirmButton = {
                    TextButton(onClick = { showLogoutDialog = false; onLogoutConfirmed() }) { Text("Yes", color = Color.Red) }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) { Text("No") }
                }
            )
        }
    }
}