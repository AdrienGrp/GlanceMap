package com.glancemap.glancemapwearos.presentation.features.recording.external

import com.glancemap.glancemapwearos.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ExternalSensorSimulation {
    const val HEART_RATE_ADDRESS = "02:00:00:00:00:01"
    const val RUN_POD_ADDRESS = "02:00:00:00:00:02"

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    val devices: List<ExternalSensorDevice>
        get() =
            if (BuildConfig.DEBUG && _enabled.value) {
                listOf(
                    ExternalSensorDevice(
                        name = "Simulated HR strap",
                        address = HEART_RATE_ADDRESS,
                        rssi = -42,
                        kinds = setOf(ExternalSensorKind.HEART_RATE),
                        lastSeenAtMillis = System.currentTimeMillis(),
                    ),
                    ExternalSensorDevice(
                        name = "Simulated run pod",
                        address = RUN_POD_ADDRESS,
                        rssi = -45,
                        kinds = setOf(ExternalSensorKind.RUNNING_SPEED_CADENCE),
                        lastSeenAtMillis = System.currentTimeMillis(),
                    ),
                )
            } else {
                emptyList()
            }

    fun setEnabled(enabled: Boolean) {
        _enabled.value = BuildConfig.DEBUG && enabled
    }

    fun isSimulatedAddress(address: String?): Boolean =
        BuildConfig.DEBUG &&
            _enabled.value &&
            (address == HEART_RATE_ADDRESS || address == RUN_POD_ADDRESS)
}
