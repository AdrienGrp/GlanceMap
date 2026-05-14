package com.glancemap.glancemapwearos.presentation.features.navigate.motion

import com.glancemap.glancemapwearos.core.service.location.config.WATCH_GPS_ACCURACY_FLOOR_M
import com.glancemap.glancemapwearos.core.service.location.config.WATCH_GPS_ACCURACY_FLOOR_TOLERANCE_M
import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode
import com.glancemap.glancemapwearos.presentation.features.navigate.moveLatLong
import org.mapsforge.core.model.LatLong
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal data class MarkerMotionReading(
    val fixElapsedMs: Long,
    val accuracyM: Float,
    val speedMps: Float,
    val bearingDeg: Float?,
)

internal data class MarkerMotionSeed(
    val latLong: LatLong,
    val reading: MarkerMotionReading,
    val sourceMode: LocationSourceMode = LocationSourceMode.AUTO_FUSED,
)

internal data class MarkerMotionGpsFix(
    val latLong: LatLong,
    val nowElapsedMs: Long,
    val reading: MarkerMotionReading,
    val allowLargeCorrection: Boolean = false,
    val sourceMode: LocationSourceMode = LocationSourceMode.AUTO_FUSED,
)

internal class MarkerMotionController(
    private val predictionFreshnessMaxAgeMs: Long,
    private val maxAcceptedFixAgeMs: Long,
    private val maxPredictionAccuracyM: Float = DEFAULT_MAX_PREDICTION_ACCURACY_M,
    private val minPredictionSpeedMps: Float = DEFAULT_MIN_PREDICTION_SPEED_MPS,
    private val correctionBlendDurationMs: Long = DEFAULT_CORRECTION_BLEND_DURATION_MS,
    private val predictionTickMs: Long = DEFAULT_PREDICTION_TICK_MS,
) {
    private val state = MarkerMotionState()
    private val fixProcessor =
        MarkerMotionGpsFixProcessor(
            state = state,
            maxAcceptedFixAgeMs = maxAcceptedFixAgeMs,
            minPredictionSpeedMps = minPredictionSpeedMps,
            correctionBlendDurationMs = correctionBlendDurationMs,
        )

    fun reset(reason: String = "reset") {
        state.lastAcceptedFix = null
        state.displayedLatLong = null
        state.correctionBlend = null
        state.smoothedSpeedMps = 0f
        state.predictionRequiresFreshFix = true
        state.clampedCorrectionStreak = 0
        MarkerMotionTelemetry.recordIdle(
            nowElapsedMs = 0L,
            reason = reason,
        )
    }

    fun seedAnchor(seed: MarkerMotionSeed) {
        val sanitizedSpeed = sanitizeSpeed(seed.reading.speedMps)
        val motionAccuracyM = effectiveMotionAccuracy(seed.reading.accuracyM, seed.sourceMode)
        val fixElapsedMs = seed.reading.fixElapsedMs.coerceAtLeast(0L)
        state.smoothedSpeedMps = sanitizedSpeed
        state.lastAcceptedFix =
            MotionFix(
                latLong = seed.latLong,
                fixElapsedMs = fixElapsedMs,
                accuracyM = motionAccuracyM,
                speedMps = sanitizedSpeed,
                bearingDeg = seed.reading.bearingDeg?.let(::normalize360),
                sourceMode = seed.sourceMode,
            )
        state.displayedLatLong = seed.latLong
        state.correctionBlend = null
        state.predictionRequiresFreshFix = true
        state.clampedCorrectionStreak = 0
        MarkerMotionTelemetry.recordSeedAnchor(
            nowElapsedMs = fixElapsedMs,
            accuracyM = motionAccuracyM,
            speedMps = sanitizedSpeed,
            bearingDeg = seed.reading.bearingDeg?.let(::normalize360),
        )
    }

    fun requireFreshFixForPrediction() {
        state.predictionRequiresFreshFix = true
        state.correctionBlend = null
        MarkerMotionTelemetry.recordPredictionBlocked(
            reason = "await_fresh_fix",
            nowElapsedMs = state.lastAcceptedFix?.fixElapsedMs ?: 0L,
            fixAgeMs = null,
            accuracyM = state.lastAcceptedFix?.accuracyM,
            speedMps = state.lastAcceptedFix?.speedMps,
            bearingDeg = state.lastAcceptedFix?.bearingDeg,
        )
    }

    fun suggestedPredictionTickMs(): Long = predictionTickMs

    fun onGpsFix(fix: MarkerMotionGpsFix): LatLong = fixProcessor.onGpsFix(fix)

    fun predict(
        nowElapsedMs: Long,
        serviceFreshnessMaxAgeMs: Long,
        watchGpsDegraded: Boolean,
    ): LatLong? {
        var currentDisplayed = state.displayedLatLong ?: state.lastAcceptedFix?.latLong ?: return null

        state.correctionBlend?.let { blend ->
            val elapsedMs = (nowElapsedMs - blend.startElapsedMs).coerceAtLeast(0L)
            val fraction = (elapsedMs.toFloat() / blend.durationMs.toFloat()).coerceIn(0f, 1f)
            val blended = lerpLatLong(blend.from, blend.to, fraction)
            state.displayedLatLong = blended
            state.lastAcceptedFix?.let { fix ->
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
            state.correctionBlend = null
            currentDisplayed = blended
        }

        if (watchGpsDegraded || state.predictionRequiresFreshFix) {
            val fix = state.lastAcceptedFix
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

        val fix = state.lastAcceptedFix ?: return currentDisplayed
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
        state.displayedLatLong = predicted
        return predicted
    }
}

@Suppress("TooManyFunctions")
private class MarkerMotionGpsFixProcessor(
    private val state: MarkerMotionState,
    private val maxAcceptedFixAgeMs: Long,
    private val minPredictionSpeedMps: Float,
    private val correctionBlendDurationMs: Long,
) {
    fun onGpsFix(fix: MarkerMotionGpsFix): LatLong {
        val context = buildGpsFixContext(fix)
        return rejectGpsFix(context) ?: acceptGpsFix(context)
    }

    private fun buildGpsFixContext(fix: MarkerMotionGpsFix): GpsFixContext {
        val reliableFixElapsedMs =
            fix.reading.fixElapsedMs
                .takeIf { it > 0L }
                ?.coerceAtMost(fix.nowElapsedMs)
                ?: fix.nowElapsedMs
        return GpsFixContext(
            fix = fix,
            timing =
                GpsFixTiming(
                    reliableFixElapsedMs = reliableFixElapsedMs,
                    fixAgeMs = (fix.nowElapsedMs - reliableFixElapsedMs).coerceAtLeast(0L),
                ),
            accuracyM = effectiveMotionAccuracy(fix.reading.accuracyM, fix.sourceMode),
            currentDisplayed = state.displayedLatLong,
            previousFix = state.lastAcceptedFix,
        )
    }

    private fun rejectGpsFix(context: GpsFixContext): LatLong? =
        when {
            isStaleGpsFix(context) -> rejectBlockedGpsFix(context, "stale_fix", context.currentDisplayed)
            else -> rejectDuplicateGpsFix(context) ?: rejectOutlierGpsFix(context)
        }

    private fun isStaleGpsFix(context: GpsFixContext): Boolean {
        val hasDisplayedMarker = context.currentDisplayed != null
        val fixIsTooOld = context.timing.fixAgeMs > maxAcceptedFixAgeMs
        return hasDisplayedMarker && fixIsTooOld
    }

    private fun rejectBlockedGpsFix(
        context: GpsFixContext,
        reason: String,
        displayLatLong: LatLong?,
    ): LatLong? {
        recordBlockedGpsFix(context, reason)
        return displayLatLong
    }

    private fun rejectDuplicateGpsFix(context: GpsFixContext): LatLong? {
        val previousFix = context.previousFix ?: return null
        val isDuplicate =
            isDuplicateMotionFix(
                previousFix = previousFix,
                candidate = context.fix.latLong,
                candidateFixElapsedMs = context.timing.reliableFixElapsedMs,
                candidateAccuracyM = context.accuracyM,
            )
        return if (isDuplicate) {
            rejectBlockedGpsFix(
                context = context,
                reason = "duplicate_fix",
                displayLatLong = context.currentDisplayed ?: previousFix.latLong,
            )
        } else {
            null
        }
    }

    private fun rejectOutlierGpsFix(context: GpsFixContext): LatLong? {
        val previousFix = context.previousFix
        if (previousFix != null && isSourceModeTransition(context)) {
            return null
        }
        val outlierDecision =
            previousFix?.detectOutlier(
                candidate = context.fix.latLong,
                candidateAccuracyM = context.accuracyM,
                candidateFixElapsedMs = context.timing.reliableFixElapsedMs,
            )
        return if (previousFix != null && outlierDecision != null) {
            state.correctionBlend = null
            state.clampedCorrectionStreak = 0
            MarkerMotionTelemetry.recordOutlierDropped(
                nowElapsedMs = context.fix.nowElapsedMs,
                fixAgeMs = context.timing.fixAgeMs,
                accuracyM = context.accuracyM,
                jumpMeters = outlierDecision.jumpMeters,
                impliedSpeedMps = outlierDecision.impliedSpeedMps,
                dtSec = outlierDecision.dtSec,
            )
            context.currentDisplayed ?: previousFix.latLong
        } else {
            null
        }
    }

    private fun recordBlockedGpsFix(
        context: GpsFixContext,
        reason: String,
    ) {
        MarkerMotionTelemetry.recordPredictionBlocked(
            reason = reason,
            nowElapsedMs = context.fix.nowElapsedMs,
            fixAgeMs = context.timing.fixAgeMs,
            accuracyM = context.accuracyM,
            speedMps =
                context.fix.reading.speedMps
                    .takeIf { it.isFinite() },
            bearingDeg =
                context.fix.reading.bearingDeg
                    ?.takeIf { it.isFinite() },
        )
    }

    private fun acceptGpsFix(context: GpsFixContext): LatLong {
        val motion = resolveAcceptedMotion(context)
        state.lastAcceptedFix =
            MotionFix(
                latLong = context.fix.latLong,
                fixElapsedMs = context.timing.reliableFixElapsedMs,
                accuracyM = context.accuracyM,
                speedMps = motion.speedMps,
                bearingDeg = motion.bearingDeg,
                sourceMode = context.fix.sourceMode,
            )
        state.predictionRequiresFreshFix = false
        return applyAcceptedGpsFix(context, motion)
    }

    private fun resolveAcceptedMotion(context: GpsFixContext): ResolvedMotion {
        val derivedMotion =
            context.previousFix?.deriveMotionTo(
                target = context.fix.latLong,
                targetFixElapsedMs = context.timing.reliableFixElapsedMs,
            )
        val resolvedSpeedMps =
            resolveMotionSpeedMps(
                rawSpeedMps = context.fix.reading.speedMps,
                derivedSpeedMps = derivedMotion?.speedMps,
                accuracyM = context.accuracyM,
            )
        state.smoothedSpeedMps = smoothMotionSpeed(resolvedSpeedMps)
        return ResolvedMotion(
            speedMps = state.smoothedSpeedMps,
            bearingDeg =
                resolveMotionBearingDeg(
                    rawBearingDeg = context.fix.reading.bearingDeg,
                    rawSpeedMps = context.fix.reading.speedMps,
                    derivedMotion = derivedMotion,
                    fallbackBearingDeg = context.previousFix?.bearingDeg,
                    resolvedSpeedMps = state.smoothedSpeedMps,
                ),
        )
    }

    private fun smoothMotionSpeed(resolvedSpeedMps: Float): Float =
        if (state.smoothedSpeedMps <= 0f) {
            resolvedSpeedMps
        } else {
            SPEED_SMOOTHING_ALPHA * resolvedSpeedMps +
                (1f - SPEED_SMOOTHING_ALPHA) * state.smoothedSpeedMps
        }

    private fun applyAcceptedGpsFix(
        context: GpsFixContext,
        motion: ResolvedMotion,
    ): LatLong =
        context.currentDisplayed
            ?.let { currentDisplayed ->
                val correction =
                    CorrectionContext(
                        currentDisplayed = currentDisplayed,
                        correctionDistanceM = distanceMeters(currentDisplayed, context.fix.latLong),
                    )
                acceptCorrection(context, motion, correction)
            }
            ?: acceptInitialFix(context, motion)

    private fun acceptInitialFix(
        context: GpsFixContext,
        motion: ResolvedMotion,
    ): LatLong {
        state.displayedLatLong = context.fix.latLong
        state.correctionBlend = null
        state.clampedCorrectionStreak = 0
        recordFixAccepted(
            context = context,
            motion = motion,
            event =
                FixAcceptedTelemetry(
                    mode = MarkerMotionMode.FIXED,
                    reason = "initial_fix",
                    correctionDistanceM = null,
                    blendDurationMs = null,
                ),
        )
        return context.fix.latLong
    }

    private fun acceptCorrection(
        context: GpsFixContext,
        motion: ResolvedMotion,
        correction: CorrectionContext,
    ): LatLong =
        when {
            shouldFreezeStationaryJitter(correction.correctionDistanceM, context.accuracyM, motion.speedMps) ->
                acceptStationaryJitter(context, motion, correction)
            correction.correctionDistanceM <= correctionDeadbandMeters(context.accuracyM, motion.speedMps) ->
                acceptDeadbandSnap(context, motion, correction)
            else -> startCorrectionBlend(context, motion, correction)
        }

    private fun acceptStationaryJitter(
        context: GpsFixContext,
        motion: ResolvedMotion,
        correction: CorrectionContext,
    ): LatLong {
        state.correctionBlend = null
        state.clampedCorrectionStreak = 0
        recordFixAccepted(
            context = context,
            motion = motion,
            event =
                FixAcceptedTelemetry(
                    mode = MarkerMotionMode.FIXED,
                    reason = "stationary_jitter",
                    correctionDistanceM = correction.correctionDistanceM,
                    blendDurationMs = null,
                ),
        )
        return correction.currentDisplayed
    }

    private fun acceptDeadbandSnap(
        context: GpsFixContext,
        motion: ResolvedMotion,
        correction: CorrectionContext,
    ): LatLong {
        state.displayedLatLong = context.fix.latLong
        state.correctionBlend = null
        state.clampedCorrectionStreak = 0
        recordFixAccepted(
            context = context,
            motion = motion,
            event =
                FixAcceptedTelemetry(
                    mode = MarkerMotionMode.FIXED,
                    reason = "deadband_snap",
                    correctionDistanceM = correction.correctionDistanceM,
                    blendDurationMs = null,
                ),
        )
        return context.fix.latLong
    }

    private fun startCorrectionBlend(
        context: GpsFixContext,
        motion: ResolvedMotion,
        correction: CorrectionContext,
    ): LatLong {
        val sustainedLagCatchUpReason = sustainedLagCatchUpReason(context, motion, correction)
        val correctionTarget =
            resolveCorrectionTarget(
                request =
                    CorrectionTargetRequest(
                        currentDisplayed = correction.currentDisplayed,
                        targetLatLong = context.fix.latLong,
                        correctionDistanceM = correction.correctionDistanceM,
                        accuracyM = context.accuracyM,
                        speedMps = motion.speedMps,
                        allowLargeCorrection =
                            context.fix.allowLargeCorrection ||
                                sustainedLagCatchUpReason != null ||
                                isSourceModeTransition(context),
                    ),
            )
        updateClampTelemetry(context, motion, correction, correctionTarget)
        if (shouldApplyCorrectionImmediately(context, sustainedLagCatchUpReason)) {
            state.displayedLatLong = correctionTarget.targetLatLong
            state.correctionBlend = null
            recordFixAccepted(
                context = context,
                motion = motion,
                event =
                    FixAcceptedTelemetry(
                        mode = MarkerMotionMode.FIXED,
                        reason =
                            correctionReason(
                                context = context,
                                sustainedLagCatchUpReason = sustainedLagCatchUpReason,
                                wasClamped = correctionTarget.wasClamped,
                            ),
                        correctionDistanceM = correctionTarget.visibleCorrectionDistanceM,
                        blendDurationMs = null,
                    ),
            )
            return correctionTarget.targetLatLong
        }
        state.correctionBlend =
            CorrectionBlend(
                from = correction.currentDisplayed,
                to = correctionTarget.targetLatLong,
                startElapsedMs = context.fix.nowElapsedMs,
                durationMs = correctionBlendDurationMs,
            )
        recordFixAccepted(
            context = context,
            motion = motion,
            event =
                FixAcceptedTelemetry(
                    mode = MarkerMotionMode.BLEND,
                    reason =
                        correctionReason(
                            context = context,
                            sustainedLagCatchUpReason = sustainedLagCatchUpReason,
                            wasClamped = correctionTarget.wasClamped,
                        ),
                    correctionDistanceM = correctionTarget.visibleCorrectionDistanceM,
                    blendDurationMs = correctionBlendDurationMs,
                ),
        )
        return correction.currentDisplayed
    }

    private fun updateClampTelemetry(
        context: GpsFixContext,
        motion: ResolvedMotion,
        correction: CorrectionContext,
        correctionTarget: CorrectionTargetDecision,
    ) {
        if (correctionTarget.wasClamped) {
            state.clampedCorrectionStreak += 1
            MarkerMotionTelemetry.recordCorrectionClamped(
                event =
                    CorrectionClampTelemetryEvent(
                        nowElapsedMs = context.fix.nowElapsedMs,
                        actualCorrectionDistanceM = correction.correctionDistanceM,
                        visibleCorrectionDistanceM = correctionTarget.visibleCorrectionDistanceM,
                        accuracyM = context.accuracyM,
                        speedMps = motion.speedMps,
                        bearingDeg = motion.bearingDeg,
                    ),
            )
        } else {
            state.clampedCorrectionStreak = 0
        }
    }

    private fun shouldApplyCorrectionImmediately(
        context: GpsFixContext,
        sustainedLagCatchUpReason: String?,
    ): Boolean =
        context.fix.sourceMode == LocationSourceMode.WATCH_GPS ||
            sustainedLagCatchUpReason != null ||
            isSourceModeTransition(context)

    private fun isSourceModeTransition(context: GpsFixContext): Boolean =
        context.previousFix?.sourceMode != null &&
            context.previousFix.sourceMode != context.fix.sourceMode

    private fun correctionReason(
        context: GpsFixContext,
        sustainedLagCatchUpReason: String?,
        wasClamped: Boolean,
    ): String =
        when {
            sustainedLagCatchUpReason != null -> sustainedLagCatchUpReason
            isSourceModeTransition(context) -> "source_switch"
            wasClamped -> "correction_clamped"
            else -> "gps_correction"
        }

    private fun recordFixAccepted(
        context: GpsFixContext,
        motion: ResolvedMotion,
        event: FixAcceptedTelemetry,
    ) {
        MarkerMotionTelemetry.recordFixAccepted(
            mode = event.mode,
            reason = event.reason,
            nowElapsedMs = context.fix.nowElapsedMs,
            fixAgeMs = context.timing.fixAgeMs,
            accuracyM = context.accuracyM,
            speedMps = motion.speedMps,
            bearingDeg = motion.bearingDeg,
            correctionDistanceM = event.correctionDistanceM,
            blendDurationMs = event.blendDurationMs,
        )
    }

    private fun MotionFix.detectOutlier(
        candidate: LatLong,
        candidateAccuracyM: Float,
        candidateFixElapsedMs: Long,
    ): OutlierDecision? {
        val dtMs = (candidateFixElapsedMs - fixElapsedMs).coerceAtLeast(0L)
        if (dtMs < OUTLIER_MIN_WINDOW_MS) return null
        val dtSec = dtMs / 1000f

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

    private fun sustainedLagCatchUpReason(
        context: GpsFixContext,
        motion: ResolvedMotion,
        correction: CorrectionContext,
    ): String? {
        if (state.clampedCorrectionStreak < SUSTAINED_LAG_CATCH_UP_CLAMP_STREAK) {
            return null
        }
        return when (context.fix.sourceMode) {
            LocationSourceMode.WATCH_GPS ->
                "watch_gps_catch_up".takeIf {
                    context.accuracyM <= WATCH_GPS_CATCH_UP_MAX_ACCURACY_M &&
                        motion.speedMps >= WATCH_GPS_CATCH_UP_MIN_SPEED_MPS &&
                        correction.correctionDistanceM >= WATCH_GPS_CATCH_UP_MIN_LAG_M
                }
            LocationSourceMode.AUTO_FUSED ->
                "auto_fused_catch_up".takeIf {
                    context.accuracyM <= AUTO_FUSED_CATCH_UP_MAX_ACCURACY_M &&
                        motion.speedMps >= AUTO_FUSED_CATCH_UP_MIN_SPEED_MPS &&
                        correction.correctionDistanceM >= AUTO_FUSED_CATCH_UP_MIN_LAG_M
                }
        }
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
        val trustedWalkingDerivedSpeed =
            trustedDerivedSpeed
                ?.takeIf { accuracyM <= DERIVED_WALKING_SPEED_MAX_ACCURACY_M }
                ?.takeIf { it in DERIVED_WALKING_SPEED_MIN_MPS..DERIVED_WALKING_SPEED_MAX_MPS }
                ?.coerceAtMost(DERIVED_WALKING_SPEED_CAP_MPS)

        return when {
            shouldPreferWalkingDerivedSpeed(trustedRawSpeed, trustedWalkingDerivedSpeed) ->
                trustedWalkingDerivedSpeed ?: 0f
            trustedRawSpeed != null -> trustedRawSpeed
            trustedWalkingDerivedSpeed != null -> trustedWalkingDerivedSpeed
            else -> 0f
        }
    }

    private fun shouldPreferWalkingDerivedSpeed(
        rawSpeedMps: Float?,
        walkingDerivedSpeedMps: Float?,
    ): Boolean {
        if (rawSpeedMps == null || walkingDerivedSpeedMps == null) return false
        val rawLooksLow = rawSpeedMps <= LOW_RAW_SPEED_OVERRIDE_MAX_MPS
        val derivedPullsAhead = walkingDerivedSpeedMps >= rawSpeedMps + LOW_RAW_SPEED_OVERRIDE_GAIN_MPS
        return rawLooksLow && derivedPullsAhead
    }

    private fun resolveMotionBearingDeg(
        rawBearingDeg: Float?,
        rawSpeedMps: Float,
        derivedMotion: DerivedMotion?,
        fallbackBearingDeg: Float?,
        resolvedSpeedMps: Float,
    ): Float? {
        val rawBearingIsUsable =
            rawBearingDeg != null &&
                rawBearingDeg.isFinite() &&
                max(sanitizeSpeed(rawSpeedMps), resolvedSpeedMps) >= GPS_BEARING_MIN_SPEED_MPS
        val derivedBearingIsUsable =
            derivedMotion?.bearingDeg != null &&
                max(derivedMotion.speedMps, resolvedSpeedMps) >= minPredictionSpeedMps
        return when {
            rawBearingIsUsable -> normalize360(rawBearingDeg)
            derivedBearingIsUsable -> derivedMotion.bearingDeg
            else -> fallbackBearingDeg?.let(::normalize360)
        }
    }
}

private class MarkerMotionState {
    var lastAcceptedFix: MotionFix? = null
    var displayedLatLong: LatLong? = null
    var correctionBlend: CorrectionBlend? = null
    var smoothedSpeedMps: Float = 0f
    var predictionRequiresFreshFix: Boolean = true
    var clampedCorrectionStreak: Int = 0
}

private data class MotionFix(
    val latLong: LatLong,
    val fixElapsedMs: Long,
    val accuracyM: Float,
    val speedMps: Float,
    val bearingDeg: Float?,
    val sourceMode: LocationSourceMode,
)

private data class GpsFixTiming(
    val reliableFixElapsedMs: Long,
    val fixAgeMs: Long,
)

private data class GpsFixContext(
    val fix: MarkerMotionGpsFix,
    val timing: GpsFixTiming,
    val accuracyM: Float,
    val currentDisplayed: LatLong?,
    val previousFix: MotionFix?,
)

private data class ResolvedMotion(
    val speedMps: Float,
    val bearingDeg: Float?,
)

private data class CorrectionContext(
    val currentDisplayed: LatLong,
    val correctionDistanceM: Float,
)

private data class FixAcceptedTelemetry(
    val mode: MarkerMotionMode,
    val reason: String,
    val correctionDistanceM: Float?,
    val blendDurationMs: Long?,
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

private fun effectiveMotionAccuracy(
    accuracyM: Float,
    sourceMode: LocationSourceMode,
): Float {
    val sanitizedAccuracy = sanitizeAccuracy(accuracyM)
    if (
        sourceMode == LocationSourceMode.WATCH_GPS &&
        isKnownWatchGpsAccuracyFloor(sanitizedAccuracy)
    ) {
        return WATCH_GPS_FLOOR_MOTION_ACCURACY_M
    }
    return sanitizedAccuracy
}

private fun isKnownWatchGpsAccuracyFloor(
    accuracyM: Float,
): Boolean {
    if (!accuracyM.isFinite()) return false
    return abs(accuracyM - WATCH_GPS_ACCURACY_FLOOR_M) <= WATCH_GPS_ACCURACY_FLOOR_TOLERANCE_M
}

private fun isDuplicateMotionFix(
    previousFix: MotionFix,
    candidate: LatLong,
    candidateFixElapsedMs: Long,
    candidateAccuracyM: Float,
): Boolean {
    val fixTimeDeltaMs = candidateFixElapsedMs - previousFix.fixElapsedMs
    val isSameTime = fixTimeDeltaMs <= DUPLICATE_FIX_TIME_EPSILON_MS
    val isSameAccuracy = abs(previousFix.accuracyM - candidateAccuracyM) <= DUPLICATE_FIX_ACCURACY_EPSILON_M
    val isSamePosition = distanceMeters(previousFix.latLong, candidate) <= DUPLICATE_FIX_DISTANCE_EPSILON_M
    return isSameTime && isSameAccuracy && isSamePosition
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
private const val DEFAULT_MAX_PREDICTION_ACCURACY_M = 45f
private const val WATCH_GPS_FLOOR_MOTION_ACCURACY_M = 18f
private const val DEFAULT_MIN_PREDICTION_SPEED_MPS = 0.35f
private const val DEFAULT_CORRECTION_BLEND_DURATION_MS = 350L
private const val DEFAULT_PREDICTION_TICK_MS = 250L
private const val PREDICTION_START_DELAY_MS = 150L
private const val PREDICTION_SPEED_SCALE = 0.9f
private const val MIN_PREDICTION_DISTANCE_M = 0.35f
private const val PREDICTION_RENDER_EPSILON_M = 0.25f
private const val DUPLICATE_FIX_TIME_EPSILON_MS = 250L
private const val DUPLICATE_FIX_DISTANCE_EPSILON_M = 0.25f
private const val DUPLICATE_FIX_ACCURACY_EPSILON_M = 0.1f
private const val SUSTAINED_LAG_CATCH_UP_CLAMP_STREAK = 2
private const val WATCH_GPS_CATCH_UP_MIN_LAG_M = 60f
private const val WATCH_GPS_CATCH_UP_MIN_SPEED_MPS = 2.0f
private const val WATCH_GPS_CATCH_UP_MAX_ACCURACY_M = 25f
private const val AUTO_FUSED_CATCH_UP_MIN_LAG_M = 35f
private const val AUTO_FUSED_CATCH_UP_MIN_SPEED_MPS = 0.8f
private const val AUTO_FUSED_CATCH_UP_MAX_ACCURACY_M = 12f
private const val SPEED_SMOOTHING_ALPHA = 0.35f
private const val GPS_BEARING_MIN_SPEED_MPS = 0.45f
private const val DERIVED_MOTION_MIN_WINDOW_MS = 900L
private const val DERIVED_WALKING_SPEED_MAX_ACCURACY_M = 45f
private const val DERIVED_WALKING_SPEED_MIN_MPS = 0.25f
private const val DERIVED_WALKING_SPEED_MAX_MPS = 2.4f
private const val DERIVED_WALKING_SPEED_CAP_MPS = 1.8f
private const val LOW_RAW_SPEED_OVERRIDE_MAX_MPS = 0.75f
private const val LOW_RAW_SPEED_OVERRIDE_GAIN_MPS = 0.2f
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
