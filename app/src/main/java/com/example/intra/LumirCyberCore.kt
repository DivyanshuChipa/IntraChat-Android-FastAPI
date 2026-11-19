package com.example.intra

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 🔮 LumirCyberCore.kt - Holographic 3D Hexagonal Quantum Core Visualizer
 *
 * Faithfully brings the user's Ibis Paint artistic vision to life:
 * 1. Central Dual-Core: Radiant lavender/purple plasma nebula with warm amber fusion star.
 * 2. 3D Tilted Perspective Hexagon Cage: Three intersecting astrolabe wireframe rings
 *    projected in 3D perspective space with rotating pitch, yaw, and roll.
 * 3. Solid Hexagonal Crystal Nodes: Glowing hexagonal prisms placed at key cage vertices.
 * 4. Saturn-like Equatorial Orbital Ring & Dotted Stardust Particle Belt:
 *    A smooth elliptical ring encircled by orbiting mini-hexagonal particle beads.
 */
@Composable
fun LumirCyberCore(
    modifier: Modifier = Modifier,
    size: Dp = 260.dp,
    isListening: Boolean = false,
    isThinking: Boolean = false,
    isSpeaking: Boolean = false,
    isConnected: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Lumir3DCoreAnimations")

    // Rotation speeds based on AI state
    val mainDuration = when {
        isThinking -> 4000
        isSpeaking -> 9000
        else -> 22000
    }

    val angleA by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = mainDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angleA"
    )

    val angleB by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (mainDuration * 1.35f).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angleB"
    )

    val angleC by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (mainDuration * 1.7f).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angleC"
    )

    // Orbital stardust particle progress
    val orbitProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isThinking) 3000 else 7500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitProgress"
    )

    // Core Breathing / Voice Reaction Pulse
    val pulseDuration = when {
        isSpeaking -> 420
        isListening -> 1200
        isThinking -> 700
        else -> 2400
    }

    val corePulseScale by infiniteTransition.animateFloat(
        initialValue = when {
            isSpeaking -> 0.88f
            isListening -> 0.94f
            else -> 0.97f
        },
        targetValue = when {
            isSpeaking -> 1.28f
            isListening -> 1.12f
            else -> 1.05f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulseScale"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
            val baseRadius = size.toPx() * 0.44f

            // Color Palette directly inspired by the sketch
            val wireframeColor = Color(0xFFF3E8FF).copy(alpha = 0.95f) // Crisp ivory/lavender wireframe
            val accentLineColor = Color(0xFFE9D5FF).copy(alpha = 0.75f)
            val nodeFillColor = Color(0xFFFFFBEB) // Warm light crystal node
            val nodeStrokeColor = Color(0xFFE9D5FF)

            // ====================================================================
            // 1. BEHIND-THE-CORE: Back-half of Orbital Ring & Particle Belt
            // ====================================================================
            drawOrbitalBelt(
                center = center,
                radiusX = baseRadius * 1.15f,
                radiusY = baseRadius * 0.32f,
                tiltAngleDeg = -18f,
                orbitProgress = orbitProgress,
                drawFrontHalf = false
            )

            // ====================================================================
            // 2. 3D PERSPECTIVE HEXAGON CAGE (Three Intersecting Tilted Rings)
            // ====================================================================

            // Ring 1: Upright Tilted Hexagon (Roll rotating, Pitch ~ 25°, Yaw ~ 18°)
            drawTiltedHexagon(
                center = center,
                radius = baseRadius * 0.92f,
                pitchDeg = 24f,
                yawDeg = 16f,
                rollDeg = angleA,
                color = wireframeColor,
                strokeWidth = 2.0.dp.toPx(),
                hasNodes = true,
                nodeFillColor = nodeFillColor,
                nodeStrokeColor = nodeStrokeColor,
                nodeSize = 7.dp.toPx(),
                nodeIndices = listOf(0, 2, 4) // Nodes on alternate vertices
            )

            // Ring 2: Wide Perspective Horizon Hexagon (Tilted flat like a planetary disc, Pitch ~ 64°)
            drawTiltedHexagon(
                center = center,
                radius = baseRadius * 0.88f,
                pitchDeg = 66f,
                yawDeg = -22f,
                rollDeg = angleB,
                color = accentLineColor,
                strokeWidth = 1.8.dp.toPx(),
                hasNodes = true,
                nodeFillColor = nodeFillColor,
                nodeStrokeColor = nodeStrokeColor,
                nodeSize = 6.dp.toPx(),
                nodeIndices = listOf(1, 3, 5)
            )

            // Ring 3: Cross-Intersecting Hexagon (Counter-Pitch ~ -42°, Yaw ~ 38°)
            drawTiltedHexagon(
                center = center,
                radius = baseRadius * 0.82f,
                pitchDeg = -38f,
                yawDeg = 36f,
                rollDeg = angleC,
                color = wireframeColor.copy(alpha = 0.85f),
                strokeWidth = 1.6.dp.toPx(),
                hasNodes = false,
                nodeFillColor = nodeFillColor,
                nodeStrokeColor = nodeStrokeColor,
                nodeSize = 5.dp.toPx(),
                nodeIndices = emptyList()
            )

            // Synaptic Cross Connections between the rings (creates the complex geometric polyhedron in sketch)
            drawSynapticCrossStruts(
                center = center,
                radius = baseRadius * 0.85f,
                angleA = angleA,
                angleB = angleB,
                color = wireframeColor.copy(alpha = 0.45f)
            )

            // ====================================================================
            // 3. CENTRAL DUAL-CORE (Plasma Nebula + Golden Fusion Star)
            // ====================================================================
            val plasmaRadius = (baseRadius * 0.38f) * corePulseScale

            // Outer Soft Lavender/Purple Nebula Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFF5D0FE).copy(alpha = 0.85f),
                        Color(0xFFC084FC).copy(alpha = 0.65f),
                        Color(0xFF9333EA).copy(alpha = 0.30f),
                        Color(0xFF581C87).copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = plasmaRadius * 1.55f
                ),
                radius = plasmaRadius * 1.55f,
                center = center
            )

            // Vibrant Mid-Aura Sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFAF5FF),
                        Color(0xFFE879F9),
                        Color(0xFFA855F7),
                        Color(0xFF6B21A8).copy(alpha = 0.4f)
                    ),
                    center = center,
                    radius = plasmaRadius
                ),
                radius = plasmaRadius,
                center = center
            )

            // Inner Warm Golden/Amber Fusion Star
            val innerStarRadius = (plasmaRadius * 0.32f).coerceAtLeast(10.dp.toPx())
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFBEB), // White-yellow apex
                        Color(0xFFFEF08A), // Warm solar yellow
                        Color(0xFFF59E0B), // Golden amber edge
                        Color(0xFFD97706).copy(alpha = 0.7f)
                    ),
                    center = center,
                    radius = innerStarRadius
                ),
                radius = innerStarRadius,
                center = center
            )

            // ====================================================================
            // 4. IN-FRONT-OF-THE-CORE: Fore-half of Orbital Ring & Particle Belt
            // ====================================================================
            drawOrbitalBelt(
                center = center,
                radiusX = baseRadius * 1.15f,
                radiusY = baseRadius * 0.32f,
                tiltAngleDeg = -18f,
                orbitProgress = orbitProgress,
                drawFrontHalf = true
            )
        }
    }
}

