package com.stardroid.awakening.layers

import android.util.Log
import com.stardroid.awakening.control.AstronomerModel
import com.stardroid.awakening.renderer.DrawBatch
import com.stardroid.awakening.renderer.Matrix
import com.stardroid.awakening.renderer.PrimitiveType
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.*

/**
 * Displays the International Space Station position on the celestial sphere.
 *
 * Fetches lat/lon/alt from https://api.wheretheiss.at/v1/satellites/25544 every 10 seconds
 * on a background thread. Converts geodetic satellite position to a celestial direction
 * relative to the observer. Only shows when ISS is above the observer's horizon.
 */
class ISSLayer {

    private data class ISSPosition(
        val latDeg: Double,
        val lonDeg: Double,
        val altKm: Double,
        val timestamp: Long
    )

    private val color = floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f)
    private val cachedPosition = AtomicReference<ISSPosition?>(null)
    private var fetchThread: Thread? = null
    @Volatile private var running = false

    private val FETCH_INTERVAL_MS = 10_000L
    private val EARTH_RADIUS_KM = 6371.0

    fun start() {
        if (running) return
        running = true
        fetchThread = Thread {
            while (running) {
                try {
                    fetchPosition()
                } catch (e: Exception) {
                    Log.e(TAG, "ISS fetch failed", e)
                }
                try {
                    Thread.sleep(FETCH_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.apply {
            name = "ISSFetchThread"
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        fetchThread?.interrupt()
        fetchThread = null
    }

    /**
     * Get ISS position as a DrawBatch.
     *
     * @param model AstronomerModel for observer location and zenith
     * @return DrawBatch with 0 or 1 point
     */
    fun getBatch(model: AstronomerModel?): DrawBatch {
        val empty = DrawBatch(
            type = PrimitiveType.POINTS,
            vertices = floatArrayOf(),
            vertexCount = 0,
            transform = Matrix.identity()
        )

        val pos = cachedPosition.get() ?: return empty
        if (model == null) return empty

        val observerLat = model.location.latitude.toDouble()
        val observerLon = model.location.longitude.toDouble()

        // Convert ISS geodetic (lat/lon/alt) to ECEF
        val issEcef = geodeticToEcef(pos.latDeg, pos.lonDeg, pos.altKm)

        // Convert observer geodetic to ECEF (altitude ~0)
        val obsEcef = geodeticToEcef(observerLat, observerLon, 0.0)

        // Topocentric vector (ISS relative to observer in ECEF)
        val dx = issEcef.first - obsEcef.first
        val dy = issEcef.second - obsEcef.second
        val dz = issEcef.third - obsEcef.third

        // Check if above horizon: dot product with observer's "up" (outward from Earth center)
        val obsLen = sqrt(obsEcef.first * obsEcef.first + obsEcef.second * obsEcef.second + obsEcef.third * obsEcef.third)
        val upX = obsEcef.first / obsLen
        val upY = obsEcef.second / obsLen
        val upZ = obsEcef.third / obsLen

        val elevation = dx * upX + dy * upY + dz * upZ
        if (elevation <= 0) return empty // Below horizon

        // Convert ECEF topocentric to ECI using GMST rotation
        // GMST rotates around Z axis
        val gmst = computeGMST(pos.timestamp)
        val cosG = cos(gmst)
        val sinG = sin(gmst)

        // ECI direction (rotate ECEF by -GMST around Z)
        // Actually, ECEF to ECI: x_eci = x_ecef * cos(gmst) - y_ecef * sin(gmst)
        val eciX = dx * cosG - dy * sinG
        val eciY = dx * sinG + dy * cosG
        val eciZ = dz

        // Normalize to unit vector (celestial direction)
        val len = sqrt(eciX * eciX + eciY * eciY + eciZ * eciZ)
        if (len < 0.001) return empty

        val nx = (eciX / len).toFloat()
        val ny = (eciY / len).toFloat()
        val nz = (eciZ / len).toFloat()

        val vertices = floatArrayOf(
            nx, ny, nz, color[0], color[1], color[2], color[3]
        )

        return DrawBatch(
            type = PrimitiveType.POINTS,
            vertices = vertices,
            vertexCount = 1,
            transform = Matrix.identity()
        )
    }

    private fun fetchPosition() {
        val url = URL("https://api.wheretheiss.at/v1/satellites/25544")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000

        try {
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                val pos = ISSPosition(
                    latDeg = json.getDouble("latitude"),
                    lonDeg = json.getDouble("longitude"),
                    altKm = json.getDouble("altitude"),
                    timestamp = System.currentTimeMillis()
                )
                cachedPosition.set(pos)
                Log.d(TAG, "ISS at lat=${pos.latDeg}, lon=${pos.lonDeg}, alt=${pos.altKm}km")
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Convert geodetic coordinates to ECEF (Earth-Centered, Earth-Fixed).
     * Simplified spherical Earth model.
     */
    private fun geodeticToEcef(latDeg: Double, lonDeg: Double, altKm: Double): Triple<Double, Double, Double> {
        val latRad = Math.toRadians(latDeg)
        val lonRad = Math.toRadians(lonDeg)
        val r = EARTH_RADIUS_KM + altKm

        val x = r * cos(latRad) * cos(lonRad)
        val y = r * cos(latRad) * sin(lonRad)
        val z = r * sin(latRad)

        return Triple(x, y, z)
    }

    /**
     * Compute Greenwich Mean Sidereal Time in radians.
     */
    private fun computeGMST(timeMillis: Long): Double {
        val jd = 2440587.5 + timeMillis / 86400000.0
        val t = (jd - 2451545.0) / 36525.0
        val gmstDeg = (280.46061837 + 360.98564736629 * (jd - 2451545.0) +
                0.000387933 * t * t - t * t * t / 38710000.0).mod(360.0)
        return Math.toRadians(gmstDeg)
    }

    companion object {
        private const val TAG = "ISSLayer"
    }
}
