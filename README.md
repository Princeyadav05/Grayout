# Grayout

A personal Android app that enforces grayscale mode to reduce phone addiction.

## What It Does

- Toggle grayscale on/off with a single tap
- Automatic enforcement on a timer so grayscale turns back on after you disable it
- Per-app exclusions so specific apps stay in color
- Schedule grayscale for specific days and time windows
- Quick Settings tiles for fast access
- Runs as a foreground service using `WRITE_SECURE_SETTINGS` to control display settings

## Requirements

- Android 8.0+ (API 26)
- One-time ADB setup for the secure settings permission

## Install

### Option 1: F-Droid client (recommended)

Install [Droidify](https://github.com/Droid-ify/client) or any F-Droid-compatible client. Grayout is available on the [IzzyOnDroid](https://apt.izzysoft.de/fdroid/) repo, which Droidify includes by default. Search for "Grayout" and install.

This is the easiest way to install and get updates. It also avoids the Google Play Protect warning that browser downloads trigger (see note below).

### Option 2: ADB install

Download the latest APK from [Releases](https://github.com/Princeyadav05/Grayout/releases/latest), then install with ADB:

```bash
adb install grayout-v*.apk
```

You already need ADB for the permission setup below, so this adds no extra steps. ADB installs are not affected by Play Protect.

### Option 3: Direct download

Download the APK from [Releases](https://github.com/Princeyadav05/Grayout/releases/latest) and open it on your device.

You can verify the download:

```bash
shasum -a 256 grayout-v*.apk
```

Match the output against the `.sha256` file attached to the release. On Linux, `sha256sum` works the same way.

> **Play Protect note:** If you install via a browser download, Google Play Protect may block the installation because the app uses an accessibility service (for per-app exclusions). This is a known limitation with sideloaded apps that use accessibility permissions. To get around it, use Option 1 or Option 2 instead. Both bypass Play Protect entirely.

## Setup

After installing, grant the secure settings permission via ADB:

1. Connect your device via USB with ADB debugging enabled
2. Run:
   ```bash
   adb shell pm grant com.princeyadav.grayout android.permission.WRITE_SECURE_SETTINGS
   ```
3. Open the app

This is a one-time step. The permission persists across reboots and app updates.

## Tech

- Kotlin
- Jetpack Compose
- Material 3
- Single-activity MVVM architecture
- Foreground Service

## Building from source

```bash
./gradlew assembleDebug   # debug build for local dev
```

For the release build process, see [RELEASING.md](RELEASING.md).

## License

MIT
