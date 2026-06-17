package com.glancemap.glancemapwearos.presentation.features.recording.external

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ExternalRunPodRuntimeInfo(
    val address: String,
    val connected: Boolean = false,
    val batteryLevelPercent: Int? = null,
    val batteryUpdatedAtMillis: Long = 0L,
    val powerWatts: Int? = null,
    val powerUpdatedAtMillis: Long = 0L,
    val deviceInfo: Map<String, String> = emptyMap(),
)

object ExternalRunPodRuntimeStatus {
    private val _infos = MutableStateFlow<Map<String, ExternalRunPodRuntimeInfo>>(emptyMap())
    val infos: StateFlow<Map<String, ExternalRunPodRuntimeInfo>> = _infos.asStateFlow()

    fun markDisconnected(address: String) {
        update(address) { it.copy(connected = false) }
    }

    fun updateBattery(
        address: String,
        batteryLevelPercent: Int?,
        timeMillis: Long,
    ) {
        update(address) {
            it.copy(
                connected = true,
                batteryLevelPercent = batteryLevelPercent ?: it.batteryLevelPercent,
                batteryUpdatedAtMillis =
                    if (batteryLevelPercent != null) {
                        timeMillis
                    } else {
                        it.batteryUpdatedAtMillis
                    },
            )
        }
    }

    fun updatePower(
        address: String,
        powerWatts: Int?,
        timeMillis: Long,
    ) {
        update(address) {
            it.copy(
                connected = true,
                powerWatts = powerWatts ?: it.powerWatts,
                powerUpdatedAtMillis =
                    if (powerWatts != null) {
                        timeMillis
                    } else {
                        it.powerUpdatedAtMillis
                    },
            )
        }
    }

    fun updateDeviceInfo(
        address: String,
        field: String,
        value: String,
    ) {
        if (field.isBlank() || value.isBlank() || value == "na") return
        update(address) {
            it.copy(
                connected = true,
                deviceInfo = it.deviceInfo + (field to value),
            )
        }
    }

    private fun update(
        address: String,
        transform: (ExternalRunPodRuntimeInfo) -> ExternalRunPodRuntimeInfo,
    ) {
        if (address.isBlank()) return
        val current = _infos.value[address] ?: ExternalRunPodRuntimeInfo(address = address)
        _infos.value = _infos.value + (address to transform(current))
    }
}
