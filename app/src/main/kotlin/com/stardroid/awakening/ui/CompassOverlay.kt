package com.stardroid.awakening.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.stardroid.awakening.control.AstronomerModel
import kotlin.math.cos
import kotlin.math.sin

/**
 * A small compass globe overlay showing current orientation.
 * Displays cardinal directions (N, S, E, W) and horizon line.
 */
class CompassOverlay(context: Context) : View(context) {

    var astronomerModel: AstronomerModel? = null

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(128, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 100, 100, 100)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 100, 150, 100)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val northPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    private val southPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    private val zenithPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.FILL
    }

    private val pointerPath = Path()

    private var isVisible = true

    fun toggle() {
        isVisible = !isVisible
        visibility = if (isVisible) VISIBLE else GONE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val model = astronomerModel ?: return

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(width, height) / 2f - 10f

        // Draw background circle
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)
        canvas.drawCircle(centerX, centerY, radius, circlePaint)

        // Get current pointing direction
        val pointing = model.getPointing()
        val lineOfSight = pointing.lineOfSight
        val up = pointing.perpendicular

        // Get cardinal directions in celestial coords
        val north = model.getNorth()
        val zenith = model.getZenith()

        // Calculate azimuth (angle from north) and altitude (angle from horizon)
        // Project lineOfSight onto horizontal plane
        val horizontalComponent = lineOfSight - zenith * (lineOfSight.x * zenith.x + lineOfSight.y * zenith.y + lineOfSight.z * zenith.z)

        // Altitude: angle between lineOfSight and horizontal plane
        val altitudeDot = lineOfSight.x * zenith.x + lineOfSight.y * zenith.y + lineOfSight.z * zenith.z
        val altitude = Math.toDegrees(Math.asin(altitudeDot.toDouble()).coerceIn(-1.0, 1.0)).toFloat()

        // Azimuth: angle from north in horizontal plane
        val northDot = horizontalComponent.x * north.x + horizontalComponent.y * north.y + horizontalComponent.z * north.z
        val east = model.getZenith().let { z ->
            val n = north
            // East = North × Up (zenith)
            com.stardroid.awakening.math.Vector3(
                n.y * z.z - n.z * z.y,
                n.z * z.x - n.x * z.z,
                n.x * z.y - n.y * z.x
            )
        }
        val eastDot = horizontalComponent.x * east.x + horizontalComponent.y * east.y + horizontalComponent.z * east.z
        val azimuth = Math.toDegrees(Math.atan2(eastDot.toDouble(), northDot.toDouble())).toFloat()

        // Draw horizon line (tilted based on phone roll)
        val rollAngle = Math.atan2(up.x.toDouble(), up.z.toDouble()).toFloat()
        canvas.save()
        canvas.rotate(Math.toDegrees(rollAngle.toDouble()).toFloat(), centerX, centerY)
        canvas.drawLine(centerX - radius * 0.8f, centerY, centerX + radius * 0.8f, centerY, horizonPaint)
        canvas.restore()

        // Draw cardinal direction indicators
        val cardinalRadius = radius * 0.7f
        val directions = listOf(
            Triple("N", 0f, northPaint),
            Triple("E", 90f, textPaint),
            Triple("S", 180f, southPaint),
            Triple("W", 270f, textPaint)
        )

        for ((label, angle, paint) in directions) {
            val dirAngle = Math.toRadians((angle - azimuth).toDouble())
            val dx = (sin(dirAngle) * cardinalRadius).toFloat()
            val dy = (-cos(dirAngle) * cardinalRadius).toFloat()

            if (label == "N") {
                // Draw north pointer (red triangle)
                drawPointer(canvas, centerX + dx, centerY + dy, dirAngle.toFloat(), northPaint)
            } else if (label == "S") {
                // Draw south pointer (white triangle)
                drawPointer(canvas, centerX + dx, centerY + dy, dirAngle.toFloat(), southPaint)
            } else {
                // Draw E/W as text
                canvas.drawText(label, centerX + dx, centerY + dy + 8f, textPaint)
            }
        }

        // Draw altitude indicator (dot showing how high we're looking)
        val altitudeY = centerY - (altitude / 90f) * radius * 0.6f
        canvas.drawCircle(centerX, altitudeY, 6f, zenithPaint)

        // Draw altitude text
        textPaint.textSize = 18f
        canvas.drawText("${altitude.toInt()}°", centerX, height - 5f, textPaint)
        textPaint.textSize = 24f
    }

    private fun drawPointer(canvas: Canvas, x: Float, y: Float, angle: Float, paint: Paint) {
        val size = 12f
        pointerPath.reset()
        pointerPath.moveTo(0f, -size)
        pointerPath.lineTo(-size * 0.5f, size * 0.5f)
        pointerPath.lineTo(size * 0.5f, size * 0.5f)
        pointerPath.close()

        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(Math.toDegrees(angle.toDouble()).toFloat() + 180f)
        canvas.drawPath(pointerPath, paint)
        canvas.restore()
    }

    fun update() {
        if (isVisible) {
            invalidate()
        }
    }
}
