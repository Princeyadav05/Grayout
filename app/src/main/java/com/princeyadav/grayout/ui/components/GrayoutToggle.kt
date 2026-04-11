package com.princeyadav.grayout.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.ui.theme.GrayoutMotion
import com.princeyadav.grayout.ui.theme.GrayoutTheme

@Composable
fun GrayoutToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GrayoutTheme.colors

    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.text else colors.off,
        animationSpec = tween(
            durationMillis = GrayoutMotion.Fast,
            easing = GrayoutMotion.Easing,
        ),
        label = "trackColor",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) colors.bg else colors.offText,
        animationSpec = tween(
            durationMillis = GrayoutMotion.Fast,
            easing = GrayoutMotion.Easing,
        ),
        label = "thumbColor",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 25.dp else 0.dp,
        animationSpec = tween(
            durationMillis = GrayoutMotion.Fast,
            easing = GrayoutMotion.Easing,
        ),
        label = "thumbOffset",
    )

    Box(
        modifier = modifier
            .size(width = 56.dp, height = 31.dp)
            .background(trackColor, RoundedCornerShape(percent = 50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(25.dp)
                .background(thumbColor, CircleShape),
        )
    }
}
