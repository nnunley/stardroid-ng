package com.stardroid.awakening.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.stardroid.awakening.control.AstronomerModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A small globe overlay showing Earth's orientation matching the phone's current pointing direction.
 * Draws a wireframe sphere with latitude/longitude grid and simplified continent outlines.
 */
class CompassOverlay(context: Context) : View(context) {

    var astronomerModel: AstronomerModel? = null

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 0, 0, 20)
        style = Paint.Style.FILL
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 80, 80, 120)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 100, 130, 180)
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
    }

    private val equatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 100, 130, 180)
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }

    private val continentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 80, 180, 80)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val observerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 255, 60, 60)
        style = Paint.Style.FILL
    }

    private val northLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 80, 80)
        textSize = 16f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val continentPath = Path()
    private val gridPath = Path()

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
        val radius = minOf(width, height) / 2f - 8f

        // Get orientation from astronomer model
        val pointing = model.getPointing()
        val lineOfSight = pointing.lineOfSight
        val north = model.getNorth()
        val zenith = model.getZenith()

        // Compute azimuth: angle from north in horizontal plane
        val dot = lineOfSight.x * zenith.x + lineOfSight.y * zenith.y + lineOfSight.z * zenith.z
        val horizontalX = lineOfSight.x - zenith.x * dot
        val horizontalY = lineOfSight.y - zenith.y * dot
        val horizontalZ = lineOfSight.z - zenith.z * dot

        val northDot = horizontalX * north.x + horizontalY * north.y + horizontalZ * north.z
        val eastX = north.y * zenith.z - north.z * zenith.y
        val eastY = north.z * zenith.x - north.x * zenith.z
        val eastZ = north.x * zenith.y - north.y * zenith.x
        val eastDot = horizontalX * eastX + horizontalY * eastY + horizontalZ * eastZ
        val azimuth = atan2(eastDot.toDouble(), northDot.toDouble()).toFloat()

        // Observer location
        val obsLat = Math.toRadians(model.location.latitude.toDouble()).toFloat()
        val obsLon = Math.toRadians(model.location.longitude.toDouble()).toFloat()

        // Globe rotation angles:
        // - tiltAngle: rotate so observer's latitude is centered (view from behind observer)
        // - spinAngle: rotate globe to match phone azimuth
        val tiltAngle = obsLat
        val spinAngle = -azimuth + obsLon

        // Draw background
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)
        canvas.drawCircle(centerX, centerY, radius, outlinePaint)

        // Draw latitude/longitude grid
        drawGrid(canvas, centerX, centerY, radius, tiltAngle, spinAngle)

        // Draw continents
        for (continent in CONTINENTS) {
            drawContinent(canvas, continent, centerX, centerY, radius, tiltAngle, spinAngle)
        }

        // Draw observer location
        drawObserver(canvas, obsLat, obsLon, centerX, centerY, radius, tiltAngle, spinAngle)

        // Draw north pole label if visible
        drawNorthLabel(canvas, centerX, centerY, radius, tiltAngle, spinAngle)
    }

    /**
     * Project a 3D point on the unit sphere to 2D screen coordinates.
     * Returns null if the point is on the back face (z <= 0 after rotation).
     *
     * The sphere point is specified by latitude and longitude (radians).
     * Rotation: first spin around Y-axis (longitude/azimuth), then tilt around X-axis (latitude).
     */
    private fun project(
        lat: Float, lon: Float,
        centerX: Float, centerY: Float, radius: Float,
        tilt: Float, spin: Float
    ): FloatArray? {
        // Convert lat/lon to 3D point on unit sphere
        val cosLat = cos(lat)
        var x = cosLat * sin(lon)
        var y = sin(lat)
        var z = cosLat * cos(lon)

        // Rotate around Y-axis by spin (azimuth)
        val sinSpin = sin(spin)
        val cosSpin = cos(spin)
        val x1 = x * cosSpin + z * sinSpin
        val z1 = -x * sinSpin + z * cosSpin

        // Rotate around X-axis by tilt (latitude)
        val sinTilt = sin(tilt)
        val cosTilt = cos(tilt)
        val y2 = y * cosTilt - z1 * sinTilt
        val z2 = y * sinTilt + z1 * cosTilt

        // Back-face cull
        if (z2 <= 0f) return null

        // Orthographic projection
        return floatArrayOf(
            centerX + x1 * radius,
            centerY - y2 * radius
        )
    }

    private fun drawGrid(
        canvas: Canvas,
        centerX: Float, centerY: Float, radius: Float,
        tilt: Float, spin: Float
    ) {
        // Latitude lines every 30 degrees
        for (latDeg in -60..60 step 30) {
            val lat = Math.toRadians(latDeg.toDouble()).toFloat()
            val paint = if (latDeg == 0) equatorPaint else gridPaint

            gridPath.reset()
            var started = false
            for (lonStep in 0..72) {
                val lon = Math.toRadians((lonStep * 5 - 180).toDouble()).toFloat()
                val p = project(lat, lon, centerX, centerY, radius, tilt, spin)
                if (p != null) {
                    if (!started) {
                        gridPath.moveTo(p[0], p[1])
                        started = true
                    } else {
                        gridPath.lineTo(p[0], p[1])
                    }
                } else {
                    started = false
                }
            }
            canvas.drawPath(gridPath, paint)
        }

        // Longitude lines every 30 degrees
        for (lonDeg in -180 until 180 step 30) {
            val lon = Math.toRadians(lonDeg.toDouble()).toFloat()

            gridPath.reset()
            var started = false
            for (latStep in -18..18) {
                val lat = Math.toRadians((latStep * 5).toDouble()).toFloat()
                val p = project(lat, lon, centerX, centerY, radius, tilt, spin)
                if (p != null) {
                    if (!started) {
                        gridPath.moveTo(p[0], p[1])
                        started = true
                    } else {
                        gridPath.lineTo(p[0], p[1])
                    }
                } else {
                    started = false
                }
            }
            canvas.drawPath(gridPath, gridPaint)
        }
    }

    private fun drawContinent(
        canvas: Canvas,
        coastline: List<FloatArray>,
        centerX: Float, centerY: Float, radius: Float,
        tilt: Float, spin: Float
    ) {
        continentPath.reset()
        var started = false

        for (point in coastline) {
            val lat = Math.toRadians(point[0].toDouble()).toFloat()
            val lon = Math.toRadians(point[1].toDouble()).toFloat()
            val p = project(lat, lon, centerX, centerY, radius, tilt, spin)
            if (p != null) {
                if (!started) {
                    continentPath.moveTo(p[0], p[1])
                    started = true
                } else {
                    continentPath.lineTo(p[0], p[1])
                }
            } else {
                started = false
            }
        }
        canvas.drawPath(continentPath, continentPaint)
    }

    private fun drawObserver(
        canvas: Canvas,
        obsLat: Float, obsLon: Float,
        centerX: Float, centerY: Float, radius: Float,
        tilt: Float, spin: Float
    ) {
        val p = project(obsLat, obsLon, centerX, centerY, radius, tilt, spin) ?: return
        canvas.drawCircle(p[0], p[1], 4f, observerPaint)
    }

    private fun drawNorthLabel(
        canvas: Canvas,
        centerX: Float, centerY: Float, radius: Float,
        tilt: Float, spin: Float
    ) {
        // North pole is at lat=90
        val lat = Math.toRadians(85.0).toFloat() // slightly below pole for label placement
        val lon = 0f
        val p = project(lat, lon, centerX, centerY, radius, tilt, spin) ?: return
        canvas.drawText("N", p[0], p[1] - 4f, northLabelPaint)
    }

    fun update() {
        if (isVisible) {
            invalidate()
        }
    }

    companion object {
        // Simplified continent outlines as [latitude, longitude] pairs.
        // Reduced to ~20-40 points per continent for recognizable shapes.

        private val AFRICA = listOf(
            floatArrayOf(37.0f, -5.6f),    // Strait of Gibraltar
            floatArrayOf(37.5f, 10.5f),    // Tunisia
            floatArrayOf(32.0f, 32.0f),    // NE Egypt
            floatArrayOf(22.0f, 36.5f),    // Red Sea coast
            floatArrayOf(11.5f, 43.0f),    // Djibouti / Horn
            floatArrayOf(5.0f, 42.0f),
            floatArrayOf(-1.0f, 42.0f),
            floatArrayOf(-10.5f, 40.5f),   // Tanzania coast
            floatArrayOf(-15.0f, 40.5f),
            floatArrayOf(-25.5f, 35.0f),   // Mozambique
            floatArrayOf(-34.0f, 26.0f),   // South Africa SE
            floatArrayOf(-34.8f, 18.5f),   // Cape of Good Hope
            floatArrayOf(-30.0f, 17.0f),
            floatArrayOf(-17.0f, 11.5f),   // Angola coast
            floatArrayOf(-6.0f, 12.0f),
            floatArrayOf(-1.0f, 9.0f),
            floatArrayOf(4.0f, 7.0f),      // Nigeria coast
            floatArrayOf(6.0f, 2.5f),
            floatArrayOf(5.0f, -3.0f),     // Ghana/Ivory Coast
            floatArrayOf(4.5f, -7.5f),     // Liberia
            floatArrayOf(8.5f, -13.5f),    // Sierra Leone
            floatArrayOf(14.7f, -17.5f),   // Senegal
            floatArrayOf(21.0f, -17.0f),   // Western Sahara
            floatArrayOf(27.7f, -13.0f),   // Morocco SW
            floatArrayOf(35.8f, -5.9f),    // Morocco N
            floatArrayOf(37.0f, -5.6f),    // Close loop
        )

        private val EUROPE = listOf(
            floatArrayOf(36.0f, -5.6f),    // Gibraltar
            floatArrayOf(38.7f, -9.5f),    // Portugal
            floatArrayOf(43.0f, -9.0f),    // NW Spain
            floatArrayOf(47.5f, -5.5f),    // Brittany
            floatArrayOf(51.0f, 1.5f),     // SE England
            floatArrayOf(53.0f, -6.0f),    // Ireland approx
            floatArrayOf(58.5f, -5.0f),    // Scotland
            floatArrayOf(61.0f, 5.0f),     // Norway SW
            floatArrayOf(71.0f, 25.0f),    // Norway N
            floatArrayOf(70.0f, 30.0f),    // Finland N/Kola
            floatArrayOf(65.0f, 30.0f),
            floatArrayOf(60.0f, 30.0f),    // St Petersburg
            floatArrayOf(55.0f, 20.0f),    // Baltics
            floatArrayOf(54.5f, 14.0f),    // Poland coast
            floatArrayOf(55.5f, 8.0f),     // Denmark
            floatArrayOf(53.5f, 6.0f),     // Netherlands
            floatArrayOf(51.0f, 3.5f),     // Belgium coast
            floatArrayOf(48.5f, -4.5f),    // Brittany
            floatArrayOf(46.0f, -1.5f),    // Bay of Biscay
            floatArrayOf(43.3f, -2.0f),    // N Spain
            floatArrayOf(42.0f, 3.0f),     // Mediterranean
            floatArrayOf(43.5f, 7.5f),     // Riviera
            floatArrayOf(44.0f, 12.5f),    // Italy E
            floatArrayOf(41.0f, 16.5f),    // Italy SE
            floatArrayOf(38.0f, 15.5f),    // Calabria
            floatArrayOf(37.5f, 15.0f),    // Sicily
            floatArrayOf(39.0f, 20.0f),    // Greece W
            floatArrayOf(38.0f, 24.0f),    // Greece E
            floatArrayOf(41.0f, 29.0f),    // Turkey/Istanbul
            floatArrayOf(43.5f, 28.5f),    // Romania coast
            floatArrayOf(46.0f, 30.5f),    // Ukraine/Odessa
            floatArrayOf(45.0f, 37.0f),    // Sea of Azov
        )

        private val ASIA = listOf(
            floatArrayOf(41.0f, 29.0f),    // Istanbul
            floatArrayOf(42.0f, 41.0f),    // Georgia
            floatArrayOf(39.5f, 53.0f),    // Turkmenistan
            floatArrayOf(25.0f, 57.0f),    // Oman
            floatArrayOf(12.5f, 45.0f),    // Yemen/Gulf of Aden
            floatArrayOf(30.0f, 48.0f),    // Kuwait
            floatArrayOf(32.0f, 35.5f),    // Israel
            floatArrayOf(36.5f, 36.0f),    // Turkey S
            floatArrayOf(37.0f, 36.0f),
            floatArrayOf(41.0f, 29.0f),    // Back to Istanbul (closing Mediterranean side)
            // Break - continuing from East
            floatArrayOf(25.0f, 57.0f),    // Oman (restart eastern coast)
            floatArrayOf(25.5f, 62.0f),    // Pakistan coast
            floatArrayOf(22.0f, 68.0f),    // Gujarat
            floatArrayOf(18.0f, 73.0f),    // Western India
            floatArrayOf(8.0f, 77.0f),     // S India
            floatArrayOf(6.5f, 80.0f),     // Sri Lanka
            floatArrayOf(16.0f, 82.5f),    // Bay of Bengal
            floatArrayOf(22.0f, 89.0f),    // Bangladesh
            floatArrayOf(16.0f, 97.0f),    // Myanmar
            floatArrayOf(7.0f, 100.0f),    // Thai/Malay
            floatArrayOf(1.3f, 103.8f),    // Singapore
            floatArrayOf(-2.0f, 106.0f),   // Sumatra
            floatArrayOf(-7.0f, 106.0f),   // Java W
            floatArrayOf(7.0f, 117.0f),    // Borneo
            floatArrayOf(12.0f, 109.0f),   // Vietnam
            floatArrayOf(22.0f, 108.0f),   // S China
            floatArrayOf(30.0f, 122.0f),   // Shanghai
            floatArrayOf(35.0f, 129.0f),   // Korea
            floatArrayOf(39.0f, 128.0f),   // Korea N
            floatArrayOf(43.0f, 132.0f),   // Vladivostok
            floatArrayOf(52.0f, 141.0f),   // Sakhalin
            floatArrayOf(60.0f, 163.0f),   // Kamchatka
            floatArrayOf(67.0f, -170.0f),  // Chukotka
            floatArrayOf(70.0f, -180.0f),  // Bering Strait
        )

        private val JAPAN = listOf(
            floatArrayOf(31.0f, 131.0f),   // Kyushu S
            floatArrayOf(33.0f, 130.0f),   // Kyushu W
            floatArrayOf(34.0f, 131.0f),   // SW Honshu
            floatArrayOf(35.5f, 134.0f),   // Honshu S coast
            floatArrayOf(34.7f, 137.0f),   // Honshu mid
            floatArrayOf(35.3f, 139.5f),   // Tokyo Bay
            floatArrayOf(37.0f, 137.0f),   // Sea of Japan side
            floatArrayOf(39.5f, 140.0f),   // N Honshu
            floatArrayOf(41.5f, 141.0f),   // Hokkaido S
            floatArrayOf(43.0f, 145.5f),   // Hokkaido E
            floatArrayOf(45.0f, 142.0f),   // Hokkaido N
            floatArrayOf(42.0f, 139.5f),   // Hokkaido W
            floatArrayOf(39.5f, 139.5f),
            floatArrayOf(36.5f, 136.5f),
            floatArrayOf(33.5f, 129.5f),
            floatArrayOf(31.0f, 131.0f),   // Close
        )

        private val NORTH_AMERICA = listOf(
            floatArrayOf(7.5f, -77.0f),    // Panama
            floatArrayOf(15.0f, -83.0f),   // Honduras
            floatArrayOf(18.5f, -88.0f),   // Yucatan
            floatArrayOf(21.5f, -87.0f),   // Yucatan E
            floatArrayOf(25.0f, -81.0f),   // Florida S
            floatArrayOf(30.5f, -81.5f),   // Florida NE
            floatArrayOf(35.0f, -75.5f),   // Cape Hatteras
            floatArrayOf(40.5f, -74.0f),   // New York
            floatArrayOf(42.0f, -70.0f),   // Cape Cod
            floatArrayOf(44.5f, -66.5f),   // Maine
            floatArrayOf(46.5f, -61.0f),   // Nova Scotia
            floatArrayOf(47.5f, -55.5f),   // Newfoundland
            floatArrayOf(52.0f, -56.0f),   // Labrador
            floatArrayOf(58.0f, -62.0f),
            floatArrayOf(63.5f, -68.0f),   // Baffin
            floatArrayOf(70.0f, -85.0f),   // Arctic
            floatArrayOf(72.0f, -95.0f),
            floatArrayOf(69.0f, -105.0f),
            floatArrayOf(64.0f, -90.0f),   // Hudson Bay
            floatArrayOf(55.0f, -82.0f),   // S Hudson Bay
            floatArrayOf(58.0f, -95.0f),
            floatArrayOf(63.0f, -135.0f),  // Yukon
            floatArrayOf(61.0f, -140.0f),  // Alaska SE
            floatArrayOf(64.0f, -158.0f),  // Alaska W
            floatArrayOf(71.0f, -156.0f),  // Barrow
            floatArrayOf(66.0f, -168.0f),  // Bering
            floatArrayOf(60.0f, -163.0f),  // Alaska SW
            floatArrayOf(55.0f, -165.0f),  // Aleutians start
        )

        private val NA_WEST_COAST = listOf(
            floatArrayOf(60.0f, -140.0f),  // Alaska SE
            floatArrayOf(55.0f, -132.0f),  // BC
            floatArrayOf(48.5f, -125.0f),  // Washington
            floatArrayOf(42.0f, -124.5f),  // Oregon
            floatArrayOf(34.0f, -118.5f),  // LA
            floatArrayOf(32.5f, -117.0f),  // San Diego
            floatArrayOf(25.0f, -110.0f),  // Baja tip
            floatArrayOf(23.0f, -106.0f),  // Mazatlan
            floatArrayOf(17.0f, -101.0f),  // Acapulco
            floatArrayOf(15.5f, -95.0f),   // Oaxaca
            floatArrayOf(15.0f, -83.0f),   // Honduras
            floatArrayOf(7.5f, -77.0f),    // Panama
        )

        private val SOUTH_AMERICA = listOf(
            floatArrayOf(7.5f, -77.0f),    // Panama/Colombia
            floatArrayOf(12.0f, -72.0f),   // Venezuela
            floatArrayOf(10.5f, -67.0f),
            floatArrayOf(10.0f, -61.0f),   // Trinidad
            floatArrayOf(6.5f, -52.5f),    // French Guiana
            floatArrayOf(0.0f, -50.0f),    // Amazon mouth
            floatArrayOf(-2.5f, -44.0f),
            floatArrayOf(-7.5f, -35.0f),   // NE Brazil
            floatArrayOf(-13.0f, -38.5f),  // Salvador
            floatArrayOf(-22.9f, -43.0f),  // Rio
            floatArrayOf(-28.0f, -48.5f),  // S Brazil
            floatArrayOf(-34.5f, -53.0f),  // Uruguay
            floatArrayOf(-38.0f, -57.5f),  // Buenos Aires
            floatArrayOf(-41.0f, -63.0f),  // Patagonia
            floatArrayOf(-46.0f, -67.0f),
            floatArrayOf(-52.0f, -69.0f),
            floatArrayOf(-54.8f, -68.0f),  // Tierra del Fuego
            floatArrayOf(-53.0f, -72.0f),
            floatArrayOf(-46.0f, -75.5f),  // Chile
            floatArrayOf(-37.0f, -73.5f),
            floatArrayOf(-30.0f, -71.5f),
            floatArrayOf(-23.5f, -70.5f),  // Atacama
            floatArrayOf(-18.0f, -70.5f),
            floatArrayOf(-14.0f, -76.0f),  // Peru
            floatArrayOf(-5.0f, -81.0f),
            floatArrayOf(0.0f, -80.0f),    // Ecuador
            floatArrayOf(2.0f, -78.0f),
            floatArrayOf(7.5f, -77.0f),    // Close
        )

        private val AUSTRALIA = listOf(
            floatArrayOf(-12.5f, 130.0f),  // Darwin
            floatArrayOf(-11.0f, 136.0f),  // Arnhem Land
            floatArrayOf(-14.5f, 136.0f),  // Gulf of Carpentaria S
            floatArrayOf(-16.0f, 141.0f),
            floatArrayOf(-19.0f, 146.5f),  // Townsville
            floatArrayOf(-23.5f, 150.5f),  // Tropic
            floatArrayOf(-28.0f, 153.5f),  // Brisbane
            floatArrayOf(-33.5f, 151.0f),  // Sydney
            floatArrayOf(-37.5f, 150.0f),  // Melbourne E
            floatArrayOf(-39.0f, 146.0f),  // Tasmania vicinity
            floatArrayOf(-38.0f, 141.0f),  // SA border
            floatArrayOf(-35.0f, 137.0f),  // Adelaide
            floatArrayOf(-33.5f, 134.0f),  // Great Australian Bight
            floatArrayOf(-34.0f, 123.0f),
            floatArrayOf(-32.0f, 115.5f),  // Perth
            floatArrayOf(-26.0f, 113.5f),  // Shark Bay
            floatArrayOf(-22.0f, 114.0f),
            floatArrayOf(-15.0f, 124.0f),  // Kimberley
            floatArrayOf(-12.5f, 130.0f),  // Close
        )

        private val ANTARCTICA = listOf(
            floatArrayOf(-65.0f, -60.0f),
            floatArrayOf(-70.0f, -30.0f),
            floatArrayOf(-70.0f, 0.0f),
            floatArrayOf(-68.0f, 40.0f),
            floatArrayOf(-67.0f, 70.0f),
            floatArrayOf(-66.5f, 100.0f),
            floatArrayOf(-66.0f, 130.0f),
            floatArrayOf(-68.0f, 160.0f),
            floatArrayOf(-75.0f, -170.0f),
            floatArrayOf(-72.0f, -130.0f),
            floatArrayOf(-73.0f, -100.0f),
            floatArrayOf(-65.0f, -60.0f),  // Close
        )

        private val GREENLAND = listOf(
            floatArrayOf(60.0f, -43.0f),
            floatArrayOf(66.0f, -36.0f),
            floatArrayOf(72.0f, -22.0f),
            floatArrayOf(77.0f, -18.0f),
            floatArrayOf(83.0f, -30.0f),
            floatArrayOf(82.0f, -45.0f),
            floatArrayOf(78.0f, -68.0f),
            floatArrayOf(76.0f, -72.0f),
            floatArrayOf(70.0f, -54.0f),
            floatArrayOf(63.0f, -50.0f),
            floatArrayOf(60.0f, -43.0f),   // Close
        )

        private val UK_IRELAND = listOf(
            floatArrayOf(50.0f, -5.5f),    // Cornwall
            floatArrayOf(51.5f, 1.0f),     // SE England
            floatArrayOf(53.5f, 0.0f),     // E England
            floatArrayOf(55.0f, -1.5f),    // NE England
            floatArrayOf(58.5f, -3.0f),    // Scotland N
            floatArrayOf(57.5f, -5.5f),    // Scotland W
            floatArrayOf(55.5f, -5.5f),    // SW Scotland
            floatArrayOf(54.0f, -3.0f),    // Lake District
            floatArrayOf(52.0f, -5.0f),    // Wales
            floatArrayOf(50.0f, -5.5f),    // Close
        )

        private val CONTINENTS: List<List<FloatArray>> = listOf(
            AFRICA,
            EUROPE,
            ASIA,
            JAPAN,
            NORTH_AMERICA,
            NA_WEST_COAST,
            SOUTH_AMERICA,
            AUSTRALIA,
            ANTARCTICA,
            GREENLAND,
            UK_IRELAND,
        )
    }
}
