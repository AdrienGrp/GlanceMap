package com.glancemap.glancemapwearos.presentation.features.navigate

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mapsforge.core.model.LatLong

class NavigateLiveDistanceSupportTest {
    @Test
    fun liveDistanceOriginPrefersCurrentMarkerPosition() {
        val marker = LatLong(45.001, 6.001)
        val fallback = LatLong(45.0, 6.0)

        assertEquals(marker, resolveLiveDistanceOrigin(marker, fallback))
    }

    @Test
    fun liveDistanceOriginFallsBackToLastKnownLocation() {
        val fallback = LatLong(45.0, 6.0)

        assertEquals(fallback, resolveLiveDistanceOrigin(null, fallback))
    }

    @Test
    fun liveDistanceLabelUsesMetersForShortMetricDistances() {
        assertEquals("275 m", formatLiveDistanceLabel(meters = 275.4, isMetric = true))
    }

    @Test
    fun liveDistanceLabelUsesKilometersForLongMetricDistances() {
        assertEquals(
            "1.5 km",
            normalizeDecimalSeparator(formatLiveDistanceLabel(meters = 1_530.0, isMetric = true)),
        )
    }

    @Test
    fun liveDistanceLabelUsesFeetForShortImperialDistances() {
        assertEquals("902 ft", formatLiveDistanceLabel(meters = 275.0, isMetric = false))
    }

    @Test
    fun liveDistanceLabelUsesMilesForLongImperialDistances() {
        assertEquals(
            "1.0 mi",
            normalizeDecimalSeparator(formatLiveDistanceLabel(meters = 1_609.344, isMetric = false)),
        )
    }

    private fun normalizeDecimalSeparator(value: String): String = value.replace(',', '.')
}
