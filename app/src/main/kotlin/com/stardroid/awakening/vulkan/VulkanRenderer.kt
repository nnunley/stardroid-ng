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
