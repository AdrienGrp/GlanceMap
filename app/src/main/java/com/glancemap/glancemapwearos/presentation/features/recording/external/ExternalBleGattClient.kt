@file:Suppress("LongParameterList")

package com.glancemap.glancemapwearos.presentation.features.recording.external

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattConnectionSettings
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import java.util.UUID

internal data class BleDisconnectEvent(
    val status: Int,
    val reason: String,
    val retryable: Boolean,
)

@Suppress("TooManyFunctions")
internal class ExternalBleGattClient(
    private val context: Context,
    private val address: String,
    private val logTag: String,
    private val serviceUuid: UUID,
    private val measurementUuid: UUID,
    private val extraNotifyCharacteristics: List<BleCharacteristicRef> = emptyList(),
    private val readyMeasurementUuids: Set<UUID> = setOf(measurementUuid),
    private val readCharacteristics: List<BleCharacteristicRef> = emptyList(),
    private val onServicesReady: (BluetoothGatt) -> Unit = {},
    private val onCharacteristicRead: (UUID, ByteArray) -> Unit = { _, _ -> },
    private val onConnecting: () -> Unit = {},
    private val onConnectionChanged: (Boolean) -> Unit = {},
    private val onDisconnected: (BleDisconnectEvent) -> Unit = {},
    private val onMeasurement: (UUID, ByteArray) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private val pendingOperations = ArrayDeque<BleGattOperation>()
    private var activeOperation: BleGattOperation? = null
    private var readySubscriptionCount = 0
    private var state = BleGattState.IDLE
    private val setupTimeoutRunnable =
        Runnable {
            if (state != BleGattState.READY && state != BleGattState.IDLE) {
                failConnection(
                    event = BleDisconnectEvent(GATT_STATUS_UNAVAILABLE, "setup_timeout", retryable = true),
                    telemetryEvent = "setup_timeout",
                )
            }
        }

    private val callback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                runOnMain { handleConnectionStateChange(gatt, status, newState) }
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                runOnMain { handleServicesDiscovered(gatt, status) }
            }

            @Deprecated("Deprecated in Android 13, still called on older devices.")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                @Suppress("DEPRECATION")
                val value = characteristic.value?.copyOf() ?: return
                runOnMain { deliverMeasurement(gatt, characteristic.uuid, value) }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                val copiedValue = value.copyOf()
                runOnMain { deliverMeasurement(gatt, characteristic.uuid, copiedValue) }
            }

            @Deprecated("Deprecated in Android 13, still called on older devices.")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                @Suppress("DEPRECATION")
                val value = characteristic.value?.copyOf() ?: byteArrayOf()
                runOnMain { handleCharacteristicRead(gatt, characteristic.uuid, value, status) }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                val copiedValue = value.copyOf()
                runOnMain { handleCharacteristicRead(gatt, characteristic.uuid, copiedValue, status) }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                runOnMain { handleDescriptorWrite(gatt, descriptor, status) }
            }
        }

    fun connect() {
        runOnMain(::connectOnMain)
    }

    fun disconnect() {
        runOnMain {
            closeCurrentGatt(requestDisconnect = true)
            onConnectionChanged(false)
            DebugTelemetry.log(logTag, "event=disconnect_requested")
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("ReturnCount")
    private fun connectOnMain() {
        closeCurrentGatt(requestDisconnect = true)
        onConnectionChanged(false)
        pendingOperations.clear()
        activeOperation = null
        readySubscriptionCount = 0
        if (!hasConnectPermission(context)) {
            rejectConnection("permission", retryable = false)
            return
        }
        val adapter =
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
                ?.adapter
        if (adapter == null || !adapter.isEnabled) {
            rejectConnection("bluetooth_unavailable", retryable = false)
            return
        }
        val device =
            runCatching { adapter.getRemoteDevice(address) }
                .getOrElse {
                    rejectConnection("bad_address", retryable = false)
                    return
                }
        state = BleGattState.CONNECTING
        onConnecting()
        val requestedGatt =
            if (Build.VERSION.SDK_INT >= ANDROID_API_LEVEL_37) {
                device.connectGattApi37(context, callback)
            } else {
                device.connectGattLegacy(context, callback)
            }
        if (requestedGatt == null) {
            rejectConnection("connect_gatt_null", retryable = true)
            return
        }
        gatt = requestedGatt
        mainHandler.removeCallbacks(setupTimeoutRunnable)
        mainHandler.postDelayed(setupTimeoutRunnable, SETUP_TIMEOUT_MS)
        DebugTelemetry.log(logTag, "event=connect_requested")
    }

    @SuppressLint("MissingPermission")
    private fun handleConnectionStateChange(
        callbackGatt: BluetoothGatt,
        status: Int,
        newState: Int,
    ) {
        if (gatt !== callbackGatt) return
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    failConnection(
                        BleDisconnectEvent(status, "transport_connect_failed", retryable = true),
                        telemetryEvent = "connect_failed",
                    )
                    return
                }
                state = BleGattState.DISCOVERING
                DebugTelemetry.log(logTag, "event=transport_connected status=$status")
                if (!hasConnectPermission(context)) {
                    failConnection(
                        BleDisconnectEvent(status, "permission", retryable = false),
                        telemetryEvent = "services_skipped",
                    )
                } else if (!runCatching { callbackGatt.discoverServices() }.getOrDefault(false)) {
                    failConnection(
                        BleDisconnectEvent(status, "service_discovery_request_failed", retryable = true),
                        telemetryEvent = "services_failed",
                    )
                }
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                val event = BleDisconnectEvent(status, "transport_disconnected", retryable = true)
                DebugTelemetry.log(logTag, "event=disconnected status=$status reason=${event.reason}")
                closeCurrentGatt(requestDisconnect = false)
                onConnectionChanged(false)
                onDisconnected(event)
            }
        }
    }

    private fun handleServicesDiscovered(
        callbackGatt: BluetoothGatt,
        status: Int,
    ) {
        if (gatt !== callbackGatt || state != BleGattState.DISCOVERING) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            failConnection(
                BleDisconnectEvent(status, "service_discovery_failed", retryable = true),
                telemetryEvent = "services_failed",
            )
            return
        }
        onServicesReady(callbackGatt)
        pendingOperations.clear()
        activeOperation = null
        readySubscriptionCount = 0
        pendingOperations +=
            BleGattOperation.Notify(
                serviceUuid = serviceUuid,
                characteristicUuid = measurementUuid,
                contributesToReady = measurementUuid in readyMeasurementUuids,
            )
        extraNotifyCharacteristics.forEach { ref ->
            pendingOperations +=
                BleGattOperation.Notify(
                    serviceUuid = ref.serviceUuid,
                    characteristicUuid = ref.characteristicUuid,
                    contributesToReady = ref.characteristicUuid in readyMeasurementUuids,
                )
        }
        readCharacteristics.forEach { ref ->
            pendingOperations += BleGattOperation.Read(ref.serviceUuid, ref.characteristicUuid)
        }
        state = BleGattState.SUBSCRIBING
        processNextOperation(callbackGatt)
    }

    private fun deliverMeasurement(
        callbackGatt: BluetoothGatt,
        characteristicUuid: UUID,
        value: ByteArray,
    ) {
        if (gatt !== callbackGatt || state != BleGattState.READY) return
        onMeasurement(characteristicUuid, value)
    }

    private fun handleCharacteristicRead(
        callbackGatt: BluetoothGatt,
        characteristicUuid: UUID,
        value: ByteArray,
        status: Int,
    ) {
        if (gatt !== callbackGatt || activeOperation !is BleGattOperation.Read) return
        activeOperation = null
        if (status == BluetoothGatt.GATT_SUCCESS) {
            onCharacteristicRead(characteristicUuid, value)
        } else {
            DebugTelemetry.log(logTag, "event=read_failed uuid=$characteristicUuid status=$status")
        }
        processNextOperation(callbackGatt)
    }

    private fun handleDescriptorWrite(
        callbackGatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int,
    ) {
        if (gatt !== callbackGatt) return
        val operation = activeOperation as? BleGattOperation.Notify ?: return
        activeOperation = null
        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (operation.contributesToReady) readySubscriptionCount += 1
            DebugTelemetry.log(logTag, "event=notify_enabled uuid=${operation.characteristicUuid}")
        } else {
            DebugTelemetry.log(
                logTag,
                "event=notify_descriptor_failed uuid=${operation.characteristicUuid} " +
                    "descriptor=${descriptor.uuid} status=$status",
            )
        }
        processNextOperation(callbackGatt)
    }

    @SuppressLint("MissingPermission")
    @Suppress("ReturnCount")
    private fun enableNotifications(
        callbackGatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ): Boolean {
        val descriptorValue = notificationDescriptorValue(characteristic.properties)
        val descriptor = characteristic.getDescriptor(BluetoothUuid.descriptor16(0x2902))
        if (descriptor == null || descriptorValue == null) {
            DebugTelemetry.log(
                logTag,
                "event=notify_failed uuid=${characteristic.uuid} descriptor=${descriptor != null} " +
                    "supported=${descriptorValue != null}",
            )
            return false
        }
        val notificationEnabled =
            runCatching { callbackGatt.setCharacteristicNotification(characteristic, true) }.getOrDefault(false)
        if (!notificationEnabled) {
            DebugTelemetry.log(logTag, "event=notify_failed uuid=${characteristic.uuid} enabled=false")
            return false
        }
        val requested =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                callbackGatt.writeDescriptor(descriptor, descriptorValue) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = descriptorValue
                @Suppress("DEPRECATION")
                callbackGatt.writeDescriptor(descriptor)
            }
        if (!requested) {
            DebugTelemetry.log(logTag, "event=notify_failed uuid=${characteristic.uuid} write=false")
            return false
        }
        DebugTelemetry.log(logTag, "event=notify_requested uuid=${characteristic.uuid}")
        return true
    }

    @SuppressLint("MissingPermission")
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun processNextOperation(callbackGatt: BluetoothGatt) {
        if (gatt !== callbackGatt) return
        while (pendingOperations.isNotEmpty()) {
            val operation = pendingOperations.removeFirst()
            if (operation is BleGattOperation.Read && state == BleGattState.SUBSCRIBING) {
                if (!markReady()) return
            }
            when (operation) {
                is BleGattOperation.Notify -> {
                    val characteristic =
                        callbackGatt
                            .getService(operation.serviceUuid)
                            ?.getCharacteristic(operation.characteristicUuid)
                    if (characteristic == null) {
                        DebugTelemetry.log(logTag, "event=notify_missing uuid=${operation.characteristicUuid}")
                        continue
                    }
                    activeOperation = operation
                    if (enableNotifications(callbackGatt, characteristic)) return
                    activeOperation = null
                }
                is BleGattOperation.Read -> {
                    val characteristic =
                        callbackGatt
                            .getService(operation.serviceUuid)
                            ?.getCharacteristic(operation.characteristicUuid)
                    if (characteristic == null) {
                        DebugTelemetry.log(logTag, "event=read_missing uuid=${operation.characteristicUuid}")
                        continue
                    }
                    activeOperation = operation
                    if (runCatching { callbackGatt.readCharacteristic(characteristic) }.getOrDefault(false)) {
                        DebugTelemetry.log(logTag, "event=read_requested uuid=${characteristic.uuid}")
                        return
                    }
                    activeOperation = null
                    DebugTelemetry.log(logTag, "event=read_failed uuid=${characteristic.uuid} request=false")
                }
            }
        }
        if (state == BleGattState.SUBSCRIBING) markReady()
    }

    private fun markReady(): Boolean {
        if (readySubscriptionCount == 0) {
            failConnection(
                BleDisconnectEvent(GATT_STATUS_UNAVAILABLE, "measurement_subscription_failed", retryable = true),
                telemetryEvent = "measurement_missing",
            )
            return false
        }
        state = BleGattState.READY
        mainHandler.removeCallbacks(setupTimeoutRunnable)
        onConnectionChanged(true)
        DebugTelemetry.log(logTag, "event=connected status=0 readySubscriptions=$readySubscriptionCount")
        return true
    }

    private fun rejectConnection(
        reason: String,
        retryable: Boolean,
    ) {
        state = BleGattState.IDLE
        onConnectionChanged(false)
        DebugTelemetry.log(logTag, "event=connect_skipped reason=$reason")
        onDisconnected(BleDisconnectEvent(GATT_STATUS_UNAVAILABLE, reason, retryable))
    }

    private fun failConnection(
        event: BleDisconnectEvent,
        telemetryEvent: String,
    ) {
        DebugTelemetry.log(logTag, "event=$telemetryEvent status=${event.status} reason=${event.reason}")
        closeCurrentGatt(requestDisconnect = true)
        onConnectionChanged(false)
        onDisconnected(event)
    }

    @SuppressLint("MissingPermission")
    private fun closeCurrentGatt(requestDisconnect: Boolean) {
        mainHandler.removeCallbacks(setupTimeoutRunnable)
        pendingOperations.clear()
        activeOperation = null
        readySubscriptionCount = 0
        state = BleGattState.IDLE
        val currentGatt = gatt
        gatt = null
        if (currentGatt != null) {
            runCatching {
                if (requestDisconnect && hasConnectPermission(context)) currentGatt.disconnect()
                currentGatt.close()
            }
        }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
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
        val contributesToReady: Boolean,
    ) : BleGattOperation

    data class Read(
        override val serviceUuid: UUID,
        override val characteristicUuid: UUID,
    ) : BleGattOperation
}

