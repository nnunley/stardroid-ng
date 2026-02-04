package com.stardroid.awakening

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.stardroid.awakening.control.AstronomerModel
import com.stardroid.awakening.control.SensorOrientationController
import com.stardroid.awakening.data.ConstellationCatalog
import com.stardroid.awakening.data.StarCatalog
import com.stardroid.awakening.math.LatLong
import com.stardroid.awakening.ar.CameraSurfaceView
import com.stardroid.awakening.layers.Layer
import com.stardroid.awakening.layers.LayerManager
import com.stardroid.awakening.ui.CompassOverlay
import com.stardroid.awakening.ui.FpsOverlay
import com.stardroid.awakening.ui.LayerToggleOverlay
import com.stardroid.awakening.vulkan.VulkanSurfaceView

class MainActivity : AppCompatActivity(), LocationListener {
    private lateinit var vulkanSurfaceView: VulkanSurfaceView
    private lateinit var fpsOverlay: FpsOverlay
    private lateinit var compassOverlay: CompassOverlay
    private lateinit var layerToggleOverlay: LayerToggleOverlay
    private lateinit var layerManager: LayerManager
    private lateinit var starCatalog: StarCatalog
    private var cameraPreviewView: CameraSurfaceView? = null
    private lateinit var constellationCatalog: ConstellationCatalog
    private lateinit var astronomerModel: AstronomerModel
    private var sensorController: SensorOrientationController? = null
    private var locationManager: LocationManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize astronomer model and layer manager
        astronomerModel = AstronomerModel()
        layerManager = LayerManager()

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
        vulkanSurfaceView.astronomerModel = astronomerModel
        vulkanSurfaceView.layerManager = layerManager
        container.addView(vulkanSurfaceView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Create compass overlay in bottom-left corner
        compassOverlay = CompassOverlay(this)
        compassOverlay.astronomerModel = astronomerModel
        val compassSize = (100 * resources.displayMetrics.density).toInt()
        val compassParams = FrameLayout.LayoutParams(compassSize, compassSize).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            setMargins(16, 0, 0, 48)
        }
        container.addView(compassOverlay, compassParams)

        // Create FPS overlay in bottom-left corner (above compass)
        fpsOverlay = FpsOverlay(this)
        val fpsParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            setMargins(16, 0, 0, compassSize + 56)
        }
        container.addView(fpsOverlay, fpsParams)

        // Create layer toggle FAB in bottom-right corner
        layerToggleOverlay = LayerToggleOverlay(this)
        layerToggleOverlay.setup(
            manager = layerManager,
            onChange = {
                // Layer changed callback - handle AR camera toggle
                updateArCameraVisibility(container)
            },
            onOpacity = { opacity ->
                // Update Vulkan background opacity
                vulkanSurfaceView.backgroundOpacity = opacity
            }
        )
        val fabSize = (48 * resources.displayMetrics.density).toInt()
        val layerParams = FrameLayout.LayoutParams(fabSize, fabSize).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(0, 0, 16, 48)
        }
        container.addView(layerToggleOverlay, layerParams)

        // Connect FPS updates and compass updates
        vulkanSurfaceView.onFpsUpdate = { fps ->
            fpsOverlay.updateFps(fps)
            // Update compass on UI thread
            compassOverlay.post { compassOverlay.update() }
        }

        setContentView(container)

        // Initialize sensors
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorController = SensorOrientationController(sensorManager, astronomerModel)

        // Request location permission
        requestLocationUpdates()
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
        sensorController?.start()
    }

    override fun onPause() {
        sensorController?.stop()
        vulkanSurfaceView.onPause()
        super.onPause()
    }

    private fun requestLocationUpdates() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
            return
        }

        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        // Try GPS first, then network
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )

        for (provider in providers) {
            if (locationManager?.isProviderEnabled(provider) == true) {
                locationManager?.requestLocationUpdates(
                    provider,
                    60000L,  // Update every minute
                    1000f,   // Or every 1km
                    this
                )
                // Get last known location as starting point
                locationManager?.getLastKnownLocation(provider)?.let { location ->
                    onLocationChanged(location)
                }
                break
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocationUpdates()
                }
            }
            CAMERA_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Re-trigger AR camera if layer is enabled
                    val container = vulkanSurfaceView.parent as? FrameLayout
                    container?.let { updateArCameraVisibility(it) }
                }
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        astronomerModel.location = LatLong(
            location.latitude.toFloat(),
            location.longitude.toFloat()
        )
    }

    private fun updateArCameraVisibility(container: FrameLayout) {
        val shouldShowCamera = layerManager.isVisible(Layer.AR_CAMERA)
        android.util.Log.d("MainActivity", "updateArCameraVisibility: shouldShow=$shouldShowCamera, cameraView=${cameraPreviewView != null}")

        if (shouldShowCamera && cameraPreviewView == null) {
            // Check camera permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
                android.util.Log.d("MainActivity", "Requesting camera permission")
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_REQUEST
                )
                return
            }

            android.util.Log.d("MainActivity", "Creating camera preview")

            // Create camera SurfaceView - it will be behind Vulkan surface
            cameraPreviewView = CameraSurfaceView(this)
            container.addView(cameraPreviewView, 0, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))

            // Make Vulkan view transparent and put it on top using media overlay
            // This allows the camera SurfaceView to show through
            vulkanSurfaceView.setZOrderMediaOverlay(true)
            vulkanSurfaceView.holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)

            android.util.Log.d("MainActivity", "Camera SurfaceView added, Vulkan set as media overlay")
        } else if (!shouldShowCamera && cameraPreviewView != null) {
            android.util.Log.d("MainActivity", "Removing camera preview")
            // Remove camera preview
            cameraPreviewView?.stopPreview()
            container.removeView(cameraPreviewView)
            cameraPreviewView = null

            // Restore opaque Vulkan view
            vulkanSurfaceView.setZOrderMediaOverlay(false)
            vulkanSurfaceView.holder.setFormat(android.graphics.PixelFormat.OPAQUE)
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
        private const val CAMERA_PERMISSION_REQUEST = 1002
    }
}
