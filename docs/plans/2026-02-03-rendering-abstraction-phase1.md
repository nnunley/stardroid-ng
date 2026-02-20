# Rendering Abstraction Layer Phase 1 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Create the renderer abstraction layer that separates domain logic from Vulkan implementation, enabling future backend swapping.

**Architecture:** Define `RendererInterface` with lifecycle and drawing methods, create `Primitive.kt` with data classes for vertices and batches, create `Matrix.kt` for transform utilities. Refactor `VulkanRenderer` to implement the interface.

**Tech Stack:** Kotlin, Vulkan via JNI (existing), no new dependencies

---

## Task 1: Create RendererInterface.kt

**Files:**
- Create: `app/src/main/kotlin/com/stardroid/awakening/renderer/RendererInterface.kt`

**Step 1: Write the interface file**

```kotlin
package com.stardroid.awakening.renderer

import android.view.Surface

/**
 * Core interface for rendering backends.
 *
 * The domain layer speaks to this interface, not to specific
 * graphics APIs. Implementations handle Vulkan, OpenGL, or AR.
 */
interface RendererInterface {
    // Lifecycle
    fun initialize(surface: Surface, width: Int, height: Int): Boolean
    fun resize(width: Int, height: Int)
    fun release()

    // Frame rendering
    fun beginFrame(): Boolean  // Returns false if frame should be skipped
    fun endFrame()

    // Drawing
    fun draw(batch: DrawBatch)

    // View configuration
    fun setViewMatrix(matrix: FloatArray)       // 4x4 column-major
    fun setProjectionMatrix(matrix: FloatArray) // 4x4 column-major

    // State queries
    val isInitialized: Boolean
    val frameNumber: Long
}
```

**Step 2: Verify file compiles**

Run: `./gradlew compileDebugKotlin 2>&1 | head -30`
Expected: BUILD SUCCESSFUL (DrawBatch not found yet is OK - we create it next)

**Step 3: Commit**

```bash
jj new -m "feat(renderer): Add RendererInterface abstraction"
```

---

## Task 2: Create Primitive.kt

**Files:**
- Create: `app/src/main/kotlin/com/stardroid/awakening/renderer/Primitive.kt`

**Step 1: Write the primitive types file**

```kotlin
package com.stardroid.awakening.renderer

/** Types of graphical primitives */
enum class PrimitiveType {
    POINTS,      // Stars (variable size dots)
    LINES,       // Constellation lines, grids
    TRIANGLES,   // Filled shapes
    TEXT,        // Labels (future)
    IMAGE        // Textures (future)
}

/**
 * Vertex with position and color.
 *
 * Layout: x, y, z, r, g, b, a (7 floats per vertex)
 */
data class Vertex(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val r: Float = 1f,
    val g: Float = 1f,
    val b: Float = 1f,
    val a: Float = 1f
) {
    companion object {
        const val COMPONENTS = 7
        const val STRIDE_BYTES = COMPONENTS * 4
    }
}

/**
 * A batch of primitives to draw.
 *
 * Vertices are packed as [x,y,z,r,g,b,a, x,y,z,r,g,b,a, ...]
 */
data class DrawBatch(
    val type: PrimitiveType,
    val vertices: FloatArray,
    val vertexCount: Int,
    val transform: FloatArray? = null  // Optional 4x4 model matrix
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DrawBatch) return false
        return type == other.type &&
               vertices.contentEquals(other.vertices) &&
               vertexCount == other.vertexCount &&
               transform.contentEquals(other.transform)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + vertices.contentHashCode()
        result = 31 * result + vertexCount
        result = 31 * result + (transform?.contentHashCode() ?: 0)
        return result
    }
}
```

**Step 2: Verify file compiles**

