package com.glancemap.glancemapwearos.presentation.features.recording.external

import java.util.UUID

enum class ExternalSensorKind(
    val label: String,
    val serviceUuid: UUID,
) {
    HEART_RATE(
        label = "Heart rate",
        serviceUuid = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"),
    ),
    RUNNING_SPEED_CADENCE(
        label = "Run pod",
        serviceUuid = UUID.fromString("00001814-0000-1000-8000-00805f9b34fb"),
    ),
    CYCLING_SPEED_CADENCE(
        label = "Cycling speed/cadence",
        serviceUuid = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb"),
    ),
    CYCLING_POWER(
        label = "Cycling power",
        serviceUuid = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb"),
    ),
}

data class ExternalSensorDevice(
    val name: String,
    val address: String,
    val rssi: Int?,
    val kinds: Set<ExternalSensorKind>,
    val lastSeenAtMillis: Long,
) {
    val supportedLabel: String =
        kinds
            .joinToString(separator = ", ") { it.label }
            .ifBlank { "Unknown BLE device" }
}

enum class ExternalSensorScanStatus {
    IDLE,
    SCANNING,
    BLUETOOTH_UNAVAILABLE,
    BLUETOOTH_OFF,
    PERMISSION_MISSING,
    SCAN_FAILED,
}
