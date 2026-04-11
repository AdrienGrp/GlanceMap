package com.glancemap.glancemapwearos.core.service.location.config

internal data class GpsSettingsState(
    val watchOnly: Boolean,
    val intervalMs: Long,
    val ambientIntervalMs: Long,
    val ambientGps: Boolean,
    val debugTelemetry: Boolean
)
