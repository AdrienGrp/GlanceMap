package com.glancemap.glancemapwearos.domain.sensors

internal fun hasTrustedFusedHeadingError(input: FusedLargeJumpInput): Boolean =
    isTrustedFusedHeadingError(
        errorDeg = input.conservativeHeadingErrorDeg,
        maximumTrustedErrorDeg = FUSED_RESTART_TRUSTED_CONSERVATIVE_ERROR_DEG,
    ) ||
        isTrustedFusedHeadingError(
            errorDeg = input.headingErrorDeg,
            maximumTrustedErrorDeg = FUSED_RESTART_TRUSTED_LIVE_ERROR_DEG,
        )

private fun isTrustedFusedHeadingError(
    errorDeg: Float,
    maximumTrustedErrorDeg: Float,
): Boolean = errorDeg.isFinite() && errorDeg in 0f..maximumTrustedErrorDeg

internal fun isFusedPendingJumpConfirmationReady(input: FusedLargeJumpInput): Boolean {
    if (!input.hasPendingLargeJump || !input.pendingDeltaDeg.isFinite()) return false
    val fastTurnReady =
        input.pendingAgeMs >= FUSED_LARGE_JUMP_MIN_CONFIRM_AGE_MS &&
            input.pendingConsistentSampleCount >= FUSED_FAST_TURN_CONFIRM_MIN_SAMPLES &&
            input.pendingDeltaDeg <= FUSED_FAST_TURN_CONFIRM_MAX_DELTA_DEG
    val stableLongEnough =
        input.pendingAgeMs >= FUSED_LARGE_JUMP_MIN_CONFIRM_AGE_MS &&
            input.pendingDeltaDeg <= FUSED_WEAK_CONFIDENCE_LARGE_JUMP_MAX_DELTA_DEG
    return fastTurnReady || stableLongEnough
}

internal fun isFusedPendingJumpTimedOut(input: FusedLargeJumpInput): Boolean =
    input.hasPendingLargeJump &&
        input.pendingAgeMs >= FUSED_LARGE_JUMP_ABSOLUTE_TIMEOUT_MS
