# Project Status

## Current State

**Phase:** Star Data Loading - Complete and Verified

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

- [x] Bootstrap Phase 8: Animation (push constants, rotation matrix)
  - Implement `buildRotationMatrixZ()` in C++ (column-major for GLSL)
  - Pass rotation angle from Kotlin render loop to native renderer
  - Push transform matrix as push constant in command buffer
  - Animate triangle rotation at ~60 FPS using frame time limiting
  - Verified: `./gradlew assembleDebug` builds successfully

- [x] Bootstrap Phase 9: Polish
  - [x] Test on real Android device (Mali-G715 GPU)
  - [x] Verify 60 FPS performance (~61 FPS achieved)
  - [x] Verify no validation layer errors in logcat
  - [x] Add FPS overlay (toggleable via tap)
  - [ ] Test on additional Android devices

- [x] Rendering Abstraction Phase 1: Design & Implementation
  - [x] Design rendering abstraction layer
  - [x] Create spec: `specs/rendering/abstraction.md`
  - [x] Create `RendererInterface.kt`
  - [x] Create `Primitive.kt` (PrimitiveType, Vertex, DrawBatch)
  - [x] Create `Matrix.kt` (identity, perspective, rotateZ, multiply)
  - [x] Refactor `VulkanRenderer.kt` to implement `RendererInterface`
  - [x] Keep legacy render(angle) for demo backwards compatibility
  - [x] Demo: Triangle still renders at 60 FPS through abstraction layer

- [x] Rendering Abstraction Phase 2: Dynamic Vertex Buffers
  - [x] Set up gtest for C++ unit tests
  - [x] Extract math utilities to testable module (`math_utils.h`)
  - [x] Write gtest tests for matrix operations (8 tests, all pass)
  - [x] Update shaders: vec3 position + vec4 color (7 floats)
  - [x] Add uniform buffer for view/projection matrices (128 bytes)
  - [x] Add descriptor set layout, pool, and set for UBO
  - [x] Add 64KB dynamic vertex buffer (persistently mapped)
  - [x] Implement new JNI entry points:
    - nativeBeginFrame, nativeEndFrame
    - nativeDraw (with dynamic vertices and model transform)
    - nativeSetViewMatrix, nativeSetProjectionMatrix
  - [x] Update VulkanRenderer with full RendererInterface implementation
  - [x] Update VulkanSurfaceView demo to render two triangles
  - [x] Demo: One rotating RGB triangle, one static YCM triangle
  - [x] **COMPLETED**: Device verified (two triangles render, 60 FPS, no validation errors)

- [x] **Rendering Abstraction Phase 3: Multiple Primitive Types**
  - [x] Update vertex shader: Add `gl_PointSize = 4.0` for point rendering
  - [x] Refactor C++ to support multiple pipelines (POINTS, LINES, TRIANGLES)
  - [x] Create separate graphics pipelines for each topology
  - [x] Update `nativeDraw` to select appropriate pipeline per primitive type
  - [x] Disable face culling for points and lines (no front/back)
  - [x] Update demo to render points (stars) and lines (constellation lines)
  - [x] Verified: `./gradlew assembleDebug` builds successfully (8.3MB APK)
  - [x] Ready for device testing

- [x] **Star Data Loading**
  - [x] Copy star data from stardroid (Protocol Buffers binary format)
  - [x] Add datamodel module with protobuf schema (source.proto)
  - [x] Create StarCatalog class to load stars.binary
  - [x] Convert RA/Dec coordinates to xyz unit vectors on celestial sphere
  - [x] Extract color from ARGB packed format
  - [x] Integrate star rendering into VulkanSurfaceView
  - [x] Verified: 9.0MB APK includes stars.binary (117KB), constellations.binary (18KB), messier.binary (8KB)
  - [x] **VERIFIED**: Stars render as points on device
  - [x] Fixed dynamic vertex buffer overflow (64KB → 512KB)
  - [x] ~3200 stars render at 60+ FPS

## Next Tasks

2. **Coordinate System and Transforms**
   - [ ] Implement celestial coordinate system (RA/Dec to screen)
   - [ ] Device orientation sensor integration
   - [ ] Real-time sky mapping

