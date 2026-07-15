package com.glancemap.glancemapwearos.presentation.features.navigate

import com.glancemap.glancemapwearos.domain.sensors.normalize360Deg
import com.glancemap.glancemapwearos.domain.sensors.shortestAngleDiffDeg
import kotlin.math.abs

internal data class CompassVisualContinuityState(
    val rawHeadingDeg: Float,
    val offsetDeg: Float,
) {
    val targetHeadingDeg: Float
        get() = normalize360Deg(rawHeadingDeg + offsetDeg)

    val active: Boolean
        get() = abs(offsetDeg) >= COMPASS_CONTINUITY_COMPLETE_DELTA_DEG
}

internal fun startCompassVisualContinuity(
    displayedHeadingDeg: Float,
    rawHeadingDeg: Float,
): CompassVisualContinuityState {
    val normalizedRaw = normalize360Deg(rawHeadingDeg.takeIf(Float::isFinite) ?: 0f)
    val normalizedDisplayed =
        normalize360Deg(displayedHeadingDeg.takeIf(Float::isFinite) ?: normalizedRaw)
    return CompassVisualContinuityState(
        rawHeadingDeg = normalizedRaw,
        offsetDeg =
            shortestAngleDiffDeg(
                target = normalizedDisplayed,
                current = normalizedRaw,
            ),
    )
}

internal fun advanceCompassVisualContinuity(
    state: CompassVisualContinuityState,
    rawHeadingDeg: Float,
    elapsedMs: Long,
    correctionRateDegPerSec: Float = COMPASS_CONTINUITY_CORRECTION_RATE_DEG_PER_SEC,
): CompassVisualContinuityState {
    if (!rawHeadingDeg.isFinite()) return state
    val normalizedRawHeadingDeg = normalize360Deg(rawHeadingDeg)
    val rawMovementDeg =
        shortestAngleDiffDeg(
            target = normalizedRawHeadingDeg,
            current = state.rawHeadingDeg,
        )
    val timeLimitedCorrectionDeg =
        correctionRateDegPerSec.coerceAtLeast(0f) *
            elapsedMs.coerceIn(0L, COMPASS_CONTINUITY_MAX_SAMPLE_GAP_MS).toFloat() /
            1_000f
    val maximumCorrectionDeg =
        resolveCompassContinuityCorrectionLimit(
            offsetDeg = state.offsetDeg,
            rawMovementDeg = rawMovementDeg,
            timeLimitedCorrectionDeg = timeLimitedCorrectionDeg,
        )
    val nextOffsetDeg =
        when {
            !state.active || maximumCorrectionDeg <= 0f -> state.offsetDeg
            abs(state.offsetDeg) <= maximumCorrectionDeg -> 0f
            state.offsetDeg > 0f -> state.offsetDeg - maximumCorrectionDeg
            else -> state.offsetDeg + maximumCorrectionDeg
        }
    return state.copy(
        rawHeadingDeg = normalizedRawHeadingDeg,
        offsetDeg = nextOffsetDeg,
    )
}

private fun resolveCompassContinuityCorrectionLimit(
    offsetDeg: Float,
    rawMovementDeg: Float,
    timeLimitedCorrectionDeg: Float,
): Float {
    val correctionOpposesRawMovement =
        (offsetDeg > 0f && rawMovementDeg > 0f) ||
            (offsetDeg < 0f && rawMovementDeg < 0f)
    val correctionFollowsRawMovement =
        (offsetDeg > 0f && rawMovementDeg < 0f) ||
            (offsetDeg < 0f && rawMovementDeg > 0f)
    return when {
        correctionOpposesRawMovement ->
            minOf(
                timeLimitedCorrectionDeg,
                abs(rawMovementDeg) * COMPASS_CONTINUITY_MOVING_CORRECTION_FRACTION,
            )
        correctionFollowsRawMovement ->
            minOf(
                timeLimitedCorrectionDeg,
                (
                    COMPASS_CONTINUITY_MAX_DIRECTION_PRESERVING_STEP_DEG -
                        abs(rawMovementDeg)
                ).coerceAtLeast(0f),
            )
        else -> timeLimitedCorrectionDeg
    }
}

internal const val COMPASS_CONTINUITY_CORRECTION_RATE_DEG_PER_SEC = 180f
private const val COMPASS_CONTINUITY_MOVING_CORRECTION_FRACTION = 0.5f
private const val COMPASS_CONTINUITY_MAX_DIRECTION_PRESERVING_STEP_DEG = 179.5f
private const val COMPASS_CONTINUITY_MAX_SAMPLE_GAP_MS = 250L
private const val COMPASS_CONTINUITY_COMPLETE_DELTA_DEG = 0.2f
