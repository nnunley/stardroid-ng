package com.stardroid.awakening.data

import android.content.res.AssetManager
import android.util.Log
import com.stardroid.awakening.renderer.DrawBatch
import com.stardroid.awakening.renderer.Matrix
import com.stardroid.awakening.renderer.PrimitiveType
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * Loads and provides constellation line data from the FlatBuffer binary catalog.
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

    fun load() {
        if (isLoaded) return

        Log.d(TAG, "Loading constellation catalog...")
        val startTime = System.currentTimeMillis()

        try {
            assetManager.open("constellations.bin").use { stream ->
                val bytes = stream.readBytes()
                val buf = ByteBuffer.wrap(bytes)
                val sources = AstronomicalSources.getRootAsAstronomicalSources(buf)

                val loadedSegments = mutableListOf<LineSegment>()

                for (i in 0 until sources.sourcesLength) {
                    val source = sources.sources(i)!!

                    for (j in 0 until source.linesLength) {
                        val line = source.lines(j)!!

                        // Extract color (ARGB packed)
                        val color = line.color.toInt()
                        val a = ((color shr 24) and 0xFF) / 255f
                        val r = ((color shr 16) and 0xFF) / 255f
                        val g = ((color shr 8) and 0xFF) / 255f
                        val b = (color and 0xFF) / 255f

                        // Convert line strip vertices to xyz unit vectors
                        val vertices = mutableListOf<Triple<Float, Float, Float>>()
                        for (k in 0 until line.verticesLength) {
                            val vertex = line.vertices(k)!!
                            val raRad = Math.toRadians(vertex.rightAscension.toDouble())
                            val decRad = Math.toRadians(vertex.declination.toDouble())

                            val x = (cos(decRad) * cos(raRad)).toFloat()
                            val y = (cos(decRad) * sin(raRad)).toFloat()
                            val z = sin(decRad).toFloat()

                            vertices.add(Triple(x, y, z))
                        }

                        // Convert line strip to line segments (pairs)
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
        val vertices = FloatArray(lineSegments.size * 14)
        var offset = 0

        for (segment in lineSegments) {
            vertices[offset++] = segment.x1
            vertices[offset++] = segment.y1
            vertices[offset++] = segment.z1
            vertices[offset++] = segment.r
            vertices[offset++] = segment.g
            vertices[offset++] = segment.b
            vertices[offset++] = segment.a

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
            vertexCount = lineSegments.size * 2,
            transform = Matrix.identity()
        )
    }

    val lineCount: Int
        get() = lineSegments.size

    companion object {
        private const val TAG = "ConstellationCatalog"
    }
}
