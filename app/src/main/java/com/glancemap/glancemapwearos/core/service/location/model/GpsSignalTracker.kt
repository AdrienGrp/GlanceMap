package com.glancemap.glancemapwearos.core.service.location.model

import com.glancemap.glancemapwearos.core.service.location.config.WATCH_GPS_ACCURACY_FLOOR_M
import com.glancemap.glancemapwearos.core.service.location.config.WATCH_GPS_ACCURACY_FLOOR_TOLERANCE_M
import com.glancemap.glancemapwearos.core.service.location.config.WATCH_GPS_DEGRADED_ACCURACY_M
import com.glancemap.glancemapwearos.core.service.location.config.WATCH_GPS_DEGRADED_STREAK_THRESHOLD
import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode
import kotlin.math.abs

internal class GpsSignalTracker {
    var snapshot: GpsSignalSnapshot = GpsSignalSnapshot()
        private set

    private var locationUnavailableSinceMs: Long = 0L
    private var hasLoggedAvailabilityState: Boolean = false
    private var watchGpsDegradedFixStreak: Int = 0
    private var watchGpsDegradedSinceMs: Long = 0L
    private var environmentWarningSinceMs: Long = 0L

    fun onSourceModeChanged(sourceMode: LocationSourceMode?) {
        val watchGpsOnlyActive = sourceMode == LocationSourceMode.WATCH_GPS
        if (!watchGpsOnlyActive) {
            watchGpsDegradedFixStreak = 0
            watchGpsDegradedSinceMs = 0L
        }
        snapshot =
            snapshot.copy(
                watchGpsOnlyActive = watchGpsOnlyActive,
                watchGpsDegraded = watchGpsOnlyActive && watchGpsDegradedSinceMs > 0L,
                watchGpsDegradedFixStreak = if (watchGpsOnlyActive) watchGpsDegradedFixStreak else 0,
                watchGpsDegradedSinceElapsedMs = if (watchGpsOnlyActive) watchGpsDegradedSinceMs else 0L,
            )
    }

    fun onLocationAvailability(
        isAvailable: Boolean,
        nowElapsedMs: Long,
    ): Boolean {
        val previous = snapshot
        val changed = previous.isLocationAvailable != isAvailable
        if (changed) {
            if (isAvailable) {
                locationUnavailableSinceMs = 0L
                snapshot =
                    previous.copy(
                        isLocationAvailable = true,
                        unavailableSinceElapsedMs = 0L,
                    )
            } else {
                if (locationUnavailableSinceMs <= 0L) {
                    locationUnavailableSinceMs = nowElapsedMs
                }
                snapshot =
                    previous.copy(
                        isLocationAvailable = false,
                        unavailableSinceElapsedMs = locationUnavailableSinceMs,
                    )
            }
        }
        val shouldLog = !hasLoggedAvailabilityState || changed
        if (shouldLog) {
            hasLoggedAvailabilityState = true
        }
        return shouldLog
    }

    fun onEnvironmentWarning(
        warning: GpsEnvironmentWarning,
        nowElapsedMs: Long,
    ): Boolean {
        val previous = snapshot.environmentWarning
        if (previous == warning) return false
        environmentWarningSinceMs =
            if (warning == GpsEnvironmentWarning.NONE) {
                0L
            } else {
                nowElapsedMs
            }
        snapshot =
            snapshot.copy(
                environmentWarning = warning,
                environmentWarningSinceElapsedMs = environmentWarningSinceMs,
            )
        return true
    }

