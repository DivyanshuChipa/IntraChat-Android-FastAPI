package com.example.intra.ui.chat.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.intra.ChatViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInputBar(
    viewModel: ChatViewModel,
    receiverName: String,
    onAttachClick: () -> Unit,
    onCameraClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side icons
        IconButton(onClick = onAttachClick) {
            Icon(
                imageVector = Icons.Filled.AttachFile,
                contentDescription = "Attach",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onCameraClick) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = "Camera",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Input Field
        TextField(
            value = viewModel.inputMessage.value,
            onValueChange = {
                if (it != viewModel.inputMessage.value) {
                    viewModel.inputMessage.value = it
                    if (it.isNotEmpty()) viewModel.sendTyping(receiverName)
                }
            },
            placeholder = { Text("Message") },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        )

        // Send Button
        val hasText = viewModel.inputMessage.value.isNotBlank()
        IconButton(
            onClick = { viewModel.sendMessage(receiverName) },
            enabled = hasText
        ) {
            Icon(
                imageVector = Icons.Filled.Send,
                contentDescription = "Send",
                tint = if (hasText) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
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
            .border(3.dp, Color.White.copy(alpha=0.8f), RoundedCornerShape(4.dp))
    )
}
