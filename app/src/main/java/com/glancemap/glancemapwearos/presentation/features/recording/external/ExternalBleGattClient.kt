package com.glancemap.glancemapwearos.presentation.features.recording.external

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import java.util.UUID

internal class ExternalBleGattClient(
    private val context: Context,
    private val address: String,
    private val logTag: String,
    private val serviceUuid: UUID,
    private val measurementUuid: UUID,
    private val extraNotifyCharacteristics: List<BleCharacteristicRef> = emptyList(),
    private val readCharacteristics: List<BleCharacteristicRef> = emptyList(),
    private val onServicesReady: (BluetoothGatt) -> Unit = {},
    private val onCharacteristicRead: (UUID, ByteArray) -> Unit = { _, _ -> },
    private val onConnecting: () -> Unit = {},
    private val onConnectionChanged: (Boolean) -> Unit = {},
    private val onMeasurement: (UUID, ByteArray) -> Unit,
) {
    private var gatt: BluetoothGatt? = null
    private val pendingOperations = ArrayDeque<BleGattOperation>()

    private val callback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        DebugTelemetry.log(logTag, "event=connected status=$status")
                        onConnectionChanged(true)
                        discoverServicesIfPermitted(gatt)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        DebugTelemetry.log(logTag, "event=disconnected status=$status")
                        onConnectionChanged(false)
                    }
                }
            }

            @SuppressLint("MissingPermission")
            private fun discoverServicesIfPermitted(gatt: BluetoothGatt) {
                if (hasConnectPermission(context)) {
                    runCatching { gatt.discoverServices() }
                } else {
                    DebugTelemetry.log(logTag, "event=services_skipped reason=permission")
                }
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    DebugTelemetry.log(logTag, "event=services_failed status=$status")
                    return
                }
                onServicesReady(gatt)
                val primaryCharacteristic =
                    gatt
                        .getService(serviceUuid)
                        ?.getCharacteristic(measurementUuid)
                if (primaryCharacteristic == null) {
                    DebugTelemetry.log(logTag, "event=measurement_missing")
                } else {
                    pendingOperations += BleGattOperation.Notify(serviceUuid, measurementUuid)
                }
                extraNotifyCharacteristics.forEach { ref ->
                    pendingOperations += BleGattOperation.Notify(ref.serviceUuid, ref.characteristicUuid)
                }
                readCharacteristics.forEach { ref ->
                    pendingOperations += BleGattOperation.Read(ref.serviceUuid, ref.characteristicUuid)
                }
                processNextOperation(gatt)
            }

            @Deprecated("Deprecated in Android 13, still called on older devices.")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                @Suppress("DEPRECATION")
                onMeasurement(characteristic.uuid, characteristic.value)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                onMeasurement(characteristic.uuid, value)
            }

            @Deprecated("Deprecated in Android 13, still called on older devices.")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    @Suppress("DEPRECATION")
                    onCharacteristicRead(characteristic.uuid, characteristic.value)
                } else {
                    DebugTelemetry.log(logTag, "event=read_failed uuid=${characteristic.uuid} status=$status")
                }
                processNextOperation(gatt)
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    onCharacteristicRead(characteristic.uuid, value)
                } else {
                    DebugTelemetry.log(logTag, "event=read_failed uuid=${characteristic.uuid} status=$status")
                }
                processNextOperation(gatt)
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    DebugTelemetry.log(logTag, "event=notify_descriptor_failed status=$status")
                }
                processNextOperation(gatt)
            }
        }

    @SuppressLint("MissingPermission")
    fun connect() {
        onConnectionChanged(false)
        if (!hasConnectPermission(context)) {
            DebugTelemetry.log(logTag, "event=connect_skipped reason=permission")
            return
        }
        val adapter =
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
                ?.adapter
        if (adapter == null || !adapter.isEnabled) {
            DebugTelemetry.log(logTag, "event=connect_skipped reason=bluetooth_unavailable")
            return
        }
        val device =
            runCatching { adapter.getRemoteDevice(address) }
                .getOrElse {
                    DebugTelemetry.log(logTag, "event=connect_skipped reason=bad_address")
                    return
                }
        onConnecting()
        gatt =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, callback)
            }
        DebugTelemetry.log(logTag, "event=connect_requested")
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        runCatching {
            gatt?.disconnect()
            gatt?.close()
        }
        gatt = null
        onConnectionChanged(false)
        DebugTelemetry.log(logTag, "event=disconnect_requested")
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ): Boolean {
        val notificationEnabled = gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(BluetoothUuid.descriptor16(0x2902))
        if (!notificationEnabled || descriptor == null) {
            DebugTelemetry.log(logTag, "event=notify_failed uuid=${characteristic.uuid} descriptor=${descriptor != null}")
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) != BluetoothStatusCodes.SUCCESS) {
                DebugTelemetry.log(logTag, "event=notify_failed uuid=${characteristic.uuid} write=false")
                return false
            }
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            if (!gatt.writeDescriptor(descriptor)) {
                DebugTelemetry.log(logTag, "event=notify_failed uuid=${characteristic.uuid} write=false")
                return false
            }
        }
        DebugTelemetry.log(logTag, "event=notify_requested uuid=${characteristic.uuid}")
        return true
    }

    @SuppressLint("MissingPermission")
    private fun processNextOperation(gatt: BluetoothGatt) {
        while (pendingOperations.isNotEmpty()) {
            when (val operation = pendingOperations.removeFirst()) {
                is BleGattOperation.Notify -> {
                    val characteristic =
                        gatt
                            .getService(operation.serviceUuid)
                            ?.getCharacteristic(operation.characteristicUuid)
                    if (characteristic == null) {
                        DebugTelemetry.log(logTag, "event=notify_missing uuid=${operation.characteristicUuid}")
                        continue
                    }
                    if (enableNotifications(gatt, characteristic)) {
                        return
                    }
                }
                is BleGattOperation.Read -> {
                    val characteristic =
                        gatt
                            .getService(operation.serviceUuid)
                            ?.getCharacteristic(operation.characteristicUuid)
                    if (characteristic == null) {
                        DebugTelemetry.log(logTag, "event=read_missing uuid=${operation.characteristicUuid}")
                        continue
                    }
                    if (gatt.readCharacteristic(characteristic)) {
                        DebugTelemetry.log(logTag, "event=read_requested uuid=${characteristic.uuid}")
                        return
                    }
                    DebugTelemetry.log(logTag, "event=read_failed uuid=${characteristic.uuid} request=false")
                }
            }
        }
    }
}

internal data class BleCharacteristicRef(
    val serviceUuid: UUID,
    val characteristicUuid: UUID,
)

private sealed interface BleGattOperation {
    val serviceUuid: UUID
    val characteristicUuid: UUID

    data class Notify(
        override val serviceUuid: UUID,
        override val characteristicUuid: UUID,
    ) : BleGattOperation

    data class Read(
        override val serviceUuid: UUID,
        override val characteristicUuid: UUID,
    ) : BleGattOperation
}

internal fun hasConnectPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

internal object BluetoothUuid {
    fun service16(shortUuid: Int): UUID = bluetoothUuid(shortUuid)

    fun characteristic16(shortUuid: Int): UUID = bluetoothUuid(shortUuid)

    fun descriptor16(shortUuid: Int): UUID = bluetoothUuid(shortUuid)

    private fun bluetoothUuid(shortUuid: Int): UUID = UUID.fromString("0000${shortUuid.toString(16).padStart(4, '0')}-0000-1000-8000-00805f9b34fb")
}
