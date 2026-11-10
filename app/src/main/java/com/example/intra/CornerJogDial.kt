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
 * - Continuous smooth white rail: dynamically divides itself across any number of tools.
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

    // Colors matching Intra theme & the concept mockup
    val primaryColor = if (isDark) MaterialTheme.colorScheme.primary else Color(0xFF6741A8)
    val outerLavenderColor = if (isDark) Color(0xFF7E57C2).copy(alpha = 0.35f) else Color(0xFFD1C4E9).copy(alpha = 0.65f)
    val midPurpleColor = if (isDark) Color(0xFF512DA8) else Color(0xFF7E57C2)

    // Current angle fraction [0f, 1f]
    var currentFraction by remember {
        mutableFloatStateOf(
            if (items.size > 1) selectedIndex.toFloat() / (items.size - 1) else 0.5f
        )
    }

    // Keep fraction updated if selectedIndex changes externally
    LaunchedEffect(selectedIndex, items.size) {
        val target = if (items.size > 1) selectedIndex.toFloat() / (items.size - 1) else 0.5f
        currentFraction = target
    }

    val animatedFraction by animateFloatAsState(
        targetValue = currentFraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
        label = "JogDialFraction"
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
                        onDismiss()
                    } else {
                        // Tapped along the wheel: launch currently focused tool
                        val activeItem = items.getOrElse(selectedIndex.coerceIn(0, items.size - 1)) { items[0] }
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onLaunch(activeItem)
                    }
                }
            }
            .pointerInput(items.size, isLeftHandMode) {
                detectDragGestures(
                    onDragStart = { _ -> },
                    onDrag = { change, _ ->
                        change.consume()
                        val cornerX = if (isLeftHandMode) 0f else size.width.toFloat()
                        val cornerY = size.height.toFloat()

                        val dx = change.position.x - cornerX
                        val dy = change.position.y - cornerY

                        // Calculate angle in radians, convert to degrees
                        val rad = atan2(dy, dx)
                        var deg = Math.toDegrees(rad.toDouble()).toFloat() // typically -180 to 0

                        val fraction: Float = if (!isLeftHandMode) {
                            // Right Hand Mode: from -180° (left) to -90° (top)
                            ((deg + 180f) / 90f).coerceIn(0f, 1f)
                        } else {
                            // Left Hand Mode: from 0° (right) to -90° (top)
                            ((-deg) / 90f).coerceIn(0f, 1f)
                        }

                        currentFraction = fraction
                        val newIdx = (fraction * items.size).toInt().coerceIn(0, items.size - 1)
                        if (newIdx != selectedIndex) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onIndexChange(newIdx)
                        }
                    },
                    onDragEnd = {
                        // 🚀 FLUID LAUNCH: Release thumb to launch!
                        val activeItem = items.getOrElse(selectedIndex.coerceIn(0, items.size - 1)) { items[0] }
                        onLaunch(activeItem)
                    },
                    onDragCancel = {
                        onDismiss()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cornerX = if (isLeftHandMode) 0f else size.width
            val cornerY = size.height
            val corner = Offset(cornerX, cornerY)

            // Radius layers matching Ibis Paint concept
            val rOuter = 230.dp.toPx()
            val rMid = 185.dp.toPx()
            val rRail = 135.dp.toPx()
            val rInner = 60.dp.toPx()

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

            // 4. Indicator Bead on the Rail (Follows thumb sweep angle)
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
                color = Color.White.copy(alpha = 0.35f),
                radius = 12.dp.toPx(),
                center = Offset(beadX, beadY)
            )

            // Bead Solid Core
            drawCircle(
                color = Color.White,
                radius = 7.dp.toPx(),
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
                .background(primaryColor),
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
