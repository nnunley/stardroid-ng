package com.stardroid.awakening.ephemeris

import kotlin.math.*

/**
 * Calculates the Moon's position using a simplified algorithm.
 *
 * Based on Meeus, "Astronomical Algorithms", Chapter 47.
 * Accuracy is about 0.1° (sufficient for visual display).
 */
object MoonCalculator {

    /**
     * Calculate the Moon's geocentric position.
     *
     * @param jd Julian Date
     * @return CelestialPosition with RA (degrees), Dec (degrees), and distance (km converted to AU)
     */
    fun position(jd: Double): CelestialPosition {
        // Julian centuries since J2000.0
        val t = JulianDate.toJulianCenturies(jd)

        // Moon's mean longitude (degrees)
        val lPrime = (218.3164477 + 481267.88123421 * t
                - 0.0015786 * t * t + t * t * t / 538841.0
                - t * t * t * t / 65194000.0).mod(360.0)

        // Moon's mean elongation (degrees)
        val d = (297.8501921 + 445267.1114034 * t
                - 0.0018819 * t * t + t * t * t / 545868.0
                - t * t * t * t / 113065000.0).mod(360.0)

        // Sun's mean anomaly (degrees)
        val m = (357.5291092 + 35999.0502909 * t
                - 0.0001536 * t * t + t * t * t / 24490000.0).mod(360.0)

        // Moon's mean anomaly (degrees)
        val mPrime = (134.9633964 + 477198.8675055 * t
                + 0.0087414 * t * t + t * t * t / 69699.0
                - t * t * t * t / 14712000.0).mod(360.0)

        // Moon's argument of latitude (degrees)
        val f = (93.2720950 + 483202.0175233 * t
                - 0.0036539 * t * t - t * t * t / 3526000.0
                + t * t * t * t / 863310000.0).mod(360.0)

        // Additional arguments
        val a1 = (119.75 + 131.849 * t).mod(360.0)
        val a2 = (53.09 + 479264.290 * t).mod(360.0)
        val a3 = (313.45 + 481266.484 * t).mod(360.0)

        // Eccentricity correction
        val e = 1.0 - 0.002516 * t - 0.0000074 * t * t

        // Convert to radians for trig functions
        val dRad = Math.toRadians(d)
        val mRad = Math.toRadians(m)
        val mPrimeRad = Math.toRadians(mPrime)
        val fRad = Math.toRadians(f)
        val a1Rad = Math.toRadians(a1)
        val a2Rad = Math.toRadians(a2)
        val a3Rad = Math.toRadians(a3)

        // Longitude terms (simplified - main terms only)
        var sigmaL = 6288774.0 * sin(mPrimeRad)
        sigmaL += 1274027.0 * sin(2 * dRad - mPrimeRad)
        sigmaL += 658314.0 * sin(2 * dRad)
        sigmaL += 213618.0 * sin(2 * mPrimeRad)
        sigmaL -= 185116.0 * sin(mRad) * e
        sigmaL -= 114332.0 * sin(2 * fRad)
        sigmaL += 58793.0 * sin(2 * dRad - 2 * mPrimeRad)
        sigmaL += 57066.0 * sin(2 * dRad - mRad - mPrimeRad) * e
        sigmaL += 53322.0 * sin(2 * dRad + mPrimeRad)
        sigmaL += 45758.0 * sin(2 * dRad - mRad) * e

        // Additional longitude corrections
        sigmaL += 3958.0 * sin(a1Rad)
        sigmaL += 1962.0 * sin(lPrime - fRad)
        sigmaL += 318.0 * sin(a2Rad)

        // Latitude terms (simplified)
        var sigmaB = 5128122.0 * sin(fRad)
        sigmaB += 280602.0 * sin(mPrimeRad + fRad)
        sigmaB += 277693.0 * sin(mPrimeRad - fRad)
        sigmaB += 173237.0 * sin(2 * dRad - fRad)
        sigmaB += 55413.0 * sin(2 * dRad - mPrimeRad + fRad)
        sigmaB += 46271.0 * sin(2 * dRad - mPrimeRad - fRad)
        sigmaB += 32573.0 * sin(2 * dRad + fRad)

        // Additional latitude corrections
        sigmaB -= 2235.0 * sin(lPrime)
        sigmaB += 382.0 * sin(a3Rad)
        sigmaB += 175.0 * sin(a1Rad - fRad)
        sigmaB += 175.0 * sin(a1Rad + fRad)

        // Distance terms (simplified)
        var sigmaR = -20905355.0 * cos(mPrimeRad)
        sigmaR -= 3699111.0 * cos(2 * dRad - mPrimeRad)
        sigmaR -= 2955968.0 * cos(2 * dRad)
        sigmaR -= 569925.0 * cos(2 * mPrimeRad)
        sigmaR += 48888.0 * cos(mRad) * e
        sigmaR -= 3149.0 * cos(2 * fRad)
        sigmaR += 246158.0 * cos(2 * dRad - 2 * mPrimeRad)
        sigmaR -= 152138.0 * cos(2 * dRad - mRad - mPrimeRad) * e
        sigmaR -= 170733.0 * cos(2 * dRad + mPrimeRad)
        sigmaR -= 204586.0 * cos(2 * dRad - mRad) * e

        // Moon's ecliptic longitude and latitude
        val lambda = lPrime + sigmaL / 1000000.0
        val beta = sigmaB / 1000000.0

        // Distance in km
        val distanceKm = 385000.56 + sigmaR / 1000.0

        // Nutation correction (simplified)
        val omega = 125.04452 - 1934.136261 * t
        val deltaPsi = -17.2 / 3600.0 * sin(Math.toRadians(omega))

        // Apparent longitude
        val lambdaApp = lambda + deltaPsi

        // Obliquity of the ecliptic
        val epsilon = 23.439291 - 0.0130042 * t
        val epsilonApp = epsilon + 9.2 / 3600.0 * cos(Math.toRadians(omega))

        // Convert ecliptic to equatorial coordinates
        val lambdaRad = Math.toRadians(lambdaApp)
        val betaRad = Math.toRadians(beta)
        val epsilonRad = Math.toRadians(epsilonApp)

        val ra = atan2(
            sin(lambdaRad) * cos(epsilonRad) - tan(betaRad) * sin(epsilonRad),
            cos(lambdaRad)
        )
        val dec = asin(
            sin(betaRad) * cos(epsilonRad) + cos(betaRad) * sin(epsilonRad) * sin(lambdaRad)
        )

        // Convert distance to AU (1 AU = 149,597,870.7 km)
        val distanceAu = distanceKm / 149597870.7

        return CelestialPosition(
            raDeg = Math.toDegrees(ra).mod(360.0),
            decDeg = Math.toDegrees(dec),
            distanceAu = distanceAu,
            name = "Moon"
        )
    }

    /**
     * Calculate the Moon's phase (0 = new, 0.5 = full, 1 = new again).
     */
    fun phase(jd: Double): Double {
        val t = JulianDate.toJulianCenturies(jd)

        // Mean elongation
        val d = (297.8501921 + 445267.1114034 * t).mod(360.0)

        // Phase angle (0° = new, 180° = full)
        return d / 360.0
    }
}
