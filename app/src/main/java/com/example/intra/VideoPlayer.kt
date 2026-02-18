package com.example.intra

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerDialog(
    videoUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Player State
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var isDragging by remember { mutableStateOf(false) }
    var isLandscape by remember { mutableStateOf(false) }

    // Resize Mode State: FIT -> ZOOM -> FILL -> FIT
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Seek Animation State
    var showForwardAnim by remember { mutableStateOf(false) }
    var showRewindAnim by remember { mutableStateOf(false) }

    // Volume & Brightness State
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var currentVolume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var currentBrightness by remember { mutableFloatStateOf(activity?.window?.attributes?.screenBrightness ?: 0.5f) }

    // If brightness is automatic (-1), treat start as 0.5
    if (currentBrightness < 0) currentBrightness = 0.5f

    var isChangingVolume by remember { mutableStateOf(false) }
    var isChangingBrightness by remember { mutableStateOf(false) }

    // Gesture temporary values
    var volumeDragAccumulator by remember { mutableFloatStateOf(0f) }

    val window = activity?.window
    val insetsController = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    isControlsVisible = true
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)

        // Save original orientation
        val originalOrientation = activity?.requestedOrientation

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()

            // Restore system bars
            insetsController?.show(WindowInsetsCompat.Type.systemBars())

            // Reset orientation
            if (originalOrientation != null) {
                activity.requestedOrientation = originalOrientation
            }
        }
    }

    // Toggle System Bars
    LaunchedEffect(isControlsVisible) {
        if (insetsController != null) {
            if (isControlsVisible) {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    // Update progress
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            if (!isDragging) {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            }
            delay(500) // Update every 500ms
        }
    }

    // Auto-hide controls
    LaunchedEffect(isControlsVisible, isPlaying) {
        if (isControlsVisible && isPlaying) {
            delay(3000)
            isControlsVisible = false
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
            // 1. Video Surface
            AndroidView(
                factory = {
                    PlayerView(context).apply {
                        player = exoPlayer
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = false // Custom controls
                        this.resizeMode = resizeMode
                        keepScreenOn = true
                    }
                },
                update = { view ->
                    view.resizeMode = resizeMode
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                val width = size.width
                                if (offset.x < width / 2) {
                                    isChangingBrightness = true
                                    currentBrightness = activity?.window?.attributes?.screenBrightness ?: 0.5f
                                    if (currentBrightness < 0) currentBrightness = 0.5f
                                } else {
                                    isChangingVolume = true
                                    currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                                    volumeDragAccumulator = 0f
                                }
                            },
                            onDragEnd = {
                                isChangingBrightness = false
                                isChangingVolume = false
                            },
                            onDragCancel = {
                                isChangingBrightness = false
                                isChangingVolume = false
                            },
                            onVerticalDrag = { change, dragAmount ->
                                // dragAmount is -ve for up, +ve for down
                                // We want Up to increase, Down to decrease.
                                val delta = -dragAmount / size.height // Normalized delta

                                if (isChangingBrightness) {
                                    currentBrightness = (currentBrightness + delta * 2).coerceIn(0.01f, 1f)
                                    val lp = activity?.window?.attributes
                                    lp?.screenBrightness = currentBrightness
                                    activity?.window?.attributes = lp
                                } else if (isChangingVolume) {
                                    // Volume steps are discrete
                                    val range = maxVolume.toFloat()
                                    volumeDragAccumulator += (-dragAmount / size.height) * range * 1.5f // sensitivity

                                    if (kotlin.math.abs(volumeDragAccumulator) > 0.5f) {
                                        val step = volumeDragAccumulator.toInt()
                                        if (step != 0) {
                                            val newVol = (currentVolume + step).coerceIn(0f, maxVolume.toFloat())
                                            if (newVol != currentVolume) {
                                                currentVolume = newVol
                                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol.toInt(), 0)
                                                volumeDragAccumulator -= step
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val width = size.width
                                // Avoid seeking if we were just dragging? (Handled by separate gesture detectors usually)
                                if (offset.x < width / 3) {
                                    // Rewind 10s
                                    exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                                    showRewindAnim = true
                                } else if (offset.x > 2 * width / 3) {
                                    // Forward 10s
                                    exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(duration))
                                    showForwardAnim = true
                                } else {
                                    // Center double tap (Toggle Play/Pause)
                                     if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                }
                            },
                            onTap = {
                                isControlsVisible = !isControlsVisible
                            }
                        )
                    }
            )

            // 2. Buffering Indicator
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            // 3. Gesture Indicators (Volume/Brightness)
            AnimatedVisibility(
                visible = isChangingBrightness || isChangingVolume,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (isChangingBrightness) Icons.Default.BrightnessMedium else Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = if (isChangingBrightness) currentBrightness else currentVolume / maxVolume.toFloat(),
                            color = Color.White,
                            trackColor = Color.Gray.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                         Text(
                            text = if (isChangingBrightness) "${(currentBrightness * 100).toInt()}%" else "${currentVolume.toInt()}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 4. Seek Animations (Overlay)
            SeekAnimationOverlay(
                visible = showRewindAnim,
                text = "-10s",
                icon = Icons.Default.Replay10,
                alignment = Alignment.CenterStart,
                onFinished = { showRewindAnim = false }
            )
            SeekAnimationOverlay(
                visible = showForwardAnim,
                text = "+10s",
                icon = Icons.Default.Forward10,
                alignment = Alignment.CenterEnd,
                onFinished = { showForwardAnim = false }
            )

            // 4. Custom Controls Overlay
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                                )
                            )
                            .padding(top = 40.dp, bottom = 20.dp, start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close", tint = Color.White)
                        }

                        Row {
                            // Resize Toggle Button
                            IconButton(
                                onClick = {
                                    resizeMode = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                    Toast.makeText(context, when(resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit to Screen"
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom to Fill"
                                        else -> "Stretch to Fill"
                                    }, Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.AspectRatio, "Resize", tint = Color.White)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Rotate Button
                            IconButton(
                                onClick = {
                                    if (isLandscape) {
                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        isLandscape = false
                                    } else {
                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        isLandscape = true
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ScreenRotation, "Rotate", tint = Color.White)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(onClick = { shareVideoLink(context, videoUrl) }) {
                                Icon(Icons.Default.Share, "Share", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { downloadVideo(context, videoUrl) }) {
                                Icon(Icons.Default.Download, "Download", tint = Color.White)
                            }
                        }
                    }

                    // Center Play/Pause
                    IconButton(
                        onClick = {
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(64.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    // Bottom Bar (Seekbar + Time)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatVideoTime(currentPosition),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Text(
                                text = formatVideoTime(duration),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }

                        Slider(
                            value = currentPosition.toFloat(),
                            onValueChange = {
                                isDragging = true
                                isControlsVisible = true // keep controls visible while seeking
                                currentPosition = it.toLong()
                            },
                            onValueChangeFinished = {
                                isDragging = false
                                exoPlayer.seekTo(currentPosition)
                            },
                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.Gray
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SeekAnimationOverlay(
    visible: Boolean,
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    alignment: Alignment,
    onFinished: () -> Unit
) {
    LaunchedEffect(visible) {
        if (visible) {
            delay(600)
            onFinished()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
             Column(
                 modifier = Modifier
                     .align(alignment)
                     .padding(horizontal = 48.dp)
                     .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                     .padding(16.dp),
                 horizontalAlignment = Alignment.CenterHorizontally
             ) {
                 Icon(icon, contentDescription = null, tint = Color.White)
                 Text(text, color = Color.White, fontWeight = FontWeight.Bold)
             }
        }
    }
}

// Helper: Format Time (mm:ss)
fun formatVideoTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}


// Helper: Share Logic
fun shareVideoLink(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(intent, "Share Video Link"))
}

// Helper: Download Logic (Existing)
fun downloadVideo(context: Context, url: String) {
    try {
        val fileName = "Intra_${System.currentTimeMillis()}.mp4"

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription("Downloading video from Intra...")
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
