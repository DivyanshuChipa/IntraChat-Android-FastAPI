package com.example.intra

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme

import androidx.compose.ui.text.input.KeyboardType

@Composable
fun ChangeIpAddressScreen(
    settingsManager: SettingsManager,
    onIpAddressChanged: () -> Unit
) {
    var ipAddress by remember { mutableStateOf(settingsManager.getServerIp()) }
    val isIpValid by derivedStateOf {
        android.util.Patterns.IP_ADDRESS.matcher(ipAddress).matches()
    }
    val isDark = isSystemInDarkTheme()
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
                    text = "Change Server IP",
                    fontSize = 24.sp,
                    color = neonPurple
                )
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("Server IP Address") },
                    singleLine = true,
                    isError = !isIpValid,
                    supportingText = {
                        if (!isIpValid) {
                            Text("Invalid IP address", color = MaterialTheme.colorScheme.error)
                        }
                    },
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
                Button(
                    onClick = {
                        settingsManager.setServerIp(ipAddress)
                        onIpAddressChanged()
                    },
                    enabled = isIpValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = neonPurple
                    )
                ) {
                    Text(
                        text = "SAVE",
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}
