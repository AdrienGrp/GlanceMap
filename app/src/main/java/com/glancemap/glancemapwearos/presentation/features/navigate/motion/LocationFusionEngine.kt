package com.glancemap.glancemapwearos.presentation.features.navigate.motion

import android.hardware.SensorManager
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.core.service.location.config.STRICT_FRESH_FIX_MIN_AGE_MS
import com.glancemap.glancemapwearos.presentation.features.navigate.moveLatLong
import org.mapsforge.core.model.LatLong
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class LocationFusionEngine(
    private val staleGpsThresholdMs: Long = 2_500L,
    private val predictionHorizonMs: Long = 8_000L,
    private val correctionStaleGapMs: Long = STRICT_FRESH_FIX_MIN_AGE_MS,
    private val minBlendDurationMs: Long = 250L,
    private val maxBlendDurationMs: Long = 850L,
) {
    private var lastAcceptedFix: AcceptedFix? = null
    private var correctionBlend: FusionCorrectionBlend? = null
    private var displayedLatLong: LatLong? = null

    private var smoothedSpeedMps: Float = 0f

    private var motionState: MotionState = MotionState.MOVING
    private var stationaryCandidateSinceMs: Long = 0L
    private var movingCandidateSinceMs: Long = 0L

    private var lastHeadingDeg: Float = 0f
    private var lastHeadingAtMs: Long = 0L
    private var headingRateEmaDegPerSec: Float = 0f
    private var hasHeading: Boolean = false
    private var headingAccuracyStatus: Int = SensorManager.SENSOR_STATUS_UNRELIABLE
    private var magneticInterferenceActive: Boolean = false
    private var lastReplayPredictionAtMs: Long = 0L
    private var lastSummaryLogAtMs: Long = 0L
    private var predictionRequiresFreshFix: Boolean = true
    private var pendingFixConfirmation: PendingFixConfirmation? = null
    private var observedFixGapEmaMs: Long = 0L

    fun reset() {
        lastAcceptedFix = null
        correctionBlend = null
        displayedLatLong = null
        smoothedSpeedMps = 0f
        motionState = MotionState.MOVING
        stationaryCandidateSinceMs = 0L
        movingCandidateSinceMs = 0L
        lastHeadingDeg = 0f
        lastHeadingAtMs = 0L
        headingRateEmaDegPerSec = 0f
        hasHeading = false
        lastReplayPredictionAtMs = 0L
        lastSummaryLogAtMs = 0L
        predictionRequiresFreshFix = true
        pendingFixConfirmation = null
        observedFixGapEmaMs = 0L
    }

    fun seedAnchor(
        latLong: LatLong,
        fixElapsedMs: Long,
        speedMps: Float,
        accuracyM: Float,
        bearingDeg: Float?,
        nowElapsedMs: Long,
    ) {
        val sanitizedSpeed = sanitizeSpeed(speedMps)
        val sanitizedAccuracy = sanitizeAccuracy(accuracyM)
        smoothedSpeedMps = sanitizedSpeed
        updateMotionState(sanitizedSpeed, nowElapsedMs)
        correctionBlend = null
        displayedLatLong = latLong
        lastAcceptedFix =
            AcceptedFix(
                latLong = latLong,
                elapsedMs = fixElapsedMs.coerceAtLeast(0L),
                speedMps = sanitizedSpeed,
                bearingDeg = bearingDeg ?: lastHeadingDeg,
                accuracyM = sanitizedAccuracy,
            )
        predictionRequiresFreshFix = true
        pendingFixConfirmation = null
    }

    fun requireFreshFixForPrediction() {
        predictionRequiresFreshFix = true
        correctionBlend = null
        pendingFixConfirmation = null
    }

    fun suggestedPredictionTickMs(): Long =
        when (motionState) {
            MotionState.STATIONARY -> STATIONARY_PREDICTION_TICK_MS
            MotionState.MOVING ->
                when {
                    smoothedSpeedMps >= FAST_MOVING_SPEED_MPS -> FAST_MOVING_PREDICTION_TICK_MS
                    smoothedSpeedMps >= NORMAL_MOVING_SPEED_MPS -> NORMAL_MOVING_PREDICTION_TICK_MS
                    else -> SLOW_MOVING_PREDICTION_TICK_MS
                }
        }

    fun onHeading(
        headingDeg: Float,
        nowElapsedMs: Long,
    ) {
        val normalizedHeading = normalize360(headingDeg)
        if (hasHeading && lastHeadingAtMs > 0L) {
            val dtSec = ((nowElapsedMs - lastHeadingAtMs).coerceAtLeast(1L)) / 1000f
            val delta = abs(shortestAngleDelta(normalizedHeading, lastHeadingDeg))
            val instantaneousRate = delta / dtSec
            headingRateEmaDegPerSec =
                if (headingRateEmaDegPerSec <= 0f) {
                    instantaneousRate
                } else {
                    HEADING_RATE_EMA_ALPHA * instantaneousRate +
                        (1f - HEADING_RATE_EMA_ALPHA) * headingRateEmaDegPerSec
                }
        }
        hasHeading = true
        lastHeadingDeg = normalizedHeading
        lastHeadingAtMs = nowElapsedMs
    }

    fun onHeadingAccuracy(accuracyStatus: Int) {
        headingAccuracyStatus = accuracyStatus
    }

    fun onMagneticInterference(active: Boolean) {
        magneticInterferenceActive = active
    }

    fun onGpsFix(
        latLong: LatLong,
        speedMps: Float,
        accuracyM: Float,
        bearingDeg: Float?,
        nowElapsedMs: Long,
    ): LatLong {
        val previousFix = lastAcceptedFix
        val candidateAccuracyM = sanitizeAccuracy(accuracyM)

        var pendingConfirmed = false
        pendingFixConfirmation?.let { pending ->
            val matchDistance = distanceMeters(pending.latLong, latLong)
            val matchThreshold =
                pendingConfirmationThresholdMeters(
                    pendingAccuracyM = pending.accuracyM,
                    candidateAccuracyM = candidateAccuracyM,
                )
            if (matchDistance <= matchThreshold) {
                pendingConfirmed = true
                pendingFixConfirmation = null
                if (DebugTelemetry.isEnabled()) {
                    DebugTelemetry.log(
                        FUSION_TELEMETRY_TAG,
                        "confirmAccepted matchM=${matchDistance.format(1)} " +
                            "thresholdM=${matchThreshold.format(1)} ageMs=${(nowElapsedMs - pending.firstSeenElapsedMs)}",
                    )
                    maybeLogSummary(nowElapsedMs)
                }
            } else if (nowElapsedMs - pending.firstSeenElapsedMs > PENDING_CONFIRM_TIMEOUT_MS) {
                pendingFixConfirmation = null
                if (DebugTelemetry.isEnabled()) {
                    DebugTelemetry.log(
                        FUSION_TELEMETRY_TAG,
                        "confirmTimeout ageMs=${(nowElapsedMs - pending.firstSeenElapsedMs)}",
                    )
                    maybeLogSummary(nowElapsedMs)
                }
            }
        }

        if (!pendingConfirmed) {
            val outlierDrop = outlierDropDecision(previousFix, latLong, candidateAccuracyM, nowElapsedMs)
            if (outlierDrop != null) {
                if (DebugTelemetry.isEnabled()) {
                    DebugTelemetry.log(
                        FUSION_TELEMETRY_TAG,
                        "dropOutlier jumpM=${outlierDrop.jumpMeters.format(1)} " +
                            "impliedSpeed=${outlierDrop.impliedSpeedMps.format(1)} " +
                            "acc=${outlierDrop.accuracyM.format(1)} dt=${outlierDrop.dtSec.format(2)}",
                    )
                    FusionReplayTelemetry.recordOutlierDropped(
                        nowElapsedMs = nowElapsedMs,
                        jumpMeters = outlierDrop.jumpMeters,
                        impliedSpeedMps = outlierDrop.impliedSpeedMps,
                        accuracyM = outlierDrop.accuracyM,
                        dtSec = outlierDrop.dtSec,
                    )
                    maybeLogSummary(nowElapsedMs)
                }
                return predict(nowElapsedMs) ?: displayedLatLong ?: latLong
            }

            val confirmRequired =
                confirmRequiredDecision(
                    previous = previousFix,
                    candidateLatLong = latLong,
                    candidateAccuracyM = candidateAccuracyM,
                    nowElapsedMs = nowElapsedMs,
                )
            if (confirmRequired != null) {
                pendingFixConfirmation =
                    PendingFixConfirmation(
                        latLong = latLong,
                        accuracyM = candidateAccuracyM,
                        firstSeenElapsedMs = nowElapsedMs,
                    )
                if (DebugTelemetry.isEnabled()) {
                    DebugTelemetry.log(
                        FUSION_TELEMETRY_TAG,
                        "awaitConfirm jumpM=${confirmRequired.jumpMeters.format(1)} " +
                            "impliedSpeed=${confirmRequired.impliedSpeedMps.format(1)} " +
                            "acc=${confirmRequired.accuracyM.format(1)} dt=${confirmRequired.dtSec.format(2)}",
                    )
                    maybeLogSummary(nowElapsedMs)
                }
                return predict(nowElapsedMs) ?: displayedLatLong ?: latLong
            }
        }
        pendingFixConfirmation = null

        val sanitizedSpeed = sanitizeSpeed(speedMps)
        smoothedSpeedMps =
            if (smoothedSpeedMps <= 0f) {
                sanitizedSpeed
            } else {
                SPEED_EMA_ALPHA * sanitizedSpeed + (1f - SPEED_EMA_ALPHA) * smoothedSpeedMps
            }

        updateMotionState(smoothedSpeedMps, nowElapsedMs)

        val chosenBearing =
            chooseFixBearing(
                speedMps = sanitizedSpeed,
                bearingDeg = bearingDeg,
                fallbackBearingDeg = previousFix?.bearingDeg ?: lastHeadingDeg,
                nowElapsedMs = nowElapsedMs,
            )

        val accepted =
            AcceptedFix(
                latLong = latLong,
                elapsedMs = nowElapsedMs,
                speedMps = smoothedSpeedMps,
                bearingDeg = chosenBearing,
                accuracyM = candidateAccuracyM,
            )
        val currentDisplayed = predictInternal(nowElapsedMs)
        predictionRequiresFreshFix = false
        lastAcceptedFix = accepted

        val jumpMeters =
            if (currentDisplayed != null) {
                distanceMeters(currentDisplayed, latLong)
            } else {
                0f
            }
        val staleGapMs = previousFix?.let { (nowElapsedMs - it.elapsedMs).coerceAtLeast(0L) } ?: 0L
        if (staleGapMs > 0L) {
            observedFixGapEmaMs =
                if (observedFixGapEmaMs <= 0L) {
                    staleGapMs
                } else {
                    (
                        observedFixGapEmaMs * (1f - FIX_GAP_EMA_ALPHA) +
                            staleGapMs * FIX_GAP_EMA_ALPHA
                    ).toLong()
                }
        }
        val correctionStrategy =
            resolveCorrectionStrategy(
                currentDisplayed = currentDisplayed,
                jumpMeters = jumpMeters,
                accuracyM = accepted.accuracyM,
                staleGapMs = staleGapMs,
            )

        when (correctionStrategy) {
            CorrectionStrategy.SNAP -> {
                correctionBlend = null
                displayedLatLong = latLong
                if (DebugTelemetry.isEnabled()) {
                    DebugTelemetry.log(
                        FUSION_TELEMETRY_TAG,
                        "snapCorrection jumpM=${jumpMeters.format(1)} " +
                            "acc=${accepted.accuracyM.format(1)} staleGapMs=$staleGapMs",
                    )
                    FusionReplayTelemetry.recordFixAccepted(
                        nowElapsedMs = nowElapsedMs,
                        latLong = latLong,
                        accuracyM = accepted.accuracyM,
                        speedMps = accepted.speedMps,
                        bearingDeg = accepted.bearingDeg,
                        motionState = motionState.name,
                        jumpMeters = jumpMeters,
                        blendDurationMs = null,
                    )
                    maybeLogSummary(nowElapsedMs)
                }
                return latLong
            }

            CorrectionStrategy.SHORT_BLEND,
            CorrectionStrategy.NORMAL_BLEND,
            -> {
                val blendFrom = currentDisplayed ?: latLong
                val blendDuration =
                    if (correctionStrategy == CorrectionStrategy.SHORT_BLEND) {
                        SHORT_CORRECTION_BLEND_MS
                    } else {
                        computeBlendDurationMs(
                            currentDisplayed = blendFrom,
                            targetFix = accepted,
                            nowElapsedMs = nowElapsedMs,
                        )
                    }
                correctionBlend =
                    FusionCorrectionBlend(
                        from = blendFrom,
                        to = latLong,
                        startElapsedMs = nowElapsedMs,
                        durationMs = blendDuration,
                    )
                displayedLatLong = blendFrom
                if (DebugTelemetry.isEnabled()) {
                    if (correctionStrategy == CorrectionStrategy.SHORT_BLEND) {
                        DebugTelemetry.log(
                            FUSION_TELEMETRY_TAG,
                            "shortBlendCorrection jumpM=${jumpMeters.format(1)} " +
                                "acc=${accepted.accuracyM.format(1)} staleGapMs=$staleGapMs",
                        )
                    }
                    FusionReplayTelemetry.recordFixAccepted(
                        nowElapsedMs = nowElapsedMs,
                        latLong = latLong,
                        accuracyM = accepted.accuracyM,
                        speedMps = accepted.speedMps,
                        bearingDeg = accepted.bearingDeg,
                        motionState = motionState.name,
                        jumpMeters = jumpMeters,
                        blendDurationMs = blendDuration,
                    )
                    maybeLogSummary(nowElapsedMs)
                }
                return blendFrom
            }

            CorrectionStrategy.NONE -> {
                correctionBlend = null
                displayedLatLong = latLong
                if (DebugTelemetry.isEnabled()) {
                    FusionReplayTelemetry.recordFixAccepted(
                        nowElapsedMs = nowElapsedMs,
                        latLong = latLong,
                        accuracyM = accepted.accuracyM,
                        speedMps = accepted.speedMps,
                        bearingDeg = accepted.bearingDeg,
                        motionState = motionState.name,
                        jumpMeters = jumpMeters,
                        blendDurationMs = null,
                    )
                    maybeLogSummary(nowElapsedMs)
                }
                return latLong
            }
        }
    }

    fun predict(nowElapsedMs: Long): LatLong? {
        val predicted = predictInternal(nowElapsedMs) ?: return displayedLatLong
        displayedLatLong = predicted
        maybeRecordPredictionSample(nowElapsedMs, predicted)
        maybeLogSummary(nowElapsedMs)
        return predicted
    }

    private fun predictInternal(nowElapsedMs: Long): LatLong? {
        if (predictionRequiresFreshFix) {
            return displayedLatLong
        }

        correctionBlend?.let { blend ->
            val elapsed = (nowElapsedMs - blend.startElapsedMs).coerceAtLeast(0L)
            val t = (elapsed.toFloat() / blend.durationMs.toFloat()).coerceIn(0f, 1f)
            val blended = lerpLatLong(blend.from, blend.to, t)
            if (t >= 1f) correctionBlend = null
            return blended
        }

        val fix = lastAcceptedFix ?: return displayedLatLong
        val elapsedMs = (nowElapsedMs - fix.elapsedMs).coerceAtLeast(0L)
        val staleThresholdMs = effectiveStaleThresholdMs()

        if (motionState == MotionState.STATIONARY) {
            return fix.latLong
        }
        if (elapsedMs <= EARLY_PREDICTION_DELAY_MS) {
            return fix.latLong
        }

        val adaptivePredictionHorizonMs = effectivePredictionHorizonMs(staleThresholdMs)
        val rawPredictionMs = (elapsedMs - EARLY_PREDICTION_DELAY_MS).coerceAtLeast(0L)
        val predictionProfile =
            resolvePredictionProfile(
                fix = fix,
                predictionAgeMs = rawPredictionMs,
                adaptivePredictionHorizonMs = adaptivePredictionHorizonMs,
                nowElapsedMs = nowElapsedMs,
            )
        if (predictionProfile.horizonScale < MIN_PREDICTION_HORIZON_SCALE ||
            predictionProfile.speedScale < MIN_PREDICTION_SPEED_SCALE
        ) {
            return fix.latLong
        }

        val predictionMs =
            rawPredictionMs
                .coerceAtMost(
                    (adaptivePredictionHorizonMs * predictionProfile.horizonScale)
                        .toLong()
                        .coerceAtLeast(0L),
                )
        val predictionSpeedMps = (fix.speedMps * predictionProfile.speedScale).coerceAtLeast(0f)
        if (predictionSpeedMps < MIN_PREDICTION_SPEED_MPS) {
            return fix.latLong
        }

        val predictionBearing = choosePredictionBearing(fix, nowElapsedMs)
        val distanceMeters = predictionSpeedMps * (predictionMs / 1000f)
        if (distanceMeters < MIN_PREDICTION_DISTANCE_METERS) {
            return fix.latLong
        }

        return moveLatLong(
            start = fix.latLong,
            bearing = predictionBearing,
            distanceMeters = distanceMeters,
        )
    }

    private fun choosePredictionBearing(
        fix: AcceptedFix,
        nowElapsedMs: Long,
    ): Float {
        val headingConfidence = headingSignalConfidence(nowElapsedMs)
        if (headingConfidence >= MIN_HEADING_BLEND_CONFIDENCE &&
            motionState == MotionState.MOVING &&
            fix.speedMps < HEADING_BEARING_SPEED_LIMIT_MPS
        ) {
            return blendAngles(
                a = fix.bearingDeg,
                b = lastHeadingDeg,
                alpha = HEADING_BEARING_BLEND_ALPHA * headingConfidence,
            )
        }
        return fix.bearingDeg
    }

    private fun chooseFixBearing(
        speedMps: Float,
        bearingDeg: Float?,
        fallbackBearingDeg: Float,
        nowElapsedMs: Long,
    ): Float {
        if (bearingDeg != null && speedMps >= GPS_BEARING_SPEED_MIN_MPS) {
            return normalize360(bearingDeg)
        }
        if (headingSignalConfidence(nowElapsedMs) >= MIN_HEADING_FIX_CONFIDENCE) {
            return lastHeadingDeg
        }
        return normalize360(fallbackBearingDeg)
    }

    private fun isHeadingReliable(nowElapsedMs: Long): Boolean = headingSignalConfidence(nowElapsedMs) >= MIN_HEADING_RELIABLE_CONFIDENCE

    private fun headingSignalConfidence(nowElapsedMs: Long): Float {
        if (!hasHeading || lastHeadingAtMs == 0L) return 0f
        val ageMs = nowElapsedMs - lastHeadingAtMs
        if (ageMs > MAX_HEADING_AGE_MS) return 0f
        if (magneticInterferenceActive) return 0f
        if (headingRateEmaDegPerSec > MAX_RELIABLE_HEADING_RATE_DEG_PER_SEC) return 0f

        val accuracyConfidence =
            when (headingAccuracyStatus) {
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> 1f
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> 0.82f
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> 0.52f
                else -> 0f
            }
        if (accuracyConfidence <= 0f) return 0f

        val ageConfidence =
            when {
                ageMs <= HEADING_AGE_CONFIDENT_MS -> 1f
                ageMs <= HEADING_AGE_DEGRADED_MS -> 0.8f
                else -> 0.6f
            }
        val turnConfidence =
            when {
                headingRateEmaDegPerSec <= HEADING_RATE_CONFIDENT_DEG_PER_SEC -> 1f
                headingRateEmaDegPerSec <= HEADING_RATE_DEGRADED_DEG_PER_SEC -> 0.8f
                else -> 0.55f
            }
        return minOf(accuracyConfidence, ageConfidence, turnConfidence)
    }

    private fun resolvePredictionProfile(
        fix: AcceptedFix,
        predictionAgeMs: Long,
        adaptivePredictionHorizonMs: Long,
        nowElapsedMs: Long,
    ): PredictionProfile {
        val speedScale =
            when {
                fix.speedMps < VERY_SLOW_PREDICTION_SPEED_MPS -> 0f
                fix.speedMps < SLOW_PREDICTION_SPEED_MPS -> 0.50f
                fix.speedMps < NORMAL_PREDICTION_SPEED_MPS -> 0.74f
                fix.speedMps < FAST_MOVING_SPEED_MPS -> 0.88f
                else -> 1f
            }
        val accuracyScale =
            when {
                fix.accuracyM <= PREDICTION_GOOD_ACCURACY_M -> 1f
                fix.accuracyM <= PREDICTION_OK_ACCURACY_M -> 0.85f
                fix.accuracyM <= PREDICTION_SOFT_LIMIT_ACCURACY_M -> 0.62f
                fix.accuracyM <= PREDICTION_HARD_LIMIT_ACCURACY_M -> 0.35f
                else -> 0f
            }
        val horizonUsageRatio =
            if (adaptivePredictionHorizonMs > 0L) {
                predictionAgeMs.toFloat() / adaptivePredictionHorizonMs.toFloat()
            } else {
                1f
            }
        val freshnessScale =
            when {
                horizonUsageRatio <= PREDICTION_FRESH_USAGE_RATIO -> 1f
                horizonUsageRatio <= PREDICTION_AGING_USAGE_RATIO -> 0.82f
                horizonUsageRatio <= PREDICTION_STALE_USAGE_RATIO -> 0.58f
                else -> 0.34f
            }
        val headingConfidence = headingSignalConfidence(nowElapsedMs)
        val directionScale =
            if (fix.speedMps >= GPS_BEARING_SPEED_MIN_MPS) {
                1f
            } else {
                when {
                    headingConfidence >= 0.8f -> 1f
                    headingConfidence >= MIN_HEADING_BLEND_CONFIDENCE -> 0.72f
                    else -> 0.4f
                }
            }
        return PredictionProfile(
            horizonScale = minOf(accuracyScale, freshnessScale),
            speedScale = speedScale * directionScale,
        )
    }

    private fun updateMotionState(
        speedMps: Float,
        nowElapsedMs: Long,
    ) {
        val previousState = motionState
        when (motionState) {
            MotionState.MOVING -> {
                movingCandidateSinceMs = 0L
                if (speedMps <= STATIONARY_SPEED_ENTER_MPS) {
                    if (stationaryCandidateSinceMs == 0L) stationaryCandidateSinceMs = nowElapsedMs
                    if (nowElapsedMs - stationaryCandidateSinceMs >= STATIONARY_CONFIRM_MS) {
                        motionState = MotionState.STATIONARY
                    }
                } else {
                    stationaryCandidateSinceMs = 0L
                }
            }

            MotionState.STATIONARY -> {
                stationaryCandidateSinceMs = 0L
                if (speedMps >= MOVING_SPEED_EXIT_MPS) {
                    if (movingCandidateSinceMs == 0L) movingCandidateSinceMs = nowElapsedMs
                    if (nowElapsedMs - movingCandidateSinceMs >= MOVING_CONFIRM_MS) {
                        motionState = MotionState.MOVING
                    }
                } else {
                    movingCandidateSinceMs = 0L
                }
            }
        }
        if (motionState != previousState && DebugTelemetry.isEnabled()) {
            DebugTelemetry.log(
                FUSION_TELEMETRY_TAG,
                "motion ${previousState.name} -> ${motionState.name} speed=${speedMps.format(2)}",
            )
            FusionReplayTelemetry.recordMotionTransition(
                nowElapsedMs = nowElapsedMs,
                fromState = previousState.name,
                toState = motionState.name,
                speedMps = speedMps,
            )
        }
    }

    private fun outlierDropDecision(
        previous: AcceptedFix?,
        candidateLatLong: LatLong,
        candidateAccuracyM: Float,
        nowElapsedMs: Long,
    ): OutlierDropDecision? {
        val prev = previous ?: return null
        val dtSec = ((nowElapsedMs - prev.elapsedMs).coerceAtLeast(0L)) / 1000f
        if (dtSec < MIN_OUTLIER_DT_SEC) return null

        val candidateAcc = sanitizeAccuracy(candidateAccuracyM)
        val jumpDistanceM = distanceMeters(prev.latLong, candidateLatLong)
        val impliedSpeed = jumpDistanceM / dtSec

        if (impliedSpeed <= OUTLIER_MAX_IMPLIED_SPEED_MPS) return null
        if (candidateAcc <= OUTLIER_MIN_ACCURACY_M) return null

        val allowedJumpM = prev.accuracyM + candidateAcc + OUTLIER_JUMP_MARGIN_M
        if (jumpDistanceM <= allowedJumpM) return null

        return OutlierDropDecision(
            jumpMeters = jumpDistanceM,
            impliedSpeedMps = impliedSpeed,
            dtSec = dtSec,
            accuracyM = candidateAcc,
        )
    }

    private fun confirmRequiredDecision(
        previous: AcceptedFix?,
        candidateLatLong: LatLong,
        candidateAccuracyM: Float,
        nowElapsedMs: Long,
    ): ConfirmRequiredDecision? {
        val prev = previous ?: return null
        val dtSec = ((nowElapsedMs - prev.elapsedMs).coerceAtLeast(0L)) / 1000f
        if (dtSec < MIN_OUTLIER_DT_SEC) return null

        val candidateAcc = sanitizeAccuracy(candidateAccuracyM)
        val jumpDistanceM = distanceMeters(prev.latLong, candidateLatLong)
        val impliedSpeed = jumpDistanceM / dtSec
        val allowedJumpM = prev.accuracyM + candidateAcc + OUTLIER_JUMP_MARGIN_M
        val confirmBoundaryM = allowedJumpM * OUTLIER_CONFIRM_DISTANCE_RATIO

        if (jumpDistanceM <= confirmBoundaryM) return null
        if (impliedSpeed <= OUTLIER_CONFIRM_IMPLIED_SPEED_MPS) return null
        if (candidateAcc <= OUTLIER_CONFIRM_MIN_ACCURACY_M) return null

        return ConfirmRequiredDecision(
            jumpMeters = jumpDistanceM,
            impliedSpeedMps = impliedSpeed,
            dtSec = dtSec,
            accuracyM = candidateAcc,
        )
    }

    private fun pendingConfirmationThresholdMeters(
        pendingAccuracyM: Float,
        candidateAccuracyM: Float,
    ): Float {
        val threshold = pendingAccuracyM + candidateAccuracyM + OUTLIER_CONFIRM_MATCH_MARGIN_M
        return threshold.coerceIn(OUTLIER_CONFIRM_MIN_MATCH_M, OUTLIER_CONFIRM_MAX_MATCH_M)
    }

    private fun effectiveStaleThresholdMs(): Long =
        if (motionState == MotionState.STATIONARY) {
            (staleGpsThresholdMs * STATIONARY_STALE_THRESHOLD_FACTOR)
                .toLong()
                .coerceIn(staleGpsThresholdMs, MAX_STATIONARY_STALE_THRESHOLD_MS)
        } else {
            (staleGpsThresholdMs * MOVING_STALE_THRESHOLD_FACTOR)
                .toLong()
                .coerceAtLeast(MIN_MOVING_STALE_THRESHOLD_MS)
        }

    private fun effectivePredictionHorizonMs(staleThresholdMs: Long): Long {
        val gapDrivenHorizonMs =
            if (observedFixGapEmaMs > 0L) {
                observedFixGapEmaMs + PREDICTION_GAP_HEADROOM_MS
            } else {
                0L
            }
        val staleDrivenHorizonMs = staleThresholdMs * PREDICTION_HORIZON_STALE_MULTIPLIER
        return minOf(
            predictionHorizonMs,
            maxOf(
                MIN_EFFECTIVE_PREDICTION_HORIZON_MS,
                gapDrivenHorizonMs,
                staleDrivenHorizonMs,
            ),
        ).coerceAtMost(MAX_ADAPTIVE_PREDICTION_HORIZON_MS)
    }

    private fun computeBlendDurationMs(
        currentDisplayed: LatLong,
        targetFix: AcceptedFix,
        nowElapsedMs: Long,
    ): Long {
        val jumpDistanceM = distanceMeters(currentDisplayed, targetFix.latLong)
        var durationMs =
            when {
                targetFix.accuracyM <= 8f -> 260L
                targetFix.accuracyM <= 15f -> 380L
                targetFix.accuracyM <= 25f -> 520L
                else -> 700L
            }
        if (jumpDistanceM > 60f) durationMs += 140L
        if (motionState == MotionState.STATIONARY) durationMs += 90L
        if (!isHeadingReliable(nowElapsedMs)) durationMs += 80L
        return durationMs.coerceIn(minBlendDurationMs, maxBlendDurationMs)
    }

    private fun resolveCorrectionStrategy(
        currentDisplayed: LatLong?,
        jumpMeters: Float,
        accuracyM: Float,
        staleGapMs: Long,
    ): CorrectionStrategy {
        if (currentDisplayed == null) return CorrectionStrategy.NONE
        if (jumpMeters <= MIN_BLEND_DISTANCE_METERS) return CorrectionStrategy.NONE
        if (jumpMeters < CORRECTION_MEDIUM_MIN_DISTANCE_METERS) return CorrectionStrategy.NONE

        val isLargeConfidentCorrection =
            jumpMeters >= CORRECTION_LARGE_MIN_DISTANCE_METERS &&
                accuracyM <= CORRECTION_CONFIDENT_ACCURACY_M
        if (isLargeConfidentCorrection) {
            return if (staleGapMs >= correctionStaleGapMs) {
                CorrectionStrategy.SNAP
            } else {
                CorrectionStrategy.SHORT_BLEND
            }
        }

        if (jumpMeters >= CORRECTION_LARGE_MIN_DISTANCE_METERS &&
            staleGapMs >= correctionStaleGapMs
        ) {
            return CorrectionStrategy.SHORT_BLEND
        }

        return CorrectionStrategy.NORMAL_BLEND
    }

    private fun sanitizeSpeed(speedMps: Float): Float {
        if (!speedMps.isFinite()) return 0f
        return speedMps.coerceIn(0f, MAX_ACCEPTED_GPS_SPEED_MPS)
    }

    private fun sanitizeAccuracy(accuracyM: Float): Float {
        if (!accuracyM.isFinite()) return 99f
        return accuracyM.coerceAtLeast(0f)
    }

    private fun maybeRecordPredictionSample(
        nowElapsedMs: Long,
        predicted: LatLong,
    ) {
        if (!DebugTelemetry.isEnabled()) return
        if (lastReplayPredictionAtMs != 0L &&
            nowElapsedMs - lastReplayPredictionAtMs < REPLAY_PREDICT_SAMPLE_MS
        ) {
            return
        }

        val fix = lastAcceptedFix
        val staleMs =
            if (fix != null) {
                (nowElapsedMs - fix.elapsedMs).coerceAtLeast(0L)
            } else {
                0L
            }
        FusionReplayTelemetry.recordPrediction(
            nowElapsedMs = nowElapsedMs,
            latLong = predicted,
            speedMps = fix?.speedMps ?: 0f,
            bearingDeg = fix?.bearingDeg ?: lastHeadingDeg,
            motionState = motionState.name,
            staleMs = staleMs,
        )
        lastReplayPredictionAtMs = nowElapsedMs
    }

    private fun maybeLogSummary(nowElapsedMs: Long) {
        if (!DebugTelemetry.isEnabled()) return
        if (lastSummaryLogAtMs == 0L) {
            lastSummaryLogAtMs = nowElapsedMs
            return
        }
        if (nowElapsedMs - lastSummaryLogAtMs < SUMMARY_LOG_INTERVAL_MS) return
        lastSummaryLogAtMs = nowElapsedMs
        val summary = FusionReplayTelemetry.summary()
        DebugTelemetry.log(
            FUSION_TELEMETRY_TAG,
            "summary fix=${summary.acceptedFixes} drop=${summary.outlierDrops} " +
                "pred=${summary.predictions} blend=${summary.blendStarts} " +
                "avgBlendMs=${summary.avgBlendDurationMs} " +
                "toStationary=${summary.toStationary} toMoving=${summary.toMoving} " +
                "maxJumpM=${summary.maxJumpMeters.format(1)} " +
                "maxImpliedSpeed=${summary.maxImpliedSpeedMps.format(1)}",
        )
    }
}

