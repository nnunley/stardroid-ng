package com.stardroid.awakening.ephemeris

import kotlin.math.*

/**
 * Calculates the Sun's position using a simplified algorithm.
 *
 * Based on the "low accuracy" algorithm from the Astronomical Almanac,
 * accurate to about 0.01° (sufficient for visual display).
 *
 * Reference: Meeus, "Astronomical Algorithms", Chapter 25
 */
object SunCalculator {

    /**
     * Calculate the Sun's geocentric position.
     *
     * @param jd Julian Date
     * @return CelestialPosition with RA (degrees), Dec (degrees), and distance (AU)
     */
    fun position(jd: Double): CelestialPosition {
        // Julian centuries since J2000.0
        val t = JulianDate.toJulianCenturies(jd)

        // Mean longitude of the Sun (degrees)
        val l0 = (280.46646 + 36000.76983 * t + 0.0003032 * t * t).mod(360.0)

        // Mean anomaly of the Sun (degrees)
        val m = (357.52911 + 35999.05029 * t - 0.0001537 * t * t).mod(360.0)
        val mRad = Math.toRadians(m)

        // Eccentricity of Earth's orbit
        val e = 0.016708634 - 0.000042037 * t - 0.0000001267 * t * t

        // Sun's equation of center (degrees)
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(mRad) +
                (0.019993 - 0.000101 * t) * sin(2 * mRad) +
                0.000289 * sin(3 * mRad)

        // Sun's true longitude (degrees)
        val sunLon = l0 + c

        // Sun's true anomaly (degrees)
        val v = m + c

        // Sun's radius vector (distance in AU)
        val vRad = Math.toRadians(v)
        val r = (1.000001018 * (1 - e * e)) / (1 + e * cos(vRad))

        // Apparent longitude (corrected for aberration and nutation)
        val omega = 125.04 - 1934.136 * t
        val lambda = sunLon - 0.00569 - 0.00478 * sin(Math.toRadians(omega))

        // Obliquity of the ecliptic (degrees)
        val epsilon = obliquity(t) + 0.00256 * cos(Math.toRadians(omega))

        // Convert ecliptic to equatorial coordinates
        val lambdaRad = Math.toRadians(lambda)
        val epsilonRad = Math.toRadians(epsilon)

        val ra = atan2(cos(epsilonRad) * sin(lambdaRad), cos(lambdaRad))
        val dec = asin(sin(epsilonRad) * sin(lambdaRad))

        return CelestialPosition(
            raDeg = Math.toDegrees(ra).mod(360.0),
            decDeg = Math.toDegrees(dec),
            distanceAu = r,
            name = "Sun"
        )
    }

    /**
     * Mean obliquity of the ecliptic (degrees).
     */
    private fun obliquity(t: Double): Double {
        return 23.439291 - 0.0130042 * t - 0.00000016 * t * t + 0.000000504 * t * t * t
    }
}

/**
 * Represents a celestial object's position.
 */
data class CelestialPosition(
    val raDeg: Double,      // Right Ascension in degrees [0, 360)
    val decDeg: Double,     // Declination in degrees [-90, 90]
    val distanceAu: Double, // Distance in AU (for size scaling)
    val name: String
) {
    /**
     * Convert to unit vector on celestial sphere.
     */
    fun toUnitVector(): Triple<Float, Float, Float> {
        val raRad = Math.toRadians(raDeg)
        val decRad = Math.toRadians(decDeg)

        val x = (cos(decRad) * cos(raRad)).toFloat()
        val y = (cos(decRad) * sin(raRad)).toFloat()
        val z = sin(decRad).toFloat()

        return Triple(x, y, z)
    }
}
