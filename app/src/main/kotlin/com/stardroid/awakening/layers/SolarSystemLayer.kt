package com.stardroid.awakening.layers

import android.util.Log
import com.stardroid.awakening.ephemeris.*
import com.stardroid.awakening.renderer.DrawBatch
import com.stardroid.awakening.renderer.Matrix
import com.stardroid.awakening.renderer.PrimitiveType

/**
 * Layer showing Sun, Moon, and planets.
 *
 * Positions are calculated using ephemeris algorithms and updated per frame.
 */
class SolarSystemLayer {

    // Colors for solar system objects
    private val sunColor = floatArrayOf(1.0f, 0.95f, 0.6f, 1.0f)      // Bright yellow
    private val moonColor = floatArrayOf(0.9f, 0.9f, 0.85f, 1.0f)     // Pale white
    private val mercuryColor = floatArrayOf(0.7f, 0.7f, 0.7f, 1.0f)   // Gray
    private val venusColor = floatArrayOf(1.0f, 0.9f, 0.7f, 1.0f)     // Pale yellow
    private val marsColor = floatArrayOf(1.0f, 0.5f, 0.3f, 1.0f)      // Reddish orange
    private val jupiterColor = floatArrayOf(0.9f, 0.8f, 0.6f, 1.0f)   // Tan
    private val saturnColor = floatArrayOf(0.9f, 0.85f, 0.6f, 1.0f)   // Pale gold
    private val uranusColor = floatArrayOf(0.6f, 0.9f, 0.9f, 1.0f)    // Cyan
    private val neptuneColor = floatArrayOf(0.4f, 0.5f, 1.0f, 1.0f)   // Blue

    // Cache positions to avoid recalculating every frame
    private var lastUpdateTime = 0L
    private var cachedPositions: List<SolarSystemObject> = emptyList()
    private val UPDATE_INTERVAL_MS = 60_000L  // Update positions every minute

    data class SolarSystemObject(
        val name: String,
        val x: Float,
        val y: Float,
        val z: Float,
        val r: Float,
        val g: Float,
        val b: Float,
        val a: Float,
        val apparentSize: Float  // For future use with scaled rendering
    )

    /**
     * Get current solar system objects as a DrawBatch.
     */
    fun getSolarSystemBatch(): DrawBatch {
        val now = System.currentTimeMillis()

        // Update positions if cache is stale
        if (now - lastUpdateTime > UPDATE_INTERVAL_MS || cachedPositions.isEmpty()) {
            updatePositions()
            lastUpdateTime = now
        }

        if (cachedPositions.isEmpty()) {
            return DrawBatch(
                type = PrimitiveType.POINTS,
                vertices = floatArrayOf(),
                vertexCount = 0,
                transform = Matrix.identity()
            )
        }

        // Build vertex array
        val vertices = FloatArray(cachedPositions.size * 7)
        var offset = 0

        for (obj in cachedPositions) {
            vertices[offset++] = obj.x
            vertices[offset++] = obj.y
            vertices[offset++] = obj.z
            vertices[offset++] = obj.r
            vertices[offset++] = obj.g
            vertices[offset++] = obj.b
            vertices[offset++] = obj.a
        }

        return DrawBatch(
            type = PrimitiveType.POINTS,
            vertices = vertices,
            vertexCount = cachedPositions.size,
            transform = Matrix.identity()
        )
    }

    private fun updatePositions() {
        try {
            val jd = JulianDate.now()
            val objects = mutableListOf<SolarSystemObject>()

            // Sun
            val sunPos = SunCalculator.position(jd)
            val (sunX, sunY, sunZ) = sunPos.toUnitVector()
            objects.add(SolarSystemObject(
                name = "Sun",
                x = sunX, y = sunY, z = sunZ,
                r = sunColor[0], g = sunColor[1], b = sunColor[2], a = sunColor[3],
                apparentSize = 0.5f  // Sun is visually large
            ))

            // Moon
            val moonPos = MoonCalculator.position(jd)
            val (moonX, moonY, moonZ) = moonPos.toUnitVector()
            objects.add(SolarSystemObject(
                name = "Moon",
                x = moonX, y = moonY, z = moonZ,
                r = moonColor[0], g = moonColor[1], b = moonColor[2], a = moonColor[3],
                apparentSize = 0.5f  // Moon is visually large
            ))

            // Planets
            addPlanet(objects, Planet.MERCURY, jd, mercuryColor)
            addPlanet(objects, Planet.VENUS, jd, venusColor)
            addPlanet(objects, Planet.MARS, jd, marsColor)
            addPlanet(objects, Planet.JUPITER, jd, jupiterColor)
            addPlanet(objects, Planet.SATURN, jd, saturnColor)
            addPlanet(objects, Planet.URANUS, jd, uranusColor)
            addPlanet(objects, Planet.NEPTUNE, jd, neptuneColor)

            cachedPositions = objects

            Log.i(TAG, "Updated solar system positions: ${objects.size} objects")
            objects.take(3).forEach { obj ->
                Log.i(TAG, "  ${obj.name}: RA=${String.format("%.1f", Math.toDegrees(kotlin.math.atan2(obj.y.toDouble(), obj.x.toDouble())))}°")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate solar system positions", e)
        }
    }

    private fun addPlanet(
        objects: MutableList<SolarSystemObject>,
        planet: Planet,
        jd: Double,
        color: FloatArray
    ) {
        try {
            val pos = PlanetCalculator.position(planet, jd)
            val (x, y, z) = pos.toUnitVector()

            // Apparent size based on distance (smaller = fainter point)
            val apparentSize = when {
                pos.distanceAu < 1.0 -> 0.4f   // Venus, Mercury when close
                pos.distanceAu < 2.0 -> 0.3f   // Mars
                pos.distanceAu < 6.0 -> 0.25f  // Jupiter
                else -> 0.2f                    // Saturn, Uranus, Neptune
            }

            objects.add(SolarSystemObject(
                name = planet.displayName,
                x = x, y = y, z = z,
                r = color[0], g = color[1], b = color[2], a = color[3],
                apparentSize = apparentSize
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate position for $planet", e)
        }
    }

    /**
     * Force position update (e.g., when time changes significantly).
     */
    fun invalidateCache() {
        lastUpdateTime = 0L
    }

    companion object {
        private const val TAG = "SolarSystemLayer"
    }
}
