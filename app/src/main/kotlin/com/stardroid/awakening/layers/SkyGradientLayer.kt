package com.stardroid.awakening.layers

import com.stardroid.awakening.control.AstronomerModel
import com.stardroid.awakening.ephemeris.JulianDate
import com.stardroid.awakening.ephemeris.SunCalculator
import com.stardroid.awakening.math.Vector3
import kotlin.math.acos
import kotlin.math.min

/**
 * Computes sky background opacity based on Sun altitude.
 *
 * Maps the Sun's elevation above/below the horizon to a background brightness:
 * - Night (< -18 deg): 0.0 (fully transparent dark sky)
 * - Astronomical twilight (-18 to -12 deg): 0.05
 * - Nautical twilight (-12 to -6 deg): 0.15
 * - Civil twilight (-6 to 0 deg): 0.3
 * - Low sun (0 to 10 deg): 0.5
 * - Daytime (> 10 deg): 0.8
 */
class SkyGradientLayer {

    private var lastUpdateTime = 0L
    private var cachedOpacity = 0.0f
    private val UPDATE_INTERVAL_MS = 60_000L

    /**
     * Compute the desired background opacity.
     *
     * @param model AstronomerModel providing zenith direction
     * @return opacity value [0.0, 1.0]
     */
    fun computeOpacity(model: AstronomerModel?): Float {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime > UPDATE_INTERVAL_MS || lastUpdateTime == 0L) {
            cachedOpacity = calculateOpacity(model)
            lastUpdateTime = now
        }
        return cachedOpacity
    }

    private fun calculateOpacity(model: AstronomerModel?): Float {
        if (model == null) return 0.0f

        val jd = JulianDate.now()
        val sunPos = SunCalculator.position(jd)
        val sunVec = sunPos.toUnitVector()
        val sunVector = Vector3(sunVec.first, sunVec.second, sunVec.third)

        val zenith = model.getZenith()
        if (zenith.length2 < 0.01f) return 0.0f

        // Sun altitude = 90 - angle between sun and zenith
        val dot = (sunVector.x * zenith.x + sunVector.y * zenith.y + sunVector.z * zenith.z)
            .coerceIn(-1f, 1f)
        val angleDeg = Math.toDegrees(acos(dot.toDouble()))
        val altitudeDeg = 90.0 - angleDeg

        return when {
            altitudeDeg > 10.0  -> 0.8f
            altitudeDeg > 0.0   -> 0.5f
            altitudeDeg > -6.0  -> 0.3f
            altitudeDeg > -12.0 -> 0.15f
            altitudeDeg > -18.0 -> 0.05f
            else                -> 0.0f
        }
    }
}
