# Schedule and exclusion coordination

## Behavior

Schedule start, end, and active-window resynchronization now share one operation.
While an excluded app is active, the display stays in color and the schedule updates
the state to restore on exit. An end boundary replaces an earlier grayscale-on
request. The independently configured enforcement interval remains unchanged.

The operation also restores color if a manual toggle or failed exclusion-entry
write left the display gray. Failed color writes retain a preference flag for retry
on a later poll, exit, or restart reconciliation. The original restoration target
is retained until a schedule boundary deliberately replaces it or recovery succeeds.

One process-wide lock covers display writes and each exclusion state transition,
including its reads. This prevents a schedule end from interleaving with an exit
and then being overwritten by its stale restoration state. UsageStats queries and
database suspensions are outside the lock. Resynchronization rechecks the current
time after acquiring the lock, and delayed enforcement callbacks recheck whether
a new excluded session has begun.

## Verification

- Installed the pre-fix debug app on the Android 15/API 35 emulator and ran the first
  four new integration cases. All four failed at the expected assertions: schedule
  start, schedule end, end with standing enforcement, and active-window resync.
- The updated app passes all 24 Android instrumentation tests on that emulator,
  including seven schedule/exclusion integration cases using real broadcasts, Room,
  and secure settings.
- All 200 JVM unit tests pass. New cases cover boundary ordering, repeated resync,
  enforcement preservation, screen-off recovery, failed entry and exit writes,
  manual display changes, retry while remaining excluded, and forced concurrent
  entry/start and exit/end operations.
- Debug and Android test APKs build. `lintDebug` and `git diff --check` pass; lint
  continues to report warnings.

One initial Gradle connected-test run also discovered a connected physical phone.
Its installation was rejected for a signature mismatch. Subsequent device tests
used explicit `adb -s emulator-5554` installation and instrumentation commands;
the successful 24-test result is from the emulator only.

Three independent reviewers perform both code and adversarial review of the full
merge, including the preceding optimization changes. Their first pass identified
the failed-entry/manual-toggle divergence, which the pending-color recovery and
new regression cases address. Final votes are tied to the final commit in the task
record. Merge requires all three votes to be yes.

## Separate follow-ups

Timezone/clock-change rescheduling, schedule-editor duplicate-save protection,
draft preservation, and release shrinking are outside this fix. No release was
published, and no battery or startup-time improvement is claimed.
