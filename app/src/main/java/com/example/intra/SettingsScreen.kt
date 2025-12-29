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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel = viewModel(), // ViewModel yahan le liya
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
    // HACK: Abhi hum ContactViewModel se photo nahi la rahe settings me,
    // real app me hum current user ka photo bhi fetch kar sakte hain.
    // Filhal upload ke baad updated dikhayenge.
    var uploadedPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // 📸 IMAGE PICKER LAUNCHER
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Convert URI to File using MainActivity helper logic
            val mainActivity = context as? MainActivity
            val file = mainActivity?.uriToTempFile(context, it)

            if (file != null) {
                authViewModel.uploadProfilePhoto(file) { success ->
                    if (success) {
                        uploadedPhotoUri = it // Update UI immediately
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
            horizontalAlignment = Alignment.CenterHorizontally // Center everything
        ) {

            // 📸 PROFILE PICTURE SECTION
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier
                    .size(100.dp)
                    .clickable { photoPickerLauncher.launch("image/*") }
            ) {
                // 1. The Image (Local URI if uploaded recently, else Placeholder)
                // Note: Real refresh ke liye humein User object chahiye hoga
                if (uploadedPhotoUri != null) {
                    AsyncImage(
                        model = uploadedPhotoUri,
                        contentDescription = "Profile",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Default Icon
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.LightGray, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, null, modifier = Modifier.size(50.dp), tint = Color.White)
                    }
                }

                // 2. Camera Icon Overlay
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(6.dp)
                ) {
                    Icon(Icons.Filled.CameraAlt, null, tint = Color.White)
                }
            }

            Text(
                text = "Change Profile Photo",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable { photoPickerLauncher.launch("image/*") }
            )

            Text(text = "@$username", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 24.dp))

            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            // --- OLD SETTINGS (IP/Port) ---

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
            Spacer(modifier = Modifier.height(8.dp))
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
                Text("✅ Saved!", color = Color(0xFF25BB4B))
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
                text = { Text("Confirm logout?") },
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