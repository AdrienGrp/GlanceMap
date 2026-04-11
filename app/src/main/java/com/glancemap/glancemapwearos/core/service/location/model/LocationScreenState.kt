package com.glancemap.glancemapwearos.core.service.location.model

enum class LocationScreenState {
    INTERACTIVE,
    AMBIENT,
    SCREEN_OFF,
}

internal val LocationScreenState.isInteractive: Boolean
    get() = this == LocationScreenState.INTERACTIVE

internal val LocationScreenState.isNonInteractive: Boolean
    get() = !isInteractive

internal fun resolveLocationScreenState(
    isAmbient: Boolean,
    isDeviceInteractive: Boolean,
): LocationScreenState =
    when {
        isAmbient -> LocationScreenState.AMBIENT
        !isDeviceInteractive -> LocationScreenState.SCREEN_OFF
        else -> LocationScreenState.INTERACTIVE
    }
