package com.stardroid.awakening.math

/**
 * Latitude and longitude in degrees.
 * Latitude: -90 (south) to +90 (north)
 * Longitude: -180 (west) to +180 (east)
 */
data class LatLong(
    val latitude: Float,
    val longitude: Float
) {
    companion object {
        val ZERO = LatLong(0f, 0f)
    }
}
