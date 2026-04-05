package com.princeyadav.grayout.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.ui.theme.GrayoutTheme

@Composable
fun GrayoutCard(
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val dimens = GrayoutTheme.dimens
    val shape = RoundedCornerShape(dimens.radius)

    val borderColor by animateColorAsState(
        targetValue = if (isActive) colors.accent.copy(alpha = 0.27f) else colors.border,
        animationSpec = tween(300),
        label = "cardBorder",
    )
    val gradientStart by animateColorAsState(
        targetValue = if (isActive) colors.accentDim else colors.surface,
        animationSpec = tween(300),
        label = "gradientStart",
    )

    Box(
        modifier = modifier
            .border(1.dp, borderColor, shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(gradientStart, colors.surface),
                ),
                shape = shape,
            )
            .clip(shape),
    ) {
        content()
    }
}
