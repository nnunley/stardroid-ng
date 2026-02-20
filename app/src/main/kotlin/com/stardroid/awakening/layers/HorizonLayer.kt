package com.stardroid.awakening.layers

import com.stardroid.awakening.control.AstronomerModel
import com.stardroid.awakening.renderer.DrawBatch
import com.stardroid.awakening.renderer.Matrix
import com.stardroid.awakening.renderer.PrimitiveType
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates horizon line and cardinal direction markers.
 *
 * The horizon is computed based on the observer's location and
 * transforms with the phone orientation.
 */
class HorizonLayer {

    // Horizon line color (orange/brown for ground)
    private val horizonColor = floatArrayOf(0.6f, 0.4f, 0.2f, 0.8f)

    // Cardinal direction colors
    private val northColor = floatArrayOf(1.0f, 0.2f, 0.2f, 1.0f)  // Red for North
    private val southColor = floatArrayOf(0.2f, 0.6f, 1.0f, 1.0f)  // Blue for South
    private val eastColor = floatArrayOf(1.0f, 1.0f, 0.2f, 1.0f)   // Yellow for East
    private val westColor = floatArrayOf(0.2f, 1.0f, 0.2f, 1.0f)   // Green for West

    /**
     * Get horizon geometry as a DrawBatch.
     *
     * The horizon is drawn in the local horizontal coordinate system
     * (altitude-azimuth), which needs to be transformed to celestial
     * coordinates based on the observer's location and time.
     *
     * For simplicity, we draw the horizon as a circle at altitude 0
     * in local coordinates. The AstronomerModel handles the transformation.
     */
    fun getHorizonBatch(model: AstronomerModel?): DrawBatch {
        val vertices = mutableListOf<Float>()

        // Draw horizon circle (at altitude = 0)
        // In local horizontal coords: x = cos(az), y = sin(az), z = 0
        // We need to convert to celestial coords, but for now draw in local frame
        // The view matrix from AstronomerModel will handle the transformation

        // Horizon circle - 360 segments
        for (azStep in 0 until 360 step 2) {
            val az1Rad = Math.toRadians(azStep.toDouble())
            val az2Rad = Math.toRadians((azStep + 2).toDouble())

            // Horizon is at altitude 0, so z component is 0
            // We draw slightly below (negative z in local coords) to be visible
            val alt = -0.02f  // Slightly below horizon

            vertices.add(cos(az1Rad).toFloat())
            vertices.add(sin(az1Rad).toFloat())
            vertices.add(alt)
            vertices.addAll(horizonColor.toList())

            vertices.add(cos(az2Rad).toFloat())
            vertices.add(sin(az2Rad).toFloat())
            vertices.add(alt)
            vertices.addAll(horizonColor.toList())
        }

        // Cardinal direction markers (vertical lines at N/S/E/W)
        val markerHeight = 0.15f

        // North (azimuth = 0)
        addCardinalMarker(vertices, 0.0, northColor, markerHeight)

        // East (azimuth = 90)
        addCardinalMarker(vertices, 90.0, eastColor, markerHeight)

        // South (azimuth = 180)
        addCardinalMarker(vertices, 180.0, southColor, markerHeight)

        // West (azimuth = 270)
        addCardinalMarker(vertices, 270.0, westColor, markerHeight)

        val vertexArray = vertices.toFloatArray()
        val vertexCount = vertexArray.size / 7

        return DrawBatch(
            type = PrimitiveType.LINES,
            vertices = vertexArray,
            vertexCount = vertexCount,
            transform = Matrix.identity()
        )
    }

    private fun addCardinalMarker(
        vertices: MutableList<Float>,
        azimuthDeg: Double,
        color: FloatArray,
        height: Float
    ) {
        val azRad = Math.toRadians(azimuthDeg)
        val x = cos(azRad).toFloat()
        val y = sin(azRad).toFloat()

        // Bottom of marker (at horizon)
        vertices.add(x)
        vertices.add(y)
        vertices.add(-0.02f)
        vertices.addAll(color.toList())

        // Top of marker
        vertices.add(x)
        vertices.add(y)
        vertices.add(height)
        vertices.addAll(color.toList())
    }
}
