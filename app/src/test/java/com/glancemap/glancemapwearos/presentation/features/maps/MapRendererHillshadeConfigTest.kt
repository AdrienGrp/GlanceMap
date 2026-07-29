package com.glancemap.glancemapwearos.presentation.features.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapRendererHillshadeConfigTest {
    @Test
    fun wearAlgorithmUsesAdaptiveResolutionWithoutHighQualityUpscaling() {
        val algorithm = createWearHillShadingAlgorithm()

        assertTrue(algorithm.isAdaptiveZoomEnabled)
        assertFalse(algorithm.isHqEnabled)
        assertEquals(WEAR_HILLSHADE_QUALITY_SCALE, algorithm.customQualityScale, 0.0)
    }

    @Test
    fun hillshadeCacheIdentityChangesWithDemSourceAndContent() {
        val standard =
            resolveMapRendererHillshadeCacheId(
                baseCacheId = "mapcache_base",
                demSourceId = "mapsforge_dem3",
                demSignature = "DEM:one",
            )
        val detailed =
            resolveMapRendererHillshadeCacheId(
                baseCacheId = "mapcache_base",
                demSourceId = "mapzen_skadi_1s",
                demSignature = "DEM:one",
            )
        val updated =
            resolveMapRendererHillshadeCacheId(
                baseCacheId = "mapcache_base",
                demSourceId = "mapsforge_dem3",
                demSignature = "DEM:two",
            )

        assertNotEquals(standard, detailed)
        assertNotEquals(standard, updated)
        assertTrue(standard.startsWith("mapcache_hillshade_"))
    }
}
