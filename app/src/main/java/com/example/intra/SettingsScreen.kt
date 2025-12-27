package com.example.intra

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogoutConfirmed: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }

    var showLogoutDialog by remember { mutableStateOf(false) }

    // IP/Port State
    var ipInput by remember { mutableStateOf(settingsManager.getServerIp()) }
    var portInput by remember { mutableStateOf(settingsManager.getServerPort()) }
    var showSaveConfirm by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Text("Connection Settings", style = MaterialTheme.typography.titleMedium)

            // Server IP Field
            OutlinedTextField(
                value = ipInput,
                onValueChange = { ipInput = it },
                label = { Text("Server IP") },
                modifier = Modifier.fillMaxWidth()
            )

            // Server Port Field
            OutlinedTextField(
                value = portInput,
                onValueChange = { portInput = it },
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth()
            )

            // Save Config Button
            Button(
                onClick = {
                    settingsManager.saveServerConfig(ipInput, portInput)
                    showSaveConfirm = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6741A8))
            ) {
                Text("Update Connection")
            }

            if (showSaveConfirm) {
                Text("✅ Settings Saved! Reopen chat to apply.", color = Color(0xFF25BB4B))
            }

            Divider()

            // 🔴 LOGOUT BUTTON
            Button(
                onClick = { showLogoutDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Logout", color = Color.White)
            }
        }

        // Logout Confirmation Dialog (Same as before)
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Logout") },
                text = { Text("Do you really want to logout?") },
                confirmButton = {
                    TextButton(onClick = {
                        showLogoutDialog = false
                        onLogoutConfirmed()
                    }) { Text("Yes", color = Color.Red) }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) { Text("No") }
                }
            )
        }
    }
}