/**
 * 📐 Projects a 3D point (x, y, z) into 2D screen coordinates with pitch, yaw, roll rotation.
 */
private fun project3D(
    x: Float, y: Float, z: Float,
    pitchRad: Float, yawRad: Float, rollRad: Float,
    center: Offset,
    fov: Float = 700f
): Offset {
    // 1. Roll (rotate around Z axis)
    val cosR = cos(rollRad)
    val sinR = sin(rollRad)
    val x1 = x * cosR - y * sinR
    val y1 = x * sinR + y * cosR
    val z1 = z

    // 2. Yaw (rotate around Y axis)
    val cosY = cos(yawRad)
    val sinY = sin(yawRad)
    val x2 = x1 * cosY + z1 * sinY
    val y2 = y1
    val z2 = -x1 * sinY + z1 * cosY

    // 3. Pitch (rotate around X axis)
    val cosP = cos(pitchRad)
    val sinP = sin(pitchRad)
    val x3 = x2
    val y3 = y2 * cosP - z2 * sinP
    val z3 = y2 * sinP + z2 * cosP

    // Perspective projection factor
    val perspective = fov / (fov + z3).coerceAtLeast(100f)
    return Offset(center.x + x3 * perspective, center.y + y3 * perspective)
}

/**
 * ⬡ Draws a tilted 3D perspective hexagon with optional solid crystal nodes at vertices.
 */
