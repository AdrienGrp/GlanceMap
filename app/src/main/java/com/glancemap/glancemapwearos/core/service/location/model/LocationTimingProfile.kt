package com.glancemap.glancemapwearos.core.service.location.model

internal data class LocationTimingProfile(
    val intervalMs: Long,
    val markerPredictionFreshnessMaxAgeMs: Long,
    val indicatorStaleThresholdMs: Long,
    val uiImmediateSkipMaxAgeMs: Long,
    val strictFreshFixMaxAgeMs: Long,
    val burstEarlyStopMaxAgeMs: Long,
    val wakeAnchorMaxAgeMs: Long,
    val correctionStaleGapMs: Long,
    val wakeCorrectionMinGapMs: Long,
    val stabilizerMaxAcceptedFixAgeMs: Long,
    val stabilizerMaxPredictionWithoutGpsMs: Long,
    val fusionStaleThresholdMs: Long,
    val fusionPredictionHorizonMs: Long,
    val selfHealFixGapMs: Long,
    val autoFusedNoFixFailoverGapMs: Long,
    val autoFusedSevereFailoverGapMs: Long
)

internal fun resolveLocationTimingProfile(gpsIntervalMs: Long): LocationTimingProfile {
    val intervalMs = gpsIntervalMs.coerceAtLeast(MIN_INTERVAL_MS)
    val markerPredictionFreshnessMaxAgeMs = (
        intervalMs + maxOf(MARKER_PREDICTION_MIN_GRACE_MS, intervalMs / 2L)
        ).coerceIn(
        MARKER_PREDICTION_MIN_FRESHNESS_MAX_AGE_MS,
        MARKER_PREDICTION_MAX_FRESHNESS_MAX_AGE_MS
    )
    val strictFreshFixMaxAgeMs = maxOf(
        STRICT_FRESH_FIX_MIN_AGE_MS,
        intervalMs * STRICT_FRESH_FIX_INTERVAL_MULTIPLIER
    )
    val wakeAnchorMaxAgeMs = (intervalMs * WAKE_ANCHOR_INTERVAL_MULTIPLIER)
        .coerceIn(WAKE_ANCHOR_MIN_AGE_MS, WAKE_ANCHOR_MAX_AGE_MS)
    val selfHealFixGapMs = maxOf(SELF_HEAL_MIN_GAP_MS, intervalMs * SELF_HEAL_INTERVAL_MULTIPLIER)
    val autoFusedNoFixFailoverGapMs = maxOf(
        AUTO_FUSED_NO_FIX_FAILOVER_MIN_GAP_MS,
        intervalMs * AUTO_FUSED_NO_FIX_FAILOVER_INTERVAL_MULTIPLIER
    )
    val autoFusedSevereFailoverGapMs = maxOf(
        AUTO_FUSED_SEVERE_FAILOVER_MIN_GAP_MS,
        intervalMs * AUTO_FUSED_SEVERE_FAILOVER_INTERVAL_MULTIPLIER
    )

    return LocationTimingProfile(
        intervalMs = intervalMs,
        markerPredictionFreshnessMaxAgeMs = markerPredictionFreshnessMaxAgeMs,
        indicatorStaleThresholdMs = markerPredictionFreshnessMaxAgeMs,
        uiImmediateSkipMaxAgeMs = markerPredictionFreshnessMaxAgeMs,
        strictFreshFixMaxAgeMs = strictFreshFixMaxAgeMs,
        burstEarlyStopMaxAgeMs = strictFreshFixMaxAgeMs,
        wakeAnchorMaxAgeMs = wakeAnchorMaxAgeMs,
        correctionStaleGapMs = strictFreshFixMaxAgeMs,
        wakeCorrectionMinGapMs = strictFreshFixMaxAgeMs,
        stabilizerMaxAcceptedFixAgeMs = strictFreshFixMaxAgeMs,
        stabilizerMaxPredictionWithoutGpsMs = markerPredictionFreshnessMaxAgeMs,
        fusionStaleThresholdMs = markerPredictionFreshnessMaxAgeMs,
        fusionPredictionHorizonMs = markerPredictionFreshnessMaxAgeMs,
        selfHealFixGapMs = selfHealFixGapMs,
        autoFusedNoFixFailoverGapMs = autoFusedNoFixFailoverGapMs,
        autoFusedSevereFailoverGapMs = autoFusedSevereFailoverGapMs
    )
}

private const val MIN_INTERVAL_MS = 1_000L
private const val MARKER_PREDICTION_MIN_GRACE_MS = 500L
private const val MARKER_PREDICTION_MIN_FRESHNESS_MAX_AGE_MS = 1_500L
private const val MARKER_PREDICTION_MAX_FRESHNESS_MAX_AGE_MS = 12_000L
private const val STRICT_FRESH_FIX_MIN_AGE_MS = 3_000L
private const val STRICT_FRESH_FIX_INTERVAL_MULTIPLIER = 2L
private const val WAKE_ANCHOR_INTERVAL_MULTIPLIER = 3L
private const val WAKE_ANCHOR_MIN_AGE_MS = 5_000L
private const val WAKE_ANCHOR_MAX_AGE_MS = 60_000L
private const val SELF_HEAL_INTERVAL_MULTIPLIER = 3L
private const val SELF_HEAL_MIN_GAP_MS = 4_000L
private const val AUTO_FUSED_NO_FIX_FAILOVER_INTERVAL_MULTIPLIER = 4L
private const val AUTO_FUSED_NO_FIX_FAILOVER_MIN_GAP_MS = 8_000L
private const val AUTO_FUSED_SEVERE_FAILOVER_INTERVAL_MULTIPLIER = 2L
private const val AUTO_FUSED_SEVERE_FAILOVER_MIN_GAP_MS = 4_000L
