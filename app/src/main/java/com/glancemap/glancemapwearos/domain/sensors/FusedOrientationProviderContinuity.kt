package com.glancemap.glancemapwearos.domain.sensors

import kotlin.math.abs

internal const val FUSED_FIRST_SAMPLE_TIMEOUT_MS = 1_200L
internal const val FUSED_STALE_SAMPLE_RETRY_REASON = "sample_stale_retry"
private const val FUSED_STALE_RETRY_FIRST_SAMPLE_TIMEOUT_MS = 500L
private const val FUSED_STALE_RETRY_LOW_POWER_FIRST_SAMPLE_TIMEOUT_MS = 800L

internal fun resolveFusedFirstConfirmedSampleTimeoutMs(
    requestReason: String,
    lowPowerMode: Boolean,
    recalibrationBoostActive: Boolean,
): Long =
    when {
        requestReason != FUSED_STALE_SAMPLE_RETRY_REASON -> FUSED_FIRST_SAMPLE_TIMEOUT_MS
        lowPowerMode && !recalibrationBoostActive ->
            FUSED_STALE_RETRY_LOW_POWER_FIRST_SAMPLE_TIMEOUT_MS
        else -> FUSED_STALE_RETRY_FIRST_SAMPLE_TIMEOUT_MS
    }

internal fun boundedFusedHandoffHeading(
    currentHeadingDeg: Float,
    targetHeadingDeg: Float,
    maxStepDeg: Float,
): Float {
    require(maxStepDeg > 0f)
    val deltaDeg =
        shortestAngleDiffDeg(
            target = targetHeadingDeg,
            current = currentHeadingDeg,
        )
    if (abs(deltaDeg) <= maxStepDeg) return normalize360Deg(targetHeadingDeg)
    return normalize360Deg(
        currentHeadingDeg + deltaDeg.coerceIn(-maxStepDeg, maxStepDeg),
    )
}
