# Enforcement Service — Design Spec

Step 4 of the Grayout build: foreground service that periodically re-enables grayscale if disabled externally.

---

## 1. EnforcementPrefs

**File:** `service/EnforcementPrefs.kt`

Thin SharedPreferences wrapper.

- Constructor takes `SharedPreferences`
- `getInterval(): Int` — reads `"enforcement_interval_minutes"`, default `0`
- `setInterval(minutes: Int)` — writes the value
- Valid values: `0` (off), `1`, `5`, `10`, `15`, `30`

No validation — the UI only offers valid options.

---

## 2. GrayoutService

**File:** `service/GrayoutService.kt`

Foreground service with a Handler-based enforcement loop.

### Lifecycle

- **`onCreate()`**: Create notification channel (`"grayout_service"`, name `"Grayout Service"`, importance LOW). Start foreground with initial notification.
- **`onStartCommand()`**: Read interval from Intent extra `"enforcement_interval_minutes"`. If absent, read from `EnforcementPrefs`. Cancel existing Handler callbacks. If interval > 0, start the postDelayed loop. Update notification text.
- **`onDestroy()`**: Remove all Handler callbacks.

### Enforcement Loop

- Handler on main looper
- Posts a Runnable that:
  1. Checks `GrayscaleManager.isGrayscaleEnabled()`
  2. If false, calls `setGrayscale(true)`
  3. Re-posts itself with `interval * 60_000L` delay
- First check runs immediately (no initial delay)

### Notification

- Channel importance: LOW (no sound/vibration)
- Interval > 0: `"Enforcing grayscale every Xm"`
- Interval == 0: `"Grayout is idle"`
- Small icon: `ic_grayout_foreground`

### Lifecycle Policy

Service stays alive when interval is 0 — no `stopSelf()`. Notification shows idle state.

---

## 3. Manifest Changes

### Permissions

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### Service Registration

```xml
<service
    android:name=".service.GrayoutService"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Enforces screen grayscale on interval to reduce phone usage" />
</service>
```

`specialUse` is correct — grayscale enforcement doesn't fit predefined FGS categories.

---

## 4. HomeViewModel Updates

**File:** `viewmodel/HomeViewModel.kt`

### Constructor

Add `EnforcementPrefs` as second parameter alongside `GrayscaleManager`.

### New State

- `_enforcementInterval: MutableStateFlow<Int>` — initialized from `EnforcementPrefs.getInterval()`
- `enforcementInterval: StateFlow<Int>` — public read-only

### New Method

- `setEnforcementInterval(minutes: Int)` — saves to prefs, updates StateFlow

### Modified toggleGrayscale()

When toggling OFF, also calls `setEnforcementInterval(0)` to reset enforcement.

### Factory

`HomeViewModelFactory` updated to accept both `GrayscaleManager` and `EnforcementPrefs`.

### Service Communication

The ViewModel does not talk to the service directly. It updates prefs and the StateFlow. The Activity layer observes the interval and restarts the service with an updated Intent. This keeps the ViewModel free of Android framework dependencies.

---

## 5. HomeScreen UI — Enforcement Card

**File:** `ui/screens/HomeScreen.kt`

New composable between the main toggle card and the stat cards row.

### Card Contents (inside GrayoutCard)

1. `"ENFORCEMENT"` label — `labelSmall`, `accent`, uppercase
2. `"Re-enable grayscale automatically"` subtitle — `labelSmall`, `textMuted`
3. `Spacer(sectionGap)`
4. Row of 6 selectable chips: `"Off"`, `"1m"`, `"5m"`, `"10m"`, `"15m"`, `"30m"`

### Chip Styling

| State    | Background       | Text        | Border                    |
|----------|------------------|-------------|---------------------------|
| Active   | `accentDim`      | `accent`    | `accent` at 33% opacity   |
| Inactive | `Transparent`    | `textMuted` | `border`                  |

- Shape: `radiusFull` (pill)
- Padding: 6dp vertical, 14dp horizontal
- Gap: `chipGap` (8dp)
- Text style: `bodySmall` (13sp SemiBold)

### Disabled State (grayscale OFF)

- Entire card at `0.4f` alpha
- Taps ignored on chips

### Chip Value Mapping

`"Off"` → 0, `"1m"` → 1, `"5m"` → 5, `"10m"` → 10, `"15m"` → 15, `"30m"` → 30

### HomeScreen Signature

New params: `enforcementInterval: Int`, `onEnforcementIntervalChange: (Int) -> Unit`

---

## 6. MainActivity Wiring

**File:** `MainActivity.kt`

### onCreate()

- Create `EnforcementPrefs` with `getSharedPreferences("grayout_prefs", MODE_PRIVATE)`
- Pass to `HomeViewModelFactory` alongside `GrayscaleManager`
- Call `startForegroundService(Intent(this, GrayoutService::class.java))` to launch service

### Service Restart on Interval Change

- Collect `homeViewModel.enforcementInterval` in a `lifecycleScope`
- On each emission, call `startForegroundService()` with Intent extra `"enforcement_interval_minutes"`
- Use `drop(1)` to skip the initial emission (service reads from prefs on first start)

### NavGraph

Pass `enforcementInterval` state and `onEnforcementIntervalChange` lambda through to `HomeScreen`.

---

## Verification Checklist

1. App launches → persistent notification appears
2. Toggle grayscale ON → select "5m" chip → notification shows "Enforcing grayscale every 5m"
3. Manually disable grayscale via ADB → wait → grayscale re-enables within 5 minutes
4. Select "Off" chip → enforcement stops, grayscale no longer auto-re-enables
5. Toggle grayscale OFF → enforcement resets to Off, chips become disabled (0.4f opacity)
6. Kill and reopen app → interval setting persists
7. All UI uses theme tokens, chips match DESIGN.md chip pattern
