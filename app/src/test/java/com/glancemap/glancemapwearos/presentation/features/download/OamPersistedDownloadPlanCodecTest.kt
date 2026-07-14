package com.glancemap.glancemapwearos.presentation.features.download

import com.glancemap.glancemapwearos.core.maps.DemSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OamPersistedDownloadPlanCodecTest {
    @Test
    fun `round trip preserves recovery plan and updated bundle selection`() {
        val plan =
            OamPersistedDownloadPlan(
                areaIds = listOf("europe-france", "europe-germany"),
                selection =
                    OamDownloadSelection(
                        includeMap = true,
                        includePoi = false,
                        includeRouting = false,
                        includeDem = true,
                        demSource = DemSource.MAPZEN_SKADI_1S,
                        includeRefugesInfo = true,
                    ),
                nextAreaIndex = 1,
                status = OamPersistedDownloadStatus.PAUSED,
            )

        assertEquals(plan, OamPersistedDownloadPlanCodec.decode(OamPersistedDownloadPlanCodec.encode(plan)))
    }

    @Test
    fun `decode rejects incomplete or unknown plans`() {
        assertNull(OamPersistedDownloadPlanCodec.decode(null))
        assertNull(OamPersistedDownloadPlanCodec.decode(""))
        assertNull(OamPersistedDownloadPlanCodec.decode("2|RUNNING|0|mapsforge_dem3|1|1|1|1|0|europe-france"))
        assertNull(OamPersistedDownloadPlanCodec.decode("1|UNKNOWN|0|mapsforge_dem3|1|1|1|1|0|europe-france"))
    }

    @Test
    fun `unchanged pause resumes at completed area but changed settings start a new plan`() {
        val originalSelection = OamDownloadSelection(includePoi = true, includeRouting = true)
        val paused =
            OamPersistedDownloadPlan(
                areaIds = listOf("europe-france", "europe-germany"),
                selection = originalSelection,
                nextAreaIndex = 1,
                status = OamPersistedDownloadStatus.PAUSED,
            )

        val resumed = resumedOrNewDownloadPlan(paused, paused.areaIds, originalSelection)
        val changed =
            resumedOrNewDownloadPlan(
                paused,
                paused.areaIds,
                originalSelection.copy(includePoi = false, includeRouting = false),
            )

        assertEquals(1, resumed.nextAreaIndex)
        assertEquals(OamPersistedDownloadStatus.RUNNING, resumed.status)
        assertEquals(0, changed.nextAreaIndex)
        assertNotEquals(paused.selection, changed.selection)
    }
}
