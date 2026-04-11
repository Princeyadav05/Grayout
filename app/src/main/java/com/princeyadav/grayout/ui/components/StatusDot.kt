package com.princeyadav.grayout.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.ui.theme.GrayoutTheme

@Composable
fun StatusDot(
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = GrayoutTheme.colors
    Box(
        modifier = modifier
            .size(12.dp)
            .background(
                if (isActive) colors.text else Color.Transparent,
                CircleShape,
            )
            .border(
                width = if (isActive) 0.dp else 1.5.dp,
                color = if (isActive) Color.Transparent else colors.borderActive,
                shape = CircleShape,
            ),
    )
}