    fun onGpsSignalSample(
        nowElapsedMs: Long,
        ageMs: Long,
        accuracyM: Float,
        freshnessMaxAgeMs: Long,
        sourceMode: LocationSourceMode?,
    ): GpsSignalSample {
        locationUnavailableSinceMs = 0L
        val fixElapsedMs =
            if (ageMs == Long.MAX_VALUE) {
                0L
            } else {
                (nowElapsedMs - ageMs).coerceAtLeast(0L)
            }
        val fixFresh = ageMs != Long.MAX_VALUE && ageMs <= freshnessMaxAgeMs
        val watchGpsOnlyActive = sourceMode == LocationSourceMode.WATCH_GPS
        val nearKnownAccuracyFloor = isNearKnownWatchGpsAccuracyFloor(accuracyM)
        if (watchGpsOnlyActive &&
            fixFresh &&
            accuracyM.isFinite() &&
            accuracyM >= WATCH_GPS_DEGRADED_ACCURACY_M &&
            !nearKnownAccuracyFloor
        ) {
            watchGpsDegradedFixStreak += 1
            if (watchGpsDegradedFixStreak >= WATCH_GPS_DEGRADED_STREAK_THRESHOLD &&
                watchGpsDegradedSinceMs <= 0L
            ) {
                watchGpsDegradedSinceMs = nowElapsedMs
            }
        } else {
            watchGpsDegradedFixStreak = 0
            watchGpsDegradedSinceMs = 0L
        }
        val watchGpsDegraded = watchGpsOnlyActive && watchGpsDegradedSinceMs > 0L
        val environmentWarning = snapshot.environmentWarning
        val environmentWarningSinceElapsedMs = snapshot.environmentWarningSinceElapsedMs
        snapshot =
            GpsSignalSnapshot(
                lastFixElapsedRealtimeMs = fixElapsedMs,
                lastFixAgeMs = ageMs,
                lastFixAccuracyM = accuracyM,
                lastFixFresh = fixFresh,
                lastFixFreshMaxAgeMs = freshnessMaxAgeMs,
                isLocationAvailable = true,
                unavailableSinceElapsedMs = 0L,
                watchGpsOnlyActive = watchGpsOnlyActive,
                watchGpsDegraded = watchGpsDegraded,
                watchGpsDegradedFixStreak = watchGpsDegradedFixStreak,
                watchGpsDegradedSinceElapsedMs = watchGpsDegradedSinceMs,
                environmentWarning = environmentWarning,
                environmentWarningSinceElapsedMs = environmentWarningSinceElapsedMs,
            )
        return GpsSignalSample(
            ageMs = ageMs,
            fresh = fixFresh,
            maxAgeMs = freshnessMaxAgeMs,
            accuracyM = accuracyM,
            sourceMode = sourceMode,
            watchGpsDegraded = watchGpsDegraded,
            watchGpsDegradedFixStreak = watchGpsDegradedFixStreak,
            watchGpsDegradedSinceElapsedMs = watchGpsDegradedSinceMs,
        )
    }

    fun onNoPermissions(nowElapsedMs: Long) {
        locationUnavailableSinceMs = nowElapsedMs
        watchGpsDegradedFixStreak = 0
        watchGpsDegradedSinceMs = 0L
        environmentWarningSinceMs = 0L
        snapshot =
            GpsSignalSnapshot(
                isLocationAvailable = false,
                unavailableSinceElapsedMs = nowElapsedMs,
            )
    }

    fun reset() {
        locationUnavailableSinceMs = 0L
        hasLoggedAvailabilityState = false
        watchGpsDegradedFixStreak = 0
        watchGpsDegradedSinceMs = 0L
        environmentWarningSinceMs = 0L
        snapshot = GpsSignalSnapshot()
    }

    private fun isNearKnownWatchGpsAccuracyFloor(accuracyM: Float): Boolean {
        if (!accuracyM.isFinite()) return false
        return abs(accuracyM - WATCH_GPS_ACCURACY_FLOOR_M) <= WATCH_GPS_ACCURACY_FLOOR_TOLERANCE_M
    }
}

internal data class GpsSignalSample(
    val ageMs: Long,
    val fresh: Boolean,
    val maxAgeMs: Long,
    val accuracyM: Float,
    val sourceMode: LocationSourceMode?,
    val watchGpsDegraded: Boolean,
    val watchGpsDegradedFixStreak: Int,
    val watchGpsDegradedSinceElapsedMs: Long,
)
