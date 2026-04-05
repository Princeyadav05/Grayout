package com.princeyadav.grayout.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class GrayoutDimens(
    val screenPad: Dp = 20.dp,
    val cardPad: Dp = 16.dp,
    val cardPadLarge: Dp = 24.dp,
    val cardGap: Dp = 12.dp,
    val sectionGap: Dp = 16.dp,
    val itemGap: Dp = 8.dp,
    val chipGap: Dp = 8.dp,
    val dayDotGap: Dp = 6.dp,
    val radius: Dp = 16.dp,
    val radiusSm: Dp = 10.dp,
    val radiusFull: Dp = 999.dp,
)

val LocalGrayoutDimens = staticCompositionLocalOf { GrayoutDimens() }
