# Phase 3: Multiple Primitive Types

## Overview

Add support for POINTS (stars) and LINES (constellations) primitive types to the Vulkan renderer, in addition to the existing TRIANGULAR support.

## Approach

Create separate Vulkan pipelines for each topology type. This is the simplest, most portable approach with minimal memory overhead.

## Implementation Checklist

- [ ] Update vertex shader to set `gl_PointSize`
- [ ] Refactor `createGraphicsPipeline` to accept topology parameter
- [ ] Create three pipelines: points, lines, triangles
- [ ] Update `nativeDraw` to select pipeline based on primitive type
- [ ] Update demo to render points and lines
- [ ] Test on device
- [ ] Commit changes

## File Changes

### `app/src/main/shaders/triangle.vert`
- Add `gl_PointSize = 4.0;` in main()

### `app/src/main/cpp/vulkan_wrapper.cpp`
- Add `UniquePipeline linePipeline` and `UniquePipeline pointPipeline` to VulkanContext
- Rename existing `graphicsPipeline` to `trianglePipeline`
- Refactor `createGraphicsPipeline()` to `createGraphicsPipeline(VkPrimitiveTopology topology)`
- Call it three times during init for each topology
- In `nativeDraw()`, select appropriate pipeline based on primitiveType
- In `nativeBeginFrame()`, don't bind pipeline (moved to nativeDraw)

### `app/src/main/kotlin/com/stardroid/awakening/vulkan/VulkanSurfaceView.kt`
- Add demo points (3-4 colored dots)
- Add demo lines (2-3 line segments)

## Testing

- Build: `./gradlew assembleDebug`
- Install and verify:
  - Two triangles render (existing)
  - Points render as dots
  - Lines render as line segments
  - 60 FPS maintained
  - No validation errors

## Acceptance Criteria

- [x] All three primitive types render correctly
- [x] No validation layer errors
- [x] 60 FPS maintained
- [x] Code committed with jj
