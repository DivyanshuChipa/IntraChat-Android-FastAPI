package com.example.intraplay.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.intraplay.ui.TvFocusBorder
import com.example.intraplay.ui.TvSurface

@Composable
fun TvFocusableCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    shape: Shape = RoundedCornerShape(12.dp),
    backgroundColor: Color = TvSurface,
    focusedBorderColor: Color = TvFocusBorder,
    unfocusedBorderColor: Color = Color(0xFF30363D),
    borderWidth: Dp = 2.5.dp,
    scaleAmount: Float = 1.06f,
    content: @Composable (isFocused: Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) scaleAmount else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "tv_card_scale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .zIndex(if (isFocused) 10f else 1f)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(
            width = if (isFocused) borderWidth else 1.dp,
            color = if (isFocused) focusedBorderColor else unfocusedBorderColor
        ),
        shadowElevation = if (isFocused) 16.dp else 2.dp
    ) {
        content(isFocused)
    }
}
