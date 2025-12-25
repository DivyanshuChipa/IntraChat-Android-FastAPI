package com.example.intra

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    receiverName: String,
    onAttachClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val listState = rememberLazyListState()

    // ✅ Open / Close chat safely
    LaunchedEffect(receiverName) {
        viewModel.openChat(receiverName)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.closeChat()
        }
    }

    // ✅ Auto-scroll to bottom on new message
    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(receiverName, fontSize = 18.sp)
                        Text(
                            viewModel.connectionStatus.value,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        bottomBar = {
            MessageInputBar(
                viewModel = viewModel,
                receiverName = receiverName,
                onAttachClick = onAttachClick
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    bottom = 8.dp,
                    top = 8.dp
                )
            ) {
                items(viewModel.messages) { msg ->
                    MessageBubble(msg)
                }

                // ✅ Typing Indicator inside the list
                if (viewModel.typingStatuses[receiverName] == true) {
                    item {
                        TypingIndicatorUI(receiverName)
                    }
                }
            }
        }
    }
}

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
                if (it != viewModel.inputMessage.value) { // 💡 सिर्फ तभी भेजें जब वैल्यू बदले
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
    var dotCount by remember { mutableStateOf(1) }

    // 🔥 Animate dots every 500ms
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
        // Neon Purple Dot
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

        // ✅ Animated dots
        Text(
            text = ".".repeat(dotCount),
            fontSize = 16.sp,
            color = Color.Gray,
            modifier = Modifier.width(24.dp) // Fixed width to avoid shifting
        )
    }
}



@Composable
fun MessageBubble(message: ChatMessage) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (message.isSelf)
            Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isSelf)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {

                // 📁 FILE MESSAGE
                if (message.type == "file" && message.fileUrl != null) {

                    Text(
                        text = message.fileName ?: "File",
                        color = if (message.isSelf)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            try {
                                val baseUrl = "http://192.168.31.104:8000"
                                val finalUrl =
                                    if (message.fileUrl.startsWith("http"))
                                        message.fileUrl
                                    else
                                        baseUrl + message.fileUrl

                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(finalUrl)
                                    )
                                )
                            } catch (e: Exception) {
                                Log.e("Chat", "File open error", e)
                            }
                        }
                    ) {
                        Text("Open")
                    }

                } else {
                    // 💬 TEXT MESSAGE
                    Text(
                        text = message.text,
                        color = if (message.isSelf)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 🕒 TIMESTAMP (optional)
                message.timestamp?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTime(it),
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

fun formatTime(ts: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(ts))
}