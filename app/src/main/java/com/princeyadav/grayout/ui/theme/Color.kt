package com.princeyadav.grayout.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0B0B0B)
val Surface = Color(0xFF161616)
val Border = Color(0xFF2A2A2A)
val BorderActive = Color(0xFF555555)
val TextPrimary = Color(0xFFE8E8E8)
val TextMuted = Color(0xFF777777)
val TextDim = Color(0xFF4A4A4A)
val Off = Color(0xFF3A3A3A)
val OffText = Color(0xFF666666)
val Danger = Color(0xFFFF6B6B)

val BrandAccent = Color(0xFFB5A0D8)

@Immutable
data class GrayoutColors(
    val bg: Color = Bg,
    val surface: Color = Surface,
    val border: Color = Border,
    val borderActive: Color = BorderActive,
    val text: Color = TextPrimary,
    val textMuted: Color = TextMuted,
    val textDim: Color = TextDim,
    val off: Color = Off,
    val offText: Color = OffText,
    val danger: Color = Danger,
)

val LocalGrayoutColors = staticCompositionLocalOf { GrayoutColors() }
