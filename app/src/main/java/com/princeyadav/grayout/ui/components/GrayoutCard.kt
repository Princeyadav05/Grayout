package com.princeyadav.grayout.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.ui.theme.GrayoutMotion
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
        targetValue = if (isActive) colors.borderActive else colors.border,
        animationSpec = tween(
            durationMillis = GrayoutMotion.Slow,
            easing = GrayoutMotion.Easing,
        ),
        label = "cardBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isActive) 2.dp else 1.dp,
        animationSpec = tween(
            durationMillis = GrayoutMotion.Slow,
            easing = GrayoutMotion.Easing,
        ),
        label = "cardBorderWidth",
    )

    Box(
        modifier = modifier
            .border(borderWidth, borderColor, shape)
            .background(colors.surface, shape)
            .clip(shape),
    ) {
        content()
    }
}
