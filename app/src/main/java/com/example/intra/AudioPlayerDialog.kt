package com.example.intra

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 🎵 AudioPlayerDialog.kt - Fullscreen Vinyl Audio Player for Intra
 * 
 * Powered by centralized `IntraAudioPlayerManager`:
 * - ⚡ 0.2s Zero-Wait Streaming over LAN HTTP
 * - 🎨 Smart Album Artwork (ID3 Cover Art with Purple Vinyl fallback)
 * - 💿 Smooth Vinyl Disc Rotation while playing
 * - 🌟 Real-Time Audio FFT Subwoofer Bass Aura (Tuned: 0.75f damping, 350f stiffness)
 * - 🎛️ Full Controls: Seekbar, ±10s, Download, Minimize to Pill, Close
 */
@Composable
fun AudioPlayerDialog(
    audioUrl: String = IntraAudioPlayerManager.currentTrack?.url ?: "",
    fileName: String = IntraAudioPlayerManager.currentTrack?.fileName ?: "",
    onDismiss: () -> Unit = { IntraAudioPlayerManager.hideFullPlayer() }
) {
    val context = LocalContext.current
    val track = IntraAudioPlayerManager.currentTrack ?: return

    val effectiveUrl = if (audioUrl.isNotBlank()) audioUrl else track.url
    val effectiveName = if (fileName.isNotBlank()) fileName else track.fileName

    val isPlaying = IntraAudioPlayerManager.isPlaying
    val isBuffering = IntraAudioPlayerManager.isBuffering
    val currentPosition = IntraAudioPlayerManager.currentPosition
    val duration = IntraAudioPlayerManager.duration
    val albumArtBitmap = IntraAudioPlayerManager.albumArtBitmap
    val trackTitle = IntraAudioPlayerManager.trackTitle
    val trackArtist = IntraAudioPlayerManager.trackArtist
    val rawBeatLevel = IntraAudioPlayerManager.rawBeatLevel

    // Seek Slider Dragging State
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    // Vinyl Disc Rotation (Spins when playing, freezes when paused)
    var currentRotation by remember { mutableFloatStateOf(0f) }

    // Rhythmic fallback pulse
    val fallbackTransition = rememberInfiniteTransition(label = "fallback_pulse")
    val rhythmicFallback by fallbackTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rhythmic_fallback"
    )

    val currentBeatIntensity = if (rawBeatLevel > 0.05f) rawBeatLevel else if (isPlaying) rhythmicFallback else 0f

    // ⚡ Spring Physics: Tuned (0.75f damping, 350f stiffness, 22f energy cutoff)
    val animatedAuraScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f + (currentBeatIntensity * 0.22f) else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f),
        label = "aura_beat_scale"
    )
    val animatedAuraAlpha by animateFloatAsState(
        targetValue = if (isPlaying) (0.12f + currentBeatIntensity * 0.50f).coerceIn(0.35f, 0.98f) else 0.25f,
        animationSpec = tween(durationMillis = 110),
        label = "aura_beat_alpha"
    )

    // Smooth Vinyl Disc Rotation Frame Loop
    LaunchedEffect(isPlaying) {
        var lastTime = System.nanoTime()
        while (isPlaying) {
            withFrameNanos { now ->
                val dt = (now - lastTime) / 1_000_000_000f
                lastTime = now
                currentRotation = (currentRotation + dt * 45f) % 360f
            }
        }
    }

    // Helper: Formatter for milliseconds -> mm:ss
    fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    val displayTitle = trackTitle ?: effectiveName.substringBeforeLast('.')
    val displayArtist = trackArtist ?: "Intra Music"

    Dialog(
        onDismissRequest = {
            // Tapping outside docks to mini player pill
            IntraAudioPlayerManager.hideFullPlayer()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        IntraAudioPlayerManager.hideFullPlayer()
                        onDismiss()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            // Main Player Card
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 420.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume click inside dialog
                    )
                    .shadow(elevation = 24.dp, shape = RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151022)),
                border = BorderStroke(1.dp, Color(0xFF6741A8).copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF23173D),
                                    Color(0xFF130D20)
                                )
                            )
                        )
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. TOP BAR: [Logo + Music] + [Download] on Left, [Minimize] + [Close] on Right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // LEFT GROUP: Intra Logo + "Music" badge, followed by Download button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Intra Music Badge with official ic_vec2 vector
                            Surface(
                                color = Color(0xFF6741A8).copy(alpha = 0.28f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(0.8.dp, Color(0xFFA855F7).copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_vec2),
                                        contentDescription = "Intra Logo",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isBuffering) "Buffering..." else "Music",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE9D5FF)
                                    )
                                }
                            }

                            // Download Button
                            IconButton(
                                onClick = {
                                    downloadAudioFile(context, effectiveUrl, effectiveName)
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Save to Downloads",
                                    tint = Color(0xFFD8B4FE),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        // RIGHT GROUP: Minimize & Close Buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Minimize button (Docks to bottom pill)
                            IconButton(
                                onClick = {
                                    IntraAudioPlayerManager.hideFullPlayer()
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Minimize to Pill",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Close Button (Completely stops playback)
                            IconButton(
                                onClick = {
                                    IntraAudioPlayerManager.stop()
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Stop & Close",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. CENTER PIECE: Signature Aura Glow + Rotating Vinyl Disc
                    Box(
                        modifier = Modifier.size(255.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 🌟 BACKLIGHT AURA GLOW (Intra Tri-Color: Pink -> Violet -> Gold)
                        Box(
                            modifier = Modifier
                                .size(235.dp)
                                .graphicsLayer {
                                    scaleX = animatedAuraScale
                                    scaleY = animatedAuraScale
                                    alpha = animatedAuraAlpha
                                }
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFFEC4899).copy(alpha = 0.75f), // Core Neon Pink
                                            Color(0xFFA855F7).copy(alpha = 0.55f), // Electric Violet halo
                                            Color(0xFFF59E0B).copy(alpha = 0.35f), // Warm Golden Amber
                                            Color.Transparent                      // Diffuse feathered edge
                                        )
                                    ),
                                    shape = CircleShape
                                )
                        )

                        // 💿 ROTATING VINYL RECORD (Floats over backlight aura)
                        Box(
                            modifier = Modifier
                                .size(215.dp)
                                .shadow(elevation = 16.dp, shape = CircleShape)
                                .rotate(currentRotation),
                            contentAlignment = Alignment.Center
                        ) {
                            // Outer Vinyl Record Grooves
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFF2A2830),
                                                Color(0xFF121115),
                                                Color(0xFF25232B),
                                                Color(0xFF0F0E12)
                                            )
                                        ),
                                        shape = CircleShape
                                    )
                                    .border(2.dp, Color(0xFF4C4556), CircleShape)
                            )

                            // Subtle vinyl sound track grooves
                            Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .border(1.dp, Color.White.copy(alpha = 0.06f), CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(145.dp)
                                    .border(1.dp, Color.White.copy(alpha = 0.06f), CircleShape)
                            )

                            // Inner Center: Album Art or Signature Purple Gradient Disc
                            Box(
                                modifier = Modifier
                                    .size(105.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFF8B5CF6),
                                                Color(0xFF6741A8)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (albumArtBitmap != null) {
                                    Image(
                                        bitmap = albumArtBitmap.asImageBitmap(),
                                        contentDescription = "Album Art",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = "Music Note",
                                        tint = Color.White,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }

                                // Center Spindle Hole
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(Color(0xFF1E1A29), CircleShape)
                                        .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                )
                            }
                        }
                    } // End of Centerpiece Container (Aura + Vinyl)

                    Spacer(modifier = Modifier.height(18.dp))

                    // 3. TRACK INFORMATION (Title + Artist)
                    Text(
                        text = displayTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = displayArtist,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFC084FC),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // 4. PROGRESS TIMELINE & SCRUBBER SLIDER
                    val sliderMax = duration.toFloat().coerceAtLeast(1f)
                    val currentVal = if (isDraggingSlider) sliderPosition else currentPosition.toFloat()

                    Slider(
                        value = currentVal.coerceIn(0f, sliderMax),
                        onValueChange = { newPos ->
                            isDraggingSlider = true
                            sliderPosition = newPos
                        },
                        onValueChangeFinished = {
                            IntraAudioPlayerManager.seekTo(sliderPosition.toLong())
                            isDraggingSlider = false
                        },
                        valueRange = 0f..sliderMax,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFA855F7),
                            activeTrackColor = Color(0xFFA855F7),
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Timestamps (Current Position vs Total Duration)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(if (isDraggingSlider) sliderPosition.toLong() else currentPosition),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Text(
                            text = formatTime(duration),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 5. PLAYBACK CONTROLS (Rewind 10s, Play/Pause, Forward 10s)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Skip Backward 10 Seconds
                        IconButton(
                            onClick = { IntraAudioPlayerManager.rewind10() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(22.dp))

                        // Large Play / Pause FAB
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .shadow(elevation = 12.dp, shape = CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFFA855F7),
                                            Color(0xFF6741A8)
                                        )
                                    ),
                                    shape = CircleShape
                                )
                                .clickable { IntraAudioPlayerManager.togglePlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(22.dp))

                        // Skip Forward 10 Seconds
                        IconButton(
                            onClick = { IntraAudioPlayerManager.forward10() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

/**
 * 💾 Helper: Saves audio to phone storage (`Downloads/Intra/`)
 */
private fun downloadAudioFile(context: Context, url: String, originalFileName: String) {
    val cleanName = if (originalFileName.isNotBlank()) {
        originalFileName
    } else {
        "Intra_Audio_${System.currentTimeMillis()}.mp3"
    }

    if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
        Toast.makeText(context, "Saving '$cleanName' to Downloads/Intra...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.MINUTES)
                    .build()

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful || response.body == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Download failed (Server ${response.code})", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, cleanName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Intra")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }

                    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val uri = context.contentResolver.insert(collection, contentValues)

                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            response.body!!.byteStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        context.contentResolver.update(uri, contentValues, null, null)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val intraDir = File(downloadsDir, "Intra").apply { if (!exists()) mkdirs() }
                    val destFile = File(intraDir, cleanName)

                    destFile.outputStream().use { output ->
                        response.body!!.byteStream().use { input ->
                            input.copyTo(output)
                        }
                    }

                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(destFile.absolutePath),
                        arrayOf("audio/mpeg"),
                        null
                    )
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved to Downloads/Intra/$cleanName", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    } else {
        try {
            val srcFile = File(url)
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val intraDir = File(downloadsDir, "Intra").apply { if (!exists()) mkdirs() }
            val destFile = File(intraDir, cleanName)

            srcFile.copyTo(destFile, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)

            Toast.makeText(context, "Saved to Downloads/Intra/$cleanName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Save error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
