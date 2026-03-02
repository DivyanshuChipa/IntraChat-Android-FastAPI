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

@Composable
fun MessageInputBar(
    viewModel: ChatViewModel,
    receiverName: String,
    onAttachClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        IconButton(onClick = onAttachClick) {
            Icon(Icons.Filled.AttachFile, contentDescription = "Attach")
        }

        OutlinedTextField(
            value = viewModel.inputMessage.value,
            onValueChange = {
                if (it != viewModel.inputMessage.value) {
                    viewModel.inputMessage.value = it
                    if (it.isNotEmpty()) viewModel.sendTyping(receiverName)
                }
            },
            placeholder = { Text("Type something...") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )

        IconButton(
            onClick = { viewModel.sendMessage(receiverName) },
            enabled = viewModel.inputMessage.value.isNotBlank()
        ) {
            Icon(Icons.Filled.Send, contentDescription = "Send")
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
