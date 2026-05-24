package com.glancemap.glancemapcompanionapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTransferViewModelUriSupportTest {
    @Test
    fun `recognizes geojson file names`() {
        assertTrue(isGeoJsonFileName("refuges.geojson"))
        assertTrue(isGeoJsonFileName("refuges.geo.json"))
        assertTrue(isGeoJsonFileName("REFUGES.GEOJSON"))
        assertFalse(isGeoJsonFileName("refuges.json"))
        assertFalse(isGeoJsonFileName("refuges.gpx"))
    }

    @Test
    fun `keeps normal gpx display name`() {
        assertEquals(
            "tour-du-lac.gpx",
            chooseGpxTransferFileName(
                displayName = "tour-du-lac.gpx",
                uriCandidates = emptyList(),
                gpxText = null,
                preferFallbackName = false,
            ),
        )
    }

    @Test
    fun `recovers gpx file name from uri candidates`() {
        assertEquals(
            "Tour du lac.gpx",
            chooseGpxTransferFileName(
                displayName = "document",
                uriCandidates = listOf("primary:Download/Tour%20du%20lac.gpx"),
                gpxText = null,
                preferFallbackName = false,
            ),
        )
    }

    @Test
    fun `uses gpx metadata name for whatsapp generic display name`() {
        assertEquals(
            "Tour du lac.gpx",
            chooseGpxTransferFileName(
                displayName = "document.gpx",
                uriCandidates = emptyList(),
                gpxText = "<gpx><trk><name>Tour du lac</name><trkseg /></trk></gpx>",
                preferFallbackName = true,
            ),
        )
    }
}
