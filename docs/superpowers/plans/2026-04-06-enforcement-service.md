# Enforcement Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a foreground service that periodically re-enables grayscale when disabled externally, controlled via interval chips on the Home screen.

**Architecture:** EnforcementPrefs stores the interval in SharedPreferences. GrayoutService runs a Handler-based loop to check/re-enable grayscale. HomeViewModel exposes interval state; the Activity layer observes changes and restarts the service with updated Intents. UI adds an enforcement chip card to HomeScreen.

**Tech Stack:** Kotlin, Jetpack Compose, Android Foreground Service, Handler, SharedPreferences, StateFlow

---

### Task 1: Create EnforcementPrefs

**Files:**
- Create: `app/src/main/java/com/princeyadav/grayout/service/EnforcementPrefs.kt`

- [ ] **Step 1: Create EnforcementPrefs class**

```kotlin
package com.princeyadav.grayout.service

import android.content.SharedPreferences

class EnforcementPrefs(private val prefs: SharedPreferences) {

    fun getInterval(): Int =
        prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL)

    fun setInterval(minutes: Int) {
        prefs.edit().putInt(KEY_INTERVAL, minutes).apply()
    }

    companion object {
        private const val KEY_INTERVAL = "enforcement_interval_minutes"
        private const val DEFAULT_INTERVAL = 0
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
feat(service): add EnforcementPrefs for interval storage
```

---

### Task 2: Create GrayoutService

**Files:**
- Create: `app/src/main/java/com/princeyadav/grayout/service/GrayoutService.kt`

- [ ] **Step 1: Create GrayoutService class**

```kotlin
package com.princeyadav.grayout.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.princeyadav.grayout.R

class GrayoutService : Service() {

    private lateinit var grayscaleManager: GrayscaleManager
    private lateinit var enforcementPrefs: EnforcementPrefs
    private val handler = Handler(Looper.getMainLooper())
    private var currentInterval = 0

    private val enforcementRunnable = object : Runnable {
        override fun run() {
            if (!grayscaleManager.isGrayscaleEnabled()) {
                grayscaleManager.setGrayscale(true)
            }
            handler.postDelayed(this, currentInterval * 60_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        grayscaleManager = GrayscaleManager(contentResolver)
        enforcementPrefs = EnforcementPrefs(
            getSharedPreferences("grayout_prefs", MODE_PRIVATE)
        )
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val interval = intent?.getIntExtra(EXTRA_INTERVAL, -1)
            ?.takeIf { it >= 0 }
            ?: enforcementPrefs.getInterval()

        currentInterval = interval
        handler.removeCallbacks(enforcementRunnable)

        if (interval > 0) {
            handler.post(enforcementRunnable)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(interval))

        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(enforcementRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Grayout Service",
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(interval: Int) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_grayout_foreground)
            .setContentTitle("Grayout")
            .setContentText(
                if (interval > 0) "Enforcing grayscale every ${interval}m"
                else "Grayout is idle"
            )
            .setOngoing(true)
            .build()

    companion object {
        const val CHANNEL_ID = "grayout_service"
        const val NOTIFICATION_ID = 1
        const val EXTRA_INTERVAL = "enforcement_interval_minutes"
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
feat(service): add GrayoutService foreground enforcement loop
```

---

### Task 3: Update AndroidManifest

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add permissions before `<application>` tag**

Add after the existing `WRITE_SECURE_SETTINGS` permission (line 6):

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

- [ ] **Step 2: Register GrayoutService inside `<application>` tag**

Add after the `</activity>` closing tag (after line 27), before `</application>`:

```xml
<service
    android:name=".service.GrayoutService"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Enforces screen grayscale on interval to reduce phone usage" />
</service>
```

