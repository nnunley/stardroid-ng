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
import com.stardroid.awakening.ui.CompassOverlay
import com.stardroid.awakening.ui.FpsOverlay
import com.stardroid.awakening.vulkan.VulkanSurfaceView

class MainActivity : AppCompatActivity(), LocationListener {
    private lateinit var vulkanSurfaceView: VulkanSurfaceView
    private lateinit var fpsOverlay: FpsOverlay
    private lateinit var compassOverlay: CompassOverlay
    private lateinit var starCatalog: StarCatalog
    private lateinit var constellationCatalog: ConstellationCatalog
    private lateinit var astronomerModel: AstronomerModel
    private var sensorController: SensorOrientationController? = null
    private var locationManager: LocationManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize astronomer model
        astronomerModel = AstronomerModel()

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

        // Create compass overlay in bottom-right corner
        compassOverlay = CompassOverlay(this)
        compassOverlay.astronomerModel = astronomerModel
        val compassSize = (100 * resources.displayMetrics.density).toInt()
        val compassParams = FrameLayout.LayoutParams(compassSize, compassSize).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(0, 0, 16, 48)
        }
        container.addView(compassOverlay, compassParams)

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
        if (requestCode == LOCATION_PERMISSION_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }

    override fun onLocationChanged(location: Location) {
        astronomerModel.location = LatLong(
            location.latitude.toFloat(),
            location.longitude.toFloat()
        )
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
    }
}
