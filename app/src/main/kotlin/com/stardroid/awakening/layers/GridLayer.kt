package com.stardroid.awakening.layers

import com.stardroid.awakening.renderer.DrawBatch
import com.stardroid.awakening.renderer.Matrix
import com.stardroid.awakening.renderer.PrimitiveType
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates RA/Dec coordinate grid lines for the celestial sphere.
 *
 * Grid lines are drawn at 15° intervals (1 hour RA, 15° Dec).
 */
class GridLayer {

    private var gridVertices: FloatArray = floatArrayOf()
    private var vertexCount: Int = 0
    private var isGenerated = false

    // Grid line color (subtle blue-gray)
    private val gridColor = floatArrayOf(0.3f, 0.4f, 0.5f, 0.5f)

    /**
     * Generate grid geometry. Call once during initialization.
     */
    fun generate() {
        if (isGenerated) return

        val vertices = mutableListOf<Float>()

        // RA lines (meridians) - every 15° (1 hour)
        for (raHour in 0 until 24) {
            val raDeg = raHour * 15.0
            val raRad = Math.toRadians(raDeg)

            // Draw line from south pole to north pole
            for (decStep in -90 until 90 step 5) {
                val dec1Rad = Math.toRadians(decStep.toDouble())
                val dec2Rad = Math.toRadians((decStep + 5).toDouble())

                // First point
                vertices.add((cos(dec1Rad) * cos(raRad)).toFloat())
                vertices.add((cos(dec1Rad) * sin(raRad)).toFloat())
                vertices.add(sin(dec1Rad).toFloat())
                vertices.addAll(gridColor.toList())

                // Second point
                vertices.add((cos(dec2Rad) * cos(raRad)).toFloat())
                vertices.add((cos(dec2Rad) * sin(raRad)).toFloat())
                vertices.add(sin(dec2Rad).toFloat())
                vertices.addAll(gridColor.toList())
            }
        }

        // Dec lines (parallels) - every 15° from -75° to +75°
        for (decDeg in -75..75 step 15) {
            if (decDeg == 0) continue // Skip equator, we'll draw it specially

            val decRad = Math.toRadians(decDeg.toDouble())

            // Draw circle at this declination
            for (raStep in 0 until 360 step 5) {
                val ra1Rad = Math.toRadians(raStep.toDouble())
                val ra2Rad = Math.toRadians((raStep + 5).toDouble())

                // First point
                vertices.add((cos(decRad) * cos(ra1Rad)).toFloat())
                vertices.add((cos(decRad) * sin(ra1Rad)).toFloat())
                vertices.add(sin(decRad).toFloat())
                vertices.addAll(gridColor.toList())

                // Second point
                vertices.add((cos(decRad) * cos(ra2Rad)).toFloat())
                vertices.add((cos(decRad) * sin(ra2Rad)).toFloat())
                vertices.add(sin(decRad).toFloat())
                vertices.addAll(gridColor.toList())
            }
        }

        // Celestial equator (Dec = 0) - brighter
        val equatorColor = floatArrayOf(0.4f, 0.5f, 0.6f, 0.7f)
        val decRad = 0.0
        for (raStep in 0 until 360 step 5) {
            val ra1Rad = Math.toRadians(raStep.toDouble())
            val ra2Rad = Math.toRadians((raStep + 5).toDouble())

            vertices.add((cos(decRad) * cos(ra1Rad)).toFloat())
            vertices.add((cos(decRad) * sin(ra1Rad)).toFloat())
            vertices.add(sin(decRad).toFloat())
            vertices.addAll(equatorColor.toList())

            vertices.add((cos(decRad) * cos(ra2Rad)).toFloat())
            vertices.add((cos(decRad) * sin(ra2Rad)).toFloat())
            vertices.add(sin(decRad).toFloat())
            vertices.addAll(equatorColor.toList())
        }

        gridVertices = vertices.toFloatArray()
        vertexCount = vertices.size / 7  // 7 floats per vertex (xyz + rgba)
        isGenerated = true
    }

    /**
     * Get grid as a DrawBatch for rendering.
     */
    fun getGridBatch(): DrawBatch {
        if (!isGenerated) {
            generate()
        }

        return DrawBatch(
            type = PrimitiveType.LINES,
            vertices = gridVertices,
            vertexCount = vertexCount,
            transform = Matrix.identity()
        )
    }
}
