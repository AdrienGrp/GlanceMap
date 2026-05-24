package com.glancemap.glancemapwearos.presentation.features.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OamRemoteFileRequestsTest {
    @Test
    fun knownMissingDemTilesAreSkippedFromUpdateRequests() {
        val requests =
            buildRemoteFileRequestsForBundle(
                area = area(),
                bundle =
                    installedBundle(
                        demTileIds = listOf("N45E006", "N45E007"),
                    ),
                demRemoteFileRequest = { tileId ->
                    RemoteFileRequest(
                        url = "https://example.test/dem/$tileId.hgt.zip",
                        fileName = "$tileId.hgt.zip",
                    )
                },
                shouldSkipKnownMissingDemTile = { it == "N45E006" },
            )

        val fileNames = requests.map { it.fileName }
        assertTrue("map.zip" in fileNames)
        assertTrue("segment.rd5" in fileNames)
        assertFalse("N45E006.hgt.zip" in fileNames)
        assertTrue("N45E007.hgt.zip" in fileNames)
        assertEquals(3, requests.size)
    }

    @Test
    fun allKnownMissingDemOnlyBundleHasNoRemoteRequests() {
        val requests =
            buildRemoteFileRequestsForBundle(
                area = area(),
                bundle =
                    installedBundle(
                        includeMap = false,
                        routingFileNames = emptyList(),
                        demTileIds = listOf("N45E006"),
                    ),
                demRemoteFileRequest = { tileId ->
                    RemoteFileRequest(
                        url = "https://example.test/dem/$tileId.hgt.zip",
                        fileName = "$tileId.hgt.zip",
                    )
                },
                shouldSkipKnownMissingDemTile = { true },
            )

        assertEquals(emptyList<RemoteFileRequest>(), requests)
    }

    private fun area(): OamDownloadArea =
        OamDownloadArea(
            id = "test-area",
            continent = "Europe",
            region = "Test Area",
            mapSizeLabel = "1 MB",
            mapSizeBytes = 1L,
            poiSizeLabel = "1 MB",
            poiSizeBytes = 1L,
            notes = "",
            contourLabel = "",
            mapZipUrl = "https://example.test/maps/map.zip",
            poiZipUrl = "https://example.test/pois/poi.zip",
        )

    private fun installedBundle(
        includeMap: Boolean = true,
        routingFileNames: List<String> = listOf("segment.rd5"),
        demTileIds: List<String>,
    ): OamInstalledBundle =
        OamInstalledBundle(
            areaId = "test-area",
            areaLabel = "Test Area",
            bundleChoice = OamBundleChoice.MAP_ONLY,
            mapFileName = "map.map".takeIf { includeMap },
            poiFileName = null,
            routingFileNames = routingFileNames,
            demTileIds = demTileIds,
            installedAtMillis = 1L,
        )
}
