package com.glancemap.glancemapwearos.presentation.features.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mapsforge.core.model.LatLong
import java.time.Instant

class TraceGpxEncoderTest {
    @Test
    fun encodeRecordedTraceAsGpxStartsNewTrackSegmentAfterResume() {
        val points =
            listOf(
                recordedPoint(latitude = 45.0, longitude = 6.0, timeMillis = 1_000L),
                recordedPoint(latitude = 45.0001, longitude = 6.0001, timeMillis = 2_000L),
                recordedPoint(
                    latitude = 45.001,
                    longitude = 6.001,
                    timeMillis = 60_000L,
                    startsNewSegment = true,
                ),
            )

        val xml =
            encodeRecordedTraceAsGpx(
                title = "Paused recording",
                points = points,
            ).toString(Charsets.UTF_8)

        assertEquals(2, "<trkseg>".toRegex().findAll(xml).count())
        assertEquals(listOf(2, 1), recordedTraceSegments(points).map(List<RecordedTracePoint>::size))
    }

    @Test
    fun encodeRecordedTraceAsGpxWritesCoreTrackPointAndExtensions() {
        val bytes =
            encodeRecordedTraceAsGpx(
                title = "Morning Test",
                points =
                    listOf(
                        RecordedTracePoint(
                            latLong = LatLong(45.123456789, 6.987654321),
                            elevationMeters = 1234.56,
                            timeMillis = Instant.parse("2026-06-10T10:15:30Z").toEpochMilli(),
                            accuracyMeters = 7.5f,
                            speedMps = 1.25f,
                            elevationSource = "DEM",
                            heartRateBpm = 142,
                            stepCount = 87,
                            cadenceSpm = 164,
                            barometricPressureHpa = 913.42,
                        ),
                    ),
            )

        val xml = bytes.toString(Charsets.UTF_8)

        assertTrue(xml.contains("creator=\"GlanceMap\""))
        assertTrue(xml.contains("xmlns:gmap=\"https://glancemap.app/gpx/extensions/1\""))
        assertTrue(xml.contains("lat=\"45.12345679\""))
        assertTrue(xml.contains("lon=\"6.98765432\""))
        assertTrue(xml.contains("<ele>1234.6</ele>"))
        assertTrue(xml.contains("<time>2026-06-10T10:15:30Z</time>"))
        assertTrue(xml.contains("<extensions>"))
        assertTrue(xml.contains("<gmap:accuracyMeters>7.50</gmap:accuracyMeters>"))
        assertTrue(xml.contains("<gmap:speedMps>1.25</gmap:speedMps>"))
        assertTrue(xml.contains("<gmap:elevationSource>DEM</gmap:elevationSource>"))
        assertTrue(xml.contains("<gmap:heartRateBpm>142</gmap:heartRateBpm>"))
        assertTrue(xml.contains("<gmap:stepCount>87</gmap:stepCount>"))
        assertTrue(xml.contains("<gmap:cadenceSpm>164</gmap:cadenceSpm>"))
        assertTrue(xml.contains("<gmap:pressureHpa>913.42</gmap:pressureHpa>"))
    }

    @Test
    fun encodeRecordedTraceAsGpxWritesExtensionsForSensorOnlyPointData() {
        val bytes =
            encodeRecordedTraceAsGpx(
                title = "Sensor Test",
                points =
                    listOf(
                        RecordedTracePoint(
                            latLong = LatLong(45.0, 6.0),
                            elevationMeters = null,
                            timeMillis = Instant.parse("2026-06-10T10:15:30Z").toEpochMilli(),
                            accuracyMeters = null,
                            speedMps = null,
                            elevationSource = null,
                            heartRateBpm = 138,
                        ),
                    ),
            )

        val xml = bytes.toString(Charsets.UTF_8)

        assertTrue(xml.contains("<extensions>"))
        assertTrue(xml.contains("<gmap:heartRateBpm>138</gmap:heartRateBpm>"))
    }

    @Test
    fun encodeRecordedTraceAsGpxSkipsExtensionsWhenNoExtraPointDataExists() {
        val bytes =
            encodeRecordedTraceAsGpx(
                title = "Plain Test",
                points =
                    listOf(
                        RecordedTracePoint(
                            latLong = LatLong(45.0, 6.0),
                            elevationMeters = null,
                            timeMillis = Instant.parse("2026-06-10T10:15:30Z").toEpochMilli(),
                            accuracyMeters = null,
                            speedMps = null,
                            elevationSource = null,
                        ),
                    ),
            )

        val xml = bytes.toString(Charsets.UTF_8)

        assertTrue(xml.contains("<trkpt"))
        assertEquals(1, "<extensions>".toRegex().findAll(xml).count())
        assertFalse(xml.substringAfter("<trkpt").substringBefore("</trkpt>").contains("<extensions>"))
        assertFalse(xml.contains("gmap:accuracyMeters"))
    }

    private fun recordedPoint(
        latitude: Double,
        longitude: Double,
        timeMillis: Long,
        startsNewSegment: Boolean = false,
    ): RecordedTracePoint =
        RecordedTracePoint(
            latLong = LatLong(latitude, longitude),
            elevationMeters = null,
            timeMillis = timeMillis,
            accuracyMeters = 8f,
            speedMps = 1f,
            startsNewSegment = startsNewSegment,
        )
}
