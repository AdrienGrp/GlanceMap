package com.glancemap.glancemapwearos.presentation.features.recording.external

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import java.util.UUID

data class ExternalRunPodMeasurement(
    val speedMps: Float?,
    val cadenceSpm: Int?,
    val rawTotalDistanceUnits: Long?,
    val totalDistanceMeters: Double?,
    val timeMillis: Long,
)

class ExternalRunPodClient(
    private val context: Context,
    private val address: String,
    private val onMeasurement: (ExternalRunPodMeasurement) -> Unit,
) {
    private var gatt: BluetoothGatt? = null

    private val callback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        DebugTelemetry.log("ExternalRunPod", "event=connected status=$status")
                        runCatching { gatt.discoverServices() }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        DebugTelemetry.log("ExternalRunPod", "event=disconnected status=$status")
                    }
                }
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    DebugTelemetry.log("ExternalRunPod", "event=services_failed status=$status")
                    return
                }
                logGattTable(gatt.services)
                val characteristic =
                    gatt.getService(RUNNING_SPEED_CADENCE_SERVICE_UUID)
                        ?.getCharacteristic(RSC_MEASUREMENT_UUID)
                if (characteristic == null) {
                    DebugTelemetry.log("ExternalRunPod", "event=measurement_missing")
                    return
                }
                enableRunPodNotifications(gatt, characteristic)
            }

            @Deprecated("Deprecated in Android 13, still called on older devices.")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                @Suppress("DEPRECATION")
                handleMeasurement(characteristic.uuid, characteristic.value)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                handleMeasurement(characteristic.uuid, value)
            }
        }

    @SuppressLint("MissingPermission")
    fun connect() {
        if (!hasConnectPermission(context)) {
            DebugTelemetry.log("ExternalRunPod", "event=connect_skipped reason=permission")
            return
        }
        val adapter =
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
                ?.adapter
        if (adapter == null || !adapter.isEnabled) {
            DebugTelemetry.log("ExternalRunPod", "event=connect_skipped reason=bluetooth_unavailable")
            return
        }
        val device =
            runCatching { adapter.getRemoteDevice(address) }
                .getOrElse {
                    DebugTelemetry.log("ExternalRunPod", "event=connect_skipped reason=bad_address")
                    return
                }
        gatt =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, callback)
            }
        DebugTelemetry.log("ExternalRunPod", "event=connect_requested")
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        runCatching {
            gatt?.disconnect()
            gatt?.close()
        }
        gatt = null
        DebugTelemetry.log("ExternalRunPod", "event=disconnect_requested")
    }

    @SuppressLint("MissingPermission")
    private fun enableRunPodNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ) {
        val notificationEnabled = gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        if (!notificationEnabled || descriptor == null) {
            DebugTelemetry.log("ExternalRunPod", "event=notify_failed descriptor=${descriptor != null}")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
        DebugTelemetry.log("ExternalRunPod", "event=notify_requested")
    }

    private fun logGattTable(services: List<BluetoothGattService>) {
        DebugTelemetry.log("ExternalRunPod", "event=gatt_table serviceCount=${services.size}")
        services.take(MAX_GATT_SERVICES_TO_LOG).forEach { service ->
            DebugTelemetry.log(
                "ExternalRunPod",
                "event=gatt_service uuid=${service.uuid} characteristicCount=${service.characteristics.size}",
            )
            service.characteristics.take(MAX_GATT_CHARACTERISTICS_PER_SERVICE_TO_LOG).forEach { characteristic ->
                DebugTelemetry.log(
                    "ExternalRunPod",
                    "event=gatt_characteristic service=${service.uuid} uuid=${characteristic.uuid} " +
                        "properties=${characteristic.properties}",
                )
            }
        }
    }

    private fun handleMeasurement(
        characteristicUuid: UUID,
        value: ByteArray,
    ) {
        if (characteristicUuid != RSC_MEASUREMENT_UUID) return
        val measurement = decodeRscMeasurement(value) ?: return
        onMeasurement(measurement)
            DebugTelemetry.log(
                "ExternalRunPod",
                "event=sample speedMps=${measurement.speedMps?.let { formatTelemetryFloat(it) } ?: "na"} " +
                    "cadenceSpm=${measurement.cadenceSpm ?: -1} " +
                    "rawDistanceUnits=${measurement.rawTotalDistanceUnits ?: -1} " +
                    "distanceMeters=${measurement.totalDistanceMeters?.let { formatTelemetryDouble(it) } ?: "na"}",
            )
        }

    companion object {
        val RUNNING_SPEED_CADENCE_SERVICE_UUID: UUID =
            PodBluetoothUuid.service16(0x1814)
        private val RSC_MEASUREMENT_UUID: UUID =
            PodBluetoothUuid.characteristic16(0x2A53)
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            PodBluetoothUuid.descriptor16(0x2902)

        fun hasConnectPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED

        fun decodeRscMeasurement(value: ByteArray): ExternalRunPodMeasurement? {
            if (value.size < 4) return null
            val flags = value[0].toInt() and 0xFF
            var offset = 1
            val speedRaw = value.readUInt16Le(offset) ?: return null
            offset += 2
            val cadenceRaw = value[offset].toInt() and 0xFF
            offset += 1

            val strideLengthPresent = flags and 0x01 != 0
            val totalDistancePresent = flags and 0x02 != 0
            if (strideLengthPresent) {
                if (value.size < offset + 2) return null
                offset += 2
            }
            val rawTotalDistanceUnits =
                if (totalDistancePresent) {
                    value.readUInt32Le(offset)
                } else {
                    null
                }
            val totalDistanceMeters = rawTotalDistanceUnits?.toDouble()?.div(RSC_TOTAL_DISTANCE_UNITS_PER_METER)

            return ExternalRunPodMeasurement(
                speedMps = (speedRaw / 256f).takeIf { it.isFinite() && it in 0f..12f },
                cadenceSpm = cadenceRaw.takeIf { it in 1..255 },
                rawTotalDistanceUnits = rawTotalDistanceUnits?.takeIf { it >= 0L },
                totalDistanceMeters = totalDistanceMeters?.takeIf { it.isFinite() && it >= 0.0 },
                timeMillis = System.currentTimeMillis(),
            )
        }
    }
}

