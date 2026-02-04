package com.stardroid.awakening.spatial

import kotlin.math.*

/**
 * Lightweight HEALPix implementation for spatial indexing on the celestial sphere.
 *
 * HEALPix divides the sphere into equal-area pixels arranged in rings of constant
 * latitude. This implementation uses the RING numbering scheme.
 *
 * Key concepts:
 * - Nside: resolution parameter (power of 2). Total pixels = 12 * Nside^2
 * - Nside=64 gives 49,152 pixels, ~0.9° resolution - good for ~100K stars
 * - Nside=32 gives 12,288 pixels, ~1.8° resolution - good for ~10K stars
 *
 * Reference: https://healpix.jpl.nasa.gov/
 */
class HEALPix(val nside: Int) {

    init {
        require(nside > 0 && (nside and (nside - 1)) == 0) {
            "Nside must be a positive power of 2"
        }
    }

    /** Total number of pixels */
    val npix: Int = 12 * nside * nside

    /** Number of pixels in polar cap (north or south) */
    private val ncap: Int = 2 * nside * (nside - 1)

    /**
     * Convert RA/Dec coordinates to HEALPix pixel index (RING scheme).
     *
     * @param raDeg Right ascension in degrees [0, 360)
     * @param decDeg Declination in degrees [-90, 90]
     * @return Pixel index in range [0, npix)
     */
    fun raDecToPixel(raDeg: Double, decDeg: Double): Int {
        // Convert to colatitude (theta) and longitude (phi)
        val theta = Math.toRadians(90.0 - decDeg)  // Colatitude from north pole
        val phi = Math.toRadians(raDeg)

        return angToPixRing(theta, phi)
    }

    /**
     * Convert spherical angles to pixel index (RING scheme).
     *
     * @param theta Colatitude in radians [0, PI]
     * @param phi Longitude in radians [0, 2*PI)
     */
    private fun angToPixRing(theta: Double, phi: Double): Int {
        val z = cos(theta)
        val za = abs(z)
        val tt = (phi / (PI / 2.0)).mod(4.0)  // phi normalized to [0, 4)

        return if (za <= 2.0 / 3.0) {
            // Equatorial region
            val temp1 = nside * (0.5 + tt)
            val temp2 = nside * z * 0.75
            val jp = (temp1 - temp2).toInt()  // Index of ascending edge line
            val jm = (temp1 + temp2).toInt()  // Index of descending edge line

            val ir = nside + 1 + jp - jm  // Ring number in [1, 2*nside]
            var kshift = 1 - (ir and 1)  // 1 if ir is even, 0 otherwise

            val nl4 = 4 * nside
            var ip = (jp + jm - nside + kshift + 1) / 2  // Pixel number in ring
            ip = ((ip - 1).mod(nl4)) + 1

            ncap + nl4 * (ir - 1) + ip - 1
        } else {
            // Polar caps
            val tp = tt - tt.toInt()
            val tmp = nside * sqrt(3.0 * (1.0 - za))

            val jp = (tp * tmp).toInt()  // Increasing edge index
            val jm = ((1.0 - tp) * tmp).toInt()  // Decreasing edge index

            val ir = jp + jm + 1  // Ring number
            var ip = (tt * ir).toInt() + 1  // Pixel number in ring
            ip = minOf(ip, 4 * ir)

            if (z > 0) {
                // North polar cap
                2 * ir * (ir - 1) + ip - 1
            } else {
                // South polar cap
                npix - 2 * ir * (ir + 1) + ip - 1
            }
        }
    }

    /**
     * Get the center coordinates of a pixel.
     *
     * @param pixel Pixel index
     * @return Pair of (raDeg, decDeg)
     */
    fun pixelToRaDec(pixel: Int): Pair<Double, Double> {
        val (theta, phi) = pixToAngRing(pixel)
        val decDeg = 90.0 - Math.toDegrees(theta)
        val raDeg = Math.toDegrees(phi)
        return Pair(raDeg, decDeg)
    }

