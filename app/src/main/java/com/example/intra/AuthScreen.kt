package com.example.intra

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Activity
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AuthScreen(
    viewModel: AuthViewModel = viewModel(),
    onAuthenticated: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }

    if (viewModel.isAuthenticated.value) {
        onAuthenticated()
    }

    val username = viewModel.usernameInput.value
    val password = viewModel.passwordInput.value
    val errorMsg = viewModel.errorMessage.value
    val loading = viewModel.isLoading.value

    var isLogin by remember { mutableStateOf(true) }
    var passwordVisible by remember { mutableStateOf(false) }

    // ⚙️ ADVANCED OPTIONS & DISCOVERY STATE
    var showAdvanced by remember { mutableStateOf(false) }
    var ipInput by remember { mutableStateOf(settingsManager.getServerIp()) }
    var portInput by remember { mutableStateOf(settingsManager.getServerPort()) }

    val coroutineScope = rememberCoroutineScope()
    var isScanning by remember { mutableStateOf(false) }
    var isServerOnline by remember { mutableStateOf<Boolean?>(null) }

    fun triggerAutoScan() {
        if (isScanning) return
        isScanning = true
        coroutineScope.launch {
            val port = portInput.toIntOrNull() ?: 8000
            val foundIp = ServerDiscoveryManager.scanForServer(
                context = context,
                port = port,
                currentSavedIp = ipInput
            )
            if (foundIp != null) {
                ipInput = foundIp
                settingsManager.saveServerConfig(foundIp, portInput)
                isServerOnline = true
            } else {
                isServerOnline = false
            }
            isScanning = false
        }
    }

    LaunchedEffect(Unit) {
        triggerAutoScan()
    }

    val isDark = isSystemInDarkTheme()
    val neonPurple = Color(0xFF7A00FF)
    val cardColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFAF7FF)

    val bgGradient = if (isDark) {
        listOf(Color(0xFF0F001F), Color(0xFF1A0033))
    } else {
        listOf(Color(0xFF6A00FF), Color(0xFF9A4DFF))
    }

    // --- STATUS BAR LOGIC (Same as SettingsScreen) ---
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.statusBarColor = bgGradient.first().toArgb()
                insetsController.isAppearanceLightStatusBars = false // Because gradient is dark
            } else {
                if (!isDark) {
                    window.statusBarColor = Color.Black.toArgb()
                } else {
                    window.statusBarColor = bgGradient.first().toArgb()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = bgGradient)),
        contentAlignment = Alignment.Center
    ) {

        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {

            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(text = "Intra", fontSize = 38.sp, color = neonPurple, fontWeight = FontWeight.ExtraBold)
                Text(text = "LAN Messenger", fontSize = 16.sp, color = Color.Gray, fontWeight = FontWeight.Medium)

                Spacer(Modifier.height(24.dp))

                // USERNAME & PASSWORD FIELDS (Standard)
                OutlinedTextField(
                    value = username,
                    onValueChange = { viewModel.usernameInput.value = it },
                    label = { Text("Username") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = neonPurple,
                        focusedLabelColor = neonPurple
                    )
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { viewModel.passwordInput.value = it },
                    label = { Text("Password") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, null, tint = neonPurple)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = neonPurple,
                        focusedLabelColor = neonPurple
                    )
                )

                Spacer(Modifier.height(16.dp))

                // 🌐 SERVER STATUS & AUTO-DETECT CARD
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = !isScanning) { triggerAutoScan() },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) {
                            when {
                                isScanning -> Color(0xFF2C240A)
                                isServerOnline == true -> Color(0xFF0D2818)
                                else -> Color(0xFF2B1214)
                            }
                        } else {
                            when {
                                isScanning -> Color(0xFFFFF8E1)
                                isServerOnline == true -> Color(0xFFE8F5E9)
                                else -> Color(0xFFFFEBEE)
                            }
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        when {
                            isScanning -> Color(0xFFFFB300).copy(alpha = 0.7f)
                            isServerOnline == true -> Color(0xFF4CAF50).copy(alpha = 0.7f)
                            else -> Color(0xFFE57373).copy(alpha = 0.7f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFFFB300)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(
                                            if (isServerOnline == true) Color(0xFF4CAF50) else Color(0xFFE53935),
                                            CircleShape
                                        )
                                        .clip(CircleShape)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = when {
                                        isScanning -> "Scanning Local Wi-Fi..."
                                        isServerOnline == true -> "Server Connected"
                                        else -> "Server Not Detected"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isScanning -> Color(0xFFFFB300)
                                        isServerOnline == true -> Color(0xFF4CAF50)
                                        else -> Color(0xFFE53935)
                                    }
                                )
                                Text(
                                    text = when {
                                        isScanning -> "Searching Intra on subnet..."
                                        isServerOnline == true -> "$ipInput:$portInput"
                                        else -> "Tap to scan or set in Advanced Options"
                                    },
                                    fontSize = 11.sp,
                                    color = if (isDark) Color.LightGray else Color.DarkGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = { triggerAutoScan() },
                            enabled = !isScanning,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Scan Wi-Fi",
                                tint = neonPurple,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ⚙️ ADVANCED OPTIONS TOGGLE
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Advanced Options (Manual IP)",
                        color = neonPurple,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = if(showAdvanced) Icons.Filled.ExpandMore else Icons.Filled.Settings,
                        contentDescription = null,
                        tint = neonPurple,
                        modifier = Modifier.size(18.dp).padding(start = 6.dp)
                    )
                }

                // ⚙️ ADVANCED FIELDS (Animated)
                AnimatedVisibility(
                    visible = showAdvanced,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        OutlinedTextField(
                            value = ipInput,
                            onValueChange = {
                                ipInput = it
                                settingsManager.saveServerConfig(ipInput, portInput)
                            },
                            label = { Text("Server IP (e.g. 192.168.1.5)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = neonPurple,
                                focusedLabelColor = neonPurple
                            )
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = portInput,
                            onValueChange = {
                                portInput = it
                                settingsManager.saveServerConfig(ipInput, portInput)
                            },
                            label = { Text("Port (e.g. 8000)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = neonPurple,
                                focusedLabelColor = neonPurple
                            )
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { triggerAutoScan() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                enabled = !isScanning
                            ) {
                                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Auto Scan", fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    settingsManager.saveServerConfig(ipInput, portInput)
                                    coroutineScope.launch {
                                        val port = portInput.toIntOrNull() ?: 8000
                                        isServerOnline = ServerDiscoveryManager.verifyIntraServer(ipInput, port)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = neonPurple)
                            ) {
                                Text("Save & Test", fontSize = 12.sp)
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                // LOGIN BUTTON
                Button(
                    onClick = {
                        if (isLogin) viewModel.login() else viewModel.register()
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    enabled = !loading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = neonPurple)
                ) {
                    if (loading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text(text = if (isLogin) "LOGIN" else "REGISTER", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = if (isLogin) "No account? Register" else "Already have an account? Login",
                    color = neonPurple,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        isLogin = !isLogin
                        viewModel.clearError()
                    }
                )
            }
        }

        // ERROR MESSAGE
        AnimatedVisibility(
            visible = errorMsg != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Text(
                text = errorMsg ?: "",
                color = Color.White,
                modifier = Modifier
                    .padding(16.dp)
                    .background(Color.Red.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .padding(14.dp)
            )
        }
    }
}