package com.princeyadav.grayout.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing

object GrayoutMotion {
    const val Fast = 180              // chip flicks, toggle position, hover, press
    const val Slow = 320              // main-toggle state change, screen transitions, save commit
    val Easing = FastOutSlowInEasing
}