private enum class MotionState { MOVING, STATIONARY }

private data class AcceptedFix(
    val latLong: LatLong,
    val elapsedMs: Long,
    val speedMps: Float,
    val bearingDeg: Float,
    val accuracyM: Float,
)

private data class FusionCorrectionBlend(
    val from: LatLong,
    val to: LatLong,
    val startElapsedMs: Long,
    val durationMs: Long,
)

private data class OutlierDropDecision(
    val jumpMeters: Float,
    val impliedSpeedMps: Float,
    val dtSec: Float,
    val accuracyM: Float,
)

private data class ConfirmRequiredDecision(
    val jumpMeters: Float,
    val impliedSpeedMps: Float,
    val dtSec: Float,
    val accuracyM: Float,
)

private data class PendingFixConfirmation(
    val latLong: LatLong,
    val accuracyM: Float,
    val firstSeenElapsedMs: Long,
)

private data class PredictionProfile(
    val horizonScale: Float,
    val speedScale: Float,
)

private enum class CorrectionStrategy {
    NONE,
    NORMAL_BLEND,
    SHORT_BLEND,
    SNAP,
}

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val MIN_BLEND_DISTANCE_METERS = 0.8f
private const val MIN_PREDICTION_DISTANCE_METERS = 0.1f
private const val MIN_PREDICTION_SPEED_MPS = 0.12f
private const val MIN_PREDICTION_HORIZON_SCALE = 0.2f
private const val MIN_PREDICTION_SPEED_SCALE = 0.18f
private const val MIN_EFFECTIVE_PREDICTION_HORIZON_MS = 1_000L
private const val EARLY_PREDICTION_DELAY_MS = 120L
private const val CORRECTION_MEDIUM_MIN_DISTANCE_METERS = 5f
private const val CORRECTION_LARGE_MIN_DISTANCE_METERS = 25f
private const val CORRECTION_CONFIDENT_ACCURACY_M = 20f
private const val SHORT_CORRECTION_BLEND_MS = 180L

