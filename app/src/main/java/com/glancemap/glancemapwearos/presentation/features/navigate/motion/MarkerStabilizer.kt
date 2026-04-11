package com.glancemap.glancemapwearos.presentation.features.navigate.motion

import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.core.service.location.config.FINE_FIX_MAX_ACCURACY_M
import com.glancemap.glancemapwearos.core.service.location.config.STRICT_FRESH_FIX_MIN_AGE_MS
import org.mapsforge.core.model.LatLong
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal class MarkerStabilizer(
    private val maxAcceptedAccuracyM: Float = FINE_FIX_MAX_ACCURACY_M,
    private val maxAcceptedFixAgeMs: Long = 8_000L,
    private val minOutlierWindowMs: Long = 1_000L,
    private val maxOutlierImpliedSpeedMps: Float = 14f,
    private val maxPredictionWithoutGpsMs: Long = 10_000L,
    private val wakeCorrectionMinGapMs: Long = STRICT_FRESH_FIX_MIN_AGE_MS
) {
    private var renderedLatLong: LatLong? = null
    private var lastGpsLatLong: LatLong? = null
    private var lastGpsElapsedMs: Long = 0L
    private var lastGpsAccuracyM: Float = 12f
    private var lastGpsSpeedMps: Float = 0f

    fun reset() {
        renderedLatLong = null
        lastGpsLatLong = null
        lastGpsElapsedMs = 0L
        lastGpsAccuracyM = 12f
        lastGpsSpeedMps = 0f
    }

    fun seedAnchor(
        latLong: LatLong,
        fixElapsedMs: Long,
        accuracyM: Float,
        speedMps: Float
    ) {
        renderedLatLong = latLong
        lastGpsLatLong = latLong
        lastGpsElapsedMs = fixElapsedMs.coerceAtLeast(0L)
        lastGpsAccuracyM = sanitizeAccuracy(accuracyM)
        lastGpsSpeedMps = sanitizeSpeed(speedMps)
    }

    fun onGpsFix(
        candidate: LatLong,
        nowElapsedMs: Long,
        fixElapsedMs: Long,
        accuracyM: Float,
        speedMps: Float
    ): LatLong {
        val accuracy = sanitizeAccuracy(accuracyM)
        val speed = sanitizeSpeed(speedMps)
        val ageMs = (nowElapsedMs - fixElapsedMs).coerceAtLeast(0L)
        val hasReliableFixTimestamp = fixElapsedMs > 0L
        val currentRendered = renderedLatLong

        if (currentRendered != null &&
            hasReliableFixTimestamp &&
            ageMs > maxAcceptedFixAgeMs
        ) {
            if (DebugTelemetry.isEnabled()) {
                DebugTelemetry.log(
                    TAG,
                    "freeze staleFix acc=${accuracy.format(1)} ageMs=$ageMs"
                )
            }
            return currentRendered
        }
        val lowConfidenceFix = accuracy > maxAcceptedAccuracyM

        val lastGps = lastGpsLatLong
        if (currentRendered != null && lastGps != null && lastGpsElapsedMs > 0L) {
            val dtMs = (fixElapsedMs - lastGpsElapsedMs).coerceAtLeast(0L)
            if (dtMs >= minOutlierWindowMs) {
                val dtSec = dtMs / 1000f
                val jumpMeters = distanceMeters(lastGps, candidate)
                val impliedSpeed = if (dtSec > 0f) jumpMeters / dtSec else 0f
                val allowedJumpMeters = (lastGpsAccuracyM + accuracy + OUTLIER_JUMP_MARGIN_M)
                    .coerceAtLeast(MIN_OUTLIER_JUMP_M)
                val dynamicMaxSpeed = maxOf(
                    maxOutlierImpliedSpeedMps,
                    lastGpsSpeedMps * OUTLIER_SPEED_MULTIPLIER + OUTLIER_SPEED_MARGIN_M
                )
                if (jumpMeters > allowedJumpMeters && impliedSpeed > dynamicMaxSpeed) {
                    if (DebugTelemetry.isEnabled()) {
                        DebugTelemetry.log(
                            TAG,
                            "freeze outlier jumpM=${jumpMeters.format(1)} " +
                                "speed=${impliedSpeed.format(1)} limit=${dynamicMaxSpeed.format(1)}"
                        )
                    }
                    return currentRendered
                }
            }
        }

        val sinceLastGpsMs = if (lastGpsElapsedMs > 0L && fixElapsedMs > 0L) {
            (fixElapsedMs - lastGpsElapsedMs).coerceAtLeast(0L)
        } else {
            0L
        }
        val jumpToRenderedMeters = if (currentRendered != null) {
            distanceMeters(currentRendered, candidate)
        } else {
            0f
        }
        val wakeCorrectionMaxGapMs = resolveWakeCorrectionMaxGapMs(wakeCorrectionMinGapMs)
        val wakeCorrectionMaxDistanceM = resolveWakeCorrectionMaxDistanceMeters(accuracy)
        val wakeCorrectionGapEligible =
            sinceLastGpsMs in wakeCorrectionMinGapMs..wakeCorrectionMaxGapMs
        val wakeCorrectionDistanceEligible =
            jumpToRenderedMeters <= wakeCorrectionMaxDistanceM
        val isWakeCorrectionCandidate =
            currentRendered != null &&
                sinceLastGpsMs >= wakeCorrectionMinGapMs &&
                jumpToRenderedMeters >= WAKE_CORRECTION_MIN_DISTANCE_M &&
                accuracy <= WAKE_CORRECTION_CONFIDENT_ACCURACY_M
        val shouldSnapWakeCorrection =
            isWakeCorrectionCandidate &&
                wakeCorrectionGapEligible &&
                wakeCorrectionDistanceEligible
        if (isWakeCorrectionCandidate && !shouldSnapWakeCorrection && DebugTelemetry.isEnabled()) {
            DebugTelemetry.log(
                TAG,
                "skip wakeCorrection jumpM=${jumpToRenderedMeters.format(1)} " +
                    "gapMs=$sinceLastGpsMs acc=${accuracy.format(1)} " +
                    "maxJumpM=${wakeCorrectionMaxDistanceM.format(1)} maxGapMs=$wakeCorrectionMaxGapMs"
            )
        }
        if (shouldSnapWakeCorrection) {
            renderedLatLong = candidate
            lastGpsLatLong = candidate
            lastGpsElapsedMs = fixElapsedMs
            lastGpsAccuracyM = accuracy
            lastGpsSpeedMps = speed
            if (DebugTelemetry.isEnabled()) {
                DebugTelemetry.log(
                    TAG,
                    "snap wakeCorrection jumpM=${jumpToRenderedMeters.format(1)} " +
                        "gapMs=$sinceLastGpsMs acc=${accuracy.format(1)}"
                )
            }
            return candidate
        }

        val shouldClampStationaryDrift = currentRendered != null &&
            speed <= STATIONARY_DRIFT_MAX_SPEED_MPS &&
            accuracy >= STATIONARY_DRIFT_MIN_ACCURACY_M &&
            distanceMeters(currentRendered, candidate) <= stationaryDriftClampMeters(accuracy)
        if (shouldClampStationaryDrift) {
            lastGpsLatLong = candidate
            lastGpsElapsedMs = fixElapsedMs
            lastGpsAccuracyM = accuracy
            lastGpsSpeedMps = speed
            if (DebugTelemetry.isEnabled()) {
                DebugTelemetry.log(
                    TAG,
                    "freeze stationaryDrift acc=${accuracy.format(1)} speed=${speed.format(2)}"
                )
            }
            return currentRendered
        }

        lastGpsLatLong = candidate
        lastGpsElapsedMs = fixElapsedMs
        lastGpsAccuracyM = accuracy
        lastGpsSpeedMps = speed
        return smoothTowards(
            target = candidate,
            speedMps = speed,
            accuracyM = accuracy,
            prediction = false,
            lowConfidenceFix = lowConfidenceFix
        )
    }

    fun onPrediction(candidate: LatLong, nowElapsedMs: Long): LatLong {
        val currentRendered = renderedLatLong
        if (currentRendered == null) {
            renderedLatLong = candidate
            return candidate
        }

        if (lastGpsElapsedMs > 0L) {
            val sinceLastGpsMs = (nowElapsedMs - lastGpsElapsedMs).coerceAtLeast(0L)
            if (sinceLastGpsMs > maxPredictionWithoutGpsMs) {
                return currentRendered
            }
        }

        return smoothTowards(
            target = candidate,
            speedMps = lastGpsSpeedMps,
            accuracyM = lastGpsAccuracyM,
            prediction = true,
            lowConfidenceFix = false
        )
    }

    private fun smoothTowards(
        target: LatLong,
        speedMps: Float,
        accuracyM: Float,
        prediction: Boolean,
        lowConfidenceFix: Boolean
    ): LatLong {
        val current = renderedLatLong
        if (current == null) {
            renderedLatLong = target
            return target
        }

        val distanceMeters = distanceMeters(current, target)
        val deadbandMeters = deadbandMeters(
            speedMps = speedMps,
            accuracyM = accuracyM,
            prediction = prediction,
            lowConfidenceFix = lowConfidenceFix
        )
        if (distanceMeters <= deadbandMeters) {
            return current
        }

        val alpha = smoothingAlpha(
            speedMps = speedMps,
            accuracyM = accuracyM,
            prediction = prediction,
            lowConfidenceFix = lowConfidenceFix
        )
        val smoothed = lerpLatLong(current, target, alpha)
        renderedLatLong = smoothed
        return smoothed
    }

    private fun deadbandMeters(
        speedMps: Float,
        accuracyM: Float,
        prediction: Boolean,
        lowConfidenceFix: Boolean
    ): Float {
        val base = when {
            speedMps < 0.6f -> 2.8f
            speedMps < 1.6f -> 0.85f
            else -> 0.6f
        }
        val accuracyTerm = (accuracyM * 0.06f).coerceIn(0f, 1.4f)
        val predictionAdjustment = if (prediction) -0.7f else 0f
        val lowConfidenceAdjustment = if (lowConfidenceFix) 0.35f else 0f
        return (base + accuracyTerm + predictionAdjustment + lowConfidenceAdjustment)
            .coerceIn(0.45f, 4.2f)
    }

    private fun smoothingAlpha(
        speedMps: Float,
        accuracyM: Float,
        prediction: Boolean,
        lowConfidenceFix: Boolean
    ): Float {
        var alpha = when {
            speedMps < 0.6f -> 0.30f
            speedMps < 1.8f -> 0.48f
            speedMps < 3.5f -> 0.58f
            else -> 0.64f
        }
        alpha -= when {
            accuracyM > 24f -> 0.05f
            accuracyM > 14f -> 0.02f
            else -> 0f
        }
        if (lowConfidenceFix) alpha -= 0.03f
        if (prediction) alpha += 0.05f
        return alpha.coerceIn(0.20f, 0.76f)
    }

    private fun sanitizeAccuracy(accuracyM: Float): Float {
        if (!accuracyM.isFinite()) return DEFAULT_UNKNOWN_ACCURACY_M
        return accuracyM.coerceAtLeast(0f)
    }

    private fun sanitizeSpeed(speedMps: Float): Float {
        if (!speedMps.isFinite()) return 0f
        return speedMps.coerceAtLeast(0f)
    }

    private fun stationaryDriftClampMeters(accuracyM: Float): Float {
        val scaled = accuracyM * STATIONARY_DRIFT_ACCURACY_MULTIPLIER
        return scaled.coerceIn(
            STATIONARY_DRIFT_MIN_CLAMP_M,
            STATIONARY_DRIFT_MAX_CLAMP_M
        )
    }
}

