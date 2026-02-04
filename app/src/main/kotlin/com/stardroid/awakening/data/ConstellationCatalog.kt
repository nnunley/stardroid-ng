package com.stardroid.awakening.data

import android.content.res.AssetManager
import android.util.Log
import com.google.android.stardroid.source.proto.SourceProto
import com.stardroid.awakening.renderer.DrawBatch
import com.stardroid.awakening.renderer.Matrix
import com.stardroid.awakening.renderer.PrimitiveType
import kotlin.math.cos
import kotlin.math.sin

/**
 * Loads and provides constellation line data from the binary protobuf catalog.
 *
 * Constellation lines are stored as line strips with RA/Dec coordinates.
 * They are converted to 3D xyz positions on the celestial sphere (unit vectors)
 * and output as line segments (pairs of vertices) for rendering.
 */
class ConstellationCatalog(private val assetManager: AssetManager) {

    private var lineSegments: List<LineSegment> = emptyList()
    private var isLoaded = false

    data class LineSegment(
        val x1: Float, val y1: Float, val z1: Float,
        val x2: Float, val y2: Float, val z2: Float,
        val r: Float, val g: Float, val b: Float, val a: Float
    )

    /**
     * Load constellation lines from binary protobuf file.
     * Call this before accessing constellation data.
     */
    fun load() {
        if (isLoaded) return

        Log.d(TAG, "Loading constellation catalog...")
        val startTime = System.currentTimeMillis()

        try {
            assetManager.open("constellations.binary").use { stream ->
                val sources = SourceProto.AstronomicalSourcesProto.parseFrom(stream)

                val loadedSegments = mutableListOf<LineSegment>()

                for (i in 0 until sources.sourceCount) {
                    val source = sources.getSource(i)

                    // Each source may have multiple line elements
                    for (j in 0 until source.lineCount) {
                        val line = source.getLine(j)

                        // Extract color (ARGB packed)
                        val color = line.color.toInt()
                        val a = ((color shr 24) and 0xFF) / 255f
                        val r = ((color shr 16) and 0xFF) / 255f
                        val g = ((color shr 8) and 0xFF) / 255f
                        val b = (color and 0xFF) / 255f

                        // Convert line strip vertices to xyz unit vectors
                        val vertices = mutableListOf<Triple<Float, Float, Float>>()
                        for (k in 0 until line.vertexCount) {
                            val vertex = line.getVertex(k)
                            val raRad = Math.toRadians(vertex.rightAscension.toDouble())
                            val decRad = Math.toRadians(vertex.declination.toDouble())

                            val x = (cos(decRad) * cos(raRad)).toFloat()
                            val y = (cos(decRad) * sin(raRad)).toFloat()
                            val z = sin(decRad).toFloat()

                            vertices.add(Triple(x, y, z))
                        }

                        // Convert line strip to line segments (pairs)
                        // A line strip with N vertices becomes N-1 segments
                        for (k in 0 until vertices.size - 1) {
                            val v1 = vertices[k]
                            val v2 = vertices[k + 1]
                            loadedSegments.add(
                                LineSegment(
                                    v1.first, v1.second, v1.third,
                                    v2.first, v2.second, v2.third,
                                    r, g, b, a
                                )
                            )
                        }
                    }
                }

                lineSegments = loadedSegments
                isLoaded = true

                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "Loaded ${lineSegments.size} line segments in ${elapsed}ms")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load constellation catalog", e)
        }
    }

    /**
     * Get all constellation lines as a DrawBatch for rendering.
     * Lines are rendered as pairs of vertices on the celestial sphere.
     */
    fun getConstellationBatch(): DrawBatch {
        if (!isLoaded) {
            Log.w(TAG, "Constellation catalog not loaded, returning empty batch")
            return DrawBatch(
                type = PrimitiveType.LINES,
                vertices = floatArrayOf(),
                vertexCount = 0,
                transform = Matrix.identity()
            )
        }

        // Build vertex array: 2 vertices per segment, 7 floats per vertex (xyz + rgba)
        // Each segment has 2 vertices = 14 floats
        val vertices = FloatArray(lineSegments.size * 14)
        var offset = 0

        for (segment in lineSegments) {
            // First vertex of the segment
            vertices[offset++] = segment.x1
            vertices[offset++] = segment.y1
            vertices[offset++] = segment.z1
            vertices[offset++] = segment.r
            vertices[offset++] = segment.g
            vertices[offset++] = segment.b
            vertices[offset++] = segment.a

            // Second vertex of the segment
            vertices[offset++] = segment.x2
            vertices[offset++] = segment.y2
            vertices[offset++] = segment.z2
            vertices[offset++] = segment.r
            vertices[offset++] = segment.g
            vertices[offset++] = segment.b
            vertices[offset++] = segment.a
        }

        return DrawBatch(
            type = PrimitiveType.LINES,
            vertices = vertices,
            vertexCount = lineSegments.size * 2,  // 2 vertices per segment
            transform = Matrix.identity()
        )
    }

    /**
     * Get the number of loaded line segments.
     */
    val lineCount: Int
        get() = lineSegments.size

    companion object {
        private const val TAG = "ConstellationCatalog"
    }
}
