# Phase 2: Dynamic Vertex Buffers Design

**Date:** 2026-02-03
**Status:** Ready for implementation

## Overview

Replace the hardcoded triangle demo with a dynamic rendering system that supports:
- Multiple draw calls per frame
- Per-frame view/projection matrices (for AR integration)
- Per-draw-call model transforms
- Dynamic vertex data from Kotlin

## Architecture

### Data Flow Per Frame

```
beginFrame()
  → Reset vertex buffer offset
  → Acquire swapchain image
  → Begin command buffer
  → Begin render pass
  → Bind pipeline + uniform buffer (view/projection)

draw(batch)  [called 0-N times]
  → Copy vertices to buffer at current offset
  → Push model matrix
  → Record vkCmdDraw

endFrame()
  → End render pass
  → Submit + present
```

### Vertex Format

7 floats per vertex (28 bytes):
- Position: x, y, z (3 floats)
- Color: r, g, b, a (4 floats)

This matches `Primitive.kt` spec and supports 3D star positions.

### Matrix Handling

| Matrix | Storage | Update Frequency | Size |
|--------|---------|------------------|------|
| View | Uniform buffer | Per frame | 64 bytes |
| Projection | Uniform buffer | Per frame | 64 bytes |
| Model | Push constant | Per draw call | 64 bytes |

Separating view/projection enables AR integration where ARCore provides camera matrices.

### Buffer Strategy

**Uniform Buffer:**
- 128 bytes (view + projection)
- Persistently mapped
- Updated via `setViewMatrix()` / `setProjectionMatrix()`

**Dynamic Vertex Buffer:**
- Initial size: 64KB (~2300 vertices)
- Grows if needed, never shrinks
- Persistently mapped
- Offset reset to 0 each frame

## Native C++ Changes

### VulkanContext Additions

```cpp
struct VulkanContext {
    // ... existing fields ...

    // Uniform buffer for view/projection
    VkBuffer uniformBuffer;
    VkDeviceMemory uniformBufferMemory;
    void* uniformBufferMapped;

    // Dynamic vertex buffer
    VkBuffer dynamicVertexBuffer;
    VkDeviceMemory dynamicVertexBufferMemory;
    void* dynamicVertexBufferMapped;
    size_t dynamicVertexBufferSize;
    size_t dynamicVertexBufferOffset;

    // Descriptor set for uniform buffer
    VkDescriptorPool descriptorPool;
    VkDescriptorSetLayout descriptorSetLayout;
    VkDescriptorSet descriptorSet;

    // Cached matrices
    float viewMatrix[16];
    float projectionMatrix[16];
};
```

### New JNI Entry Points

```cpp
nativeBeginFrame(context) → bool
nativeEndFrame(context)
nativeDraw(context, primitiveType, vertices, vertexCount, transform)
nativeSetViewMatrix(context, matrix)
nativeSetProjectionMatrix(context, matrix)
```

### Shader Changes

**Vertex shader:**
```glsl
#version 450

layout(location = 0) in vec3 inPosition;  // Changed from vec2
layout(location = 1) in vec4 inColor;     // Changed from vec3

layout(set = 0, binding = 0) uniform Matrices {
    mat4 view;
    mat4 projection;
} ubo;

layout(push_constant) uniform PushConstants {
    mat4 model;
} pc;

layout(location = 0) out vec4 fragColor;

void main() {
    gl_Position = ubo.projection * ubo.view * pc.model * vec4(inPosition, 1.0);
    fragColor = inColor;
}
```

**Fragment shader:**
```glsl
#version 450

layout(location = 0) in vec4 fragColor;
layout(location = 0) out vec4 outColor;

void main() {
    outColor = fragColor;
}
```

## Kotlin API

### VulkanRenderer Implementation

