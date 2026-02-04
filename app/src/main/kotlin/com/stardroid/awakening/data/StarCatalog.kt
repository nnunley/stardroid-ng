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
 * Loads and provides star data from the binary protobuf catalog.
 *
 * Stars are stored with RA/Dec coordinates and converted to 3D xyz
 * positions on the celestial sphere (unit vectors).
 */
class StarCatalog(private val assetManager: AssetManager) {

    private var stars: List<Star> = emptyList()
    private var isLoaded = false

    data class Star(
        val x: Float,
        val y: Float,
        val z: Float,
        val r: Float,
        val g: Float,
        val b: Float,
        val a: Float,
        val size: Int,
        val name: String?
    )

    /**
     * Load stars from binary protobuf file.
     * Call this before accessing star data.
     */
    fun load() {
        if (isLoaded) return

        Log.d(TAG, "Loading star catalog...")
        val startTime = System.currentTimeMillis()

        try {
            assetManager.open("stars.binary").use { stream ->
                val sources = SourceProto.AstronomicalSourcesProto.parseFrom(stream)

                val loadedStars = mutableListOf<Star>()

                for (i in 0 until sources.sourceCount) {
                    val source = sources.getSource(i)

                    // Each source may have multiple points (usually 1 for stars)
                    for (j in 0 until source.pointCount) {
                        val point = source.getPoint(j)
                        val location = point.location

                        // Convert RA/Dec to xyz unit vector
                        val raRad = Math.toRadians(location.rightAscension.toDouble())
                        val decRad = Math.toRadians(location.declination.toDouble())

                        val x = (cos(decRad) * cos(raRad)).toFloat()
                        val y = (cos(decRad) * sin(raRad)).toFloat()
                        val z = sin(decRad).toFloat()

                        // Extract color (ARGB packed)
                        val color = point.color.toInt()
                        val a = ((color shr 24) and 0xFF) / 255f
                        val r = ((color shr 16) and 0xFF) / 255f
                        val g = ((color shr 8) and 0xFF) / 255f
                        val b = (color and 0xFF) / 255f

                        // Get name if available
                        val name = if (source.nameStrIdsCount > 0) {
                            source.getNameStrIds(0)
                        } else null

                        loadedStars.add(Star(x, y, z, r, g, b, a, point.size, name))
                    }
                }

                stars = loadedStars
                isLoaded = true

                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "Loaded ${stars.size} stars in ${elapsed}ms")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load star catalog", e)
        }
    }

    /**
     * Get all stars as a DrawBatch for rendering.
     * Stars are rendered as points on the celestial sphere.
     */
    fun getStarBatch(): DrawBatch {
        if (!isLoaded) {
            Log.w(TAG, "Star catalog not loaded, returning empty batch")
            return DrawBatch(
                type = PrimitiveType.POINTS,
                vertices = floatArrayOf(),
                vertexCount = 0,
                transform = Matrix.identity()
            )
        }

        // Build vertex array: xyz + rgba = 7 floats per star
        val vertices = FloatArray(stars.size * 7)
        var offset = 0

        for (star in stars) {
            vertices[offset++] = star.x
            vertices[offset++] = star.y
            vertices[offset++] = star.z
            vertices[offset++] = star.r
            vertices[offset++] = star.g
            vertices[offset++] = star.b
            vertices[offset++] = star.a
        }

        return DrawBatch(
            type = PrimitiveType.POINTS,
            vertices = vertices,
            vertexCount = stars.size,
            transform = Matrix.identity()
        )
    }

    /**
     * Get the number of loaded stars.
     */
    val starCount: Int
        get() = stars.size

    companion object {
        private const val TAG = "StarCatalog"
    }
}
