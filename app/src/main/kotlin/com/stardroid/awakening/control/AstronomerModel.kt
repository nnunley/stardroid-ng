package com.stardroid.awakening.control

import android.hardware.GeomagneticField
import android.hardware.SensorManager
import com.stardroid.awakening.math.*
import java.util.*

/**
 * The astronomer's model - transforms phone orientation to celestial coordinates.
 *
 * Handles three coordinate frames:
 * 1. Celestial - fixed against background stars
 * 2. Phone - relative to device orientation
 * 3. Local - relative to observer position (North, East, Up)
 */
class AstronomerModel {
    /** User's location on Earth */
    var location = LatLong.ZERO
        set(value) {
            field = value
            updateMagneticDeclination()
            celestialCoordsNeedUpdate = true
        }

    /** Field of view in degrees */
    var fieldOfView = 60f

    /** Current pointing direction in celestial coordinates */
    private val pointing = Pointing()

    /** Zenith in celestial coordinates */
    private var zenithCelestial = Vector3.unitZ()

    /** True north along ground in celestial coordinates */
    private var northCelestial = Vector3.unitY()

    /** True east in celestial coordinates */
    private var eastCelestial = Vector3.unitX()

    /** Magnetic declination (difference between magnetic and true north) */
    private var magneticDeclination = 0f

    /** Phone's up direction from sensors */
    private var upPhone = Vector3(0f, 0f, 1f)

    /** Whether using rotation vector sensor */
    private var useRotationVector = false

    /** Rotation vector from fused sensor */
    private val rotationVector = FloatArray(4)

    /** Acceleration from accelerometer */
    private val acceleration = Vector3(0f, 0f, -9.8f)

    /** Magnetic field from magnetometer */
    private val magneticField = Vector3(0f, 1f, 0f)

    /** [North, Up, East] inverse in phone coordinates */
    private var axesPhoneInverse = Matrix3x3.identity

    /** [North, Up, East] in celestial coordinates (with magnetic correction) */
    private var axesMagneticCelestial = Matrix3x3.identity

    private var celestialCoordsNeedUpdate = true
    private var lastCelestialUpdate = 0L

    /**
     * User's pointing direction and screen up vector.
     */
    data class Pointing(
        val lineOfSight: Vector3 = Vector3(1f, 0f, 0f),
        val perpendicular: Vector3 = Vector3(0f, 0f, 1f)
    ) {
        fun updateLineOfSight(v: Vector3) {
            (lineOfSight as Vector3).assign(v)
        }
        fun updatePerpendicular(v: Vector3) {
            (perpendicular as Vector3).assign(v)
        }
    }

    /**
     * Set phone sensor values from accelerometer and magnetometer.
     */
    fun setPhoneSensorValues(accel: Vector3, magnetic: Vector3) {
        if (accel.length2 < 0.01f || magnetic.length2 < 0.01f) return
        acceleration.assign(accel)
        magneticField.assign(magnetic)
        useRotationVector = false
    }

    /**
     * Set phone sensor values from rotation vector sensor.
     */
    fun setPhoneSensorValues(rotVec: FloatArray) {
        System.arraycopy(rotVec, 0, rotationVector, 0, minOf(rotVec.size, 4))
        useRotationVector = true
    }

    /**
     * Get current pointing direction.
     */
    fun getPointing(): Pointing {
        calculatePointing()
        return pointing
    }

    /**
     * Get zenith in celestial coordinates.
     */
    fun getZenith(): Vector3 {
        updateCelestialCoords()
        return zenithCelestial.copy()
    }

    /**
     * Get north (along ground) in celestial coordinates.
     */
    fun getNorth(): Vector3 {
        updateCelestialCoords()
        return northCelestial.copy()
    }

    private fun calculatePointing() {
        updateCelestialCoords()
        calculatePhoneAxes()

        val transform = axesMagneticCelestial * axesPhoneInverse

        // Phone's -Z axis is the looking direction (out of screen toward user, negated)
        val lookPhone = Vector3(0f, 0f, -1f)
        // Phone's +Y axis is screen up
        val screenUpPhone = Vector3(0f, 1f, 0f)

        val lookCelestial = transform * lookPhone
        val screenUpCelestial = transform * screenUpPhone

        pointing.updateLineOfSight(lookCelestial)
        pointing.updatePerpendicular(screenUpCelestial)
    }

    private fun updateCelestialCoords() {
        val now = System.currentTimeMillis()
        if (!celestialCoordsNeedUpdate && now - lastCelestialUpdate < 60000) return

        lastCelestialUpdate = now
        celestialCoordsNeedUpdate = false

        // Calculate zenith
        zenithCelestial = calculateZenith(Date(now), location)

        // Calculate north: project celestial pole onto horizon
        val celestialPole = Vector3.unitZ()
        val zDotUp = zenithCelestial dot celestialPole
        northCelestial = celestialPole - zenithCelestial * zDotUp
        northCelestial.normalize()

        // East is cross product
        eastCelestial = northCelestial cross zenithCelestial

        // Apply magnetic declination (rotate celestial axes opposite direction)
        val rotationMatrix = Matrix3x3.rotateAroundAxis(zenithCelestial, -magneticDeclination)
        val magneticNorth = rotationMatrix * northCelestial
        val magneticEast = magneticNorth cross zenithCelestial

        axesMagneticCelestial = Matrix3x3(magneticNorth, zenithCelestial, magneticEast)
    }

    private fun calculatePhoneAxes() {
        val magneticNorthPhone: Vector3
        val magneticEastPhone: Vector3

        if (useRotationVector) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
            // SensorManager rotation matrix rows:
            // Row 0 = East (X world axis in device coords)
            // Row 1 = North (Y world axis in device coords)
            // Row 2 = Up (Z world axis in device coords)
            magneticEastPhone = Vector3(rotationMatrix[0], rotationMatrix[1], rotationMatrix[2])
            magneticNorthPhone = Vector3(rotationMatrix[3], rotationMatrix[4], rotationMatrix[5])
            upPhone = Vector3(rotationMatrix[6], rotationMatrix[7], rotationMatrix[8])
        } else {
            // Use accelerometer and magnetometer
            // Accelerometer measures reaction to gravity, so points UP when device is flat
            upPhone = acceleration.normalizedCopy()
            val magNorm = magneticField.normalizedCopy()
            // Project magnetic field to horizontal plane (vector rejection)
            magneticNorthPhone = magNorm - upPhone * (magNorm dot upPhone)
            magneticNorthPhone.normalize()
            // East = North × Up (right-handed)
            magneticEastPhone = magneticNorthPhone cross upPhone
        }

        // Build inverse matrix (transpose since orthonormal)
        // Passing false means these are row vectors, which transposes to give us inverse
        axesPhoneInverse = Matrix3x3(magneticNorthPhone, upPhone, magneticEastPhone, false)
    }

    private fun updateMagneticDeclination() {
        try {
            val field = GeomagneticField(
                location.latitude,
                location.longitude,
                0f,  // altitude (sea level)
                System.currentTimeMillis()
            )
            magneticDeclination = field.declination
        } catch (e: Exception) {
            magneticDeclination = 0f
        }
    }

    companion object {
        // Phone coordinate system: -Z is looking direction (into screen)
        private val POINTING_IN_PHONE_COORDS = Vector3(0f, 0f, -1f)
        private val SCREEN_UP_IN_PHONE_COORDS = Vector3(0f, 1f, 0f)
    }
}
