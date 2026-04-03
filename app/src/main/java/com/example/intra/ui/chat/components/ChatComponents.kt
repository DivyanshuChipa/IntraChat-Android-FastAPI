package com.example.intra.ui.chat.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.intra.ChatViewModel
import com.example.intra.SettingsManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay

@SuppressLint("MissingPermission")
@Composable
fun MessageInputBar(
    viewModel: ChatViewModel,
    receiverName: String,
    onAttachClick: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }

    val inputValue = viewModel.inputMessage.value
    val hasText = inputValue.isNotBlank()
    var isFocused by remember { mutableStateOf(false) }

    val sendScale = remember { Animatable(1f) }
    var pendingWeatherCommand by remember { mutableStateOf<String?>(null) }

    // Initialize Location Client
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Permission Launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val isGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            val weatherCommand = pendingWeatherCommand
            pendingWeatherCommand = null

            if (isGranted) {
                // Fetch location and send message
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            viewModel.sendMessage(
                                receiverName,
                                location.latitude,
                                location.longitude,
                                weatherCommand
                            )
                        } else {
                            viewModel.sendMessage(receiverName, messageText = weatherCommand)
                        }
                    }
                    .addOnFailureListener {
                        viewModel.sendMessage(receiverName, messageText = weatherCommand)
                    }
            } else {
                // Permission denied, just send message normally
                viewModel.sendMessage(receiverName, messageText = weatherCommand)
            }
        }
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.24f else 0f,
        animationSpec = tween(250),
        label = "inputGlowAlpha"
    )

    LaunchedEffect(hasText) {
        if (hasText) {
            sendScale.snapTo(0.88f)
            sendScale.animateTo(1.12f, animationSpec = tween(120))
            sendScale.animateTo(1f, animationSpec = tween(140))
        } else {
            sendScale.animateTo(1f, animationSpec = tween(120))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        AnimatedVisibility(
            visible = !hasText,
            enter = fadeIn(tween(220)) + slideInHorizontally(
                animationSpec = tween(220),
                initialOffsetX = { -it / 2 }
            ),
            exit = fadeOut(tween(180)) + slideOutHorizontally(
                animationSpec = tween(180),
                targetOffsetX = { -it / 2 }
            )
        ) {
            IconButton(onClick = onAttachClick) {
                Icon(Icons.Filled.AttachFile, contentDescription = "Attach")
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha))
                .padding(2.dp)
        ) {
            OutlinedTextField(
                value = inputValue,
                onValueChange = {
                    if (it != inputValue) {
                        viewModel.inputMessage.value = it
                        if (it.isNotEmpty()) viewModel.sendTyping(receiverName)
                    }
                },
                placeholder = { Text("Type something...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused }
                    .clip(RoundedCornerShape(24.dp)),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )
        }

        IconButton(
            onClick = {
                val outboundText = inputValue.trim()
                if (outboundText.equals("/weather", ignoreCase = true) && settingsManager.isLocationEnabled()) {
                    // Check Permissions
                    val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val hasCoarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

                    if (hasFineLocation || hasCoarseLocation) {
                        // Permissions granted, fetch location
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                            .addOnSuccessListener { location ->
                                if (location != null) {
                                    viewModel.sendMessage(
                                        receiverName,
                                        location.latitude,
                                        location.longitude,
                                        outboundText
                                    )
                                } else {
                                    viewModel.sendMessage(receiverName, messageText = outboundText) // Fallback
                                }
                            }
                            .addOnFailureListener {
                                viewModel.sendMessage(receiverName, messageText = outboundText) // Fallback
                            }
                    } else {
                        // Request Permissions
                        pendingWeatherCommand = outboundText
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                } else {
                    // Normal Message or Location OFF
                    viewModel.sendMessage(receiverName, messageText = outboundText)
                }
            },
            enabled = hasText,
            modifier = Modifier.scale(sendScale.value)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}

@Composable
fun TypingIndicatorUI(name: String) {
    var dotCount by remember { mutableIntStateOf(1) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            dotCount = if (dotCount < 3) dotCount + 1 else 1
        }
    }

    Row(
        modifier = Modifier
            .padding(12.dp)
            .background(
                Color.LightGray.copy(alpha = 0.2f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(Color(0xFFBB86FC), CircleShape)
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = "$name is typing",
            fontSize = 12.sp,
            color = Color.Gray,
            fontStyle = FontStyle.Italic
        )

        Text(
            text = ".".repeat(dotCount),
            fontSize = 16.sp,
            color = Color.Gray,
            modifier = Modifier.width(24.dp)
        )
    }
}

@Composable
fun UniqueLoader(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "loader")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier
            .size(40.dp)
            .rotate(rotation)
            .border(3.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
    )
}
