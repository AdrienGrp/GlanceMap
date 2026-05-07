package com.glancemap.glancemapcompanionapp

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
}