private fun DrawScope.drawTiltedHexagon(
    center: Offset,
    radius: Float,
    pitchDeg: Float,
    yawDeg: Float,
    rollDeg: Float,
    color: Color,
    strokeWidth: Float,
    hasNodes: Boolean,
    nodeFillColor: Color,
    nodeStrokeColor: Color,
    nodeSize: Float,
    nodeIndices: List<Int>
) {
    val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()
    val yawRad = Math.toRadians(yawDeg.toDouble()).toFloat()
    val rollRad = Math.toRadians(rollDeg.toDouble()).toFloat()

    val projectedVertices = mutableListOf<Offset>()

    for (i in 0 until 6) {
        val theta = (i * Math.PI / 3.0).toFloat()
        val x = radius * cos(theta)
        val y = radius * sin(theta)
        val z = 0f

        val pt = project3D(x, y, z, pitchRad, yawRad, rollRad, center)
        projectedVertices.add(pt)
    }

    // Draw the 6 polygon edges
    val path = Path().apply {
        moveTo(projectedVertices[0].x, projectedVertices[0].y)
        for (i in 1 until 6) {
            lineTo(projectedVertices[i].x, projectedVertices[i].y)
        }
        close()
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Draw solid mini hexagonal crystal nodes at specified vertices
    if (hasNodes) {
        for (idx in nodeIndices) {
            if (idx in 0 until 6) {
                drawHexNode(
                    center = projectedVertices[idx],
                    radius = nodeSize,
                    fillColor = nodeFillColor,
                    strokeColor = nodeStrokeColor
                )
            }
        }
    }
}

/**
 * 💎 Draws a miniature filled solid hexagonal crystal node.
 */
private fun DrawScope.drawHexNode(
    center: Offset,
    radius: Float,
    fillColor: Color,
    strokeColor: Color
) {
    val path = Path()
    for (i in 0 until 6) {
        val angle = Math.toRadians((i * 60.0) + 30.0).toFloat()
        val x = center.x + radius * cos(angle)
        val y = center.y + radius * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()

    drawPath(path, fillColor)
    drawPath(path, strokeColor, style = Stroke(width = 1.2.dp.toPx()))
}

/**
 * 🪐 Draws the tilted continuous elliptical ring and the dotted particle stardust belt.
 */
private fun DrawScope.drawOrbitalBelt(
    center: Offset,
    radiusX: Float,
    radiusY: Float,
    tiltAngleDeg: Float,
    orbitProgress: Float,
    drawFrontHalf: Boolean
) {
    val tiltRad = Math.toRadians(tiltAngleDeg.toDouble()).toFloat()
    val cosTilt = cos(tiltRad)
    val sinTilt = sin(tiltRad)

    // 1. Continuous Elliptical Orbit Wire
    val ringColor = Color(0xFFF3E8FF).copy(alpha = if (drawFrontHalf) 0.90f else 0.40f)
    val strokeW = if (drawFrontHalf) 2.0.dp.toPx() else 1.2.dp.toPx()

    val path = Path()
    val steps = 60
    val startStep = if (drawFrontHalf) steps / 2 else 0
    val endStep = if (drawFrontHalf) steps else steps / 2

    for (i in startStep..endStep) {
        val theta = (i * 2.0 * Math.PI / steps).toFloat()
        val rawX = radiusX * cos(theta)
        val rawY = radiusY * sin(theta)

        // Rotate by tilt angle
        val rotX = rawX * cosTilt - rawY * sinTilt
        val rotY = rawX * sinTilt + rawY * cosTilt

        val screenPt = Offset(center.x + rotX, center.y + rotY)
        if (i == startStep) path.moveTo(screenPt.x, screenPt.y) else path.lineTo(screenPt.x, screenPt.y)
    }

    drawPath(
        path = path,
        color = ringColor,
        style = Stroke(width = strokeW, cap = StrokeCap.Round)
    )

    // 2. Dotted Stardust Particle Belt (32 mini-hexagonal beads orbiting)
    val particleCount = 32
    for (i in 0 until particleCount) {
        val baseAngle = (i * 360f / particleCount)
        val currentAngleDeg = (baseAngle + orbitProgress * 360f) % 360f

        // Front half is between 180° and 360° (or sin > 0 depending on coordinate)
        val theta = Math.toRadians(currentAngleDeg.toDouble()).toFloat()
        val isFront = sin(theta) >= 0f

        if (isFront == drawFrontHalf) {
            val rawX = (radiusX * 1.04f) * cos(theta)
            val rawY = (radiusY * 1.04f) * sin(theta)

            val rotX = rawX * cosTilt - rawY * sinTilt
            val rotY = rawX * sinTilt + rawY * cosTilt

            val particleCenter = Offset(center.x + rotX, center.y + rotY)

            // Perspective sizing: Front particles are bigger and brighter
            val depthFactor = (sin(theta) + 1f) / 2f // 0 (back) to 1 (front)
            val beadRadius = (1.5.dp.toPx() + 2.0.dp.toPx() * depthFactor)
            val beadAlpha = (0.35f + 0.65f * depthFactor).coerceIn(0f, 1f)

            // Draw mini diamond/hex bead
            drawHexNode(
                center = particleCenter,
                radius = beadRadius,
                fillColor = Color.White.copy(alpha = beadAlpha),
                strokeColor = Color(0xFFE9D5FF).copy(alpha = beadAlpha)
            )
        }
    }
}

/**
 * ⚡ Draws cross-connecting synaptic struts between Ring A and Ring B vertices.
 */
private fun DrawScope.drawSynapticCrossStruts(
    center: Offset,
    radius: Float,
    angleA: Float,
    angleB: Float,
    color: Color
) {
    val pitchRadA = Math.toRadians(24.0).toFloat()
    val yawRadA = Math.toRadians(16.0).toFloat()
    val rollRadA = Math.toRadians(angleA.toDouble()).toFloat()

    val pitchRadB = Math.toRadians(66.0).toFloat()
    val yawRadB = Math.toRadians(-22.0).toFloat()
    val rollRadB = Math.toRadians(angleB.toDouble()).toFloat()

    // Pick 2 cross vertices to draw connecting holographic struts
    for (i in listOf(0, 3)) {
        val theta = (i * Math.PI / 3.0).toFloat()
        val ptA = project3D(radius * cos(theta), radius * sin(theta), 0f, pitchRadA, yawRadA, rollRadA, center)
        val ptB = project3D(radius * cos(theta + 0.5f), radius * sin(theta + 0.5f), 0f, pitchRadB, yawRadB, rollRadB, center)

        drawLine(
            color = color,
            start = ptA,
            end = ptB,
            strokeWidth = 1.0.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF241442)
@Composable
fun LumirCyberCorePreview() {
    Box(
        modifier = Modifier
            .size(320.dp)
            .background(Color(0xFF241442)),
        contentAlignment = Alignment.Center
    ) {
        LumirCyberCore(
            size = 280.dp,
            isListening = true
        )
    }
}

