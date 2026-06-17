package com.glancemap.glancemapwearos.presentation.features.recording.external

import android.content.Context
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import java.util.UUID

class ExternalHeartRateClient(
    private val context: Context,
    private val address: String,
    private val onHeartRate: (bpm: Int, timeMillis: Long) -> Unit,
) {
    private val client =
        ExternalBleGattClient(
            context = context,
            address = address,
            logTag = "ExternalHeartRate",
            serviceUuid = HEART_RATE_SERVICE_UUID,
            measurementUuid = HEART_RATE_MEASUREMENT_UUID,
            onMeasurement = ::handleMeasurement,
        )

    fun connect() {
        client.connect()
    }

    fun disconnect() {
        client.disconnect()
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
