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
- Section labels (`STATUS`, `PERMISSIONS`, `NAME`, `TODAY`, `KEPT IN COLOR · 3`), opaque
- The vertical gradient wash at the top of the Status card and the Schedules empty-state card
- Active switch tracks and the selected bottom-nav pill
- The "Now" pill on a running schedule, and the first schedule color
- The foreground notification accent, the launcher icon tint, and the wordmark
- The flash on the ADB command sheet `Copy` confirmation

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
| `textMuted`       | `#999999` | Secondary / supporting text, inactive nav and chip text, the off toggle thumb, the editor's time arrow |
| `off`             | `#3A3A3A` | Toggle off track, inactive pill background                   |
| `offText`         | `#666666` | Disabled editor actions while a schedule loads. Not for text. |
| `danger`          | `#FF6B6B` | Destructive actions and errors (`Couldn't turn on grayscale`) |
| `success`         | `#6BE8A0` | Healthy status words (`Granted`, `Unrestricted`, `On`)       |
| `warning`         | `#FFC857` | Problems that need the user (`Not granted`, `Needs the grayscale permission`) |
| `accent`          | `#B5A0D8` | Brand, "now", active switch track (see Philosophy)           |
| `schedulePalette` | `#B5A0D8`, `#238174`, `#F8C6B4` | Per-schedule identity color (dot, Today strip), picked from the schedule id. Lavender, deep teal, pale peach: pairwise luminance ratio 2.01 / 1.53 / 3.08 (accent-teal / accent-peach / teal-peach), so they stay apart in grayscale, and each is at least 3:1 on `surface` (7.7 / 3.9 / 11.8) |

Contrast when used as text (WCAG, on `bg` / `surface`): `textMuted` 6.9 / 6.4, `accent` 8.4 / 7.7, `success` 12.8 / 11.8, `warning` 12.8 / 11.8, `danger` 7.1 / 6.5. All pass 4.5:1. Check composited colors (alpha over a fill), not just the token.

**Rule:** never hardcode a `Color(0x...)` in UI code. Pull from `GrayoutTheme.colors`.

The `MaterialTheme` color scheme in `Theme.kt` is wired from these same tokens so that stock Material 3 components (`HorizontalDivider`, `Text`, etc.) also pick up the theme automatically.

---

## Typography

Two font families, bundled as TTFs in `res/font` (SIL Open Font License, see `licenses/Geist-OFL.txt`):
- **Geist** — weights 400 / 500 / 600 / 700 / 800
- **Geist Mono** — weights 500 / 700

Styles are defined on `GrayoutTheme.typography`.

| Style           | Font             | Size | Weight     | Usage                                      |
|-----------------|------------------|------|------------|--------------------------------------------|
| `headingLarge`  | Geist            | 28sp | Bold       | Hero state label (`Grayscale on` / `off`)  |
| `headingMedium` | Geist            | 24sp | Bold       | Screen titles                              |
| `headingSmall`  | Geist            | 20sp | Bold       | Wordmark text, editor titles               |
| `titleMedium`   | Geist            | 18sp | Bold       | Schedule name, stat values                 |
| `bodyLarge`     | Geist            | 16sp | Regular    | Input text                                 |
| `bodyMedium`    | Geist            | 14sp | SemiBold   | **The main body style.** Row labels, subtitles, chip labels |
| `bodyRegular`   | Geist            | 14sp | Regular    | Every supporting sentence: Settings row subtitles, the About line, the restore helper, problem lines, "Ends next day", the paused line, the setup explanation. Never row labels. |
| `labelSmall`    | Geist            | 12sp | Regular    | Section labels (`STATUS`, `AUTOMATIC RESTORE`) and short tags only (`START` / `END`, the Home "Schedule" caption, repeat days). Never a sentence. `0.5sp` tracking. |
| `labelXSmall`   | Geist            | 11sp | SemiBold   | Pill text, day-letter text, nav labels     |
| `mono`          | Geist Mono       | 22sp | Bold       | Automatic restore value ("5 min"; "Off" uses `titleMedium`), time pickers |
| `monoSmall`     | Geist Mono       | 14sp | Medium     | Schedule times                             |

Headings use `-0.5sp` letter spacing. `labelSmall` uses `+0.5sp`.

