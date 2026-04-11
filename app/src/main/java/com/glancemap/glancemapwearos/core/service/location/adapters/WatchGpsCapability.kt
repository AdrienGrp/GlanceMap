package com.glancemap.glancemapwearos.core.service.location.adapters

internal enum class WatchGpsAvailabilityReason {
    HARDWARE_MISSING,
    PROVIDER_MISSING,
    PROVIDER_DISABLED,
    AVAILABLE
}

internal class WatchGpsUnavailableException(
    val reason: WatchGpsAvailabilityReason
) : IllegalStateException(
    when (reason) {
        WatchGpsAvailabilityReason.HARDWARE_MISSING -> "watch_gps_hardware_missing"
        WatchGpsAvailabilityReason.PROVIDER_MISSING -> "watch_gps_provider_missing"
        WatchGpsAvailabilityReason.PROVIDER_DISABLED -> "watch_gps_provider_disabled"
        WatchGpsAvailabilityReason.AVAILABLE -> "watch_gps_available"
    }
)

internal fun resolveWatchGpsAvailabilityReason(
    hasGpsHardwareFeature: Boolean,
    isGpsProviderPresent: Boolean,
    isGpsProviderEnabled: Boolean
): WatchGpsAvailabilityReason {
    if (!hasGpsHardwareFeature) return WatchGpsAvailabilityReason.HARDWARE_MISSING
    if (!isGpsProviderPresent) return WatchGpsAvailabilityReason.PROVIDER_MISSING
    if (!isGpsProviderEnabled) return WatchGpsAvailabilityReason.PROVIDER_DISABLED
    return WatchGpsAvailabilityReason.AVAILABLE
}