private const val SPEED_EMA_ALPHA = 0.50f

private const val STATIONARY_SPEED_ENTER_MPS = 0.35f
private const val STATIONARY_CONFIRM_MS = 4_000L
private const val MOVING_SPEED_EXIT_MPS = 0.65f
private const val MOVING_CONFIRM_MS = 1_800L

private const val GPS_BEARING_SPEED_MIN_MPS = 1.1f
private const val HEADING_BEARING_SPEED_LIMIT_MPS = 1.6f
private const val HEADING_BEARING_BLEND_ALPHA = 0.35f
private const val MIN_HEADING_BLEND_CONFIDENCE = 0.45f
private const val MIN_HEADING_FIX_CONFIDENCE = 0.52f
private const val MIN_HEADING_RELIABLE_CONFIDENCE = 0.45f

private const val MAX_HEADING_AGE_MS = 1_500L
private const val HEADING_AGE_CONFIDENT_MS = 450L
private const val HEADING_AGE_DEGRADED_MS = 900L
private const val HEADING_RATE_CONFIDENT_DEG_PER_SEC = 22f
private const val HEADING_RATE_DEGRADED_DEG_PER_SEC = 38f
private const val MAX_RELIABLE_HEADING_RATE_DEG_PER_SEC = 55f
private const val HEADING_RATE_EMA_ALPHA = 0.28f

