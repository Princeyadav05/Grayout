# Home Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the placeholder Home screen with the full DESIGN.md Home Screen spec, adapted for MVP (no schedules).

**Architecture:** Stateless `HomeScreen` composable receives `isGrayscaleOn` + `onToggle` from NavGraph, which collects from `HomeViewModel`. Three reusable components (`GrayoutCard`, `GrayoutToggle`, `StatusDot`) live in `ui/components/`. Private sub-composables for Home-specific sections live inside `HomeScreen.kt`.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, `collectAsStateWithLifecycle`

---

### Task 1: Add lifecycle-runtime-compose dependency

**Files:**
- Modify: `gradle/libs.versions.toml:18` (add library entry after existing lifecycle libs)
- Modify: `app/build.gradle.kts:44` (add implementation line)

- [ ] **Step 1: Add library to version catalog**

In `gradle/libs.versions.toml`, add this line after line 19 (`androidx-lifecycle-viewmodel-ktx`):

```toml
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeKtx" }
```

- [ ] **Step 2: Add dependency to app build file**

In `app/build.gradle.kts`, add this line after `implementation(libs.androidx.lifecycle.viewmodel.ktx)` (line 45):

```kotlin
    implementation(libs.androidx.lifecycle.runtime.compose)
```

- [ ] **Step 3: Sync and verify**

Run: `cd /Users/princeyadav/Documents/Prince/Projects/Grayout && ./gradlew :app:dependencies --configuration releaseRuntimeClasspath 2>&1 | grep lifecycle-runtime-compose`

Expected: a line showing `androidx.lifecycle:lifecycle-runtime-compose:2.6.1`

- [ ] **Step 4: Commit**

```
feat(deps): add lifecycle-runtime-compose for collectAsStateWithLifecycle
```

---

### Task 2: Create StatusDot component

**Files:**
- Create: `app/src/main/java/com/princeyadav/grayout/ui/components/StatusDot.kt`

- [ ] **Step 1: Create the component file**

```kotlin
package com.princeyadav.grayout.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.ui.theme.GrayoutTheme

@Composable
fun StatusDot(
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = GrayoutTheme.colors
    val fillColor by animateColorAsState(
        targetValue = if (isActive) colors.success else colors.off,
        animationSpec = tween(300),
        label = "dotColor",
    )
    val glowColor = colors.success.copy(alpha = 0.27f)

    Box(
        modifier = modifier
            .size(8.dp)
            .drawBehind {
                if (isActive) {
                    drawCircle(
                        color = glowColor,
                        radius = size.minDimension / 2 + 8.dp.toPx(),
                    )
                }
            }
            .background(fillColor, CircleShape),
    )
}
```

- [ ] **Step 2: Verify build**

Run: `cd /Users/princeyadav/Documents/Prince/Projects/Grayout && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```
feat(ui): add StatusDot component
```

---

### Task 3: Create GrayoutCard component

**Files:**
- Create: `app/src/main/java/com/princeyadav/grayout/ui/components/GrayoutCard.kt`

- [ ] **Step 1: Create the component file**

```kotlin
package com.princeyadav.grayout.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
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
        targetValue = if (isActive) colors.accent.copy(alpha = 0.27f) else colors.border,
        animationSpec = tween(300),
        label = "cardBorder",
    )
    val gradientStart by animateColorAsState(
        targetValue = if (isActive) colors.accentDim else colors.surface,
        animationSpec = tween(300),
        label = "gradientStart",
    )

    Box(
        modifier = modifier
            .border(1.dp, borderColor, shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(gradientStart, colors.surface),
                ),
                shape = shape,
            )
            .clip(shape),
    ) {
        content()
    }
}
```

- [ ] **Step 2: Verify build**

Run: `cd /Users/princeyadav/Documents/Prince/Projects/Grayout && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```
feat(ui): add GrayoutCard component
```

---

### Task 4: Create GrayoutToggle component

**Files:**
- Create: `app/src/main/java/com/princeyadav/grayout/ui/components/GrayoutToggle.kt`

- [ ] **Step 1: Create the component file**

Toggle is 56×31dp. Thumb is 25dp diameter with 3dp internal padding. Thumb travel = 56 − 3 − 3 − 25 = 25dp.

