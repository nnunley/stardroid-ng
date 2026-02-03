# Project Status

## Current State

**Phase:** Bootstrap Phase 2 - Android Surface Setup Complete

**Last Updated:** 2026-02-03

## Completed Tasks

- [x] Create Vulkan demo bootstrap spec (`specs/bootstrap/vulkan-demo.md`)
- [x] Bootstrap Phase 1: Project structure and Gradle configuration
  - Gradle Kotlin DSL with version catalog
  - Modern Android config (compileSdk 35, minSdk 26, Kotlin 2.1.0)
  - Basic MainActivity with placeholder icons
  - CMakeLists.txt template for future native code
  - Successfully builds to `app-debug.apk`
- [x] Update specs: Replace Protocol Buffers with FlatBuffers
  - Renamed and rewrote `flatbuffers-schema.md`
  - Updated all related specs (catalogs, data-models, module-structure, build)
- [x] Add AGENTS.md with build/test/deploy commands
- [x] Bootstrap Phase 2: Basic Android app with VulkanSurfaceView
  - Implement VulkanRenderer.kt with JNI method declarations
  - Implement VulkanSurfaceView.kt with surface lifecycle management
  - Update MainActivity to use VulkanSurfaceView
  - Fix Java 25 compatibility issue (AGP 8.7.3 requires Java 23 or 17)
  - Fix gradle wrapper and build warnings
  - Verified: `./gradlew assembleDebug` produces 5.6MB APK

## Next Tasks

1. **Bootstrap Phase 3:** Native Vulkan initialization (C++/CMake)
2. **Bootstrap Phase 4:** Render colored triangle (SPIR-V shaders)

## Decisions Made

- Using C++ for Vulkan (matches spec, better perf, more examples)
- NDK is configured and used for CMake builds
- Java 23 locked in gradle.properties (Java 25 has AGP compatibility issues)

## Build Verification

```bash
./gradlew assembleDebug  # SUCCESS - produces 5.6MB APK
```
