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

Home reconciles grayscale and enforcement settings on activity resume, including
changes made through Quick Settings while stopped. Only explicit interval choices
command the service; replaying UI state never writes an old interval back. Home's
resume effect owns icon/diagnostic loading, and the exclusion screen's resume
observer owns its app-list load. Concurrent app-list refreshes share the running
load and publish with current exclusion preferences so loading cannot undo a toggle.

### Decision logic (`logic/` and top-level functions in `service/`)

The tricky decisions are extracted so they run under JVM unit tests, with the
Android classes as thin shells. Some are pure predicates over plain values; others
are appliers that take their collaborators (prefs, the grayscale controller) as
parameters, so tests drive them with fakes and no emulator is needed:

- `daltonizerPlan`: pure; what to write and whether to capture a baseline.
- `nextExclusionTransition`: pure exclusion state-machine step; `applyExclusionTransition` runs its side effects through injected collaborators.
- `applyEnforcementTick`: applier; re-enables grayscale on a tick and reports `Applied` / `Skipped` / `WriteFailed`.
- `preGrayOnScreenOff`: applier for the screen-off pre-gray; `shouldReEnableOnExclusionEnd` is the pure predicate behind the exclusion-end re-assert.
- `isCurrentlyFiring` / `nextScheduleEvent`: schedule window math and the next alarm time (pure over the `Schedule` model).
- `shouldServiceRun`: pure; whether the foreground service should be alive at all.

### GrayscaleManager (`service/GrayscaleManager.kt`)

The only class that touches `Settings.Secure`. Every read and write goes through
it. It verifies writes (writes, reads back, retries once) because a single `putInt`
can be silently dropped by a competing display feature, and it handles the baseline
capture and restore described above. It returns `false` when the write did not
stick or the permission is missing, so callers can react.
Permission checks only read the grant status; they do not write secure settings.
The activity and service observe both daltonizer keys so mode-only changes are seen.

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
running modes. Application and service startup share `reconcileExclusionOnStart`.
With the screen on and exclusions configured, a saved session and its restoration
target survive until foreground detection proves whether it is still live. A sticky
restart never blindly restores gray over an excluded app. With the screen off, or
no exclusions remaining, startup reconciles immediately and retains failed writes.
A fresh service also preserves a surviving enforcement countdown. Only an explicit
UI/tile interval choice marks the first command as a requested countdown reset;
boot and schedule resynchronization may carry the interval without resetting it.

### Enforcement path

Interval ticks are `AlarmManager` alarms, not a coroutine loop. The alarm targets
`EnforcementAlarmReceiver`, which writes grayscale directly through
`GrayscaleManager` and never starts the service. It targets a receiver on purpose:
starting a foreground service from a background-fired alarm is restricted on API 31+,
while a broadcast receiver may run and write `Settings.Secure` freely. If the tick's
write fails (the daltonizer permission was revoked), the receiver reschedules a retry
one interval out and posts a "permission lost" notification; the retry auto-recovers
once the permission is restored, without reopening the app.

### Exclusions

`ForegroundAppDetector` polls the current foreground app once a second (only while
the screen is on and at least one app is excluded) using `UsageStatsManager` through
`UsageStatsForegroundProvider`. Each poll feeds the pure exclusion state machine.
The normal query looks back ten seconds. On the first poll only, an empty recent
query gets one bounded 24-hour fallback to resolve an app left open across process
death. The fallback rejects activity departures, lock/screen-off, and reboot events
that contradict an old resume. Own-package events pass through unchanged; genuinely
unknown foreground keeps the restoration target until fresh evidence arrives.
`ExclusionPrefs` (SharedPreferences) holds the FSM state: the excluded set, whether an
excluded app is currently active, and the grayscale state to restore when it closes.
The detector reads the current grayscale state only when entering an exclusion.
If restoring grayscale on exit fails, it keeps the saved state and retries on the
next poll before clearing the session. Failed color writes also retain a pending
flag, so a stable excluded app or a later exit can retry without losing the state
to restore. Failed grayscale restoration is retained too. A screen-on cold start
preserves both successful suppression targets and failed-write recovery evidence.

