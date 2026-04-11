package com.glancemap.glancemapwearos.presentation.features.navigate.motion

import android.hardware.SensorManager
import com.glancemap.glancemapwearos.presentation.features.navigate.moveLatLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mapsforge.core.model.LatLong
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class LocationFusionEngineTest {
    @Test
    fun predictsForwardWhenSpeedAndAccuracyAreHealthy() {
        val engine = LocationFusionEngine()
        val base = LatLong(48.8566, 2.3522)

        engine.onGpsFix(
            latLong = base,
            speedMps = 1.4f,
            accuracyM = 8f,
            bearingDeg = 90f,
            nowElapsedMs = 10_000L,
        )
        val predicted = engine.predict(nowElapsedMs = 14_000L) ?: base

        assertTrue(distanceMeters(base, predicted) > 2f)
    }

    @Test
    fun beginsPredictionSoonerAfterFreshFix() {
        val engine = LocationFusionEngine()
        val base = LatLong(48.8566, 2.3522)

        engine.onGpsFix(
            latLong = base,
            speedMps = 1.4f,
            accuracyM = 8f,
            bearingDeg = 90f,
            nowElapsedMs = 10_000L,
        )
        val predicted = engine.predict(nowElapsedMs = 10_500L) ?: base

        assertTrue(distanceMeters(base, predicted) > 0.1f)
    }

    @Test
    fun beginsPredictionBeforeQuarterSecondForWalkingFix() {
        val engine = LocationFusionEngine()
        val base = LatLong(48.8566, 2.3522)

        engine.onGpsFix(
            latLong = base,
            speedMps = 1.2f,
            accuracyM = 8f,
            bearingDeg = 90f,
            nowElapsedMs = 10_000L,
        )
        val predicted = engine.predict(nowElapsedMs = 10_250L) ?: base

        assertTrue(distanceMeters(base, predicted) > 0.08f)
    }

    @Test
    fun usesFasterPredictionTickWhenWalking() {
        val engine = LocationFusionEngine()
        val base = LatLong(48.8566, 2.3522)

        engine.onGpsFix(
            latLong = base,
            speedMps = 1.0f,
            accuracyM = 8f,
            bearingDeg = 90f,
            nowElapsedMs = 10_000L,
        )

        assertEquals(250L, engine.suggestedPredictionTickMs())
    }

    @Test
    fun suppressesPredictionWhenSpeedIsNearlyStationary() {
        val engine = LocationFusionEngine()
        val base = LatLong(48.8566, 2.3522)

        engine.onGpsFix(
            latLong = base,
            speedMps = 0.2f,
            accuracyM = 8f,
            bearingDeg = 90f,
            nowElapsedMs = 20_000L,
        )
        val predicted = engine.predict(nowElapsedMs = 24_000L) ?: base

        assertTrue(distanceMeters(base, predicted) < 0.3f)
    }

    @Test
    fun suppressesPredictionWhenGpsAccuracyIsTooPoor() {
        val engine = LocationFusionEngine()
        val base = LatLong(48.8566, 2.3522)

        engine.onGpsFix(
            latLong = base,
            speedMps = 1.5f,
            accuracyM = 48f,
            bearingDeg = 90f,
            nowElapsedMs = 30_000L,
        )
        val predicted = engine.predict(nowElapsedMs = 34_000L) ?: base

        assertTrue(distanceMeters(base, predicted) < 0.3f)
    }

    @Test
    fun trustsCompassHeadingOnlyWhenConfidenceIsGood() {
        val base = LatLong(48.8566, 2.3522)

        val highConfidenceEngine = LocationFusionEngine()
        highConfidenceEngine.onHeadingAccuracy(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
        highConfidenceEngine.onMagneticInterference(false)
        highConfidenceEngine.onHeading(headingDeg = 90f, nowElapsedMs = 39_900L)
        highConfidenceEngine.onGpsFix(
            latLong = base,
            speedMps = 1.2f,
            accuracyM = 8f,
            bearingDeg = 0f,
            nowElapsedMs = 40_000L,
        )
        highConfidenceEngine.onHeading(headingDeg = 90f, nowElapsedMs = 43_900L)
        val predictedWithGoodCompass = highConfidenceEngine.predict(nowElapsedMs = 44_000L) ?: base

        val lowConfidenceEngine = LocationFusionEngine()
        lowConfidenceEngine.onHeadingAccuracy(SensorManager.SENSOR_STATUS_UNRELIABLE)
        lowConfidenceEngine.onMagneticInterference(true)
        lowConfidenceEngine.onHeading(headingDeg = 90f, nowElapsedMs = 39_900L)
        lowConfidenceEngine.onGpsFix(
            latLong = base,
            speedMps = 1.2f,
            accuracyM = 8f,
            bearingDeg = 0f,
            nowElapsedMs = 40_000L,
        )
        lowConfidenceEngine.onHeading(headingDeg = 90f, nowElapsedMs = 43_900L)
        val predictedWithPoorCompass = lowConfidenceEngine.predict(nowElapsedMs = 44_000L) ?: base

        assertTrue(predictedWithGoodCompass.longitude > predictedWithPoorCompass.longitude)
        assertTrue(distanceMeters(predictedWithGoodCompass, predictedWithPoorCompass) > 0.5f)
    }

    @Test
    fun snapsLargeConfidentCorrectionAfterStaleGap() {
        val engine = LocationFusionEngine()
        val base = LatLong(48.8566, 2.3522)
        val target = moveLatLong(base, bearing = 90f, distanceMeters = 60f)

        engine.onGpsFix(
            latLong = base,
            speedMps = 0.6f,
            accuracyM = 8f,
            bearingDeg = null,
            nowElapsedMs = 10_000L,
        )
        val snapped =
            engine.onGpsFix(
                latLong = target,
                speedMps = 0.5f,
                accuracyM = 8f,
                bearingDeg = null,
                nowElapsedMs = 26_000L,
            )

        assertTrue(distanceMeters(snapped, target) < 0.5f)
    }

    @Test
    fun usesShortBlendForLargeConfidentCorrectionWhenGapIsShort() {
        val engine = LocationFusionEngine()
        val base = LatLong(48.8566, 2.3522)
        val target = moveLatLong(base, bearing = 45f, distanceMeters = 40f)

        engine.onGpsFix(
            latLong = base,
            speedMps = 0.8f,
            accuracyM = 8f,
            bearingDeg = null,
            nowElapsedMs = 40_000L,
        )
        val blendStart =
            engine.onGpsFix(
                latLong = target,
                speedMps = 0.7f,
                accuracyM = 8f,
                bearingDeg = null,
                nowElapsedMs = 43_000L,
            )

        val startDistanceToTarget = distanceMeters(blendStart, target)
        assertTrue("startDistanceToTarget=$startDistanceToTarget", startDistanceToTarget > 5f)

        val settled = engine.predict(nowElapsedMs = 43_260L) ?: target
        assertTrue(distanceMeters(settled, target) < 1f)
    }

    @Test
    fun keepsNormalBlendForMediumCorrection() {
        val engine = LocationFusionEngine()
        val base = LatLong(48.8566, 2.3522)
        val target = moveLatLong(base, bearing = 0f, distanceMeters = 12f)

        engine.onGpsFix(
            latLong = base,
            speedMps = 0.9f,
            accuracyM = 8f,
            bearingDeg = null,
            nowElapsedMs = 70_000L,
        )
        val blendStart =
            engine.onGpsFix(
                latLong = target,
                speedMps = 0.8f,
                accuracyM = 8f,
                bearingDeg = null,
                nowElapsedMs = 72_000L,
            )

        val mediumStartDistanceToTarget = distanceMeters(blendStart, target)
        assertTrue(
            "mediumStartDistanceToTarget=$mediumStartDistanceToTarget",
            mediumStartDistanceToTarget > 2f,
        )

        val midBlend = engine.predict(nowElapsedMs = 72_200L) ?: target
        assertTrue(distanceMeters(midBlend, target) > 0.5f)

        val settled = engine.predict(nowElapsedMs = 72_700L) ?: target
        assertTrue(distanceMeters(settled, target) < 1f)
    }

    @Test
    fun seededAnchorBlendsFromWakeAnchorInsteadOfColdSnapping() {
        val engine = LocationFusionEngine()
        val base = LatLong(48.8566, 2.3522)
        val target = moveLatLong(base, bearing = 90f, distanceMeters = 18f)

        engine.seedAnchor(
            latLong = base,
            fixElapsedMs = 100_000L,
            speedMps = 1.1f,
            accuracyM = 8f,
            bearingDeg = 90f,
            nowElapsedMs = 103_000L,
        )
        val blendedStart =
            engine.onGpsFix(
                latLong = target,
                speedMps = 1.2f,
                accuracyM = 8f,
                bearingDeg = 90f,
                nowElapsedMs = 103_500L,
            )

        assertTrue(distanceMeters(blendedStart, base) < 0.5f)
        assertTrue(distanceMeters(blendedStart, target) > 1f)
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
