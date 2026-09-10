# Late schedule-alarm handling

## Result

A start delivered after its original schedule occurrence ended no longer enables
grayscale or changes an excluded app's saved restoration target. It advances the
alarm chain without reasserting whichever schedule happens to be active now.

The original failing emulator test remains intact: it arms a start using an earlier
scheduling clock, lets the real AlarmManager deliver it at the actual current time,
and checks the real receiver, display setting, and next registered boundary. Its
expected-color assertion failed on `e7f3b1d` and passes with this fix. The control
delivered inside its valid window still enables grayscale.

## Implementation

- Each registration persists a unique generation plus its schedule ID, original
  civil window, zone, and resolved absolute timestamps. Current enabled schedule
  configuration and actual delivery time determine whether it may act.
- Duplicate or superseded deliveries do not apply their display state. They can
  repair the currently saved OS registration with the same generation and deadline,
  covering a process interruption between persistence and alarm registration.
- Schedule mutations, alarm replacement, and receiver consumption share a coroutine
  mutex across instances. Display decisions also use the existing grayscale lock.
- Rearming after delivery uses the same decision instant, so an end crossed during
  a settings write is registered for immediate delivery instead of skipped.
- During active coverage, a closing event is kept ahead of interior overlapping
  starts. Touching windows prefer the end as well. A valid closing event resolves
  directly to the current aggregate state, avoiding an off/on transition and still
  closing when delivery occurs after all covered windows have elapsed.
- Configuration matching retains original civil times while expiry and ordering use
  instants. This handles normalized spring gaps, repeated fall hours, and effective
  overlap created by DST. Due closing events still act after zone changes. A start
  must remain current in both its originally armed interval and the current-zone
  interpretation of that civil occurrence.
- The separate enforcement interval is preserved, and accepted schedule changes
  continue through the existing exclusion-aware display operation.

## Upgrade compatibility

Already-armed older intents have only a start/end flag. Before the first new-format
registration, these use guarded current-window compatibility: expired starts
outside active windows are ignored, a missing kind is not treated as an end, and a
legitimate closing event still works. Such an old payload cannot validate origin
metadata it never contained. New registrations establish full provenance; a durable
initialization marker prevents old-format events becoming trusted again after the
last schedule is removed.

## Verification

- **239 JVM tests passed**, including disabled/deleted/edited occurrences, prior-day
  starts, touching boundaries, legacy compatibility, spring gaps, repeated hours,
  and changed-zone start validation.
- **49 Android instrumentation tests passed** on the Android 15/API 35 emulator.
  The 21 late-alarm cases include the original real-alarm failure, valid control,
  manual display states, exclusion targets and pending writes, duplicate and
  superseded generations, current-chain closing events, concurrent mutation and
  registration, interrupted registration, touching/overlapping chains, end crossing
  during handling, and delivered boundaries after a zone change.
- Debug and Android test APK builds, `lintDebug`, and `git diff --check` passed.
  Lint continues to report warnings with no errors.

Late delivery is controlled in these tests. They verify the app's behavior when an
alarm arrives late, not the frequency of naturally occurring delays or every OEM's
alarm behavior. General clock/timezone-change broadcast rescheduling and editor
draft/save improvements remain separate work.

Three independent code and adversarial reviews must approve the final commit before
merge. Final review votes are recorded in the task and Git review note.
