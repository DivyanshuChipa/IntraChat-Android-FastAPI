package com.example.intra

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 🔮 LumirCyberCore.kt - Sci-Fi Holographic AI Synaptic Visualizer
 *
 * Renders a living, reactive Hexagonal Cybernetic Core during Lumir AI Voice Calls:
 * - Outer Hexagon: 6 glowing cyan/purple vertices with connecting synaptic vectors.
 * - Counter-rotating inner polygon: Adds holographic 3D depth.
 * - Central Plasma Core: Pulsing multi-layered radial gradient sphere (Lumir Sunset Palette).
 * - Multi-State Reactivity:
 *     - Listening: Rhythmic organic breathing pulse.
 *     - Thinking: Fast rotation with inward particle flow.
 *     - Speaking: Audio wave reactive radial expansion.
 */
@Composable
fun LumirCyberCore(
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    isListening: Boolean = false,
    isThinking: Boolean = false,
    isSpeaking: Boolean = false,
    isConnected: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LumirCoreAnimations")

    // 1. Rotation speed based on AI mental state
    val rotationDuration = when {
        isThinking -> 3500 // Faster spin when thinking/processing
        isSpeaking -> 8000 // Medium spin when speaking
        else -> 18000 // Slow, serene ambient rotation
    }

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = rotationDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    // Reverse rotation for inner concentric polygon
    val innerRotationAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (rotationDuration * 1.5).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRotationAngle"
    )

    // 2. Core Pulsing / Breathing scale
    val pulseDuration = when {
        isSpeaking -> 450 // Rapid rhythmic pulse when talking
        isListening -> 1200 // Deep breathing pulse when listening
        isThinking -> 750 // Moderate pulse when computing
        else -> 2500 // Gentle resting heartbeat
    }

    val corePulseScale by infiniteTransition.animateFloat(
        initialValue = when {
            isSpeaking -> 0.90f
            isListening -> 0.93f
            else -> 0.96f
        },
        targetValue = when {
            isSpeaking -> 1.25f
            isListening -> 1.12f
            else -> 1.04f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseDuration, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulseScale"
    )

    // 3. Particle progress along synaptic lines (for thinking state)
    val particleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particleProgress"
    )

    // Color definitions (Lumir Signature Palette)
    val primaryCyan = Color(0xFF00E5FF)
    val neonPink = Color(0xFFFF2A85)
    val deepViolet = Color(0xFF7C3AED)
    val warmAmber = Color(0xFFFFB300)
    val glowPurple = Color(0xFF9333EA)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val outerRadius = (this.size.minDimension / 2f) * 0.90f
            val innerRadius = outerRadius * 0.55f

            // A. Outer Ambient Glow (Diffused Background Aura)
            val auraRadius = outerRadius * (if (isSpeaking) 1.2f else 1.05f) * corePulseScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowPurple.copy(alpha = if (isSpeaking) 0.35f else 0.18f),
                        deepViolet.copy(alpha = if (isThinking) 0.25f else 0.10f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = auraRadius
                ),
                radius = auraRadius,
                center = center
            )

            // B. Outer Hexagon (6 Vertices) with Rotation
            val outerVertices = mutableListOf<Offset>()
            val rotRad = Math.toRadians(rotationAngle.toDouble())

            for (i in 0 until 6) {
                val angle = rotRad + (i * Math.PI / 3.0)
                val x = center.x + (outerRadius * cos(angle)).toFloat()
                val y = center.y + (outerRadius * sin(angle)).toFloat()
                outerVertices.add(Offset(x, y))
            }

            // Draw outer hexagon polygon perimeter
            val hexPath = Path().apply {
                moveTo(outerVertices[0].x, outerVertices[0].y)
                for (i in 1 until 6) {
                    lineTo(outerVertices[i].x, outerVertices[i].y)
                }
                close()
            }

            drawPath(
                path = hexPath,
                brush = Brush.sweepGradient(
                    colors = listOf(primaryCyan, neonPink, deepViolet, warmAmber, primaryCyan),
                    center = center
                ),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // C. Synaptic Spoke Lines (Connecting Vertices to Center Core)
            for (i in 0 until 6) {
                val vertex = outerVertices[i]
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            primaryCyan.copy(alpha = 0.7f),
                            deepViolet.copy(alpha = 0.2f)
                        ),
                        start = vertex,
                        end = center
                    ),
                    start = vertex,
                    end = center,
                    strokeWidth = 1.5.dp.toPx()
                )

                // Thinking State: Synaptic Particle Pulse moving inwards
                if (isThinking) {
                    val pX = vertex.x + (center.x - vertex.x) * particleProgress
                    val pY = vertex.y + (center.y - vertex.y) * particleProgress
                    drawCircle(
                        color = Color.White,
                        radius = 3.dp.toPx(),
                        center = Offset(pX, pY)
                    )
                }

                // Glowing Node at each Vertex
                drawCircle(
                    color = primaryCyan,
                    radius = 4.dp.toPx(),
                    center = vertex
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.dp.toPx(),
                    center = vertex
                )
            }

            // D. Inner Concentric Tri-Star / Hexagon (Counter-Rotating for 3D Hologram Effect)
            val innerVertices = mutableListOf<Offset>()
            val innerRotRad = Math.toRadians(innerRotationAngle.toDouble())

            for (i in 0 until 6) {
                val angle = innerRotRad + (i * Math.PI / 3.0)
                val x = center.x + (innerRadius * cos(angle)).toFloat()
                val y = center.y + (innerRadius * sin(angle)).toFloat()
                innerVertices.add(Offset(x, y))
            }

            val innerPath = Path().apply {
                moveTo(innerVertices[0].x, innerVertices[0].y)
                for (i in 1 until 6) {
                    lineTo(innerVertices[i].x, innerVertices[i].y)
                }
                close()
            }

            drawPath(
                path = innerPath,
                color = neonPink.copy(alpha = 0.45f),
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
            )

            // E. Central Plasma Core (Pulsing Heart of Lumir)
            val coreRadius = (outerRadius * 0.38f) * corePulseScale

            // Layer 1: Core Ambient Radiance
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        neonPink.copy(alpha = 0.85f),
                        deepViolet.copy(alpha = 0.50f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = coreRadius * 1.4f
                ),
                radius = coreRadius * 1.4f,
                center = center
            )

            // Layer 2: Vibrant Mid-Orb (Sunset Palette)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        warmAmber,
                        neonPink,
                        deepViolet
                    ),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )

            // Layer 3: White-Hot Nucleus
            drawCircle(
                color = Color.White.copy(alpha = 0.90f),
                radius = coreRadius * 0.28f,
                center = center
            )
        }
    }
}
