package com.example.intra

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CallScreen(
    state: CallState,
    onEndCall: () -> Unit,
    onAcceptCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)), // Dark Navy Blue Background
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 60.dp)
        ) {
            // --- TOP SECTION: User Info ---
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Avatar Placeholder (Baad mein real photo layenge)
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color.Gray.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(60.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // User Name
                Text(
                    text = state.targetUser,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Status Text
                Text(
                    text = when (state.status) {
                        CallStatus.OUTGOING -> "Calling..."
                        CallStatus.INCOMING -> "Incoming Call..."
                        CallStatus.CONNECTED -> "00:00" // Baad mein timer lagayenge
                        CallStatus.ENDED -> "Call Ended"
                        else -> ""
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 18.sp
                )
            }

            // --- BOTTOM SECTION: Buttons ---
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
            ) {

                if (state.status == CallStatus.INCOMING) {
                    // --- INCOMING CALL UI (Green & Red Buttons) ---

                    // Reject Button
                    CallActionButton(
                        icon = Icons.Default.CallEnd,
                        color = Color(0xFFEF4444), // Red
                        onClick = onEndCall
                    )

                    // Accept Button
                    CallActionButton(
                        icon = Icons.Default.Call,
                        color = Color(0xFF22C55E), // Green
                        onClick = onAcceptCall
                    )

                } else {
                    // --- ACTIVE / OUTGOING CALL UI ---

                    // Mute Button
                    CallActionButton(
                        icon = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        color = if (state.isMuted) Color.White else Color.White.copy(alpha = 0.2f),
                        iconTint = if (state.isMuted) Color.Black else Color.White,
                        onClick = onToggleMute
                    )

                    // End Call Button (Center Big)
                    CallActionButton(
                        icon = Icons.Default.CallEnd,
                        color = Color(0xFFEF4444), // Red
                        size = 72.dp,
                        onClick = onEndCall
                    )

                    // Speaker Button
                    CallActionButton(
                        icon = if (state.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        color = if (state.isSpeakerOn) Color.White else Color.White.copy(alpha = 0.2f),
                        iconTint = if (state.isSpeakerOn) Color.Black else Color.White,
                        onClick = onToggleSpeaker
                    )
                }
            }
        }
    }
}

@Composable
fun CallActionButton(
    icon: ImageVector,
    color: Color,
    iconTint: Color = Color.White,
    size: androidx.compose.ui.unit.Dp = 56.dp,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(size / 2)
        )
    }
}