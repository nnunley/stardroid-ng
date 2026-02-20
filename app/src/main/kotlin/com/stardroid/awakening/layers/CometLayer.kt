package com.stardroid.awakening.layers

import android.util.Log
import com.stardroid.awakening.ephemeris.JulianDate
import com.stardroid.awakening.renderer.DrawBatch
import com.stardroid.awakening.renderer.Matrix
import com.stardroid.awakening.renderer.PrimitiveType
import kotlin.math.*

/**
 * Displays periodic comets with positions computed from orbital elements.
 *
 * Uses Kepler's equation with Newton-Raphson iteration (50 iterations for
 * high-eccentricity orbits). Only shows comets within ~5 AU of Earth.
 */
class CometLayer {

    private data class CometElements(
        val name: String,
        val epochJd: Double,       // Epoch of perihelion passage (JD)
        val q: Double,             // Perihelion distance (AU)
        val e: Double,             // Eccentricity
        val iDeg: Double,          // Inclination (degrees)
        val omegaDeg: Double,      // Argument of perihelion (degrees)
        val bigOmegaDeg: Double,   // Longitude of ascending node (degrees)
        val periodYears: Double    // Orbital period (years)
    )

    // Orbital elements for well-known periodic comets
    // Epoch values are approximate perihelion passage dates
    private val comets = listOf(
        CometElements(
            name = "2P/Encke",
            epochJd = 2460349.5,       // 2024-Jan-22 perihelion
            q = 0.3359,
            e = 0.8483,
            iDeg = 11.78,
            omegaDeg = 186.54,
            bigOmegaDeg = 334.57,
            periodYears = 3.30
        ),
        CometElements(
            name = "1P/Halley",
            epochJd = 2446467.5,       // 1986-Feb-09 perihelion
            q = 0.5860,
            e = 0.96714,
            iDeg = 162.26,
            omegaDeg = 111.33,
            bigOmegaDeg = 58.42,
            periodYears = 75.32
        ),
        CometElements(
            name = "55P/Tempel-Tuttle",
            epochJd = 2450872.5,       // 1998-Feb-28 perihelion
            q = 0.9764,
            e = 0.9055,
            iDeg = 162.49,
            omegaDeg = 172.50,
            bigOmegaDeg = 235.27,
            periodYears = 33.22
        ),
        CometElements(
            name = "109P/Swift-Tuttle",
            epochJd = 2448968.5,       // 1992-Dec-12 perihelion
            q = 0.9595,
            e = 0.9632,
            iDeg = 113.45,
            omegaDeg = 152.98,
            bigOmegaDeg = 139.38,
            periodYears = 133.28
        ),
        CometElements(
            name = "67P/Churyumov-Gerasimenko",
            epochJd = 2460600.5,       // 2024-Nov-02 perihelion (approx)
            q = 1.2432,
            e = 0.6405,
            iDeg = 7.04,
            omegaDeg = 12.78,
            bigOmegaDeg = 50.14,
            periodYears = 6.44
        )
    )

    private val color = floatArrayOf(0.3f, 1.0f, 0.7f, 0.9f)
    private val MAX_DISTANCE_AU = 5.0

    private var cachedVertices: FloatArray = floatArrayOf()
    private var cachedCount: Int = 0
    private var lastUpdateTime = 0L
    private val UPDATE_INTERVAL_MS = 60_000L

