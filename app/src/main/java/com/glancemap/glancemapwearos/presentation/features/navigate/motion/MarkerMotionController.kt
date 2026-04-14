package com.glancemap.glancemapwearos.presentation.features.navigate.motion

import com.glancemap.glancemapwearos.presentation.features.navigate.moveLatLong
import org.mapsforge.core.model.LatLong
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal class MarkerMotionController(
    private val predictionFreshnessMaxAgeMs: Long,
    private val maxAcceptedFixAgeMs: Long,
    private val maxPredictionAccuracyM: Float = DEFAULT_MAX_PREDICTION_ACCURACY_M,
    private val minPredictionSpeedMps: Float = DEFAULT_MIN_PREDICTION_SPEED_MPS,
    private val correctionBlendDurationMs: Long = DEFAULT_CORRECTION_BLEND_DURATION_MS,
    private val predictionTickMs: Long = DEFAULT_PREDICTION_TICK_MS,
) {
    private var lastAcceptedFix: MotionFix? = null
    private var displayedLatLong: LatLong? = null
    private var correctionBlend: CorrectionBlend? = null
    private var smoothedSpeedMps: Float = 0f
    private var predictionRequiresFreshFix: Boolean = true

    fun reset(reason: String = "reset") {
        lastAcceptedFix = null
        displayedLatLong = null
        correctionBlend = null
        smoothedSpeedMps = 0f
        predictionRequiresFreshFix = true
        MarkerMotionTelemetry.recordIdle(
            nowElapsedMs = 0L,
            reason = reason,
        )
    }

    fun seedAnchor(
        latLong: LatLong,
        fixElapsedMs: Long,
        accuracyM: Float,
        speedMps: Float,
        bearingDeg: Float?,
    ) {
        val sanitizedSpeed = sanitizeSpeed(speedMps)
        smoothedSpeedMps = sanitizedSpeed
        lastAcceptedFix =
            MotionFix(
                latLong = latLong,
                fixElapsedMs = fixElapsedMs.coerceAtLeast(0L),
                accuracyM = sanitizeAccuracy(accuracyM),
                speedMps = sanitizedSpeed,
                bearingDeg = bearingDeg?.let(::normalize360),
            )
        displayedLatLong = latLong
        correctionBlend = null
        predictionRequiresFreshFix = true
        MarkerMotionTelemetry.recordSeedAnchor(
            nowElapsedMs = fixElapsedMs.coerceAtLeast(0L),
            accuracyM = sanitizeAccuracy(accuracyM),
            speedMps = sanitizedSpeed,
            bearingDeg = bearingDeg?.let(::normalize360),
        )
    }

    fun requireFreshFixForPrediction() {
        predictionRequiresFreshFix = true
        correctionBlend = null
        MarkerMotionTelemetry.recordPredictionBlocked(
            reason = "await_fresh_fix",
            nowElapsedMs = lastAcceptedFix?.fixElapsedMs ?: 0L,
            fixAgeMs = null,
            accuracyM = lastAcceptedFix?.accuracyM,
            speedMps = lastAcceptedFix?.speedMps,
            bearingDeg = lastAcceptedFix?.bearingDeg,
        )
    }

    fun suggestedPredictionTickMs(): Long = predictionTickMs

    fun onGpsFix(
        latLong: LatLong,
        nowElapsedMs: Long,
        fixElapsedMs: Long,
        accuracyM: Float,
        rawSpeedMps: Float,
        rawBearingDeg: Float?,
        allowLargeCorrection: Boolean = false,
    ): LatLong {
        val currentDisplayed = displayedLatLong
        val sanitizedAccuracy = sanitizeAccuracy(accuracyM)
        val reliableFixElapsedMs =
            fixElapsedMs
                .takeIf { it > 0L }
                ?.coerceAtMost(nowElapsedMs)
                ?: nowElapsedMs
        val fixAgeMs = (nowElapsedMs - reliableFixElapsedMs).coerceAtLeast(0L)

        if (currentDisplayed != null && fixAgeMs > maxAcceptedFixAgeMs) {
            MarkerMotionTelemetry.recordPredictionBlocked(
                reason = "stale_fix",
                nowElapsedMs = nowElapsedMs,
                fixAgeMs = fixAgeMs,
                accuracyM = sanitizedAccuracy,
                speedMps = rawSpeedMps.takeIf { it.isFinite() },
                bearingDeg = rawBearingDeg?.takeIf { it.isFinite() },
            )
            return currentDisplayed
        }

        val previousFix = lastAcceptedFix
        val outlierDecision =
            previousFix?.detectOutlier(
                candidate = latLong,
                candidateAccuracyM = sanitizedAccuracy,
                candidateFixElapsedMs = reliableFixElapsedMs,
            )
        if (outlierDecision != null) {
            correctionBlend = null
            MarkerMotionTelemetry.recordOutlierDropped(
                nowElapsedMs = nowElapsedMs,
                fixAgeMs = fixAgeMs,
                accuracyM = sanitizedAccuracy,
                jumpMeters = outlierDecision.jumpMeters,
                impliedSpeedMps = outlierDecision.impliedSpeedMps,
                dtSec = outlierDecision.dtSec,
            )
            return currentDisplayed ?: previousFix.latLong
        }

        val derivedMotion = previousFix?.deriveMotionTo(target = latLong, targetFixElapsedMs = reliableFixElapsedMs)
        val resolvedSpeedMps =
            resolveMotionSpeedMps(
                rawSpeedMps = rawSpeedMps,
                derivedSpeedMps = derivedMotion?.speedMps,
                accuracyM = sanitizedAccuracy,
            )
        smoothedSpeedMps =
            if (smoothedSpeedMps <= 0f) {
                resolvedSpeedMps
            } else {
                SPEED_SMOOTHING_ALPHA * resolvedSpeedMps +
                    (1f - SPEED_SMOOTHING_ALPHA) * smoothedSpeedMps
            }
        val resolvedBearingDeg =
            resolveMotionBearingDeg(
                rawBearingDeg = rawBearingDeg,
                rawSpeedMps = rawSpeedMps,
                derivedMotion = derivedMotion,
                fallbackBearingDeg = previousFix?.bearingDeg,
                resolvedSpeedMps = smoothedSpeedMps,
            )

        lastAcceptedFix =
            MotionFix(
                latLong = latLong,
                fixElapsedMs = reliableFixElapsedMs,
                accuracyM = sanitizedAccuracy,
                speedMps = smoothedSpeedMps,
                bearingDeg = resolvedBearingDeg,
            )
        predictionRequiresFreshFix = false

        if (currentDisplayed == null) {
            displayedLatLong = latLong
            correctionBlend = null
            MarkerMotionTelemetry.recordFixAccepted(
                mode = MarkerMotionMode.FIXED,
                reason = "initial_fix",
                nowElapsedMs = nowElapsedMs,
                fixAgeMs = fixAgeMs,
                accuracyM = sanitizedAccuracy,
                speedMps = smoothedSpeedMps,
                bearingDeg = resolvedBearingDeg,
                correctionDistanceM = null,
                blendDurationMs = null,
            )
            return latLong
        }

        val correctionDistanceM = distanceMeters(currentDisplayed, latLong)
        if (shouldFreezeStationaryJitter(correctionDistanceM, sanitizedAccuracy, smoothedSpeedMps)) {
            correctionBlend = null
            MarkerMotionTelemetry.recordFixAccepted(
                mode = MarkerMotionMode.FIXED,
                reason = "stationary_jitter",
                nowElapsedMs = nowElapsedMs,
                fixAgeMs = fixAgeMs,
                accuracyM = sanitizedAccuracy,
                speedMps = smoothedSpeedMps,
                bearingDeg = resolvedBearingDeg,
                correctionDistanceM = correctionDistanceM,
                blendDurationMs = null,
            )
            return currentDisplayed
        }

        if (correctionDistanceM <= correctionDeadbandMeters(sanitizedAccuracy, smoothedSpeedMps)) {
            displayedLatLong = latLong
            correctionBlend = null
            MarkerMotionTelemetry.recordFixAccepted(
                mode = MarkerMotionMode.FIXED,
                reason = "deadband_snap",
                nowElapsedMs = nowElapsedMs,
                fixAgeMs = fixAgeMs,
                accuracyM = sanitizedAccuracy,
                speedMps = smoothedSpeedMps,
                bearingDeg = resolvedBearingDeg,
                correctionDistanceM = correctionDistanceM,
                blendDurationMs = null,
            )
            return latLong
        }

        val correctionTarget =
            resolveCorrectionTarget(
                request =
                    CorrectionTargetRequest(
                        currentDisplayed = currentDisplayed,
                        targetLatLong = latLong,
                        correctionDistanceM = correctionDistanceM,
                        accuracyM = sanitizedAccuracy,
                        speedMps = smoothedSpeedMps,
                        allowLargeCorrection = allowLargeCorrection,
                    ),
            )
        val visibleTarget = correctionTarget.targetLatLong
        if (correctionTarget.wasClamped) {
            MarkerMotionTelemetry.recordCorrectionClamped(
                event =
                    CorrectionClampTelemetryEvent(
                        nowElapsedMs = nowElapsedMs,
                        actualCorrectionDistanceM = correctionDistanceM,
                        visibleCorrectionDistanceM = correctionTarget.visibleCorrectionDistanceM,
                        accuracyM = sanitizedAccuracy,
                        speedMps = smoothedSpeedMps,
                        bearingDeg = resolvedBearingDeg,
                    ),
            )
        }
        correctionBlend =
            CorrectionBlend(
                from = currentDisplayed,
                to = visibleTarget,
                startElapsedMs = nowElapsedMs,
                durationMs = correctionBlendDurationMs,
            )
        MarkerMotionTelemetry.recordFixAccepted(
            mode = MarkerMotionMode.BLEND,
            reason = if (correctionTarget.wasClamped) "correction_clamped" else "gps_correction",
            nowElapsedMs = nowElapsedMs,
            fixAgeMs = fixAgeMs,
            accuracyM = sanitizedAccuracy,
            speedMps = smoothedSpeedMps,
            bearingDeg = resolvedBearingDeg,
            correctionDistanceM = correctionTarget.visibleCorrectionDistanceM,
            blendDurationMs = correctionBlendDurationMs,
        )
        return currentDisplayed
    }

    fun predict(
        nowElapsedMs: Long,
        serviceFreshnessMaxAgeMs: Long,
        watchGpsDegraded: Boolean,
    ): LatLong? {
        var currentDisplayed = displayedLatLong ?: lastAcceptedFix?.latLong ?: return null

        correctionBlend?.let { blend ->
            val elapsedMs = (nowElapsedMs - blend.startElapsedMs).coerceAtLeast(0L)
            val fraction = (elapsedMs.toFloat() / blend.durationMs.toFloat()).coerceIn(0f, 1f)
            val blended = lerpLatLong(blend.from, blend.to, fraction)
            displayedLatLong = blended
            lastAcceptedFix?.let { fix ->
                MarkerMotionTelemetry.recordBlendState(
                    nowElapsedMs = nowElapsedMs,
                    fixAgeMs = (nowElapsedMs - fix.fixElapsedMs).coerceAtLeast(0L),
                    accuracyM = fix.accuracyM,
                    speedMps = fix.speedMps,
                    bearingDeg = fix.bearingDeg,
                    correctionDistanceM = distanceMeters(blended, blend.to),
                )
            }
            if (fraction < 1f) {
                return blended
            }
            correctionBlend = null
            currentDisplayed = blended
        }

        if (watchGpsDegraded || predictionRequiresFreshFix) {
            val fix = lastAcceptedFix
            MarkerMotionTelemetry.recordPredictionBlocked(
                reason = if (watchGpsDegraded) "degraded_gps" else "await_fresh_fix",
                nowElapsedMs = nowElapsedMs,
                fixAgeMs = fix?.let { (nowElapsedMs - it.fixElapsedMs).coerceAtLeast(0L) },
                accuracyM = fix?.accuracyM,
                speedMps = fix?.speedMps,
                bearingDeg = fix?.bearingDeg,
            )
            return currentDisplayed
        }

        val fix = lastAcceptedFix ?: return currentDisplayed
        val freshnessMaxAgeMs =
            minOf(
                predictionFreshnessMaxAgeMs,
                serviceFreshnessMaxAgeMs.takeIf { it > 0L } ?: Long.MAX_VALUE,
            )
        val fixAgeMs = (nowElapsedMs - fix.fixElapsedMs).coerceAtLeast(0L)
        if (fixAgeMs <= PREDICTION_START_DELAY_MS || fixAgeMs > freshnessMaxAgeMs) {
            MarkerMotionTelemetry.recordPredictionBlocked(
                reason = if (fixAgeMs <= PREDICTION_START_DELAY_MS) "prediction_delay" else "stale",
                nowElapsedMs = nowElapsedMs,
                fixAgeMs = fixAgeMs,
                accuracyM = fix.accuracyM,
                speedMps = fix.speedMps,
                bearingDeg = fix.bearingDeg,
            )
            return currentDisplayed
        }
        if (fix.accuracyM > maxPredictionAccuracyM) {
            MarkerMotionTelemetry.recordPredictionBlocked(
                reason = "bad_accuracy",
                nowElapsedMs = nowElapsedMs,
                fixAgeMs = fixAgeMs,
                accuracyM = fix.accuracyM,
                speedMps = fix.speedMps,
                bearingDeg = fix.bearingDeg,
            )
            return currentDisplayed
        }
        val bearingDeg =
            fix.bearingDeg ?: run {
                MarkerMotionTelemetry.recordPredictionBlocked(
                    reason = "no_bearing",
                    nowElapsedMs = nowElapsedMs,
                    fixAgeMs = fixAgeMs,
                    accuracyM = fix.accuracyM,
                    speedMps = fix.speedMps,
                    bearingDeg = null,
                )
                return currentDisplayed
            }
        if (fix.speedMps < minPredictionSpeedMps) {
            MarkerMotionTelemetry.recordPredictionBlocked(
                reason = "slow",
                nowElapsedMs = nowElapsedMs,
                fixAgeMs = fixAgeMs,
                accuracyM = fix.accuracyM,
                speedMps = fix.speedMps,
                bearingDeg = bearingDeg,
            )
            return currentDisplayed
        }

        val effectivePredictionAgeMs = (fixAgeMs - PREDICTION_START_DELAY_MS).coerceAtLeast(0L)
        val predictedDistanceM =
            fix.speedMps * PREDICTION_SPEED_SCALE * (effectivePredictionAgeMs / 1000f)
        if (predictedDistanceM < MIN_PREDICTION_DISTANCE_M) {
            MarkerMotionTelemetry.recordPredictionBlocked(
                reason = "too_close",
                nowElapsedMs = nowElapsedMs,
                fixAgeMs = fixAgeMs,
                accuracyM = fix.accuracyM,
                speedMps = fix.speedMps,
                bearingDeg = bearingDeg,
            )
            return currentDisplayed
        }

        val predicted =
            moveLatLong(
                start = fix.latLong,
                bearing = bearingDeg,
                distanceMeters = predictedDistanceM,
            )
        MarkerMotionTelemetry.recordPredictionDisplayed(
            nowElapsedMs = nowElapsedMs,
            fixAgeMs = fixAgeMs,
            accuracyM = fix.accuracyM,
            speedMps = fix.speedMps,
            bearingDeg = bearingDeg,
            predictedDistanceM = predictedDistanceM,
        )
        if (distanceMeters(currentDisplayed, predicted) < PREDICTION_RENDER_EPSILON_M) {
            return currentDisplayed
        }
        displayedLatLong = predicted
        return predicted
    }

    private fun MotionFix.detectOutlier(
        candidate: LatLong,
        candidateAccuracyM: Float,
        candidateFixElapsedMs: Long,
    ): OutlierDecision? {
        val dtMs = (candidateFixElapsedMs - fixElapsedMs).coerceAtLeast(0L)
        if (dtMs < OUTLIER_MIN_WINDOW_MS) return null
        val dtSec = dtMs / 1000f
        if (dtSec <= 0f) return null

        val jumpMeters = distanceMeters(latLong, candidate)
        val impliedSpeedMps = jumpMeters / dtSec
        val allowedJumpMeters =
            max(
                MIN_OUTLIER_JUMP_M,
                accuracyM + candidateAccuracyM + OUTLIER_JUMP_MARGIN_M,
            )
        val allowedSpeedMps =
            max(
                MAX_OUTLIER_SPEED_MPS,
                speedMps * OUTLIER_SPEED_MULTIPLIER + OUTLIER_SPEED_MARGIN_M,
            )
        return if (jumpMeters > allowedJumpMeters && impliedSpeedMps > allowedSpeedMps) {
            OutlierDecision(
                jumpMeters = jumpMeters,
                impliedSpeedMps = impliedSpeedMps,
                dtSec = dtSec,
            )
        } else {
            null
        }
    }

    private fun shouldFreezeStationaryJitter(
        correctionDistanceM: Float,
        accuracyM: Float,
        speedMps: Float,
    ): Boolean {
        if (speedMps > STATIONARY_JITTER_MAX_SPEED_MPS) return false
        return correctionDistanceM <= accuracyM.coerceIn(STATIONARY_JITTER_MIN_RADIUS_M, STATIONARY_JITTER_MAX_RADIUS_M)
    }

    private fun correctionDeadbandMeters(
        accuracyM: Float,
        speedMps: Float,
    ): Float =
        when {
            speedMps < 0.6f -> accuracyM.coerceIn(2.5f, 6f)
            else -> 1.2f
        }

    private fun resolveCorrectionTarget(request: CorrectionTargetRequest): CorrectionTargetDecision {
        val canClamp =
            !request.allowLargeCorrection &&
                request.correctionDistanceM >= LARGE_CORRECTION_MIN_DISTANCE_M &&
                (
                    request.accuracyM >= LARGE_CORRECTION_MIN_ACCURACY_M ||
                        request.correctionDistanceM >= LARGE_CORRECTION_FORCE_CLAMP_DISTANCE_M
                )
        if (!canClamp) {
            return CorrectionTargetDecision(
                targetLatLong = request.targetLatLong,
                visibleCorrectionDistanceM = request.correctionDistanceM,
                wasClamped = false,
            )
        }

        val maxVisibleCorrectionM =
            (
                LARGE_CORRECTION_BASE_VISIBLE_M +
                    request.accuracyM * LARGE_CORRECTION_ACCURACY_SCALE +
                    request.speedMps * LARGE_CORRECTION_SPEED_SCALE
            ).coerceAtLeast(LARGE_CORRECTION_BASE_VISIBLE_M)
        val wasClamped = request.correctionDistanceM > maxVisibleCorrectionM
        val visibleCorrectionDistanceM =
            if (wasClamped) {
                maxVisibleCorrectionM
            } else {
                request.correctionDistanceM
            }
        val targetLatLong =
            if (wasClamped) {
                moveLatLong(
                    start = request.currentDisplayed,
                    bearing = bearingBetweenDegrees(request.currentDisplayed, request.targetLatLong),
                    distanceMeters = maxVisibleCorrectionM,
                )
            } else {
                request.targetLatLong
            }
        return CorrectionTargetDecision(
            targetLatLong = targetLatLong,
            visibleCorrectionDistanceM = visibleCorrectionDistanceM,
            wasClamped = wasClamped,
        )
    }

    private fun resolveMotionSpeedMps(
        rawSpeedMps: Float,
        derivedSpeedMps: Float?,
        accuracyM: Float,
    ): Float {
        val trustedRawSpeed =
            rawSpeedMps
                .takeIf { it.isFinite() }
                ?.coerceAtLeast(0f)
        val trustedDerivedSpeed =
            derivedSpeedMps
                ?.takeIf { it.isFinite() }
                ?.coerceAtLeast(0f)

        if (
            trustedRawSpeed != null &&
            trustedDerivedSpeed != null &&
            trustedRawSpeed <= LOW_RAW_SPEED_OVERRIDE_MAX_MPS &&
            accuracyM <= DERIVED_SPEED_MAX_ACCURACY_M &&
            trustedDerivedSpeed >= trustedRawSpeed + LOW_RAW_SPEED_OVERRIDE_GAIN_MPS
        ) {
            return trustedDerivedSpeed
        }
        if (trustedRawSpeed != null) return trustedRawSpeed
        if (trustedDerivedSpeed != null && accuracyM <= DERIVED_SPEED_MAX_ACCURACY_M) {
            return trustedDerivedSpeed
        }
        return 0f
    }

    private fun resolveMotionBearingDeg(
        rawBearingDeg: Float?,
        rawSpeedMps: Float,
        derivedMotion: DerivedMotion?,
        fallbackBearingDeg: Float?,
        resolvedSpeedMps: Float,
    ): Float? {
        if (
            rawBearingDeg != null &&
            rawBearingDeg.isFinite() &&
            max(sanitizeSpeed(rawSpeedMps), resolvedSpeedMps) >= GPS_BEARING_MIN_SPEED_MPS
        ) {
            return normalize360(rawBearingDeg)
        }
        if (
            derivedMotion != null &&
            derivedMotion.bearingDeg != null &&
            max(derivedMotion.speedMps, resolvedSpeedMps) >= minPredictionSpeedMps
        ) {
            return derivedMotion.bearingDeg
        }
        return fallbackBearingDeg?.let(::normalize360)
    }
}

