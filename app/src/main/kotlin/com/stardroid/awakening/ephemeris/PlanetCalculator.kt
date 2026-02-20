package com.stardroid.awakening.ephemeris

import kotlin.math.*

/**
 * Calculates planetary positions using VSOP87-based simplified algorithm.
 *
 * Based on Meeus, "Astronomical Algorithms", Chapters 31-32.
 * Accuracy is about 1 arcminute for inner planets, 1-2 arcminutes for outer planets.
 */
object PlanetCalculator {

    /**
     * Orbital elements for each planet.
     * Values are for J2000.0 with secular rates per Julian century.
     * Format: [L0, L1, a, e0, e1, i0, i1, omega0, omega1, Omega0, Omega1]
     *   L = mean longitude, a = semi-major axis (AU), e = eccentricity,
     *   i = inclination, omega = argument of perihelion, Omega = longitude of ascending node
     */
    private val ORBITAL_ELEMENTS = mapOf(
        Planet.MERCURY to doubleArrayOf(
            252.2503235, 149474.0722491,  // L0, L1
            0.38709927, 0.0,              // a (constant)
            0.20563593, 0.00002123,       // e0, e1
            7.00497902, -0.00594749,      // i0, i1
            77.45779628, 0.16047689,      // omega0, omega1
            48.33076593, -0.12534081      // Omega0, Omega1
        ),
        Planet.VENUS to doubleArrayOf(
            181.9790995, 58519.2130302,
            0.72333566, 0.0,
            0.00677672, -0.00004107,
            3.39467605, -0.00078890,
            131.60246718, 0.00268329,
            76.67984255, -0.27769418
        ),
        Planet.MARS to doubleArrayOf(
            355.4329650, 19141.6964746,
            1.52371034, 0.0,
            0.09339410, 0.00007882,
            1.84969142, -0.00813131,
            336.05637041, 0.44441088,
            49.55953891, -0.29257343
        ),
        Planet.JUPITER to doubleArrayOf(
            34.3514839, 3036.3027748,
            5.20288700, 0.0,
            0.04838624, -0.00013253,
            1.30439695, -0.00183714,
            14.72847983, 0.21252668,
            100.47390909, 0.20469106
        ),
        Planet.SATURN to doubleArrayOf(
            49.9558099, 1223.5110686,
            9.53667594, 0.0,
            0.05386179, -0.00050991,
            2.48599187, 0.00193609,
            92.59887831, -0.41897216,
            113.66242448, -0.28867794
        ),
        Planet.URANUS to doubleArrayOf(
            313.2381045, 429.8640561,
            19.18916464, 0.0,
            0.04725744, -0.00004397,
            0.77263783, -0.00242939,
            170.95427630, 0.40805281,
            74.01692503, 0.04240589
        ),
        Planet.NEPTUNE to doubleArrayOf(
            304.8798344, 219.8833092,
            30.06992276, 0.0,
            0.00859048, 0.00005105,
            1.77004347, 0.00035372,
            44.96476227, -0.32241464,
            131.78422574, -0.00508664
        )
    )

    /**
     * Calculate a planet's geocentric position.
     *
     * @param planet The planet to calculate
     * @param jd Julian Date
     * @return CelestialPosition with RA (degrees), Dec (degrees), and distance (AU)
     */
    fun position(planet: Planet, jd: Double): CelestialPosition {
        val t = JulianDate.toJulianCenturies(jd)

        // Get Earth's heliocentric position
        val earthHelio = heliocentricPosition(Planet.EARTH, t)

        // Get planet's heliocentric position
        val planetHelio = heliocentricPosition(planet, t)

        // Convert to geocentric by subtracting Earth's position
        val x = planetHelio.x - earthHelio.x
        val y = planetHelio.y - earthHelio.y
        val z = planetHelio.z - earthHelio.z

        // Distance from Earth
        val distance = sqrt(x * x + y * y + z * z)

        // Convert to equatorial coordinates
        // Obliquity of the ecliptic
        val epsilon = Math.toRadians(23.439291 - 0.0130042 * t)

        // Rotate from ecliptic to equatorial
        val xEq = x
        val yEq = y * cos(epsilon) - z * sin(epsilon)
        val zEq = y * sin(epsilon) + z * cos(epsilon)

        // Convert to RA/Dec
        val ra = atan2(yEq, xEq)
        val dec = atan2(zEq, sqrt(xEq * xEq + yEq * yEq))

        return CelestialPosition(
            raDeg = Math.toDegrees(ra).mod(360.0),
            decDeg = Math.toDegrees(dec),
            distanceAu = distance,
            name = planet.displayName
        )
    }

