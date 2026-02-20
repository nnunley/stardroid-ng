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
 * Loads and provides Messier object data from the binary protobuf catalog.
 *
 * ~110 deep-sky objects (galaxies, nebulae, clusters) rendered as colored points.
 */
class MessierCatalog(private val assetManager: AssetManager) {

    private var vertices: FloatArray = floatArrayOf()
    private var vertexCount: Int = 0
    private var isLoaded = false

    fun load() {
        if (isLoaded) return

        Log.d(TAG, "Loading Messier catalog...")
        val startTime = System.currentTimeMillis()

        try {
            assetManager.open("messier.binary").use { stream ->
                val sources = SourceProto.AstronomicalSourcesProto.parseFrom(stream)

                val verts = mutableListOf<Float>()

                for (i in 0 until sources.sourceCount) {
                    val source = sources.getSource(i)

                    for (j in 0 until source.pointCount) {
                        val point = source.getPoint(j)
                        val location = point.location

                        val raDeg = location.rightAscension.toDouble()
                        val decDeg = location.declination.toDouble()
                        val raRad = Math.toRadians(raDeg)
                        val decRad = Math.toRadians(decDeg)

                        val x = (cos(decRad) * cos(raRad)).toFloat()
                        val y = (cos(decRad) * sin(raRad)).toFloat()
                        val z = sin(decRad).toFloat()

                        val color = point.color.toInt()
                        val a = ((color shr 24) and 0xFF) / 255f
                        val r = ((color shr 16) and 0xFF) / 255f
                        val g = ((color shr 8) and 0xFF) / 255f
                        val b = (color and 0xFF) / 255f

                        verts.add(x); verts.add(y); verts.add(z)
                        verts.add(r); verts.add(g); verts.add(b); verts.add(a)
                    }
                }

                vertices = verts.toFloatArray()
                vertexCount = verts.size / 7
                isLoaded = true

                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "Loaded $vertexCount Messier objects in ${elapsed}ms")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Messier catalog", e)
        }
    }

    fun getMessierBatch(): DrawBatch {
        if (!isLoaded) {
            return DrawBatch(
                type = PrimitiveType.POINTS,
                vertices = floatArrayOf(),
                vertexCount = 0,
                transform = Matrix.identity()
            )
        }

        return DrawBatch(
            type = PrimitiveType.POINTS,
            vertices = vertices,
            vertexCount = vertexCount,
            transform = Matrix.identity()
        )
    }

    companion object {
        private const val TAG = "MessierCatalog"
    }
}
