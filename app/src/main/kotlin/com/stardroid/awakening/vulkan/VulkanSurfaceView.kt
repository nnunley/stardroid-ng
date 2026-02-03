package com.stardroid.awakening.vulkan

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.min

/**
 * Android Surface view that hosts Vulkan rendering.
 *
 * Manages the Vulkan surface lifecycle:
 * - Creates renderer when surface is ready
 * - Starts/stops render loop thread
 * - Handles surface resizing
 * - Cleans up on destruction
 */
class VulkanSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private val renderer = VulkanRenderer()
    private var renderThread: Thread? = null
    private var rendering = false
    private val renderLock = Any()

    init {
        holder.addCallback(this)
        // Set to opaque to avoid blending with background
        holder.setFormat(android.graphics.PixelFormat.OPAQUE)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // Initialize Vulkan with the native surface
        val success = renderer.init(holder.surface)
        if (success) {
            startRenderLoop()
        } else {
            android.util.Log.e(TAG, "Failed to initialize Vulkan renderer")
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Notify renderer of surface size change
        if (width > 0 && height > 0) {
            renderer.resize(width, height)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Stop rendering and clean up
        stopRenderLoop()
        renderer.destroy()
    }

    fun onResume() {
        // Can be called from Activity.onResume() if needed for additional setup
        synchronized(renderLock) {
            rendering = true
        }
        // The render thread should already be running after surfaceCreated
        // This is mainly for restarting if paused mid-rendering
    }

    fun onPause() {
        // Can be called from Activity.onPause() if needed for cleanup
        synchronized(renderLock) {
            rendering = false
        }
    }

    private fun startRenderLoop() {
        synchronized(renderLock) {
            if (rendering) {
                return // Already running
            }
            rendering = true
        }

        renderThread = Thread {
            try {
                var angle = 0f
                val fps = 60
                val frameTimeMs = 1000L / fps
                var lastFrameTime = System.currentTimeMillis()

                while (rendering) {
                    val frameStart = System.currentTimeMillis()

                    // Render frame with current angle
                    renderer.render(angle)

                    // Increment angle for animation
                    angle += 1f
                    if (angle >= 360f) {
                        angle = 0f
                    }

                    // Frame rate limiting - aim for 60 FPS
                    val frameEnd = System.currentTimeMillis()
                    val frameTime = frameEnd - frameStart
                    if (frameTime < frameTimeMs) {
                        val sleepTime = frameTimeMs - frameTime
                        Thread.sleep(sleepTime)
                    }

                    lastFrameTime = frameEnd
                }
            } catch (e: InterruptedException) {
                // Thread was interrupted, cleanup
                android.util.Log.d(TAG, "Render thread interrupted")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error in render loop", e)
            }
        }.apply {
            name = "VulkanRenderThread"
            start()
        }
    }

    private fun stopRenderLoop() {
        synchronized(renderLock) {
            rendering = false
        }

        renderThread?.let {
            // Give render thread time to finish current frame
            it.join(1000)
            if (it.isAlive) {
                android.util.Log.w(TAG, "Render thread did not stop within timeout")
            }
        }
        renderThread = null
    }

    companion object {
        private const val TAG = "VulkanSurfaceView"
    }
}
