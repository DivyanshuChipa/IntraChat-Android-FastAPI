package com.example.intra

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
    if (viewModel.isAuthenticated.value) {
        onAuthenticated()
    }

    val username = viewModel.usernameInput.value
    val password = viewModel.passwordInput.value
    val errorMsg = viewModel.errorMessage.value
    val loading = viewModel.isLoading.value

    var isLogin by remember { mutableStateOf(true) }
    var passwordVisible by remember { mutableStateOf(false) }

    val isDark = isSystemInDarkTheme()

    // 🎨 COLORS
    val neonPurple = Color(0xFF7A00FF)
    val cardColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFAF7FF)
    val textColor = if (isDark) Color.White else Color.Black
    val subTextColor = if (isDark) Color.LightGray else Color.Gray

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (isDark) listOf(
                        Color(0xFF0F001F),
                        Color(0xFF1A0033)
                    ) else listOf(
                        Color(0xFF6A00FF),
                        Color(0xFF9A4DFF)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {

        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardColor
            )
        ) {

            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = "Intra",
                    fontSize = 36.sp,
                    color = neonPurple
                )

                Text(
                    text = "LAN Messenger",
                    fontSize = 16.sp,
                    color = subTextColor
                )

                Spacer(Modifier.height(18.dp))

                // USERNAME
                OutlinedTextField(
                    value = username,
                    onValueChange = { viewModel.usernameInput.value = it },
                    label = { Text("Username") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedLabelColor = neonPurple,
                        unfocusedLabelColor = subTextColor,
                        cursorColor = neonPurple,
                        focusedBorderColor = neonPurple,
                        unfocusedBorderColor = subTextColor,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )

                Spacer(Modifier.height(12.dp))

                // PASSWORD
                OutlinedTextField(
                    value = password,
                    onValueChange = { viewModel.passwordInput.value = it },
                    label = { Text("Password") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible)
                                    Icons.Filled.Visibility
                                else
                                    Icons.Filled.VisibilityOff,
                                contentDescription = null,
                                tint = subTextColor
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedLabelColor = neonPurple,
                        unfocusedLabelColor = subTextColor,
                        cursorColor = neonPurple,
                        focusedBorderColor = neonPurple,
                        unfocusedBorderColor = subTextColor,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )

                Spacer(Modifier.height(22.dp))

                // LOGIN / REGISTER BUTTON
                Button(
                    onClick = {
                        if (isLogin) viewModel.login()
                        else viewModel.register()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = neonPurple
                    )
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Text(
                            text = if (isLogin) "LOGIN" else "REGISTER",
                            color = Color.White,
                            fontSize = 18.sp
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // TOGGLE LOGIN / REGISTER
                Text(
                    text = if (isLogin)
                        "No account? Register"
                    else
                        "Already have an account? Login",
                    color = neonPurple,
                    modifier = Modifier.clickable {
                        isLogin = !isLogin
                        viewModel.clearError()
                    },
                    textAlign = TextAlign.Center
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
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}
