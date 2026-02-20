package com.stardroid.awakening.tools

import com.google.flatbuffers.FlatBufferBuilder
import com.google.gson.JsonParser
import com.stardroid.awakening.data.AstronomicalSource
import com.stardroid.awakening.data.AstronomicalSources
import com.stardroid.awakening.data.GeocentricCoordinates
import com.stardroid.awakening.data.LineElement
import java.io.File

class ConstellationConverter {

    private val lineColor = 0x804169E1u // 50% alpha royal blue (ARGB)

    fun convert() {
        val namesMap = loadNames()
        val linesJson = JsonParser.parseReader(
            File("tools/data/constellations.lines.json").reader()
        ).asJsonObject

        val features = linesJson.getAsJsonArray("features")
        println("Processing ${features.size()} constellations...")

        val builder = FlatBufferBuilder(1024 * 64)
        val sourceOffsets = mutableListOf<Int>()

        for (feature in features) {
            val obj = feature.asJsonObject
            val id = obj.get("id").asString
            val fullName = namesMap[id] ?: id
            val geometry = obj.getAsJsonObject("geometry")
            val coordinates = geometry.getAsJsonArray("coordinates")

            // Build all polylines for this constellation
            val lineOffsets = mutableListOf<Int>()
            var raSum = 0.0
            var decSum = 0.0
            var vertexTotal = 0

            for (polyline in coordinates) {
                val points = polyline.asJsonArray

                // Create vertices vector for this polyline
                LineElement.startVerticesVector(builder, points.size())
                // FlatBuffers vectors are built in reverse order
                for (i in points.size() - 1 downTo 0) {
                    val point = points[i].asJsonArray
                    var ra = point[0].asDouble
                    val dec = point[1].asDouble

                    // Normalize RA from -180..+180 to 0..360
                    if (ra < 0) ra += 360.0

                    raSum += ra
                    decSum += dec
                    vertexTotal++

                    GeocentricCoordinates.createGeocentricCoordinates(
                        builder, ra.toFloat(), dec.toFloat()
                    )
                }
                val verticesOffset = builder.endVector()

                val lineOffset = LineElement.createLineElement(
                    builder, lineColor, 1.0f, verticesOffset
                )
                lineOffsets.add(lineOffset)
            }

            // Build lines vector
            val linesVecOffset = AstronomicalSource.createLinesVector(
                builder, lineOffsets.toIntArray()
            )

            // Build names vector
            val nameOffset = builder.createString(fullName)
            val namesVecOffset = AstronomicalSource.createNamesVector(
                builder, intArrayOf(nameOffset)
            )

            // Build source with search location at centroid
            val centroidRa = if (vertexTotal > 0) (raSum / vertexTotal).toFloat() else 0f
            val centroidDec = if (vertexTotal > 0) (decSum / vertexTotal).toFloat() else 0f

            AstronomicalSource.startAstronomicalSource(builder)
            AstronomicalSource.addNames(builder, namesVecOffset)
            AstronomicalSource.addSearchLocation(
                builder,
                GeocentricCoordinates.createGeocentricCoordinates(
                    builder, centroidRa, centroidDec
                )
            )
            AstronomicalSource.addLines(builder, linesVecOffset)
            val sourceOffset = AstronomicalSource.endAstronomicalSource(builder)
            sourceOffsets.add(sourceOffset)
        }

        // Build root table
        val sourcesVecOffset = AstronomicalSources.createSourcesVector(
            builder, sourceOffsets.toIntArray()
        )
        val rootOffset = AstronomicalSources.createAstronomicalSources(builder, sourcesVecOffset)
        AstronomicalSources.finishAstronomicalSourcesBuffer(builder, rootOffset)

        // Write output
        val outputFile = File("app/src/main/assets/constellations.bin")
        outputFile.parentFile.mkdirs()
        val buf = builder.dataBuffer()
        val bytes = ByteArray(buf.remaining())
        buf.get(bytes)
        outputFile.writeBytes(bytes)

        println("Wrote ${sourceOffsets.size} constellations (${bytes.size} bytes) to ${outputFile.path}")
    }

    private fun loadNames(): Map<String, String> {
        val json = JsonParser.parseReader(
            File("tools/data/constellations.json").reader()
        ).asJsonObject

        val namesMap = mutableMapOf<String, String>()
        val features = json.getAsJsonArray("features")
        for (feature in features) {
            val obj = feature.asJsonObject
            val id = obj.get("id").asString
            val name = obj.getAsJsonObject("properties").get("name").asString
            namesMap[id] = name
        }
        return namesMap
    }
}
