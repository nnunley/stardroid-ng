package com.stardroid.awakening

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.stardroid.awakening.vulkan.VulkanSurfaceView

class MainActivity : AppCompatActivity() {
    private lateinit var vulkanSurfaceView: VulkanSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create and set VulkanSurfaceView as the main content view
        vulkanSurfaceView = VulkanSurfaceView(this)
        setContentView(vulkanSurfaceView)
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
