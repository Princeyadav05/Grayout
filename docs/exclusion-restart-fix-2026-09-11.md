# Exclusion recovery after process restart

## Fixed behavior

With grayscale on and interval enforcement off, entering an excluded app saves
the original grayscale target and switches the display to color. Previously,
screen-on application startup cleared that saved target after process death. The
detector could then remember the already-colored display as the original state,
leaving grayscale off after exit. Sticky service startup could also restore gray
immediately over a live excluded app if the target was simply retained.

Application and service startup now share one policy:

- With the screen on and exclusions configured, retain the complete session until
  foreground evidence resolves it. Successful suppression and failed writes both
  preserve their restoration target.
- With the screen off or no exclusions configured, reconcile immediately. Failed
  writes still retain the evidence required for recovery.
- Once foreground is known, the existing detector either continues the exclusion
  or restores its saved target on exit. Manual color remains color when enforcement
  is off. Subsequent schedule boundaries still replace the restoration target.

The first detector poll uses its normal ten-second query. If that is empty, it
makes one conservative 24-hour fallback query, resolving an app that was already
open before restart. Activity departures and screen/lock/reboot events invalidate
contradicted historical candidates. An unknown result retains the target; it is
never treated as evidence that an excluded app was left. The query runs on the
detector worker, and subsequent polls keep their original one-second cadence and
ten-second window.

Android documents nullable event queries before user unlock and lifecycle/reset
event meanings. The provider handles a null or denied query without destroying
state. See [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager)
and [UsageEvents.Event](https://developer.android.com/reference/android/app/usage/UsageEvents.Event).

A fresh service also preserves a surviving enforcement countdown. UI and Quick
Settings choices explicitly identify user interval changes; a boot/schedule intent
can carry the current interval without being mistaken for a request to reset it.

## Verification

The old APK reproduced the bug on an Android 15/API 35 emulator. A real SIGKILL and
automatic sticky restart changed the process PID; persisted active/restore-gray
flags changed from true/true to false/false while the excluded Clock app stayed
foreground and the screen remained interactive.

The fixed APK passed all five cases in `scripts/test_exclusion_restart.py`:

| Case | Verified outcome |
| --- | --- |
| Grayscale suppressed inside Clock | New process remains colored inside Clock, retains the on target, then restores grayscale on exit |
| Original manual color | New process and eventual exit remain in color |
| Already outside before restart | A foreground event older than ten seconds is resolved and the original grayscale target is restored |
| Pending enforcement countdown | The live alarm's original elapsed deadline survives restart and exit; delayed callbacks do not enable grayscale early |
| Screen off during restart | The original grayscale state is restored before wake and the suspended session is cleared |

The outside/screen-off cases pause the old process to prevent it from consuming
the transition, age the event, then kill it for real and await automatic restart.
The runner verifies a different PID and a running service. Force-stop is used
only for fixture cleanup, never as a substitute for the restart under test.

Additional checks passed:

- 222 JVM unit tests, including startup policy, failed-write preservation, repeated
  recovery, schedule precedence, and conservative history selection.
- 28 Android instrumentation tests, including surviving alarm-token identity for
  implicit and schedule-triggered startup, plus explicit user-change reset behavior.
- Debug APK and Android test APK builds, `lintDebug`, and `git diff --check`.
  Lint reports warnings, with no errors.

## Running the process test

Use a disposable emulator with the AOSP Clock app and the debug APK installed:

```sh
python3 scripts/test_exclusion_restart.py --serial emulator-5554
```

Pass `--adb /absolute/path/to/adb` if it is not on PATH. Physical devices are
rejected. The script resets Grayout's data on the selected emulator and uses
explicit serial targeting for every adb call. Each scenario produces one JSON
result and a failing case makes the command fail.

No grayscale was observed during sampled excluded-app restart checks, and the
startup unit tests verify that the policy performs no display writes in that case.
Host sampling cannot rule out a single-frame display artifact. OEM-specific
process management and exhaustive physical-device behavior were not tested.

Three independent code and adversarial review votes must approve the final commit
before merge; their final votes are recorded in the task and merge review note.
