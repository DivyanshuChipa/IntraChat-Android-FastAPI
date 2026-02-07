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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.material.icons.filled.Call
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import android.util.Patterns
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.alpha

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    receiverName: String,
    onAttachClick: () -> Unit,
    onBackClick: () -> Unit,
    onStartCall: () -> Unit,
) {
    val listState = rememberLazyListState()

    // Open / Close chat safely
    LaunchedEffect(receiverName) {
        viewModel.openChat(receiverName)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.closeChat()
        }
    }

    // 🔥 FIX 1: Auto-scroll on new message
    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            delay(100) // Small delay for smooth animation
            listState.animateScrollToItem(viewModel.messages.size - 1)
        }
    }

    // 🔥 FIX 2: Auto-scroll when typing indicator appears
    val isTyping = viewModel.typingStatuses[receiverName] == true
    LaunchedEffect(isTyping) {
        if (isTyping && viewModel.messages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(viewModel.messages.size) // Scroll to typing indicator
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
                },
                actions = {
                    IconButton(onClick = onStartCall) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call"
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
            // 🔥 FIX 3: Messages bottom se start honge (reverseLayout hataya)
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

                // Typing Indicator inside the list
                if (isTyping) {
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
    var dotCount by remember { mutableStateOf(1) }

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

                if (message.isLoading) {
                    val fileName = message.fileName ?: "File"
                    val fileExtension = fileName.substringAfterLast(".", "").lowercase()
                    val isImage = fileExtension in listOf("jpg", "jpeg", "png", "gif", "webp")

                    if (message.localUri != null && isImage) {
                         Box(contentAlignment = Alignment.Center) {
                             AsyncImage(
                                 model = message.localUri,
                                 contentDescription = "Uploading",
                                 modifier = Modifier
                                     .fillMaxWidth()
                                     .heightIn(max = 200.dp)
                                     .clip(RoundedCornerShape(8.dp))
                                     .alpha(0.6f),
                                 contentScale = ContentScale.Crop
                             )
                             UniqueLoader()
                         }
                    } else {
                         Row(verticalAlignment = Alignment.CenterVertically) {
                             UniqueLoader(Modifier.size(24.dp))
                             Spacer(Modifier.width(8.dp))
                             Text(
                                 text = "Uploading $fileName...",
                                 fontSize = 12.sp,
                                 color = if (message.isSelf) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                             )
                         }
                    }
                }
                // 🔥 IMPROVED: File Message with Preview & Modern UI
                else if (message.type == "file" && message.fileUrl != null) {
                    val fileName = message.fileName ?: "File"
                    val fileExtension = fileName.substringAfterLast(".", "").lowercase()

                    // Determine file type
                    val isImage = fileExtension in listOf("jpg", "jpeg", "png", "gif", "webp")
                    val isVideo = fileExtension in listOf("mp4", "mkv", "avi", "mov", "webm")

                    // 🖼️ Image Preview
                    if (isImage) {
                        val settingsManager = remember { SettingsManager(context) }
                        val baseUrl = settingsManager.getBaseUrl().removeSuffix("/")
                        val fullUrl = if (message.fileUrl.startsWith("http"))
                            message.fileUrl
                        else
                            baseUrl + message.fileUrl

                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(fullUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = fileName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(Modifier.height(6.dp))
                    }

                    // 📁 File Info Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // File Icon based on type
                        Icon(
                            imageVector = when {
                                isImage -> Icons.Default.Image
                                isVideo -> Icons.Default.VideoLibrary
                                else -> Icons.Default.InsertDriveFile
                            },
                            contentDescription = null,
                            tint = if (message.isSelf)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(Modifier.width(8.dp))

                        Text(
                            text = fileName,
                            color = if (message.isSelf)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // 🔥 Modern Action Button
                    Button(
                        onClick = {
                            try {
                                val settingsManager = SettingsManager(context)
                                val baseUrl = settingsManager.getBaseUrl().removeSuffix("/")
                                val finalUrl = if (message.fileUrl.startsWith("http"))
                                    message.fileUrl
                                else
                                    baseUrl + message.fileUrl

                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Log.e("Chat", "File open error", e)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (message.isSelf)
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                            else
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (isImage || isVideo) "View" else "Open",
                            fontSize = 13.sp
                        )
                    }

                } else {
                    // 💬 TEXT MESSAGE (With Clickable Links)
                    val textColor = if (message.isSelf)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant

                    val annotatedString = buildAnnotatedString {
                        append(message.text)
                        val matcher = Patterns.WEB_URL.matcher(message.text)
                        while (matcher.find()) {
                            val start = matcher.start()
                            val end = matcher.end()
                            addStyle(
                                style = SpanStyle(
                                    color = if (message.isSelf) Color.Cyan else Color(0xFF2196F3),
                                    textDecoration = TextDecoration.Underline
                                ),
                                start = start,
                                end = end
                            )
                            addStringAnnotation(
                                tag = "URL",
                                annotation = message.text.substring(start, end),
                                start = start,
                                end = end
                            )
                        }
                    }

                    ClickableText(
                        text = annotatedString,
                        style = LocalTextStyle.current.copy(color = textColor),
                        onClick = { offset ->
                            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                .firstOrNull()?.let { annotation ->
                                    var url = annotation.item
                                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                        url = "http://$url"
                                    }
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Log.e("Chat", "Link open error", e)
                                    }
                                }
                        }
                    )
                }

                // 🕒 TIMESTAMP
                message.timestamp?.let {
                    Spacer(Modifier.height(4.dp))
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

@Composable
fun UniqueLoader(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = modifier
            .size(40.dp)
            .rotate(rotation)
            .border(3.dp, Color.White.copy(alpha=0.8f), RoundedCornerShape(4.dp))
    )
}
