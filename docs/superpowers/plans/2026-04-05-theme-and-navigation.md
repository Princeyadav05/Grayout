# Theme & Navigation Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Set up the Compose theme system from DESIGN.md tokens and wire up the navigation shell with placeholder screens.

**Architecture:** Custom `GrayoutColors`, `GrayoutTypography`, and `GrayoutDimens` classes provided via `CompositionLocal`, wrapped in a `GrayoutTheme` object for convenient access. Navigation uses Jetpack Navigation Compose with a simple `NavHost`.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose, DM Sans font, JetBrains Mono font

---

### Task 1: Create Color.kt with all design tokens

**Files:**
- Modify: `app/src/main/java/com/princeyadav/grayout/ui/theme/Color.kt`

- [ ] **Step 1: Replace Color.kt with design system colors**

Replace the entire file with:

```kotlin
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
val Accent = Color(0xFFA0D2C6)
val AccentDim = Color(0xFF2A3D38)
val Off = Color(0xFF3A3A3A)
val OffText = Color(0xFF666666)
val Danger = Color(0xFFFF6B6B)
val Success = Color(0xFF6BE8A0)

@Immutable
data class GrayoutColors(
    val bg: Color = Bg,
    val surface: Color = Surface,
    val border: Color = Border,
    val borderActive: Color = BorderActive,
    val text: Color = TextPrimary,
    val textMuted: Color = TextMuted,
    val textDim: Color = TextDim,
    val accent: Color = Accent,
    val accentDim: Color = AccentDim,
    val off: Color = Off,
    val offText: Color = OffText,
    val danger: Color = Danger,
    val success: Color = Success,
)

val LocalGrayoutColors = staticCompositionLocalOf { GrayoutColors() }
```

### Task 2: Create Type.kt with typography scale

**Files:**
- Modify: `app/src/main/java/com/princeyadav/grayout/ui/theme/Type.kt`

DM Sans is a Google Font available on Android. We'll load it via `GoogleFont` provider to avoid bundling font files. JetBrains Mono we'll also load via Google Fonts since it's available there.

- [ ] **Step 1: Replace Type.kt with design system typography**

Replace the entire file with:

```kotlin
package com.princeyadav.grayout.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
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

val DmSansFamily = FontFamily(
    Font(googleFont = GoogleFont("DM Sans"), fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("DM Sans"), fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("DM Sans"), fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = GoogleFont("DM Sans"), fontProvider = fontProvider, weight = FontWeight.Bold),
)

val JetBrainsMonoFamily = FontFamily(
    Font(googleFont = GoogleFont("JetBrains Mono"), fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("JetBrains Mono"), fontProvider = fontProvider, weight = FontWeight.Bold),
)

@Immutable
data class GrayoutTypography(
    val headingLarge: TextStyle = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp,
    ),
    val headingMedium: TextStyle = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = (-0.5).sp,
    ),
    val headingSmall: TextStyle = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = (-0.5).sp,
    ),
    val titleMedium: TextStyle = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
    ),
    val bodyLarge: TextStyle = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    ),
    val bodyMedium: TextStyle = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
    val bodySmall: TextStyle = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
    ),
    val labelSmall: TextStyle = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp,
    ),
    val labelXSmall: TextStyle = TextStyle(
        fontFamily = DmSansFamily,
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
```

**Note:** Google Fonts provider requires adding the `androidx.compose.ui:ui-text-google-fonts` dependency and the font certs resource. Steps for this are in Task 4 (Theme.kt wiring).

### Task 3: Create Dimens.kt with spacing and radius tokens

**Files:**
- Create: `app/src/main/java/com/princeyadav/grayout/ui/theme/Dimens.kt`

- [ ] **Step 1: Create Dimens.kt**

```kotlin
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
```

### Task 4: Wire up Theme.kt and add dependencies

**Files:**
- Modify: `app/src/main/java/com/princeyadav/grayout/ui/theme/Theme.kt`
- Modify: `gradle/libs.versions.toml` (add google-fonts dependency)
- Modify: `app/build.gradle.kts` (add google-fonts dependency)
- Create: `app/src/main/res/values/font_certs.xml` (Google Fonts provider certs)

- [ ] **Step 1: Add google-fonts dependency to version catalog**

In `gradle/libs.versions.toml`, add to `[libraries]`:

```toml
androidx-compose-ui-text-google-fonts = { group = "androidx.compose.ui", name = "ui-text-google-fonts" }
```