There is no `bodySmall`. A sentence uses `bodyMedium` or `bodyRegular`, never the tracked `labelSmall`.

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
| `microGap`     | 4dp    | Inside pills and between an icon and its word        |
| `tightGap`     | 8dp    | The one "small gap" token. Replaces the old `itemGap`, `chipGap`, and `dayDotGap` — use this for any inside-a-component small gap. |
| `radius`       | 16dp   | Cards                                                |
| `radiusSm`     | 10dp   | Inner containers, inputs                             |
| `radiusFull`   | 999dp  | Pills, chips, toggles, dots                          |
| `minTouch`     | 48dp   | Minimum touch target (day buttons, inline actions)   |
| `navBarPadV`   | 12dp   | Vertical padding inside the bottom navigation bar    |
| `dotSize`      | 10dp   | Schedule color dot                                   |
| `stripHeight`  | 28dp   | Height of the Today strip (Schedules and Home)       |
| `stripGap`     | 2dp    | `bg` gap between Today strip segments and lanes      |
| `stripMarker`  | 2dp    | Width of the Today strip now marker                  |
| `iconSm`       | 16dp   | Inline problem icon (warning) next to text           |
| `iconMd`       | 20dp   | Icons inside the search field (Search, Clear)        |
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

### Section label
- `SectionLabel(text)` in `ui/components/SectionLabel.kt` is the only section label. It uppercases its text and uses `typography.labelSmall` in opaque `accent` (7.7:1 on `surface`, 8.4:1 on `bg`). No alpha.
- Used on every screen: Home (`STATUS`, `AUTOMATIC RESTORE`, `KEPT IN COLOR`, `SETUP`), Settings (`PERMISSIONS`, `ABOUT`), the editor (`NAME`, `TIME RANGE`, `REPEAT ON`), Schedules (`TODAY`) and the Kept in color list headers (`KEPT IN COLOR · 3`, `ALL APPS · 19`).
- Sub-labels inside a section (the editor's `START` / `END`, the Home "Schedule" caption) stay `labelSmall` `textMuted`.

### Home cards
Three cards, each fact shown once:
- **Status** (`STATUS` label, accent wash, `isActive` when grayscale is on): "Grayscale on/off" in `headingLarge` with the switch on the same row. A hairline divider, then the schedule row: a `labelSmall` `textMuted` caption ("Schedule", or "Schedules" when several run) with the lavender "Now" pill next to it while one runs, then one line per running schedule, each with its own color dot: "Daily focus until 11:00 PM". The name ellipsizes on one line; "until <end>" never wraps. When nothing runs: one dotted line "Next: Work, Tue 9:00 AM", or "No upcoming schedules" with no dot. The chevron always shows, at the end of the caption row; the row opens Schedules (`onClickLabel = "Open schedules"`).
  - Under the schedule lines, when an enabled schedule has a window today, the same `TodayStrip` as Schedules, with no sentence (the lines above already say what runs). It is part of the tappable row and hidden from TalkBack. Home refreshes it every minute while resumed.
  - Before the grayscale permission is granted the card is replaced by the **Setup** card: "One-time setup", one sentence, and a filled "Show the command" pill that opens the setup guide.
  - If a write fails while the permission is granted, an inline row under the switch shows the warning icon + "Couldn't turn on grayscale" in `danger` + an underlined "Try again". Polite live region.
- **Automatic restore**: the current value, "5 min" in `mono` or "Off" in `titleMedium`, with an outlined "Change" pill, plus one `bodyRegular` helper line ("No timed restore. Schedules still apply." when off). "Change" opens a bottom sheet with a radio list (Off, 1, 5, 10, 15, 30 min; radios use `accent`) and an "Only during schedules" switch ("Outside a schedule, off stays off."). With it on, the helper reads "Turns grayscale back on during schedules." and the notification shows "Grayscale comes back with the next schedule" when a restore would land outside a window.
- **Notification prompt** (only once, right after automatic restore is first turned on while notifications are off): an active card, "See when grayscale comes back?", one sentence, "Allow notifications" (filled) and "Not now" (outlined). Either answer is remembered. The app never asks at launch.
- **Kept in color**: "3 apps" / "None yet. Grayscale applies to every app.", up to three app icons, "+N", chevron. The whole card is the tap target, so the row has no `minTouch` height and the padding is even top and bottom. When apps are kept in color but usage access is off: warning icon + "Paused: usage access is off" in `warning` + "Fix".

### Wordmark (home header)
- A compact lockup: the ring mark (`ic_grayout_foreground`) tinted with `BrandAccent`, 24dp, next to the text "Grayout" in `headingSmall`.
- This replaces the older freestanding app header and is the only place the wordmark appears in-app.

### `GrayoutToggle`
- Track: `accent` (lavender) when on, `off` when off.
- Thumb: `bg` when on, `textMuted` when off (3.99:1 on the `off` track, so an off toggle reads as off, not disabled, also in grayscale).
- Animates on `GrayoutMotion.Fast`. Haptic tick on press.

### Schedules list
- Header "Schedules" + filled "+ Add" pill.
- **Today card**: `TODAY` section label, then `TodayStrip` (`ui/components/TodayStrip.kt`, shared with Home): a `stripHeight` strip (`bg` fill, 1dp `border`, `radiusSm`) with today's windows in each schedule's color and a `stripMarker` `text` now marker with a "Now" label (`labelXSmall` `text`) above it, centered on the marker and clamped inside the strip near either edge, axis labels in `labelXSmall` `textMuted`. Each segment is inset by `stripGap`, so touching windows show a `bg` gap in grayscale. Where windows overlap, the strip height splits into thin lanes, one per overlapping schedule (`stripLanes` in `logic/ScheduleDisplay.kt`), so no segment covers another; elsewhere segments use the full height. Then and one sentence ("Daily focus is on until 11:00 PM" / "On now: Focus until 10:00 AM, Night until 6:00 AM" when several run / "Next: Work at 9:00 AM" / "Nothing else scheduled today"). The card reads as one node: "Today. <sentence>". It refreshes every minute while visible.
- **Rows** in one card, hairline dividers: schedule color dot (`dotSize`), bold name, lavender "Now" pill when running, `monoSmall` "9:00 PM → 11:00 PM" in `text` on its own line (an overnight window adds " next day" in `bodyRegular` `textMuted`, from `endsNextDay` in `logic/ScheduleDisplay.kt`), then the days in words on the next line in `labelSmall` `textMuted` (Every day, Weekdays, Weekends, or "Mon, Wed, Fri"), switch at the end. Off rows stay fully readable; the switch is the only on/off signal.
- Labels only for problems, under the row: warning icon + "Needs the grayscale permission" + "Fix"; info icon + "Might start a few minutes late" + "Allow" (exact alarms); a refused enable ("Overlaps with \"Work\"") as a polite live region.
- Schedule colors come from `schedulePalette`, picked by `scheduleColorIndex(id)`, so they never shift when another schedule is deleted.

### Schedule editor
- Day buttons are `minTouch` boxes with a 40dp painted circle, laid out in a `FlowRow` with `Arrangement.SpaceBetween` (`tightGap` between wrapped rows), so all seven fit one row on a ~411dp phone and wrap to a second row on narrower screens or a larger display size instead of shrinking. Presets (Every day, Weekdays, Weekends) wrap with `tightGap`.
- Start and end times are `mono` buttons in a 1dp `border` outline with `radiusSm` corners (`cardGap` / `tightGap` padding, `minTouch` height), so they read as tappable. Both are `weight(1f)`, so they are the same width, around a `minTouch`-wide arrow in `textMuted`. When the end is not after the start, "Ends next day" (`bodyRegular` `textMuted`) sits under the end box and the end announces "..., next day". They announce "Start time, 9:00 PM". Tapping one opens a bottom sheet with the Material 3 `TimePicker` themed from tokens (`accent` selector and selected fields, `bg` dial, `text` numbers) and Cancel / Set pills. No platform time dialog.

### Status value and problem rows (Settings)
- `StatusValue(text, isProblem)`: healthy is a plain word ("Granted", "Unrestricted") in `success`; a problem is the Material warning icon (`iconSm`) + the word in `warning`.
- Optional settings that are off (Notifications) show a neutral "Off" in `textMuted`, not a problem.
- Problem rows add a `bodyRegular` subtitle that says why it matters ("Needed to keep apps in color") and open the fix on tap. Healthy rows have no icon and no subtitle.
- Every row you can tap ends in a Material chevron (`KeyboardArrowRight`) in `textMuted`, healthy or not (Grayscale permission, Notifications). Rows you cannot tap show none.
- `SettingsRow` layout: label, status and chevron share the top line; the subtitle sits below that line and uses the full card width. Every row is at least `minTouch` tall with `cardGap` vertical padding.
- The "Settings" title sits in a `minTouch`-tall row, like the Schedules header, so titles line up across tabs.
- Settings has two cards: PERMISSIONS (grayscale permission, usage access, alarms & reminders when needed, notifications, battery) and ABOUT ("Grayout 1.4.0", then `aboutStatusLine` in `logic/AboutStatus.kt`: the same grayscale state Home shows, then the service state, for example "Grayscale on. Background service idle, nothing to watch." "Idle" names the service, never grayscale).

### Bottom navigation
- 3 tabs: Home, Schedules, Settings.
- Active: icon in `accent` on an `accent` 0.2 alpha pill, label in `text` ExtraBold. Inactive: icon and label in `textMuted`.
- Row padding is `navBarPadV` top and bottom, plus the system navigation-bar inset.
- Bar background: `bg`. Top border: 1dp `border`.
- Labels use `labelXSmall`.

### Chevron
- `Chevron()` in `SettingsRow.kt`: the Material `KeyboardArrowRight` icon in `textMuted`, decorative (`contentDescription = null`). Used on Home rows that open another screen and on every tappable Settings row. No "›" text glyph.

### Usage access banner (exclusions)
- Shown on "Kept in color" (the exclusions screen) while usage access is off. A plain `GrayoutCard`, not a red card.
- Kept short so the app list starts high: warning icon (`iconSm`) + "Usage access is off." in `warning`, then an outlined pill "Allow usage access" (the same `PillButton` as Home's "Change").
- While it shows, the first list header gets a `bodyRegular` `textMuted` line: "Paused until usage access is allowed".

### Kept in color wording
- The screen says "Kept in color" / "keep in color" everywhere a user can see or hear it: list header `KEPT IN COLOR · 3`, "Keep all in color" / "All kept in color", the snackbar "3 apps kept in color", and toggles announced as "Keep Camera in color". Never "Exclude", "Excluded" or "Exclusions" in copy. Code identifiers and prefs keys keep their `exclusion` names.
- Search field: a leading Material `Search` icon (`iconMd`, `textMuted`) and the placeholder "Search apps".

### Category chips (exclusions)
- A horizontally scrolling row under the search field: `All`, then one chip per category (`Camera`, `Photos`, `Maps`) that has at least one installed app. The row is hidden when no category has apps.
- Active: `text` fill, `bg` label, ExtraBold. Inactive: transparent, `textMuted` label, 1dp `border`. Animated on `GrayoutMotion.Fast`.
- Chips are wrap-content: the painted pill uses `cardGap` horizontal and `tightGap` vertical padding, centered inside a 48dp-tall touch target.
- Label in `bodyMedium`; app count after it (only when 2 or more) in `labelXSmall`, `textMuted` (inactive) or opaque `bg` (active), `tightGap` apart.
- Radio semantics: the row is a `selectableGroup`, each chip is `selectable` with `Role.RadioButton`.

### Header action pill
- A small outlined pill on the right of a section header (`Keep all in color` on a category section).
- 1dp `borderActive` outline, `text` label in `labelSmall` ExtraBold, `radiusFull`, `cardGap` / `tightGap` padding inside a 48dp touch target.
- Done state (`All kept in color`): `border` outline, `textMuted` label, SemiBold, disabled.

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
- Destructive (Delete): transparent background, Material delete icon (`iconSm`) + `danger` label in `bodyMedium`, so it does not read as disabled gray in grayscale.
- Add (+): `text` background, `bg` label, full-round pill.

---

## General Rules

- **Dark theme only.** There is no light mode, and there is no plan to add one.
- **No hardcoded colors or sizes.** Every color must come from `GrayoutTheme.colors` (or `BrandAccent`), every dimension from `GrayoutTheme.dimens`, every text style from `GrayoutTheme.typography`, every duration from `GrayoutMotion`.
- **Color reinforces, never alone.** Every colored state has a word; every problem has an icon and an action. Check new UI in grayscale.
- **Icons for problems and actions only.** Use Material icons (`Icons.Filled.Warning`, `Info`, `Refresh`, `Delete`, the `KeyboardArrowRight` chevron) where the user must act. No icons on healthy rows, no tick or check glyphs, no bare "!", no text glyphs ("›") as icons.
- **Depth is borders, not shadows.** The app uses no elevation. Cards sit on `bg`, darker than `surface`, with a 1dp border.
- **Lavender means brand or "now".** Use `accent` for the cases in the Philosophy section, not as a general highlight.
- **Generous whitespace.** Don't crowd elements. `sectionGap` between sections, `cardGap` between cards.
- **Monospace only for time/number displays.** `mono` and `monoSmall` are reserved for actual numeric values the user reads as data (schedule times, next-run countdown). They are never used for regular body copy.
