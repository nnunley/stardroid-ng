package com.stardroid.awakening

import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.stardroid.awakening.ui.FpsOverlay
import com.stardroid.awakening.vulkan.VulkanSurfaceView

class MainActivity : AppCompatActivity() {
    private lateinit var vulkanSurfaceView: VulkanSurfaceView
    private lateinit var fpsOverlay: FpsOverlay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create container layout
        val container = FrameLayout(this)

        // Create Vulkan surface view
        vulkanSurfaceView = VulkanSurfaceView(this)
        container.addView(vulkanSurfaceView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Create FPS overlay in top-left corner
        fpsOverlay = FpsOverlay(this)
        val fpsParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            setMargins(16, 120, 0, 0) // Below status bar
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
