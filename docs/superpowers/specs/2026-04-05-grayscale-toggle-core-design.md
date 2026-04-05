# Grayscale Toggle Core — Design Spec

## Context

Grayout needs its core toggle mechanism: the ability to read and write Android's grayscale (daltonizer) system setting, expose that state to the UI via a ViewModel, and display it on the Home screen. This is the foundation that the foreground service and enforcement loop will build on later.

Scope: GrayscaleManager, HomeViewModel, MainActivity wiring, and a minimal Home screen showing current state. No permissions, no service, no scheduling.

## Components

### 1. GrayscaleManager

**File:** `app/src/main/java/com/princeyadav/grayout/service/GrayscaleManager.kt`

- Class takes `ContentResolver` as constructor parameter
- Companion constants:
  - `DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"`
  - `DALTONIZER_MODE = "accessibility_display_daltonizer"`
- `isGrayscaleEnabled(): Boolean` — reads `Settings.Secure.getInt(contentResolver, DALTONIZER_ENABLED, 0)`, returns `true` if `1`
- `setGrayscale(enabled: Boolean)`:
  - Writes `DALTONIZER_ENABLED` to `1` (on) or `0` (off)
  - Writes `DALTONIZER_MODE` to `0` (monochromacy/grayscale) when enabling, `-1` (normal) when disabling
- No try-catch, no permission checking — kept minimal

### 2. HomeViewModel + Factory

**File:** `app/src/main/java/com/princeyadav/grayout/viewmodel/HomeViewModel.kt`

**HomeViewModel:**
- Extends `ViewModel()`
- Constructor takes `GrayscaleManager`
- Private `_isGrayscaleOn = MutableStateFlow(false)`
- Public `isGrayscaleOn: StateFlow<Boolean> = _isGrayscaleOn.asStateFlow()`
- `init` block: reads `grayscaleManager.isGrayscaleEnabled()` and sets initial StateFlow value
- `toggleGrayscale()`: flips current value, calls `grayscaleManager.setGrayscale(newValue)`, updates StateFlow

**HomeViewModelFactory:**
- Implements `ViewModelProvider.Factory`
- Constructor takes `GrayscaleManager`
- `create()` returns `HomeViewModel(grayscaleManager)`
- Lives in the same file as HomeViewModel

### 3. MainActivity Wiring

**File:** `app/src/main/java/com/princeyadav/grayout/MainActivity.kt` (modify)

- Add `by viewModels` delegate with `HomeViewModelFactory`
- Create `GrayscaleManager(applicationContext.contentResolver)` inside the factory lambda
- Pass `homeViewModel` to `GrayoutNavGraph`

### 4. NavGraph Changes

**File:** `app/src/main/java/com/princeyadav/grayout/ui/navigation/NavGraph.kt` (modify)

- Add `homeViewModel: HomeViewModel` parameter to `GrayoutNavGraph`
- Home route: replace `PlaceholderScreen("Home")` with inline composable that:
  - Collects `homeViewModel.isGrayscaleOn` via `collectAsState()`
  - Displays "Grayscale: ON" or "Grayscale: OFF" using `GrayoutTheme.typography.headingLarge` and `GrayoutTheme.colors.text`
  - Centered in a Box, same layout as current PlaceholderScreen
- Other routes remain as PlaceholderScreen

### 5. Dependency Addition

**Files:** `gradle/libs.versions.toml` + `app/build.gradle.kts`

- Add `lifecycle-viewmodel-ktx` using existing `lifecycleRuntimeKtx` version (2.6.1)
- Catalog alias: `androidx-lifecycle-viewmodel-ktx`

## Files Changed

| File | Action |
|------|--------|
| `gradle/libs.versions.toml` | Add viewmodel-ktx library entry |
| `app/build.gradle.kts` | Add viewmodel-ktx implementation dependency |
| `service/GrayscaleManager.kt` | **New** |
| `viewmodel/HomeViewModel.kt` | **New** |
| `MainActivity.kt` | Modify — add viewModel delegate, pass to NavGraph |
| `ui/navigation/NavGraph.kt` | Modify — accept viewModel param, show state in Home route |

## Verification

1. Build the project — should compile without errors
2. Install on device/emulator that has `WRITE_SECURE_SETTINGS` granted via ADB
3. Launch app — Home screen shows "Grayscale: OFF"
4. Verify the GrayscaleManager reads/writes correctly by toggling via ADB:
   - `adb shell settings put secure accessibility_display_daltonizer_enabled 1`
   - `adb shell settings put secure accessibility_display_daltonizer 0`
   - Relaunch app — should show "Grayscale: ON"
5. (Full toggle testing happens once UI toggle is built in next step)