```kotlin
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
import com.princeyadav.grayout.ui.theme.GrayoutTheme

@Composable
fun GrayoutToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GrayoutTheme.colors

    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.accent else colors.off,
        animationSpec = tween(250),
        label = "trackColor",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) colors.bg else colors.offText,
        animationSpec = tween(250),
        label = "thumbColor",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 25.dp else 0.dp,
        animationSpec = tween(250),
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
```

- [ ] **Step 2: Verify build**

Run: `cd /Users/princeyadav/Documents/Prince/Projects/Grayout && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```
feat(ui): add GrayoutToggle component
```

---

### Task 5: Create HomeScreen

**Files:**
- Create: `app/src/main/java/com/princeyadav/grayout/ui/screens/HomeScreen.kt`

- [ ] **Step 1: Create HomeScreen with all sections**

```kotlin
package com.princeyadav.grayout.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.princeyadav.grayout.ui.components.GrayoutCard
import com.princeyadav.grayout.ui.components.GrayoutToggle
import com.princeyadav.grayout.ui.components.StatusDot
import com.princeyadav.grayout.ui.theme.GrayoutTheme

@Composable
fun HomeScreen(
    isGrayscaleOn: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimens.screenPad),
    ) {
        Spacer(modifier = Modifier.height(dimens.sectionGap))

        AppHeader()

        MainToggleCard(isGrayscaleOn = isGrayscaleOn, onToggle = onToggle)

        Spacer(modifier = Modifier.height(dimens.cardGap))

        StatCardsRow(isGrayscaleOn = isGrayscaleOn)

        Spacer(modifier = Modifier.height(dimens.sectionGap))

        Text(
            text = "Schedules coming soon",
            style = typography.bodyMedium,
            color = colors.textMuted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(dimens.sectionGap))
    }
}

@Composable
private fun AppHeader() {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = dimens.sectionGap),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(dimens.radiusSm))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            colors.accentDim,
                            colors.accent.copy(alpha = 0.4f),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "◐",
                fontSize = 20.sp,
                color = colors.text,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "Grayout",
            style = typography.headingSmall,
            color = colors.text,
        )
    }
}

@Composable
private fun MainToggleCard(
    isGrayscaleOn: Boolean,
    onToggle: () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    val circleFill by animateColorAsState(
        targetValue = if (isGrayscaleOn) colors.accentDim else colors.off,
        animationSpec = tween(300),
        label = "circleFill",
    )
    val circleStroke by animateColorAsState(
        targetValue = if (isGrayscaleOn) colors.accent.copy(alpha = 0.33f) else colors.border,
        animationSpec = tween(300),
        label = "circleStroke",
    )
    val iconColor by animateColorAsState(
        targetValue = if (isGrayscaleOn) colors.accent else colors.textDim,
        animationSpec = tween(300),
        label = "iconColor",
    )
    val labelColor by animateColorAsState(
        targetValue = if (isGrayscaleOn) colors.accent else colors.text,
        animationSpec = tween(300),
        label = "labelColor",
    )

    GrayoutCard(isActive = isGrayscaleOn) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.cardPadLarge),
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(circleFill, CircleShape)
                    .border(2.dp, circleStroke, CircleShape)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onToggle() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "◐",
                    fontSize = 40.sp,
                    color = iconColor,
                )
            }

            Spacer(modifier = Modifier.height(dimens.sectionGap))

            Text(
                text = if (isGrayscaleOn) "Grayscale On" else "Grayscale Off",
                style = typography.headingLarge,
                color = labelColor,
            )

            Spacer(modifier = Modifier.height(dimens.itemGap))

            Text(
                text = if (isGrayscaleOn) "Screen colors are turned off"
                    else "Screen colors are normal",
                style = typography.bodyMedium,
                color = colors.textMuted,
            )

            Spacer(modifier = Modifier.height(dimens.sectionGap))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bg, RoundedCornerShape(dimens.radiusSm))
                    .padding(dimens.cardPad),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Enforce Grayscale",
                    style = typography.bodyMedium,
                    color = colors.text,
                )
                GrayoutToggle(
                    checked = isGrayscaleOn,
                    onCheckedChange = { onToggle() },
                )
            }
        }
    }
}

