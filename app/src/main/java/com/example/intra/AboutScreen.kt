package com.example.intra

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

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
            // 🔥 App Logo
            Image(
                painter = painterResource(id = R.drawable.ic_notificationvector), // apna logo yaha
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
            )

            Spacer(Modifier.height(16.dp))

            // App Name
            Text(
                text = "INTRA",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "v1.0.2 (Beta)",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(Modifier.height(32.dp))

            // Info Sections with Icons
            InfoSection(
                icon = Icons.Default.Info,
                title = "What is Intra?",
                body = "Intra is a secure, local network messaging and calling app. It works without internet, keeping your data within your WiFi network."
            )

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            InfoSection(
                icon = Icons.Default.Lock,
                title = "Privacy",
                body = "Your messages and calls never leave your local network. No cloud, no tracking."
            )

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            InfoSection(
                icon = Icons.Default.Code,
                title = "Credits",
                body = "Developed for learning and secure communication.\nUsing Jetpack Compose & Python FastAPI."
            )

            Spacer(Modifier.height(24.dp))

            // 🔗 GitHub Link
            Text(
                text = "View on GitHub",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/DivyanshuChipa/IntraChat-Android-FastAPI"))
                    context.startActivity(intent)
                }
            )

            Spacer(Modifier.height(32.dp))

            // License Section
            Text(
                text = "MIT License © 2025 DivyanshuChipa",
                color = Color.Gray,
                fontSize = 12.sp
            )

            Spacer(Modifier.weight(1f))

            Text("Made with ❤️ for LAN", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun InfoSection(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}