Schedule boundaries and active-window resynchronization use
`applyScheduleGrayscale`: during an exclusion they keep the display colored and
replace the state to restore on exit. Thus a schedule start is deferred until exit,
and a schedule end supersedes a saved grayscale-on state. A process-wide lock
covers the screen-state check too: while the screen is off, the new target is
applied immediately and a suspended exclusion is cleared only after success. The
next screen-on entry captures that target for restoration on exit. The lock also
covers each exclusion read/decide/apply operation, schedule writes, enforcement,
screen-off recovery, and physical display writes. UsageStats queries and suspending
database work stay outside it. Resynchronization rechecks the time after taking
the lock so an ended window cannot restore a stale request.

### Schedules

Schedules are stored in Room (`data/`): the `Schedule` entity (`model/`), a DAO, and
`ScheduleRepository`. Schedule mutations, alarm replacement, and receiver handling
share a coroutine mutex. `ScheduleAlarmManager` persists the next registration's
generation, originating schedule, civil window, zone, and resolved instants before
registering one system alarm (exact when permitted, otherwise inexact).

`ScheduleReceiver` validates the current generation and still-enabled occurrence
before changing grayscale. Expired starts cannot use another active window to
justify enabling grayscale. Superseded and duplicate deliveries never apply their
display state; they can repair the saved registration with its unchanged deadline.
Accepted or invalid-current deliveries rearm alarms separately from display sync.
Explicit editor/boot rescheduling retains its existing active-window synchronization.

Alarm expiry and ordering use absolute instants while configuration validation uses
the original civil window, including DST gaps and repeated hours. The same decision
instant selects the next event so a closing boundary crossed during a write is not
skipped. During an active period, the next closing event remains armed instead of
an interior overlapping start; touching boundaries also prefer the end. A valid end
resolves directly to current aggregate coverage, keeping gray on if another window
is active and turning it off if all have ended. Due ends still close after a zone
change; starts must be current in both the armed interval and current civil window.

Pre-upgrade kind-only alarms get guarded current-window compatibility before the
first new registration. They cannot validate an unavailable original occurrence.
After initialization, a persistent marker prevents old-format events becoming
trusted again, including after the last alarm is removed. `BootReceiver` restarts
the service and reschedules after reboot.

### Storage

- **SharedPreferences** (`grayout_prefs`): enforcement interval, exclusion set and FSM flags, daltonizer baseline.
- **Room** (`grayout_db`): schedules.

### Quick Settings tiles

`GrayscaleToggleTile` flips grayscale. `EnforcementCycleTile` cycles the interval
(off, 1, 5, 10, 15, 30) and restarts the service. Both are bound behind
`BIND_QUICK_SETTINGS_TILE`.

## Key design decisions

- **Alarms, not loops.** Interval ticks and schedule events use `AlarmManager` with the allow-while-idle variants, so they survive Doze without a wakelock-hungry timer.
- **Receivers, not the service, are the alarm targets.** `EnforcementAlarmReceiver` writes grayscale itself and never starts the service, because starting a foreground service from a background alarm is restricted on API 31+. `ScheduleReceiver` also writes grayscale directly, and starts the service to resync enforcement through a guard that swallows the background-start rejection when an inexact alarm fires.
- **Pure logic, thin shell.** The calendar math, the exclusion FSM, and the write plan are Android-free and unit-tested; the services and receivers just wire them to the framework.
- **One Settings.Secure owner.** All daltonizer reads and writes go through `GrayscaleManager`, which is the only place that verifies writes and preserves the shared baseline.

## Permissions

See the Permissions section in [README.md](README.md). Nothing leaves the device.
