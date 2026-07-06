package com.glancemap.glancemapwearos.presentation.features.recording.external

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import java.util.UUID

class ExternalHeartRateClient(
    private val context: Context,
    private val address: String,
    private val onHeartRate: (bpm: Int, timeMillis: Long) -> Unit,
    private val onUnavailable: () -> Unit = {},
    private val autoReconnect: Boolean = false,
) {
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var manualDisconnect = false
    private var reconnectAttempt = 0
    private var reconnectScheduled = false
    private val reconnectRunnable: Runnable =
        Runnable {
            reconnectScheduled = false
            if (manualDisconnect) return@Runnable
            reconnectAttempt += 1
            val attempt = reconnectAttempt
            DebugTelemetry.log(
                "ExternalHeartRate",
                "event=reconnect_attempt attempt=$attempt",
            )
            client.connect()
        }

    private val client: ExternalBleGattClient =
        ExternalBleGattClient(
            context = context,
            address = address,
            logTag = "ExternalHeartRate",
            serviceUuid = HEART_RATE_SERVICE_UUID,
            measurementUuid = HEART_RATE_MEASUREMENT_UUID,
            readCharacteristics =
                listOf(
                    BleCharacteristicRef(BATTERY_SERVICE_UUID, BATTERY_LEVEL_UUID),
                ),
            onConnecting = {
                ExternalSensorConnectionStatus.markConnecting(address)
            },
            onConnectionChanged = { connected ->
                ExternalSensorConnectionStatus.update(address, connected)
                if (connected) {
                    reconnectAttempt = 0
                    reconnectScheduled = false
                    reconnectHandler.removeCallbacks(reconnectRunnable)
                }
            },
            onDisconnected = { status ->
                if (!manualDisconnect) {
                    onUnavailable()
                    scheduleReconnect(status)
                }
            },
            onCharacteristicRead = ::handleRead,
            onMeasurement = ::handleMeasurement,
        )

    fun connect() {
        manualDisconnect = false
        client.connect()
    }

    fun disconnect() {
        manualDisconnect = true
        reconnectScheduled = false
        reconnectHandler.removeCallbacks(reconnectRunnable)
        client.disconnect()
    }

    private fun scheduleReconnect(status: Int) {
        if (!autoReconnect || manualDisconnect || reconnectScheduled) return
        val nextAttempt = reconnectAttempt + 1
        val delayMs = reconnectDelayMs(nextAttempt)
        reconnectScheduled = true
        DebugTelemetry.log(
            "ExternalHeartRate",
            "event=reconnect_scheduled attempt=$nextAttempt delayMs=$delayMs status=$status",
        )
        reconnectHandler.postDelayed(reconnectRunnable, delayMs)
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

    private fun handleRead(
        characteristicUuid: UUID,
        value: ByteArray,
    ) {
        if (characteristicUuid != BATTERY_LEVEL_UUID) return
        val batteryLevelPercent = decodeBatteryLevel(value) ?: return
        ExternalSensorConnectionStatus.updateBattery(address, batteryLevelPercent)
        DebugTelemetry.log("ExternalHeartRate", "event=battery levelPercent=$batteryLevelPercent")
    }

    companion object {
        val HEART_RATE_SERVICE_UUID: UUID =
            BluetoothUuid.service16(0x180D)
        private val HEART_RATE_MEASUREMENT_UUID: UUID =
            BluetoothUuid.characteristic16(0x2A37)
        private val BATTERY_SERVICE_UUID: UUID =
            BluetoothUuid.service16(0x180F)
        private val BATTERY_LEVEL_UUID: UUID =
            BluetoothUuid.characteristic16(0x2A19)

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

        internal fun decodeBatteryLevel(value: ByteArray): Int? =
            value
                .firstOrNull()
                ?.toInt()
                ?.and(0xFF)
                ?.takeIf { it in 0..100 }
    }
}

private fun reconnectDelayMs(attempt: Int): Long =
    when (attempt) {
        1 -> 2_000L
        2 -> 5_000L
        3 -> 10_000L
        else -> 30_000L
    }
