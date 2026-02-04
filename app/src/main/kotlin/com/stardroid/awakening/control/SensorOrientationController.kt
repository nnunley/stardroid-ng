package com.stardroid.awakening.control

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.stardroid.awakening.math.Vector3

/**
 * Handles device orientation sensors and updates the AstronomerModel.
 *
 * Prefers rotation vector sensor (fused accelerometer + magnetometer + gyro)
 * but falls back to accelerometer + magnetometer if unavailable.
 */
class SensorOrientationController(
    private val sensorManager: SensorManager,
    private val model: AstronomerModel
) : SensorEventListener {

    private var rotationVectorSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var magnetometerSensor: Sensor? = null

    private var useRotationVector = false
    private var isStarted = false

    // Cached sensor values for legacy mode
    private val acceleration = Vector3(0f, 0f, -9.8f)
    private val magneticField = Vector3(0f, 1f, 0f)

    // Damping for sensor smoothing
    private var dampingFactor = 0.5f

    init {
        // Try to get rotation vector sensor first
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (rotationVectorSensor != null) {
            useRotationVector = true
            Log.i(TAG, "Using rotation vector sensor")
        } else {
            // Fall back to accelerometer + magnetometer
            accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            useRotationVector = false
            Log.i(TAG, "Using accelerometer + magnetometer")
        }
    }

    /**
     * Start listening to sensors.
     */
    fun start() {
        if (isStarted) return
        isStarted = true

        val delay = SensorManager.SENSOR_DELAY_GAME

        if (useRotationVector) {
            rotationVectorSensor?.let {
                sensorManager.registerListener(this, it, delay)
            }
        } else {
            accelerometerSensor?.let {
                sensorManager.registerListener(this, it, delay)
            }
            magnetometerSensor?.let {
                sensorManager.registerListener(this, it, delay)
            }
        }
    }

    /**
     * Stop listening to sensors.
     */
    fun stop() {
        if (!isStarted) return
        isStarted = false
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                model.setPhoneSensorValues(event.values)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                applyDamping(acceleration, event.values, dampingFactor)
                model.setPhoneSensorValues(acceleration, magneticField)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                applyDamping(magneticField, event.values, dampingFactor * 0.1f)
                model.setPhoneSensorValues(acceleration, magneticField)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Could show calibration prompt for magnetometer
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            when (accuracy) {
                SensorManager.SENSOR_STATUS_UNRELIABLE -> {
                    Log.w(TAG, "Magnetometer unreliable - calibration needed")
                }
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> {
                    Log.w(TAG, "Magnetometer accuracy low")
                }
            }
        }
    }

    /**
     * Apply exponential moving average damping.
     */
    private fun applyDamping(target: Vector3, newValues: FloatArray, factor: Float) {
        target.x = target.x * factor + newValues[0] * (1 - factor)
        target.y = target.y * factor + newValues[1] * (1 - factor)
        target.z = target.z * factor + newValues[2] * (1 - factor)
    }

    /**
     * Set damping factor (0 = no damping, 1 = infinite damping).
     */
    fun setDamping(factor: Float) {
        dampingFactor = factor.coerceIn(0f, 0.99f)
    }

    companion object {
        private const val TAG = "SensorOrientation"
    }
}
