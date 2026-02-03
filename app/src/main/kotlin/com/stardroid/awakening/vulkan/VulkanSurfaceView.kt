package com.stardroid.awakening.vulkan

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.stardroid.awakening.renderer.DrawBatch
import com.stardroid.awakening.renderer.Matrix
import com.stardroid.awakening.renderer.PrimitiveType

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
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    /** Callback for FPS updates. Called on render thread, use post() to update UI. */
    var onFpsUpdate: ((fps: Double) -> Unit)? = null

    init {
        holder.addCallback(this)
        // Set to opaque to avoid blending with background
        holder.setFormat(android.graphics.PixelFormat.OPAQUE)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // Initialize Vulkan with the native surface and initial dimensions
        val rect = holder.surfaceFrame
        surfaceWidth = rect.width()
        surfaceHeight = rect.height()
        val success = renderer.initialize(holder.surface, surfaceWidth, surfaceHeight)
        if (success) {
            startRenderLoop()
        } else {
            android.util.Log.e(TAG, "Failed to initialize Vulkan renderer")
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Notify renderer of surface size change
        if (width > 0 && height > 0) {
            surfaceWidth = width
            surfaceHeight = height
            renderer.resize(width, height)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Stop rendering and clean up
        stopRenderLoop()
        renderer.release()
    }

    fun onResume() {
        // Can be called from Activity.onResume() if needed for additional setup
        // Note: Don't set rendering=true here - surfaceCreated handles starting the loop
        // This is called before surfaceCreated in the lifecycle
    }

    fun onPause() {
        // Can be called from Activity.onPause() if needed for cleanup
        // Note: surfaceDestroyed will handle stopping the render loop
        // Only stop here if we want to pause rendering while surface is still valid
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
                val targetFps = 60
                val frameTimeMs = 1000L / targetFps

                // FPS tracking
                var frameCount = 0
                var fpsStartTime = System.nanoTime()
                val fpsUpdateIntervalNs = 500_000_000L // Update FPS every 0.5 seconds
                var currentFps = 0.0

                // Triangle 1: RGB triangle (rotating)
                val triangle1Vertices = floatArrayOf(
                    // x, y, z, r, g, b, a
                     0.0f, -0.5f, 0f,  1f, 0f, 0f, 1f,  // red
                    -0.5f,  0.5f, 0f,  0f, 1f, 0f, 1f,  // green
                     0.5f,  0.5f, 0f,  0f, 0f, 1f, 1f,  // blue
                )

                // Triangle 2: Yellow-Cyan-Magenta (static, offset)
                val triangle2Vertices = floatArrayOf(
                    // x, y, z, r, g, b, a
                    0.3f, -0.3f, 0f,  1f, 1f, 0f, 1f,  // yellow
                    0.1f,  0.1f, 0f,  0f, 1f, 1f, 1f,  // cyan
                    0.5f,  0.1f, 0f,  1f, 0f, 1f, 1f,  // magenta
                )

                // Demo points (stars)
                val pointVertices = floatArrayOf(
                    // x, y, z, r, g, b, a
                    -0.7f, -0.7f, 0f,  1f, 1f, 1f, 1f,  // white
                    -0.6f, -0.5f, 0f,  1f, 1f, 0.8f, 1f,  // yellow-ish
                    -0.8f, -0.3f, 0f,  0.8f, 0.8f, 1f, 1f,  // blue-ish
                    -0.5f, -0.6f, 0f,  1f, 0.9f, 0.9f, 1f,  // white-ish
                )

                // Demo lines (constellation-like)
                val lineVertices = floatArrayOf(
                    // x, y, z, r, g, b, a
                    // Line 1: connects two stars
                    -0.7f, -0.7f, 0f,  0.5f, 0.5f, 1f, 1f,  // start
                    -0.6f, -0.5f, 0f,  0.5f, 0.5f, 1f, 1f,  // end
                    // Line 2: another constellation line
                    -0.6f, -0.5f, 0f,  0.5f, 0.5f, 1f, 1f,  // start
                    -0.8f, -0.3f, 0f,  0.5f, 0.5f, 1f, 1f,  // end
                )

                while (rendering) {
                    val frameStart = System.currentTimeMillis()

                    // Set up projection matrix
                    val aspect = if (surfaceHeight > 0) {
                        surfaceWidth.toFloat() / surfaceHeight.toFloat()
                    } else {
                        1f
                    }
                    val projection = Matrix.perspective(
                        fovYDegrees = 60f,
                        aspect = aspect,
                        near = 0.1f,
                        far = 100f
                    )
                    renderer.setProjectionMatrix(projection)
                    // Move camera back (translate world by -2 in Z)
                    renderer.setViewMatrix(Matrix.translate(0f, 0f, -2f))

                    // Begin frame
                    if (renderer.beginFrame()) {
                        // Draw first triangle (rotating)
                        val batch1 = DrawBatch(
                            type = PrimitiveType.TRIANGLES,
                            vertices = triangle1Vertices,
                            vertexCount = 3,
                            transform = Matrix.rotateZ(angle)
                        )
                        renderer.draw(batch1)

                        // Draw second triangle (static)
                        val batch2 = DrawBatch(
                            type = PrimitiveType.TRIANGLES,
                            vertices = triangle2Vertices,
                            vertexCount = 3,
                            transform = Matrix.identity()
                        )
                        renderer.draw(batch2)

                        // Draw points (stars)
                        val pointBatch = DrawBatch(
                            type = PrimitiveType.POINTS,
                            vertices = pointVertices,
                            vertexCount = 4,
                            transform = Matrix.identity()
                        )
                        renderer.draw(pointBatch)

                        // Draw lines (constellation lines)
                        val lineBatch = DrawBatch(
                            type = PrimitiveType.LINES,
                            vertices = lineVertices,
                            vertexCount = 4,  // 2 lines = 4 vertices
                            transform = Matrix.identity()
                        )
                        renderer.draw(lineBatch)

                        // End frame
                        renderer.endFrame()
                    }

                    frameCount++

                    // Increment angle for animation
                    angle += 1f
                    if (angle >= 360f) {
                        angle = 0f
                    }

                    // Calculate FPS
                    val now = System.nanoTime()
                    val elapsed = now - fpsStartTime
                    if (elapsed >= fpsUpdateIntervalNs) {
                        currentFps = frameCount * 1_000_000_000.0 / elapsed
                        frameCount = 0
                        fpsStartTime = now

                        // Log FPS periodically
                        android.util.Log.i(TAG, "FPS: %.1f".format(currentFps))

                        // Notify callback
                        onFpsUpdate?.invoke(currentFps)
                    }

                    // Frame rate limiting - aim for 60 FPS
                    val frameEnd = System.currentTimeMillis()
                    val frameTime = frameEnd - frameStart
                    if (frameTime < frameTimeMs) {
                        val sleepTime = frameTimeMs - frameTime
                        Thread.sleep(sleepTime)
                    }
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