private const val VERY_SLOW_PREDICTION_SPEED_MPS = 0.45f
private const val SLOW_PREDICTION_SPEED_MPS = 0.75f
private const val NORMAL_PREDICTION_SPEED_MPS = 1.15f
private const val PREDICTION_GOOD_ACCURACY_M = 10f
private const val PREDICTION_OK_ACCURACY_M = 18f
private const val PREDICTION_SOFT_LIMIT_ACCURACY_M = 28f
private const val PREDICTION_HARD_LIMIT_ACCURACY_M = 40f
private const val PREDICTION_FRESH_USAGE_RATIO = 0.35f
private const val PREDICTION_AGING_USAGE_RATIO = 0.60f
private const val PREDICTION_STALE_USAGE_RATIO = 0.85f

private const val MIN_OUTLIER_DT_SEC = 1.0f
private const val OUTLIER_MAX_IMPLIED_SPEED_MPS = 10f
private const val OUTLIER_MIN_ACCURACY_M = 12f
private const val OUTLIER_JUMP_MARGIN_M = 30f
private const val OUTLIER_CONFIRM_IMPLIED_SPEED_MPS = 6.0f
private const val OUTLIER_CONFIRM_MIN_ACCURACY_M = 8f
private const val OUTLIER_CONFIRM_DISTANCE_RATIO = 0.80f
private const val OUTLIER_CONFIRM_MATCH_MARGIN_M = 12f
private const val OUTLIER_CONFIRM_MIN_MATCH_M = 12f
private const val OUTLIER_CONFIRM_MAX_MATCH_M = 60f
private const val PENDING_CONFIRM_TIMEOUT_MS = 12_000L
private const val MOVING_STALE_THRESHOLD_FACTOR = 0.72f
private const val STATIONARY_STALE_THRESHOLD_FACTOR = 1.8f
private const val MIN_MOVING_STALE_THRESHOLD_MS = 1_500L
private const val MAX_STATIONARY_STALE_THRESHOLD_MS = 5_000L
private const val FIX_GAP_EMA_ALPHA = 0.35f
private const val PREDICTION_GAP_HEADROOM_MS = 4_000L
private const val PREDICTION_HORIZON_STALE_MULTIPLIER = 4L
private const val MAX_ADAPTIVE_PREDICTION_HORIZON_MS = 40_000L
private const val REPLAY_PREDICT_SAMPLE_MS = 1_000L
private const val SUMMARY_LOG_INTERVAL_MS = 15_000L
private const val FUSION_TELEMETRY_TAG = "FusionTelemetry"
private const val FAST_MOVING_SPEED_MPS = 1.6f
private const val NORMAL_MOVING_SPEED_MPS = 0.8f
private const val FAST_MOVING_PREDICTION_TICK_MS = 200L
private const val NORMAL_MOVING_PREDICTION_TICK_MS = 250L
private const val SLOW_MOVING_PREDICTION_TICK_MS = 300L
private const val STATIONARY_PREDICTION_TICK_MS = 700L
private const val MAX_ACCEPTED_GPS_SPEED_MPS = 8f

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
    val h =
        sinHalfLat * sinHalfLat +
            cos(lat1) * cos(lat2) * sinHalfLon * sinHalfLon
    val c = 2.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    return (EARTH_RADIUS_METERS * c).toFloat()
}

private fun lerpLatLong(
    from: LatLong,
    to: LatLong,
    t: Float,
): LatLong {
    val blend = t.coerceIn(0f, 1f).toDouble()
    return LatLong(
        from.latitude + (to.latitude - from.latitude) * blend,
        from.longitude + (to.longitude - from.longitude) * blend,
    )
}

private fun normalize360(value: Float): Float {
    var result = value % 360f
    if (result < 0f) result += 360f
    return result
}

private fun shortestAngleDelta(
    target: Float,
    current: Float,
): Float {
    var delta = normalize360(target) - normalize360(current)
    while (delta > 180f) delta -= 360f
    while (delta < -180f) delta += 360f
    return delta
}

private fun blendAngles(
    a: Float,
    b: Float,
    alpha: Float,
): Float {
    val delta = shortestAngleDelta(b, a)
    return normalize360(a + alpha.coerceIn(0f, 1f) * delta)
}

private fun Float.format(digits: Int): String = "%.${digits}f".format(this)
