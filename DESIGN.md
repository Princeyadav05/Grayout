# Grayout — Design Specification

This document defines the visual language for the Grayout Android app. It reflects the current state of the theme files under `app/src/main/java/com/princeyadav/grayout/ui/theme/`. Follow these tokens, patterns, and general rules when building any Jetpack Compose UI.

---

## Philosophy

Grayout is a grayscale-enforcement app, and most of the time its users see it in grayscale too. Depth and hierarchy come from **luminance**: layered blacks, border weights, and text brightness.

**Color reinforces, it never carries meaning alone.** The app uses a small, deliberate palette (lavender, mint, amber, coral, plus per-schedule colors), but every colored state also has a word, and every problem also has an icon. Check any new UI in grayscale: if a state can only be told apart by hue, it is wrong.

- On/off is expressed through the switch, layout, text weight, and luminance. Color only adds to it.
- Healthy states are words (`Granted`, `Unrestricted`) in `success`. Problems are a Material warning icon + words in `warning`, plus an action (`Fix`, `Try again`). Errors use `danger`.
- Healthy rows get no icon. Never use a tick or check glyph, and never a bare "!", as an icon.

`BrandAccent = #B5A0D8` (dusty lavender) is the brand and "now/active" color. It is available both as a standalone top-level `val` and as `GrayoutTheme.colors.accent`. It is used for:
- Eyebrow labels (`STATUS`, `AUTOMATIC RESTORE`, `KEPT IN COLOR`) at 0.75 alpha
- The vertical gradient wash at the top of the Status card and the Schedules empty-state card
- Active switch tracks and the selected bottom-nav pill
- The "Now" pill on a running schedule, and the first schedule color
- The foreground notification accent, the launcher icon tint, and the wordmark
- The flash on the ADB command sheet `Copy` confirmation, and exclusions section headers

---

## Color Tokens

All tokens live in `Color.kt` and are exposed on `GrayoutTheme.colors`. `BrandAccent` is also a standalone top-level `val` (the notification needs a raw ARGB int).

| Token             | Hex       | Usage                                                        |
|-------------------|-----------|--------------------------------------------------------------|
| `bg`              | `#0B0B0B` | Scaffold / screen background                                 |
| `surface`         | `#161616` | Card backgrounds                                             |
| `border`          | `#2A2A2A` | Card borders, dividers, inactive chip borders                |
| `borderActive`    | `#555555` | Borders on active/selected cards                             |
| `text`            | `#E8E8E8` | Primary text, active chip fill                               |
| `textMuted`       | `#999999` | Secondary / supporting text, inactive nav and chip text      |
| `textDim`         | `#4A4A4A` | Decorative glyphs only (chevrons). Never real text.          |
| `off`             | `#3A3A3A` | Toggle off track, inactive pill background                   |
| `offText`         | `#666666` | Thumb on an off toggle. Not for text.                        |
| `danger`          | `#FF6B6B` | Destructive actions and errors (`Couldn't turn on grayscale`) |
| `success`         | `#6BE8A0` | Healthy status words (`Granted`, `Unrestricted`, `On`)       |
| `warning`         | `#FFC857` | Problems that need the user (`Not granted`, `Needs the grayscale permission`) |
| `accent`          | `#B5A0D8` | Brand, "now", active switch track (see Philosophy)           |
| `schedulePalette` | `#B5A0D8`, `#5FD4C4`, `#F4A78B` | Per-schedule identity color (dot, Today strip), picked from the schedule id |

Contrast when used as text (WCAG, on `bg` / `surface`): `textMuted` 6.9 / 6.4, `accent` 8.4 / 7.7, `success` 12.8 / 11.8, `warning` 12.8 / 11.8, `danger` 7.1 / 6.5. All pass 4.5:1. Check composited colors (alpha over a fill), not just the token.

