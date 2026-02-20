package com.stardroid.awakening.data

import android.content.res.AssetManager
import android.util.Log
import com.google.android.stardroid.source.proto.SourceProto
import com.stardroid.awakening.renderer.DrawBatch
import com.stardroid.awakening.renderer.Matrix
import com.stardroid.awakening.renderer.PrimitiveType
import com.stardroid.awakening.spatial.SpatialStarIndex
import kotlin.math.cos
import kotlin.math.sin

/**
 * Loads and provides star data from the binary protobuf catalog.
 *
 * Stars are stored with RA/Dec coordinates and converted to 3D xyz
 * positions on the celestial sphere (unit vectors).
 *
 * Uses HEALPix spatial indexing for efficient frustum culling.
 */
class StarCatalog(private val assetManager: AssetManager) {

    private var stars: List<Star> = emptyList()
    private var isLoaded = false
    private val spatialIndex = SpatialStarIndex()

    data class Star(
        val x: Float,
        val y: Float,
        val z: Float,
        val r: Float,
        val g: Float,
        val b: Float,
        val a: Float,
        val raDeg: Double,
        val decDeg: Double,
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

                        // Store RA/Dec for spatial indexing
                        val raDeg = location.rightAscension.toDouble()
                        val decDeg = location.declination.toDouble()

                        // Convert RA/Dec to xyz unit vector
                        val raRad = Math.toRadians(raDeg)
                        val decRad = Math.toRadians(decDeg)

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

                        loadedStars.add(Star(x, y, z, r, g, b, a, raDeg, decDeg, point.size, name))
                    }
                }

                stars = loadedStars
                isLoaded = true

                // Build spatial index for frustum culling
                buildSpatialIndex()

                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "Loaded ${stars.size} stars in ${elapsed}ms")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load star catalog", e)
        }
    }

    /**
     * Build the HEALPix spatial index for efficient frustum culling.
     */
    private fun buildSpatialIndex() {
        val indexStars = stars.map { star ->
            SpatialStarIndex.Star(
                x = star.x,
                y = star.y,
                z = star.z,
                r = star.r,
                g = star.g,
                b = star.b,
                a = star.a,
                raDeg = star.raDeg,
                decDeg = star.decDeg,
                name = star.name
            )
        }
        spatialIndex.buildIndex(indexStars)

        // Log stats
        val stats = spatialIndex.getStats()
        Log.d(TAG, "Spatial index: nside=${stats.nside}, ${stats.occupiedPixels}/${stats.totalPixels} pixels, " +
                "~${String.format("%.1f", stats.avgStarsPerPixel)} stars/pixel, resolution=${String.format("%.2f", stats.resolutionDeg)}°")
    }

    /**
     * Get all stars as a DrawBatch for rendering (no culling).
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

        return spatialIndex.getAllStarsBatch()
    }

    /**
     * Get visible stars as a DrawBatch with frustum culling.
     *
     * @param lookX View direction X component (unit vector)
     * @param lookY View direction Y component
     * @param lookZ View direction Z component
     * @param fovDeg Field of view in degrees
     */
    fun getVisibleStarBatch(lookX: Float, lookY: Float, lookZ: Float, fovDeg: Float): DrawBatch {
        if (!isLoaded) {
            Log.w(TAG, "Star catalog not loaded, returning empty batch")
            return DrawBatch(
                type = PrimitiveType.POINTS,
                vertices = floatArrayOf(),
                vertexCount = 0,
                transform = Matrix.identity()
            )
        }

        return spatialIndex.getVisibleStarBatch(lookX, lookY, lookZ, fovDeg)
    }

    /**
     * Get the number of loaded stars.
     */
    val starCount: Int
        get() = stars.size

    /**
     * Get spatial index statistics.
     */
    fun getIndexStats(): SpatialStarIndex.IndexStats? {
        return if (isLoaded) spatialIndex.getStats() else null
    }

    companion object {
        private const val TAG = "StarCatalog"
    }
}
