package com.stardroid.awakening.layers

import com.stardroid.awakening.renderer.DrawBatch
import com.stardroid.awakening.renderer.Matrix
import com.stardroid.awakening.renderer.PrimitiveType
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

/**
 * Displays radiant points of major meteor showers.
 *
 * Active showers appear bright cyan, inactive ones are dim.
 * Positions are cached and refreshed every 60 seconds.
 */
class MeteorShowerLayer {

    private data class Shower(
        val name: String,
        val raDeg: Double,
        val decDeg: Double,
        val peakMonth: Int,   // 1-12
        val peakDay: Int,
        val activeStartMonth: Int,
        val activeStartDay: Int,
        val activeEndMonth: Int,
        val activeEndDay: Int,
        val zhr: Int
    )

    private val showers = listOf(
        Shower("Quadrantids",     230.1, 48.5,  1, 3,   12, 28, 1, 12, 120),
        Shower("Lyrids",          271.4, 33.3,  4, 22,  4, 16, 4, 25, 18),
        Shower("Eta Aquariids",   338.0, -1.0,  5, 6,   4, 19, 5, 28, 50),
        Shower("Delta Aquariids", 340.0, -16.0, 7, 30,  7, 12, 8, 23, 20),
        Shower("Perseids",         48.0, 58.0,  8, 12,  7, 17, 8, 24, 100),
        Shower("Draconids",       262.0, 54.0, 10, 8,  10, 6, 10, 10, 10),
        Shower("Orionids",         95.0, 16.0, 10, 21, 10, 2, 11, 7, 20),
        Shower("Taurids",          52.0, 14.0, 11, 5,  10, 20, 11, 30, 5),
        Shower("Leonids",         152.3, 22.0, 11, 17, 11, 6, 11, 30, 15),
        Shower("Geminids",        112.0, 33.0, 12, 14, 12, 4, 12, 20, 150),
        Shower("Ursids",          217.0, 76.0, 12, 22, 12, 17, 12, 26, 10)
    )

    private val activeColor = floatArrayOf(0.3f, 0.9f, 1.0f, 1.0f)
    private val inactiveColor = floatArrayOf(0.2f, 0.3f, 0.4f, 0.3f)

    private var cachedVertices: FloatArray = floatArrayOf()
    private var cachedCount: Int = 0
    private var lastUpdateTime = 0L
    private val UPDATE_INTERVAL_MS = 60_000L

    fun getBatch(): DrawBatch {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime > UPDATE_INTERVAL_MS || cachedCount == 0) {
            update()
            lastUpdateTime = now
        }
        return DrawBatch(
            type = PrimitiveType.POINTS,
            vertices = cachedVertices,
            vertexCount = cachedCount,
            transform = Matrix.identity()
        )
    }

    private fun update() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)

        val verts = FloatArray(showers.size * 7)
        var offset = 0

        for (shower in showers) {
            val raRad = Math.toRadians(shower.raDeg)
            val decRad = Math.toRadians(shower.decDeg)

            val x = (cos(decRad) * cos(raRad)).toFloat()
            val y = (cos(decRad) * sin(raRad)).toFloat()
            val z = sin(decRad).toFloat()

            val active = isActive(shower, month, day)
            val color = if (active) activeColor else inactiveColor

            verts[offset++] = x
            verts[offset++] = y
            verts[offset++] = z
            verts[offset++] = color[0]
            verts[offset++] = color[1]
            verts[offset++] = color[2]
            verts[offset++] = color[3]
        }

        cachedVertices = verts
        cachedCount = showers.size
    }

    private fun isActive(shower: Shower, month: Int, day: Int): Boolean {
        val current = month * 100 + day
        val start = shower.activeStartMonth * 100 + shower.activeStartDay
        val end = shower.activeEndMonth * 100 + shower.activeEndDay

        return if (start <= end) {
            current in start..end
        } else {
            // Wraps around year boundary (e.g., Quadrantids: Dec 28 - Jan 12)
            current >= start || current <= end
        }
    }
}
