# Grayout [WIP]

A personal Android app that enforces grayscale mode to reduce phone addiction.

## What It Does

- Toggle grayscale on/off with a single tap
- Schedule automatic grayscale enforcement (specific days, time windows)
- Runs as a foreground service using `WRITE_SECURE_SETTINGS` to control display settings

## Requirements

- Android 8.0+ (API 26)
- One-time ADB setup for secure settings permission

## Setup

1. Install the app on your device
2. Connect via USB with ADB debugging enabled
3. Run the permission grant command:
   ```bash
   adb shell pm grant com.princeyadav.grayout android.permission.WRITE_SECURE_SETTINGS
   ```
4. Open the app

## Tech

- Kotlin
- Jetpack Compose
- Material 3
- Single-activity MVVM architecture
- Foreground Service + WorkManager

## Building

```bash
./gradlew assembleDebug
```

## License

MIT
