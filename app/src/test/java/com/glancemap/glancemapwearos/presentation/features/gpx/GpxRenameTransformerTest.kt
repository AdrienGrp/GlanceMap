package com.glancemap.glancemapwearos.presentation.features.gpx

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class GpxRenameTransformerTest {
    @Test
    fun renameKeepsRecordingMetadataAndPointExtensions() {
        val renamedXml = renameGpxXmlTitle(recordingGpx, "Bike & climb")
        val output = renamedXml.toString(Charsets.UTF_8)
        val document =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(ByteArrayInputStream(renamedXml))

        assertTrue(output.contains("<name>Bike &amp; climb</name>"))
        assertTrue(!output.contains("Old recording title"))
        assertTrue(document.getElementsByTagName("name").item(0).textContent == "Bike & climb")
        assertTrue(document.getElementsByTagName("name").item(1).textContent == "Bike & climb")
        assertTrue(document.getElementsByTagName("gmap:activityType").item(0).textContent == "recording")
        assertTrue(document.getElementsByTagName("gmap:activityProfile").item(0).textContent == "BIKE")
        assertTrue(output.contains("recordingTrackSmoothingMode"))
        assertTrue(output.contains("heartRateBpm"))
    }

    private companion object {
        val recordingGpx =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="GlanceMap"
                xmlns="http://www.topografix.com/GPX/1/1"
                xmlns:gmap="https://glancemap.app/gpx">
              <metadata>
                <name>Old recording title</name>
                <extensions>
                  <gmap:activityType>recording</gmap:activityType>
                  <gmap:activityProfile>BIKE</gmap:activityProfile>
                  <gmap:recordingTrackSmoothingMode>ADAPTIVE</gmap:recordingTrackSmoothingMode>
                </extensions>
              </metadata>
              <trk>
                <name>Old recording title</name>
                <trkseg>
                  <trkpt lat="45.0" lon="6.0">
                    <ele>1000.0</ele>
                    <time>2026-07-16T10:00:00Z</time>
                    <extensions><gmap:heartRateBpm>145</gmap:heartRateBpm></extensions>
                  </trkpt>
                </trkseg>
                <trkseg>
                  <trkpt lat="45.0001" lon="6.0001">
                    <ele>1005.0</ele>
                    <time>2026-07-16T10:00:03Z</time>
                  </trkpt>
                </trkseg>
              </trk>
            </gpx>
            """.trimIndent()
    }
}