Run: `./gradlew compileDebugKotlin 2>&1 | head -30`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
jj new -m "feat(renderer): Add Primitive types (PrimitiveType, Vertex, DrawBatch)"
```

---

## Task 3: Create Matrix.kt

**Files:**
- Create: `app/src/main/kotlin/com/stardroid/awakening/renderer/Matrix.kt`

**Step 1: Write the matrix utilities file**

```kotlin
package com.stardroid.awakening.renderer

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Matrix utilities for 4x4 transforms.
 * All matrices are column-major for Vulkan/GLSL compatibility.
 */
object Matrix {
    /** Create 4x4 identity matrix */
    fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )

    /** Create perspective projection matrix */
    fun perspective(
        fovYDegrees: Float,
        aspect: Float,
        near: Float,
        far: Float
    ): FloatArray {
        val fovYRadians = fovYDegrees * (Math.PI.toFloat() / 180f)
        val f = 1f / tan(fovYRadians / 2f)
        val nf = 1f / (near - far)

        return floatArrayOf(
            f / aspect, 0f, 0f, 0f,
            0f, f, 0f, 0f,
            0f, 0f, (far + near) * nf, -1f,
            0f, 0f, 2f * far * near * nf, 0f
        )
    }

    /** Create rotation matrix around Z axis */
    fun rotateZ(angleDegrees: Float): FloatArray {
        val angleRadians = angleDegrees * (Math.PI.toFloat() / 180f)
        val c = cos(angleRadians)
        val s = sin(angleRadians)

        return floatArrayOf(
            c, s, 0f, 0f,
            -s, c, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
    }

    /** Multiply two 4x4 matrices: result = a * b (column-major) */
    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val result = FloatArray(16)
        for (col in 0..3) {
            for (row in 0..3) {
                var sum = 0f
                for (k in 0..3) {
                    sum += a[row + k * 4] * b[k + col * 4]
                }
                result[row + col * 4] = sum
            }
        }
        return result
    }
}
```

**Step 2: Verify file compiles**

Run: `./gradlew compileDebugKotlin 2>&1 | head -30`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
jj new -m "feat(renderer): Add Matrix utilities (identity, perspective, rotateZ, multiply)"
```

---

## Task 4: Refactor VulkanRenderer to implement RendererInterface

**Files:**
- Modify: `app/src/main/kotlin/com/stardroid/awakening/vulkan/VulkanRenderer.kt`

**Step 1: Update VulkanRenderer to implement interface**

The refactored VulkanRenderer should:
1. Implement `RendererInterface`
2. Add `frameNumber` tracking
3. Add stub implementations for new methods (beginFrame, endFrame, draw, setViewMatrix, setProjectionMatrix)
4. Keep existing `render(angle)` method for backwards compatibility during transition

