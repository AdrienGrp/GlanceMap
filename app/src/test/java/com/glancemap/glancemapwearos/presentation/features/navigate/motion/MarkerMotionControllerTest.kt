package com.glancemap.glancemapwearos.presentation.features.navigate.motion

import com.glancemap.glancemapwearos.presentation.features.navigate.moveLatLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mapsforge.core.model.LatLong
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MarkerMotionControllerTest {
    @Test
    fun predictsForwardFromDerivedMotionWhenRawSpeedIsMissing() {
        MarkerMotionTelemetry.clear()
        val controller = MarkerMotionController(predictionFreshnessMaxAgeMs = 4_500L, maxAcceptedFixAgeMs = 6_000L)
        val base = LatLong(48.8566, 2.3522)
        val walkingFix = moveLatLong(base, bearing = 90f, distanceMeters = 3f)

        controller.onGpsFix(
            latLong = base,
            nowElapsedMs = 10_000L,
            fixElapsedMs = 10_000L,
            accuracyM = 8f,
            rawSpeedMps = 0f,
            rawBearingDeg = null,
        )
        controller.onGpsFix(
            latLong = walkingFix,
            nowElapsedMs = 13_000L,
            fixElapsedMs = 13_000L,
            accuracyM = 8f,
            rawSpeedMps = 0f,
            rawBearingDeg = null,
        )

        val predicted =
            controller.predict(
                nowElapsedMs = 14_000L,
                serviceFreshnessMaxAgeMs = 4_500L,
                watchGpsDegraded = false,
            ) ?: walkingFix

        assertTrue(distanceMeters(walkingFix, predicted) > 0.4f)
        assertTrue(predicted.longitude > walkingFix.longitude)
        assertEquals(MarkerMotionMode.PREDICT, MarkerMotionTelemetry.latestSnapshot().mode)
    }

    @Test
    fun suppressesPredictionWhenAccuracyIsPoor() {
        MarkerMotionTelemetry.clear()
        val controller = MarkerMotionController(predictionFreshnessMaxAgeMs = 4_500L, maxAcceptedFixAgeMs = 6_000L)
        val base = LatLong(48.8566, 2.3522)

        controller.onGpsFix(
            latLong = base,
            nowElapsedMs = 20_000L,
            fixElapsedMs = 20_000L,
            accuracyM = 32f,
            rawSpeedMps = 1.5f,
            rawBearingDeg = 90f,
        )

        val predicted =
            controller.predict(
                nowElapsedMs = 21_000L,
                serviceFreshnessMaxAgeMs = 4_500L,
                watchGpsDegraded = false,
            ) ?: base

        assertTrue(distanceMeters(base, predicted) < 0.2f)
        val snapshot = MarkerMotionTelemetry.latestSnapshot()
        assertEquals(MarkerMotionMode.FIXED, snapshot.mode)
        assertEquals("bad_accuracy", snapshot.reason)
    }

    @Test
    fun freezesStationaryJitterWithinAccuracyBubble() {
        MarkerMotionTelemetry.clear()
        val controller = MarkerMotionController(predictionFreshnessMaxAgeMs = 4_500L, maxAcceptedFixAgeMs = 6_000L)
        val base = LatLong(48.8566, 2.3522)
        val jitter = moveLatLong(base, bearing = 90f, distanceMeters = 3f)

        val first =
            controller.onGpsFix(
                latLong = base,
                nowElapsedMs = 30_000L,
                fixElapsedMs = 30_000L,
                accuracyM = 8f,
                rawSpeedMps = 0.1f,
                rawBearingDeg = null,
            )
        val second =
            controller.onGpsFix(
                latLong = jitter,
                nowElapsedMs = 31_000L,
                fixElapsedMs = 31_000L,
                accuracyM = 8f,
                rawSpeedMps = 0.1f,
                rawBearingDeg = null,
            )

        assertTrue(distanceMeters(first, second) < 0.2f)
        assertEquals(2, MarkerMotionTelemetry.summary().acceptedFixes)
    }

    @Test
    fun blendsCorrectionInsteadOfTeleporting() {
        MarkerMotionTelemetry.clear()
        val controller = MarkerMotionController(predictionFreshnessMaxAgeMs = 4_500L, maxAcceptedFixAgeMs = 6_000L)
        val base = LatLong(48.8566, 2.3522)
        val target = moveLatLong(base, bearing = 0f, distanceMeters = 20f)

        controller.onGpsFix(
            latLong = base,
            nowElapsedMs = 40_000L,
            fixElapsedMs = 40_000L,
            accuracyM = 8f,
            rawSpeedMps = 1.2f,
            rawBearingDeg = 0f,
        )
        val blendStart =
            controller.onGpsFix(
                latLong = target,
                nowElapsedMs = 42_000L,
                fixElapsedMs = 42_000L,
                accuracyM = 8f,
                rawSpeedMps = 1.2f,
                rawBearingDeg = 0f,
            )

        assertTrue(distanceMeters(blendStart, base) < 0.5f)

        val settled =
            controller.predict(
                nowElapsedMs = 42_400L,
                serviceFreshnessMaxAgeMs = 4_500L,
                watchGpsDegraded = false,
            ) ?: target

        val remainingMeters = distanceMeters(settled, target)
        assertTrue("remainingMeters=$remainingMeters", remainingMeters < 2f)
        assertEquals(1, MarkerMotionTelemetry.summary().blendStarts)
    }

    @Test
    fun dropsOutlierJump() {
        MarkerMotionTelemetry.clear()
        val controller = MarkerMotionController(predictionFreshnessMaxAgeMs = 4_500L, maxAcceptedFixAgeMs = 6_000L)
        val base = LatLong(48.8566, 2.3522)
        val outlier = moveLatLong(base, bearing = 45f, distanceMeters = 120f)

        val first =
            controller.onGpsFix(
                latLong = base,
                nowElapsedMs = 50_000L,
                fixElapsedMs = 50_000L,
                accuracyM = 6f,
                rawSpeedMps = 1f,
                rawBearingDeg = 90f,
            )
        val second =
            controller.onGpsFix(
                latLong = outlier,
                nowElapsedMs = 51_500L,
                fixElapsedMs = 51_500L,
                accuracyM = 25f,
                rawSpeedMps = 0.5f,
                rawBearingDeg = 45f,
            )

        assertTrue(distanceMeters(first, second) < 0.5f)
        val summary = MarkerMotionTelemetry.summary()
        assertEquals(1, summary.outlierDrops)
        assertEquals("outlier_drop", MarkerMotionTelemetry.latestSnapshot().reason)
    }

    @Test
    fun clampsLargeCorrectionDuringNormalInteractiveWalking() {
        MarkerMotionTelemetry.clear()
        val controller = MarkerMotionController(predictionFreshnessMaxAgeMs = 4_500L, maxAcceptedFixAgeMs = 6_000L)
        val base = LatLong(48.8566, 2.3522)
        val farTarget = moveLatLong(base, bearing = 90f, distanceMeters = 40f)

        controller.onGpsFix(
            latLong = base,
            nowElapsedMs = 60_000L,
            fixElapsedMs = 60_000L,
            accuracyM = 10f,
            rawSpeedMps = 1.2f,
            rawBearingDeg = 90f,
        )
        val initialDisplay =
            controller.onGpsFix(
                latLong = farTarget,
                nowElapsedMs = 63_000L,
                fixElapsedMs = 63_000L,
                accuracyM = 22f,
                rawSpeedMps = 1.4f,
                rawBearingDeg = 90f,
            )

        val settled =
            controller.predict(
                nowElapsedMs = 63_400L,
                serviceFreshnessMaxAgeMs = 4_500L,
                watchGpsDegraded = false,
            ) ?: farTarget

        assertTrue(distanceMeters(initialDisplay, settled) < 20f)
        assertTrue(distanceMeters(settled, farTarget) > 10f)
        assertEquals(1, MarkerMotionTelemetry.summary().clampedCorrections)
    }

    @Test
    fun bypassesClampForWakeStyleCatchUpCorrection() {
        MarkerMotionTelemetry.clear()
        val controller = MarkerMotionController(predictionFreshnessMaxAgeMs = 4_500L, maxAcceptedFixAgeMs = 6_000L)
        val base = LatLong(48.8566, 2.3522)
        val farTarget = moveLatLong(base, bearing = 0f, distanceMeters = 40f)

        controller.onGpsFix(
            latLong = base,
            nowElapsedMs = 70_000L,
            fixElapsedMs = 70_000L,
            accuracyM = 10f,
            rawSpeedMps = 1.2f,
            rawBearingDeg = 0f,
        )
        controller.onGpsFix(
            latLong = farTarget,
            nowElapsedMs = 78_000L,
            fixElapsedMs = 78_000L,
            accuracyM = 22f,
            rawSpeedMps = 1.4f,
            rawBearingDeg = 0f,
            allowLargeCorrection = true,
        )

        val settled =
            controller.predict(
                nowElapsedMs = 78_400L,
                serviceFreshnessMaxAgeMs = 4_500L,
                watchGpsDegraded = false,
            ) ?: farTarget

        assertTrue(distanceMeters(settled, farTarget) < 2f)
        assertEquals(0, MarkerMotionTelemetry.summary().clampedCorrections)
    }

    @Test
    fun clampsModerateAccuracyLargeCorrection() {
        MarkerMotionTelemetry.clear()
        val controller = MarkerMotionController(predictionFreshnessMaxAgeMs = 4_500L, maxAcceptedFixAgeMs = 6_000L)
        val base = LatLong(48.8566, 2.3522)
        val farTarget = moveLatLong(base, bearing = 180f, distanceMeters = 28f)

        controller.onGpsFix(
            latLong = base,
            nowElapsedMs = 90_000L,
            fixElapsedMs = 90_000L,
            accuracyM = 10f,
            rawSpeedMps = 1.1f,
            rawBearingDeg = 180f,
        )
        controller.onGpsFix(
            latLong = farTarget,
            nowElapsedMs = 93_200L,
            fixElapsedMs = 93_200L,
            accuracyM = 15f,
            rawSpeedMps = 1.2f,
            rawBearingDeg = 180f,
        )

        val settled =
            controller.predict(
                nowElapsedMs = 93_600L,
                serviceFreshnessMaxAgeMs = 4_500L,
                watchGpsDegraded = false,
            ) ?: farTarget

        assertTrue(distanceMeters(settled, farTarget) > 5f)
        assertEquals(1, MarkerMotionTelemetry.summary().clampedCorrections)
    }

    @Test
    fun clampsVeryLargeCorrectionEvenWhenReportedAccuracyLooksGood() {
        MarkerMotionTelemetry.clear()
        val controller = MarkerMotionController(predictionFreshnessMaxAgeMs = 4_500L, maxAcceptedFixAgeMs = 6_000L)
        val base = LatLong(48.8566, 2.3522)
        val farTarget = moveLatLong(base, bearing = 180f, distanceMeters = 40f)

        controller.onGpsFix(
            latLong = base,
            nowElapsedMs = 95_000L,
            fixElapsedMs = 95_000L,
            accuracyM = 10f,
            rawSpeedMps = 1.2f,
            rawBearingDeg = 180f,
        )
        controller.onGpsFix(
            latLong = farTarget,
            nowElapsedMs = 98_100L,
            fixElapsedMs = 98_100L,
            accuracyM = 13.1f,
            rawSpeedMps = 1.33f,
            rawBearingDeg = 188.2f,
        )

        val settled =
            controller.predict(
                nowElapsedMs = 98_500L,
                serviceFreshnessMaxAgeMs = 4_500L,
                watchGpsDegraded = false,
            ) ?: farTarget

        assertTrue(distanceMeters(settled, farTarget) > 15f)
        assertEquals(1, MarkerMotionTelemetry.summary().clampedCorrections)
    }
}

private fun distanceMeters(
    from: LatLong,
    to: LatLong,
): Float {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(to.longitude - from.longitude)
    val a =
        sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(lat1) * cos(lat2) * sin(dLon / 2.0) * sin(dLon / 2.0)
    val c = 2.0 * asin(sqrt(a))
    return (6_371_000.0 * c).toFloat()
}
