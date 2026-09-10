# Completing the three optimization follow-ups

Scope: timezone/clock/exact-alarm recovery, equivalent release shrinking and
verification, and live Home next-schedule text. The earlier editor/database/badge
improvements are included in the same integration branch.

## Changes

- System changes rebuild schedule alarms from local-time configuration. Continuing
  occurrences preserve manual choices; newly entered/departed windows update the
  display or the exclusion restoration target. Durable retry evidence survives
  failed writes and boot-style rescheduling.
- Enforcement recovery preserves a monotonic deadline, with boot/interval
  provenance and serialized transitions. Revoked exact access can use inexact
  alarms; permission regrant rebuilds canceled registrations. Explicit choices
  supersede stale restoration state.
- Home updates at real start boundaries and Room changes. Home and schedule badges
  invalidate timers immediately on clock/timezone events and release observation
  while inactive. Schedule selection uses the alarm path's DST resolution.
- Release enables R8 and resource shrinking. Local verification uses the actual
  release variant with a disposable signing key. Production signing stays required,
  CI builds the optimized variant, and tagged releases retain their R8 mapping.
- Android 12 testing exposed system navigation overlapping the bottom labels and
  low-contrast status icons. The custom navigation bar now consumes system insets,
  and system bars explicitly match the dark app theme.

## Review gates

Three Codex/OpenAI agents perform cross-review and adversarial review. Findings
addressed include stale saved deadlines overwriting newer interval choices,
asynchronous persistence losing recovery evidence, pending closure lost at boot,
an implicit start swallowing an explicit interval request, and asynchronous
timezone cleanup leaking into subsequent tests.

Smoke checks verify actual before/after state, cancellation before permission
regrant, live alarm records, persisted UI edits, and the exact APK SHA-256. They use
an English Android 12 emulator with SystemUI, rather than an ATD image without
Quick Settings. The optimized app has no instrumentation keep rules or debug flag.

All three reviewers gave final code, adversarial, and merge approval for
`deee9b66f2e56b4d10d0ee2783004d1cb8ae5faf`: runtime, UI, and release optimization.
The production/build/script source is unchanged from
`9216edc16a08d4fb835faec6002d2af06ee1b5eb`; the later commit only makes asynchronous
instrumentation tests await actual service completion before asserting.

## Equivalent release measurement

Both artifacts were built from production revision `9216edc` with the same
dependencies, version (`1.0.0-dev`, code 1), SDK/JDK, and disposable signing key.
The isolated baseline checkout changes only `isMinifyEnabled` and
`isShrinkResources` from true to false. Both APKs are non-debuggable.

| Measurement | Unoptimized release | Optimized release |
| --- | ---: | ---: |
| APK bytes | 8,484,395 | 1,455,324 |
| Compressed DEX bytes | 7,852,051 | 1,184,085 |
| Median of five force-stopped emulator launches | 323 ms | 273 ms |

The APK is **7,029,071 bytes smaller, an 82.85% reduction**. Launch samples were
300/343/418/323/298 ms for the baseline and 273/278/262/223/284 ms for the optimized
APK. These are small samples on the same Android 12 emulator with no parallel
Gradle build during the launch sampling. They do not establish physical-device
startup, memory, or battery gains.

Artifact SHA-256 values:

- Baseline: `02fb26904d5179ff8dcd285fd3fd94d5d076acb62d740f5df1ee341f0c41665c`
- Optimized: `430f68b15c4596de320c74d42235a981d16c2af3da0adb280f94721376454aef`

The final smoke report independently identifies the optimized hash above. The
matching R8 mapping was retained. These are locally signed verification artifacts;
production packaging was separately verified to fail when its signing keystore
is absent.

## Final validation

- **291 JVM tests passed**, no failures, errors, or skips.
- **70 instrumentation tests passed on Android 12/API 31 and Android 15/API 35**,
  including the final asynchronous test-wait correction.
- Debug, Android test, and optimized release APKs built successfully.
- `lintDebug` passed with **66 warnings and no errors**; `git diff --check` passed.
- All **11 optimized-APK smoke checks passed**: startup, navigation/display writes,
  Room creation/edit/toggle/restart persistence, real schedule start/end and live
  Home text, timezone entry/closure, exclusions, both Quick Settings tiles,
  exact-alarm revoke/regrant with preserved deadlines, real enforcement delivery,
  Room deletion, and no app crashes.

Earlier test runs identified two test-harness defects: fluent editor wrappers
bypassed the intended failure stub, and one service test asserted before an
asynchronous start completed. Both were corrected and the full suites rerun.
No production change followed the final equivalent-artifact builds.

The source and evidence were approved before merging the review branch into main.
No release tag or production-signed APK was published by this work.
