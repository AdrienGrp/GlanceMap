package com.glancemap.glancemapwearos.core.service.location.model

import android.location.Location
import android.os.Bundle
import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode

private const val LOCATION_SOURCE_MODE_EXTRA =
    "com.glancemap.glancemapwearos.extra.LOCATION_SOURCE_MODE"

internal fun Location.attachDeliveredSourceMode(sourceMode: LocationSourceMode?): Location {
    if (sourceMode == null) return this
    extras =
        Bundle(extras ?: Bundle()).apply {
            putString(LOCATION_SOURCE_MODE_EXTRA, sourceMode.name)
        }
    return this
}

internal fun Location.deliveredSourceModeOrNull(): LocationSourceMode? =
    extras
        ?.getString(LOCATION_SOURCE_MODE_EXTRA)
        .let(::parseDeliveredSourceMode)

internal fun parseDeliveredSourceMode(value: String?): LocationSourceMode? =
    value?.let { sourceName ->
        LocationSourceMode.entries.firstOrNull { it.name == sourceName }
    }
