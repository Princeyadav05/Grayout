# Grayout — Design Specification

This document defines the visual language for the Grayout Android app. Follow these tokens, patterns, and screen specs when building any Jetpack Compose UI.

---

## Color Tokens

Map these to a Compose `MaterialTheme` color scheme. The app is dark-only (no light theme).

| Token         | Hex       | Usage                                      |
|---------------|-----------|---------------------------------------------|
| `bg`          | `#0B0B0B` | Scaffold/screen background                  |
| `surface`     | `#161616` | Card backgrounds                            |
| `border`      | `#2A2A2A` | Card borders, dividers, input borders       |
| `borderActive`| `#555555` | Borders on active/selected cards            |
| `text`        | `#E8E8E8` | Primary text (headings, body)               |
| `textMuted`   | `#777777` | Secondary/supporting text                   |
| `textDim`     | `#4A4A4A` | Disabled/tertiary text                      |
| `accent`      | `#A0D2C6` | Muted teal — active states, CTAs, toggles on, selected chips, nav highlight |
| `accentDim`   | `#2A3D38` | Dark tint of accent — active card tint, selected day backgrounds |
| `off`         | `#3A3A3A` | Toggle off background, inactive chip bg     |
| `offText`     | `#666666` | Text on off/disabled elements               |
| `danger`      | `#FF6B6B` | Delete actions, destructive buttons         |
| `success`     | `#6BE8A0` | Active status dots, running indicators      |

## Typography

| Style           | Font             | Size | Weight  | Usage                          |
|-----------------|------------------|------|---------|--------------------------------|
| `headingLarge`  | DM Sans          | 28sp | Bold    | Main toggle state label        |
| `headingMedium` | DM Sans          | 24sp | Bold    | Screen titles (Schedules, Settings) |
| `headingSmall`  | DM Sans          | 20sp | Bold    | App name, editor title         |
| `titleMedium`   | DM Sans          | 18sp | Bold    | Schedule name, stat values     |
| `bodyLarge`     | DM Sans          | 16sp | Regular | Input text                     |
| `bodyMedium`    | DM Sans          | 14sp | SemiBold| Toggle labels, schedule times  |
| `bodySmall`     | DM Sans          | 13sp | SemiBold| Subtitle text, chip labels     |
| `labelSmall`    | DM Sans          | 12sp | Regular | Section headers (uppercase + 0.5sp tracking), muted labels |
| `labelXSmall`   | DM Sans          | 11sp | SemiBold| Day selector text, badge text, nav labels |
| `mono`          | JetBrains Mono   | 22sp | Bold    | Time display (start/end pickers) |
| `monoSmall`     | JetBrains Mono   | 14sp | Medium  | Schedule time range on cards   |

Letter spacing: headings use `-0.5sp`. Labels use `+0.5sp`.

## Spacing & Sizing

| Token          | Value | Usage                                 |
|----------------|-------|---------------------------------------|
| `screenPad`    | 20dp  | Horizontal padding on all screens     |
| `cardPad`      | 16dp  | Default card internal padding         |
| `cardPadLarge` | 24dp  | Main toggle card padding              |
| `cardGap`      | 12dp  | Gap between cards                     |
| `sectionGap`   | 16dp  | Gap between card sections             |
| `itemGap`      | 8dp   | Gap between list items inside cards   |
| `chipGap`      | 8dp   | Gap between chips                     |
| `dayDotGap`    | 6dp   | Gap between day selector circles      |

## Corner Radius

| Token       | Value | Usage                          |
|-------------|-------|--------------------------------|
| `radius`    | 16dp  | Cards                          |
| `radiusSm`  | 10dp  | Inner containers, inputs       |
| `radiusFull`| 999dp | Chips, pills, toggles, day dots|

## Component Patterns

### Cards
- Background: `surface`
- Border: 1dp `border` (default), `accent` at 27% opacity when active
- Radius: `radius` (16dp)
- No elevation/shadow — borders only

### Toggle Switch
- Track: `accent` when on, `off` when off
- Thumb: `bg` when on, `offText` when off
- Size: 56×31dp (large, main), 44×24dp (settings rows)
- Animate: 250ms ease

### Day Selector Circles
- Size: 32dp (schedule list), 40dp (editor)
- Selected: `accentDim` fill, `accent` border at 33% opacity, `accent` text
- Unselected: transparent fill, `border` border, `textDim` text
- All circles in a row, evenly spaced

