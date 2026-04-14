package com.glancemap.glancemapwearos.core.service.location.service

import android.location.Location
import android.os.SystemClock
import com.glancemap.glancemapwearos.core.service.location.config.WATCH_GPS_ACCURACY_FLOOR_M
import com.glancemap.glancemapwearos.core.service.location.config.WATCH_GPS_ACCURACY_FLOOR_TOLERANCE_M
import com.glancemap.glancemapwearos.core.service.location.engine.LocationEngine
import com.glancemap.glancemapwearos.core.service.location.model.resolveLocationTimingProfile
import com.glancemap.glancemapwearos.core.service.location.policy.LocationFixPolicy
import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode
import com.glancemap.glancemapwearos.core.service.location.telemetry.LocationServiceTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

internal class SelfHealFailoverCoordinator(
    private val serviceScope: CoroutineScope,
    private val isServiceActive: () -> Boolean,
    private val engine: LocationEngine,
    private val telemetry: LocationServiceTelemetry,
    private val requestLocationUpdateIfNeeded: () -> Unit,
    private val requestImmediateLocation: (String) -> Unit,
    private val trackingEnabled: () -> Boolean,
    private val ambientModeActive: () -> Boolean,
    private val hasFinePermission: () -> Boolean,
    private val hasCoarsePermission: () -> Boolean,
    private val watchGpsOnly: () -> Boolean,
    private val lastAnyAcceptedFixAtElapsedMs: () -> Long,
    private val lastCallbackAcceptedFixAtElapsedMs: () -> Long,
    private val lastRequestAppliedAtElapsedMs: () -> Long,
    private val expectedIntervalMs: () -> Long,
    private val strictFreshMaxAgeMs: () -> Long,
) {
    private var autoFusedPoorAccuracyStreak: Int = 0
    private var autoFusedWatchGpsRecoveryStreak: Int = 0
    private var autoFusedFallbackToWatchGps: Boolean = false
    private var autoFusedFallbackSinceElapsedMs: Long = 0L
    private var lastAutoFusedRecoveryProbeAtElapsedMs: Long = 0L
    private var autoFusedRecoveryGraceUntilElapsedMs: Long = 0L
    private var pendingNoFixRecoveryProbeUntilElapsedMs: Long = 0L
    private var lastSelfHealAtElapsedMs: Long = 0L
    private var selfHealJob: Job? = null

    fun isAutoFusedFallbackToWatchGps(): Boolean = autoFusedFallbackToWatchGps

    fun currentLocationSourceMode(): LocationSourceMode =
        if (watchGpsOnly() || autoFusedFallbackToWatchGps) {
            LocationSourceMode.WATCH_GPS
        } else {
            LocationSourceMode.AUTO_FUSED
        }

    fun clearAutoFusedFailoverState(reason: String) {
        clearAutoFusedFailoverStateInternal(reason = reason)
        lastAutoFusedRecoveryProbeAtElapsedMs = 0L
        autoFusedRecoveryGraceUntilElapsedMs = 0L
        pendingNoFixRecoveryProbeUntilElapsedMs = 0L
    }

    fun maybeTriggerAutoFusedFailover(
        acceptedLocation: Location,
        callbackOrigin: LocationSourceMode,
        nowElapsedMs: Long,
    ) {
        if (watchGpsOnly()) {
            clearAutoFusedFailoverStateInternal(reason = "watch_only_enabled")
            return
        }
        if (autoFusedFallbackToWatchGps) {
            maybeRecoverAutoFusedFromWatchGps(
                acceptedLocation = acceptedLocation,
                callbackOrigin = callbackOrigin,
                nowElapsedMs = nowElapsedMs,
            )
            return
        }
        if (callbackOrigin != LocationSourceMode.AUTO_FUSED) return
        if (nowElapsedMs < autoFusedRecoveryGraceUntilElapsedMs) return

        val ageMs = LocationFixPolicy.locationAgeMs(acceptedLocation, nowElapsedMs)
        val isFresh = ageMs != Long.MAX_VALUE && ageMs <= strictFreshMaxAgeMs()
        if (
            isFresh &&
            acceptedLocation.accuracy.isFinite() &&
            acceptedLocation.accuracy <= AUTO_FUSED_NO_FIX_RECOVERY_CLEAR_ACCURACY_M
        ) {
            pendingNoFixRecoveryProbeUntilElapsedMs = 0L
        }
        if (!isFresh) {
            autoFusedPoorAccuracyStreak = 0
            return
        }

        val accuracyM = acceptedLocation.accuracy
        val lastAcceptedFixAt = lastAnyAcceptedFixAtElapsedMs()
        val referenceFixAt =
            if (lastAcceptedFixAt > 0L) {
                lastAcceptedFixAt
            } else {
                lastRequestAppliedAtElapsedMs()
            }
        val fixGapMs =
            if (referenceFixAt > 0L) {
                (nowElapsedMs - referenceFixAt).coerceAtLeast(0L)
            } else {
                Long.MAX_VALUE
            }
        val requiredStreak =
            resolveAutoFusedAccuracyFailoverRequiredStreak(
                accuracyM = accuracyM,
                fixGapMs = fixGapMs,
                expectedIntervalMs = expectedIntervalMs(),
            )
        if (requiredStreak == null) {
            autoFusedPoorAccuracyStreak = 0
            return
        }

        autoFusedPoorAccuracyStreak += 1
        if (autoFusedPoorAccuracyStreak < requiredStreak) return

        autoFusedFallbackToWatchGps = true
        autoFusedWatchGpsRecoveryStreak = 0
        autoFusedFallbackSinceElapsedMs = nowElapsedMs
        lastAutoFusedRecoveryProbeAtElapsedMs = 0L
        telemetry.logAutoFusedFallbackTriggered(
            accuracyM = accuracyM,
            streak = autoFusedPoorAccuracyStreak,
            requiredStreak = requiredStreak,
            thresholdM = resolveAutoFusedFailoverThresholdM(requiredStreak = requiredStreak),
            fixGapMs = fixGapMs,
        )
        requestLocationUpdateIfNeeded()
    }

    fun updateSelfHealMonitor() {
        if (!shouldRunSelfHealMonitor()) {
            selfHealJob?.cancel()
            selfHealJob = null
            return
        }
        if (selfHealJob?.isActive == true) return

        selfHealJob =
            serviceScope.launch {
                while (isServiceActive() && shouldRunSelfHealMonitor()) {
                    delay(SELF_HEAL_CHECK_INTERVAL_MS)
                    maybeTriggerInteractiveSelfHeal(
                        nowElapsedMs = SystemClock.elapsedRealtime(),
                        interactiveTracking = trackingEnabled() && !ambientModeActive(),
                        expectedIntervalMs = expectedIntervalMs(),
                    )
                }
                selfHealJob = null
            }
    }

    fun maybeTriggerInteractiveSelfHealNow(
        nowElapsedMs: Long,
        interactiveTracking: Boolean,
        expectedIntervalMs: Long,
    ) {
        maybeTriggerInteractiveSelfHeal(
            nowElapsedMs = nowElapsedMs,
            interactiveTracking = interactiveTracking,
            expectedIntervalMs = expectedIntervalMs,
        )
    }

    fun stop() {
        selfHealJob?.cancel()
        selfHealJob = null
        autoFusedPoorAccuracyStreak = 0
        autoFusedWatchGpsRecoveryStreak = 0
        autoFusedFallbackToWatchGps = false
        autoFusedFallbackSinceElapsedMs = 0L
        lastAutoFusedRecoveryProbeAtElapsedMs = 0L
        autoFusedRecoveryGraceUntilElapsedMs = 0L
        pendingNoFixRecoveryProbeUntilElapsedMs = 0L
        lastSelfHealAtElapsedMs = 0L
    }

    private fun shouldRunSelfHealMonitor(): Boolean {
        val hasAnyPermission = hasFinePermission() || hasCoarsePermission()
        return trackingEnabled() && !ambientModeActive() && hasAnyPermission
    }

    private fun maybeTriggerInteractiveSelfHeal(
        nowElapsedMs: Long,
        interactiveTracking: Boolean,
        expectedIntervalMs: Long,
    ) {
        if (!interactiveTracking) return
        if (engine.isBurstActive()) return
        if (expectedIntervalMs <= 0L) return

        val lastFixAt =
            if (lastCallbackAcceptedFixAtElapsedMs() > 0L) {
                lastCallbackAcceptedFixAtElapsedMs()
            } else {
                lastAnyAcceptedFixAtElapsedMs()
            }
        val referenceFixAt =
            if (lastFixAt > 0L) {
                lastFixAt
            } else {
                lastRequestAppliedAtElapsedMs()
            }
        if (referenceFixAt <= 0L) return

        val fixGapMs = (nowElapsedMs - referenceFixAt).coerceAtLeast(0L)
        if (
            maybeTriggerAutoFusedRecoveryProbe(
                nowElapsedMs = nowElapsedMs,
                fixGapMs = fixGapMs,
                expectedIntervalMs = expectedIntervalMs,
            )
        ) {
            return
        }

        val timingProfile = resolveLocationTimingProfile(expectedIntervalMs)
        val noFixFailoverThresholdMs = timingProfile.autoFusedNoFixFailoverGapMs
        if (
            maybeTriggerAutoFusedNoFixFailover(
                nowElapsedMs = nowElapsedMs,
                fixGapMs = fixGapMs,
                thresholdMs = noFixFailoverThresholdMs,
            )
        ) {
            return
        }

        val staleThresholdMs = timingProfile.selfHealFixGapMs
        if (fixGapMs < staleThresholdMs) return

        val sinceLastAppliedMs =
            if (lastRequestAppliedAtElapsedMs() > 0L) {
                (nowElapsedMs - lastRequestAppliedAtElapsedMs()).coerceAtLeast(0L)
            } else {
                Long.MAX_VALUE
            }
        if (sinceLastAppliedMs < staleThresholdMs) return

        val sinceLastHealMs =
            if (lastSelfHealAtElapsedMs > 0L) {
                (nowElapsedMs - lastSelfHealAtElapsedMs).coerceAtLeast(0L)
            } else {
                Long.MAX_VALUE
            }
        if (sinceLastHealMs < SELF_HEAL_COOLDOWN_MS) return

        lastSelfHealAtElapsedMs = nowElapsedMs
        telemetry.logSelfHealTriggered(
            fixGapMs = fixGapMs,
            staleThresholdMs = staleThresholdMs,
            expectedIntervalMs = expectedIntervalMs,
            activityState = engine.activityState(),
        )
        engine.forceRequestRefresh()
        serviceScope.launch { requestLocationUpdateIfNeeded() }
    }

    private fun maybeRecoverAutoFusedFromWatchGps(
        acceptedLocation: Location,
        callbackOrigin: LocationSourceMode,
        nowElapsedMs: Long,
    ) {
        if (callbackOrigin != LocationSourceMode.WATCH_GPS) return

        val ageMs = LocationFixPolicy.locationAgeMs(acceptedLocation, nowElapsedMs)
        val isFresh = ageMs != Long.MAX_VALUE && ageMs <= strictFreshMaxAgeMs()
        val accuracyM = acceptedLocation.accuracy
        val isGoodAccuracy =
            accuracyM.isFinite() &&
                (
                    accuracyM <= AUTO_FUSED_RECOVERY_ACCURACY_M ||
                        isNearKnownWatchGpsAccuracyFloor(accuracyM)
                )
        if (!isFresh || !isGoodAccuracy) {
            autoFusedWatchGpsRecoveryStreak = 0
            return
        }

        autoFusedWatchGpsRecoveryStreak += 1
        if (autoFusedWatchGpsRecoveryStreak < AUTO_FUSED_RECOVERY_STREAK) return

        val fallbackDurationMs =
            if (autoFusedFallbackSinceElapsedMs > 0L) {
                (nowElapsedMs - autoFusedFallbackSinceElapsedMs).coerceAtLeast(0L)
            } else {
                0L
            }
        if (fallbackDurationMs < AUTO_FUSED_RECOVERY_MIN_FALLBACK_MS) return

        clearAutoFusedFailoverStateInternal(reason = "auto_recovery_watch_gps_stable")
        autoFusedRecoveryGraceUntilElapsedMs = nowElapsedMs + AUTO_FUSED_RECOVERY_GRACE_MS
        telemetry.logAutoFusedRecoveryTriggered(
            reason = "stable_watch_gps",
            fallbackDurationMs = fallbackDurationMs,
            fixGapMs = ageMs,
            expectedIntervalMs = expectedIntervalMs(),
        )
        requestLocationUpdateIfNeeded()
    }

    private fun maybeTriggerAutoFusedRecoveryProbe(
        nowElapsedMs: Long,
        fixGapMs: Long,
        expectedIntervalMs: Long,
    ): Boolean {
        if (!autoFusedFallbackToWatchGps || watchGpsOnly()) return false
        if (expectedIntervalMs <= 0L) return false
        val fallbackSince = autoFusedFallbackSinceElapsedMs
        if (fallbackSince <= 0L) return false

        val fallbackDurationMs = (nowElapsedMs - fallbackSince).coerceAtLeast(0L)
        val minProbeDurationMs =
            (expectedIntervalMs * AUTO_FUSED_RECOVERY_PROBE_MIN_MULTIPLIER)
                .coerceAtLeast(AUTO_FUSED_RECOVERY_PROBE_MIN_FALLBACK_MS)
        if (fallbackDurationMs < minProbeDurationMs) return false

        val sinceLastProbeMs =
            if (lastAutoFusedRecoveryProbeAtElapsedMs > 0L) {
                (nowElapsedMs - lastAutoFusedRecoveryProbeAtElapsedMs).coerceAtLeast(0L)
            } else {
                Long.MAX_VALUE
            }
        if (sinceLastProbeMs < AUTO_FUSED_RECOVERY_PROBE_COOLDOWN_MS) return false

        clearAutoFusedFailoverStateInternal(reason = "auto_recovery_probe")
        lastAutoFusedRecoveryProbeAtElapsedMs = nowElapsedMs
        autoFusedRecoveryGraceUntilElapsedMs = nowElapsedMs + AUTO_FUSED_RECOVERY_GRACE_MS
        telemetry.logAutoFusedRecoveryTriggered(
            reason = "periodic_probe",
            fallbackDurationMs = fallbackDurationMs,
            fixGapMs = fixGapMs,
            expectedIntervalMs = expectedIntervalMs,
        )
        requestLocationUpdateIfNeeded()
        return true
    }

    private fun maybeTriggerAutoFusedNoFixFailover(
        nowElapsedMs: Long,
        fixGapMs: Long,
        thresholdMs: Long,
    ): Boolean {
        if (watchGpsOnly() || autoFusedFallbackToWatchGps) return false
        if (nowElapsedMs < autoFusedRecoveryGraceUntilElapsedMs) return false
        if (engine.currentSourceModeOrNull() != LocationSourceMode.AUTO_FUSED) return false
        when (
            resolveAutoFusedNoFixRecoveryAction(
                fixGapMs = fixGapMs,
                thresholdMs = thresholdMs,
                nowElapsedMs = nowElapsedMs,
                probeUntilElapsedMs = pendingNoFixRecoveryProbeUntilElapsedMs,
            )
        ) {
            AutoFusedNoFixRecoveryAction.NONE -> return false
            AutoFusedNoFixRecoveryAction.WAIT_FOR_PROBE -> return true
            AutoFusedNoFixRecoveryAction.START_PROBE -> {
                pendingNoFixRecoveryProbeUntilElapsedMs = nowElapsedMs + AUTO_FUSED_NO_FIX_RECOVERY_PROBE_GRACE_MS
                telemetry.logAutoFusedNoFixRecoveryProbeTriggered(
                    fixGapMs = fixGapMs,
                    thresholdMs = thresholdMs,
                    graceMs = AUTO_FUSED_NO_FIX_RECOVERY_PROBE_GRACE_MS,
                )
                requestImmediateLocation(AUTO_FUSED_NO_FIX_RECOVERY_SOURCE)
                return true
            }
            AutoFusedNoFixRecoveryAction.FAILOVER -> Unit
        }

        autoFusedPoorAccuracyStreak = 0
        autoFusedWatchGpsRecoveryStreak = 0
        autoFusedFallbackToWatchGps = true
        autoFusedFallbackSinceElapsedMs = nowElapsedMs
        lastAutoFusedRecoveryProbeAtElapsedMs = 0L
        pendingNoFixRecoveryProbeUntilElapsedMs = 0L
        telemetry.logAutoFusedFallbackTriggeredNoFix(
            fixGapMs = fixGapMs,
            thresholdMs = thresholdMs,
        )
        requestLocationUpdateIfNeeded()
        return true
    }

    private fun clearAutoFusedFailoverStateInternal(reason: String) {
        val wasEnabled = autoFusedFallbackToWatchGps
        autoFusedPoorAccuracyStreak = 0
        autoFusedWatchGpsRecoveryStreak = 0
        autoFusedFallbackToWatchGps = false
        autoFusedFallbackSinceElapsedMs = 0L
        pendingNoFixRecoveryProbeUntilElapsedMs = 0L
        if (wasEnabled) {
            telemetry.logAutoFusedFallbackCleared(reason = reason)
        }
    }

    private fun isNearKnownWatchGpsAccuracyFloor(accuracyM: Float): Boolean =
        abs(
            accuracyM - WATCH_GPS_ACCURACY_FLOOR_M,
        ) <= WATCH_GPS_ACCURACY_FLOOR_TOLERANCE_M
}

