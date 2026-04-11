package com.glancemap.glancemapwearos.presentation.features.navigate.motion

import android.hardware.SensorManager
import com.glancemap.glancemapwearos.domain.sensors.CompassProviderType
import com.glancemap.glancemapwearos.domain.sensors.HeadingSource
import com.glancemap.glancemapwearos.domain.sensors.initialCompassRenderState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LowConfidenceCompassPredictionOverrideTrackerTest {
    @Test
    fun activatesAfterStableFreshLowBandFusedSamples() {
        val tracker = LowConfidenceCompassPredictionOverrideTracker()
        val baseState = initialCompassRenderState(CompassProviderType.GOOGLE_FUSED)
        var evaluation = tracker.reset()

        listOf(1_000L, 2_400L, 3_800L, 5_200L).forEach { sampleAtMs ->
            evaluation =
                tracker.update(
                    renderState =
                        baseState.copy(
                            accuracy = SensorManager.SENSOR_STATUS_ACCURACY_LOW,
                            headingErrorDeg = 25f,
                            headingSampleElapsedRealtimeMs = sampleAtMs,
                            headingSampleStale = false,
                            headingSource = HeadingSource.FUSED_ORIENTATION,
                            magneticInterference = false,
                        ),
                    nowElapsedMs = sampleAtMs + 100L,
                )
        }

        assertTrue(evaluation.active)
        assertTrue(tracker.isActive)
        org.junit.Assert.assertEquals("stable_low_band", evaluation.reason)
    }

    @Test
    fun resetsWhenSamplesGoStaleOrInterferenceAppears() {
        val tracker = LowConfidenceCompassPredictionOverrideTracker()
        val baseState = initialCompassRenderState(CompassProviderType.GOOGLE_FUSED)

        listOf(1_000L, 2_400L, 3_800L, 5_200L).forEach { sampleAtMs ->
            tracker.update(
                renderState =
                    baseState.copy(
                        accuracy = SensorManager.SENSOR_STATUS_ACCURACY_LOW,
                        headingErrorDeg = 25f,
                        headingSampleElapsedRealtimeMs = sampleAtMs,
                        headingSampleStale = false,
                        headingSource = HeadingSource.FUSED_ORIENTATION,
                        magneticInterference = false,
                    ),
                nowElapsedMs = sampleAtMs + 100L,
            )
        }

        val staleReset =
            tracker.update(
                renderState =
                    baseState.copy(
                        accuracy = SensorManager.SENSOR_STATUS_ACCURACY_LOW,
                        headingErrorDeg = 25f,
                        headingSampleElapsedRealtimeMs = 5_200L,
                        headingSampleStale = true,
                        headingSource = HeadingSource.FUSED_ORIENTATION,
                        magneticInterference = false,
                    ),
                nowElapsedMs = 5_400L,
            )
        assertFalse(staleReset.active)
        assertFalse(tracker.isActive)
        org.junit.Assert.assertEquals("stale_sample", staleReset.reason)

        val reactivated =
            tracker.update(
                renderState =
                    baseState.copy(
                        accuracy = SensorManager.SENSOR_STATUS_ACCURACY_LOW,
                        headingErrorDeg = 25f,
                        headingSampleElapsedRealtimeMs = 10_000L,
                        headingSampleStale = false,
                        headingSource = HeadingSource.FUSED_ORIENTATION,
                        magneticInterference = false,
                    ),
                nowElapsedMs = 10_100L,
            )
        assertFalse(reactivated.active)
        org.junit.Assert.assertEquals("warming_up", reactivated.reason)

        val interferenceReset =
            tracker.update(
                renderState =
                    baseState.copy(
                        accuracy = SensorManager.SENSOR_STATUS_ACCURACY_LOW,
                        headingErrorDeg = 25f,
                        headingSampleElapsedRealtimeMs = 11_200L,
                        headingSampleStale = false,
                        headingSource = HeadingSource.FUSED_ORIENTATION,
                        magneticInterference = true,
                    ),
                nowElapsedMs = 11_300L,
            )

        assertFalse(interferenceReset.active)
        assertFalse(tracker.isActive)
        org.junit.Assert.assertEquals("magnetic_interference", interferenceReset.reason)
    }
}
