# Grayout optimization follow-up

Reviewed the current repository with two Codex/OpenAI subagents: one covered the
runtime, scheduling, and data layer; the other covered Compose and ViewModels.
This builds on the earlier review and subsequent scheduling/restart fixes.

## Implemented

| Change | User impact | Evidence |
| --- | --- | --- |
| Guard schedule load, save, and delete operations | Repeated taps cannot start competing saves or deletes. Fields and actions show their busy/disabled state. | Suspended DAO regression tests cover repeated calls and edits during persistence. |
| Preserve a loaded editor across recreation | Re-entering the same retained schedule ID no longer reloads stored fields over unsaved changes. Initial load failures cannot save placeholder values. | ViewModel regressions and an activity recreation test cover retained name/day changes. |
| Retain recoverable persistence state | Retrying a failed alarm update cannot insert a duplicate. After deletion, edits and Save remain blocked while Delete can retry the alarm update. | Failure/retry tests cover load, write, and alarm failures. |
| Refresh schedule badges at boundaries | A visible schedule switches between On and Now at its start/end without reopening the screen or polling Room. | Tests cover start/end, overlapping legacy rows, overnight windows, DST gaps, data changes, and cancellation/resume. |
| Recheck the Room singleton inside its initialization lock | Concurrent cold callers reuse the first database instance instead of constructing another. | Existing database instrumentation and full build validation. |

Badge observation shares the list's existing Room flow. Its timer runs only while
the destination is resumed and recalculates at the nearest row boundary. This
follows Android's [lifecycle-aware coroutine guidance](https://developer.android.com/topic/libraries/architecture/coroutines).
An hour-long boundary regression verifies that only one database flow subscription
is needed for both start and end updates.

## Remaining opportunities

The three items below are the original follow-up scope. Their implementation and
final verification are recorded in
[the completion review](final-optimization-verification-2026-09-11.md).

1. **Reconcile timezone and exact-alarm permission changes.** The manifest's boot
   receiver only listens for boot. A future schedule alarm retains its original
   absolute timestamp after a timezone change. Rescheduling needs explicit handling
   for a schedule that was active in the old zone but is inactive in the new one,
   while respecting manual grayscale and exclusion state. API 31/32 exact-alarm
   permission regrant also needs to rebuild alarms canceled on revocation. See
   Android's [exact-alarm permission guidance](https://developer.android.com/develop/background-work/services/alarms#using-schedule-exact-permission).
   Visible badge timers also need invalidation if the device clock/zone changes
   while the schedule screen remains resumed; currently they refresh on resume
   or the next scheduled recalculation.
2. **Measure release shrinking.** Release currently disables R8. The existing debug
   APK measured 12.35 MiB, with compressed DEX entries accounting for about 92% of
   its size. Debug includes development tooling, so this is not a release baseline
   or a savings estimate. Compare equivalent release builds and smoke-test Room,
   navigation, services, receivers, and tiles before changing release policy.
   Android documents [enabling R8 and resource shrinking](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization).
3. **Refresh Home's next-schedule text while visible.** It currently refreshes on
   resume. A similar lifecycle-bound boundary observer can keep it current without
   adding database polling.

The runtime already gates foreground-app polling on the screen being on and
exclusions being configured, reads grayscale only on entry, and uses alarms for
enforcement. Further polling, secure-setting-write, and observer changes should
follow profiling. This pass does not establish measured battery or startup gains.

## Verification

- `testDebugUnitTest`: 255 tests passed, zero failures or skips, including 16 new
  ViewModel regressions.
- `connectedDebugAndroidTest`: 50 tests passed on the Android 15/API 35 emulator,
  including the new activity recreation regression.
- Debug APK and Android test APK built successfully.
- `lintDebug`: passed with 62 warnings and no errors.
- `git diff --check`: passed.

The first final emulator attempt lacked the secure-settings and notification
grants after APK reinstallation. Restoring both grants and rerunning the full
suite passed. To reproduce, build/install the debug APK on the test emulator,
then grant `android.permission.WRITE_SECURE_SETTINGS` and
`android.permission.POST_NOTIFICATIONS` before running the connected tests.

The recreation test covers activity recreation with a retained ViewModel. It does
not claim unsaved-edit recovery after process death. No signed release, physical
device profiling, or release-size comparison was performed.