    /**
     * Convert pixel index to spherical angles (RING scheme).
     */
    private fun pixToAngRing(pixel: Int): Pair<Double, Double> {
        if (pixel < ncap) {
            // North polar cap
            val iring = ((1 + sqrt(1.0 + 2.0 * pixel)) / 2.0).toInt()
            val iphi = pixel + 1 - 2 * iring * (iring - 1)
            val theta = acos(1.0 - iring * iring / (3.0 * nside * nside))
            val phi = (iphi - 0.5) * PI / (2.0 * iring)
            return Pair(theta, phi)
        } else if (pixel < npix - ncap) {
            // Equatorial region
            val ip = pixel - ncap
            val nl4 = 4 * nside
            val iring = ip / nl4 + nside
            val iphi = ip.mod(nl4) + 1
            val fodd = ((iring + nside) and 1) + 1.0  // 1 if odd ring, 2 if even
            val theta = acos((2 * nside - iring) * 2.0 / (3.0 * nside))
            val phi = (iphi - fodd / 2.0) * PI / (2.0 * nside)
            return Pair(theta, phi)
        } else {
            // South polar cap
            val ip = npix - pixel
            val iring = ((1 + sqrt(2.0 * ip - 1.0)) / 2.0).toInt()
            val iphi = 4 * iring + 1 - (ip - 2 * iring * (iring - 1))
            val theta = acos(-1.0 + iring * iring / (3.0 * nside * nside))
            val phi = (iphi - 0.5) * PI / (2.0 * iring)
            return Pair(theta, phi)
        }
    }

    /**
     * Get all pixels within a cone (disc) centered at given coordinates.
     *
     * @param raDeg Center RA in degrees
     * @param decDeg Center Dec in degrees
     * @param radiusDeg Cone radius in degrees
     * @return Set of pixel indices that overlap with the cone
     */
    fun queryDisc(raDeg: Double, decDeg: Double, radiusDeg: Double): Set<Int> {
        val result = mutableSetOf<Int>()

        // Convert center to unit vector
        val theta0 = Math.toRadians(90.0 - decDeg)
        val phi0 = Math.toRadians(raDeg)
        val cosRadius = cos(Math.toRadians(radiusDeg))

        val x0 = sin(theta0) * cos(phi0)
        val y0 = sin(theta0) * sin(phi0)
        val z0 = cos(theta0)

        // For small nside (< 32), just check all pixels - it's fast enough
        if (nside < 32) {
            for (pix in 0 until npix) {
                val (theta, phi) = pixToAngRing(pix)
                val x = sin(theta) * cos(phi)
                val y = sin(theta) * sin(phi)
                val z = cos(theta)

                val cosAngle = x * x0 + y * y0 + z * z0
                if (cosAngle >= cosRadius) {
                    result.add(pix)
                }
            }
            return result
        }

        // For larger nside, use ring-based optimization
        // Only check rings that could possibly intersect the disc
        val radiusRad = Math.toRadians(radiusDeg)

        // Dec range that could intersect
        val decMin = decDeg - radiusDeg - resolution()
        val decMax = decDeg + radiusDeg + resolution()

        for (pix in 0 until npix) {
            val (theta, phi) = pixToAngRing(pix)
            val pixDecDeg = 90.0 - Math.toDegrees(theta)

            // Quick rejection based on declination
            if (pixDecDeg < decMin || pixDecDeg > decMax) {
                continue
            }

            val x = sin(theta) * cos(phi)
            val y = sin(theta) * sin(phi)
            val z = cos(theta)

            val cosAngle = x * x0 + y * y0 + z * z0
            if (cosAngle >= cosRadius) {
                result.add(pix)
            }
        }

        return result
    }

    /**
     * Get approximate angular resolution in degrees.
     */
    fun resolution(): Double {
        // Each pixel covers approximately (4*PI / npix) steradians
        // Side length is roughly sqrt(4*PI / npix) radians
        return Math.toDegrees(sqrt(4 * PI / npix))
    }

    companion object {
        /**
         * Create HEALPix with appropriate resolution for given star count.
         * Aims for ~4-8 stars per pixel on average.
         */
        fun forStarCount(starCount: Int): HEALPix {
            val targetPixels = starCount / 6  // ~6 stars per pixel
            // npix = 12 * nside^2, so nside = sqrt(npix / 12)
            val nside = maxOf(1, (sqrt(targetPixels / 12.0)).toInt())
            // Round up to nearest power of 2
            val nsidePow2 = Integer.highestOneBit(nside).let {
                if (it < nside) it * 2 else it
            }
            return HEALPix(maxOf(8, minOf(128, nsidePow2)))  // Clamp to reasonable range
        }
    }
}