private const val TAG = "MarkerStabilizer"
private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val DEFAULT_UNKNOWN_ACCURACY_M = 99f
private const val MIN_OUTLIER_JUMP_M = 24f
private const val OUTLIER_JUMP_MARGIN_M = 10f
private const val OUTLIER_SPEED_MULTIPLIER = 2.5f
private const val OUTLIER_SPEED_MARGIN_M = 5f
private const val WAKE_CORRECTION_MIN_DISTANCE_M = 25f
private const val WAKE_CORRECTION_CONFIDENT_ACCURACY_M = 20f
private const val WAKE_CORRECTION_MAX_GAP_MULTIPLIER = 2L
private const val WAKE_CORRECTION_MAX_DISTANCE_ACCURACY_MULTIPLIER = 2f
private const val WAKE_CORRECTION_MAX_DISTANCE_MARGIN_M = 6f
private const val STATIONARY_DRIFT_MAX_SPEED_MPS = 0.30f
private const val STATIONARY_DRIFT_MIN_ACCURACY_M = 14f
private const val STATIONARY_DRIFT_ACCURACY_MULTIPLIER = 0.55f
private const val STATIONARY_DRIFT_MIN_CLAMP_M = 8f
private const val STATIONARY_DRIFT_MAX_CLAMP_M = 18f

private fun resolveWakeCorrectionMaxGapMs(minGapMs: Long): Long {
    return (minGapMs * WAKE_CORRECTION_MAX_GAP_MULTIPLIER).coerceAtLeast(minGapMs)
}

private fun resolveWakeCorrectionMaxDistanceMeters(accuracyM: Float): Float {
    return (
        accuracyM * WAKE_CORRECTION_MAX_DISTANCE_ACCURACY_MULTIPLIER +
            WAKE_CORRECTION_MAX_DISTANCE_MARGIN_M
        ).coerceAtLeast(WAKE_CORRECTION_MIN_DISTANCE_M)
}

private fun lerpLatLong(from: LatLong, to: LatLong, alpha: Float): LatLong {
    val t = alpha.coerceIn(0f, 1f).toDouble()
    return LatLong(
        from.latitude + (to.latitude - from.latitude) * t,
        from.longitude + (to.longitude - from.longitude) * t
    )
}

private fun distanceMeters(a: LatLong, b: LatLong): Float {
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

private fun Float.format(digits: Int): String = "%.${digits}f".format(this)
