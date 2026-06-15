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

class ExternalHeartRateClient(
    private val context: Context,
    private val address: String,
    private val onHeartRate: (bpm: Int, timeMillis: Long) -> Unit,
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
                        DebugTelemetry.log("ExternalHeartRate", "event=connected status=$status")
                        runCatching { gatt.discoverServices() }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        DebugTelemetry.log("ExternalHeartRate", "event=disconnected status=$status")
                    }
                }
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    DebugTelemetry.log("ExternalHeartRate", "event=services_failed status=$status")
                    return
                }
                val characteristic =
                    gatt.getService(HEART_RATE_SERVICE_UUID)
                        ?.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
                if (characteristic == null) {
                    DebugTelemetry.log("ExternalHeartRate", "event=measurement_missing")
                    return
                }
                enableHeartRateNotifications(gatt, characteristic)
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
            DebugTelemetry.log("ExternalHeartRate", "event=connect_skipped reason=permission")
            return
        }
        val adapter =
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
                ?.adapter
        if (adapter == null || !adapter.isEnabled) {
            DebugTelemetry.log("ExternalHeartRate", "event=connect_skipped reason=bluetooth_unavailable")
            return
        }
        val device =
            runCatching { adapter.getRemoteDevice(address) }
                .getOrElse {
                    DebugTelemetry.log("ExternalHeartRate", "event=connect_skipped reason=bad_address")
                    return
                }
        gatt =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, callback)
            }
        DebugTelemetry.log("ExternalHeartRate", "event=connect_requested")
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        runCatching {
            gatt?.disconnect()
            gatt?.close()
        }
        gatt = null
        DebugTelemetry.log("ExternalHeartRate", "event=disconnect_requested")
    }

    @SuppressLint("MissingPermission")
    private fun enableHeartRateNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ) {
        val notificationEnabled = gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        if (!notificationEnabled || descriptor == null) {
            DebugTelemetry.log("ExternalHeartRate", "event=notify_failed descriptor=${descriptor != null}")
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
        DebugTelemetry.log("ExternalHeartRate", "event=notify_requested")
    }

    private fun handleMeasurement(
        characteristicUuid: UUID,
        value: ByteArray,
    ) {
        if (characteristicUuid != HEART_RATE_MEASUREMENT_UUID) return
        val bpm = decodeHeartRateMeasurement(value) ?: return
        onHeartRate(bpm, System.currentTimeMillis())
        DebugTelemetry.log("ExternalHeartRate", "event=sample bpm=$bpm")
    }

    companion object {
        val HEART_RATE_SERVICE_UUID: UUID =
            BluetoothUuid.service16(0x180D)
        private val HEART_RATE_MEASUREMENT_UUID: UUID =
            BluetoothUuid.characteristic16(0x2A37)
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            BluetoothUuid.descriptor16(0x2902)

        fun hasConnectPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED

        fun decodeHeartRateMeasurement(value: ByteArray): Int? {
            if (value.size < 2) return null
            val flags = value[0].toInt() and 0xFF
            return if ((flags and 0x01) == 0) {
                value[1].toInt() and 0xFF
            } else {
                if (value.size < 3) return null
                (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
            }.takeIf { it in 20..240 }
        }
    }
}

private object BluetoothUuid {
    fun service16(shortUuid: Int): UUID = bluetoothUuid(shortUuid)

    fun characteristic16(shortUuid: Int): UUID = bluetoothUuid(shortUuid)

    fun descriptor16(shortUuid: Int): UUID = bluetoothUuid(shortUuid)

    private fun bluetoothUuid(shortUuid: Int): UUID =
        UUID.fromString("0000${shortUuid.toString(16).padStart(4, '0')}-0000-1000-8000-00805f9b34fb")
}
