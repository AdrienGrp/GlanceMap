package com.glancemap.glancemapwearos.core.service.location.policy

import com.google.android.gms.location.Priority

internal enum class LocationRuntimeMode {
    BURST,
    INTERACTIVE,
    PASSIVE,
}

internal enum class LocationSourceMode(
    val telemetryValue: String,
) {
    AUTO_FUSED("auto_fused"),
    WATCH_GPS("watch_gps"),
}

internal data class LocationUpdateConfig(
    val priority: Int,
    val intervalMs: Long,
    val minDistanceMeters: Float,
    val mode: LocationRuntimeMode,
    val sourceMode: LocationSourceMode,
)

internal object LocationUpdatePolicy {
    fun resolveServiceConfig(
        isInHighAccuracyBurst: Boolean,
        interactive: Boolean,
        passiveTracking: Boolean,
        watchOnly: Boolean,
        hasFinePermission: Boolean,
        userIntervalMs: Long,
        ambientUserIntervalMs: Long,
        minUserIntervalMs: Long,
        maxUserIntervalMs: Long,
        minAmbientIntervalMs: Long,
        highAccuracyBurstIntervalMs: Long,
        foregroundMinDistanceM: Float,
        backgroundMinDistanceM: Float,
    ): LocationUpdateConfig? {
        if (!isInHighAccuracyBurst && !interactive && !passiveTracking) {
            return null
        }
        val safeUserIntervalMs = userIntervalMs.coerceIn(minUserIntervalMs, maxUserIntervalMs)
        val safeAmbientIntervalMs = ambientUserIntervalMs.coerceIn(minAmbientIntervalMs, maxUserIntervalMs)
        val sourceMode =
            if (watchOnly) {
                LocationSourceMode.WATCH_GPS
            } else {
                LocationSourceMode.AUTO_FUSED
            }
        if (isInHighAccuracyBurst) {
            return LocationUpdateConfig(
                priority = Priority.PRIORITY_HIGH_ACCURACY,
                intervalMs = highAccuracyBurstIntervalMs,
                minDistanceMeters = foregroundMinDistanceM,
                mode = LocationRuntimeMode.BURST,
                sourceMode = sourceMode,
            )
        }

        if (interactive) {
            val interactivePriority =
                if (hasFinePermission) {
                    Priority.PRIORITY_HIGH_ACCURACY
                } else {
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY
                }
            return LocationUpdateConfig(
                priority = interactivePriority,
                intervalMs = safeUserIntervalMs,
                minDistanceMeters = foregroundMinDistanceM,
                mode = LocationRuntimeMode.INTERACTIVE,
                sourceMode = sourceMode,
            )
        }

        return LocationUpdateConfig(
            priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            intervalMs = safeAmbientIntervalMs,
            minDistanceMeters = backgroundMinDistanceM,
            mode = LocationRuntimeMode.PASSIVE,
            sourceMode = sourceMode,
        )
    }
}
