# Grayout optimization review

Reviewed the repository with two Codex/OpenAI agents, covering background services,
scheduling, UI, ViewModels, persistence, tests, and build configuration.

The clearest immediate wins were removing repeated work and fixing state refresh
and recovery. The existing alarm-based enforcement and screen-off polling gate are
worth retaining. This review does not establish measured battery, memory, startup,
or release-size gains.

## Implemented

| Change | Practical result | Main source |
| --- | --- | --- |
| Read grayscale only when entering an exclusion | Stable one-second polls avoid unnecessary secure-setting reads, preserving the polling interval | `service/ForegroundAppDetector.kt` |
| Preserve failed exit restoration state | A failed grayscale restore retries on the next poll instead of losing the saved state | `service/ForegroundAppDetector.kt` |
| Give each screen one initial loading trigger | Home stops repeating icon and diagnostic loads across initialization/activity/navigation; exclusions stop scanning installed apps twice on entry | `MainActivity.kt`, `viewmodel/HomeViewModel.kt`, `viewmodel/ExclusionViewModel.kt` |
| Coalesce overlapping exclusion refreshes | One installed-app load runs at a time; publication uses current preferences so a load cannot undo a newer toggle | `viewmodel/ExclusionViewModel.kt` |
| Refresh Home from authoritative state on resume | Quick Settings changes appear after returning; recreation cannot send an old UI interval back to the service | `MainActivity.kt`, `viewmodel/HomeViewModel.kt` |
| Make permission checks read-only | Checking access no longer rewrites a system setting or races with a concurrent setting change | `service/GrayscaleManager.kt` |
| Observe both daltonizer settings | Home and enforcement respond when the mode changes while the enabled flag stays the same | `MainActivity.kt`, `service/GrayoutService.kt` |

Source paths above are relative to `app/src/main/java/com/princeyadav/grayout/`.

## Recommended next work

### 1. Schedule/exclusion coordination: fixed in the follow-up

`scheduling/ScheduleReceiver.kt` and `scheduling/ScheduleAlarmManager.kt` currently
enable grayscale directly during an active excluded-app session. The detector sees
no new entry and leaves the excluded app gray. Schedule end also leaves the saved
restore state unchanged, so leaving an excluded app can restore grayscale after
the schedule has ended.

Use a shared schedule-state operation that updates the deferred restoration state
when an exclusion is active. Verify start and end transitions both inside and
outside an excluded app, including failed writes. Also reschedule local-time alarms
after timezone or clock changes; `BootReceiver.kt` currently handles only boot.

The schedule/exclusion conflict is now fixed and verified on an emulator. See
[the follow-up implementation and verification](schedule-exclusion-fix-2026-09-11.md).
Timezone and clock-change rescheduling remain separate work. The paragraphs above
record the original findings and recommended approach.

### 2. Protect schedule editing and time-based UI state

Implemented in the [follow-up review](optimization-follow-up-2026-09-11.md):
guarded editor operations, retained unsaved edits, and lifecycle-bound schedule
badge updates. The findings below record the original recommendation.

`viewmodel/ScheduleEditorViewModel.kt` allows overlapping save operations across
the suspending overlap check and insert. Add an in-flight guard and disable the
actions while persistence is running. Repeated `loadSchedule(id)` calls also reload
over unsaved edits after activity recreation; loading the same retained ID should
be idempotent.

`viewmodel/ScheduleViewModel.kt` refreshes active badges on entry and actions, so a
visible screen can become stale as a schedule boundary passes. Use lifecycle-bound
updates at the next boundary rather than frequent database polling.

### 3. Evaluate release shrinking with an optimized-build test

`app/build.gradle.kts` explicitly disables R8. Android recommends enabling code
optimization and resource shrinking for release builds. This could reduce the
packaged code/resources, but the benefit has not been measured for Grayout.
[Android's R8 guidance](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization).

Build a comparable optimized artifact, measure its size and startup behavior, and
smoke-test Room, navigation, services, receivers, and Quick Settings tiles before
changing the release policy. No build-dependency upgrades or release-policy changes
were included in this pass.

## Verification

- `./gradlew testDebugUnitTest`: 183 tests passed, zero failures or skips.
- `./gradlew connectedDebugAndroidTest`: 17 tests passed on an Android 15/API 35 emulator.
- Debug APK and Android test APK built successfully.
- `lintDebug` passed with warnings; `git diff --check` passed.

New regression coverage checks polling read counts, restoration retry, concurrent
app loading and toggles, external state refresh, rapid interval changes, activity
recreation, and mode-only changes in the real activity/service.

Physical-device battery profiling and a signed release build were not performed.
