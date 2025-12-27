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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.viewmodel.compose.viewModel

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

    // ⚙️ ADVANCED OPTIONS STATE
    var showAdvanced by remember { mutableStateOf(false) }
    var ipInput by remember { mutableStateOf(settingsManager.getServerIp()) }
    var portInput by remember { mutableStateOf(settingsManager.getServerPort()) }

    val isDark = isSystemInDarkTheme()
    val neonPurple = Color(0xFF7A00FF)
    val cardColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFAF7FF)
    val textColor = if (isDark) Color.White else Color.Black

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (isDark) listOf(Color(0xFF0F001F), Color(0xFF1A0033))
                    else listOf(Color(0xFF6A00FF), Color(0xFF9A4DFF))
                )
            ),
        contentAlignment = Alignment.Center
    ) {

        Card(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor)
        ) {

            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(text = "Intra", fontSize = 36.sp, color = neonPurple, fontWeight = FontWeight.Bold)
                Text(text = "LAN Messenger", fontSize = 16.sp, color = Color.Gray)

                Spacer(Modifier.height(18.dp))

                // USERNAME & PASSWORD FIELDS (Standard)
                OutlinedTextField(
                    value = username,
                    onValueChange = { viewModel.usernameInput.value = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { viewModel.passwordInput.value = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, null)
                        }
                    }
                )

                Spacer(Modifier.height(16.dp))

                // ⚙️ ADVANCED OPTIONS TOGGLE
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Advanced Options (IP/Port)",
                        color = neonPurple,
                        fontSize = 14.sp
                    )
                    Icon(
                        imageVector = if(showAdvanced) Icons.Filled.ExpandMore else Icons.Filled.Settings,
                        contentDescription = null,
                        tint = neonPurple,
                        modifier = Modifier.size(16.dp).padding(start = 4.dp)
                    )
                }

                // ⚙️ ADVANCED FIELDS (Animated)
                AnimatedVisibility(visible = showAdvanced) {
                    Column {
                        OutlinedTextField(
                            value = ipInput,
                            onValueChange = {
                                ipInput = it
                                settingsManager.saveServerConfig(ipInput, portInput)
                            },
                            label = { Text("Server IP (e.g. 192.168.1.5)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = neonPurple,
                                focusedLabelColor = neonPurple
                            )
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = portInput,
                            onValueChange = {
                                portInput = it
                                settingsManager.saveServerConfig(ipInput, portInput)
                            },
                            label = { Text("Port (e.g. 8000)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // LOGIN BUTTON
                Button(
                    onClick = {
                        if (isLogin) viewModel.login() else viewModel.register()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = neonPurple)
                ) {
                    if (loading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                    } else {
                        Text(text = if (isLogin) "LOGIN" else "REGISTER", fontSize = 18.sp)
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = if (isLogin) "No account? Register" else "Already have an account? Login",
                    color = neonPurple,
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