(No version needed — it's managed by the Compose BOM.)

- [ ] **Step 2: Add dependency to build.gradle.kts**

In `app/build.gradle.kts`, add to `dependencies`:

```kotlin
implementation(libs.androidx.compose.ui.text.google.fonts)
```

- [ ] **Step 3: Create font_certs.xml**

Create `app/src/main/res/values/font_certs.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <array name="com_google_android_gms_fonts_certs">
        <item>@array/com_google_android_gms_fonts_certs_dev</item>
        <item>@array/com_google_android_gms_fonts_certs_prod</item>
    </array>
    <string-array name="com_google_android_gms_fonts_certs_dev">
        <item>
            MIIEqDCCA5CgAwIBAgIJANWFuGx90071MA0GCSqGSIb3DQEBBAUAMIGUMQswCQYD
            VQQGEwJVUzETMBEGA1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMNTW91bnRhaW4g
            VmlldzEQma4GA1UEChMGR29vZ2xlMRAwDgYDVQQLEwdBbmRyb2lkMRAwDgYDVQQD
            EwdBbmRyb2lkMSIwIAYJKoZIhvcNAQkBFhNhbmRyb2lkQGFuZHJvaWQuY29tMB4X
            DTA4MDgyMTIzMTMzNFoXDTM2MDEwNzIzMTMzNFowgZQxCzAJBgNVBAYTAlVTMRMw
            EQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1Nb3VudGFpbiBWaWV3MRAwDgYD
            VQQKEwdBbmRyb2lkMRAwDgYDVQQLEwdBbmRyb2lkMRAwDgYDVQQDEwdBbmRyb2lk
            MSIwIAYJKoZIhvcNAQkBFhNhbmRyb2lkQGFuZHJvaWQuY29tMIIBuDCCASwGByqG
            SM44BAEwggEfAoGBANnI3EdmCFSJEhBEhGCwgg0yK78MSTpjEGXAMI0qKxBm6Mxp
            yOjINIpHgN3HcFafHDLA7BpxI1+vAJbl+JhAtJVJbRaBJBD0xaerOLJI+cykF9LR
            M2i8m32vGKKr3sN5fIKBuJRQQCacgq1EOMFNxCM0fGCFhFUSPSiSj8MtHaMLAgMB
            AAGjgfwwgfkwHQYDVR0OBBYEFEhZAFY9JqFjMN8G1q77kmrcfAhcMIHJBgNVHSME
            gcEwgb6AFEhZAFY9JqFjMN8G1q77kmrcfAhcoYGapIGXMIGUMQswCQYDVQQGEwJV
            UzETMBEGA1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMNTW91bnRhaW4gVmlldzEQ
            MA4GA1UEChMHQW5kcm9pZDEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5k
            cm9pZDEiMCAGCSqGSIb3DQEJARYTYWukcm9pZEBhbmRyb2lkLmNvbYIJANWFuGx9
            0071MAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQEEBQADgYEBt0lYKzz1Zqrp8zyH
            XxoNJKFq3yJgs2amjBEB3eqMef3WYHTGjfljzT5SB1MStEq8hQWdqTfiDzE3Kns3
            ksXYgS7AAhj3HtG0yWDBkDBg95ygao8bm4hk7UMDlQ8Uukmgg3S7pfPOktML2Vcl
            UxNb9zhXzfMlV5Tnj9TyJGo=
        </item>
    </string-array>
    <string-array name="com_google_android_gms_fonts_certs_prod">
        <item>
            MIIEQzCCAyugAwIBAgIJAMLgh0ZkSjCNMA0GCSqGSIb3DQEBBAUAMHQxCzAJBgNV
            BAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1Nb3VudGFpbiBW
            aWV3MRQwEgYDVQQKEwtHb29nbGUgSW5jLjEQMA4GA1UECxMHQW5kcm9pZDEQMA4G
            A1UEAxMHQW5kcm9pZDAeFw0wODA4MjEyMzEzMzRaFw0zNjAxMDcyMzEzMzRaMHQx
            CzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1Nb3Vu
            dGFpbiBWaWV3MRQwEgYDVQQKEwtHb29nbGUgSW5jLjEQMA4GA1UECxMHQW5kcm9p
            ZDEQMA4GA1UEAxMHQW5kcm9pZDCCASAwDQYJKoZIhvcNAQEBBQADggENADCCAQgC
            ggEBAKtWLgDYO6IIrgqWbxJOKdoR8qtW0I9Y4sypEwPpt1TTcvZApxsdyxMJZ2Ju
            RWL1xx2lk2TnurEGCPYdqcImYRrPdsz+06I79TY+cEYFoN+ZNsnNDoDcMdGAb9jx
            FZ2qcHwTO3dO7F/ImQ/dPK/3Ph2jM2g/mH0iGiEcJxHUjFMHuLFoiDHtLiGHYsW0
            KsJJ4kcmB4YhMOjJzktCAcPKkakqiqhGBRSKLse+gy+oMgp1Xd0Gp5g3tgIb2F2
            /8fOGVB9kp0dNoGFPkgSKi7RIXOIW5c+JTJ4za6OvDVb4TNT8I3NOKKGvIpFvAR
            B9VBHM5GBR1dNjdMSATxIFuHPadagMECAwEAAaOBnDCBmTAdBgNVHQ4EFgQUhYJb
            RJPa3UNmv0aj+tqvt8f2k0YwagYDVR0jBGMwYYAUhYJbRJPa3UNmv0aj+tqvt8f2
            k0ahbKRaMFgxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRQwEgYD
            VQQKDAtHb29nbGUgSW5jLjEQMA4GA1UECxMHQW5kcm9pZDEMMAoGA1UEAxMDZGV2
            ggkAy4TH1Lu4xrEwDAYDVR0TBAUwAwEB/zANBgkqhkiG9w0BAQQFAAOCAQEAmrFQ
            kVSO0sNIN8c2BZKBkoNJ1JBo3fMTyqnj9OI7CmHbmqFNfDXl6h3dODJFkImZfGV
            9c2FIbPERAGH0wrI8Rz+lkVEISh5QhFIfp8vmBVVEepSBPixRHxJppN+XxkREcNZ
            R5p0GcrLKec6RyUekZIHqTM5roLPRNlMtG7+bbMFicO2RyYMD4InnJHmKN7oLFhJ
            dlkp7/Eq6sACp5MdpfOdrsLz1bpn1fCf+wlDJ1/bG7OKn3HqSA8CjYWbGZVHOY8
            Bfu7l3LBdBPYtq1dFd7ECpJ7hK5anDpFG9WACN3V/0NjTRQ9oPhnHRqHzCzYaJpk
            DGxzmARWlSNBi/MEbg==
        </item>
    </string-array>
</resources>
```

- [ ] **Step 4: Replace Theme.kt**

```kotlin
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
```

- [ ] **Step 5: Build to verify theme compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

Use `/commit-push` skill. Message: "set up compose theme from design system"

### Task 5: Add navigation shell with placeholder screens

**Files:**
- Modify: `gradle/libs.versions.toml` (add navigation-compose)
- Modify: `app/build.gradle.kts` (add navigation-compose dependency)
- Modify: `app/src/main/java/com/princeyadav/grayout/MainActivity.kt`
- Create: `app/src/main/java/com/princeyadav/grayout/ui/navigation/NavGraph.kt`

- [ ] **Step 1: Add navigation-compose to version catalog**

In `gradle/libs.versions.toml`, add to `[versions]`:

```toml
navigationCompose = "2.8.9"
```

And to `[libraries]`:

```toml
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
```

- [ ] **Step 2: Add dependency to build.gradle.kts**

In `app/build.gradle.kts`, add to `dependencies`:

```kotlin
implementation(libs.androidx.navigation.compose)
```

- [ ] **Step 3: Create NavGraph.kt with routes and placeholder screens**

Create `app/src/main/java/com/princeyadav/grayout/ui/navigation/NavGraph.kt`:

```kotlin
package com.princeyadav.grayout.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.princeyadav.grayout.ui.theme.GrayoutTheme

object Routes {
    const val HOME = "home"
    const val SCHEDULES = "schedules"
    const val SCHEDULE_EDITOR = "schedule_editor"
    const val SETTINGS = "settings"
}

@Composable
fun GrayoutNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) { PlaceholderScreen("Home") }
        composable(Routes.SCHEDULES) { PlaceholderScreen("Schedules") }
        composable(Routes.SCHEDULE_EDITOR) { PlaceholderScreen("Schedule Editor") }
        composable(Routes.SETTINGS) { PlaceholderScreen("Settings") }
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GrayoutTheme.colors.bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            style = GrayoutTheme.typography.headingLarge,
            color = GrayoutTheme.colors.text,
        )
    }
}
```

- [ ] **Step 4: Update MainActivity.kt**

Replace the entire file:

```kotlin
package com.princeyadav.grayout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.princeyadav.grayout.ui.navigation.GrayoutNavGraph
import com.princeyadav.grayout.ui.theme.GrayoutTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GrayoutTheme {
                val navController = rememberNavController()
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = GrayoutTheme.colors.bg,
                ) { innerPadding ->
                    GrayoutNavGraph(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 5: Build to verify navigation compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

Use `/commit-push` skill. Message: "add navigation shell with placeholder screens"
