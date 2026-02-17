package com.example.intra

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

@Composable
fun ImageViewerDialog(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

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
            // 1. Image Viewer
            AsyncImage(
                model = imageUrl,
                contentDescription = "Full screen image",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            // 2. Control Buttons (Download + Close) - Top Right
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 32.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                // DOWNLOAD BUTTON
                IconButton(
                    onClick = {
                        downloadImage(context, imageUrl)
                    },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // CLOSE BUTTON
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

// Helper Function: Download Logic
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
