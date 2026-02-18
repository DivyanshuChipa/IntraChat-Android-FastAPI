package com.example.intra

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlin.math.abs

@Composable
fun ImageViewerDialog(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showControls by remember { mutableStateOf(true) }
    var dismissalOffset by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (abs(dismissalOffset) > 100) 0.8f else 1f))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                                dismissalOffset = 0f
                            } else {
                                scale = 2.5f
                            }
                        },
                        onTap = { showControls = !showControls }
                    )
                }
        ) {
            // 1. Image Viewer with Zoom & Pan
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = dismissalOffset }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                            } while (event.changes.any { it.pressed })

                            // Gesture Released
                            if (scale == 1f && abs(dismissalOffset) > 200f) {
                                onDismiss()
                            } else {
                                dismissalOffset = 0f
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (scale == 1f && zoom == 1f) {
                                // Pan only, no zoom -> Swipe Dismiss
                                dismissalOffset += pan.y
                            } else {
                                // Zooming or Panning while zoomed
                                dismissalOffset = 0f
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    val maxTranslateX = (size.width * (scale - 1)) / 2
                                    val maxTranslateY = (size.height * (scale - 1)) / 2
                                    offset = Offset(
                                        (offset.x + pan.x).coerceIn(-maxTranslateX, maxTranslateX),
                                        (offset.y + pan.y).coerceIn(-maxTranslateY, maxTranslateY)
                                    )
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        }
                    }
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Full screen image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            }

            // 2. Control Buttons Overlay
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .padding(top = 40.dp, bottom = 20.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // CLOSE BUTTON (Left)
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }

                    Row {
                        // SHARE BUTTON
                        IconButton(
                            onClick = { shareImageLink(context, imageUrl) },
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // DOWNLOAD BUTTON
                        IconButton(
                            onClick = { downloadImage(context, imageUrl) },
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

// Helper Function: Share Logic
fun shareImageLink(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(intent, "Share Image via"))
}

// Helper Function: Download Logic (Existing)
fun downloadImage(context: Context, url: String) {
    try {
        val extension = url.substringAfterLast('.', "jpg")
        val fileName = "Intra_Image_${System.currentTimeMillis()}.$extension"

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription("Downloading image from Intra...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Intra/$fileName")

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)

        Toast.makeText(context, "Downloading started...", Toast.LENGTH_SHORT).show()

    } catch (e: Exception) {
        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        e.printStackTrace()
    }
}
