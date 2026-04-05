# Home Screen — Design Spec

Implements the DESIGN.md Home Screen spec (Section 1) with MVP adaptations: schedule-related sections show placeholders instead of live data.

---

## File Structure

```
ui/
  screens/
    HomeScreen.kt            — Full Home screen composable + private sub-composables
  components/
    GrayoutCard.kt           — Base card (surface bg, border, radius, active variant)
    GrayoutToggle.kt         — Custom toggle switch (56×31dp, DESIGN.md spec)
    StatusDot.kt             — 8dp status dot with optional glow
```

### Dependency Addition

Add `lifecycle-runtime-compose` for `collectAsStateWithLifecycle()`.

---

## Components

### GrayoutCard

Base card used across the app. Stateless wrapper.

- Background: `surface`
- Border: 1dp `border`
- Radius: `radius` (16dp)
- No elevation/shadow
- **Active variant** (via boolean param): background becomes vertical gradient `accentDim` → `surface`, border becomes `accent` at 27% opacity
- Accepts `modifier`, `isActive: Boolean = false`, `content: @Composable () -> Unit`

### GrayoutToggle

Custom toggle matching DESIGN.md Toggle Switch spec.

- Track: `accent` when on, `off` when off
- Thumb: `bg` when on, `offText` when off
- Size: 56×31dp
- Thumb animation: 250ms ease translateX
- Accepts `checked: Boolean`, `onCheckedChange: (Boolean) -> Unit`, `modifier`

### StatusDot

8dp circle indicator.

- Active: `success` fill + glow (`success` at 27% opacity, 8dp blur radius)
- Inactive: `off` fill, no glow
- Accepts `isActive: Boolean`, `modifier`

---

## Home Screen Layout

`HomeScreen` is a stateless composable. Signature:

```kotlin
@Composable
fun HomeScreen(
    isGrayscaleOn: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Root layout: `Column` with `verticalScroll`, `screenPad` (20dp) horizontal padding. Respects `modifier` (which carries Scaffold inner padding).

### 1. App Header

Private composable inside `HomeScreen.kt`.

- `Row`, vertically centered
- **App icon**: 40dp rounded square (`radiusSm`), vertical gradient from `accentDim` to `accent` at 40% opacity. Contains "◐" character in `text` color, 20sp, centered.
- 12dp gap
- **"Grayout"** in `headingSmall`, `text` color
- Bottom spacing: `sectionGap` (16dp)

### 2. Main Toggle Card

Private composable inside `HomeScreen.kt`. The primary interaction surface.

**Card**: `GrayoutCard` with `isActive = isGrayscaleOn`. Uses `cardPadLarge` (24dp) internal padding. Content is centered horizontally.

**Contents (top to bottom):**

1. **120dp circle** — tappable. Off: `off` fill, `border` 2dp stroke. On: `accentDim` fill, `accent` 2dp stroke at 33% opacity. Contains "◐" icon at 40sp (`textDim` when off, `accent` when on). Tapping calls `onToggle`. Color transitions: 300ms.

2. **State label** — `headingLarge`. Text: "Grayscale On" / "Grayscale Off". Color: `accent` when on, `text` when off. Top spacing: `sectionGap` (16dp).

3. **Subtitle** — `bodyMedium`, `textMuted`. Text: "Screen colors are turned off" / "Screen colors are normal". Top spacing: `itemGap` (8dp).

4. **Inner container row** — Top spacing: `sectionGap` (16dp). Full width. `bg` background, `radiusSm` corners, `cardPad` (16dp) padding. Contains:
   - Left: "Enforce Grayscale" in `bodyMedium`, `text` color
   - Right (end-aligned): `GrayoutToggle` (56×31dp), `checked = isGrayscaleOn`, `onCheckedChange` calls `onToggle`

Both the circle and the toggle control the same state via `onToggle`.

### 3. Stat Cards Row

Top spacing: `cardGap` (12dp). `Row` with `cardGap` spacing, each card `.weight(1f)`.

**"Status" card:**
- `GrayoutCard`, `cardPad` padding
- "STATUS" in `labelSmall`, uppercase, `textMuted`
- `itemGap` (8dp) below label
- Row: `StatusDot(isActive = isGrayscaleOn)` + 8dp gap + value text
  - Active: "Active" in `bodyMedium`, `success` color
  - Inactive: "Inactive" in `bodyMedium`, `offText` color

**"Next Schedule" card:**
- `GrayoutCard`, `cardPad` padding
- "NEXT SCHEDULE" in `labelSmall`, uppercase, `textMuted`
- `itemGap` (8dp) below label
- "—" in `mono` style, `textDim` color

### 4. Bottom Placeholder

Top spacing: `sectionGap` (16dp). Centered horizontally.

- "Schedules coming soon" in `bodyMedium`, `textMuted`

---

## Data Flow

- `NavGraph` collects `homeViewModel.isGrayscaleOn` via `collectAsStateWithLifecycle()` (replaces current `collectAsState()`)
- Passes `isGrayscaleOn: Boolean` and `onToggle: () -> Unit` (calling `homeViewModel.toggleGrayscale()`) to `HomeScreen`
- `HomeScreen` is fully stateless — all state and callbacks come from params
- Status card derives Active/Inactive from the same `isGrayscaleOn` boolean

---

## Animations

Per DESIGN.md:
- Toggle thumb: 250ms ease translateX
- Color transitions (card bg, border tint, text color, dot): 300ms
- Keep it subtle. No bouncing or spring physics.

---

## Verification Criteria

1. App builds and runs on a real device
2. Home screen matches this spec layout
3. Tapping the toggle (or the circle) changes the screen to grayscale and back
4. Status card updates to reflect current state (Active/Inactive + dot)
5. All colors come from `GrayoutTheme` — no hardcoded hex values in composables
6. `adb shell settings get secure accessibility_display_daltonizer_enabled` flips between 0 and 1 after toggling
