# Grayout — Android Grayscale Enforcement App

## Project
- Android app, Kotlin, Jetpack Compose, min SDK 26
- Package: com.princeyadav.grayout
- Single-activity architecture, Compose navigation
- No third-party dependencies unless absolutely necessary

## Design
- Read DESIGN.md for design tokens, component patterns, and general rules.
- All UI must use theme tokens from DESIGN.md. No hardcoded colors/sizes.
- Dark theme only. No light mode.

## Scope
- Grayscale toggle (on/off via Settings.Secure)
- Interval enforcement via foreground service + AlarmManager (re-apply grayscale every N minutes)
- Per-app exclusions (pause grayscale while an excluded app is in the foreground)
- Day and time schedules (Room-backed)
- Quick Settings tiles (grayscale toggle, enforcement cycle)
- Screens: Home, Schedules, Schedule editor, Exclusion list, Settings
- No WorkManager. Enforcement ticks and schedule events run on AlarmManager.

## Code Style
- Kotlin, idiomatic. No Java.
- Compose for all UI. No XML layouts.
- Keep files small. One screen/feature per file.
- Use Material 3 theming. All colors/typography defined via theme, never hardcoded.
- State hoisting: keep business state out of composables and in ViewModels (small local UI state, like a sheet's open flag, is fine).
- Use StateFlow (not LiveData) for observable state.
- Single locale (English). UI copy is written inline in composables; there is no
  string-resource localization and none is planned. `strings.xml` holds only `app_name`.

## Architecture Rules
- MVVM: Screen (Composable) → ViewModel → Service/Manager
- Don't over-abstract. No use-case classes, no DI framework.
  Manual dependency wiring is fine.
- Foreground service (GrayoutService) coordinates enforcement and exclusion watching.
  Interval ticks and schedule events are driven by AlarmManager, not a coroutine loop.
- Decision logic is extracted into `logic/` (pure) and top-level functions in `service/`, so it
  can be unit-tested on the JVM: pure predicates directly, effectful appliers via injected fakes.
- GrayscaleManager is the ONLY class that touches Settings.Secure.

## What NOT to Do
- No Dagger/Hilt/Koin — manual DI is fine for this scope
- No multi-module project — single app module
- No abstract base classes "for future extensibility"
- No commented-out code or TODOs without context
- Don't suppress lint warnings without explanation
- No XML layouts, no Fragments
- No WorkManager. Use AlarmManager for enforcement and schedules.

## File Organization
```
app/src/main/java/com/princeyadav/grayout/
  ui/screens/      — Compose screens (Home, Schedules, ScheduleEditor, ExclusionList, Settings)
  ui/navigation/   — NavGraph and routes
  ui/theme/        — Theme, colors, typography, spacing
  ui/components/   — Reusable composables (cards, toggles, rows)
  service/         — GrayscaleManager, GrayoutService, ForegroundAppDetector, prefs, tiles, alarm receiver
  scheduling/      — ScheduleAlarmManager and receivers (schedule fire, boot)
  logic/           — Pure, Android-free decision functions (unit-tested)
  data/            — Room database, DAO, repository
  model/           — Data classes (AppInfo, Schedule)
  viewmodel/       — ViewModels
```

## Testing
- Decision logic (`logic/`, plus the top-level decision functions in `service/`) and ViewModels
  are unit-tested on the JVM. Keep new decision logic testable this way (pure, or with its
  collaborators injected) so it does not need an emulator.

## Git
- Public repo. Never commit secrets, keystores, or local.properties.
- One commit per logical change.
- **Conventional commits required.** Format: `type(scope): description`
  - Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `style`, `perf`, `ci`
  - Scope: primary module/area (e.g., `theme`, `nav`, `service`, `settings`)
  - Example: `feat(theme): set up compose theme from design system`

## Releases

- Full release ritual lives in `RELEASING.md` at the repo root. Read it before cutting a release.
- Releases are tag-triggered: pushing `vX.Y.Z` runs `.github/workflows/release.yml`, which builds a signed APK and publishes a GitHub release.
- `versionCode` and `versionName` are derived from the git tag at CI build time via `versionFromTag()` in `app/build.gradle.kts`. **Never bump them manually** — the defaults (`1.0.0-dev` / `1`) are only for local dev builds.
- Signing keystore lives in GitHub Secrets (`SIGNING_KEYSTORE_BASE64` + `SIGNING_KEY_ALIAS` + `SIGNING_STORE_PASSWORD` + `SIGNING_KEY_PASSWORD`). Never commit keystores, passwords, or `local.properties`. `.gitignore` already covers `*.jks` and `*.keystore`.
- Release notes auto-generate from conventional commits between tags — another reason the `type(scope): desc` format is required.
- Universal APK only. No AAB, no per-ABI splits, no Play Store, no F-Droid in current scope.