private fun ByteArray.readUInt16Le(offset: Int): Int? {
    if (size < offset + 2) return null
    return (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8)
}

private fun ByteArray.readUInt32Le(offset: Int): Long? {
    if (size < offset + 4) return null
    return (this[offset].toLong() and 0xFFL) or
        ((this[offset + 1].toLong() and 0xFFL) shl 8) or
        ((this[offset + 2].toLong() and 0xFFL) shl 16) or
        ((this[offset + 3].toLong() and 0xFFL) shl 24)
}

private fun formatTelemetryFloat(value: Float): String =
    String.format(java.util.Locale.US, "%.2f", value)

private fun formatTelemetryDouble(value: Double): String =
    String.format(java.util.Locale.US, "%.1f", value)

private const val RSC_TOTAL_DISTANCE_UNITS_PER_METER = 10.0
private const val MAX_GATT_SERVICES_TO_LOG = 16
private const val MAX_GATT_CHARACTERISTICS_PER_SERVICE_TO_LOG = 16

private object PodBluetoothUuid {
    fun service16(shortUuid: Int): UUID = bluetoothUuid(shortUuid)

    fun characteristic16(shortUuid: Int): UUID = bluetoothUuid(shortUuid)

    fun descriptor16(shortUuid: Int): UUID = bluetoothUuid(shortUuid)

    private fun bluetoothUuid(shortUuid: Int): UUID =
        UUID.fromString("0000${shortUuid.toString(16).padStart(4, '0')}-0000-1000-8000-00805f9b34fb")
}