3. **Constellation Lines**
   - [ ] Load constellations.binary
   - [ ] Render constellation lines

## Decisions Made

- Using C++ for Vulkan (matches spec, better perf, more examples)
- NDK is configured and used for CMake builds
- Java 23 locked in gradle.properties (Java 25 has AGP compatibility issues)
- Shaders embedded as C arrays (no runtime file I/O)
- Renderer abstraction layer enables future backend swapping (OpenGL, AR)
- Kept legacy render(angle) API for backwards compatibility during transition
- All matrices use column-major format (Vulkan/GLSL standard)
- C++ math utilities extracted to `math_utils.h` for testability
- Using gtest (Google Test) for C++ unit testing
- Using Protocol Buffers (from stardroid) instead of FlatBuffers for star data (existing pipeline)
- Star coordinates stored as RA/Dec, converted to xyz unit vectors at load time
- Uniform buffer for view/projection (AR integration ready)
- Push constants for model matrix (per-draw-call transforms)
- 7-float vertex format: xyz position + rgba color (3D star positions)
- Multiple pipelines approach: separate pipeline per topology type (simpler, more portable)
- Face culling disabled for POINTS and LINES (only triangles use back-face culling)
- Dynamic pipeline selection in nativeDraw based on PrimitiveType enum

## Build Verification

```bash
./gradlew assembleDebug  # SUCCESS
# Vulkan demo with:
#   - Two rotating/static triangles (RGB and YCM)
#   - 4 rendered points (white stars)
#   - 2 rendered line segments (constellation lines)
#   - APK size: 8.3MB
# Ready for device testing
```

## What's Working Now

- Vulkan instance, surface, physical device, logical device created ✓
- Swapchain with proper format selection ✓
- Render pass and framebuffers ✓
- Command buffers and synchronization ✓
- Basic render loop with dark blue background ✓
- Swapchain recreation on resize ✓
- Validation layer support (debug builds) ✓
- Graphics pipeline created and bound ✓
- SPIR-V shaders compiled and embedded ✓
- Vertex buffer with triangle data ✓
- Rotating colored triangle animation ✓
- Renderer abstraction layer (RendererInterface) ✓
- Primitive types (PrimitiveType, Vertex, DrawBatch) ✓
- Matrix utilities (identity, perspective, rotateZ, multiply) ✓
- **C++ unit tests with gtest (8 tests passing) ✓**
- **Dynamic vertex buffer (64KB, persistently mapped) ✓**
- **Uniform buffer for view/projection matrices ✓**
- **New API: beginFrame/draw/endFrame pattern ✓**
- **Multiple draw calls per frame ✓**
- **Per-draw-call model transforms ✓**
- **Multiple primitive types (POINTS, LINES, TRIANGLES) ✓**
- **Separate pipelines per topology (points, lines, triangles) ✓**
- **Dynamic pipeline selection in draw calls ✓**
- **Points render as dots (4.0 pixel size) ✓**
- **Lines render with 1.0 line width ✓**
- **Demo shows all three primitive types ✓**

## Phase 3 API Summary

Same as Phase 2, now supporting:
- `PrimitiveType.POINTS` - renders as dot sprites
- `PrimitiveType.LINES` - renders as line segments
- `PrimitiveType.TRIANGLES` - renders as filled triangles

## Phase 2 API Summary

```kotlin
// Kotlin usage
renderer.setProjectionMatrix(Matrix.perspective(...))
renderer.setViewMatrix(Matrix.identity())

if (renderer.beginFrame()) {
    renderer.draw(DrawBatch(
        type = PrimitiveType.TRIANGLES,
        vertices = floatArrayOf(x, y, z, r, g, b, a, ...),
        vertexCount = 3,
        transform = Matrix.rotateZ(angle)
    ))
    renderer.endFrame()
}
```

## Vulkan Demo Acceptance Criteria

- [x] Project builds successfully with `./gradlew assembleDebug`
- [x] APK installs on Android 8.0+ device with Vulkan 1.1 support
- [x] App displays a rotating colored triangle on screen
- [x] No crashes or validation errors in logcat
- [x] Frame rate stable at 60 FPS (verified: ~61 FPS on Mali-G715)
- [ ] **Phase 2:** Two triangles render via dynamic API