- [ ] **Step 3: Build to verify manifest merges correctly**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
feat(service): add foreground service permissions and manifest entry
```

---

### Task 4: Update HomeViewModel

**Files:**
- Modify: `app/src/main/java/com/princeyadav/grayout/viewmodel/HomeViewModel.kt`

- [ ] **Step 1: Replace the entire file with updated ViewModel**

```kotlin
package com.princeyadav.grayout.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.GrayscaleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel(
    private val grayscaleManager: GrayscaleManager,
    private val enforcementPrefs: EnforcementPrefs,
) : ViewModel() {

    private val _isGrayscaleOn = MutableStateFlow(false)
    val isGrayscaleOn: StateFlow<Boolean> = _isGrayscaleOn.asStateFlow()

    private val _enforcementInterval = MutableStateFlow(0)
    val enforcementInterval: StateFlow<Int> = _enforcementInterval.asStateFlow()

    init {
        _isGrayscaleOn.value = grayscaleManager.isGrayscaleEnabled()
        _enforcementInterval.value = enforcementPrefs.getInterval()
    }

    fun toggleGrayscale() {
        val newValue = !_isGrayscaleOn.value
        grayscaleManager.setGrayscale(newValue)
        _isGrayscaleOn.value = newValue
        if (!newValue) {
            setEnforcementInterval(0)
        }
    }

    fun setEnforcementInterval(minutes: Int) {
        enforcementPrefs.setInterval(minutes)
        _enforcementInterval.value = minutes
    }
}

