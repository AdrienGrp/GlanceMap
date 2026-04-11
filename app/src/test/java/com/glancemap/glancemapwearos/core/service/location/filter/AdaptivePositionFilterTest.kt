package com.glancemap.glancemapwearos.core.service.location.filter

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePositionFilterTest {

    @Test
    fun preservesSubThreeMeterFixConfidenceWithoutInflatingToOldFloor() {
        val filter = AdaptivePositionFilter()

        val estimate = filter.update(
            measurement(
                xMeters = 0.0,
                yMeters = 0.0,
                elapsedMs = 1_000L,
                accuracyMeters = 1.4f
            )
        )

        assertEquals(2.0f, estimate.positionStdDevMeters, 0.001f)
    }

    @Test
    fun reducesStationaryJitterAcrossRepeatedFixes() {
        val filter = AdaptivePositionFilter()
        val outputs = listOf(
            filter.update(measurement(xMeters = -6.0, yMeters = 1.2, elapsedMs = 1_000L)),
            filter.update(measurement(xMeters = 5.5, yMeters = -1.0, elapsedMs = 4_000L)),
            filter.update(measurement(xMeters = -5.8, yMeters = 0.8, elapsedMs = 7_000L)),
            filter.update(measurement(xMeters = 6.2, yMeters = -0.9, elapsedMs = 10_000L)),
            filter.update(measurement(xMeters = -5.1, yMeters = 0.6, elapsedMs = 13_000L))
        )

        val outputSpreadMeters = outputs.maxOf { it.xMeters } - outputs.minOf { it.xMeters }
        assertTrue("outputSpreadMeters=$outputSpreadMeters", outputSpreadMeters < 10.0)
        val finalEstimate = outputs.last()
        assertTrue("finalX=${finalEstimate.xMeters}", abs(finalEstimate.xMeters) < 4.0)
        assertTrue("finalY=${finalEstimate.yMeters}", abs(finalEstimate.yMeters) < 2.0)
    }

    @Test
    fun tracksWalkingProgressWithoutLargeLag() {
        val filter = AdaptivePositionFilter()
        var estimate = filter.update(
            measurement(
                xMeters = 0.0,
                yMeters = 0.0,
                elapsedMs = 1_000L,
                speedMps = 1.4f
            )
        )
        val samples = listOf(
            4.6 to 3_000L,
            8.1 to 6_000L,
            12.8 to 9_000L,
            16.5 to 12_000L,
            20.9 to 15_000L
        )
        for ((yMeters, elapsedMs) in samples) {
            estimate = filter.update(
                measurement(
                    xMeters = if (elapsedMs % 2L == 0L) 0.7 else -0.6,
                    yMeters = yMeters,
                    elapsedMs = elapsedMs,
                    speedMps = 1.4f
                )
            )
        }

        assertTrue("finalY=${estimate.yMeters}", abs(estimate.yMeters - 20.9) < 4.0)
        assertTrue("finalX=${estimate.xMeters}", abs(estimate.xMeters) < 3.0)
        assertTrue("speed=${estimate.speedMps}", estimate.speedMps > 0.5f)
    }

    @Test
    fun downweightsLargePoorAccuracyOutlier() {
        val filter = AdaptivePositionFilter()
        filter.update(measurement(xMeters = 0.0, yMeters = 0.0, elapsedMs = 1_000L, accuracyMeters = 8f))
        filter.update(measurement(xMeters = 1.5, yMeters = 0.8, elapsedMs = 4_000L, accuracyMeters = 8f))
        filter.update(measurement(xMeters = 2.8, yMeters = 1.1, elapsedMs = 7_000L, accuracyMeters = 8f))

        val outlier = filter.update(
            measurement(
                xMeters = 68.0,
                yMeters = 4.0,
                elapsedMs = 10_000L,
                accuracyMeters = 35f,
                speedMps = 0.8f
            )
        )

        assertTrue("outlierX=${outlier.xMeters}", outlier.xMeters < 35.0)
        assertTrue("outlierY=${outlier.yMeters}", outlier.yMeters < 10.0)
    }

    private fun measurement(
        xMeters: Double,
        yMeters: Double,
        elapsedMs: Long,
        accuracyMeters: Float = 12f,
        speedMps: Float? = null
    ): PositionMeasurement {
        return PositionMeasurement(
            xMeters = xMeters,
            yMeters = yMeters,
            accuracyMeters = accuracyMeters,
            elapsedMs = elapsedMs,
            speedMps = speedMps
        )
    }
}
