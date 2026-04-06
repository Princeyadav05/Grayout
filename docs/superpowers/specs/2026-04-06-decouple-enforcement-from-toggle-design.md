# Decouple Enforcement from Grayscale Toggle

## Goal

Make the enforcement interval independent from the grayscale toggle. Toggling grayscale off should not reset or disable enforcement. The enforcement card should always be fully interactive regardless of grayscale state.

## Motivation

Currently, toggling grayscale off silently resets the enforcement interval to 0 (off). This is confusing — the user sets enforcement to 5m, toggles grayscale off, and their enforcement setting is gone without feedback. Enforcement should persist so the service can re-enable grayscale on schedule even after the user manually turns it off.

## Changes

### HomeViewModel.kt — `toggleGrayscale()`

Remove the side-effect that resets enforcement when grayscale is toggled off:

```kotlin
// REMOVE these lines (31-33):
if (!newValue) {
    setEnforcementInterval(0)
}
```

After the change, `toggleGrayscale()` only calls `grayscaleManager.setGrayscale()` and updates `_isGrayscaleOn`.

### HomeScreen.kt — `EnforcementCard`

1. **Remove `isGrayscaleOn` parameter** from function signature and call site.
2. **Remove alpha dimming**: delete `val alpha = if (isGrayscaleOn) 1f else 0.4f` and the `.alpha(alpha)` modifier. The card is always fully visible.
3. **Remove grayscale gate on chip clicks**: change `onClick = { if (isGrayscaleOn) onIntervalChange(value) }` to `onClick = { onIntervalChange(value) }`. Chips are always tappable.
4. **Update subtitle text**: "Re-enable grayscale automatically" → "Turns grayscale back on periodically, even if you switch it off".
5. **Remove unused import**: `import androidx.compose.ui.draw.alpha`.

## Files Not Touched

- GrayscaleManager
- GrayscaleService
- EnforcementPrefs
- NotificationHelper
- Any other file

## Behavioral Result

User sets enforcement to e.g. "5m", then toggles grayscale off. The enforcement service re-enables grayscale after 5 minutes. The enforcement card remains fully visible and interactive at all times.