```kotlin
class VulkanRenderer : RendererInterface {
    private var nativeContext: Long = 0
    private var _frameNumber: Long = 0
    private var inFrame: Boolean = false

    private var viewMatrix: FloatArray = Matrix.identity()
    private var projectionMatrix: FloatArray = Matrix.identity()
    private var matricesDirty: Boolean = true

    override fun beginFrame(): Boolean {
        if (nativeContext == 0L) return false

        if (matricesDirty) {
            nativeSetViewMatrix(nativeContext, viewMatrix)
            nativeSetProjectionMatrix(nativeContext, projectionMatrix)
            matricesDirty = false
        }

        inFrame = nativeBeginFrame(nativeContext)
        return inFrame
    }

    override fun draw(batch: DrawBatch) {
        if (!inFrame) return
        val transform = batch.transform ?: Matrix.identity()
        nativeDraw(
            nativeContext,
            batch.type.ordinal,
            batch.vertices,
            batch.vertexCount,
            transform
        )
    }

    override fun endFrame() {
        if (!inFrame) return
        nativeEndFrame(nativeContext)
        inFrame = false
        _frameNumber++
    }

    override fun setViewMatrix(matrix: FloatArray) {
        viewMatrix = matrix.copyOf()
        matricesDirty = true
    }

    override fun setProjectionMatrix(matrix: FloatArray) {
        projectionMatrix = matrix.copyOf()
        matricesDirty = true
    }
}
```

## Demo

Two triangles demonstrating multiple draw calls:

```kotlin
if (renderer.beginFrame()) {
    // Triangle 1: rotating
    renderer.draw(DrawBatch(
        type = PrimitiveType.TRIANGLES,
        vertices = floatArrayOf(
            0.0f, -0.5f, 0f,  1f, 0f, 0f, 1f,
           -0.5f,  0.5f, 0f,  0f, 1f, 0f, 1f,
            0.5f,  0.5f, 0f,  0f, 0f, 1f, 1f,
        ),
        vertexCount = 3,
        transform = Matrix.rotateZ(angle)
    ))

    // Triangle 2: static, offset
    renderer.draw(DrawBatch(
        type = PrimitiveType.TRIANGLES,
        vertices = floatArrayOf(
            0.3f, -0.3f, 0f,  1f, 1f, 0f, 1f,
            0.1f,  0.1f, 0f,  0f, 1f, 1f, 1f,
            0.5f,  0.1f, 0f,  1f, 0f, 1f, 1f,
        ),
        vertexCount = 3,
        transform = Matrix.identity()
    ))

    renderer.endFrame()
}
```

## Implementation Checklist

### 1. Update shaders
- [ ] Modify `triangle.vert` for 7-float vertex (xyz + rgba)
- [ ] Add uniform buffer binding for view/projection
- [ ] Keep push constant for model matrix
- [ ] Rebuild SPIR-V

### 2. Native infrastructure
- [ ] Add uniform buffer creation/cleanup
- [ ] Add descriptor pool, layout, and set
- [ ] Add dynamic vertex buffer (64KB, persistently mapped)
- [ ] Update pipeline vertex input for 7 floats

### 3. Native JNI entry points
- [ ] Implement `nativeBeginFrame()`
- [ ] Implement `nativeEndFrame()`
- [ ] Implement `nativeDraw()`
- [ ] Implement `nativeSetViewMatrix()` / `nativeSetProjectionMatrix()`

### 4. Kotlin changes
- [ ] Update `VulkanRenderer` with new JNI declarations
- [ ] Implement `beginFrame/endFrame/draw`
- [ ] Implement `setViewMatrix/setProjectionMatrix`

### 5. Demo update
- [ ] Update `VulkanSurfaceView` to use new API
- [ ] Render two triangles with different transforms
- [ ] Remove legacy `render(angle)` usage

### 6. Verification
- [ ] Build succeeds
- [ ] Demo shows two triangles
- [ ] No validation errors
- [ ] 60 FPS maintained

## Future Considerations

- **AR Integration:** View/projection matrices will come from ARCore
- **FlatBuffers:** Star data can potentially be passed directly to GPU (zero-copy)
- **Multiple primitive types:** Points for stars, lines for constellations
- **Buffer optimization:** Double-buffering if performance requires it
