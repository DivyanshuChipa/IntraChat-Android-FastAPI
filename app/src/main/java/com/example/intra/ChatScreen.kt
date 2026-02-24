package com.example.intra

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Patterns
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedButton
import kotlinx.coroutines.delay


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
    var videoUrlToPlay by remember { mutableStateOf<String?>(null) }
    var imageUrlToView by remember { mutableStateOf<String?>(null) } // 👈 YE ADD HUA

    // Set Status Bar Color
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()
    val backgroundColor = MaterialTheme.colorScheme.background

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.statusBarColor = backgroundColor.toArgb()
                insetsController.isAppearanceLightStatusBars = !isDark
            } else {
                if (!isDark) {
                    window.statusBarColor = Color.Black.toArgb()
                } else {
                    window.statusBarColor = backgroundColor.toArgb()
                }

            }
        }
    }

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

    if (videoUrlToPlay != null) {
        VideoPlayerDialog(
            videoUrl = videoUrlToPlay!!,
            onDismiss = { videoUrlToPlay = null }
        )
    }
    
    // 👈 YE CODE ADD HUA:
    if (imageUrlToView != null) {
        ImageViewerDialog(
            imageUrl = imageUrlToView!!,
            onDismiss = { imageUrlToView = null }
        )
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
                    // 👈 YE LINE UPDATE HUI
                    MessageBubble(
                        message = msg,
                        onVideoClick = { url -> videoUrlToPlay = url },
                        onImageClick = { url -> imageUrlToView = url },
                        onOptionSelected = { cmd -> viewModel.sendOptionCommand(receiverName, cmd) }
                    )
                }

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
fun MessageBubble(
    message: ChatMessage,
    onVideoClick: (String) -> Unit = {},
    onImageClick: (String) -> Unit = {}, // 👈 YE ADD HUA
    onOptionSelected: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()

    // 🗓️ Date Picker logic
    val showDatePicker = {
        DatePickerDialog(
            context,
            { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                val formattedDate = String.format(Locale.US, "%02d/%02d/%04d", dayOfMonth, month + 1, year)
                val finalCommand = "###passport9### ###passportdate<$formattedDate>###"
                onOptionSelected(finalCommand)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

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

                val fileName = message.fileName ?: "File"
                val fileExtension = fileName.substringAfterLast(".", "").lowercase()

                val isImage = fileExtension in listOf("jpg", "jpeg", "png", "gif", "webp")
                val isVideo = fileExtension in listOf("mp4", "mkv", "avi", "mov", "webm")

                if (message.isLoading) {
                    if (message.localUri != null && (isImage || isVideo)) {
                        Box(contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(message.localUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Uploading Preview",
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
                } else if (message.type == "file" && message.fileUrl != null) {

                    if (isImage || isVideo) {

                        val settingsManager = remember { SettingsManager(context) }
                        val baseUrl = settingsManager.getBaseUrl().removeSuffix("/")

                        val fullUrl = if (message.fileUrl.startsWith("http"))
                            message.fileUrl
                        else
                            baseUrl + message.fileUrl

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.clickable {
                                if (isVideo) {
                                    onVideoClick(fullUrl)
                                } else if (isImage) {
                                    onImageClick(fullUrl)
                                }
                            }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(fullUrl)
                                    .videoFrameMillis(2000)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = fileName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )

                            if (isVideo) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = "Play",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(48.dp)
                                        .background(Color.Black.copy(alpha=0.3f), CircleShape)
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
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

                    Button(
                        onClick = {
                            try {
                                val settingsManager = SettingsManager(context)
                                val baseUrl = settingsManager.getBaseUrl().removeSuffix("/")
                                val finalUrl = if (message.fileUrl.startsWith("http"))
                                    message.fileUrl
                                else
                                    baseUrl + message.fileUrl
                                
                                // 👈 YE LOGIC UPDATE HUA
                                if (isVideo) {
                                    onVideoClick(finalUrl)
                                } else if (isImage) {
                                    onImageClick(finalUrl)
                                } else {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl))
                                    context.startActivity(intent)
                                }
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

                // 🔘 Options Buttons loop
                if (message.type == "utility_options" && message.options != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    message.options.forEach { optionText ->
                        OutlinedButton(
                            onClick = {
                                when (optionText) {
                                    "🛂 Passport A6 (6 Photos)" -> onOptionSelected("###passport###")
                                    "🛂 Passport A6 (9 Photos)" -> onOptionSelected("###passport9###")
                                    "📄 Extract Text (OCR)" -> onOptionSelected("###ocr###")
                                    "📅 Passport + Date" -> {
                                        showDatePicker()
                                    }
                                    "🗜️ Compress Image" -> onOptionSelected("###compress###")
                                    else -> {
                                        // Do nothing for unknown options as per request, or handle appropriately
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if(message.isSelf) Color.White else MaterialTheme.colorScheme.onSurfaceVariant),
                            border = BorderStroke(1.dp, if(message.isSelf) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f))
                        ) {
                            Text(text = optionText)
                        }
                    }
                }
                
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
