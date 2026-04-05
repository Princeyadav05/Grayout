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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    Box(
        modifier = modifier
            .size(8.dp)
            .drawBehind {
                if (isActive) {
                    val glowRadius = size.minDimension / 2 + 6.dp.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.success.copy(alpha = 0.3f),
                                Color.Transparent,
                            ),
                            radius = glowRadius,
                        ),
                        radius = glowRadius,
                    )
                }
            }
            .background(fillColor, CircleShape),
    )
}
