# Agent Instructions

## Repository

This is a **jj repository** (Jujutsu). Use jj commands, not git.

```bash
# Status
jj status
jj log --limit 5

# Commit (jj auto-tracks changes, no staging)
jj describe -m "commit message"
jj new -m "next commit message"

# Undo
jj undo
```

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

### Build Troubleshooting

If build fails with cryptic version number error (e.g., "25.0.2"):
- Check Java version: `java -version`
- Java 25 has AGP compatibility issues - use Java 17 or 23
- gradle.properties should have: `org.gradle.java.home=<path-to-java-23>`

If SDK not found:
- Ensure `local.properties` exists with: `sdk.dir=/Users/ndn/Library/Android/sdk`

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
