package com.glancemap.glancemapwearos.core.service.location.engine

import android.location.Location
import com.glancemap.glancemapwearos.core.service.location.activity.LocationActivityState
import com.glancemap.glancemapwearos.core.service.location.activity.LocationActivityTracker
import com.glancemap.glancemapwearos.core.service.location.config.BACKGROUND_MIN_DISTANCE_M
import com.glancemap.glancemapwearos.core.service.location.config.FOREGROUND_MIN_DISTANCE_M
import com.glancemap.glancemapwearos.core.service.location.config.HIGH_ACCURACY_BURST_DURATION
import com.glancemap.glancemapwearos.core.service.location.config.HIGH_ACCURACY_BURST_EARLY_STOP_ACCURACY_M
import com.glancemap.glancemapwearos.core.service.location.config.HIGH_ACCURACY_BURST_INTERVAL
import com.glancemap.glancemapwearos.core.service.location.config.IMMEDIATE_COOLDOWN_MS
import com.glancemap.glancemapwearos.core.service.location.config.JITTER_THRESHOLD_MOVING
import com.glancemap.glancemapwearos.core.service.location.config.JITTER_THRESHOLD_STATIONARY
import com.glancemap.glancemapwearos.core.service.location.config.MAX_USER_INTERVAL_MS
import com.glancemap.glancemapwearos.core.service.location.config.MIN_USER_INTERVAL_MS
import com.glancemap.glancemapwearos.core.service.location.config.MOVEMENT_HISTORY_DURATION
import com.glancemap.glancemapwearos.core.service.location.filter.LocationOutputFilter
import com.glancemap.glancemapwearos.core.service.location.model.GpsSignalSnapshot
import com.glancemap.glancemapwearos.core.service.location.model.GpsSignalTracker
import com.glancemap.glancemapwearos.core.service.location.policy.FixAcceptancePolicy
import com.glancemap.glancemapwearos.core.service.location.policy.LocationFixPolicy
import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode
import com.glancemap.glancemapwearos.core.service.location.policy.LocationUpdatePolicy
import com.glancemap.glancemapwearos.core.service.location.policy.LocationRuntimeMode
import com.glancemap.glancemapwearos.core.service.location.processing.LocationCandidateProcessor
import com.glancemap.glancemapwearos.core.service.location.processing.ProcessedLocationCandidate
import com.glancemap.glancemapwearos.core.service.location.runtime.ImmediateRequestBurstController
import com.glancemap.glancemapwearos.core.service.location.telemetry.LocationServiceTelemetry

