# Grayout — Design Specification

This document defines the visual language for the Grayout Android app. It reflects the current state of the theme files under `app/src/main/java/com/princeyadav/grayout/ui/theme/`. Follow these tokens, patterns, and general rules when building any Jetpack Compose UI.

---

## Philosophy

Grayout is a grayscale-enforcement app, so its UI is itself almost entirely achromatic. Depth, state, and hierarchy come from **luminance** — layered blacks, border weights, and text brightness — not from color.

State is communicated **without hue**:
- On/off is expressed through layout, border weight, text weight, and luminance.
- Disabled/inactive elements go gray. They never turn a different color.
- A card that is "active" has a brighter border, not a blue tint.

The app has a **single brand accent**: `BrandAccent = #B5A0D8` (dusty lavender). It is deliberately scarce — a light sprinkle of identity, not a state color. It lives as a standalone top-level `val` in `Color.kt`, **not inside `GrayoutTheme.colors.*`**, so it is harder to reach for by accident.

`BrandAccent` is used only for:
- Eyebrow labels (`STATUS`, `ENFORCEMENT`, `EXCLUSIONS`) at 0.75 alpha
- The vertical gradient wash at the top of the status hero card
- The foreground notification accent
- The adaptive launcher icon tint
- The wordmark lockup on the home header
- The `STATUS` dot when grayscale is **off**
- The flash/pulse on the ADB command sheet `COPY → COPIED` confirmation
- Section headers inside the exclusions screen
- The schedule "Now" badge on the schedule list

Everything else is grayscale.

---

## Color Tokens

All tokens live in `Color.kt`. There are **10 tokens** on `GrayoutTheme.colors`, plus one standalone `BrandAccent`. That's it.

| Token          | Hex       | Usage                                                           |
|----------------|-----------|-----------------------------------------------------------------|
| `bg`           | `#0B0B0B` | Scaffold / screen background                                    |
| `surface`      | `#161616` | Card backgrounds                                                |
| `border`       | `#2A2A2A` | Card borders, dividers, inactive chip borders                   |
| `borderActive` | `#555555` | Borders on active/selected cards                                |
| `text`         | `#E8E8E8` | Primary text, active chip fill, active toggle track             |
| `textMuted`    | `#777777` | Secondary / supporting text, inactive chip text                 |
| `textDim`      | `#4A4A4A` | Disabled / tertiary text, footer tagline                        |
| `off`          | `#3A3A3A` | Toggle off track, inactive pill background                      |
| `offText`      | `#666666` | Text on off/disabled elements                                   |
| `danger`       | `#FF6B6B` | Destructive actions and error states                            |
| `BrandAccent`  | `#B5A0D8` | Standalone. Brand/identity only — see list above.               |

**Rule:** never hardcode a `Color(0x...)` in UI code. Pull from `GrayoutTheme.colors` or import `BrandAccent`.

The `MaterialTheme` color scheme in `Theme.kt` is wired from these same tokens so that stock Material 3 components (`HorizontalDivider`, `Text`, etc.) also pick up the theme automatically.

---

## Typography

Two font families, loaded via Google Fonts:
- **Plus Jakarta Sans** — weights 400 / 500 / 600 / 700 / 800
- **JetBrains Mono** — weights 500 / 700

Styles are defined on `GrayoutTheme.typography`.

| Style           | Font             | Size | Weight     | Usage                                      |
|-----------------|------------------|------|------------|--------------------------------------------|
| `headingLarge`  | Plus Jakarta Sans| 28sp | Bold       | Hero state label (`Grayscale on` / `off`)  |
| `headingMedium` | Plus Jakarta Sans| 24sp | Bold       | Screen titles                              |
| `headingSmall`  | Plus Jakarta Sans| 20sp | Bold       | Wordmark text, editor titles               |
| `titleMedium`   | Plus Jakarta Sans| 18sp | Bold       | Schedule name, stat values                 |
| `bodyLarge`     | Plus Jakarta Sans| 16sp | Regular    | Input text                                 |
| `bodyMedium`    | Plus Jakarta Sans| 14sp | SemiBold   | **The one body style.** Row labels, subtitles, chip labels |
| `labelSmall`    | Plus Jakarta Sans| 12sp | Regular    | Eyebrow / section headers (`STATUS`, `ENFORCEMENT`), muted captions. `0.5sp` tracking. |
| `labelXSmall`   | Plus Jakarta Sans| 11sp | SemiBold   | Pill text, day-letter text, nav labels     |
| `mono`          | JetBrains Mono   | 22sp | Bold       | Full-size time display (pickers)           |
| `monoSmall`     | JetBrains Mono   | 14sp | Medium     | Next-schedule chip on hero, schedule times |

Headings use `-0.5sp` letter spacing. `labelSmall` uses `+0.5sp`.

There is no `bodySmall` — everything that used to be 13sp has been collapsed onto `bodyMedium`.

---

## Spacing & Sizing

All in `Dimens.kt` on `GrayoutTheme.dimens`.

| Token          | Value  | Usage                                                |
|----------------|--------|------------------------------------------------------|
| `screenPad`    | 20dp   | Horizontal padding on every screen                   |
| `cardPad`      | 16dp   | Default card internal padding                        |
| `cardPadLarge` | 24dp   | Reserved for tall hero cards                         |
| `cardGap`      | 12dp   | Gap between cards, gap inside the hero pill row      |
| `sectionGap`   | 16dp   | Gap between sections inside a card                   |
| `tightGap`     | 8dp    | The one "small gap" token. Replaces the old `itemGap`, `chipGap`, and `dayDotGap` — use this for any inside-a-component small gap. |
| `radius`       | 16dp   | Cards                                                |
| `radiusSm`     | 10dp   | Inner containers, inputs                             |
| `radiusFull`   | 999dp  | Pills, chips, toggles, dots                          |

