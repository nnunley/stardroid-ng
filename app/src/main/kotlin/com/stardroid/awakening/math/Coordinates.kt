package com.stardroid.awakening.math

import java.util.*
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

const val DEGREES_TO_RADIANS = (Math.PI / 180.0).toFloat()
const val RADIANS_TO_DEGREES = (180.0 / Math.PI).toFloat()

/**
 * Right Ascension and Declination in degrees.
 */
data class RaDec(
    val ra: Float,   // 0-360 degrees
    val dec: Float   // -90 to +90 degrees
)

/**
 * Convert RA/Dec to geocentric unit vector.
 */
fun getGeocentricCoords(ra: Float, dec: Float): Vector3 {
    val raRad = ra * DEGREES_TO_RADIANS
    val decRad = dec * DEGREES_TO_RADIANS
    return Vector3(
        cos(raRad) * cos(decRad),
        sin(raRad) * cos(decRad),
        sin(decRad)
    )
}

/**
 * Convert geocentric unit vector to RA in degrees.
 */
fun getRaOfUnitVector(v: Vector3): Float {
    return RADIANS_TO_DEGREES * atan2(v.y, v.x)
}

/**
 * Convert geocentric unit vector to Dec in degrees.
 */
fun getDecOfUnitVector(v: Vector3): Float {
    return RADIANS_TO_DEGREES * asin(v.z)
}

/**
 * Calculate RA/Dec of zenith for given time and location.
 */
fun calculateZenithRaDec(time: Date, location: LatLong): RaDec {
    val ra = meanSiderealTime(time, location.longitude)
    val dec = location.latitude
    return RaDec(ra, dec)
}

/**
 * Calculate zenith vector in geocentric coordinates.
 */
fun calculateZenith(time: Date, location: LatLong): Vector3 {
    val raDec = calculateZenithRaDec(time, location)
    return getGeocentricCoords(raDec.ra, raDec.dec)
}
