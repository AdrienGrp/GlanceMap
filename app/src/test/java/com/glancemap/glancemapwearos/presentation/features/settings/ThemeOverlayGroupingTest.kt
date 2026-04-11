package com.glancemap.glancemapwearos.presentation.features.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeOverlayGroupingTest {

    @Test
    fun groupsTiramisuPoiAndTransportOverlaysAsPoi() {
        assertEquals("poi", ThemeOverlayGrouping.groupIdForOverlay("tms_food"))
        assertEquals("poi", ThemeOverlayGrouping.groupIdForOverlay("tms_acco"))
        assertEquals("poi", ThemeOverlayGrouping.groupIdForOverlay("tms_trans-pub"))
        assertEquals("poi", ThemeOverlayGrouping.groupIdForOverlay("tms_huts"))
    }

    @Test
    fun groupsTiramisuNetworkOverlaysAsRoutes() {
        assertEquals("routes", ThemeOverlayGrouping.groupIdForOverlay("tms_hknw"))
        assertEquals("routes", ThemeOverlayGrouping.groupIdForOverlay("tms_cynw"))
        assertEquals("routes", ThemeOverlayGrouping.groupIdForOverlay("tms_tracks-cycle"))
    }

    @Test
    fun groupsHikeRideSightPoiOverlaysAsPoi() {
        assertEquals("poi", ThemeOverlayGrouping.groupIdForOverlay("publictrans"))
        assertEquals("poi", ThemeOverlayGrouping.groupIdForOverlay("shop"))
        assertEquals("poi", ThemeOverlayGrouping.groupIdForOverlay("hut_shelter"))
        assertEquals("poi", ThemeOverlayGrouping.groupIdForOverlay("infrastructure"))
    }

    @Test
    fun keepsMapDensityTogglesInMapDetails() {
        assertEquals("map", ThemeOverlayGrouping.groupIdForOverlay("more"))
        assertEquals("map", ThemeOverlayGrouping.groupIdForOverlay("less"))
    }
}
