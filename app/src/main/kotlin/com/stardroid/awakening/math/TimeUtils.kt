package com.stardroid.awakening.math

import java.util.*
import kotlin.math.floor

private const val MINUTES_PER_HOUR = 60.0
private const val SECONDS_PER_HOUR = 3600.0

/**
 * Calculate Julian Day for a given date.
 * Valid for years 1900-2099.
 */
fun julianDay(date: Date): Double {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
    cal.time = date
    val hour = (cal[Calendar.HOUR_OF_DAY] +
            cal[Calendar.MINUTE] / MINUTES_PER_HOUR +
            cal[Calendar.SECOND] / SECONDS_PER_HOUR)
    val year = cal[Calendar.YEAR]
    val month = cal[Calendar.MONTH] + 1
    val day = cal[Calendar.DAY_OF_MONTH]

    return (367.0 * year - floor(
        7.0 * (year + floor((month + 9.0) / 12.0)) / 4.0
    ) + floor(275.0 * month / 9.0) + day + 1721013.5 + hour / 24.0)
}

/**
 * Calculate local mean sidereal time in degrees.
 * Longitude is negative for western values.
 */
fun meanSiderealTime(date: Date, longitude: Float): Float {
    val jd = julianDay(date)
    val delta = jd - 2451545.0

    // Greenwich sidereal time
    val gst = 280.461 + 360.98564737 * delta

    // Local sidereal time
    val lst = normalizeAngle(gst + longitude)
    return lst.toFloat()
}

/**
 * Normalize angle to range [0, 360).
 */
private fun normalizeAngle(angleDegrees: Double): Double {
    var result = angleDegrees % 360.0
    if (result < 0) result += 360.0
    return result
}
