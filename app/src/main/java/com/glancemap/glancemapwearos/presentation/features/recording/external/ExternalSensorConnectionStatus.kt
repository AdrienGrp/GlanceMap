package com.glancemap.glancemapwearos.presentation.features.recording.external

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ExternalSensorConnectionStatus {
    private val _connectedAddresses = MutableStateFlow<Set<String>>(emptySet())
    val connectedAddresses: StateFlow<Set<String>> = _connectedAddresses.asStateFlow()
    private val _batteryLevels = MutableStateFlow<Map<String, Int>>(emptyMap())
    val batteryLevels: StateFlow<Map<String, Int>> = _batteryLevels.asStateFlow()

    fun update(
        address: String,
        connected: Boolean,
    ) {
        if (address.isBlank()) return
        _connectedAddresses.value =
            if (connected) {
                _connectedAddresses.value + address
            } else {
                _connectedAddresses.value - address
            }
    }

    fun updateBattery(
        address: String,
        batteryLevelPercent: Int?,
    ) {
        val level = batteryLevelPercent?.takeIf { it in 0..100 } ?: return
        if (address.isBlank()) return
        _batteryLevels.value = _batteryLevels.value + (address to level)
    }
}
