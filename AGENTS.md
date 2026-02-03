# Agent Instructions

## Build

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Clean build
./gradlew clean assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Test

```bash
# Unit tests
./gradlew test

# Instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

## Deploy

```bash
# Install debug APK to connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Build and install in one step
./gradlew installDebug

# View logs
adb logcat -s StardroidAwakening
```
