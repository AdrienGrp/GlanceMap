package com.glancemap.glancemapwearos.presentation.features.download

import org.junit.Assert.assertEquals
import org.junit.Test

class OamBundleRefreshSummaryTest {
    @Test
    fun bundlesToRefreshOnlyIncludesConfirmedUpdates() {
        val updateBundle = installedBundle(areaId = "update")
        val unknownBundle = installedBundle(areaId = "unknown")
        val upToDateBundle = installedBundle(areaId = "up-to-date")

        val summary =
            OamBundleRefreshSummary(
                checks =
                    listOf(
                        updateCheck(updateBundle, OamBundleUpdateStatus.UPDATE_AVAILABLE),
                        updateCheck(unknownBundle, OamBundleUpdateStatus.UNKNOWN),
                        updateCheck(upToDateBundle, OamBundleUpdateStatus.UP_TO_DATE),
                    ),
            )

        assertEquals(listOf(updateBundle), summary.bundlesToRefresh)
        assertEquals(1, summary.updateAvailableCount)
        assertEquals(1, summary.unknownCount)
        assertEquals(1, summary.upToDateCount)
    }

    @Test
    fun refreshForcesOnlyIncludesChangedRemoteFiles() {
        val bundle =
            installedBundle(
                areaId = "area",
                routingFileNames = listOf("E5_N45.rd5", "E5_N46.rd5"),
                demTileIds = listOf("N45E006", "N45E007"),
            )
        val check =
            OamBundleUpdateCheck(
                bundle = bundle,
                status = OamBundleUpdateStatus.UPDATE_AVAILABLE,
                checkedFileCount = 4,
                changedFileNames = listOf("Area.Poi.zip", "E5_N46.rd5", "N45E007.hgt.zip"),
            )

        val forces = check.refreshForces(area())

        assertEquals(false, forces.forceMap)
        assertEquals(true, forces.forcePoi)
        assertEquals(setOf("E5_N46.rd5"), forces.forceRoutingFileNames)
        assertEquals(setOf("N45E007"), forces.forceDemTileIds)
    }

    private fun updateCheck(
        bundle: OamInstalledBundle,
        status: OamBundleUpdateStatus,
    ): OamBundleUpdateCheck =
        OamBundleUpdateCheck(
            bundle = bundle,
            status = status,
            checkedFileCount = if (status == OamBundleUpdateStatus.UNKNOWN) 0 else 1,
        )

    private fun area(): OamDownloadArea =
        OamDownloadArea(
            id = "area",
            continent = "Europe",
            region = "Area",
            mapSizeLabel = "1 MB",
            mapSizeBytes = 1L,
            poiSizeLabel = "1 MB",
            poiSizeBytes = 1L,
            notes = "",
            contourLabel = "",
            mapZipUrl = "https://example.test/Area.zip",
            poiZipUrl = "https://example.test/Area.Poi.zip",
        )

    private fun installedBundle(
        areaId: String,
        routingFileNames: List<String> = emptyList(),
        demTileIds: List<String> = emptyList(),
    ): OamInstalledBundle =
        OamInstalledBundle(
            areaId = areaId,
            areaLabel = areaId,
            bundleChoice = OamBundleChoice.MAP_ONLY,
            mapFileName = "$areaId.map",
            poiFileName = null,
            routingFileNames = routingFileNames,
            demTileIds = demTileIds,
            installedAtMillis = 1L,
        )
}
