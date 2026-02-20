package com.stardroid.awakening.layers

import com.stardroid.awakening.renderer.DrawBatch
import com.stardroid.awakening.renderer.Matrix
import com.stardroid.awakening.renderer.PrimitiveType
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the ecliptic plane as a great circle on the celestial sphere.
 *
 * The ecliptic is inclined at ~23.44 deg to the celestial equator.
 * Ecliptic longitudes are converted to equatorial RA/Dec using the obliquity transform.
 */
class EclipticLayer {

    private var vertices: FloatArray = floatArrayOf()
    private var vertexCount: Int = 0
    private var isGenerated = false

    private val color = floatArrayOf(0.8f, 0.7f, 0.3f, 0.6f)

    fun getBatch(): DrawBatch {
        if (!isGenerated) {
            generate()
        }
        return DrawBatch(
            type = PrimitiveType.LINES,
            vertices = vertices,
            vertexCount = vertexCount,
            transform = Matrix.identity()
        )
    }

    private fun generate() {
        val obliquity = Math.toRadians(23.44)
        val cosEps = cos(obliquity)
        val sinEps = sin(obliquity)

        val verts = mutableListOf<Float>()
        val step = 2 // degrees

        for (lon in 0 until 360 step step) {
            val lon1 = Math.toRadians(lon.toDouble())
            val lon2 = Math.toRadians((lon + step).toDouble())

            // Ecliptic to equatorial: latitude = 0 on the ecliptic
            // x_eq = cos(lon), y_eq = cos(eps)*sin(lon), z_eq = sin(eps)*sin(lon)
            val x1 = cos(lon1).toFloat()
            val y1 = (cosEps * sin(lon1)).toFloat()
            val z1 = (sinEps * sin(lon1)).toFloat()

            val x2 = cos(lon2).toFloat()
            val y2 = (cosEps * sin(lon2)).toFloat()
            val z2 = (sinEps * sin(lon2)).toFloat()

            verts.add(x1); verts.add(y1); verts.add(z1)
            verts.addAll(color.toList())

            verts.add(x2); verts.add(y2); verts.add(z2)
            verts.addAll(color.toList())
        }

        vertices = verts.toFloatArray()
        vertexCount = verts.size / 7
        isGenerated = true
    }
}
