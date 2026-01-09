package com.example.intra

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest

/* =========================================================
   🔽 REUSABLE COLLAPSIBLE SECTION
   ========================================================= */
@Composable
fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {

        /* ---- HEADER ROW ---- */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = if (expanded)
                    Icons.Default.KeyboardArrowUp
                else
                    Icons.Default.KeyboardArrowDown,
                contentDescription = null
            )
        }

        /* ---- EXPAND / COLLAPSE CONTENT ---- */
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                content()
            }
        }
    }
}

/* =========================================================
   ⚙️ SETTINGS SCREEN
   ========================================================= */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel = viewModel(),
    onLogoutConfirmed: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }

    /* ---- UI STATES ---- */
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var showConnection by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }

    var ipInput by remember { mutableStateOf(settingsManager.getServerIp()) }
    var portInput by remember { mutableStateOf(settingsManager.getServerPort()) }

    val username = settingsManager.getUsername() ?: "User"

    /* ---- PROFILE PHOTO STATE ---- */
    var uploadedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var savedPhotoPath by remember { mutableStateOf(settingsManager.getMyPhoto()) }

    /* ---- IMAGE PICKER ---- */
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val activity = context as? MainActivity ?: return@rememberLauncherForActivityResult
        val file = activity.uriToTempFile(context, uri) ?: return@rememberLauncherForActivityResult

        authViewModel.uploadProfilePhoto(file) {
            uploadedPhotoUri = uri
            savedPhotoPath = settingsManager.getMyPhoto()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->

        /* =================================================
           🔽 SCROLLABLE CONTENT (SMALL PHONE SAFE)
           ================================================= */
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            /* =================================================
               📸 PROFILE PHOTO
               ================================================= */
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clickable { photoPicker.launch("image/*") },
                contentAlignment = Alignment.BottomEnd
            ) {
                when {
                    uploadedPhotoUri != null -> {
                        AsyncImage(
                            model = uploadedPhotoUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    savedPhotoPath != null -> {
                        val fullUrl =
                            settingsManager.getBaseUrl().removeSuffix("/") + savedPhotoPath
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(fullUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.LightGray, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(60.dp))
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, null, tint = Color.White)
                }
            }

            Text(
                text = "Change Profile Photo",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clickable { photoPicker.launch("image/*") }
            )

            Text(
                text = "@$username",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(vertical = 24.dp)
            )

            /* =================================================
               🔌 CONNECTION SETTINGS
               ================================================= */
            CollapsibleSection(
                title = "Connection Settings",
                expanded = showConnection,
                onToggle = { showConnection = !showConnection }
            ) {
                OutlinedTextField(
                    value = ipInput,
                    onValueChange = { ipInput = it },
                    label = { Text("Server IP") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = portInput,
                    onValueChange = { portInput = it },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        settingsManager.saveServerConfig(ipInput, portInput)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Update Connection")
                }
            }

            Spacer(Modifier.height(16.dp))

            /* =================================================
               👤 ACCOUNT SETTINGS
               ================================================= */
            CollapsibleSection(
                title = "Account Settings",
                expanded = showAccount,
                onToggle = { showAccount = !showAccount }
            ) {
                OutlinedButton(
                    onClick = {
                        authViewModel.passwordInput.value = ""
                        authViewModel.clearError()
                        showDeleteDialog = true
                    },
                    border = BorderStroke(1.dp, Color.Red),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete Account Permanently")
                }
            }

            Spacer(Modifier.height(24.dp))

            /* =================================================
               🚪 LOGOUT
               ================================================= */
            Button(
                onClick = { showLogoutDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Logout", color = Color.White)
            }
        }

        /* =================================================
           🔴 LOGOUT DIALOG
           ================================================= */
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Logout") },
                text = { Text("Are you sure you want to logout?") },
                confirmButton = {
                    TextButton(onClick = {
                        showLogoutDialog = false
                        onLogoutConfirmed()
                    }) {
                        Text("Yes", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        /* =================================================
           💀 DELETE ACCOUNT DIALOG
           ================================================= */
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = {
                    Text(
                        "Delete Account?",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text("This action cannot be undone.")
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = authViewModel.passwordInput.value,
                            onValueChange = { authViewModel.passwordInput.value = it },
                            label = { Text("Enter Password to Confirm") },
                            visualTransformation = PasswordVisualTransformation()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        onClick = {
                            authViewModel.deleteAccount {
                                showDeleteDialog = false
                                onLogoutConfirmed()
                            }
                        }
                    ) {
                        Text("DELETE")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