internal enum class AutoFusedNoFixRecoveryAction {
    NONE,
    START_PROBE,
    WAIT_FOR_PROBE,
    FAILOVER,
}

internal fun resolveAutoFusedNoFixRecoveryAction(
    fixGapMs: Long,
    thresholdMs: Long,
    nowElapsedMs: Long,
    probeUntilElapsedMs: Long,
): AutoFusedNoFixRecoveryAction =
    when {
        fixGapMs < thresholdMs -> AutoFusedNoFixRecoveryAction.NONE
        probeUntilElapsedMs > nowElapsedMs -> AutoFusedNoFixRecoveryAction.WAIT_FOR_PROBE
        probeUntilElapsedMs <= 0L -> AutoFusedNoFixRecoveryAction.START_PROBE
        else -> AutoFusedNoFixRecoveryAction.FAILOVER
    }

internal fun resolveAutoFusedAccuracyFailoverRequiredStreak(
    accuracyM: Float,
    fixGapMs: Long,
    expectedIntervalMs: Long,
): Int? {
    if (!accuracyM.isFinite()) return null

    val severeFixGapThresholdMs =
        resolveLocationTimingProfile(expectedIntervalMs).autoFusedSevereFailoverGapMs
    if (
        accuracyM >= AUTO_FUSED_SEVERE_FAILOVER_ACCURACY_M &&
        fixGapMs >= severeFixGapThresholdMs
    ) {
        return AUTO_FUSED_SEVERE_FAILOVER_STREAK
    }
    if (accuracyM >= AUTO_FUSED_FAILOVER_ACCURACY_M) {
        return AUTO_FUSED_FAILOVER_STREAK
    }
    return null
}