    fun getBatch(): DrawBatch {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime > UPDATE_INTERVAL_MS || lastUpdateTime == 0L) {
            update()
            lastUpdateTime = now
        }
        return DrawBatch(
            type = PrimitiveType.POINTS,
            vertices = cachedVertices,
            vertexCount = cachedCount,
            transform = Matrix.identity()
        )
    }

    private fun update() {
        val jd = JulianDate.now()
        val t = JulianDate.toJulianCenturies(jd)

        // Earth's position (simplified, same as PlanetCalculator)
        val earthPos = earthHeliocentric(t)

        val verts = mutableListOf<Float>()

        for (comet in comets) {
            try {
                val pos = cometHeliocentric(comet, jd) ?: continue

                // Geocentric ecliptic
                val dx = pos.first - earthPos.first
                val dy = pos.second - earthPos.second
                val dz = pos.third - earthPos.third
                val dist = sqrt(dx * dx + dy * dy + dz * dz)

                if (dist > MAX_DISTANCE_AU) continue

                // Ecliptic to equatorial
                val obliquity = Math.toRadians(23.439291 - 0.0130042 * t)
                val cosEps = cos(obliquity)
                val sinEps = sin(obliquity)

                val xEq = dx
                val yEq = dy * cosEps - dz * sinEps
                val zEq = dy * sinEps + dz * cosEps

                // Normalize to unit vector
                val r = sqrt(xEq * xEq + yEq * yEq + zEq * zEq)
                val nx = (xEq / r).toFloat()
                val ny = (yEq / r).toFloat()
                val nz = (zEq / r).toFloat()

                verts.add(nx); verts.add(ny); verts.add(nz)
                verts.add(color[0]); verts.add(color[1]); verts.add(color[2]); verts.add(color[3])
            } catch (e: Exception) {
                Log.e(TAG, "Failed to compute position for ${comet.name}", e)
            }
        }

        cachedVertices = verts.toFloatArray()
        cachedCount = verts.size / 7
    }

    /**
     * Compute heliocentric ecliptic position of a comet at given JD.
     * Returns (x, y, z) in AU or null if computation fails.
     */
    private fun cometHeliocentric(comet: CometElements, jd: Double): Triple<Double, Double, Double>? {
        val periodDays = comet.periodYears * 365.25
        val a = comet.q / (1 - comet.e) // Semi-major axis

        // Mean anomaly from perihelion
        val dt = jd - comet.epochJd
        val n = 2 * PI / periodDays // Mean motion (rad/day)
        val M = (n * dt).mod(2 * PI)

        // Solve Kepler's equation with 50 iterations for high eccentricity
        var E = if (comet.e > 0.8) PI else M
        for (i in 0 until 50) {
            val dE = (M - E + comet.e * sin(E)) / (1 - comet.e * cos(E))
            E += dE
            if (abs(dE) < 1e-12) break
        }

        // True anomaly
        val nu = 2 * atan2(
            sqrt(1 + comet.e) * sin(E / 2),
            sqrt(1 - comet.e) * cos(E / 2)
        )

        // Distance from Sun
        val r = a * (1 - comet.e * cos(E))

        // Position in orbital plane
        val xOrb = r * cos(nu)
        val yOrb = r * sin(nu)

        // Rotate to ecliptic coordinates
        val omega = Math.toRadians(comet.omegaDeg)
        val bigOmega = Math.toRadians(comet.bigOmegaDeg)
        val inc = Math.toRadians(comet.iDeg)

        val cosO = cos(bigOmega)
        val sinO = sin(bigOmega)
        val cosW = cos(omega)
        val sinW = sin(omega)
        val cosI = cos(inc)
        val sinI = sin(inc)

        val x = (cosO * cosW - sinO * sinW * cosI) * xOrb +
                (-cosO * sinW - sinO * cosW * cosI) * yOrb
        val y = (sinO * cosW + cosO * sinW * cosI) * xOrb +
                (-sinO * sinW + cosO * cosW * cosI) * yOrb
        val z = (sinW * sinI) * xOrb + (cosW * sinI) * yOrb

        return Triple(x, y, z)
    }

    /**
     * Simplified Earth heliocentric ecliptic position.
     */
    private fun earthHeliocentric(t: Double): Triple<Double, Double, Double> {
        val L = Math.toRadians((100.46457166 + 36000.7698231 * t).mod(360.0))
        val e = 0.01671123 - 0.00004392 * t
        val omega = Math.toRadians((102.93768193 + 0.32327364 * t).mod(360.0))

        val M = L - omega
        var E = M
        for (i in 0 until 10) {
            val dE = (M - E + e * sin(E)) / (1 - e * cos(E))
            E += dE
            if (abs(dE) < 1e-9) break
        }

        val nu = 2 * atan2(sqrt(1 + e) * sin(E / 2), sqrt(1 - e) * cos(E / 2))
        val r = 1.00000261 * (1 - e * cos(E))

        val xOrb = r * cos(nu)
        val yOrb = r * sin(nu)

        // Earth's orbit defines the ecliptic, so i=0, Omega=0
        val cosW = cos(omega)
        val sinW = sin(omega)
        val x = cosW * xOrb - sinW * yOrb
        val y = sinW * xOrb + cosW * yOrb
        val z = 0.0

        return Triple(x, y, z)
    }

    companion object {
        private const val TAG = "CometLayer"
    }
}
