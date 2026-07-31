package com.glancemap.glancemapwearos.core.service.location.model

enum class GpsEnvironmentWarning {
    NONE,
    LOCATION_SETTINGS_UNSATISFIED,
    WATCH_GPS_UNAVAILABLE,
    AUTO_PHONE_DISCONNECTED_NO_WATCH_GPS,
    AUTO_PHONE_DISCONNECTED_USING_WATCH_GPS,
}