**Rule:** never hardcode a `Color(0x...)` in UI code. Pull from `GrayoutTheme.colors`.

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
| `labelSmall`    | Plus Jakarta Sans| 12sp | Regular    | Eyebrow / section headers (`STATUS`, `AUTOMATIC RESTORE`), muted captions. `0.5sp` tracking. |
| `labelXSmall`   | Plus Jakarta Sans| 11sp | SemiBold   | Pill text, day-letter text, nav labels     |
| `mono`          | JetBrains Mono   | 22sp | Bold       | Automatic restore value, time pickers      |
| `monoSmall`     | JetBrains Mono   | 14sp | Medium     | Schedule times                             |

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
| `cardGap`      | 12dp   | Gap between cards                                    |
| `sectionGap`   | 16dp   | Gap between sections inside a card                   |
| `tightGap`     | 8dp    | The one "small gap" token. Replaces the old `itemGap`, `chipGap`, and `dayDotGap` — use this for any inside-a-component small gap. |
| `radius`       | 16dp   | Cards                                                |
| `radiusSm`     | 10dp   | Inner containers, inputs                             |
| `radiusFull`   | 999dp  | Pills, chips, toggles, dots                          |
| `minTouch`     | 48dp   | Minimum touch target (day buttons, inline actions)   |
| `navBarPadV`   | 12dp   | Vertical padding inside the bottom navigation bar    |
| `dotSize`      | 10dp   | Schedule color dot                                   |
| `stripHeight`  | 28dp   | Height of the Today strip on Schedules               |
| `iconSm`       | 16dp   | Inline problem icon (warning) next to text           |
| `stepSize`     | 24dp   | Numbered step circle in the setup guide              |

**Rule:** never hardcode `.dp` values for spacing or radii. Use tokens.

---

## Motion

All motion constants live in the `GrayoutMotion` object in `Motion.kt`. These are the **only** motion durations the app uses — anything else is wrong.

| Constant         | Value       | Usage                                                        |
|------------------|-------------|--------------------------------------------------------------|
| `Fast`           | 180 ms      | Chip flicks, pill state, press/hover, toggle thumb           |
| `Slow`           | 320 ms      | Main state changes, card border tint, screen transitions    |
| `Easing`         | `FastOutSlowInEasing` | The single easing curve everywhere             |

No spring physics, no bounce, no custom easings. If you need a duration, pick `Fast` or `Slow`.

---

## Component Patterns

### `GrayoutCard`
- Background: `surface`. Corner: `dimens.radius` (16dp).
- Border: 1dp `border` by default, animates to 2dp `borderActive` when `isActive = true`. The animation uses `GrayoutMotion.Slow`.
- No elevation, no shadow. Depth is borders only.
- `wash = true` opts into the hero accent wash. Default is false. Only the Home Status/Setup card and the Schedules empty-state card pass `wash = true`.

### Eyebrow label
- A small uppercase text using `typography.labelSmall`, colored `BrandAccent.copy(alpha = 0.75f)`.
- Used at the top of sections inside a card (`STATUS`, `AUTOMATIC RESTORE`, `KEPT IN COLOR`, `SETUP`) and for section headers on the exclusions screen.
- Optionally paired with a 6dp dot to its left.

### Home cards
Three cards, each fact shown once:
- **Status** (`STATUS` eyebrow, accent wash, `isActive` when grayscale is on): "Grayscale on/off" in `headingLarge` with the switch on the same row. A hairline divider, then the schedule row: schedule color dot, a `labelSmall` "Schedule" caption, and "Daily focus, until 11:00 PM" (running, with the lavender "Now" pill) / "Next: Work, Tue 9:00 AM" / "No upcoming schedules". The row opens Schedules (`onClickLabel = "Open schedules"`).
  - Before the grayscale permission is granted the card is replaced by the **Setup** card: "One-time setup", one sentence, and a filled "Show the command" pill that opens the setup guide.
  - If a write fails while the permission is granted, an inline row under the switch shows the warning icon + "Couldn't turn on grayscale" in `danger` + an underlined "Try again". Polite live region.
- **Automatic restore**: the current value in `mono` ("5 min" / "Off") with an outlined "Change" pill, plus one `labelSmall` helper line. "Change" opens a bottom sheet with a radio list (Off, 1, 5, 10, 15, 30 min); radios use `accent`.
- **Kept in color**: "3 apps" / "No apps yet", up to three app icons, "+N", chevron. When apps are kept in color but usage access is off: warning icon + "Paused: usage access is off" in `warning` + "Fix".

### Wordmark (home header)
- A compact lockup: the ring mark (`ic_grayout_foreground`) tinted with `BrandAccent`, 24dp, next to the text "Grayout" in `headingSmall`.
- This replaces the older freestanding app header and is the only place the wordmark appears in-app.

### `GrayoutToggle`
- Track: `accent` (lavender) when on, `off` when off.
- Thumb: `bg` when on, `offText` when off.
- Animates on `GrayoutMotion.Fast`. Haptic tick on press.

