package com.princeyadav.grayout.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.princeyadav.grayout.R

val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

val PlusJakartaSansFamily = FontFamily(
    Font(googleFont = GoogleFont("Plus Jakarta Sans"), fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Plus Jakarta Sans"), fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("Plus Jakarta Sans"), fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = GoogleFont("Plus Jakarta Sans"), fontProvider = fontProvider, weight = FontWeight.Bold),
    Font(googleFont = GoogleFont("Plus Jakarta Sans"), fontProvider = fontProvider, weight = FontWeight.ExtraBold),
)

val JetBrainsMonoFamily = FontFamily(
    Font(googleFont = GoogleFont("JetBrains Mono"), fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("JetBrains Mono"), fontProvider = fontProvider, weight = FontWeight.Bold),
)

@Immutable
data class GrayoutTypography(
    val headingLarge: TextStyle = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp,
    ),
    val headingMedium: TextStyle = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = (-0.5).sp,
    ),
    val headingSmall: TextStyle = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = (-0.5).sp,
    ),
    val titleMedium: TextStyle = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
    ),
    val bodyLarge: TextStyle = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    ),
    val bodyMedium: TextStyle = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
    val labelSmall: TextStyle = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp,
    ),
    val labelXSmall: TextStyle = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
    ),
    val mono: TextStyle = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
    ),
    val monoSmall: TextStyle = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
)

val LocalGrayoutTypography = staticCompositionLocalOf { GrayoutTypography() }
