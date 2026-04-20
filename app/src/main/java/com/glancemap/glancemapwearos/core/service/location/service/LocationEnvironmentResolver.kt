package com.glancemap.glancemapwearos.core.service.location.service

import com.glancemap.glancemapwearos.core.service.location.adapters.LocationSettingsPreflightResult
import com.glancemap.glancemapwearos.core.service.location.adapters.WatchGpsAvailabilityReason
import com.glancemap.glancemapwearos.core.service.location.model.GpsEnvironmentWarning
import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode

internal enum class LocationEnvironmentAction {
    CONTINUE,
    RESTART_REQUEST,
}

internal data class LocationEnvironmentDecision(
    val warning: GpsEnvironmentWarning,
    val action: LocationEnvironmentAction,
)

internal fun resolveLocationEnvironmentDecision(
    sourceMode: LocationSourceMode,
    watchOnlyRequested: Boolean,
    watchGpsAvailability: WatchGpsAvailabilityReason?,
    phoneConnected: Boolean?,
    locationSettings: LocationSettingsPreflightResult?,
): LocationEnvironmentDecision {
    if (locationSettings?.satisfied == false) {
        return LocationEnvironmentDecision(
            warning = GpsEnvironmentWarning.LOCATION_SETTINGS_UNSATISFIED,
            action = LocationEnvironmentAction.CONTINUE,
        )
    }

    val watchGpsAvailable = watchGpsAvailability == WatchGpsAvailabilityReason.AVAILABLE
    val watchGpsUnavailable = watchGpsAvailability != null && !watchGpsAvailable
    if (sourceMode == LocationSourceMode.WATCH_GPS) {
        return LocationEnvironmentDecision(
            warning =
                when {
                    watchGpsUnavailable -> GpsEnvironmentWarning.WATCH_GPS_UNAVAILABLE
                    !watchOnlyRequested && phoneConnected == false ->
                        GpsEnvironmentWarning.AUTO_PHONE_DISCONNECTED_USING_WATCH_GPS
                    else -> GpsEnvironmentWarning.NONE
                },
            action = LocationEnvironmentAction.CONTINUE,
        )
    }

    if (sourceMode == LocationSourceMode.AUTO_FUSED && phoneConnected == false) {
        return if (watchGpsAvailable) {
            LocationEnvironmentDecision(
                warning = GpsEnvironmentWarning.AUTO_PHONE_DISCONNECTED_USING_WATCH_GPS,
                action = LocationEnvironmentAction.RESTART_REQUEST,
            )
        } else {
            LocationEnvironmentDecision(
                warning = GpsEnvironmentWarning.AUTO_PHONE_DISCONNECTED_NO_WATCH_GPS,
                action = LocationEnvironmentAction.CONTINUE,
            )
        }
    }

    return LocationEnvironmentDecision(
        warning = GpsEnvironmentWarning.NONE,
        action = LocationEnvironmentAction.CONTINUE,
    )
}
