package com.stardroid.awakening.renderer

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Matrix utilities for 4x4 transforms.
 * All matrices are column-major for Vulkan/GLSL compatibility.
 */
object Matrix {
    /** Create 4x4 identity matrix */
    fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )

    /** Create perspective projection matrix */
    fun perspective(
        fovYDegrees: Float,
        aspect: Float,
        near: Float,
        far: Float
    ): FloatArray {
        val fovYRadians = fovYDegrees * (Math.PI.toFloat() / 180f)
        val f = 1f / tan(fovYRadians / 2f)
        val nf = 1f / (near - far)

        return floatArrayOf(
            f / aspect, 0f, 0f, 0f,
            0f, f, 0f, 0f,
            0f, 0f, (far + near) * nf, -1f,
            0f, 0f, 2f * far * near * nf, 0f
        )
    }

    /** Create rotation matrix around Z axis */
    fun rotateZ(angleDegrees: Float): FloatArray {
        val angleRadians = angleDegrees * (Math.PI.toFloat() / 180f)
        val c = cos(angleRadians)
        val s = sin(angleRadians)

        return floatArrayOf(
            c, s, 0f, 0f,
            -s, c, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
    }

    /** Multiply two 4x4 matrices: result = a * b (column-major) */
    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val result = FloatArray(16)
        for (col in 0..3) {
            for (row in 0..3) {
                var sum = 0f
                for (k in 0..3) {
                    sum += a[row + k * 4] * b[k + col * 4]
                }
                result[row + col * 4] = sum
            }
        }
        return result
    }
}
