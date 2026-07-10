package com.glancemap.glancemapwearos.core.service.location.service

import com.glancemap.glancemapwearos.core.service.location.config.SCREEN_OFF_RECORDING_MAX_BATCH_DELAY_MS
import com.glancemap.glancemapwearos.core.service.location.model.GpsSignalSnapshot
import com.glancemap.glancemapwearos.core.service.location.model.LocationScreenState
import com.glancemap.glancemapwearos.core.service.location.model.isInteractive
import com.glancemap.glancemapwearos.core.service.location.policy.LocationRuntimeMode
import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode
import com.glancemap.glancemapwearos.core.service.location.policy.NavigationRuntimeDemandReason

internal fun shouldSuppressImmediateBurstForFreshStream(
    runtimeReason: String,
    runtimeMode: LocationRuntimeMode?,
    intervalMs: Long,
    signal: GpsSignalSnapshot,
    nowElapsedMs: Long,
): Boolean {
    val recOrGuidanceStream =
        runtimeReason == NavigationRuntimeDemandReason.RECORDING ||
            runtimeReason == NavigationRuntimeDemandReason.RECORDING_GUIDANCE ||
            runtimeReason == NavigationRuntimeDemandReason.GUIDANCE_VISIBLE ||
            runtimeReason == NavigationRuntimeDemandReason.GUIDANCE_AMBIENT ||
            runtimeReason == NavigationRuntimeDemandReason.GUIDANCE_BACKGROUND
    if (!recOrGuidanceStream || runtimeMode != LocationRuntimeMode.INTERACTIVE || intervalMs > 3_000L) return false
    val fixAgeMs = (nowElapsedMs - signal.lastFixElapsedRealtimeMs).coerceAtLeast(0L)
    val allowedAgeMs = maxOf(intervalMs * 2L, 6_000L)
    return signal.lastFixElapsedRealtimeMs > 0L &&
        fixAgeMs <= allowedAgeMs &&
        signal.lastFixAccuracyM.isFinite() &&
        signal.lastFixAccuracyM <= 35f
}

internal fun resolveMaxUpdateDelayMs(
    screenState: LocationScreenState,
    runtimeReason: String,
    turnByTurnScreenOffBatchingEnabled: Boolean,
    requestSpec: com.glancemap.glancemapwearos.core.service.location.engine.RequestSpec,
): Long =
    when {
        requestSpec.sourceMode != LocationSourceMode.AUTO_FUSED -> 0L
        screenState.isInteractive || requestSpec.mode == LocationRuntimeMode.BURST -> 0L
        runtimeReason == NavigationRuntimeDemandReason.RECORDING ->
            minOf(requestSpec.intervalMs * 5L, SCREEN_OFF_RECORDING_MAX_BATCH_DELAY_MS)
        runtimeReason.isBatchableGuidanceReason() ->
            if (turnByTurnScreenOffBatchingEnabled) requestSpec.intervalMs * 2L else 0L
        runtimeReason == NavigationRuntimeDemandReason.RECORDING_AUTO_PAUSED -> 0L
        requestSpec.mode == LocationRuntimeMode.PASSIVE -> requestSpec.intervalMs * 2L
        else -> 0L
    }

private fun String.isBatchableGuidanceReason(): Boolean =
    this == NavigationRuntimeDemandReason.RECORDING_GUIDANCE ||
        this == NavigationRuntimeDemandReason.GUIDANCE_AMBIENT ||
        this == NavigationRuntimeDemandReason.GUIDANCE_BACKGROUND
