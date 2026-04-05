package com.princeyadav.grayout.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Bg,
    secondary = AccentDim,
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Surface,
    onSurfaceVariant = TextMuted,
    outline = Border,
    outlineVariant = BorderActive,
    error = Danger,
    onError = Bg,
)

object GrayoutTheme {
    val colors: GrayoutColors
        @Composable @ReadOnlyComposable
        get() = LocalGrayoutColors.current

    val typography: GrayoutTypography
        @Composable @ReadOnlyComposable
        get() = LocalGrayoutTypography.current

    val dimens: GrayoutDimens
        @Composable @ReadOnlyComposable
        get() = LocalGrayoutDimens.current
}

@Composable
fun GrayoutTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalGrayoutColors provides GrayoutColors(),
        LocalGrayoutTypography provides GrayoutTypography(),
        LocalGrayoutDimens provides GrayoutDimens(),
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            content = content,
        )
    }
}
