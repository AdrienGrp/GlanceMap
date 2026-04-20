package com.glancemap.glancemapwearos.core.service.location.telemetry

import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.core.service.location.activity.LocationActivityState

internal class LocationServiceTelemetry(
    private val tag: String,
    private val summaryIntervalMs: Long,
) {
    private var summaryWindowStartedAtMs: Long = 0L
    private var locationCallbacks: Int = 0
    private var acceptedFixes: Int = 0
    private var filteredByAccuracy: Int = 0
    private var filteredByInvalidCoordinates: Int = 0
    private var filteredByJitter: Int = 0
    private var filteredByStale: Int = 0
    private var immediateRequests: Int = 0
    private var immediateSkippedCooldown: Int = 0
    private var immediateSkippedBurst: Int = 0
    private var callbackAcceptedFixes: Int = 0
    private var immediateAcceptedFixes: Int = 0
    private var filteredBySourceMismatch: Int = 0
    private var burstInteractiveDoubleApplyCount: Int = 0
    private var fixGapCount: Int = 0
    private var fixGapSumMs: Long = 0L
    private var fixGapMinMs: Long = Long.MAX_VALUE
    private var fixGapMaxMs: Long = 0L
    private var lastAcceptedFixAtMs: Long = 0L

    fun onLocationCallback() {
        locationCallbacks += 1
    }

    fun onFilteredByAccuracy(
        nowElapsedMs: Long,
        activityState: LocationActivityState,
        burst: Boolean,
    ) {
        filteredByAccuracy += 1
        maybeLogSummary(nowElapsedMs, activityState, burst)
    }

    fun onFilteredByJitter(
        nowElapsedMs: Long,
        activityState: LocationActivityState,
        burst: Boolean,
    ) {
        filteredByJitter += 1
        maybeLogSummary(nowElapsedMs, activityState, burst)
    }

    fun onFilteredByStale(
        nowElapsedMs: Long,
        activityState: LocationActivityState,
        burst: Boolean,
    ) {
        filteredByStale += 1
        maybeLogSummary(nowElapsedMs, activityState, burst)
    }

    fun logStaleFixDropped(
        nowElapsedMs: Long,
        activityState: LocationActivityState,
        burst: Boolean,
        source: String,
        ageMs: Long,
        maxAgeMs: Long,
    ) {
        filteredByStale += 1
        log("staleFix: dropped source=$source ageMs=$ageMs maxAgeMs=$maxAgeMs")
        maybeLogSummary(nowElapsedMs, activityState, burst)
    }

    fun logAccuracyFixDropped(
        nowElapsedMs: Long,
        activityState: LocationActivityState,
        burst: Boolean,
        source: String,
        accuracyM: Float,
        maxAccuracyM: Float,
        ageMs: Long,
        maxAgeMs: Long,
    ) {
        filteredByAccuracy += 1
        log(
            "accuracyFix: dropped source=$source accuracyM=${accuracyM.format(1)} " +
                "maxAccuracyM=${maxAccuracyM.format(1)} ageMs=$ageMs maxAgeMs=$maxAgeMs",
        )
        maybeLogSummary(nowElapsedMs, activityState, burst)
    }

    fun logInvalidCoordinatesDropped(
        nowElapsedMs: Long,
        activityState: LocationActivityState,
        burst: Boolean,
        source: String,
        latitude: Double,
        longitude: Double,
        provider: String?,
    ) {
        filteredByInvalidCoordinates += 1
        log(
            "coordFix: dropped source=$source lat=$latitude lon=$longitude " +
                "provider=${provider ?: "unknown"}",
        )
        maybeLogSummary(nowElapsedMs, activityState, burst)
    }

    fun logJumpFixDeferred(
        nowElapsedMs: Long,
        activityState: LocationActivityState,
        burst: Boolean,
        source: String,
        jumpM: Float,
        maxAllowedM: Float,
        gapMs: Long,
        previousSpeedMps: Float,
        candidateSpeedMps: Float,
    ) {
        filteredByJitter += 1
        log(
            "jumpFix: deferred source=$source jumpM=${jumpM.format(1)} " +
                "maxAllowedM=${maxAllowedM.format(1)} gapMs=$gapMs " +
                "prevSpeedMps=${previousSpeedMps.format(2)} candSpeedMps=${candidateSpeedMps.format(2)}",
        )
        maybeLogSummary(nowElapsedMs, activityState, burst)
    }

    fun logJumpFixConfirmed(
        source: String,
        jumpM: Float,
        confirmRadiusM: Float,
        gapMs: Long,
    ) {
        log(
            "jumpFix: confirmed source=$source jumpM=${jumpM.format(1)} " +
                "confirmRadiusM=${confirmRadiusM.format(1)} gapMs=$gapMs",
        )
    }

    fun onCallbackFixAccepted(
        nowElapsedMs: Long,
        activityState: LocationActivityState,
        burst: Boolean,
        source: String,
        ageMs: Long,
        accuracyM: Float,
        provider: String?,
        origin: String,
    ) {
        callbackAcceptedFixes += 1
        onAcceptedFix(
            nowElapsedMs = nowElapsedMs,
            activityState = activityState,
            burst = burst,
            source = "callback",
            sourceDetail = source,
            ageMs = ageMs,
            accuracyM = accuracyM,
            provider = provider,
            origin = origin,
        )
    }

    fun onImmediateFixAccepted(
        nowElapsedMs: Long,
        activityState: LocationActivityState,
        burst: Boolean,
        source: String,
        ageMs: Long,
        accuracyM: Float,
        provider: String?,
        origin: String,
    ) {
        immediateAcceptedFixes += 1
        onAcceptedFix(
            nowElapsedMs = nowElapsedMs,
            activityState = activityState,
            burst = burst,
            source = "immediate",
            sourceDetail = source,
            ageMs = ageMs,
            accuracyM = accuracyM,
            provider = provider,
            origin = origin,
        )
    }

    fun onImmediateRequestSkippedCooldown(
        nowElapsedMs: Long,
        activityState: LocationActivityState,
        burst: Boolean,
        source: String,
    ) {
        immediateSkippedCooldown += 1
        log("immediateRequest: skipCooldown source=$source")
        maybeLogSummary(nowElapsedMs, activityState, burst)
    }

    fun onImmediateRequestSkippedBurst(
        nowElapsedMs: Long,
        activityState: LocationActivityState,
        burst: Boolean,
        source: String,
    ) {
        immediateSkippedBurst += 1
        log("immediateRequest: skipBurst source=$source")
        maybeLogSummary(nowElapsedMs, activityState, burst)
    }

    fun onImmediateRequestStarted(
        durationMs: Long,
        burstId: Long,
        source: String,
    ) {
        immediateRequests += 1
        log("immediateRequest: burstStart id=$burstId source=$source durationMs=$durationMs")
    }

    fun onImmediateRequestEnded(
        burstId: Long,
        reason: String = "timer",
        source: String,
    ) {
        log("immediateRequest: burstEnd id=$burstId source=$source reason=$reason")
    }

    fun logImmediateRequestSkippedPassiveExperiment(
        source: String,
        backend: String,
    ) {
        log("immediateRequest: skipPassiveExperiment source=$source backend=$backend")
    }

    fun logGetCurrentLocationFailed(
        source: String,
        backend: String,
        errorType: String,
        errorDetail: String? = null,
    ) {
        val detailSuffix = errorDetail?.takeIf { it.isNotBlank() }?.let { " errorDetail=$it" } ?: ""
        log("getCurrentLocation: failed source=$source backend=$backend errorType=$errorType$detailSuffix")
    }

    fun logRequestUpdatesFailed(
        priority: Int,
        intervalMs: Long,
        minDistanceMeters: Float,
        backend: String,
        errorType: String,
        errorDetail: String? = null,
    ) {
        val detailSuffix = errorDetail?.takeIf { it.isNotBlank() }?.let { " errorDetail=$it" } ?: ""
        log(
            "requestUpdates failed: priority=$priority intervalMs=$intervalMs " +
                "minDistanceM=${minDistanceMeters.format(1)} backend=$backend " +
                "errorType=$errorType$detailSuffix",
        )
    }

    fun logSelfHealTriggered(
        fixGapMs: Long,
        staleThresholdMs: Long,
        expectedIntervalMs: Long,
        activityState: LocationActivityState,
    ) {
        log(
            "selfHeal: trigger fixGapMs=$fixGapMs staleThresholdMs=$staleThresholdMs " +
                "expectedIntervalMs=$expectedIntervalMs state=${activityState.name}",
        )
    }

    fun logAvailabilityRecoveryTriggered(
        unavailableForMs: Long,
        staleThresholdMs: Long,
        expectedIntervalMs: Long,
        activityState: LocationActivityState,
    ) {
        log(
            "availabilityRecovery: trigger unavailableForMs=$unavailableForMs " +
                "staleThresholdMs=$staleThresholdMs expectedIntervalMs=$expectedIntervalMs " +
                "state=${activityState.name}",
        )
    }

    fun logLocationAvailabilityChanged(available: Boolean) {
        log("locationAvailability: available=$available")
    }

    fun logGpsSignalSample(
        ageMs: Long,
        fresh: Boolean,
        maxAgeMs: Long,
        accuracyM: Float,
    ) {
        log(
            "gpsSignal: sample ageMs=$ageMs fresh=$fresh maxAgeMs=$maxAgeMs " +
                "accuracyM=${accuracyM.format(1)}",
        )
    }

    fun logGpsSignalSample(
        ageMs: Long,
        fresh: Boolean,
        maxAgeMs: Long,
        accuracyM: Float,
        sourceMode: String,
        watchGpsDegraded: Boolean,
        watchGpsDegradedFixStreak: Int,
        provider: String?,
        accepted: Boolean?,
    ) {
        val acceptedToken =
            when (accepted) {
                true -> "true"
                false -> "false"
                null -> "na"
            }
        log(
            "gpsSignal: sample ageMs=$ageMs fresh=$fresh maxAgeMs=$maxAgeMs " +
                "accuracyM=${accuracyM.format(1)} sourceMode=$sourceMode " +
                "provider=${provider ?: "unknown"} accepted=$acceptedToken " +
                "watchGpsDegraded=$watchGpsDegraded " +
                "watchGpsDegradedFixStreak=$watchGpsDegradedFixStreak",
        )
    }

    fun logWatchGpsDegradedStateChanged(
        degraded: Boolean,
        accuracyM: Float,
        streak: Int,
        sourceMode: String,
    ) {
        log(
            "watchGpsDegraded: state=${if (degraded) "entered" else "cleared"} " +
                "sourceMode=$sourceMode accuracyM=${accuracyM.format(1)} streak=$streak",
        )
    }

    fun logAutoFusedFallbackTriggered(
        accuracyM: Float,
        streak: Int,
        requiredStreak: Int,
        thresholdM: Float,
        fixGapMs: Long,
    ) {
        log(
            "sourceFailover: auto_fused->watch_gps reason=accuracy_plateau " +
                "accuracyM=${accuracyM.format(1)} streak=$streak requiredStreak=$requiredStreak " +
                "thresholdM=${thresholdM.format(1)} fixGapMs=$fixGapMs",
        )
    }

    fun logAutoFusedFallbackTriggeredNoFix(
        fixGapMs: Long,
        thresholdMs: Long,
    ) {
        log(
            "sourceFailover: auto_fused->watch_gps reason=no_fix_gap " +
                "fixGapMs=$fixGapMs thresholdMs=$thresholdMs",
        )
    }

    fun logAutoFusedFallbackForced(reason: String) {
        log("sourceFailover: auto_fused->watch_gps reason=$reason")
    }

    fun logAutoFusedNoFixRecoveryProbeTriggered(
        fixGapMs: Long,
        thresholdMs: Long,
        graceMs: Long,
    ) {
        log(
            "sourceFailover: auto_fused recovery_probe reason=no_fix_gap " +
                "fixGapMs=$fixGapMs thresholdMs=$thresholdMs graceMs=$graceMs",
        )
    }

    fun logAutoFusedFallbackCleared(reason: String) {
        log("sourceFailover: cleared reason=$reason")
    }

    fun logAutoFusedRecoveryTriggered(
        reason: String,
        fallbackDurationMs: Long,
        fixGapMs: Long,
        expectedIntervalMs: Long,
    ) {
        log(
            "sourceFailover: watch_gps->auto_fused reason=$reason " +
                "fallbackDurationMs=$fallbackDurationMs fixGapMs=$fixGapMs " +
                "expectedIntervalMs=$expectedIntervalMs",
        )
    }

    fun logLocationEnvironmentPreflight(
        sourceMode: String,
        locationSettingsSatisfied: Boolean?,
        locationSettingsStatusCode: Int?,
        phoneConnected: Boolean?,
        watchGpsAvailability: String?,
        warning: String,
        action: String,
    ) {
        log(
            "locationEnvironment: sourceMode=$sourceMode " +
                "settingsSatisfied=${locationSettingsSatisfied ?: "na"} " +
                "settingsStatus=${locationSettingsStatusCode?.toString() ?: "na"} " +
                "phoneConnected=${phoneConnected ?: "na"} " +
                "watchGps=${watchGpsAvailability ?: "na"} warning=$warning action=$action",
        )
    }

    fun logLocationEnvironmentWarningChanged(warning: String) {
        log("locationEnvironment: warning=$warning")
    }

    fun logCachedLocationAccepted(
        ageMs: Long,
        accuracyM: Float,
        provider: String?,
    ) {
        log(
            "cachedLocation: accepted ageMs=$ageMs accuracyM=$accuracyM " +
                "provider=${provider ?: "unknown"}",
        )
    }

    fun logCachedLocationRejected(
        ageMs: Long,
        accuracyM: Float,
        maxAgeMs: Long,
        maxAccuracyM: Float,
        provider: String?,
    ) {
        log(
            "cachedLocation: rejected ageMs=$ageMs accuracyM=$accuracyM " +
                "maxAgeMs=$maxAgeMs maxAccuracyM=$maxAccuracyM provider=${provider ?: "unknown"}",
        )
    }

    fun logSourceMismatchDropped(
        nowElapsedMs: Long,
        activityState: LocationActivityState,
        burst: Boolean,
        callbackOrigin: String,
        provider: String?,
        expectedOrigin: String,
    ) {
        filteredBySourceMismatch += 1
        log(
            "sourceMismatch: dropped callbackOrigin=$callbackOrigin " +
                "expectedOrigin=$expectedOrigin provider=${provider ?: "unknown"}",
        )
        maybeLogSummary(nowElapsedMs, activityState, burst)
    }

    fun logKeepAppOpen(enabled: Boolean) {
        log("keepAppOpen: $enabled")
    }

    fun logScreenState(screenState: String) {
        log("screenState: $screenState")
    }

    fun logTrackingEnabled(enabled: Boolean) {
        log("tracking: enabled=$enabled")
    }

    fun logRuntimeStateApplied(
        screenState: String,
        trackingEnabled: Boolean,
        screenStateChanged: Boolean,
        trackingChanged: Boolean,
        backgroundGpsEnabled: Boolean,
    ) {
        val changedFields =
            buildList {
                if (screenStateChanged) add("screenState")
                if (trackingChanged) add("tracking")
            }.joinToString(separator = ",")
        log(
            "runtimeState: screenState=$screenState trackingEnabled=$trackingEnabled " +
                "backgroundGpsEnabled=$backgroundGpsEnabled changed=$changedFields",
        )
    }

    fun logImmediateLocationWorkCancelled(
        reason: String,
        cancelledBurst: Boolean,
        cancelledFetch: Boolean,
    ) {
        log(
            "immediateWork: cancelled reason=$reason burst=$cancelledBurst fetch=$cancelledFetch",
        )
    }

    fun logActivityTransition(
        from: LocationActivityState,
        to: LocationActivityState,
    ) {
        log("activityState: ${from.name} -> ${to.name}")
    }

    fun logRequestUpdatesApplied(
        priority: Int,
        intervalMs: Long,
        minDistanceMeters: Float,
        activityState: LocationActivityState,
        bound: Boolean,
        keepOpen: Boolean,
        watchOnly: Boolean,
        burst: Boolean,
        backend: String,
        runtimeMode: String,
        trackingEnabled: Boolean,
        interactive: Boolean,
        screenState: String,
        hasFinePermission: Boolean,
        hasCoarsePermission: Boolean,
        passivePriority: Boolean,
    ) {
        if (burst && runtimeMode == "INTERACTIVE") {
            burstInteractiveDoubleApplyCount += 1
        }
        log(
            "requestUpdates applied: priority=$priority intervalMs=$intervalMs " +
                "minDistanceM=$minDistanceMeters state=${activityState.name} " +
                "bound=$bound keepOpen=$keepOpen watchOnly=$watchOnly burst=$burst " +
                "backend=$backend mode=$runtimeMode trackingEnabled=$trackingEnabled " +
                "interactive=$interactive screenState=$screenState " +
                "finePermission=$hasFinePermission coarsePermission=$hasCoarsePermission " +
                "passivePriority=$passivePriority",
        )
    }

    fun logRequestUpdatesCleared(
        reason: String,
        bound: Boolean,
        keepOpen: Boolean,
        trackingEnabled: Boolean,
        screenState: String,
        backgroundGpsEnabled: Boolean,
    ) {
        log(
            "requestUpdates cleared: reason=$reason bound=$bound keepOpen=$keepOpen " +
                "trackingEnabled=$trackingEnabled screenState=$screenState " +
                "backgroundGpsEnabled=$backgroundGpsEnabled",
        )
    }

    fun logLocationBatchProcessed(
        rawCandidates: Int,
        normalizedCandidates: Int,
        acceptedCandidates: Int,
        fallbackUsed: Boolean,
        callbackOrigin: String,
        duplicateCandidatesDropped: Int,
    ) {
        log(
            "locationBatch: raw=$rawCandidates normalized=$normalizedCandidates " +
                "accepted=$acceptedCandidates fallback=$fallbackUsed " +
                "origin=$callbackOrigin duplicatesDropped=$duplicateCandidatesDropped",
        )
    }

    fun setDebugEnabled(enabled: Boolean) {
        DebugTelemetry.setEnabled(enabled)
    }

    private fun recordAcceptedFix(nowElapsedMs: Long) {
        val previousAcceptedAt = lastAcceptedFixAtMs
        if (previousAcceptedAt > 0L) {
            val gap = (nowElapsedMs - previousAcceptedAt).coerceAtLeast(0L)
            fixGapCount += 1
            fixGapSumMs += gap
            if (gap < fixGapMinMs) fixGapMinMs = gap
            if (gap > fixGapMaxMs) fixGapMaxMs = gap
        }
        lastAcceptedFixAtMs = nowElapsedMs
        acceptedFixes += 1
    }

    private fun onAcceptedFix(
        nowElapsedMs: Long,
        activityState: LocationActivityState,
        burst: Boolean,
        source: String,
        sourceDetail: String,
        ageMs: Long,
        accuracyM: Float,
        provider: String?,
        origin: String,
    ) {
        recordAcceptedFix(nowElapsedMs)
        log(
            "fixAccepted: source=$source detail=$sourceDetail ageMs=$ageMs " +
                "accuracyM=${accuracyM.format(1)} origin=$origin provider=${provider ?: "unknown"}",
        )
        maybeLogSummary(nowElapsedMs, activityState, burst)
    }

    private fun maybeLogSummary(
        nowElapsedMs: Long,
        activityState: LocationActivityState?,
        burst: Boolean?,
    ) {
        if (!DebugTelemetry.isEnabled()) return
        if (summaryWindowStartedAtMs == 0L) {
            summaryWindowStartedAtMs = nowElapsedMs
            return
        }
        if (nowElapsedMs - summaryWindowStartedAtMs < summaryIntervalMs) return

        val windowMs = (nowElapsedMs - summaryWindowStartedAtMs).coerceAtLeast(1L)
        val callbackRatePerMin = locationCallbacks * 60_000.0 / windowMs.toDouble()
        val acceptedRatePerMin = acceptedFixes * 60_000.0 / windowMs.toDouble()
        val avgFixGapMs = if (fixGapCount > 0) (fixGapSumMs / fixGapCount).toString() else "na"
        val minFixGapMs = if (fixGapCount > 0) fixGapMinMs.toString() else "na"
        val maxFixGapMs = if (fixGapCount > 0) fixGapMaxMs.toString() else "na"

        summaryWindowStartedAtMs = nowElapsedMs
        log(
            "summary windowMs=$windowMs callbacks=$locationCallbacks cbPerMin=${"%.1f".format(callbackRatePerMin)} " +
                "fixes=$acceptedFixes fixPerMin=${"%.1f".format(acceptedRatePerMin)} " +
                "callbackFixes=$callbackAcceptedFixes immediateFixes=$immediateAcceptedFixes " +
                "fixGapAvgMs=$avgFixGapMs fixGapMinMs=$minFixGapMs fixGapMaxMs=$maxFixGapMs " +
                "filteredAcc=$filteredByAccuracy filteredCoord=$filteredByInvalidCoordinates " +
                "filteredJitter=$filteredByJitter " +
                "filteredStale=$filteredByStale filteredSourceMismatch=$filteredBySourceMismatch " +
                "immediate=$immediateRequests skipCooldown=$immediateSkippedCooldown " +
                "skipBurst=$immediateSkippedBurst " +
                "burstInteractiveDoubleApply=$burstInteractiveDoubleApplyCount " +
                "state=${activityState?.name ?: "UNKNOWN"} " +
                "burst=${burst ?: false}",
        )

        locationCallbacks = 0
        acceptedFixes = 0
        filteredByAccuracy = 0
        filteredByInvalidCoordinates = 0
        filteredByJitter = 0
        filteredByStale = 0
        filteredBySourceMismatch = 0
        immediateRequests = 0
        immediateSkippedCooldown = 0
        immediateSkippedBurst = 0
        callbackAcceptedFixes = 0
        immediateAcceptedFixes = 0
        burstInteractiveDoubleApplyCount = 0
        fixGapCount = 0
        fixGapSumMs = 0L
        fixGapMinMs = Long.MAX_VALUE
        fixGapMaxMs = 0L
    }

    private fun log(message: String) {
        DebugTelemetry.log(tag, message)
    }
}

private fun Float.format(digits: Int): String = "%.${digits}f".format(this)