```kotlin
package com.stardroid.awakening.vulkan

import android.view.Surface
import com.stardroid.awakening.renderer.DrawBatch
import com.stardroid.awakening.renderer.RendererInterface

/**
 * Vulkan implementation of RendererInterface.
 *
 * This class serves as the Kotlin-side bridge to native Vulkan rendering.
 * The actual Vulkan initialization and rendering happens in C++ code.
 */
class VulkanRenderer : RendererInterface {
    private var nativeContext: Long = 0
    private var _frameNumber: Long = 0

    // RendererInterface implementation

    override fun initialize(surface: Surface, width: Int, height: Int): Boolean {
        if (nativeContext != 0L) {
            release()
        }
        nativeContext = nativeInit(surface)
        if (nativeContext != 0L) {
            nativeResize(nativeContext, width, height)
        }
        return nativeContext != 0L
    }

    override fun resize(width: Int, height: Int) {
        if (nativeContext != 0L) {
            nativeResize(nativeContext, width, height)
        }
    }

    override fun release() {
        if (nativeContext != 0L) {
            nativeDestroy(nativeContext)
            nativeContext = 0
        }
    }

    override fun beginFrame(): Boolean {
        if (nativeContext == 0L) return false
        // For now, always return true - native handles frame pacing
        return true
    }

    override fun endFrame() {
        if (nativeContext != 0L) {
            _frameNumber++
        }
    }

    override fun draw(batch: DrawBatch) {
        // TODO: Implement dynamic vertex buffer upload in Phase 2
        // For now, this is a no-op - we use render(angle) for the demo
    }

    override fun setViewMatrix(matrix: FloatArray) {
        // TODO: Implement in Phase 2 with uniform buffers
    }

    override fun setProjectionMatrix(matrix: FloatArray) {
        // TODO: Implement in Phase 2 with uniform buffers
    }

    override val isInitialized: Boolean
        get() = nativeContext != 0L

    override val frameNumber: Long
        get() = _frameNumber

    // Legacy API (kept for backwards compatibility during transition)

    /**
     * Initialize Vulkan with the given Android Surface.
     * @deprecated Use initialize(surface, width, height) instead
     */
    fun init(surface: Surface): Boolean {
        if (nativeContext != 0L) {
            release()
        }
        nativeContext = nativeInit(surface)
        return nativeContext != 0L
    }

    /**
     * Render a frame with the given rotation angle.
     * This is the legacy demo API - will be replaced by beginFrame/draw/endFrame.
     */
    fun render(angle: Float) {
        if (nativeContext != 0L) {
            nativeRender(nativeContext, angle)
            _frameNumber++
        }
    }

    /**
     * Clean up Vulkan resources.
     * @deprecated Use release() instead
     */
    fun destroy() = release()

    // JNI native method declarations
    private external fun nativeInit(surface: Surface): Long
    private external fun nativeRender(context: Long, angle: Float)
    private external fun nativeResize(context: Long, width: Int, height: Int)
    private external fun nativeDestroy(context: Long)

    companion object {
        private var libraryLoaded = false
        private var loadError: String? = null

        init {
            try {
                System.loadLibrary("vulkan_wrapper")
                libraryLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                loadError = e.message
                libraryLoaded = false
            }
        }

        fun isLibraryLoaded(): Boolean = libraryLoaded
        fun getLoadError(): String? = loadError
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | head -30`
Expected: BUILD SUCCESSFUL

**Step 3: Verify full build**

Run: `./gradlew assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
jj new -m "refactor(vulkan): Implement RendererInterface in VulkanRenderer"
```

---

## Task 5: Update VulkanSurfaceView to use new interface

**Files:**
- Modify: `app/src/main/kotlin/com/stardroid/awakening/vulkan/VulkanSurfaceView.kt`

**Step 1: Update to use RendererInterface type**

Change the renderer field type to `RendererInterface` (though still using VulkanRenderer concretely).
Use the new `initialize()` method signature in `surfaceCreated`.

```kotlin
// In surfaceCreated, change:
val success = renderer.init(holder.surface)

// To:
val rect = holder.surfaceFrame
val success = renderer.initialize(holder.surface, rect.width(), rect.height())
```

**Step 2: Verify full build and test**

Run: `./gradlew assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
jj new -m "refactor(vulkan): Update VulkanSurfaceView to use RendererInterface"
```

---

## Task 6: Verify Demo Still Works

**Step 1: Install and test on device**

Run: `./gradlew installDebug`
Expected: App installs and shows rotating triangle at 60 FPS

**Step 2: Check logcat for errors**

Run: `adb logcat -s VulkanWrapper:* VulkanSurfaceView:* | head -50`
Expected: No errors, normal initialization logs

**Step 3: Final commit with all changes squashed if needed**

If all tests pass, squash the commits into a single feature commit:
```bash
jj squash --from <first-commit> --into <last-commit> -m "feat(renderer): Add rendering abstraction layer (Phase 1)

- Add RendererInterface for backend independence
- Add Primitive types (PrimitiveType, Vertex, DrawBatch)
- Add Matrix utilities (identity, perspective, rotateZ, multiply)
- Refactor VulkanRenderer to implement interface
- Update VulkanSurfaceView to use new initialization API"
```

---

## Summary

After completing these tasks:
- `renderer/` package contains the abstraction layer
- `VulkanRenderer` implements `RendererInterface`
- Demo continues to work unchanged
- Foundation is ready for Phase 2 (dynamic vertex buffers)