private data class MotionFix(
    val latLong: LatLong,
    val fixElapsedMs: Long,
    val accuracyM: Float,
    val speedMps: Float,
    val bearingDeg: Float?,
)

private data class CorrectionBlend(
    val from: LatLong,
    val to: LatLong,
    val startElapsedMs: Long,
    val durationMs: Long,
)

private data class DerivedMotion(
    val speedMps: Float,
    val bearingDeg: Float?,
)

private data class OutlierDecision(
    val jumpMeters: Float,
    val impliedSpeedMps: Float,
    val dtSec: Float,
)

private data class CorrectionTargetDecision(
    val targetLatLong: LatLong,
    val visibleCorrectionDistanceM: Float,
    val wasClamped: Boolean,
)

private data class CorrectionTargetRequest(
    val currentDisplayed: LatLong,
    val targetLatLong: LatLong,
    val correctionDistanceM: Float,
    val accuracyM: Float,
    val speedMps: Float,
    val allowLargeCorrection: Boolean,
)

private fun MotionFix.deriveMotionTo(
    target: LatLong,
    targetFixElapsedMs: Long,
): DerivedMotion? {
    val dtMs = (targetFixElapsedMs - fixElapsedMs).coerceAtLeast(0L)
    if (dtMs < DERIVED_MOTION_MIN_WINDOW_MS) return null
    val dtSec = dtMs / 1000f
    if (dtSec <= 0f) return null
    val distanceM = distanceMeters(latLong, target)
    val speedMps = distanceM / dtSec
    return DerivedMotion(
        speedMps = speedMps,
        bearingDeg = bearingBetweenDegrees(latLong, target),
    )
}