**Rule:** never hardcode `.dp` values for spacing or radii. Use tokens.

---

## Motion

All motion constants live in the `GrayoutMotion` object in `Motion.kt`. These are the **only** motion durations the app uses — anything else is wrong.

| Constant         | Value       | Usage                                                        |
|------------------|-------------|--------------------------------------------------------------|
| `Fast`           | 180 ms      | Chip flicks, pill state, press/hover, toggle thumb           |
| `Slow`           | 320 ms      | Main state changes, card border tint, screen transitions    |
| `BreathPeriodMs` | 4000 ms     | Full period of the hero breathing pulse                      |
| `Easing`         | `FastOutSlowInEasing` | The single easing curve everywhere             |

No spring physics, no bounce, no custom easings. If you need a duration, pick `Fast` or `Slow`.

---

## Component Patterns

### `GrayoutCard`
- Background: `surface`. Corner: `dimens.radius` (16dp).
- Border: 1dp `border` by default, animates to 2dp `borderActive` when `isActive = true`. The animation uses `GrayoutMotion.Slow`.
- No elevation, no shadow. Depth is borders only.

### Eyebrow label
- A small uppercase text using `typography.labelSmall`, colored `BrandAccent.copy(alpha = 0.75f)`.
- Used at the top of sections inside a card (`STATUS`, `ENFORCEMENT`, `EXCLUSIONS`) and for section headers on the exclusions screen.
- Optionally paired with a 6dp dot to its left.

### Status hero card (home)
- One unified card that combines status + pills + toggle. There is no separate 120dp circle anymore.
- Top edge has a vertical gradient wash: `BrandAccent` at 0.08 alpha at the top, fading to `Color.Transparent` by 40% of the card height. This is the only place the wash appears.
- Contents, top to bottom:
  1. Eyebrow row: a 6dp dot (`BrandAccent` when off, `text` when on) + the `STATUS` label.
  2. `Grayscale on` / `Grayscale off` in `headingLarge`, colored `text`.
  3. A `bodyMedium` subtitle in `textMuted` ("Your screen is muted" / "Tap to mute your screen"), or an error line in `text` if the last toggle failed.
  4. A row with the enforcement interval pill (`Off`, `1m`, `5m`…) and the next-schedule summary in `monoSmall`.
  5. A divider, then the `Grayscale` label + `GrayoutToggle` row.
- When grayscale is on, the card passes `isActive = true` to `GrayoutCard`, which brightens the border.

### Enforcement pills
- Six pills in a single row, equal width (`Modifier.weight(1f)`): `Off`, `1m`, `5m`, `10m`, `15m`, `30m`.
- Shape: `radiusFull`. Min height 48dp (touch target).
- Active: background `text`, text color `bg`, no border, `bodyMedium` at `ExtraBold` weight.
- Inactive: transparent background, text `textMuted`, 1dp `border` border, `bodyMedium` at `SemiBold`.
- State changes animate over `GrayoutMotion.Fast`.

### Wordmark (home header)
- A compact lockup: the ring mark (`ic_grayout_foreground`) tinted with `BrandAccent`, 24dp, next to the text "Grayout" in `headingSmall`.
- This replaces the older freestanding app header and is the only place the wordmark appears in-app.

### `GrayoutToggle`
- Track: `text` when on, `off` when off.
- Thumb: `bg` when on, `offText` when off.
- Animates on `GrayoutMotion.Fast`. Haptic tick on press.

### `StatusDot`
- 8dp circle. Used in rows (e.g. "Service status") to indicate liveness.
- When on: fills with `text`. When off: fills with `off`.

### Bottom navigation
- 3 tabs: Home, Schedule, Settings.
- Active: icon and label in `text`. Inactive: `textMuted`.
- Bar background: `bg`. Top border: 1dp `border`.
- Labels use `labelXSmall`.

### Buttons
- Primary (Save): `text` background, `bg` label, `radius` corners, 16dp vertical padding.
- Destructive (Delete): transparent background, `danger` label, `bodyMedium`.
- Add (+): `text` background, `bg` label, full-round pill.

---

## General Rules

- **Dark theme only.** There is no light mode, and there is no plan to add one.
- **No hardcoded colors or sizes.** Every color must come from `GrayoutTheme.colors` (or `BrandAccent`), every dimension from `GrayoutTheme.dimens`, every text style from `GrayoutTheme.typography`, every duration from `GrayoutMotion`.
- **State is non-chromatic.** When something is off, inactive, or disabled, it goes gray — not a faded accent. If you find yourself tinting a failure state blue or a success state green, stop.
- **Depth is borders, not shadows.** The app uses no elevation. Cards sit on `bg`, darker than `surface`, with a 1dp border.
- **`BrandAccent` is sparingly used.** If you're about to reach for `BrandAccent` outside the cases listed in the Philosophy section, reconsider. The whole point of keeping it out of `GrayoutTheme.colors` is to create friction.
- **Generous whitespace.** Don't crowd elements. `sectionGap` between sections, `cardGap` between cards.
- **Monospace only for time/number displays.** `mono` and `monoSmall` are reserved for actual numeric values the user reads as data (schedule times, next-run countdown). They are never used for regular body copy.
