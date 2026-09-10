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

Final reviewer approvals, measured artifacts, validation, and merge evidence are
recorded here after the final stable revision passes all gates.