private enum class BleGattState {
    IDLE,
    CONNECTING,
    DISCOVERING,
    SUBSCRIBING,
    READY,
}

internal fun notificationDescriptorValue(characteristicProperties: Int): ByteArray? =
    when {
        characteristicProperties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ->
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        characteristicProperties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 ->
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        else -> null
    }

internal fun hasConnectPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

@SuppressLint("NewApi", "MissingPermission")
private fun BluetoothDevice.connectGattApi37(
    context: Context,
    callback: BluetoothGattCallback,
): BluetoothGatt? {
    val settings =
        BluetoothGattConnectionSettings
            .Builder()
            .setAutoConnectEnabled(false)
            .setTransport(BluetoothDevice.TRANSPORT_LE)
            .build()
    return connectGatt(settings, context.mainExecutor, callback)
}

@Suppress("DEPRECATION")
@SuppressLint("MissingPermission")
private fun BluetoothDevice.connectGattLegacy(
    context: Context,
    callback: BluetoothGattCallback,
): BluetoothGatt? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    } else {
        connectGatt(context, false, callback)
    }

internal object BluetoothUuid {
    fun service16(shortUuid: Int): UUID = bluetoothUuid(shortUuid)

    fun characteristic16(shortUuid: Int): UUID = bluetoothUuid(shortUuid)

    fun descriptor16(shortUuid: Int): UUID = bluetoothUuid(shortUuid)

    private fun bluetoothUuid(shortUuid: Int): UUID = UUID.fromString("0000${shortUuid.toString(16).padStart(4, '0')}-0000-1000-8000-00805f9b34fb")
}

private const val SETUP_TIMEOUT_MS = 20_000L
private const val GATT_STATUS_UNAVAILABLE = -1
private const val ANDROID_API_LEVEL_37 = 37
