package com.glancemap.glancemapwearos.presentation.features.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OamDownloadSelectionTest {
    @Test
    fun refugesInfoIsOffByDefault() {
        val selection = OamDownloadSelection()

        assertFalse(selection.includeRefugesInfo)
        assertEquals("Map + POI + Routing + Standard elevation", selection.label())
    }

    @Test
    fun refugesInfoOnlyCanDownload() {
        val selection =
            OamDownloadSelection(
                includeMap = false,
                includePoi = false,
                includeRouting = false,
                includeDem = false,
                includeRefugesInfo = true,
            )

        assertTrue(selection.canDownload)
        assertEquals("Refuges.info", selection.label())
    }
}
