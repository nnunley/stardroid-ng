package com.stardroid.awakening.vulkan

import android.view.Surface

/**
 * Manages Vulkan rendering lifecycle through JNI calls to native code.
 *
 * This class serves as the Kotlin-side bridge to native Vulkan rendering.
 * The actual Vulkan initialization and rendering happens in C++ code.
 */
class VulkanRenderer {
    private var nativeContext: Long = 0

    /**
     * Initialize Vulkan with the given Android Surface.
     * Must be called from a thread that will be used for rendering.
     *
     * @param surface The Android Surface to render to
     * @return true if initialization succeeded, false otherwise
     */
    fun init(surface: Surface): Boolean {
        if (nativeContext != 0L) {
            destroy()
        }
        nativeContext = nativeInit(surface)
        return nativeContext != 0L
    }

    /**
     * Render a frame with the given rotation angle.
     *
     * @param angle Rotation angle in degrees
     */
    fun render(angle: Float) {
        if (nativeContext != 0L) {
            nativeRender(nativeContext, angle)
        }
    }

    /**
     * Handle surface resize.
     *
     * @param width New surface width
     * @param height New surface height
     */
    fun resize(width: Int, height: Int) {
        if (nativeContext != 0L) {
            nativeResize(nativeContext, width, height)
        }
    }

    /**
     * Clean up Vulkan resources.
     * Safe to call multiple times.
     */
    fun destroy() {
        if (nativeContext != 0L) {
            nativeDestroy(nativeContext)
            nativeContext = 0
        }
    }

    /**
     * Check if the renderer is initialized.
     */
    val isInitialized: Boolean
        get() = nativeContext != 0L

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

        /**
         * Check if the native library is available.
         */
        fun isLibraryLoaded(): Boolean = libraryLoaded

        /**
         * Get the error message if library loading failed.
         */
        fun getLoadError(): String? = loadError
    }
}
