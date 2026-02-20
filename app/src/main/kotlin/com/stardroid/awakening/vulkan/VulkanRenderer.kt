package com.stardroid.awakening.vulkan

import android.view.Surface
import com.stardroid.awakening.renderer.DrawBatch
import com.stardroid.awakening.renderer.Matrix
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
    private var inFrame: Boolean = false

    // Cached matrices
    private var viewMatrix: FloatArray = Matrix.identity()
    private var projectionMatrix: FloatArray = Matrix.identity()
    private var matricesDirty: Boolean = true

    // RendererInterface implementation

    override fun initialize(surface: Surface, width: Int, height: Int): Boolean {
        if (nativeContext != 0L) {
            release()
        }
        nativeContext = nativeInit(surface)
        if (nativeContext != 0L) {
            nativeResize(nativeContext, width, height)
            // Initialize with identity matrices
            matricesDirty = true
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

        // Upload matrices if changed (before beginFrame to ensure they're ready)
        if (matricesDirty) {
            nativeSetViewMatrix(nativeContext, viewMatrix)
            nativeSetProjectionMatrix(nativeContext, projectionMatrix)
            matricesDirty = false
        }

        inFrame = nativeBeginFrame(nativeContext)
        return inFrame
    }

    override fun endFrame() {
        if (!inFrame) return
        nativeEndFrame(nativeContext)
        inFrame = false
        _frameNumber++
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

    override fun setViewMatrix(matrix: FloatArray) {
        viewMatrix = matrix.copyOf()
        matricesDirty = true
    }

    override fun setProjectionMatrix(matrix: FloatArray) {
        projectionMatrix = matrix.copyOf()
        matricesDirty = true
    }

    /**
     * Set background opacity (0.0 = fully transparent, 1.0 = fully opaque dark).
     */
    fun setBackgroundOpacity(opacity: Float) {
        if (nativeContext != 0L) {
            nativeSetBackgroundOpacity(nativeContext, opacity.coerceIn(0f, 1f))
        }
    }

    /**
     * Get current swapchain dimensions (width, height).
     * Returns [0, 0] if not initialized.
     */
    fun getSwapchainDimensions(): IntArray {
        return if (nativeContext != 0L) {
            nativeGetSwapchainDimensions(nativeContext)
        } else {
            intArrayOf(0, 0)
        }
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

    // New Phase 2 JNI methods
    private external fun nativeBeginFrame(context: Long): Boolean
    private external fun nativeEndFrame(context: Long)
    private external fun nativeDraw(
        context: Long,
        primitiveType: Int,
        vertices: FloatArray,
        vertexCount: Int,
        transform: FloatArray
    )
    private external fun nativeSetViewMatrix(context: Long, matrix: FloatArray)
    private external fun nativeSetProjectionMatrix(context: Long, matrix: FloatArray)
    private external fun nativeSetBackgroundOpacity(context: Long, opacity: Float)
    private external fun nativeGetSwapchainDimensions(context: Long): IntArray

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
