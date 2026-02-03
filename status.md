# Project Status

## Current State

**Phase:** Bootstrap Phase 7 - Vertex Buffer and Triangle Rendering Complete

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
- [x] Bootstrap Phase 3 & 4: Native Vulkan initialization (C++/CMake)
  - Create CMakeLists.txt with Vulkan library support
  - Implement vulkan_wrapper.cpp with JNI interface
  - Implement VkInstance creation with validation layers (debug builds)
  - Implement VkSurfaceKHR creation from Android Surface
  - Implement physical device selection with graphics + present queue support
  - Implement logical device creation with VK_KHR_swapchain extension
  - Add vulkan_wrapper.h with API declarations
  - Add VkResult to string helper for better error logging
  - Verified: `./gradlew assembleDebug` produces 7.9MB APK with native libraries
  - Code review completed and issues addressed
- [x] Bootstrap Phase 5: Swapchain, render pass, and basic render loop
  - Implement swapchain creation with format/present mode selection
  - Create image views for all swapchain images
  - Create render pass with single color attachment
  - Create framebuffers for each swapchain image
  - Add command pool and command buffers
  - Implement synchronization objects (semaphores, fences)
  - Implement basic render loop with clear color (dark blue)
  - Handle swapchain recreation on resize
  - Add composite alpha mode negotiation for device compatibility
  - Add zero-extent resize protection (minimized windows)
  - Code review completed - all critical/important issues fixed
  - Verified: `./gradlew assembleDebug` produces 9.1MB APK with all Phase 5 features
- [x] Bootstrap Phase 6: Graphics pipeline and shaders
  - Create triangle.vert shader (position vec2, color vec3, push constant mat4)
  - Create triangle.frag shader (pass-through color)
  - Add CMake shader compilation (glslc GLSL → SPIR-V)
  - Add Python script to generate embedded SPIR-V C header
  - Create graphics pipeline with:
    - Shader modules from embedded SPIR-V (aligned for uint32_t)
    - Vertex input binding (position + color)
    - Dynamic viewport/scissor state
    - Push constant range for transform matrix
    - Pipeline layout and graphics pipeline
  - Code review completed - alignment issue fixed
  - Verified: `./gradlew assembleDebug` builds successfully

- [x] Bootstrap Phase 7: Vertex buffer and triangle rendering
  - Create vertex buffer with triangle data (3 vertices)
  - Implement memory type finder for host-visible memory
  - Update recordCommandBuffer to bind pipeline and draw
  - Fix vertex winding (counter-clockwise)
  - Code verified: `./gradlew assembleDebug` builds successfully
  - Verified: APK builds to 8.3MB with all Phase 7 features

## Next Tasks

1. **Bootstrap Phase 8:** Animation (push constants, rotation matrix)

## Decisions Made

- Using C++ for Vulkan (matches spec, better perf, more examples)
- NDK is configured and used for CMake builds
- Java 23 locked in gradle.properties (Java 25 has AGP compatibility issues)
- Shaders embedded as C arrays (no runtime file I/O)

## Build Verification

```bash
./gradlew assembleDebug  # SUCCESS
# Phase 6 includes: shaders, graphics pipeline with vertex input
# Pipeline uses dynamic viewport/scissor state for resize handling
```

## What's Working Now

- Vulkan instance, surface, physical device, logical device created ✓
- Swapchain with proper format selection ✓
- Render pass and framebuffers ✓
- Command buffers and synchronization ✓
- Basic render loop that clears to dark blue ✓
- Swapchain recreation on resize ✓
- Validation layer support (debug builds) ✓
- **Graphics pipeline created ✓** (not yet bound - Phase 7)
- **SPIR-V shaders compiled and embedded ✓**
