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
        assertEquals(WEAR_HILLSHADE_MIN_ZOOM_LEVEL, algorithm.zoomMinOverride)
    }

    @Test
    fun wearAlgorithmConstrainsDemConcurrencyAndPreprocessing() {
        val params = createWearHillShadingParams()

        assertEquals(1, params.readingThreadsCount)
        assertEquals(1, params.computingThreadsCount)
        assertFalse(params.isPreprocess)
    }

    @Test
    fun hillshadeRenderingStartsOnlyAtDetailedZoom() {
        assertFalse(shouldRenderHillshadeAtZoom((WEAR_HILLSHADE_MIN_ZOOM_LEVEL - 1).toByte()))
        assertTrue(shouldRenderHillshadeAtZoom(WEAR_HILLSHADE_MIN_ZOOM_LEVEL.toByte()))
    }

    @Test
    fun baseMapStartupPrewarmingIsDisabledWhileHillshadeIsActive() {
        assertFalse(
            shouldWarmMapStartupTileCache(
                prewarmingEnabled = true,
                skipNextStartupPrewarm = false,
                hillshadeEnabled = true,
            ),
        )
        assertTrue(
            shouldWarmMapStartupTileCache(
                prewarmingEnabled = true,
                skipNextStartupPrewarm = false,
                hillshadeEnabled = false,
            ),
        )
        assertFalse(
            shouldWarmMapStartupTileCache(
                prewarmingEnabled = true,
                skipNextStartupPrewarm = true,
                hillshadeEnabled = false,
            ),
        )
    }

    @Test
    fun detailedTerrainOutputIsCappedAboveStandardResolution() {
        assertTrue(WEAR_HILLSHADE_MAX_OUTPUT_AXIS > 1200)
        assertEquals(-2, resolveWearHillshadeQualityFactor(inputAxisLen = 3600, adaptiveQualityFactor = 1))
        assertEquals(1, resolveWearHillshadeQualityFactor(inputAxisLen = 1200, adaptiveQualityFactor = 1))
        assertEquals(-3, resolveWearHillshadeQualityFactor(inputAxisLen = 3600, adaptiveQualityFactor = -3))
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