### Chips (Quick Select: "Every day", "Weekdays", "Weekends")
- Active: `accentDim` bg, `accent` text, `accent` border at 33% opacity
- Inactive: transparent bg, `textMuted` text, `border` border
- Radius: full round (pill shape)
- Padding: 6dp vertical, 14dp horizontal

### Status Dot
- Size: 8dp circle
- Active: `success` fill with subtle glow (`success` at 27% opacity, 8dp blur)
- Inactive: `off` fill, no glow

### Active/Off Badge
- Active: `accentDim` bg, `accent` text, 12dp radius pill
- Off: `off` bg, `offText` text

### Bottom Navigation
- 3 tabs: Home, Schedule, Settings
- Icons: outlined stroke style, 22dp, `accent` stroke when active, `#666` when inactive
- Active tab fills icon with `accentDim`
- Label: 11sp, semibold, `accent` when active, `#555` when inactive
- Bar: `bg` background, `border` top border, 12dp top padding, 28dp bottom padding (safe area)

### Buttons
- Primary (Save): `accent` bg, `bg` text, `radius` corners, 16dp vertical padding, 16sp bold
- Destructive (Delete): transparent bg, `danger` text, 14sp semibold
- Add (+): `accent` bg, `bg` text, full-round pill, 13sp bold

---

## Screen Specs

### 1. Home Screen

Top to bottom:
1. **App header** — App icon (◐ in a rounded square with `accent` gradient tint) + "Grayout" text (headingSmall)
2. **Main toggle card** (cardPadLarge) — Large circle (120dp) as visual indicator, taps to toggle. Below: state label (headingLarge, `accent` when on, `text` when off), subtitle (bodyMedium, `textMuted`), then a row with "Grayscale" label + Toggle switch inside an inner container (`bg` background, `radiusSm`). When enabled, card gets a gradient bg from `accentDim` to `surface` and an `accent` tinted border.
3. **Two stat cards** side by side — "Status" showing Active/Inactive (`success`/`offText`), "Next Schedule" showing time in `mono` font.
4. **Active Schedules preview card** — Header with "Active Schedules" + count in `accent`. List of schedule summaries (name + time + days) in inner rows with `bg` background and StatusDot.

### 2. Schedule List Screen

Top to bottom:
1. **Header row** — "Schedules" (headingMedium), subtitle, and "+ Add" pill button
2. **Schedule cards** — Each card contains: Active/Off badge + Toggle in top row. Below: schedule name (titleMedium), time range in `monoSmall` (`accent` when active, `textMuted` when off), day dots row. Inactive schedules render at 50% opacity. Active cards get tinted border.

### 3. Schedule Editor Screen

Top to bottom:
1. **Header** — Back button (circle, `surface` bg, chevron icon) + "Edit Schedule" (headingSmall)
2. **Name card** — Label + text input (`bg` background, `border` border, focus border tints to `accent`)
3. **Time card** — "Time Range" label + "All day" toggle. When not all-day: two time displays (START/END) in `mono` font with `accent` color, arrow between them.
4. **Days card** — "Repeat on" label, row of 7 day circles (tappable), quick-select chips below
5. **Save button** — Full-width primary button
6. **Delete button** — Centered `danger` text

### 4. Settings Screen

Top to bottom:
1. **Title** — "Settings" (headingMedium)
2. **Service card** — Section header ("SERVICE" in `accent`, labelSmall uppercase). Rows: "Start on boot" with toggle, "Show notification" with toggle, "Service status" with arrow. Each row has label (bodyMedium+semibold) and optional subtitle (labelSmall, `textMuted`). Rows separated by `border` divider.
3. **Setup card** — Section header "SETUP". Rows: "ADB permission", "Battery optimization" with arrows.
4. **About card** — Section header "ABOUT". Version row. Footer tagline centered in `textDim`.

---

## Animations

- Screen transitions: fade in + 12dp slide up, 500ms
- Toggle thumb: 250ms ease translateX
- Color transitions: 300ms (border tint, text color changes)
- Keep it subtle. No bouncing, no spring physics.

## General Rules

- Dark theme only. No light mode.
- No elevation/shadow anywhere. Depth via borders and subtle background tints.
- Cards are the primary container for everything.
- Accent color (`#A0D2C6`) is the ONLY chromatic color in the app besides danger red and success green.
- When something is off/disabled, it goes gray — never accent-colored.
- Generous whitespace. Don't crowd elements.
- Monospace font only for time displays.