internal fun resolveAutoFusedFailoverThresholdM(requiredStreak: Int): Float =
    if (requiredStreak <= AUTO_FUSED_SEVERE_FAILOVER_STREAK) {
        AUTO_FUSED_SEVERE_FAILOVER_ACCURACY_M
    } else {
        AUTO_FUSED_FAILOVER_ACCURACY_M
    }

private const val SELF_HEAL_CHECK_INTERVAL_MS = 5_000L // was 2 s; cooldown is 15 s so 5 s is sufficient
private const val SELF_HEAL_COOLDOWN_MS = 15_000L
private const val AUTO_FUSED_FAILOVER_ACCURACY_M = 120f
private const val AUTO_FUSED_FAILOVER_STREAK = 4
private const val AUTO_FUSED_SEVERE_FAILOVER_ACCURACY_M = 100f
private const val AUTO_FUSED_SEVERE_FAILOVER_STREAK = 3
private const val AUTO_FUSED_RECOVERY_ACCURACY_M = 65f
private const val AUTO_FUSED_RECOVERY_STREAK = 4
private const val AUTO_FUSED_RECOVERY_MIN_FALLBACK_MS = 20_000L
private const val AUTO_FUSED_RECOVERY_PROBE_MIN_MULTIPLIER = 6L
private const val AUTO_FUSED_RECOVERY_PROBE_MIN_FALLBACK_MS = 30_000L
private const val AUTO_FUSED_RECOVERY_PROBE_COOLDOWN_MS = 45_000L
private const val AUTO_FUSED_RECOVERY_GRACE_MS = 15_000L
private const val AUTO_FUSED_NO_FIX_RECOVERY_PROBE_GRACE_MS = 4_000L
private const val AUTO_FUSED_NO_FIX_RECOVERY_CLEAR_ACCURACY_M = 65f
private const val AUTO_FUSED_NO_FIX_RECOVERY_SOURCE = "auto_fused_no_fix_recovery"
