# App Icon Design — Grayout

## Overview

Replace the default Android template icon with a custom Grayout icon: concentric circles radiating outward from a solid center dot, on a near-black background. The design reflects the app's grayscale theme — monochromatic, minimal, and geometric.

## Approach

**Approach A (selected): Clean replacement with custom naming.** Create new vector drawables with `ic_grayout_*` naming, place adaptive icons in `mipmap-anydpi-v26/`, delete all legacy raster and template files. MinSdk 26 guarantees adaptive icon support, so webp fallbacks are unnecessary.

## Foreground — `drawable/ic_grayout_foreground.xml`

- **Viewport**: 108dp x 108dp (standard adaptive icon canvas)
- **Safe zone**: Inner 72dp centered area (content stays within 18–90dp range)
- **Center**: (54, 54)
- **Elements** (all circles drawn as path arcs):

| Element     | Radius | Style                  | Color     | Alpha |
|-------------|--------|------------------------|-----------|-------|
| Outer ring  | 28     | Stroke, width 2        | `#D4D4D4` | 0.2   |
| Mid ring    | 20     | Stroke, width 2        | `#D4D4D4` | 0.5   |
| Inner ring  | 12     | Stroke, width 2        | `#D4D4D4` | 0.8   |
| Center dot  | 4      | Solid fill             | `#D4D4D4` | 1.0   |

All content fits within the 72dp safe zone (outermost ring edge: 54 + 28 + 1 = 83dp, well within 18–90dp bounds).

## Background — `drawable/ic_grayout_background.xml`

- **Viewport**: 108dp x 108dp
- **Fill**: Solid `#0B0B0B` (matches the app's `bg` design token)

## Adaptive Icon — `mipmap-anydpi-v26/`

Both `ic_launcher.xml` and `ic_launcher_round.xml`:

```xml
<adaptive-icon>
    <background android:drawable="@drawable/ic_grayout_background" />
    <foreground android:drawable="@drawable/ic_grayout_foreground" />
    <monochrome android:drawable="@drawable/ic_grayout_foreground" />
</adaptive-icon>
```

Monochrome layer reuses the foreground for Android 13+ themed icons.

## Files Deleted

- `drawable/ic_launcher_foreground.xml` — default Android robot
- `drawable/ic_launcher_background.xml` — default green grid
- `mipmap-anydpi/ic_launcher.xml` — old adaptive icon location
- `mipmap-anydpi/ic_launcher_round.xml` — old adaptive icon location
- `mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp` — legacy rasters (5 files)
- `mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_round.webp` — legacy rasters (5 files)

Total: 14 files removed.

## No Changes Needed

- **AndroidManifest.xml** — already references `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`, which resolve to the new `mipmap-anydpi-v26/` files.

## Technical Notes

- Android VectorDrawable has no `<circle>` element. Circles are drawn as two-arc paths: `M cx-r,cy a r,r 0 1,1 2r,0 a r,r 0 1,1 -2r,0 z`
- Opacity is applied via `android:alpha` on each `<path>` element.
- Stroke circles use `android:strokeColor` and `android:strokeWidth` with `android:fillColor="#00000000"` (transparent fill).
