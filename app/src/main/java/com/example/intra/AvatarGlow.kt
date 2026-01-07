package com.example.intra

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AvatarGlowRing(
    isActive: Boolean
) {
    if (!isActive) return
    val infiniteTransition = rememberInfiniteTransition(label = "glow")

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isActive) 1200 else 3000,
                easing = EaseInOut
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(150.dp)
            .scale(scale)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x92AB0CB7).copy(alpha = 0.5f), // purple
                        Color(0xFDFFEB85).copy(alpha = 0.25f), // yellow
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            )
    )
}
