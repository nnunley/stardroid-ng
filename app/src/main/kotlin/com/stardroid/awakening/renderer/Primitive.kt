package com.stardroid.awakening.renderer

/** Types of graphical primitives */
enum class PrimitiveType {
    POINTS,      // Stars (variable size dots)
    LINES,       // Constellation lines, grids
    TRIANGLES,   // Filled shapes
    TEXT,        // Labels (future)
    IMAGE        // Textures (future)
}

/**
 * Vertex with position and color.
 *
 * Layout: x, y, z, r, g, b, a (7 floats per vertex)
 */
data class Vertex(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val r: Float = 1f,
    val g: Float = 1f,
    val b: Float = 1f,
    val a: Float = 1f
) {
    companion object {
        const val COMPONENTS = 7
        const val STRIDE_BYTES = COMPONENTS * 4
    }
}

/**
 * A batch of primitives to draw.
 *
 * Vertices are packed as [x,y,z,r,g,b,a, x,y,z,r,g,b,a, ...]
 */
data class DrawBatch(
    val type: PrimitiveType,
    val vertices: FloatArray,
    val vertexCount: Int,
    val transform: FloatArray? = null  // Optional 4x4 model matrix
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DrawBatch) return false
        return type == other.type &&
               vertices.contentEquals(other.vertices) &&
               vertexCount == other.vertexCount &&
               transform.contentEquals(other.transform)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + vertices.contentHashCode()
        result = 31 * result + vertexCount
        result = 31 * result + (transform?.contentHashCode() ?: 0)
        return result
    }
}
