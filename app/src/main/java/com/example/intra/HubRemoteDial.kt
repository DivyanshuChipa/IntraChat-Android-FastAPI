package com.example.intra

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 🎛️ HubItem: Model representing an action in the Hub.
 */
data class HubItem(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/**
 * 🎛️ HubRemoteDial: An ergonomic, thumb-friendly curved arc slider/knob.
 *
 * Controls the TopBar tools from the natural thumb zone (Bottom-Right or Bottom-Left in Left-Handed Mode).
 * Uses clean Material 3 styling accented with the signature Lumir hair gradient:
 * Deep Purple (#8E24AA) ➔ Vibrant Pink (#E91E63) ➔ Warm Amber (#FFB300).
 */
@Composable
fun HubRemoteDial(
    items: List<HubItem>,
    selectedIndex: Int,
    onIndexChange: (Int) -> Unit,
    onLaunch: (HubItem) -> Unit,
    onDismiss: () -> Unit,
    isLeftHandMode: Boolean,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    val currentItem = items.getOrElse(selectedIndex.coerceIn(0, items.size - 1)) { items[0] }

    // Animated fraction for smooth knob position along the curve
    val targetFraction = if (items.size > 1) {
        (selectedIndex.toFloat() / (items.size - 1)).coerceIn(0f, 1f)
    } else 0.5f

    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "KnobPosition"
    )

    // Lumir Hair Signature Gradient
    val lumirGradient = remember {
        Brush.horizontalGradient(
            listOf(
                Color(0xFF8E24AA), // Deep Purple
                Color(0xFFE91E63), // Vibrant Pink
                Color(0xFFFFB300)  // Warm Amber
            )
        )
    }

    Surface(
        modifier = modifier
            .width(230.dp)
            .shadow(12.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp).copy(alpha = 0.96f),
        border = BorderStroke(1.5.dp, lumirGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ==========================================
            // 1. TOP HEADER: CURRENT ITEM PILL & CLOSE
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Focused Item Pill
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF8E24AA).copy(alpha = 0.20f),
                                    Color(0xFFE91E63).copy(alpha = 0.20f),
                                    Color(0xFFFFB300).copy(alpha = 0.20f)
                                )
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = currentItem.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = currentItem.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }

                // Close Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close Dial",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ==========================================
            // 2. CURVED ARC KNOB TRACK (CANVAS)
            // ==========================================
            val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            val activeColor = Color(0xFFE91E63)
            val amberColor = Color(0xFFFFB300)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .pointerInput(items.size) {
                        detectTapGestures { offset ->
                            val padX = 22.dp.toPx()
                            val usableWidth = (size.width - padX * 2).coerceAtLeast(1f)
                            val t = ((offset.x - padX) / usableWidth).coerceIn(0f, 1f)
                            val newIdx = (t * (items.size - 1)).roundToInt().coerceIn(0, items.size - 1)
                            if (newIdx != selectedIndex) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onIndexChange(newIdx)
                            }
                        }
                    }
                    .pointerInput(items.size) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val padX = 22.dp.toPx()
                            val usableWidth = (size.width - padX * 2).coerceAtLeast(1f)
                            val t = ((change.position.x - padX) / usableWidth).coerceIn(0f, 1f)
                            val newIdx = (t * (items.size - 1)).roundToInt().coerceIn(0, items.size - 1)
                            if (newIdx != selectedIndex) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onIndexChange(newIdx)
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val padX = 22.dp.toPx()
                    val p0 = Offset(padX, size.height * 0.78f)
                    val p1 = Offset(size.width / 2f, size.height * 0.22f)
                    val p2 = Offset(size.width - padX, size.height * 0.78f)

                    // Helper to get point on quadratic bezier curve
                    fun getPoint(t: Float): Offset {
                        val oneMinusT = 1f - t
                        val x = oneMinusT * oneMinusT * p0.x + 2f * oneMinusT * t * p1.x + t * t * p2.x
                        val y = oneMinusT * oneMinusT * p0.y + 2f * oneMinusT * t * p1.y + t * t * p2.y
                        return Offset(x, y)
                    }

                    // Draw Curved Track Path
                    val path = Path().apply {
                        moveTo(p0.x, p0.y)
                        quadraticTo(p1.x, p1.y, p2.x, p2.y)
                    }
                    drawPath(
                        path = path,
                        color = trackColor,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw Discrete Detent Ticks
                    val count = items.size
                    for (i in 0 until count) {
                        val t = if (count > 1) i.toFloat() / (count - 1) else 0.5f
                        val pt = getPoint(t)
                        val isSelected = i == selectedIndex
                        if (isSelected) {
                            drawCircle(
                                color = amberColor.copy(alpha = 0.4f),
                                radius = 7.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = activeColor,
                                radius = 4.5.dp.toPx(),
                                center = pt
                            )
                        } else {
                            drawCircle(
                                color = trackColor,
                                radius = 2.5.dp.toPx(),
                                center = pt
                            )
                        }
                    }

                    // Draw Draggable Knob Bead
                    val knobPt = getPoint(animatedFraction)

                    // Knob Outer Glow
                    drawCircle(
                        color = Color(0xFFE91E63).copy(alpha = 0.25f),
                        radius = 17.dp.toPx(),
                        center = knobPt
                    )

                    // Knob Body (Lumir Gradient)
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFFFB300), Color(0xFFE91E63), Color(0xFF8E24AA)),
                            center = knobPt,
                            radius = 14.dp.toPx()
                        ),
                        radius = 13.dp.toPx(),
                        center = knobPt
                    )

                    // Knob Inner Center Core
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = knobPt
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ==========================================
            // 3. ACTION BUTTON: "TAP TO LAUNCH"
            // ==========================================
            Button(
                onClick = { onLaunch(currentItem) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "Open ${currentItem.title}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    maxLines = 1
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
