package com.glancemap.glancemapwearos.domain.sensors

import kotlin.math.abs

internal const val FUSED_READY_TIMEOUT_MS = 1_000L
internal const val FUSED_STALE_SAMPLE_RETRY_REASON = "sample_stale_retry"
private const val FUSED_STALE_RETRY_READY_TIMEOUT_MS = 1_000L
private const val FUSED_STALE_RETRY_LOW_POWER_READY_TIMEOUT_MS = 1_200L

internal fun resolveFusedReadyTimeoutMs(
    requestReason: String,
    lowPowerMode: Boolean,
    recalibrationBoostActive: Boolean,
): Long =
    when {
        requestReason != FUSED_STALE_SAMPLE_RETRY_REASON -> FUSED_READY_TIMEOUT_MS
        lowPowerMode && !recalibrationBoostActive ->
            FUSED_STALE_RETRY_LOW_POWER_READY_TIMEOUT_MS
        else -> FUSED_STALE_RETRY_READY_TIMEOUT_MS
    }

internal data class FusedWarmupState(
    val firstUsableSampleAtElapsedMs: Long = 0L,
    val usableSampleCount: Int = 0,
    val stableClusterStartedAtElapsedMs: Long = 0L,
    val stableSampleCount: Int = 0,
    val lastHeadingDeg: Float? = null,
    val lastSampleAtElapsedMs: Long = 0L,
    val relockResetCount: Int = 0,
)

internal data class FusedWarmupUpdate(
    val state: FusedWarmupState,
    val warmupAgeMs: Long,
    val stableAgeMs: Long,
    val previousHeadingDeg: Float?,
    val stepDeg: Float?,
    val allowedStepDeg: Float?,
    val sampleGapMs: Long?,
    val relockDetected: Boolean,
    val ready: Boolean,
)

private data class FusedWarmupStepEvaluation(
    val previousHeadingDeg: Float?,
    val stepDeg: Float?,
    val allowedStepDeg: Float?,
    val sampleGapMs: Long?,
    val relockDetected: Boolean,
)

internal fun updateFusedWarmup(
    state: FusedWarmupState,
    headingDeg: Float,
    nowElapsedMs: Long,
    minimumSamples: Int = FUSED_WARMUP_MIN_USABLE_SAMPLES,
    minimumDurationMs: Long = FUSED_WARMUP_MIN_DURATION_MS,
): FusedWarmupUpdate {
    val firstSampleAtElapsedMs =
        state.firstUsableSampleAtElapsedMs.takeIf { it > 0L } ?: nowElapsedMs
    val step =
        evaluateFusedWarmupStep(
            state = state,
            headingDeg = headingDeg,
            nowElapsedMs = nowElapsedMs,
        )
    val stableClusterStartedAtElapsedMs =
        when {
            state.stableClusterStartedAtElapsedMs <= 0L || step.relockDetected -> nowElapsedMs
            else -> state.stableClusterStartedAtElapsedMs
        }
    val stableSampleCount =
        if (state.stableSampleCount <= 0 || step.relockDetected) {
            1
        } else {
            state.stableSampleCount + 1
        }
    val nextSampleCount = state.usableSampleCount.coerceAtLeast(0) + 1
    val warmupAgeMs = (nowElapsedMs - firstSampleAtElapsedMs).coerceAtLeast(0L)
    val stableAgeMs =
        (nowElapsedMs - stableClusterStartedAtElapsedMs).coerceAtLeast(0L)
    val nextState =
        FusedWarmupState(
            firstUsableSampleAtElapsedMs = firstSampleAtElapsedMs,
            usableSampleCount = nextSampleCount,
            stableClusterStartedAtElapsedMs = stableClusterStartedAtElapsedMs,
            stableSampleCount = stableSampleCount,
            lastHeadingDeg = headingDeg,
            lastSampleAtElapsedMs = nowElapsedMs,
            relockResetCount = state.relockResetCount + if (step.relockDetected) 1 else 0,
        )
    return FusedWarmupUpdate(
        state = nextState,
        warmupAgeMs = warmupAgeMs,
        stableAgeMs = stableAgeMs,
        previousHeadingDeg = step.previousHeadingDeg,
        stepDeg = step.stepDeg,
        allowedStepDeg = step.allowedStepDeg,
        sampleGapMs = step.sampleGapMs,
        relockDetected = step.relockDetected,
        ready =
            nextSampleCount >= minimumSamples &&
                warmupAgeMs >= minimumDurationMs &&
                stableSampleCount >= minimumSamples &&
                stableAgeMs >= minimumDurationMs,
    )
}

private fun evaluateFusedWarmupStep(
    state: FusedWarmupState,
    headingDeg: Float,
    nowElapsedMs: Long,
): FusedWarmupStepEvaluation {
    val previousHeadingDeg = state.lastHeadingDeg
    val sampleGapMs =
        state.lastSampleAtElapsedMs
            .takeIf { it > 0L }
            ?.let { (nowElapsedMs - it).coerceAtLeast(0L) }
    val stepDeg =
        previousHeadingDeg?.let { previous ->
            abs(shortestAngleDiffDeg(target = headingDeg, current = previous))
        }
    val allowedStepDeg = sampleGapMs?.let(::maxPlausibleFusedWarmupStepDeg)
    return FusedWarmupStepEvaluation(
        previousHeadingDeg = previousHeadingDeg,
        stepDeg = stepDeg,
        allowedStepDeg = allowedStepDeg,
        sampleGapMs = sampleGapMs,
        relockDetected =
            stepDeg != null && allowedStepDeg != null && stepDeg > allowedStepDeg,
    )
}

internal fun maxPlausibleFusedWarmupStepDeg(sampleGapMs: Long): Float {
    val additionalGapMs =
        (sampleGapMs - FUSED_WARMUP_BASE_STEP_INTERVAL_MS).coerceAtLeast(0L)
    val cadenceAllowanceDeg =
        additionalGapMs * FUSED_WARMUP_ADDITIONAL_RATE_DEG_PER_SEC / 1_000f
    return (FUSED_WARMUP_BASE_MAX_STEP_DEG + cadenceAllowanceDeg)
        .coerceAtMost(FUSED_WARMUP_ABSOLUTE_MAX_STEP_DEG)
}

internal const val FUSED_WARMUP_MIN_USABLE_SAMPLES = 2
internal const val FUSED_WARMUP_MIN_DURATION_MS = 120L
internal const val FUSED_WARMUP_BASE_MAX_STEP_DEG = 50f
internal const val FUSED_WARMUP_BASE_STEP_INTERVAL_MS = 50L
internal const val FUSED_WARMUP_ADDITIONAL_RATE_DEG_PER_SEC = 360f
internal const val FUSED_WARMUP_ABSOLUTE_MAX_STEP_DEG = 120f
