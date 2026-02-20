package com.stardroid.awakening.layers

import com.stardroid.awakening.renderer.DrawBatch
import com.stardroid.awakening.renderer.Matrix
import com.stardroid.awakening.renderer.PrimitiveType
import kotlin.math.cos
import kotlin.math.sin

/**
 * A single bright "Star of Bethlehem" point near Gemini.
 *
 * Hardcoded RA ~93 deg, Dec ~+20 deg. Gold-white color.
 */
class StarOfBethlehemLayer {

    private val color = floatArrayOf(1.0f, 0.95f, 0.7f, 1.0f)

    // RA = 93 deg, Dec = +20 deg
    private val raRad = Math.toRadians(93.0)
    private val decRad = Math.toRadians(20.0)
    private val x = (cos(decRad) * cos(raRad)).toFloat()
    private val y = (cos(decRad) * sin(raRad)).toFloat()
    private val z = sin(decRad).toFloat()

    private val vertices = floatArrayOf(
        x, y, z, color[0], color[1], color[2], color[3]
    )

    fun getBatch(): DrawBatch {
        return DrawBatch(
            type = PrimitiveType.POINTS,
            vertices = vertices,
            vertexCount = 1,
            transform = Matrix.identity()
        )
    }
}
