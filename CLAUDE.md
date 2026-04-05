# Grayout — Claude Code Rules

## Project

- Android app, Kotlin, Jetpack Compose, min SDK 26
- Package: `com.princeyadav.grayout`
- Single-activity architecture, Compose navigation
- No third-party dependencies unless absolutely necessary

## Design

- Read `DESIGN.md` for design tokens, component patterns, and general rules.
- Read the relevant file in `docs/screens/` for screen-specific layouts.
- All UI must use theme tokens from `DESIGN.md`. No hardcoded colors/sizes.
- When building a new screen, check if a screen spec exists in `docs/screens/` before asking.
- Dark theme only. No light mode.

## Code Style

- Kotlin, idiomatic. No Java.
- Compose for all UI. No XML layouts.
- Keep files small. One screen/feature per file.
- Use Material 3 theming. All colors/typography defined via theme, never hardcoded.
- State hoisting: UI components are stateless, ViewModels hold state.
- Use StateFlow (not LiveData) for observable state.

## Architecture Rules

- MVVM: Screen (Composable) → ViewModel → Repository/Service
- Don't over-abstract. No use-case classes, no DI framework for MVP. Manual dependency wiring is fine.
- Foreground Service for grayscale enforcement.
- WorkManager for scheduling.
- Room only if we need persistence (schedules). SharedPreferences for simple key-value.

## What NOT to Do

- No Dagger/Hilt/Koin — manual DI is fine for this scope
- No multi-module project — single app module
- No abstract base classes "for future extensibility"
- No commented-out code or TODOs without context
- Don't suppress lint warnings without explanation
- No XML layouts, no Fragments

## File Organization

```
app/src/main/java/com/princeyadav/grayout/
  ui/              — Compose screens and components
  ui/theme/        — Theme, colors, typography
  ui/components/   — Reusable composables (cards, toggles, buttons)
  service/         — Foreground service for grayscale
  scheduling/      — WorkManager workers and schedule logic
  model/           — Data classes
  viewmodel/       — ViewModels
```

## Testing

- Not required for MVP, but ViewModels should be testable (no direct Android framework deps in VM logic).

## Git

- Public repo. Never commit secrets, keystores, or `local.properties`.
- One commit per logical change.
