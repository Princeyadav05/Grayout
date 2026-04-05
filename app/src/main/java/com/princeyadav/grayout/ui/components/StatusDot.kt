package com.princeyadav.grayout.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.ui.theme.GrayoutTheme

@Composable
fun StatusDot(
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = GrayoutTheme.colors
    val fillColor by animateColorAsState(
        targetValue = if (isActive) colors.success else colors.off,
        animationSpec = tween(300),
        label = "dotColor",
    )
    val glowColor = colors.success.copy(alpha = 0.27f)

    Box(
        modifier = modifier
            .size(8.dp)
            .drawBehind {
                if (isActive) {
                    drawCircle(
                        color = glowColor,
                        radius = size.minDimension / 2 + 8.dp.toPx(),
                    )
                }
            }
            .background(fillColor, CircleShape),
    )
}
