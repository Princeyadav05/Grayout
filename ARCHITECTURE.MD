# Grayout — MVP Architecture

## What the MVP Does

1. **Toggle** — Tap a button, screen goes grayscale (or back to color)
2. **Enforce** — Turn on enforcement mode: every N minutes (5, 10, 15, 30), grayscale is re-applied even if user turned it off
3. **Stop** — Turn off enforcement. Grayscale stays in whatever state it's in.

No schedules, no time windows, no day selection. That's v2.

---

## How Grayscale Works on Android

Two values in `Settings.Secure` control screen color:

```
accessibility_display_daltonizer_enabled = 1 (on) or 0 (off)
accessibility_display_daltonizer = 0 (means grayscale)
```

Write these → screen goes gray. It persists even if app is killed.
Normal apps can't write here. Needs `WRITE_SECURE_SETTINGS` granted via ADB (one-time):

```
adb shell pm grant com.princeyadav.grayout android.permission.WRITE_SECURE_SETTINGS
```

---

## The Pieces (6 total)

### 1. GrayscaleManager — `service/GrayscaleManager.kt`

Plain Kotlin class. Takes `ContentResolver`. Does the actual Settings.Secure reads/writes.
Everything else in the app uses this class to touch grayscale.

```
GrayscaleManager(contentResolver)
  ├── enableGrayscale()        → writes daltonizer_enabled=1, daltonizer=0
  ├── disableGrayscale()       → writes daltonizer_enabled=0
  ├── isGrayscaleEnabled()     → reads daltonizer_enabled, returns Boolean
  ├── toggle()                 → reads state, flips it
  └── hasPermission()          → tries a read, returns false on SecurityException
```

### 2. GrayscaleService — `service/GrayscaleService.kt`

Foreground service. Runs a coroutine loop that calls `enableGrayscale()` every N minutes.
Android keeps it alive because it shows a persistent notification.

```
GrayscaleService
  ├── onStartCommand(intent)
  │     → reads EXTRA_INTERVAL_MINUTES from intent (default 10)
  │     → shows notification via NotificationHelper
  │     → launches coroutine: while(active) { enableGrayscale(); delay(interval) }
  │
  ├── stop()
  │     → cancels coroutine
  │     → removes notification
  │     → calls stopSelf()
  │
  └── Intent actions:
        ACTION_START   — starts the enforcement loop
        ACTION_STOP    — stops everything
```

### 3. NotificationHelper — `service/NotificationHelper.kt`

Creates the notification channel (required on Android 8+) and builds the notification.

```
NotificationHelper(context)
  ├── createChannel()           → creates "grayscale_enforcement" channel (once)
  └── buildNotification(interval) → returns Notification showing "Grayscale active · every N min"
```

### 4. HomeViewModel — `viewmodel/HomeViewModel.kt`

Holds UI state. The screen reads from this, never touches GrayscaleManager directly.

```
HomeViewModel(application)
  ├── grayscaleOn: StateFlow<Boolean>         → is grayscale currently on?
  ├── enforcementActive: StateFlow<Boolean>   → is the service running?
  ├── intervalMinutes: StateFlow<Int>         → current interval (5/10/15/30)
  ├── permissionGranted: StateFlow<Boolean>   → was ADB setup done?
  │
  ├── toggleGrayscale()         → calls GrayscaleManager.toggle()
  ├── startEnforcement(interval) → starts GrayscaleService
  ├── stopEnforcement()          → stops GrayscaleService
  └── setInterval(minutes)       → updates interval, restarts service if running
```

### 5. Home Screen — `ui/HomeScreen.kt`

Pure UI. Reads state from HomeViewModel, calls its functions on user interaction.

```
HomeScreen
  ├── Grayscale toggle (big switch, shows on/off state)
  ├── Enforcement section:
  │     ├── Start/Stop enforcement toggle
  │     ├── Interval selector (5, 10, 15, 30 min)
  │     └── Status text ("Enforcing every 10 min" or "Enforcement off")
  └── If permission not granted → show ADB setup instructions instead
```

### 6. Settings Screen — `ui/SettingsScreen.kt` (optional, can come later)

ADB permission status, app version, basic info.

---

## How It All Connects

```
┌──────────────────────────────────┐
│          Home Screen             │
│  (toggle, interval, start/stop) │
│              │                   │
│        HomeViewModel             │
└──────────┬───────────────────────┘
           │
     ┌─────┴──────┐
     ▼            ▼
┌──────────┐  ┌─────────────────┐
│ Grayscale│  │   Grayscale     │
│ Manager  │  │   Service       │
│ (toggle) │  │   (timer loop)  │
└──────────┘  └────────┬────────┘
                       │
                       ▼
               ┌───────────────┐
               │ Notification  │
               │ Helper        │
               └───────────────┘
```

Simple toggle: User → HomeViewModel → GrayscaleManager → Settings.Secure
Enforcement:   User → HomeViewModel → GrayscaleService → GrayscaleManager → Settings.Secure (repeats on timer)

---

## Manifest Entries Needed

```xml
<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>

<service
    android:name=".service.GrayscaleService"
    android:foregroundServiceType="specialUse"
    android:exported="false" />
```

---

## What's NOT in MVP (v2)

- Schedules (time windows, day selection)
- Room database
- WorkManager
- BootReceiver
- Schedule screens (list + editor)

These layer on top cleanly when ready. GrayscaleManager and Service don't change.