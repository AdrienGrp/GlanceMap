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
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal data class MarkerMotionReading(
    val fixElapsedMs: Long,
    val accuracyM: Float,
    val speedMps: Float?,
    val bearingDeg: Float?,
    val speedAccuracyMps: Float? = null,
    val bearingAccuracyDeg: Float? = null,
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

internal data class MarkerMotionUpdate(
    val displayedLatLong: LatLong,
    val fixAccepted: Boolean,
)

internal class MarkerMotionController(
    predictionFreshnessMaxAgeMs: Long,
    maxAcceptedFixAgeMs: Long,
    private val maxPredictionAccuracyM: Float = DEFAULT_MAX_PREDICTION_ACCURACY_M,
    private val minPredictionSpeedMps: Float = DEFAULT_MIN_PREDICTION_SPEED_MPS,
    private val correctionBlendDurationMs: Long = DEFAULT_CORRECTION_BLEND_DURATION_MS,
    private val predictionTickMs: Long = DEFAULT_PREDICTION_TICK_MS,
) {
    private var predictionFreshnessMaxAgeMs = predictionFreshnessMaxAgeMs
    private val state = MarkerMotionState()
    private val fixProcessor =
        MarkerMotionGpsFixProcessor(
            state = state,
            maxAcceptedFixAgeMs = maxAcceptedFixAgeMs,
            maxVisualCorrectionAccuracyM = maxPredictionAccuracyM,
            minPredictionSpeedMps = minPredictionSpeedMps,
            correctionBlendDurationMs = correctionBlendDurationMs,
            isBikeActivityProfile = false,
        )

    fun updateTiming(
        predictionFreshnessMaxAgeMs: Long,
        maxAcceptedFixAgeMs: Long,
    ) {
        this.predictionFreshnessMaxAgeMs = predictionFreshnessMaxAgeMs
        fixProcessor.updateMaxAcceptedFixAgeMs(maxAcceptedFixAgeMs)
    }

    fun updateActivityProfile(isBikeActivityProfile: Boolean) {
        fixProcessor.updateActivityProfile(isBikeActivityProfile)
    }

    fun reset(reason: String = "reset") {
        state.lastAcceptedFix = null
        state.visualAnchorFix = null
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
        val motionFix =
            MotionFix(
                latLong = seed.latLong,
                fixElapsedMs = fixElapsedMs,
                accuracyM = motionAccuracyM,
                speedMps = sanitizedSpeed,
                bearingDeg = seed.reading.bearingDeg?.let(::normalize360),
                sourceMode = seed.sourceMode,
            )
        state.lastAcceptedFix = motionFix
        state.visualAnchorFix = motionFix
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

    /**
     * Keeps the marker smooth only while a fresh fix can actually drive a visible prediction.
     * When the marker is stationary, stale, or otherwise blocked, checking four times per second
     * cannot change the map, so use a quieter cadence until the next accepted GPS fix.
     */
    fun suggestedPredictionTickMs(
        nowElapsedMs: Long,
        serviceFreshnessMaxAgeMs: Long,
        watchGpsDegraded: Boolean,
    ): Long {
        if (hasActiveCorrectionBlend(nowElapsedMs)) return DEFAULT_CORRECTION_BLEND_TICK_MS
        return if (
            hasActiveVisualMotion(
                nowElapsedMs = nowElapsedMs,
                serviceFreshnessMaxAgeMs = serviceFreshnessMaxAgeMs,
                watchGpsDegraded = watchGpsDegraded,
            )
        ) {
            predictionTickMs
        } else {
            IDLE_PREDICTION_TICK_MS
        }
    }

    private fun hasActiveCorrectionBlend(nowElapsedMs: Long): Boolean =
        state.correctionBlend?.let { blend ->
            val blendAgeMs = (nowElapsedMs - blend.startElapsedMs).coerceAtLeast(0L)
            blendAgeMs < blend.durationMs
        } ?: false

    private fun hasActiveVisualMotion(
        nowElapsedMs: Long,
        serviceFreshnessMaxAgeMs: Long,
        watchGpsDegraded: Boolean,
    ): Boolean {
        val predictionActive =
            state.visualAnchorFix?.let { fix ->
                val freshnessMaxAgeMs =
                    minOf(
                        predictionFreshnessMaxAgeMs,
                        serviceFreshnessMaxAgeMs.takeIf { it > 0L } ?: Long.MAX_VALUE,
                    )
                val fixAgeMs = (nowElapsedMs - fix.fixElapsedMs).coerceAtLeast(0L)
                !watchGpsDegraded &&
                    !state.predictionRequiresFreshFix &&
                    fixAgeMs <= freshnessMaxAgeMs &&
                    fix.accuracyM <= maxPredictionAccuracyM &&
                    fix.bearingDeg != null &&
                    fix.speedMps >= minPredictionSpeedMps
            } ?: false
        return predictionActive
    }

    fun onGpsFix(fix: MarkerMotionGpsFix): MarkerMotionUpdate {
        val previousAcceptedFix = state.lastAcceptedFix
        val displayedLatLong = fixProcessor.onGpsFix(fix)
        return MarkerMotionUpdate(
            displayedLatLong = displayedLatLong,
            fixAccepted = state.lastAcceptedFix !== previousAcceptedFix,
        )
    }

    fun predict(
        nowElapsedMs: Long,
        serviceFreshnessMaxAgeMs: Long,
        watchGpsDegraded: Boolean,
    ): LatLong? {
        var currentDisplayed = state.displayedLatLong ?: state.visualAnchorFix?.latLong ?: return null

        state.correctionBlend?.let { blend ->
            val elapsedMs = (nowElapsedMs - blend.startElapsedMs).coerceAtLeast(0L)
            val linearFraction = (elapsedMs.toFloat() / blend.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
            val fraction = linearFraction * linearFraction * (3f - 2f * linearFraction)
            val movingTarget = movingCorrectionTarget(blend, nowElapsedMs)
            val blended = lerpLatLong(blend.from, movingTarget, fraction)
            state.displayedLatLong = blended
            state.visualAnchorFix?.let { fix ->
                MarkerMotionTelemetry.recordBlendState(
                    nowElapsedMs = nowElapsedMs,
                    fixAgeMs = (nowElapsedMs - fix.fixElapsedMs).coerceAtLeast(0L),
                    accuracyM = fix.accuracyM,
                    speedMps = fix.speedMps,
                    bearingDeg = fix.bearingDeg,
                    correctionDistanceM = distanceMeters(blended, movingTarget),
                )
            }
            if (fraction < 1f) {
                return blended
            }
            state.correctionBlend = null
            currentDisplayed = blended
        }

        if (watchGpsDegraded || state.predictionRequiresFreshFix) {
            val fix = state.visualAnchorFix
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

        val fix = state.visualAnchorFix ?: return currentDisplayed
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
        if (fix.speedMps < minPredictionSpeedMps) {
            MarkerMotionTelemetry.recordPredictionBlocked(
                reason = "slow",
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

@Suppress("LargeClass", "TooManyFunctions")
private class MarkerMotionGpsFixProcessor(
    private val state: MarkerMotionState,
    private var maxAcceptedFixAgeMs: Long,
    private val maxVisualCorrectionAccuracyM: Float,
    private val minPredictionSpeedMps: Float,
    private val correctionBlendDurationMs: Long,
    private var isBikeActivityProfile: Boolean,
) {
    fun updateMaxAcceptedFixAgeMs(maxAcceptedFixAgeMs: Long) {
        this.maxAcceptedFixAgeMs = maxAcceptedFixAgeMs
    }

    fun updateActivityProfile(isBikeActivityProfile: Boolean) {
        this.isBikeActivityProfile = isBikeActivityProfile
    }

    fun onGpsFix(fix: MarkerMotionGpsFix): LatLong {
        val candidateContext = buildGpsFixContext(fix)
        rejectGpsFix(candidateContext)?.let { return it }

        advanceActiveCorrectionBlend(fix.nowElapsedMs)
        val context = buildGpsFixContext(fix)
        return acceptGpsFix(context)
    }

    private fun advanceActiveCorrectionBlend(nowElapsedMs: Long) {
        val blend = state.correctionBlend ?: return
        val elapsedMs = (nowElapsedMs - blend.startElapsedMs).coerceAtLeast(0L)
        val linearFraction = (elapsedMs.toFloat() / blend.durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
        val fraction = linearFraction * linearFraction * (3f - 2f * linearFraction)
        val movingTarget = movingCorrectionTarget(blend, nowElapsedMs)
        val blended = lerpLatLong(blend.from, movingTarget, fraction)
        state.displayedLatLong = blended
        state.visualAnchorFix?.let { fix ->
            MarkerMotionTelemetry.recordBlendState(
                nowElapsedMs = nowElapsedMs,
                fixAgeMs = (nowElapsedMs - fix.fixElapsedMs).coerceAtLeast(0L),
                accuracyM = fix.accuracyM,
                speedMps = fix.speedMps,
                bearingDeg = fix.bearingDeg,
                correctionDistanceM = distanceMeters(blended, movingTarget),
            )
        }
        if (fraction >= 1f) {
            state.correctionBlend = null
        }
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
            else ->
                rejectNonForwardGpsFix(context)
                    ?: rejectDuplicateGpsFix(context)
                    ?: rejectOutlierGpsFix(context)
        }

    private fun rejectNonForwardGpsFix(context: GpsFixContext): LatLong? {
        val previousFix = context.previousFix ?: return null
        val sameSource = previousFix.sourceMode == context.fix.sourceMode
        val timestampDidNotAdvance = context.timing.reliableFixElapsedMs <= previousFix.fixElapsedMs
        return if (sameSource && timestampDidNotAdvance) {
            rejectBlockedGpsFix(
                context = context,
                reason = "non_forward_fix",
                displayLatLong = context.currentDisplayed ?: previousFix.latLong,
            )
        } else {
            null
        }
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
                    ?.takeIf { it.isFinite() },
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
                speedAccuracyMps = context.fix.reading.speedAccuracyMps,
            )
        val confirmedStop =
            isConfirmedStop(
                rawSpeedMps = context.fix.reading.speedMps,
                speedAccuracyMps = context.fix.reading.speedAccuracyMps,
                derivedSpeedMps = derivedMotion?.speedMps,
                accuracyM = context.accuracyM,
            )
        state.smoothedSpeedMps =
            smoothMotionSpeed(
                resolvedSpeedMps = resolvedSpeedMps,
                fixIntervalMs =
                    context.previousFix
                        ?.let { (context.timing.reliableFixElapsedMs - it.fixElapsedMs).coerceAtLeast(0L) }
                        ?: 0L,
                confirmedStop = confirmedStop,
            )
        return ResolvedMotion(
            speedMps = state.smoothedSpeedMps,
            bearingDeg =
                resolveMotionBearingDeg(
                    input =
                        MotionBearingInput(
                            rawBearingDeg = context.fix.reading.bearingDeg,
                            rawSpeedMps = context.fix.reading.speedMps,
                            bearingAccuracyDeg = context.fix.reading.bearingAccuracyDeg,
                            derivedMotion = derivedMotion,
                            fallbackBearingDeg = context.previousFix?.bearingDeg,
                            resolvedSpeedMps = state.smoothedSpeedMps,
                            confirmedStop = confirmedStop,
                        ),
                ),
        )
    }

    private fun smoothMotionSpeed(
        resolvedSpeedMps: Float,
        fixIntervalMs: Long,
        confirmedStop: Boolean,
    ): Float =
        when {
            confirmedStop -> 0f
            state.smoothedSpeedMps <= 0f || fixIntervalMs <= 0L -> resolvedSpeedMps
            else -> {
                val timeConstantMs =
                    if (isBikeActivityProfile) {
                        BIKE_SPEED_SMOOTHING_TIME_CONSTANT_MS
                    } else {
                        WALK_SPEED_SMOOTHING_TIME_CONSTANT_MS
                    }
                val alpha =
                    (1.0 - exp(-fixIntervalMs.toDouble() / timeConstantMs.toDouble()))
                        .toFloat()
                        .coerceIn(0f, 1f)
                alpha * resolvedSpeedMps + (1f - alpha) * state.smoothedSpeedMps
            }
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
        updateVisualMotionAnchor(context.fix.latLong)
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
            shouldHoldWeakAutoFusedCorrection(context) ->
                acceptWeakAutoFusedCorrection(context, motion, correction)
            shouldFreezeStationaryJitter(correction.correctionDistanceM, context.accuracyM, motion.speedMps) ->
                acceptStationaryJitter(context, motion, correction)
            correction.correctionDistanceM <= correctionDeadbandMeters(context.accuracyM, motion.speedMps) ->
                acceptDeadbandHold(context, motion, correction)
            else -> startCorrectionBlend(context, motion, correction)
        }

    private fun shouldHoldWeakAutoFusedCorrection(context: GpsFixContext): Boolean =
        context.fix.sourceMode == LocationSourceMode.AUTO_FUSED &&
            context.accuracyM > maxVisualCorrectionAccuracyM

    private fun acceptWeakAutoFusedCorrection(
        context: GpsFixContext,
        motion: ResolvedMotion,
        correction: CorrectionContext,
    ): LatLong {
        state.displayedLatLong = correction.currentDisplayed
        state.correctionBlend = null
        state.clampedCorrectionStreak = 0
        updateVisualMotionAnchor(correction.currentDisplayed)
        recordFixAccepted(
            context = context,
            motion = motion,
            event =
                FixAcceptedTelemetry(
                    mode = MarkerMotionMode.FIXED,
                    reason = "weak_accuracy_hold",
                    correctionDistanceM = correction.correctionDistanceM,
                    blendDurationMs = null,
                ),
        )
        return correction.currentDisplayed
    }

    private fun acceptStationaryJitter(
        context: GpsFixContext,
        motion: ResolvedMotion,
        correction: CorrectionContext,
    ): LatLong {
        state.correctionBlend = null
        state.clampedCorrectionStreak = 0
        updateVisualMotionAnchor(correction.currentDisplayed)
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

    private fun acceptDeadbandHold(
        context: GpsFixContext,
        motion: ResolvedMotion,
        correction: CorrectionContext,
    ): LatLong {
        state.displayedLatLong = correction.currentDisplayed
        state.correctionBlend = null
        state.clampedCorrectionStreak = 0
        updateVisualMotionAnchor(correction.currentDisplayed)
        recordFixAccepted(
            context = context,
            motion = motion,
            event =
                FixAcceptedTelemetry(
                    mode = MarkerMotionMode.FIXED,
                    reason = "accuracy_deadband_hold",
                    correctionDistanceM = correction.correctionDistanceM,
                    blendDurationMs = null,
                ),
        )
        return correction.currentDisplayed
    }

    private fun startCorrectionBlend(
        context: GpsFixContext,
        motion: ResolvedMotion,
        correction: CorrectionContext,
    ): LatLong {
        val sustainedLagCatchUpReason = sustainedLagCatchUpReason(context, motion, correction)
        val correctionTarget =
            resolveCorrectionTarget(
                request = correctionTargetRequest(context, motion, correction, sustainedLagCatchUpReason),
            )
        updateClampTelemetry(context, motion, correction, correctionTarget)
        return if (shouldApplyCorrectionImmediately(context, sustainedLagCatchUpReason)) {
            applyImmediateCorrection(context, motion, correctionTarget, sustainedLagCatchUpReason)
        } else {
            beginCorrectionBlend(context, motion, correction, correctionTarget, sustainedLagCatchUpReason)
        }
    }

    private fun correctionTargetRequest(
        context: GpsFixContext,
        motion: ResolvedMotion,
        correction: CorrectionContext,
        sustainedLagCatchUpReason: String?,
    ): CorrectionTargetRequest =
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
        )

    private fun applyImmediateCorrection(
        context: GpsFixContext,
        motion: ResolvedMotion,
        correctionTarget: CorrectionTargetDecision,
        sustainedLagCatchUpReason: String?,
    ): LatLong {
        state.displayedLatLong = correctionTarget.targetLatLong
        state.correctionBlend = null
        updateVisualMotionAnchor(correctionTarget.targetLatLong)
        recordFixAccepted(
            context = context,
            motion = motion,
            event =
                FixAcceptedTelemetry(
                    mode = MarkerMotionMode.FIXED,
                    reason = correctionReason(context, sustainedLagCatchUpReason, correctionTarget),
                    correctionDistanceM = correctionTarget.visibleCorrectionDistanceM,
                    blendDurationMs = null,
                ),
        )
        return correctionTarget.targetLatLong
    }

    private fun beginCorrectionBlend(
        context: GpsFixContext,
        motion: ResolvedMotion,
        correction: CorrectionContext,
        correctionTarget: CorrectionTargetDecision,
        sustainedLagCatchUpReason: String?,
    ): LatLong {
        updateVisualMotionAnchor(correctionTarget.targetLatLong)
        state.correctionBlend =
            CorrectionBlend(
                from = correction.currentDisplayed,
                to = correctionTarget.targetLatLong,
                startElapsedMs = context.fix.nowElapsedMs,
                durationMs = correctionBlendDurationMs,
                anchorFixElapsedMs = context.timing.reliableFixElapsedMs,
                speedMps = motion.speedMps,
                bearingDeg = motion.bearingDeg,
            )
        recordFixAccepted(
            context = context,
            motion = motion,
            event =
                FixAcceptedTelemetry(
                    mode = MarkerMotionMode.BLEND,
                    reason = correctionReason(context, sustainedLagCatchUpReason, correctionTarget),
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
        correctionTarget: CorrectionTargetDecision,
    ): String =
        when {
            sustainedLagCatchUpReason != null -> sustainedLagCatchUpReason
            isSourceModeTransition(context) -> "source_switch"
            correctionTarget.wasClamped -> "correction_clamped"
            correctionTarget.wasQualityWeighted -> "quality_weighted_correction"
            else -> "gps_correction"
        }

    private fun updateVisualMotionAnchor(latLong: LatLong) {
        state.visualAnchorFix = state.lastAcceptedFix?.copy(latLong = latLong)
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
            innovationDistanceM =
                context.currentDisplayed?.let { displayed ->
                    distanceMeters(displayed, context.fix.latLong)
                },
            fixGapMs =
                context.previousFix?.let { previous ->
                    (context.timing.reliableFixElapsedMs - previous.fixElapsedMs).coerceAtLeast(0L)
                },
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
            else ->
                (accuracyM * MOVING_CORRECTION_ACCURACY_DEADBAND_SCALE)
                    .coerceIn(MOVING_CORRECTION_MIN_DEADBAND_M, MOVING_CORRECTION_MAX_DEADBAND_M)
        }

    private fun resolveCorrectionTarget(request: CorrectionTargetRequest): CorrectionTargetDecision {
        val qualityWeight = resolveCorrectionQualityWeight(request)
        val deadbandM = correctionDeadbandMeters(request.accuracyM, request.speedMps)
        val qualityWeightedCorrectionM =
            if (qualityWeight >= 1f || request.correctionDistanceM <= deadbandM) {
                request.correctionDistanceM
            } else {
                deadbandM + (request.correctionDistanceM - deadbandM) * qualityWeight
            }
        val canClamp =
            !request.allowLargeCorrection &&
                request.correctionDistanceM >= LARGE_CORRECTION_MIN_DISTANCE_M &&
                (
                    request.accuracyM >= LARGE_CORRECTION_MIN_ACCURACY_M ||
                        request.correctionDistanceM >= LARGE_CORRECTION_FORCE_CLAMP_DISTANCE_M
                )
        val maxVisibleCorrectionM =
            if (canClamp) {
                (
                    LARGE_CORRECTION_BASE_VISIBLE_M +
                        request.accuracyM * LARGE_CORRECTION_ACCURACY_SCALE +
                        request.speedMps * LARGE_CORRECTION_SPEED_SCALE
                ).coerceAtLeast(LARGE_CORRECTION_BASE_VISIBLE_M)
            } else {
                Float.POSITIVE_INFINITY
            }
        val visibleCorrectionDistanceM = minOf(qualityWeightedCorrectionM, maxVisibleCorrectionM)
        val wasClamped = canClamp && maxVisibleCorrectionM < qualityWeightedCorrectionM
        val wasQualityWeighted =
            qualityWeightedCorrectionM < request.correctionDistanceM - QUALITY_WEIGHT_DISTANCE_EPSILON_M
        val targetLatLong =
            if (visibleCorrectionDistanceM < request.correctionDistanceM - QUALITY_WEIGHT_DISTANCE_EPSILON_M) {
                moveLatLong(
                    start = request.currentDisplayed,
                    bearing = bearingBetweenDegrees(request.currentDisplayed, request.targetLatLong),
                    distanceMeters = visibleCorrectionDistanceM,
                )
            } else {
                request.targetLatLong
            }
        return CorrectionTargetDecision(
            targetLatLong = targetLatLong,
            visibleCorrectionDistanceM = visibleCorrectionDistanceM,
            wasClamped = wasClamped,
            wasQualityWeighted = wasQualityWeighted,
        )
    }

    private fun resolveCorrectionQualityWeight(request: CorrectionTargetRequest): Float {
        if (request.allowLargeCorrection || request.speedMps < STATIONARY_JITTER_MAX_SPEED_MPS) return 1f
        val normalizedUncertainty =
            (
                (request.accuracyM - FULL_WEIGHT_CORRECTION_ACCURACY_M) /
                    (maxVisualCorrectionAccuracyM - FULL_WEIGHT_CORRECTION_ACCURACY_M)
            ).coerceIn(0f, 1f)
        return 1f - normalizedUncertainty * MAX_CORRECTION_QUALITY_REDUCTION
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
            LocationSourceMode.AUTO_FUSED,
            LocationSourceMode.PASSIVE_EXTERNAL,
            ->
                "auto_fused_catch_up".takeIf {
                    context.accuracyM <= AUTO_FUSED_CATCH_UP_MAX_ACCURACY_M &&
                        motion.speedMps >= AUTO_FUSED_CATCH_UP_MIN_SPEED_MPS &&
                        correction.correctionDistanceM >= AUTO_FUSED_CATCH_UP_MIN_LAG_M
                }
        }
    }

    private fun resolveMotionSpeedMps(
        rawSpeedMps: Float?,
        derivedSpeedMps: Float?,
        accuracyM: Float,
        speedAccuracyMps: Float?,
    ): Float {
        val trustedRawSpeed =
            rawSpeedMps
                ?.takeIf { it.isFinite() }
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
        val trustedBikeDerivedSpeed =
            trustedDerivedSpeed
                ?.takeIf { isBikeActivityProfile }
                ?.takeIf { accuracyM <= DERIVED_BIKE_SPEED_MAX_ACCURACY_M }
                ?.takeIf { it in DERIVED_BIKE_SPEED_MIN_MPS..DERIVED_BIKE_SPEED_MAX_MPS }
                ?.coerceAtMost(DERIVED_BIKE_SPEED_CAP_MPS)
        val profileDerivedSpeed = trustedBikeDerivedSpeed ?: trustedWalkingDerivedSpeed
        val rawSpeedAccuracyIsPoor =
            speedAccuracyMps
                ?.takeIf { it.isFinite() }
                ?.let { accuracy ->
                    accuracy > maxOf(MAX_TRUSTED_SPEED_ACCURACY_MPS, (trustedRawSpeed ?: 0f) * MAX_SPEED_ACCURACY_RATIO)
                } ?: false
        val usableRawSpeed = trustedRawSpeed.takeUnless { rawSpeedAccuracyIsPoor }

        return when {
            shouldPreferDerivedSpeed(usableRawSpeed, profileDerivedSpeed) -> profileDerivedSpeed ?: 0f
            usableRawSpeed != null -> usableRawSpeed
            profileDerivedSpeed != null -> profileDerivedSpeed
            else -> 0f
        }
    }

    private fun shouldPreferDerivedSpeed(
        rawSpeedMps: Float?,
        derivedSpeedMps: Float?,
    ): Boolean {
        if (rawSpeedMps == null || derivedSpeedMps == null) return rawSpeedMps == null && derivedSpeedMps != null
        val rawLooksLow = rawSpeedMps <= LOW_RAW_SPEED_OVERRIDE_MAX_MPS
        val derivedPullsAhead = derivedSpeedMps >= rawSpeedMps + LOW_RAW_SPEED_OVERRIDE_GAIN_MPS
        return rawLooksLow && derivedPullsAhead
    }

    private fun isConfirmedStop(
        rawSpeedMps: Float?,
        speedAccuracyMps: Float?,
        derivedSpeedMps: Float?,
        accuracyM: Float,
    ): Boolean {
        val rawSpeed = rawSpeedMps?.takeIf { it.isFinite() }?.coerceAtLeast(0f)
        val reliableSpeedReading =
            speedAccuracyMps
                ?.takeIf { it.isFinite() }
                ?.let { it <= CONFIRMED_STOP_MAX_SPEED_ACCURACY_MPS }
                ?: false
        val positionAlsoStopped =
            accuracyM <= CONFIRMED_STOP_MAX_POSITION_ACCURACY_M &&
                derivedSpeedMps?.takeIf { it.isFinite() }?.let { it <= CONFIRMED_STOP_MAX_DERIVED_SPEED_MPS } == true
        return rawSpeed != null &&
            rawSpeed <= CONFIRMED_STOP_MAX_SPEED_MPS &&
            (reliableSpeedReading || positionAlsoStopped)
    }

    private fun resolveMotionBearingDeg(input: MotionBearingInput): Float? {
        if (input.confirmedStop) return null
        val rawBearingAccuracyIsUsable =
            input.bearingAccuracyDeg
                ?.takeIf { it.isFinite() }
                ?.let { it <= MAX_TRUSTED_BEARING_ACCURACY_DEG }
                ?: true
        val rawBearingIsUsable =
            input.rawBearingDeg != null &&
                input.rawBearingDeg.isFinite() &&
                rawBearingAccuracyIsUsable &&
                max(sanitizeSpeed(input.rawSpeedMps), input.resolvedSpeedMps) >= GPS_BEARING_MIN_SPEED_MPS
        val derivedBearingIsUsable =
            input.derivedMotion?.bearingDeg != null &&
                max(input.derivedMotion.speedMps, input.resolvedSpeedMps) >= minPredictionSpeedMps
        return when {
            rawBearingIsUsable -> normalize360(input.rawBearingDeg)
            derivedBearingIsUsable -> input.derivedMotion.bearingDeg
            else -> input.fallbackBearingDeg?.let(::normalize360)
        }
    }
}

private class MarkerMotionState {
    var lastAcceptedFix: MotionFix? = null
    var visualAnchorFix: MotionFix? = null
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
    val anchorFixElapsedMs: Long,
    val speedMps: Float,
    val bearingDeg: Float?,
)

private data class DerivedMotion(
    val speedMps: Float,
    val bearingDeg: Float?,
)

private data class MotionBearingInput(
    val rawBearingDeg: Float?,
    val rawSpeedMps: Float?,
    val bearingAccuracyDeg: Float?,
    val derivedMotion: DerivedMotion?,
    val fallbackBearingDeg: Float?,
    val resolvedSpeedMps: Float,
    val confirmedStop: Boolean,
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
    val wasQualityWeighted: Boolean,
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

private val movingCorrectionTarget: (CorrectionBlend, Long) -> LatLong = { blend, nowElapsedMs ->
    val bearingDeg = blend.bearingDeg
    val effectiveNowElapsedMs = minOf(nowElapsedMs, blend.startElapsedMs + blend.durationMs)
    val fixAgeMs = (effectiveNowElapsedMs - blend.anchorFixElapsedMs).coerceAtLeast(0L)
    val predictionAgeMs = (fixAgeMs - PREDICTION_START_DELAY_MS).coerceAtLeast(0L)
    val predictedDistanceM = blend.speedMps * PREDICTION_SPEED_SCALE * (predictionAgeMs / 1000f)
    if (
        bearingDeg != null &&
        blend.speedMps >= DEFAULT_MIN_PREDICTION_SPEED_MPS &&
        predictedDistanceM >= MIN_PREDICTION_DISTANCE_M
    ) {
        moveLatLong(
            start = blend.to,
            bearing = bearingDeg,
            distanceMeters = predictedDistanceM,
        )
    } else {
        blend.to
    }
}

private fun sanitizeSpeed(speedMps: Float?): Float {
    if (speedMps == null || !speedMps.isFinite()) return 0f
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
private const val DEFAULT_MAX_PREDICTION_ACCURACY_M = 35f
private const val WATCH_GPS_FLOOR_MOTION_ACCURACY_M = 18f
private const val DEFAULT_MIN_PREDICTION_SPEED_MPS = 0.35f
private const val DEFAULT_CORRECTION_BLEND_DURATION_MS = 350L
private const val DEFAULT_PREDICTION_TICK_MS = 250L
private const val DEFAULT_CORRECTION_BLEND_TICK_MS = 100L
private const val IDLE_PREDICTION_TICK_MS = 30_000L
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
private const val WALK_SPEED_SMOOTHING_TIME_CONSTANT_MS = 7_000L
private const val BIKE_SPEED_SMOOTHING_TIME_CONSTANT_MS = 4_000L
private const val GPS_BEARING_MIN_SPEED_MPS = 0.45f
private const val MAX_TRUSTED_SPEED_ACCURACY_MPS = 1.5f
private const val MAX_SPEED_ACCURACY_RATIO = 0.75f
private const val MAX_TRUSTED_BEARING_ACCURACY_DEG = 45f
private const val CONFIRMED_STOP_MAX_SPEED_MPS = 0.25f
private const val CONFIRMED_STOP_MAX_SPEED_ACCURACY_MPS = 0.8f
private const val CONFIRMED_STOP_MAX_DERIVED_SPEED_MPS = 0.35f
private const val CONFIRMED_STOP_MAX_POSITION_ACCURACY_M = 20f
private const val DERIVED_MOTION_MIN_WINDOW_MS = 900L
private const val DERIVED_WALKING_SPEED_MAX_ACCURACY_M = 35f
private const val DERIVED_WALKING_SPEED_MIN_MPS = 0.25f
private const val DERIVED_WALKING_SPEED_MAX_MPS = 2.4f
private const val DERIVED_WALKING_SPEED_CAP_MPS = 1.8f
private const val DERIVED_BIKE_SPEED_MAX_ACCURACY_M = 25f
private const val DERIVED_BIKE_SPEED_MIN_MPS = 0.5f
private const val DERIVED_BIKE_SPEED_MAX_MPS = 20f
private const val DERIVED_BIKE_SPEED_CAP_MPS = 15f
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
private const val MOVING_CORRECTION_ACCURACY_DEADBAND_SCALE = 0.18f
private const val MOVING_CORRECTION_MIN_DEADBAND_M = 1.5f
private const val MOVING_CORRECTION_MAX_DEADBAND_M = 5.5f
private const val FULL_WEIGHT_CORRECTION_ACCURACY_M = 10f
private const val MAX_CORRECTION_QUALITY_REDUCTION = 0.45f
private const val QUALITY_WEIGHT_DISTANCE_EPSILON_M = 0.1f
private const val LARGE_CORRECTION_MIN_DISTANCE_M = 18f
private const val LARGE_CORRECTION_MIN_ACCURACY_M = 14f
private const val LARGE_CORRECTION_FORCE_CLAMP_DISTANCE_M = 26f
private const val LARGE_CORRECTION_BASE_VISIBLE_M = 8f
private const val LARGE_CORRECTION_ACCURACY_SCALE = 0.35f
private const val LARGE_CORRECTION_SPEED_SCALE = 2.2f
