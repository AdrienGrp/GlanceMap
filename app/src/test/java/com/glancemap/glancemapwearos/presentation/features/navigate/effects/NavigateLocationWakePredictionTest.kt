package com.glancemap.glancemapwearos.presentation.features.navigate.effects

import com.glancemap.glancemapwearos.presentation.features.navigate.motion.MarkerMotionReading
import com.glancemap.glancemapwearos.presentation.features.navigate.motion.MarkerMotionSeed
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mapsforge.core.model.LatLong

class NavigateLocationWakePredictionTest {
    @Test
    fun recentAccurateMovingWakeAnchorCanResumePrediction() {
        assertTrue(
            shouldResumePredictionFromWakeAnchor(
                anchor = wakeAnchor(fixElapsedMs = 98_700L),
                receivedAtElapsedMs = 100_000L,
                expectedGpsIntervalMs = 3_000L,
            ),
        )
    }

    @Test
    fun uncertainWakeAnchorStillWaitsForFreshFix() {
        assertFalse(
            shouldResumePredictionFromWakeAnchor(
                anchor = wakeAnchor(fixElapsedMs = 94_000L),
                receivedAtElapsedMs = 100_000L,
                expectedGpsIntervalMs = 3_000L,
            ),
        )
        assertFalse(
            shouldResumePredictionFromWakeAnchor(
                anchor = wakeAnchor(fixElapsedMs = 99_000L, accuracyM = 30f),
                receivedAtElapsedMs = 100_000L,
                expectedGpsIntervalMs = 3_000L,
            ),
        )
        assertFalse(
            shouldResumePredictionFromWakeAnchor(
                anchor = wakeAnchor(fixElapsedMs = 99_000L, bearingDeg = null),
                receivedAtElapsedMs = 100_000L,
                expectedGpsIntervalMs = 3_000L,
            ),
        )
    }

    private fun wakeAnchor(
        fixElapsedMs: Long,
        accuracyM: Float = 8f,
        bearingDeg: Float? = 90f,
    ): MarkerMotionSeed =
        MarkerMotionSeed(
            latLong = LatLong(48.8566, 2.3522),
            reading =
                MarkerMotionReading(
                    fixElapsedMs = fixElapsedMs,
                    accuracyM = accuracyM,
                    speedMps = 13f,
                    bearingDeg = bearingDeg,
                ),
        )
}
