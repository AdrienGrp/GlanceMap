package com.glancemap.glancemapwearos.presentation.features.navigate.motion

import com.glancemap.glancemapwearos.presentation.features.navigate.moveLatLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mapsforge.core.model.LatLong
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MarkerVisualTrajectoryTest {
    private val base = LatLong(48.8566, 2.3522)
    private val window =
        MarkerPredictionWindow(
            startDelayMs = 50L,
            fullSpeedUntilMs = 3_000L,
            stopAtMs = 4_500L,
        )

    @Test
    fun rebaseStartsAtAlreadyDisplayedPosition() {
        val trajectory = MarkerVisualTrajectory()
        val target = moveLatLong(base, bearing = 90f, distanceMeters = 10f)

        trajectory.rebase(
            anchor = movingAnchor(target, fixElapsedMs = 10_000L),
            displayedAtRebase = base,
            nowElapsedMs = 10_000L,
            predictionWindow = window,
            correctionPlan = MarkerVisualCorrectionPlan(durationMs = 1_200L),
        )

        val sample = trajectory.sample(10_000L, window) ?: error("Expected trajectory sample")

        assertTrue(distanceMeters(base, sample.latLong) < 0.02f)
        assertTrue(sample.correctionActive)
        assertTrue(sample.correctionRemainingM > 9.9f)
    }

    @Test
    fun correctionDecaysWhileForwardMotionContinues() {
        val trajectory = MarkerVisualTrajectory()
        val target = moveLatLong(base, bearing = 90f, distanceMeters = 10f)
        trajectory.rebase(
            anchor = movingAnchor(target, fixElapsedMs = 10_000L),
            displayedAtRebase = base,
            nowElapsedMs = 10_000L,
            predictionWindow = window,
            correctionPlan = MarkerVisualCorrectionPlan(durationMs = 1_200L),
        )

        val early = trajectory.sample(10_100L, window) ?: error("Expected early sample")
        val midpoint = trajectory.sample(10_600L, window) ?: error("Expected midpoint sample")

        assertTrue(distanceMeters(base, midpoint.latLong) > distanceMeters(base, early.latLong))
        assertTrue(midpoint.correctionRemainingM < early.correctionRemainingM)
        assertTrue(midpoint.correctionRemainingM > 5f)
        assertTrue(midpoint.predictedDistanceM > 0.5f)
        assertTrue(midpoint.correctionActive)
    }

    @Test
    fun replacingCorrectionReportsItsActualInterruptedDuration() {
        val trajectory = MarkerVisualTrajectory()
        val firstTarget = moveLatLong(base, bearing = 90f, distanceMeters = 10f)
        trajectory.rebase(
            anchor = movingAnchor(firstTarget, fixElapsedMs = 10_000L),
            displayedAtRebase = base,
            nowElapsedMs = 10_000L,
            predictionWindow = window,
            correctionPlan = MarkerVisualCorrectionPlan(durationMs = 1_200L, reason = "first"),
        )

        val interrupted =
            trajectory.rebase(
                anchor = movingAnchor(firstTarget, fixElapsedMs = 10_600L),
                displayedAtRebase = base,
                nowElapsedMs = 10_600L,
                predictionWindow = window,
                correctionPlan = MarkerVisualCorrectionPlan(durationMs = 1_200L, reason = "second"),
            ) ?: error("Expected interrupted correction")

        assertEquals("first", interrupted.reason)
        assertEquals(600L, interrupted.durationMs)
    }

    @Test
    fun rateLimitedCorrectionCanExtendPastNominalDurationAndStillConverge() {
        val trajectory = MarkerVisualTrajectory()
        val target = moveLatLong(base, bearing = 90f, distanceMeters = 5f)
        trajectory.rebase(
            anchor = movingAnchor(target, fixElapsedMs = 10_000L),
            displayedAtRebase = base,
            nowElapsedMs = 10_000L,
            predictionWindow = window,
            correctionPlan = MarkerVisualCorrectionPlan(durationMs = 1_200L),
        )

        val samples =
            (0..45).map { index ->
                val sampleTime = 10_000L + index * 100L
                trajectory.sample(sampleTime, window) ?: error("Expected sample at $sampleTime")
            }
        val nominalEnd = samples[12]
        val finished = samples.last()

        assertTrue(nominalEnd.correctionActive)
        assertFalse(finished.correctionActive)
        assertTrue(distanceMeters(finished.latLong, finished.baseLatLong) < 0.02f)
        assertTrue(distanceMeters(target, finished.latLong) > 1f)
        assertFalse(trajectory.hasActiveCorrection(14_500L, window))
    }

    @Test
    fun displayedPointAheadOfNewAnchorNeverMovesBackward() {
        val trajectory = MarkerVisualTrajectory()
        val displayedAhead = moveLatLong(base, bearing = 90f, distanceMeters = 5f)
        trajectory.rebase(
            anchor = movingAnchor(base, fixElapsedMs = 10_000L),
            displayedAtRebase = displayedAhead,
            nowElapsedMs = 10_000L,
            predictionWindow = window,
            correctionPlan = MarkerVisualCorrectionPlan(durationMs = 600L),
        )

        val sampleTimes = (0..45).map { index -> 10_000L + index * 100L }
        val samples =
            sampleTimes.map { sampleTime ->
                trajectory.sample(sampleTime, window) ?: error("Expected sample at $sampleTime")
            }

        samples.zipWithNext().forEachIndexed { index, (previous, next) ->
            assertTrue(
                "Marker reversed between ${sampleTimes[index]} and ${sampleTimes[index + 1]}",
                next.latLong.longitude >= previous.latLong.longitude,
            )
        }
        assertTrue(samples[6].correctionActive)
        assertFalse(samples.last().correctionActive)
        assertTrue(distanceMeters(samples.last().latLong, samples.last().baseLatLong) < 0.02f)
    }

    @Test
    fun leadingCorrectionPreservesMostForwardMotion() {
        val trajectory = MarkerVisualTrajectory()
        val displayedAhead = moveLatLong(base, bearing = 90f, distanceMeters = 5f)
        trajectory.rebase(
            anchor = movingAnchor(base, fixElapsedMs = 10_000L),
            displayedAtRebase = displayedAhead,
            nowElapsedMs = 10_000L,
            predictionWindow = window,
            correctionPlan = MarkerVisualCorrectionPlan(durationMs = 2_400L),
        )

        val initial = trajectory.sample(10_000L, window) ?: error("Expected initial sample")
        val afterOneTick = trajectory.sample(10_100L, window) ?: error("Expected next sample")

        // After the 50 ms prediction guard, the 2 m/s base trajectory has advanced 0.1 m. A
        // cadence-length correction must retain most of that movement rather than visibly pausing.
        assertTrue(distanceMeters(initial.latLong, afterOneTick.latLong) > 0.05f)
    }

    @Test
    fun predictionEasesToAStableStopAtHorizon() {
        val trajectory = MarkerVisualTrajectory()
        trajectory.seed(movingAnchor(base, fixElapsedMs = 10_000L, speedMps = 2f))

        val beforeEase = trajectory.sample(13_000L, window) ?: error("Expected pre-ease sample")
        val duringEase = trajectory.sample(13_750L, window) ?: error("Expected easing sample")
        val atHorizon = trajectory.sample(14_500L, window) ?: error("Expected horizon sample")
        val afterHorizon = trajectory.sample(20_000L, window) ?: error("Expected capped sample")

        assertTrue(distanceMeters(base, beforeEase.latLong) < distanceMeters(base, duringEase.latLong))
        assertTrue(distanceMeters(base, duringEase.latLong) < distanceMeters(base, atHorizon.latLong))
        assertTrue(distanceMeters(atHorizon.latLong, afterHorizon.latLong) < 0.02f)
    }

    private fun movingAnchor(
        latLong: LatLong,
        fixElapsedMs: Long,
        speedMps: Float = 2f,
    ): MarkerVisualAnchor =
        MarkerVisualAnchor(
            latLong = latLong,
            fixElapsedMs = fixElapsedMs,
            accuracyM = 6f,
            speedMps = speedMps,
            bearingDeg = 90f,
        )
}

private fun distanceMeters(
    from: LatLong,
    to: LatLong,
): Float {
    val earthRadiusM = 6_371_000.0
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(to.longitude - from.longitude)
    val a =
        sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(lat1) * cos(lat2) * sin(dLon / 2.0) * sin(dLon / 2.0)
    return (earthRadiusM * 2.0 * asin(sqrt(a))).toFloat()
}
