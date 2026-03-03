package com.example.intra

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.intra.ui.chat.components.MessageBubble
import com.example.intra.ui.chat.components.MessageInputBar
import com.example.intra.ui.chat.components.TypingIndicatorUI
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    receiverName: String,
    onAttachClick: () -> Unit,
    onCameraClick: () -> Unit,
    onBackClick: () -> Unit,
    onStartCall: () -> Unit,
) {
    val listState = rememberLazyListState()
    var videoUrlToPlay by remember { mutableStateOf<String?>(null) }
    var imageUrlToView by remember { mutableStateOf<String?>(null) }

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
                onAttachClick = onAttachClick,
                onCameraClick = onCameraClick
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