internal class LocationEngine(
    private val telemetry: LocationServiceTelemetry
) {
    private val activityTracker = LocationActivityTracker(
        movementHistoryDurationMs = MOVEMENT_HISTORY_DURATION
    )
    private val candidateProcessor = LocationCandidateProcessor(
        activityTracker = activityTracker,
        telemetry = telemetry,
        jitterThresholdMoving = JITTER_THRESHOLD_MOVING,
        jitterThresholdStationary = JITTER_THRESHOLD_STATIONARY,
        burstEarlyStopAccuracyM = HIGH_ACCURACY_BURST_EARLY_STOP_ACCURACY_M
    )
    private val immediateRequestBurstController = ImmediateRequestBurstController(
        cooldownMs = IMMEDIATE_COOLDOWN_MS
    )
    private val gpsSignalTracker = GpsSignalTracker()
    private val locationOutputFilter = LocationOutputFilter()
    private val requestStateLock = Any()

    private var appliedRequestState: AppliedRequestState? = null
    private var previousSourceModeBeforeRefresh: LocationSourceMode? = null
    private var isInHighAccuracyBurst: Boolean = false
    private var activeBurstSource: String = "na"

    val gpsSignalSnapshot: GpsSignalSnapshot
        get() = gpsSignalTracker.snapshot

    fun activityState(): LocationActivityState = candidateProcessor.activityState()

    fun isBurstActive(): Boolean = synchronized(requestStateLock) { isInHighAccuracyBurst }

    fun currentRequestIntervalOr(fallbackIntervalMs: Long): Long {
        return synchronized(requestStateLock) {
            appliedRequestState?.intervalMs ?: fallbackIntervalMs
        }
    }

    fun currentSourceModeOrNull(): LocationSourceMode? = synchronized(requestStateLock) {
        appliedRequestState?.sourceMode
    }

    fun currentRuntimeModeOrNull(): LocationRuntimeMode? = synchronized(requestStateLock) {
        appliedRequestState?.mode
    }

    fun onLocationAvailability(isAvailable: Boolean, nowElapsedMs: Long) {
        val shouldLog = gpsSignalTracker.onLocationAvailability(
            isAvailable = isAvailable,
            nowElapsedMs = nowElapsedMs
        )
        if (shouldLog) {
            telemetry.logLocationAvailabilityChanged(isAvailable)
        }
    }

    fun updateGpsSignalSample(
        nowElapsedMs: Long,
        ageMs: Long,
        accuracyM: Float,
        freshnessMaxAgeMs: Long,
        sourceMode: LocationSourceMode?,
        provider: String?,
        accepted: Boolean?
    ) {
        val previousSnapshot = gpsSignalTracker.snapshot
        val sample = gpsSignalTracker.onGpsSignalSample(
            nowElapsedMs = nowElapsedMs,
            ageMs = ageMs,
            accuracyM = accuracyM,
            freshnessMaxAgeMs = freshnessMaxAgeMs,
            sourceMode = sourceMode
        )
        telemetry.logGpsSignalSample(
            ageMs = sample.ageMs,
            fresh = sample.fresh,
            maxAgeMs = sample.maxAgeMs,
            accuracyM = sample.accuracyM,
            sourceMode = sample.sourceMode?.telemetryValue ?: "none",
            watchGpsDegraded = sample.watchGpsDegraded,
            watchGpsDegradedFixStreak = sample.watchGpsDegradedFixStreak,
            provider = provider,
            accepted = accepted
        )
        if (previousSnapshot.watchGpsDegraded != sample.watchGpsDegraded) {
            telemetry.logWatchGpsDegradedStateChanged(
                degraded = sample.watchGpsDegraded,
                accuracyM = sample.accuracyM,
                streak = sample.watchGpsDegradedFixStreak,
                sourceMode = sample.sourceMode?.telemetryValue ?: "none"
            )
        }
    }

    fun acceptCachedLocation(location: Location, nowElapsedMs: Long, ageMs: Long): Location {
        candidateProcessor.acceptCachedLocation(
            location = location,
            nowElapsedMs = nowElapsedMs,
            ageMs = ageMs
        )
        return filterLocationForOutput(location, nowElapsedMs)
    }

    fun onNoPermissions(nowElapsedMs: Long) {
        synchronized(requestStateLock) {
            clearRequestStateLocked()
            previousSourceModeBeforeRefresh = null
        }
        candidateProcessor.resetState()
        gpsSignalTracker.onNoPermissions(nowElapsedMs)
        locationOutputFilter.reset()
    }

    fun onTrackingDisabled() {
        synchronized(requestStateLock) {
            clearRequestStateLocked()
            previousSourceModeBeforeRefresh = null
        }
        gpsSignalTracker.onSourceModeChanged(sourceMode = null)
        locationOutputFilter.reset()
    }

    fun filterLocationForOutput(location: Location, nowElapsedMs: Long): Location {
        val filtered = locationOutputFilter.filter(location = location, nowElapsedMs = nowElapsedMs)
        return if (LocationFixPolicy.hasValidCoordinates(filtered)) {
            filtered
        } else {
            Location(location)
        }
    }

    fun resolveRequestSpec(
        interactive: Boolean,
        passiveTracking: Boolean,
        watchOnly: Boolean,
        hasFinePermission: Boolean,
        userIntervalMs: Long,
        ambientIntervalMs: Long
    ): RequestSpec? {
        val burstActive = synchronized(requestStateLock) { isInHighAccuracyBurst }
        val config = LocationUpdatePolicy.resolveServiceConfig(
            isInHighAccuracyBurst = burstActive,
            interactive = interactive,
            passiveTracking = passiveTracking,
            watchOnly = watchOnly,
            hasFinePermission = hasFinePermission,
            userIntervalMs = userIntervalMs,
            ambientUserIntervalMs = ambientIntervalMs,
            minUserIntervalMs = MIN_USER_INTERVAL_MS,
            maxUserIntervalMs = MAX_USER_INTERVAL_MS,
            minAmbientIntervalMs = MIN_USER_INTERVAL_MS,
            highAccuracyBurstIntervalMs = HIGH_ACCURACY_BURST_INTERVAL,
            foregroundMinDistanceM = FOREGROUND_MIN_DISTANCE_M,
            backgroundMinDistanceM = BACKGROUND_MIN_DISTANCE_M
        ) ?: return null
        return RequestSpec(
            priority = config.priority,
            intervalMs = config.intervalMs,
            minDistanceMeters = config.minDistanceMeters,
            mode = config.mode,
            sourceMode = config.sourceMode
        )
    }

    fun isRequestAlreadyApplied(spec: RequestSpec, effectiveIntervalMs: Long): Boolean {
        val currentState = synchronized(requestStateLock) { appliedRequestState } ?: return false
        return currentState.priority == spec.priority &&
            currentState.intervalMs == spec.intervalMs &&
            currentState.minDistanceMeters == spec.minDistanceMeters &&
            currentState.mode == spec.mode &&
            currentState.sourceMode == spec.sourceMode &&
            effectiveIntervalMs == spec.intervalMs
    }

    fun markRequestApplied(spec: RequestSpec) {
        val previousSourceMode = synchronized(requestStateLock) {
            previousSourceModeBeforeRefresh ?: appliedRequestState?.sourceMode
        }
        if (shouldResetOutputFilterForSourceTransition(previousSourceMode, spec.sourceMode)) {
            locationOutputFilter.reset()
        }
        synchronized(requestStateLock) {
            appliedRequestState = AppliedRequestState(
                priority = spec.priority,
                intervalMs = spec.intervalMs,
                minDistanceMeters = spec.minDistanceMeters,
                mode = spec.mode,
                sourceMode = spec.sourceMode
            )
            previousSourceModeBeforeRefresh = null
        }
        gpsSignalTracker.onSourceModeChanged(spec.sourceMode)
    }

    fun hasAppliedRequest(): Boolean {
        return synchronized(requestStateLock) { appliedRequestState != null }
    }

    fun markRequestFailed() {
        synchronized(requestStateLock) {
            clearRequestStateLocked()
            previousSourceModeBeforeRefresh = null
        }
        gpsSignalTracker.onSourceModeChanged(sourceMode = null)
    }

    fun forceRequestRefresh() {
        synchronized(requestStateLock) {
            previousSourceModeBeforeRefresh = appliedRequestState?.sourceMode
            clearRequestStateLocked()
        }
    }

    fun requestImmediateBurst(nowElapsedMs: Long, source: String): ImmediateBurstDecision {
        val burstActive = synchronized(requestStateLock) { isInHighAccuracyBurst }
        return when (val decision = immediateRequestBurstController.request(nowElapsedMs)) {
            is ImmediateRequestBurstController.Decision.SkipActiveBurst -> {
                telemetry.onImmediateRequestSkippedBurst(
                    nowElapsedMs = nowElapsedMs,
                    activityState = activityTracker.state,
                    burst = burstActive,
                    source = source
                )
                ImmediateBurstDecision.SkipActiveBurst
            }
            is ImmediateRequestBurstController.Decision.SkipCooldown -> {
                telemetry.onImmediateRequestSkippedCooldown(
                    nowElapsedMs = nowElapsedMs,
                    activityState = activityTracker.state,
                    burst = burstActive,
                    source = source
                )
                ImmediateBurstDecision.SkipCooldown
            }
            is ImmediateRequestBurstController.Decision.Start -> {
                synchronized(requestStateLock) {
                    isInHighAccuracyBurst = true
                    activeBurstSource = source
                }
                telemetry.onImmediateRequestStarted(
                    durationMs = HIGH_ACCURACY_BURST_DURATION,
                    burstId = decision.burstId,
                    source = source
                )
                ImmediateBurstDecision.Started(burstId = decision.burstId)
            }
        }
    }

    fun endHighAccuracyBurst(reason: String, expectedBurstId: Long? = null): EndBurstResult? {
        val endedBurstId = immediateRequestBurstController.end(expectedBurstId = expectedBurstId) ?: return null
        val burstSource = synchronized(requestStateLock) {
            val source = activeBurstSource
            isInHighAccuracyBurst = false
            activeBurstSource = "na"
            source
        }
        telemetry.onImmediateRequestEnded(
            burstId = endedBurstId,
            reason = reason,
            source = burstSource
        )
        return EndBurstResult(
            burstId = endedBurstId,
            source = burstSource
        )
    }

    fun processImmediateCandidate(
        location: Location,
        nowElapsedMs: Long,
        acceptance: FixAcceptancePolicy,
        strictMaxAgeMs: Long,
        hardMaxAgeMs: Long,
        source: String,
        sourceMode: LocationSourceMode
    ): ProcessedLocationCandidate {
        val burstActive = synchronized(requestStateLock) { isInHighAccuracyBurst }
        if (!LocationFixPolicy.hasValidCoordinates(location)) {
            telemetry.logInvalidCoordinatesDropped(
                nowElapsedMs = nowElapsedMs,
                activityState = activityState(),
                burst = burstActive,
                source = source,
                latitude = location.latitude,
                longitude = location.longitude,
                provider = location.provider
            )
            return ProcessedLocationCandidate(
                acceptedLocation = null,
                shouldEndBurstEarly = false,
                activityStateChanged = false
            )
        }
        val ageMs = LocationFixPolicy.locationAgeMs(location, nowElapsedMs)
        val outcome = candidateProcessor.processImmediateCandidate(
            location = location,
            nowElapsedMs = nowElapsedMs,
            acceptance = acceptance,
            strictMaxAgeMs = strictMaxAgeMs,
            hardMaxAgeMs = hardMaxAgeMs,
            source = source,
            isInHighAccuracyBurst = burstActive,
            burstEarlyStopMaxAgeMs = strictMaxAgeMs
        )
        updateGpsSignalSample(
            nowElapsedMs = nowElapsedMs,
            ageMs = ageMs,
            accuracyM = location.accuracy,
            freshnessMaxAgeMs = strictMaxAgeMs,
            sourceMode = sourceMode,
            provider = location.provider,
            accepted = outcome.acceptedLocation != null
        )
        return outcome
    }

    fun processCallbackCandidate(
        location: Location,
        nowElapsedMs: Long,
        acceptance: FixAcceptancePolicy,
        strictMaxAgeMs: Long,
        hardMaxAgeMs: Long,
        callbackOrigin: LocationSourceMode
    ): ProcessedLocationCandidate {
        val burstActive = synchronized(requestStateLock) { isInHighAccuracyBurst }
        if (!LocationFixPolicy.hasValidCoordinates(location)) {
            telemetry.logInvalidCoordinatesDropped(
                nowElapsedMs = nowElapsedMs,
                activityState = activityState(),
                burst = burstActive,
                source = "callback_candidate_${callbackOrigin.telemetryValue}",
                latitude = location.latitude,
                longitude = location.longitude,
                provider = location.provider
            )
            return ProcessedLocationCandidate(
                acceptedLocation = null,
                shouldEndBurstEarly = false,
                activityStateChanged = false
            )
        }
        val ageMs = LocationFixPolicy.locationAgeMs(location, nowElapsedMs)
        val outcome = candidateProcessor.processCallbackCandidate(
            location = location,
            nowElapsedMs = nowElapsedMs,
            acceptance = acceptance,
            strictMaxAgeMs = strictMaxAgeMs,
            hardMaxAgeMs = hardMaxAgeMs,
            isInHighAccuracyBurst = burstActive,
            callbackOrigin = callbackOrigin,
            burstEarlyStopMaxAgeMs = strictMaxAgeMs
        )
        updateGpsSignalSample(
            nowElapsedMs = nowElapsedMs,
            ageMs = ageMs,
            accuracyM = location.accuracy,
            freshnessMaxAgeMs = strictMaxAgeMs,
            sourceMode = callbackOrigin,
            provider = location.provider,
            accepted = outcome.acceptedLocation != null
        )
        return outcome
    }

    fun stopAndReset() {
        immediateRequestBurstController.end(expectedBurstId = null)
        synchronized(requestStateLock) {
            isInHighAccuracyBurst = false
            activeBurstSource = "na"
            clearRequestStateLocked()
            previousSourceModeBeforeRefresh = null
        }
        candidateProcessor.resetState()
        gpsSignalTracker.reset()
        locationOutputFilter.reset()
    }

    private fun clearRequestStateLocked() {
        appliedRequestState = null
    }
}

private data class AppliedRequestState(
    val priority: Int,
    val intervalMs: Long,
    val minDistanceMeters: Float,
    val mode: LocationRuntimeMode,
    val sourceMode: LocationSourceMode
)

internal fun shouldResetOutputFilterForSourceTransition(
    previousSourceMode: LocationSourceMode?,
    nextSourceMode: LocationSourceMode
): Boolean {
    return previousSourceMode != null && previousSourceMode != nextSourceMode
}

internal data class RequestSpec(
    val priority: Int,
    val intervalMs: Long,
    val minDistanceMeters: Float,
    val mode: LocationRuntimeMode,
    val sourceMode: LocationSourceMode
)

internal sealed class ImmediateBurstDecision {
    data object SkipActiveBurst : ImmediateBurstDecision()
    data object SkipCooldown : ImmediateBurstDecision()
    data class Started(val burstId: Long) : ImmediateBurstDecision()
}

internal data class EndBurstResult(
    val burstId: Long,
    val source: String
)
