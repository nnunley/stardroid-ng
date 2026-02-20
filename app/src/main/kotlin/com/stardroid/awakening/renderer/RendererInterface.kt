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
