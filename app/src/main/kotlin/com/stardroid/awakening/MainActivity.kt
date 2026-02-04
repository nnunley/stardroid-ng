package com.stardroid.awakening

import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.stardroid.awakening.data.ConstellationCatalog
import com.stardroid.awakening.data.StarCatalog
import com.stardroid.awakening.ui.FpsOverlay
import com.stardroid.awakening.vulkan.VulkanSurfaceView

class MainActivity : AppCompatActivity() {
    private lateinit var vulkanSurfaceView: VulkanSurfaceView
    private lateinit var fpsOverlay: FpsOverlay
    private lateinit var starCatalog: StarCatalog
    private lateinit var constellationCatalog: ConstellationCatalog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load catalogs in background
        starCatalog = StarCatalog(assets)
        constellationCatalog = ConstellationCatalog(assets)
        Thread {
            starCatalog.load()
            constellationCatalog.load()
        }.start()

        // Create container layout
        val container = FrameLayout(this)

        // Create Vulkan surface view
        vulkanSurfaceView = VulkanSurfaceView(this)
        vulkanSurfaceView.starCatalog = starCatalog
        vulkanSurfaceView.constellationCatalog = constellationCatalog
        container.addView(vulkanSurfaceView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Create FPS overlay in bottom-left corner
        fpsOverlay = FpsOverlay(this)
        val fpsParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            setMargins(16, 0, 0, 48)
        }
        container.addView(fpsOverlay, fpsParams)

        // Connect FPS updates
        vulkanSurfaceView.onFpsUpdate = { fps ->
            fpsOverlay.updateFps(fps)
        }

        setContentView(container)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            fpsOverlay.toggle()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onResume() {
        super.onResume()
        vulkanSurfaceView.onResume()
    }

    override fun onPause() {
        vulkanSurfaceView.onPause()
        super.onPause()
    }
}