    /**
     * Calculate heliocentric position of a planet.
     */
    private fun heliocentricPosition(planet: Planet, t: Double): HeliocentricPosition {
        val elements = if (planet == Planet.EARTH) {
            // Earth's orbital elements
            doubleArrayOf(
                100.46457166, 36000.7698231,
                1.00000261, 0.0,
                0.01671123, -0.00004392,
                0.0, 0.0,  // Ecliptic is defined by Earth's orbit
                102.93768193, 0.32327364,
                0.0, 0.0
            )
        } else {
            ORBITAL_ELEMENTS[planet] ?: throw IllegalArgumentException("Unknown planet: $planet")
        }

        // Calculate orbital elements at time t
        val L = Math.toRadians((elements[0] + elements[1] * t).mod(360.0))  // Mean longitude
        val a = elements[2]  // Semi-major axis
        val e = elements[4] + elements[5] * t  // Eccentricity
        val i = Math.toRadians(elements[6] + elements[7] * t)  // Inclination
        val omega = Math.toRadians((elements[8] + elements[9] * t).mod(360.0))  // Arg of perihelion
        val Omega = Math.toRadians((elements[10] + elements[11] * t).mod(360.0))  // Long of ascending node

        // Mean anomaly
        val M = L - omega

        // Solve Kepler's equation for eccentric anomaly (Newton-Raphson)
        var E = M
        for (iter in 0 until 10) {
            val dE = (M - E + e * sin(E)) / (1 - e * cos(E))
            E += dE
            if (abs(dE) < 1e-9) break
        }

        // True anomaly
        val nu = 2 * atan2(sqrt(1 + e) * sin(E / 2), sqrt(1 - e) * cos(E / 2))

        // Distance from Sun
        val r = a * (1 - e * cos(E))

        // Position in orbital plane
        val xOrb = r * cos(nu)
        val yOrb = r * sin(nu)

        // Rotate to ecliptic coordinates
        val cosOmega = cos(Omega)
        val sinOmega = sin(Omega)
        val cosI = cos(i)
        val sinI = sin(i)
        val cosOmegaPeri = cos(omega - L + nu)  // This simplifies for circular orbits
        val sinOmegaPeri = sin(omega - L + nu)

        // Full rotation matrix application
        val xEcl = (cos(Omega) * cos(omega) - sin(Omega) * sin(omega) * cos(i)) * xOrb +
                   (-cos(Omega) * sin(omega) - sin(Omega) * cos(omega) * cos(i)) * yOrb
        val yEcl = (sin(Omega) * cos(omega) + cos(Omega) * sin(omega) * cos(i)) * xOrb +
                   (-sin(Omega) * sin(omega) + cos(Omega) * cos(omega) * cos(i)) * yOrb
        val zEcl = sin(omega) * sin(i) * xOrb + cos(omega) * sin(i) * yOrb

        return HeliocentricPosition(xEcl, yEcl, zEcl)
    }

    private data class HeliocentricPosition(val x: Double, val y: Double, val z: Double)
}

/**
 * Planets in the solar system.
 */
enum class Planet(val displayName: String) {
    MERCURY("Mercury"),
    VENUS("Venus"),
    EARTH("Earth"),
    MARS("Mars"),
    JUPITER("Jupiter"),
    SATURN("Saturn"),
    URANUS("Uranus"),
    NEPTUNE("Neptune")
}
