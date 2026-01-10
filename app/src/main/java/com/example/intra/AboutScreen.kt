package com.example.intra

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About Intra") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Logo / Name
            Text(
                text = "INTRA",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "v1.0.0 (Beta)",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(Modifier.height(32.dp))

            // Information Section
            InfoSection(title = "What is Intra?", body = "Intra is a secure, local network messaging and calling app. It works without internet, keeping your data within your WiFi network.")

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            InfoSection(title = "Privacy", body = "Your messages and calls never leave your local network. No cloud, no tracking.")

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            InfoSection(title = "Credits", body = "Developed for learning and secure communication.\nUsing Jetpack Compose & Python FastAPI.")

            Spacer(Modifier.weight(1f))

            Text("Made with ❤️ for LAN", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun InfoSection(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}