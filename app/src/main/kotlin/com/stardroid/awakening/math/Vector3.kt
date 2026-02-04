package com.stardroid.awakening.math

import kotlin.math.sqrt

/**
 * 3D vector for celestial coordinate calculations.
 * Mutable for performance in sensor updates.
 */
data class Vector3(
    @JvmField var x: Float,
    @JvmField var y: Float,
    @JvmField var z: Float
) {
    val length2: Float
        get() = x * x + y * y + z * z

    val length: Float
        get() = sqrt(length2)

    constructor(xyz: FloatArray) : this(xyz[0], xyz[1], xyz[2]) {
        require(xyz.size == 3) { "Array must have 3 elements" }
    }

    fun assign(other: Vector3) {
        x = other.x
        y = other.y
        z = other.z
    }

    fun assign(x: Float, y: Float, z: Float) {
        this.x = x
        this.y = y
        this.z = z
    }

    fun normalize() {
        val len = length
        if (len > 0.000001f) {
            x /= len
            y /= len
            z /= len
        }
    }

    fun normalizedCopy(): Vector3 {
        return if (length < 0.000001f) zero() else this / length
    }

    infix fun dot(other: Vector3): Float {
        return x * other.x + y * other.y + z * other.z
    }

    /** Cross product */
    infix fun cross(other: Vector3): Vector3 {
        return Vector3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        )
    }

    operator fun plus(other: Vector3): Vector3 {
        return Vector3(x + other.x, y + other.y, z + other.z)
    }

    operator fun minus(other: Vector3): Vector3 {
        return Vector3(x - other.x, y - other.y, z - other.z)
    }

    operator fun times(scalar: Float): Vector3 {
        return Vector3(x * scalar, y * scalar, z * scalar)
    }

    operator fun div(scalar: Float): Vector3 {
        return Vector3(x / scalar, y / scalar, z / scalar)
    }

    operator fun unaryMinus(): Vector3 {
        return Vector3(-x, -y, -z)
    }

    operator fun timesAssign(scalar: Float) {
        x *= scalar
        y *= scalar
        z *= scalar
    }

    /** Project this vector onto a unit vector */
    fun projectOnto(unitVector: Vector3): Vector3 {
        return unitVector * (this dot unitVector)
    }

    companion object {
        fun zero() = Vector3(0f, 0f, 0f)
        fun unitX() = Vector3(1f, 0f, 0f)
        fun unitY() = Vector3(0f, 1f, 0f)
        fun unitZ() = Vector3(0f, 0f, 1f)
    }
}
