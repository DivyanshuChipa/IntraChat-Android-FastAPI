package com.example.intra

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 🎛️ CornerJogDial: An ergonomic, fluid rotary corner wheel designed for 1-hand thumb interaction.
 *
 * Inspired by tactile jog wheels and the Ibis Paint concept art:
 * - Concentric purple/lavender quarter-circle layers anchored in the phone's corner.
 * - Continuous smooth white rail with weighted tactile snap.
 * - Fluid Gesture: Slide thumb along rail ➔ release thumb = instant tool launch!
 * - Moving thumb back into the corner "X" cancels without launching.
 */
@Composable
fun CornerJogDial(
    items: List<HubItem>,
    selectedIndex: Int,
    onIndexChange: (Int) -> Unit,
    onLaunch: (HubItem) -> Unit,
    onDismiss: () -> Unit,
    isLeftHandMode: Boolean,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val isDark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current

    // Always-updated references to avoid closure capture bugs
    val currentItems by rememberUpdatedState(items)
    val currentOnLaunch by rememberUpdatedState(onLaunch)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnIndexChange by rememberUpdatedState(onIndexChange)

    // Local tracked active index
    var activeIndex by remember { mutableIntStateOf(selectedIndex.coerceIn(0, items.size - 1)) }
    var isInCancelZone by remember { mutableStateOf(false) }

    // Keep activeIndex synchronized if parent changes it
    LaunchedEffect(selectedIndex) {
        activeIndex = selectedIndex.coerceIn(0, items.size - 1)
    }

    // Colors matching Intra theme & concept mockup
    val primaryColor = if (isDark) MaterialTheme.colorScheme.primary else Color(0xFF6741A8)
    val outerLavenderColor = if (isDark) Color(0xFF7E57C2).copy(alpha = 0.35f) else Color(0xFFD1C4E9).copy(alpha = 0.65f)
    val midPurpleColor = if (isDark) Color(0xFF512DA8) else Color(0xFF7E57C2)

    // Calculate snapped bead target fraction (centers the bead on each tool's wedge)
    val targetFraction = if (items.isNotEmpty()) {
        (activeIndex + 0.5f) / items.size
    } else 0.5f

    // Spring animation for bead: weighted, tactile gear-click feel!
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.70f, stiffness = 550f),
        label = "JogDialBeadSnap"
    )

    val dialSize = 240.dp

    Box(
        modifier = modifier
            .size(dialSize)
            .pointerInput(items.size, isLeftHandMode) {
                detectTapGestures { offset ->
                    val cornerX = if (isLeftHandMode) 0f else size.width.toFloat()
                    val cornerY = size.height.toFloat()

                    val dx = offset.x - cornerX
                    val dy = offset.y - cornerY
                    val dist = sqrt(dx * dx + dy * dy)

                    // If tapped inside close core (radius < 55dp)
                    if (dist < 55.dp.toPx()) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnDismiss()
                    } else {
                        // Tapped along the wheel: calculate touched item and launch
                        val rad = atan2(dy, dx)
                        val deg = Math.toDegrees(rad.toDouble()).toFloat()

                        val fraction = if (!isLeftHandMode) {
                            ((deg - (-172f)) / 74f).coerceIn(0f, 1f)
                        } else {
                            ((-deg - 8f) / 74f).coerceIn(0f, 1f)
                        }

                        val tappedIdx = (fraction * currentItems.size).toInt().coerceIn(0, currentItems.size - 1)
                        val tool = currentItems[tappedIdx]
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnLaunch(tool)
                    }
                }
            }
            .pointerInput(items.size, isLeftHandMode) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val cornerX = if (isLeftHandMode) 0f else size.width.toFloat()
                        val cornerY = size.height.toFloat()
                        val dx = offset.x - cornerX
                        val dy = offset.y - cornerY
                        val dist = sqrt(dx * dx + dy * dy)
                        isInCancelZone = dist < 55.dp.toPx()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val cornerX = if (isLeftHandMode) 0f else size.width.toFloat()
                        val cornerY = size.height.toFloat()

                        val dx = change.position.x - cornerX
                        val dy = change.position.y - cornerY
                        val dist = sqrt(dx * dx + dy * dy)

                        // Check if thumb slipped into the cancel core
                        if (dist < 55.dp.toPx()) {
                            if (!isInCancelZone) {
                                isInCancelZone = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            return@detectDragGestures
                        } else {
                            isInCancelZone = false
                        }

                        // Calculate angle in radians, convert to degrees (-180° to 0°)
                        val rad = atan2(dy, dx)
                        val deg = Math.toDegrees(rad.toDouble()).toFloat()

                        // Calibrated comfortable thumb sweep arc (~74° span)
                        // Prevents awkward edge cramping and stabilizes sensitivity!
                        val fraction: Float = if (!isLeftHandMode) {
                            // Right Hand Mode: from -172° (left) to -98° (top)
                            ((deg - (-172f)) / 74f).coerceIn(0f, 1f)
                        } else {
                            // Left Hand Mode: from -8° (right) to -82° (top)
                            ((-deg - 8f) / 74f).coerceIn(0f, 1f)
                        }

                        // Quantized indexing with snap
                        val newIdx = (fraction * currentItems.size).toInt().coerceIn(0, currentItems.size - 1)
                        if (newIdx != activeIndex) {
                            activeIndex = newIdx
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentOnIndexChange(newIdx)
                        }
                    },
                    onDragEnd = {
                        // 🚀 FLUID LAUNCH: Release thumb to launch the exact active tool!
                        if (isInCancelZone) {
                            currentOnDismiss()
                        } else {
                            val activeTool = currentItems.getOrElse(activeIndex.coerceIn(0, currentItems.size - 1)) { currentItems[0] }
                            currentOnLaunch(activeTool)
                        }
                    },
                    onDragCancel = {
                        currentOnDismiss()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cornerX = if (isLeftHandMode) 0f else size.width
            val cornerY = size.height

            // Radius layers matching Ibis Paint concept
            val rOuter = 230.dp.toPx()
            val rMid = 185.dp.toPx()
            val rRail = 135.dp.toPx()

            // 1. Outer Translucent Lavender/Purple Ring Arc
            val outerStartAngle = if (isLeftHandMode) 270f else 180f
            drawArc(
                color = outerLavenderColor,
                startAngle = outerStartAngle,
                sweepAngle = 90f,
                useCenter = true,
                topLeft = Offset(cornerX - rOuter, cornerY - rOuter),
                size = Size(rOuter * 2, rOuter * 2)
            )

            // 2. Mid Solid Purple Ring Arc
            val midStartAngle = if (isLeftHandMode) 270f else 180f
            drawArc(
                color = midPurpleColor,
                startAngle = midStartAngle,
                sweepAngle = 90f,
                useCenter = true,
                topLeft = Offset(cornerX - rMid, cornerY - rMid),
                size = Size(rMid * 2, rMid * 2)
            )

            // 3. Continuous Smooth White Rail (Track)
            val railStartAngle = if (isLeftHandMode) 270f else 180f
            drawArc(
                color = Color.White.copy(alpha = 0.90f),
                startAngle = railStartAngle,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cornerX - rRail, cornerY - rRail),
                size = Size(rRail * 2, rRail * 2),
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // 4. Tactile Indicator Bead on the Rail
            // Maps the calibrated sweep angle so bead sits squarely on the rail
            val beadAngleDeg: Float = if (!isLeftHandMode) {
                180f + animatedFraction * 90f
            } else {
                270f + (1f - animatedFraction) * 90f
            }
            val beadRad = Math.toRadians(beadAngleDeg.toDouble())
            val beadX = cornerX + rRail * cos(beadRad).toFloat()
            val beadY = cornerY + rRail * sin(beadRad).toFloat()

            // Bead Outer Glow Halo
            drawCircle(
                color = if (isInCancelZone) Color.Red.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.35f),
                radius = 13.dp.toPx(),
                center = Offset(beadX, beadY)
            )

            // Bead Solid Core
            drawCircle(
                color = if (isInCancelZone) Color.Red else Color.White,
                radius = 7.5.dp.toPx(),
                center = Offset(beadX, beadY)
            )
        }

        // 5. Center Core "X" Button (Close / Cancel Zone)
        val closeAlignment = if (isLeftHandMode) Alignment.BottomStart else Alignment.BottomEnd
        Box(
            modifier = Modifier
                .align(closeAlignment)
                .size(60.dp)
                .clip(CircleShape)
                .background(if (isInCancelZone) Color(0xFFD32F2F) else primaryColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close Jog Dial",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * 🎛️ CornerJogDialFab: The idle Floating Action Button (FAB) at the bottom corner.
 *
 * Matching the 2nd image from the concept art:
 * Elevated Intra Purple circle with the 3x3 Apps Grid icon.
 */
@Composable
fun CornerJogDialFab(
    onClick: () -> Unit,
    isLeftHandMode: Boolean,
    modifier: Modifier = Modifier
) {
    val fabAlignment = if (isLeftHandMode) Alignment.BottomStart else Alignment.BottomEnd
    Box(
        modifier = modifier
            .fillMaxSize(),
        contentAlignment = fabAlignment
    ) {
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier
                .padding(
                    start = if (isLeftHandMode) 20.dp else 0.dp,
                    end = if (!isLeftHandMode) 20.dp else 0.dp,
                    bottom = 20.dp
                )
                .size(56.dp)
                .shadow(8.dp, CircleShape),
            shape = CircleShape,
            containerColor = Color(0xFF6741A8),
            contentColor = Color.White
        ) {
            Icon(
                imageVector = Icons.Filled.Apps,
                contentDescription = "Open Jog Dial",
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
