package com.stardroid.awakening.vulkan

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.stardroid.awakening.control.AstronomerModel
import com.stardroid.awakening.data.ConstellationCatalog
import com.stardroid.awakening.data.StarCatalog
import com.stardroid.awakening.layers.GridLayer
import com.stardroid.awakening.layers.HorizonLayer
import com.stardroid.awakening.layers.Layer
import com.stardroid.awakening.layers.LayerManager
import com.stardroid.awakening.renderer.Matrix

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

    /** Star catalog for rendering. Set before surface is created. */
    var starCatalog: StarCatalog? = null

    /** Constellation catalog for rendering. Set before surface is created. */
    var constellationCatalog: ConstellationCatalog? = null

    /** Astronomer model for sensor-based orientation. Set before surface is created. */
    var astronomerModel: AstronomerModel? = null

    /** Layer manager for visibility control. Set before surface is created. */
    var layerManager: LayerManager? = null

    /** Grid layer for RA/Dec coordinate lines. */
    private val gridLayer = GridLayer()

    /** Horizon layer for horizon line and cardinal markers. */
    private val horizonLayer = HorizonLayer()

    /** Callback for FPS updates. Called on render thread, use post() to update UI. */
    var onFpsUpdate: ((fps: Double) -> Unit)? = null

    /** Background opacity (0.0 = transparent for AR, 1.0 = opaque dark sky). */
    var backgroundOpacity: Float = 0.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            renderer.setBackgroundOpacity(field)
        }

    init {
        holder.addCallback(this)
        // Set to opaque to avoid blending with background
        holder.setFormat(android.graphics.PixelFormat.OPAQUE)
        // Ensure SurfaceView doesn't cover UI overlays
        setZOrderOnTop(false)
        setZOrderMediaOverlay(false)
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
                val targetFps = 60
                val frameTimeMs = 1000L / targetFps

                // FPS tracking
                var frameCount = 0
                var fpsStartTime = System.nanoTime()
                val fpsUpdateIntervalNs = 500_000_000L // Update FPS every 0.5 seconds
                var currentFps = 0.0

                // Fallback rotation when no sensor data
                var fallbackAngle = 0f

                while (rendering) {
                    val frameStart = System.currentTimeMillis()

                    // Set up projection matrix
                    val aspect = if (surfaceHeight > 0) {
                        surfaceWidth.toFloat() / surfaceHeight.toFloat()
                    } else {
                        1f
                    }

                    val fov = astronomerModel?.fieldOfView ?: 60f
                    val projection = Matrix.perspective(
                        fovYDegrees = fov,
                        aspect = aspect,
                        near = 0.1f,
                        far = 100f
                    )
                    renderer.setProjectionMatrix(projection)

                    // Build view matrix from astronomer model or use fallback
                    val viewMatrix = astronomerModel?.let { model ->
                        val pointing = model.getPointing()
                        val lineOfSight = pointing.lineOfSight
                        val up = pointing.perpendicular

                        // Camera at origin, looking toward lineOfSight direction
                        // lookAt expects a point to look at, so we use the unit vector
                        // scaled to be in front of the camera
                        Matrix.lookAt(
                            0f, 0f, 0f,
                            -lineOfSight.x, -lineOfSight.y, -lineOfSight.z,
                            up.x, up.y, up.z
                        )
                    } ?: run {
                        // Fallback: slowly rotate through sky
                        fallbackAngle += 0.1f
                        if (fallbackAngle >= 360f) fallbackAngle = 0f
                        Matrix.multiply(
                            Matrix.translate(0f, 0f, -2f),
                            Matrix.rotateY(fallbackAngle)
                        )
                    }
                    renderer.setViewMatrix(viewMatrix)

                    // Begin frame
                    if (renderer.beginFrame()) {
                        val layers = layerManager

                        // Draw grid lines first (background layer)
                        if (layers?.isVisible(Layer.GRID) == true) {
                            val gridBatch = gridLayer.getGridBatch()
                            if (gridBatch.vertexCount > 0) {
                                renderer.draw(gridBatch)
                            }
                        }

                        // Draw horizon line
                        if (layers?.isVisible(Layer.HORIZON) == true) {
                            val horizonBatch = horizonLayer.getHorizonBatch(astronomerModel)
                            if (horizonBatch.vertexCount > 0) {
                                renderer.draw(horizonBatch)
                            }
                        }

                        // Draw constellation lines (behind stars)
                        if (layers?.isVisible(Layer.CONSTELLATIONS) != false) {
                            constellationCatalog?.let { catalog ->
                                val constellationBatch = catalog.getConstellationBatch()
                                if (constellationBatch.vertexCount > 0) {
                                    renderer.draw(constellationBatch)
                                }
                            }
                        }

                        // Draw stars from catalog with frustum culling
                        if (layers?.isVisible(Layer.STARS) != false) {
                            starCatalog?.let { catalog ->
                                // Get look direction for frustum culling
                                val lookDir = astronomerModel?.getPointing()?.lineOfSight
                                val currentFov = fov

                                val starBatch = if (lookDir != null) {
                                    // Use frustum culling
                                    catalog.getVisibleStarBatch(
                                        -lookDir.x, -lookDir.y, -lookDir.z,  // Negate because we look toward negative direction
                                        currentFov * 1.5f  // Add margin for safety
                                    )
                                } else {
                                    // Fall back to all stars
                                    catalog.getStarBatch()
                                }
                                if (starBatch.vertexCount > 0) {
                                    renderer.draw(starBatch)
                                }
                            }
                        }

                        // End frame
                        renderer.endFrame()
                    }

                    frameCount++

                    // Calculate FPS
                    val now = System.nanoTime()
                    val elapsed = now - fpsStartTime
                    if (elapsed >= fpsUpdateIntervalNs) {
                        currentFps = frameCount * 1_000_000_000.0 / elapsed
                        frameCount = 0
                        fpsStartTime = now

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
