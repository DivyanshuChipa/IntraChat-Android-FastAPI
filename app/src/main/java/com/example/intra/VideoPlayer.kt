package com.example.intra // Apna package name check kar lena

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Download

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerDialog(
    videoUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Player Setup
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

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
                .background(Color.Black)
        ) {
            // 1. Video Player
            AndroidView(
                factory = {
                    PlayerView(context).apply {
                        player = exoPlayer
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Control Buttons (Download + Close) - Top Right
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 32.dp, end = 16.dp), // Status bar se bachane ke liye padding
                verticalAlignment = Alignment.CenterVertically
            ) {

                // 🔥 DOWNLOAD BUTTON
                IconButton(
                    onClick = {
                        downloadVideo(context, videoUrl)
                    },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download, // Icon import check kar lena
                        contentDescription = "Download",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // ❌ CLOSE BUTTON
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

// 🔥 Helper Function: Download Logic
fun downloadVideo(context: Context, url: String) {
    try {
        val fileName = "Intra_${System.currentTimeMillis()}.mp4" // Unique naam

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription("Downloading video from Intra...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Intra/$fileName") // 👈 Folder: Downloads/Intra

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)

        Toast.makeText(context, "Downloading started...", Toast.LENGTH_SHORT).show()

    } catch (e: Exception) {
        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        e.printStackTrace()
    }
}