package com.example.googleAttractionsGpx

import com.example.googleAttractionsGpx.data.repository.GpxTrackParser
import org.junit.Assert.*
import org.junit.Test

class GpxTrackParserTest {

    private val sampleGpx = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="test">
          <trk>
            <name>Test Track</name>
            <trkseg>
              <trkpt lat="48.0" lon="11.0"/>
              <trkpt lat="48.001" lon="11.0"/>
              <trkpt lat="48.002" lon="11.0"/>
            </trkseg>
          </trk>
        </gpx>
    """.trimIndent()

    @Test
    fun parseTrackPoints_returnsList() {
        val points = GpxTrackParser.parseTrackPoints(sampleGpx.byteInputStream())
        assertEquals(3, points.size)
        assertEquals(48.0, points[0].latitude, 0.0001)
        assertEquals(11.0, points[0].longitude, 0.0001)
        assertEquals(48.002, points[2].latitude, 0.0001)
    }

    @Test
    fun parseTrackPoints_multipleSegments_concatenated() {
        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test">
              <trk><trkseg>
                <trkpt lat="48.0" lon="11.0"/>
              </trkseg><trkseg>
                <trkpt lat="49.0" lon="12.0"/>
              </trkseg></trk>
            </gpx>
        """.trimIndent()
        val points = GpxTrackParser.parseTrackPoints(gpx.byteInputStream())
        assertEquals(2, points.size)
    }

    @Test
    fun parseTrackPoints_noTrackPoints_returnsEmpty() {
        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test">
              <wpt lat="48.0" lon="11.0"><name>POI</name></wpt>
            </gpx>
        """.trimIndent()
        val points = GpxTrackParser.parseTrackPoints(gpx.byteInputStream())
        assertTrue(points.isEmpty())
    }
}