### `StatusDot`
- 8dp circle. Used in rows (e.g. "Service status") to indicate liveness.
- When on: fills with `text`. When off: fills with `off`.

### Bottom navigation
- 3 tabs: Home, Schedules, Settings.
- Active: icon in `accent` on an `accent` 0.2 alpha pill, label in `text` ExtraBold. Inactive: icon and label in `textMuted`.
- Row padding is `navBarPadV` top and bottom, plus the system navigation-bar inset.
- Bar background: `bg`. Top border: 1dp `border`.
- Labels use `labelXSmall`.

### Category chips (exclusions)
- A horizontally scrolling row under the search field: `All`, then one chip per category (`Camera`, `Photos`, `Maps`) that has at least one installed app. The row is hidden when no category has apps.
- Active: `text` fill, `bg` label, ExtraBold. Inactive: transparent, `textMuted` label, 1dp `border`. Animated on `GrayoutMotion.Fast`.
- Chips are wrap-content: the painted pill uses `cardGap` horizontal and `tightGap` vertical padding, centered inside a 48dp-tall touch target.
- Label in `bodyMedium`; app count after it in `labelXSmall`, `textMuted` (inactive) or opaque `bg` (active), `tightGap` apart.
- Radio semantics: the row is a `selectableGroup`, each chip is `selectable` with `Role.RadioButton`.

### Header action pill
- A small outlined pill on the right of a section header (`Exclude all` on a category section).
- 1dp `borderActive` outline, `text` label in `labelSmall` ExtraBold, `radiusFull`, `cardGap` / `tightGap` padding inside a 48dp touch target.
- Done state (`All excluded`): `border` outline, `textMuted` label, SemiBold, disabled.

### Undo snackbar
- Bottom of the screen, `screenPad` horizontal inset: `surface` background, 1dp `borderActive`, `radiusSm` corners.
- Message in `bodyMedium` `text`; action (`Undo`) in `bodyMedium` ExtraBold `text` with a 48dp touch target.
- Short duration. Polite live region so TalkBack announces it.

### Setup guide (ADB command sheet)
- `ModalBottomSheet` titled "One-time setup", three steps: turn on Developer options, turn on USB debugging, run the command.
- Step circles are `stepSize`: done = filled `text` circle with a `bg` number; current = 1dp `accent` outline with an `accent` number; upcoming = 1dp `border` outline with a `textMuted` number. **Finished steps never show a tick.**
- Steps advance on their own: the sheet polls Developer options, USB debugging, and the grant while visible.
- Actions are outlined pills (1dp `borderActive`, `labelSmall` ExtraBold) inside a `minTouch` target: "Open About phone", "Open Developer options", "Copy", "Share". The primary action ("Try grayscale") is a filled `text` pill.
- Footer status is a polite live region: "Permission granted" in `success`, or "Waiting for the grant" in `textMuted`.

### Buttons
- Primary (Save): `text` background, `bg` label, `radius` corners, 16dp vertical padding.
- Destructive (Delete): transparent background, `danger` label, `bodyMedium`.
- Add (+): `text` background, `bg` label, full-round pill.

---

## General Rules

- **Dark theme only.** There is no light mode, and there is no plan to add one.
- **No hardcoded colors or sizes.** Every color must come from `GrayoutTheme.colors` (or `BrandAccent`), every dimension from `GrayoutTheme.dimens`, every text style from `GrayoutTheme.typography`, every duration from `GrayoutMotion`.
- **Color reinforces, never alone.** Every colored state has a word; every problem has an icon and an action. Check new UI in grayscale.
- **Icons for problems only.** Use Material icons (`Icons.Filled.Warning`, `Info`, `Refresh`) where the user must act. No icons on healthy rows, no tick or check glyphs, no bare "!".
- **Depth is borders, not shadows.** The app uses no elevation. Cards sit on `bg`, darker than `surface`, with a 1dp border.
- **Lavender means brand or "now".** Use `accent` for the cases in the Philosophy section, not as a general highlight.
- **Generous whitespace.** Don't crowd elements. `sectionGap` between sections, `cardGap` between cards.
- **Monospace only for time/number displays.** `mono` and `monoSmall` are reserved for actual numeric values the user reads as data (schedule times, next-run countdown). They are never used for regular body copy.
