package com.glancemap.glancemapwearos.core.service.location.policy

internal object LocationSourceGuard {
    fun acceptsCallbackOrigin(expectedSourceMode: LocationSourceMode, callbackOrigin: LocationSourceMode): Boolean {
        return expectedSourceMode == callbackOrigin
    }

    fun expectedOrigin(sourceMode: LocationSourceMode): String {
        return sourceMode.telemetryValue
    }
}
