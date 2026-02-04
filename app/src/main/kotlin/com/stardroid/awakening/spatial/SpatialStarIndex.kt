package com.stardroid.awakening.spatial

import android.util.Log
import com.stardroid.awakening.renderer.DrawBatch
import com.stardroid.awakening.renderer.Matrix
import com.stardroid.awakening.renderer.PrimitiveType
import kotlin.math.*

/**
 * Spatial index for stars using HEALPix pixelization.
 *
 * Stars are organized into HEALPix pixels for efficient frustum culling.
 * Only stars in pixels that overlap the current field of view are rendered.
 */
class SpatialStarIndex {

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
        val name: String?
    )

    private var healpix: HEALPix? = null
    private var pixelToStars: Map<Int, List<Star>> = emptyMap()
    private var allStars: List<Star> = emptyList()
    private var isIndexed = false

    // Cache for visible pixels to avoid recomputation every frame
    private var cachedFovHash: Int = 0
    private var cachedVisiblePixels: Set<Int> = emptySet()

    /**
     * Build the spatial index from a list of stars.
     */
    fun buildIndex(stars: List<Star>) {
        if (stars.isEmpty()) {
            Log.w(TAG, "Cannot build index from empty star list")
            return
        }

        val startTime = System.currentTimeMillis()

        allStars = stars
        healpix = HEALPix.forStarCount(stars.size)
        val hp = healpix!!

        Log.d(TAG, "Building HEALPix index: nside=${hp.nside}, npix=${hp.npix}, resolution=${String.format("%.2f", hp.resolution())}°")

        // Assign each star to a pixel
        val pixelMap = mutableMapOf<Int, MutableList<Star>>()

        for (star in stars) {
            val pixel = hp.raDecToPixel(star.raDeg, star.decDeg)
            pixelMap.getOrPut(pixel) { mutableListOf() }.add(star)
        }

        pixelToStars = pixelMap
        isIndexed = true

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "Index built in ${elapsed}ms: ${pixelMap.size} occupied pixels, avg ${stars.size / maxOf(1, pixelMap.size)} stars/pixel")
    }

    /**
     * Get stars visible within a cone (field of view).
     *
     * @param lookRaDeg RA of view center in degrees
     * @param lookDecDeg Dec of view center in degrees
     * @param fovDeg Field of view diameter in degrees
     * @return List of stars within the field of view
     */
    fun getVisibleStars(lookRaDeg: Double, lookDecDeg: Double, fovDeg: Double): List<Star> {
        if (!isIndexed) return allStars

        val hp = healpix ?: return allStars

        // Query pixels that overlap the FOV (add margin for safety)
        val queryRadius = fovDeg / 2.0 + hp.resolution()

        // Check cache
        val fovHash = (lookRaDeg * 1000).toInt() xor (lookDecDeg * 1000).toInt() xor (fovDeg * 100).toInt()
        val visiblePixels = if (fovHash == cachedFovHash) {
            cachedVisiblePixels
        } else {
            val pixels = hp.queryDisc(lookRaDeg, lookDecDeg, queryRadius)
            cachedFovHash = fovHash
            cachedVisiblePixels = pixels
            pixels
        }

        // Collect stars from visible pixels
        val result = mutableListOf<Star>()
        for (pixel in visiblePixels) {
            pixelToStars[pixel]?.let { result.addAll(it) }
        }

        return result
    }

    /**
     * Get a DrawBatch for visible stars only.
     *
     * @param lookX View direction X component (unit vector)
     * @param lookY View direction Y component
     * @param lookZ View direction Z component
     * @param fovDeg Field of view in degrees
     */
    fun getVisibleStarBatch(lookX: Float, lookY: Float, lookZ: Float, fovDeg: Float): DrawBatch {
        if (!isIndexed) {
            return getAllStarsBatch()
        }

        // Convert look direction to RA/Dec
        val lookDec = Math.toDegrees(asin(lookZ.toDouble().coerceIn(-1.0, 1.0)))
        val lookRa = Math.toDegrees(atan2(lookY.toDouble(), lookX.toDouble())).let {
            if (it < 0) it + 360.0 else it
        }

        val visibleStars = getVisibleStars(lookRa, lookDec, fovDeg.toDouble())

        return createBatch(visibleStars)
    }

    /**
     * Get a DrawBatch containing all stars (no culling).
     */
    fun getAllStarsBatch(): DrawBatch {
        return createBatch(allStars)
    }

    private fun createBatch(stars: List<Star>): DrawBatch {
        if (stars.isEmpty()) {
            return DrawBatch(
                type = PrimitiveType.POINTS,
                vertices = floatArrayOf(),
                vertexCount = 0,
                transform = Matrix.identity()
            )
        }

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
     * Get statistics about the index.
     */
    fun getStats(): IndexStats {
        val hp = healpix
        return IndexStats(
            totalStars = allStars.size,
            nside = hp?.nside ?: 0,
            totalPixels = hp?.npix ?: 0,
            occupiedPixels = pixelToStars.size,
            resolutionDeg = hp?.resolution() ?: 0.0,
            avgStarsPerPixel = if (pixelToStars.isNotEmpty()) {
                allStars.size.toFloat() / pixelToStars.size
            } else 0f
        )
    }

    data class IndexStats(
        val totalStars: Int,
        val nside: Int,
        val totalPixels: Int,
        val occupiedPixels: Int,
        val resolutionDeg: Double,
        val avgStarsPerPixel: Float
    )

    companion object {
        private const val TAG = "SpatialStarIndex"
    }
}
