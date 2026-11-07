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
    val tightGap: Dp = 8.dp,
    val microGap: Dp = 4.dp,
    val radius: Dp = 16.dp,
    val radiusSm: Dp = 10.dp,
    val radiusFull: Dp = 999.dp,
    val minTouch: Dp = 48.dp,
    val navBarPadV: Dp = 12.dp,
    val dotSize: Dp = 10.dp,
    val stripHeight: Dp = 28.dp,
    val stripLaneMin: Dp = 16.dp,
    val stripGap: Dp = 2.dp,
    val stripMarker: Dp = 2.dp,
    val iconSm: Dp = 16.dp,
    val iconMd: Dp = 20.dp,
    val stepSize: Dp = 24.dp,
    val iconLg: Dp = 24.dp,
    val navIcon: Dp = 22.dp,
    val navPillWidth: Dp = 52.dp,
    val navPillHeight: Dp = 30.dp,
    val borderThick: Dp = 2.dp,
    val radiusXs: Dp = 4.dp,
    val appIcon: Dp = 40.dp,
    val iconButton: Dp = 40.dp,
    val dayCircle: Dp = 40.dp,
    val presetPadH: Dp = 14.dp,
    val presetPadV: Dp = 6.dp,
    val dragHandleWidth: Dp = 32.dp,
    val dragHandleHeight: Dp = 4.dp,
    val toggleWidth: Dp = 56.dp,
    val toggleHeight: Dp = 31.dp,
    val toggleThumb: Dp = 25.dp,
    val toggleInset: Dp = 3.dp,
)

val LocalGrayoutDimens = staticCompositionLocalOf { GrayoutDimens() }