@Composable
private fun StatCardsRow(isGrayscaleOn: Boolean) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    Row(
        horizontalArrangement = Arrangement.spacedBy(dimens.cardGap),
        modifier = Modifier.fillMaxWidth(),
    ) {
        GrayoutCard(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(dimens.cardPad)) {
                Text(
                    text = "STATUS",
                    style = typography.labelSmall,
                    color = colors.textMuted,
                )

                Spacer(modifier = Modifier.height(dimens.itemGap))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(isActive = isGrayscaleOn)

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (isGrayscaleOn) "Active" else "Inactive",
                        style = typography.bodyMedium,
                        color = if (isGrayscaleOn) colors.success else colors.offText,
                    )
                }
            }
        }

        GrayoutCard(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(dimens.cardPad)) {
                Text(
                    text = "NEXT SCHEDULE",
                    style = typography.labelSmall,
                    color = colors.textMuted,
                )

                Spacer(modifier = Modifier.height(dimens.itemGap))

                Text(
                    text = "—",
                    style = typography.mono,
                    color = colors.textDim,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Verify build**

Run: `cd /Users/princeyadav/Documents/Prince/Projects/Grayout && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```
feat(home): add Home screen with toggle card, stat cards, and header
```

---

### Task 6: Wire HomeScreen into NavGraph

**Files:**
- Modify: `app/src/main/java/com/princeyadav/grayout/ui/navigation/NavGraph.kt`

- [ ] **Step 1: Replace placeholder with HomeScreen**

Replace the entire file content with:

```kotlin
package com.princeyadav.grayout.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.princeyadav.grayout.ui.screens.HomeScreen
import com.princeyadav.grayout.ui.theme.GrayoutTheme
import com.princeyadav.grayout.viewmodel.HomeViewModel

object Routes {
    const val HOME = "home"
    const val SCHEDULES = "schedules"
    const val SCHEDULE_EDITOR = "schedule_editor"
    const val SETTINGS = "settings"
}

@Composable
fun GrayoutNavGraph(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            val isGrayscaleOn by homeViewModel.isGrayscaleOn.collectAsStateWithLifecycle()
            HomeScreen(
                isGrayscaleOn = isGrayscaleOn,
                onToggle = homeViewModel::toggleGrayscale,
            )
        }
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

Changes from current version:
- Import `collectAsStateWithLifecycle` instead of `collectAsState`
- Import `HomeScreen` instead of inline composable
- Replace inline Home placeholder with `HomeScreen(isGrayscaleOn, onToggle)` call
- Use `homeViewModel::toggleGrayscale` method reference

- [ ] **Step 2: Verify build**

Run: `cd /Users/princeyadav/Documents/Prince/Projects/Grayout && ./gradlew :app:compileDebugKotlin 2>&1 | tail -5`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```
feat(nav): wire HomeScreen into NavGraph with collectAsStateWithLifecycle
```

---

### Task 7: Build APK and verify on device

- [ ] **Step 1: Build debug APK**

Run: `cd /Users/princeyadav/Documents/Prince/Projects/Grayout && ./gradlew :app:assembleDebug 2>&1 | tail -5`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Install and launch on device**

Run: `adb install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n com.princeyadav.grayout/.MainActivity`

- [ ] **Step 3: Verify grayscale toggle works**

Run after tapping the toggle ON:
`adb shell settings get secure accessibility_display_daltonizer_enabled`

Expected: `1`

Run after tapping the toggle OFF:
`adb shell settings get secure accessibility_display_daltonizer_enabled`

Expected: `0`

- [ ] **Step 4: Visual verification checklist**

Verify on device:
- App header shows ◐ icon in gradient square + "Grayout" text
- Main toggle card shows 120dp circle with ◐ icon, state label, subtitle, inner toggle row
- Tapping circle toggles grayscale — card bg, borders, text colors animate
- Tapping inner toggle also toggles grayscale
- Status card shows green dot + "Active" when on, gray dot + "Inactive" when off
- Next Schedule card shows "—" in monospace
- "Schedules coming soon" text centered below cards
- No hardcoded colors visible (all theme-derived)
