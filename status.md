# Project Status

## Current State

**Phase:** Bootstrap - Project Setup Complete

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

## Next Tasks

1. **Bootstrap Phase 2:** Basic Android app with VulkanSurfaceView
2. **Bootstrap Phase 3:** Native Vulkan initialization (C++ or pure Kotlin TBD)
3. **Bootstrap Phase 4:** Render colored triangle

## Open Questions

- Use C++ for Vulkan or explore pure-Kotlin approach?
- NDK is configured but currently builds nothing - remove until needed?

## Build Verification

```bash
./gradlew assembleDebug  # SUCCESS - produces 5.6MB APK
```
