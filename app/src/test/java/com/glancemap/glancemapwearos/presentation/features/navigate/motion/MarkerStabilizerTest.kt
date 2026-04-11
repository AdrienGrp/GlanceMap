package com.glancemap.glancemapwearos.presentation.features.navigate.motion

import com.glancemap.glancemapwearos.presentation.features.navigate.moveLatLong
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mapsforge.core.model.LatLong
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MarkerStabilizerTest {
    @Test
    fun suppressesStreetSideJitterWhenNearlyStationary() {
        val stabilizer = MarkerStabilizer()
        val base = LatLong(48.8566, 2.3522)
        val jitter = moveLatLong(base, bearing = 90f, distanceMeters = 3f)

        val first =
            stabilizer.onGpsFix(
                candidate = base,
                nowElapsedMs = 10_000L,
                fixElapsedMs = 10_000L,
                accuracyM = 8f,
                speedMps = 0.1f,
            )
        val second =
            stabilizer.onGpsFix(
                candidate = jitter,
                nowElapsedMs = 11_000L,
                fixElapsedMs = 11_000L,
                accuracyM = 8f,
                speedMps = 0.1f,
            )

        assertTrue(distanceMeters(first, second) < 0.2f)
    }

    @Test
    fun freezesOnOutlierJump() {
        val stabilizer = MarkerStabilizer()
        val base = LatLong(48.8566, 2.3522)
        val outlier = moveLatLong(base, bearing = 45f, distanceMeters = 120f)

        val first =
            stabilizer.onGpsFix(
                candidate = base,
                nowElapsedMs = 20_000L,
                fixElapsedMs = 20_000L,
                accuracyM = 6f,
                speedMps = 1f,
            )
        val second =
            stabilizer.onGpsFix(
                candidate = outlier,
                nowElapsedMs = 21_500L,
                fixElapsedMs = 21_500L,
                accuracyM = 25f,
                speedMps = 0.5f,
            )

        assertTrue(distanceMeters(first, second) < 0.5f)
    }

    @Test
    fun blendsTowardValidMoveInsteadOfTeleporting() {
        val stabilizer = MarkerStabilizer()
        val base = LatLong(48.8566, 2.3522)
        val target = moveLatLong(base, bearing = 0f, distanceMeters = 20f)

        stabilizer.onGpsFix(
            candidate = base,
            nowElapsedMs = 30_000L,
            fixElapsedMs = 30_000L,
            accuracyM = 9f,
            speedMps = 1.2f,
        )
        val blended =
            stabilizer.onGpsFix(
                candidate = target,
                nowElapsedMs = 32_000L,
                fixElapsedMs = 32_000L,
                accuracyM = 10f,
                speedMps = 1.2f,
            )

        val advancedMeters = distanceMeters(base, blended)
        val remainingMeters = distanceMeters(blended, target)
        assertTrue(advancedMeters > 1f)
        assertTrue(remainingMeters > 1f)
        assertTrue(advancedMeters < 20f)
    }

    @Test
    fun keepsUpdatingWhenFixAccuracyIsPoorButFresh() {
        val stabilizer = MarkerStabilizer()
        val base = LatLong(48.8566, 2.3522)
        val poor = moveLatLong(base, bearing = 180f, distanceMeters = 25f)

        val first =
            stabilizer.onGpsFix(
                candidate = base,
                nowElapsedMs = 40_000L,
                fixElapsedMs = 40_000L,
                accuracyM = 8f,
                speedMps = 0.6f,
            )
        val second =
            stabilizer.onGpsFix(
                candidate = poor,
                nowElapsedMs = 41_500L,
                fixElapsedMs = 41_500L,
                accuracyM = 60f,
                speedMps = 0.6f,
            )

        assertTrue(distanceMeters(first, second) > 0.8f)
    }

    @Test
    fun freezesWhenFixTimestampIsTooStale() {
        val stabilizer = MarkerStabilizer()
        val base = LatLong(48.8566, 2.3522)
        val stale = moveLatLong(base, bearing = 160f, distanceMeters = 30f)

        val first =
            stabilizer.onGpsFix(
                candidate = base,
                nowElapsedMs = 50_000L,
                fixElapsedMs = 50_000L,
                accuracyM = 8f,
                speedMps = 0.8f,
            )
        val second =
            stabilizer.onGpsFix(
                candidate = stale,
                nowElapsedMs = 65_000L,
                fixElapsedMs = 55_500L,
                accuracyM = 12f,
                speedMps = 0.8f,
            )

        assertTrue(distanceMeters(first, second) < 0.5f)
    }

    @Test
    fun blendsLargeConfidentCorrectionAfterLongGap() {
        val stabilizer = MarkerStabilizer()
        val base = LatLong(48.8566, 2.3522)
        val farTarget = moveLatLong(base, bearing = 35f, distanceMeters = 60f)

        stabilizer.onGpsFix(
            candidate = base,
            nowElapsedMs = 45_000L,
            fixElapsedMs = 45_000L,
            accuracyM = 8f,
            speedMps = 0.5f,
        )
        val snapped =
            stabilizer.onGpsFix(
                candidate = farTarget,
                nowElapsedMs = 60_000L,
                fixElapsedMs = 60_000L,
                accuracyM = 10f,
                speedMps = 0.4f,
            )

        val movedMeters = distanceMeters(base, snapped)
        val remainingMeters = distanceMeters(snapped, farTarget)
        assertTrue(movedMeters > 1f)
        assertTrue(remainingMeters > 1f)
    }

    @Test
    fun snapsModerateConfidentCorrectionWithinWakeWindow() {
        val stabilizer = MarkerStabilizer()
        val base = LatLong(48.8566, 2.3522)
        val target = moveLatLong(base, bearing = 35f, distanceMeters = 28f)

        stabilizer.onGpsFix(
            candidate = base,
            nowElapsedMs = 45_000L,
            fixElapsedMs = 45_000L,
            accuracyM = 12f,
            speedMps = 0.5f,
        )
        val snapped =
            stabilizer.onGpsFix(
                candidate = target,
                nowElapsedMs = 53_000L,
                fixElapsedMs = 53_000L,
                accuracyM = 12f,
                speedMps = 0.4f,
            )

        assertTrue(distanceMeters(snapped, target) < 0.5f)
    }

    @Test
    fun keepsSmoothingForLargeCorrectionWhenGapIsShort() {
        val stabilizer = MarkerStabilizer()
        val base = LatLong(48.8566, 2.3522)
        val farTarget = moveLatLong(base, bearing = 20f, distanceMeters = 30f)

        stabilizer.onGpsFix(
            candidate = base,
            nowElapsedMs = 70_000L,
            fixElapsedMs = 70_000L,
            accuracyM = 8f,
            speedMps = 1.2f,
        )
        val smoothed =
            stabilizer.onGpsFix(
                candidate = farTarget,
                nowElapsedMs = 74_000L,
                fixElapsedMs = 74_000L,
                accuracyM = 8f,
                speedMps = 1.4f,
            )

        val movedMeters = distanceMeters(base, smoothed)
        val remainingMeters = distanceMeters(smoothed, farTarget)
        assertTrue(movedMeters > 1f)
        assertTrue(remainingMeters > 1f)
    }

    @Test
    fun stopsPredictionDriftAfterLongGpsGap() {
        val stabilizer = MarkerStabilizer()
        val base = LatLong(48.8566, 2.3522)
        val prediction = moveLatLong(base, bearing = 270f, distanceMeters = 15f)

        val first =
            stabilizer.onGpsFix(
                candidate = base,
                nowElapsedMs = 50_000L,
                fixElapsedMs = 50_000L,
                accuracyM = 8f,
                speedMps = 0.4f,
            )
        val second =
            stabilizer.onPrediction(
                candidate = prediction,
                nowElapsedMs = 61_000L,
            )

        assertTrue(distanceMeters(first, second) < 0.5f)
    }

    @Test
    fun predictionAdvancesSoonerWhenWalking() {
        val stabilizer = MarkerStabilizer()
        val base = LatLong(48.8566, 2.3522)

        stabilizer.onGpsFix(
            candidate = base,
            nowElapsedMs = 60_000L,
            fixElapsedMs = 60_000L,
            accuracyM = 12f,
            speedMps = 1.2f,
        )
        val predicted =
            stabilizer.onPrediction(
                candidate = moveLatLong(base, bearing = 0f, distanceMeters = 1.6f),
                nowElapsedMs = 60_900L,
            )

        assertTrue(distanceMeters(base, predicted) > 0.3f)
    }

    @Test
    fun walkingPredictionAdvancesOnOneMeterStep() {
        val stabilizer = MarkerStabilizer()
        val base = LatLong(48.8566, 2.3522)

        stabilizer.onGpsFix(
            candidate = base,
            nowElapsedMs = 60_000L,
            fixElapsedMs = 60_000L,
            accuracyM = 12f,
            speedMps = 1.2f,
        )
        val predicted =
            stabilizer.onPrediction(
                candidate = moveLatLong(base, bearing = 0f, distanceMeters = 1.0f),
                nowElapsedMs = 60_700L,
            )

        assertTrue(distanceMeters(base, predicted) > 0.15f)
    }

    @Test
    fun clampsIndoorDriftWhenStationaryAndAccuracyIsPoor() {
        val stabilizer = MarkerStabilizer()
        val base = LatLong(48.8566, 2.3522)
        val drift = moveLatLong(base, bearing = 120f, distanceMeters = 10f)

        val first =
            stabilizer.onGpsFix(
                candidate = base,
                nowElapsedMs = 80_000L,
                fixElapsedMs = 80_000L,
                accuracyM = 8f,
                speedMps = 0.1f,
            )
        val second =
            stabilizer.onGpsFix(
                candidate = drift,
                nowElapsedMs = 81_500L,
                fixElapsedMs = 81_500L,
                accuracyM = 20f,
                speedMps = 0.2f,
            )

        assertTrue(distanceMeters(first, second) < 0.5f)
    }

    @Test
    fun doesNotClampLargeStationaryOffsetThatExceedsDriftWindow() {
        val stabilizer = MarkerStabilizer()
        val base = LatLong(48.8566, 2.3522)
        val far = moveLatLong(base, bearing = 300f, distanceMeters = 30f)

        stabilizer.onGpsFix(
            candidate = base,
            nowElapsedMs = 90_000L,
            fixElapsedMs = 90_000L,
            accuracyM = 10f,
            speedMps = 0.2f,
        )
        val moved =
            stabilizer.onGpsFix(
                candidate = far,
                nowElapsedMs = 115_000L,
                fixElapsedMs = 115_000L,
                accuracyM = 24f,
                speedMps = 0.3f,
            )

        assertTrue(distanceMeters(base, moved) > 0.8f)
    }

    @Test
    fun seededAnchorAllowsFirstWakeFixToBlendFromPassivePosition() {
        val stabilizer = MarkerStabilizer()
        val base = LatLong(48.8566, 2.3522)
        val target = moveLatLong(base, bearing = 45f, distanceMeters = 20f)

        stabilizer.seedAnchor(
            latLong = base,
            fixElapsedMs = 100_000L,
            accuracyM = 8f,
            speedMps = 1.2f,
        )
        val blended =
            stabilizer.onGpsFix(
                candidate = target,
                nowElapsedMs = 102_000L,
                fixElapsedMs = 102_000L,
                accuracyM = 8f,
                speedMps = 1.2f,
            )

        assertTrue(distanceMeters(base, blended) > 1f)
        assertTrue(distanceMeters(blended, target) > 1f)
    }
}

private const val EARTH_RADIUS_METERS = 6_371_000.0

private fun distanceMeters(
    a: LatLong,
    b: LatLong,
): Float {
    val lat1 = Math.toRadians(a.latitude)
    val lon1 = Math.toRadians(a.longitude)
    val lat2 = Math.toRadians(b.latitude)
    val lon2 = Math.toRadians(b.longitude)
    val dLat = lat2 - lat1
    val dLon = lon2 - lon1

    val sinHalfLat = sin(dLat / 2.0)
    val sinHalfLon = sin(dLon / 2.0)
    val h = sinHalfLat * sinHalfLat + cos(lat1) * cos(lat2) * sinHalfLon * sinHalfLon
    val c = 2.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    return (EARTH_RADIUS_METERS * c).toFloat()
}