class HomeViewModelFactory(
    private val grayscaleManager: GrayscaleManager,
    private val enforcementPrefs: EnforcementPrefs,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(grayscaleManager, enforcementPrefs) as T
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD FAILURE — `MainActivity.kt` still passes only `GrayscaleManager` to the factory. This is expected; Task 6 fixes it.

- [ ] **Step 3: Commit**

```
feat(viewmodel): add enforcement interval state to HomeViewModel
```

---

### Task 5: Add Enforcement Card to HomeScreen

**Files:**
- Modify: `app/src/main/java/com/princeyadav/grayout/ui/screens/HomeScreen.kt`

- [ ] **Step 1: Update HomeScreen signature and layout**

Replace the `HomeScreen` composable (lines 44–81) with:

```kotlin
@Composable
fun HomeScreen(
    isGrayscaleOn: Boolean,
    enforcementInterval: Int,
    onToggle: () -> Unit,
    onEnforcementIntervalChange: (Int) -> Unit,
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

        EnforcementCard(
            enforcementInterval = enforcementInterval,
            isGrayscaleOn = isGrayscaleOn,
            onIntervalChange = onEnforcementIntervalChange,
        )

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
```

- [ ] **Step 2: Add EnforcementCard composable**

Add after the `StatCardsRow` composable (after line 275), before the file ends:

```kotlin
@Composable
private fun EnforcementCard(
    enforcementInterval: Int,
    isGrayscaleOn: Boolean,
    onIntervalChange: (Int) -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    val alpha = if (isGrayscaleOn) 1f else 0.4f

    GrayoutCard(modifier = Modifier.alpha(alpha)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.cardPad),
        ) {
            Text(
                text = "ENFORCEMENT",
                style = typography.labelSmall,
                color = colors.accent,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Re-enable grayscale automatically",
                style = typography.labelSmall,
                color = colors.textMuted,
            )

            Spacer(modifier = Modifier.height(dimens.sectionGap))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(dimens.chipGap),
                verticalArrangement = Arrangement.spacedBy(dimens.chipGap),
            ) {
                val options = listOf(
                    0 to "Off",
                    1 to "1m",
                    5 to "5m",
                    10 to "10m",
                    15 to "15m",
                    30 to "30m",
                )
                options.forEach { (value, label) ->
                    EnforcementChip(
                        label = label,
                        isActive = enforcementInterval == value,
                        onClick = { if (isGrayscaleOn) onIntervalChange(value) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EnforcementChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    val bg by animateColorAsState(
        targetValue = if (isActive) colors.accentDim else Color.Transparent,
        animationSpec = tween(300),
        label = "chipBg",
    )
    val textColor by animateColorAsState(
        targetValue = if (isActive) colors.accent else colors.textMuted,
        animationSpec = tween(300),
        label = "chipText",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) colors.accent.copy(alpha = 0.33f) else colors.border,
        animationSpec = tween(300),
        label = "chipBorder",
    )

    Text(
        text = label,
        style = typography.bodySmall,
        color = textColor,
        modifier = Modifier
            .background(bg, RoundedCornerShape(dimens.radiusFull))
            .border(1.dp, borderColor, RoundedCornerShape(dimens.radiusFull))
            .clip(RoundedCornerShape(dimens.radiusFull))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
```

- [ ] **Step 3: Add required imports at the top of the file**

Add these imports (alongside existing ones):

```kotlin
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
```

- [ ] **Step 4: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD FAILURE — `NavGraph.kt` still calls `HomeScreen` with old signature. This is expected; Task 6 fixes it.

- [ ] **Step 5: Commit**

```
feat(home): add enforcement interval chip card to HomeScreen
```

---

### Task 6: Wire NavGraph and MainActivity

**Files:**
- Modify: `app/src/main/java/com/princeyadav/grayout/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/princeyadav/grayout/MainActivity.kt`

- [ ] **Step 1: Update NavGraph Home route**

Replace the `composable(Routes.HOME)` block (lines 37–42) with:

```kotlin
composable(Routes.HOME) {
    val isGrayscaleOn by homeViewModel.isGrayscaleOn.collectAsStateWithLifecycle()
    val enforcementInterval by homeViewModel.enforcementInterval.collectAsStateWithLifecycle()
    HomeScreen(
        isGrayscaleOn = isGrayscaleOn,
        enforcementInterval = enforcementInterval,
        onToggle = homeViewModel::toggleGrayscale,
        onEnforcementIntervalChange = homeViewModel::setEnforcementInterval,
    )
}
```

- [ ] **Step 2: Replace MainActivity with updated wiring**

Replace the full content of `MainActivity.kt` with:

```kotlin
package com.princeyadav.grayout

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.GrayscaleManager
import com.princeyadav.grayout.service.GrayoutService
import com.princeyadav.grayout.ui.navigation.GrayoutNavGraph
import com.princeyadav.grayout.ui.theme.GrayoutTheme
import com.princeyadav.grayout.viewmodel.HomeViewModel
import com.princeyadav.grayout.viewmodel.HomeViewModelFactory
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val enforcementPrefs by lazy {
        EnforcementPrefs(getSharedPreferences("grayout_prefs", MODE_PRIVATE))
    }

    private val homeViewModel: HomeViewModel by viewModels {
        HomeViewModelFactory(
            GrayscaleManager(applicationContext.contentResolver),
            enforcementPrefs,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        startForegroundService(Intent(this, GrayoutService::class.java))

        lifecycleScope.launch {
            homeViewModel.enforcementInterval
                .drop(1)
                .collect { interval ->
                    val intent = Intent(this@MainActivity, GrayoutService::class.java)
                        .putExtra(GrayoutService.EXTRA_INTERVAL, interval)
                    startForegroundService(intent)
                }
        }

        setContent {
            GrayoutTheme {
                val navController = rememberNavController()
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = GrayoutTheme.colors.bg,
                ) { innerPadding ->
                    GrayoutNavGraph(
                        navController = navController,
                        homeViewModel = homeViewModel,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build to verify everything compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
feat(service): wire enforcement service to activity and nav graph
```

---

### Task 7: Manual Verification

No code changes. Run through the verification checklist on a device/emulator.

- [ ] **Step 1: Install and launch**

Run: `./gradlew installDebug`
Verify: App launches, persistent notification appears showing "Grayout is idle"

- [ ] **Step 2: Test enforcement activation**

In app: Toggle grayscale ON, select "5m" chip.
Verify: Notification updates to "Enforcing grayscale every 5m". Active chip shows `accentDim` background with `accent` text.

- [ ] **Step 3: Test enforcement behavior**

Run via ADB: `adb shell settings put secure accessibility_display_daltonizer_enabled 0`
Wait up to 5 minutes.
Verify: Grayscale re-enables automatically.

- [ ] **Step 4: Test enforcement off**

In app: Select "Off" chip.
Verify: Notification shows "Grayout is idle". Grayscale no longer auto-re-enables after manual disable.

- [ ] **Step 5: Test grayscale toggle off resets enforcement**

In app: Toggle grayscale ON, select "10m" chip, then toggle grayscale OFF.
Verify: Enforcement chips become disabled (0.4f opacity), interval resets to Off.

- [ ] **Step 6: Test persistence**

Kill and reopen app.
Verify: Previously selected interval persists (if grayscale is still on).

- [ ] **Step 7: Verify theme compliance**

Check: All chip colors, card backgrounds, typography, and spacing use theme tokens from DESIGN.md. No hardcoded values.