private fun sanitizeAccuracy(accuracyM: Float): Float {
    if (!accuracyM.isFinite()) return DEFAULT_UNKNOWN_ACCURACY_M
    return accuracyM.coerceAtLeast(0f)
}

private fun sanitizeSpeed(speedMps: Float): Float {
    if (!speedMps.isFinite()) return 0f
    return speedMps.coerceAtLeast(0f)
}

private fun normalize360(angleDeg: Float): Float {
    var normalized = angleDeg % 360f
    if (normalized < 0f) normalized += 360f
    return normalized
}

private fun bearingBetweenDegrees(
    from: LatLong,
    to: LatLong,
): Float {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLon = Math.toRadians(to.longitude - from.longitude)
    val y = sin(dLon) * cos(lat2)
    val x =
        cos(lat1) * sin(lat2) -
            sin(lat1) * cos(lat2) * cos(dLon)
    val bearingDeg = Math.toDegrees(atan2(y, x)).toFloat()
    return normalize360(bearingDeg)
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
    return (EARTH_RADIUS_METERS * c).toFloat()
}

private fun lerpLatLong(
    from: LatLong,
    to: LatLong,
    fraction: Float,
): LatLong =
    LatLong(
        from.latitude + (to.latitude - from.latitude) * fraction,
        from.longitude + (to.longitude - from.longitude) * fraction,
    )

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val DEFAULT_UNKNOWN_ACCURACY_M = 99f
private const val DEFAULT_MAX_PREDICTION_ACCURACY_M = 25f
private const val DEFAULT_MIN_PREDICTION_SPEED_MPS = 0.7f
private const val DEFAULT_CORRECTION_BLEND_DURATION_MS = 350L
private const val DEFAULT_PREDICTION_TICK_MS = 250L
private const val PREDICTION_START_DELAY_MS = 150L
private const val PREDICTION_SPEED_SCALE = 0.9f
private const val MIN_PREDICTION_DISTANCE_M = 0.35f
private const val PREDICTION_RENDER_EPSILON_M = 0.25f
private const val SPEED_SMOOTHING_ALPHA = 0.35f
private const val GPS_BEARING_MIN_SPEED_MPS = 1.0f
private const val DERIVED_MOTION_MIN_WINDOW_MS = 900L
private const val DERIVED_SPEED_MAX_ACCURACY_M = 18f
private const val LOW_RAW_SPEED_OVERRIDE_MAX_MPS = 0.75f
private const val LOW_RAW_SPEED_OVERRIDE_GAIN_MPS = 0.45f
private const val OUTLIER_MIN_WINDOW_MS = 1_000L
private const val MIN_OUTLIER_JUMP_M = 24f
private const val OUTLIER_JUMP_MARGIN_M = 10f
private const val MAX_OUTLIER_SPEED_MPS = 14f
private const val OUTLIER_SPEED_MULTIPLIER = 2.5f
private const val OUTLIER_SPEED_MARGIN_M = 5f
private const val STATIONARY_JITTER_MAX_SPEED_MPS = 0.35f
private const val STATIONARY_JITTER_MIN_RADIUS_M = 3f
private const val STATIONARY_JITTER_MAX_RADIUS_M = 10f
private const val LARGE_CORRECTION_MIN_DISTANCE_M = 18f
private const val LARGE_CORRECTION_MIN_ACCURACY_M = 14f
private const val LARGE_CORRECTION_FORCE_CLAMP_DISTANCE_M = 26f
private const val LARGE_CORRECTION_BASE_VISIBLE_M = 8f
private const val LARGE_CORRECTION_ACCURACY_SCALE = 0.35f
private const val LARGE_CORRECTION_SPEED_SCALE = 2.2f
