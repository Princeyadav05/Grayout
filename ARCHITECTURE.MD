# Grayout Architecture

A personal Android app that enforces grayscale to reduce phone use. It flips the
system grayscale on a schedule or interval, pauses it for apps you pick, and
exposes Quick Settings tiles. Single module, single activity, Kotlin, Jetpack
Compose, MVVM.

## What it does

1. **Toggle**: turn grayscale on or off with one tap.
2. **Interval enforcement**: every N minutes, re-apply grayscale even if you turned it off.
3. **Per-app exclusions**: pause grayscale while a chosen app is in the foreground, resume when you leave it.
4. **Schedules**: turn grayscale on for chosen days and time windows.
5. **Quick Settings tiles**: toggle grayscale and cycle the enforcement interval from the notification shade.

## How grayscale works on Android

Two values in `Settings.Secure` control the screen daltonizer:

```
accessibility_display_daltonizer_enabled = 1 (on) or 0 (off)
accessibility_display_daltonizer         = 0 (grayscale mode)
```

Write these and the screen goes gray. It persists even if the app is killed.
Normal apps cannot write here, so the app needs `WRITE_SECURE_SETTINGS`, granted
once over ADB (see README Setup).

The daltonizer is a shared system facility. A color-blind user's correction lives
in the same two keys, so whenever the app turns grayscale on from a non-grayscale
state it snapshots the current values as the restore baseline, and on disable it
writes that baseline back instead of blindly writing "off".

## Layers

### UI (Compose)

`MainActivity` is the only activity. It hosts a Compose `NavGraph`
(`ui/navigation/`) with five screens (`ui/screens/`): Home, Schedules, Schedule
editor, Exclusion list, Settings. Most state is hoisted out of the composables into
ViewModels (`viewmodel/`): `HomeViewModel`, `ScheduleViewModel`,
`ScheduleEditorViewModel`, `ExclusionViewModel`. Some still lives outside them, in
`MainActivity` (permission and battery status) and in navigation-level Compose state,
plus small local UI state like a sheet's open flag. Reusable pieces live in
`ui/components/` and design tokens in `ui/theme/`.

### Decision logic (`logic/` and top-level functions in `service/`)

The tricky decisions are extracted so they run under JVM unit tests, with the
Android classes as thin shells. Some are pure predicates over plain values; others
are appliers that take their collaborators (prefs, the grayscale controller) as
parameters, so tests drive them with fakes and no emulator is needed:

- `daltonizerPlan`: pure; what to write and whether to capture a baseline.
- `nextExclusionTransition`: pure exclusion state-machine step; `applyExclusionTransition` runs its side effects through injected collaborators.
- `applyEnforcementTick`: applier; whether an enforcement alarm re-enables grayscale, and does it.
- `preGrayOnScreenOff`: applier for the screen-off pre-gray; `shouldReEnableOnExclusionEnd` is the pure predicate behind the exclusion-end re-assert.
- `isCurrentlyFiring` / `nextScheduleEvent`: schedule window math and the next alarm time (pure over the `Schedule` model).
- `shouldServiceRun`: pure; whether the foreground service should be alive at all.

### GrayscaleManager (`service/GrayscaleManager.kt`)

The only class that touches `Settings.Secure`. Every read and write goes through
it. It verifies writes (writes, reads back, retries once) because a single `putInt`
can be silently dropped by a competing display feature, and it handles the baseline
capture and restore described above. It returns `false` when the write did not
stick or the permission is missing, so callers can react.

### GrayoutService (`service/GrayoutService.kt`)

The foreground service (`specialUse` type). It coordinates interval enforcement and
exclusion watching while it runs. It does **not** time enforcement with a coroutine
loop (the only loop it hosts is the exclusion detector's foreground-app poll). Instead it:

- schedules the next interval tick with `AlarmManager` (delivered to `EnforcementAlarmReceiver`);
- registers a `ContentObserver` on the daltonizer setting to notice manual changes and re-arm the countdown;
- registers a `SCREEN_ON` / `SCREEN_OFF` receiver to gate the detector and pre-assert grayscale on screen-off;
- drives `ForegroundAppDetector` when exclusions exist and the screen is on.

`onStartCommand` reads the interval (from the intent, or persisted `EnforcementPrefs`)
and picks one of three modes: stop fully when there is nothing to do, stay alive only
to watch exclusions (interval 0 with excluded apps), or run interval enforcement.
`shouldServiceRun` decides stop versus run; the interval value then splits the two
running modes. The service also reconciles stranded exclusion state so a suppressed
grayscale session is restored rather than left in color; the exact branch rules and a
known cold-start gap (`GrayoutApp` clears the flag before a sticky restart runs) live
in the code comments.

### Enforcement path

Interval ticks are `AlarmManager` alarms, not a coroutine loop. The alarm targets
`EnforcementAlarmReceiver`, which writes grayscale directly through
`GrayscaleManager` and never starts the service. It targets a receiver on purpose:
starting a foreground service from a background-fired alarm is restricted on API 31+,
while a broadcast receiver may run and write `Settings.Secure` freely.

### Exclusions

`ForegroundAppDetector` polls the current foreground app once a second (only while
the screen is on and at least one app is excluded) using `UsageStatsManager` through
`UsageStatsForegroundProvider`. Each poll feeds the pure exclusion state machine.
`ExclusionPrefs` (SharedPreferences) holds the FSM state: the excluded set, whether an
excluded app is currently active, and the grayscale state to restore when it closes.

### Schedules

Schedules are stored in Room (`data/`): the `Schedule` entity (`model/`), a DAO, and
`ScheduleRepository`. `ScheduleAlarmManager` cancels existing alarms, computes the
single next event with `nextScheduleEvent`, and sets one `AlarmManager` alarm for it
(exact when the permission allows, otherwise the inexact allow-while-idle fallback).
`ScheduleReceiver` handles the fire: it writes grayscale on or off, keeps the
enforcement service in sync, then reschedules the next event. `BootReceiver` restarts
the service and reschedules after a reboot.

### Storage

- **SharedPreferences** (`grayout_prefs`): enforcement interval, exclusion set and FSM flags, daltonizer baseline.
- **Room** (`grayout_db`): schedules.

### Quick Settings tiles

`GrayscaleToggleTile` flips grayscale. `EnforcementCycleTile` cycles the interval
(off, 1, 5, 10, 15, 30) and restarts the service. Both are bound behind
`BIND_QUICK_SETTINGS_TILE`.

## Key design decisions

- **Alarms, not loops.** Interval ticks and schedule events use `AlarmManager` with the allow-while-idle variants, so they survive Doze without a wakelock-hungry timer.
- **Receivers, not the service, are the alarm targets.** `EnforcementAlarmReceiver` writes grayscale itself and never starts the service, because starting a foreground service from a background alarm is restricted on API 31+. `ScheduleReceiver` also writes grayscale directly, and separately (re)starts the service to resync enforcement.
- **Pure logic, thin shell.** The calendar math, the exclusion FSM, and the write plan are Android-free and unit-tested; the services and receivers just wire them to the framework.
- **One Settings.Secure owner.** All daltonizer reads and writes go through `GrayscaleManager`, which is the only place that verifies writes and preserves the shared baseline.

## Permissions

See the Permissions section in [README.md](README.md). Nothing leaves the device.
