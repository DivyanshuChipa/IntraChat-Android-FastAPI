package com.example.intra

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 💊 MiniPlayerPill.kt - Minimalist Floating Bottom Music Player for Intra
 * 
 * Sits unobtrusively at the bottom of the screen while browsing Chat, SMB, or WebDrop.
 * Features:
 * - 🎵 Minimal Music Icon + Title (Zero clutter, no heavy vinyl or visualizer)
 * - ⏯️ Play/Pause toggle
 * - ⤢ Maximize button (or tap pill to open the full Vinyl Player)
 * - ✕ Close button (stops playback and hides the pill)
 * - 📏 2dp Thin gradient timeline progress bar along bottom edge
 */
@Composable
fun MiniPlayerPill(
    modifier: Modifier = Modifier
) {
    val track = IntraAudioPlayerManager.currentTrack ?: return
    val isVisible = !IntraAudioPlayerManager.isFullPlayerVisible

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        val title = IntraAudioPlayerManager.trackTitle ?: track.fileName.substringBeforeLast('.')
        val isPlaying = IntraAudioPlayerManager.isPlaying
        val current = IntraAudioPlayerManager.currentPosition
        val total = IntraAudioPlayerManager.duration.coerceAtLeast(1L)
        val progressFraction = (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .height(52.dp)
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(26.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { IntraAudioPlayerManager.showFullPlayer() }
                ),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF1B132B).copy(alpha = 0.96f),
            border = BorderStroke(1.dp, Color(0xFF6741A8).copy(alpha = 0.45f))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Main Pill Content Row
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 14.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Music Note Icon
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Music",
                        tint = Color(0xFFA855F7),
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    // Center: Song Title
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Controls Group: Play/Pause, Maximize, Close
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Play / Pause Button
                        IconButton(
                            onClick = { IntraAudioPlayerManager.togglePlayPause() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Maximize Button (Expands to full Vinyl Player)
                        IconButton(
                            onClick = { IntraAudioPlayerManager.showFullPlayer() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Maximize",
                                tint = Color(0xFFD8B4FE),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Close Button (Stops playback and closes pill)
                        IconButton(
                            onClick = { IntraAudioPlayerManager.stop() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Bottom Edge: 2.5dp Sleek Gradient Progress Line
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progressFraction)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFA855F7),
                                    Color(0xFFEC4899)
                                )
                            )
                        )
                )
            }
        }
    }
}
