package com.stardroid.awakening.math

import kotlin.math.cos
import kotlin.math.sin

/**
 * 3x3 matrix for coordinate transformations.
 * Column-major storage for consistency with rendering matrices.
 */
class Matrix3x3 private constructor(private val m: FloatArray) {

    init {
        require(m.size == 9) { "Matrix must have 9 elements" }
    }

    /**
     * Construct from three column vectors.
     */
    constructor(col0: Vector3, col1: Vector3, col2: Vector3) : this(
        floatArrayOf(
            col0.x, col0.y, col0.z,
            col1.x, col1.y, col1.z,
            col2.x, col2.y, col2.z
        )
    )

    /**
     * Construct from three vectors as either columns or rows.
     */
    constructor(v0: Vector3, v1: Vector3, v2: Vector3, asColumns: Boolean) : this(
        if (asColumns) {
            floatArrayOf(
                v0.x, v0.y, v0.z,
                v1.x, v1.y, v1.z,
                v2.x, v2.y, v2.z
            )
        } else {
            // Rows - transpose
            floatArrayOf(
                v0.x, v1.x, v2.x,
                v0.y, v1.y, v2.y,
                v0.z, v1.z, v2.z
            )
        }
    )

    operator fun get(row: Int, col: Int): Float = m[col * 3 + row]

    operator fun times(v: Vector3): Vector3 {
        return Vector3(
            m[0] * v.x + m[3] * v.y + m[6] * v.z,
            m[1] * v.x + m[4] * v.y + m[7] * v.z,
            m[2] * v.x + m[5] * v.y + m[8] * v.z
        )
    }

    operator fun times(other: Matrix3x3): Matrix3x3 {
        val result = FloatArray(9)
        for (col in 0..2) {
            for (row in 0..2) {
                var sum = 0f
                for (k in 0..2) {
                    sum += this[row, k] * other[k, col]
                }
                result[col * 3 + row] = sum
            }
        }
        return Matrix3x3(result)
    }

    fun transpose(): Matrix3x3 {
        return Matrix3x3(
            floatArrayOf(
                m[0], m[3], m[6],
                m[1], m[4], m[7],
                m[2], m[5], m[8]
            )
        )
    }

    companion object {
        val identity = Matrix3x3(
            floatArrayOf(
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f
            )
        )

        /**
         * Create rotation matrix around arbitrary axis.
         * @param axis Unit vector to rotate around
         * @param angleDegrees Rotation angle in degrees
         */
        fun rotateAroundAxis(axis: Vector3, angleDegrees: Float): Matrix3x3 {
            val radians = angleDegrees * (Math.PI.toFloat() / 180f)
            val c = cos(radians)
            val s = sin(radians)
            val t = 1 - c
            val x = axis.x
            val y = axis.y
            val z = axis.z

            return Matrix3x3(
                floatArrayOf(
                    t * x * x + c,     t * x * y + s * z, t * x * z - s * y,
                    t * x * y - s * z, t * y * y + c,     t * y * z + s * x,
                    t * x * z + s * y, t * y * z - s * x, t * z * z + c
                )
            )
        }
    }
}
