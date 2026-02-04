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

    /** Create translation matrix */
    fun translate(x: Float, y: Float, z: Float): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        x, y, z, 1f
    )

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

    /** Create rotation matrix around X axis */
    fun rotateX(angleDegrees: Float): FloatArray {
        val angleRadians = angleDegrees * (Math.PI.toFloat() / 180f)
        val c = cos(angleRadians)
        val s = sin(angleRadians)

        return floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, c, s, 0f,
            0f, -s, c, 0f,
            0f, 0f, 0f, 1f
        )
    }

    /** Create rotation matrix around Y axis */
    fun rotateY(angleDegrees: Float): FloatArray {
        val angleRadians = angleDegrees * (Math.PI.toFloat() / 180f)
        val c = cos(angleRadians)
        val s = sin(angleRadians)

        return floatArrayOf(
            c, 0f, -s, 0f,
            0f, 1f, 0f, 0f,
            s, 0f, c, 0f,
            0f, 0f, 0f, 1f
        )
    }

    /**
     * Create a look-at view matrix.
     * @param eyeX, eyeY, eyeZ Camera position
     * @param centerX, centerY, centerZ Point to look at
     * @param upX, upY, upZ Up vector
     */
    fun lookAt(
        eyeX: Float, eyeY: Float, eyeZ: Float,
        centerX: Float, centerY: Float, centerZ: Float,
        upX: Float, upY: Float, upZ: Float
    ): FloatArray {
        // Forward vector (from center to eye)
        var fx = eyeX - centerX
        var fy = eyeY - centerY
        var fz = eyeZ - centerZ
        val fLen = kotlin.math.sqrt(fx * fx + fy * fy + fz * fz)
        fx /= fLen
        fy /= fLen
        fz /= fLen

        // Right vector = up × forward
        var rx = upY * fz - upZ * fy
        var ry = upZ * fx - upX * fz
        var rz = upX * fy - upY * fx
        val rLen = kotlin.math.sqrt(rx * rx + ry * ry + rz * rz)
        rx /= rLen
        ry /= rLen
        rz /= rLen

        // True up vector = forward × right
        val ux = fy * rz - fz * ry
        val uy = fz * rx - fx * rz
        val uz = fx * ry - fy * rx

        return floatArrayOf(
            rx, ux, fx, 0f,
            ry, uy, fy, 0f,
            rz, uz, fz, 0f,
            -(rx * eyeX + ry * eyeY + rz * eyeZ),
            -(ux * eyeX + uy * eyeY + uz * eyeZ),
            -(fx * eyeX + fy * eyeY + fz * eyeZ),
            1f
        )
    }
}
