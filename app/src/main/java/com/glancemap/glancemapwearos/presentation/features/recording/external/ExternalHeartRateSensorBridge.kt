@file:Suppress("FunctionNaming")

package com.glancemap.glancemapwearos.presentation.features.recording.external

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun ExternalHeartRateSensorBridge(
    active: Boolean,
    paused: Boolean,
    address: String?,
    onHeartRate: (bpm: Int, timeMillis: Long) -> Unit,
    onUnavailable: () -> Unit = {},
) {
    val context = LocalContext.current
    val simulated = ExternalSensorSimulation.isSimulatedAddress(address)

    LaunchedEffect(active, paused, address, simulated) {
        val linkedAddress = address?.takeIf(String::isNotBlank)
        if (!active || paused || linkedAddress == null || !simulated) return@LaunchedEffect
        ExternalSensorConnectionStatus.update(linkedAddress, true)
        ExternalSensorConnectionStatus.updateBattery(linkedAddress, SIMULATED_HEART_RATE_BATTERY_PERCENT)
        DebugTelemetry.log("ExternalHeartRate", "event=simulation_start")
        var sampleIndex = 0
        while (isActive) {
            val bpm = SIMULATED_HEART_RATE_BASE_BPM + (sampleIndex % SIMULATED_HEART_RATE_VARIATION)
            onHeartRate(bpm, System.currentTimeMillis())
            sampleIndex += 1
            delay(SIMULATED_SENSOR_SAMPLE_INTERVAL_MS)
        }
    }

    DisposableEffect(context, active, paused, address) {
        val linkedAddress = address?.takeIf(String::isNotBlank)
        if (!active || paused || linkedAddress == null) {
            return@DisposableEffect onDispose {}
        }
        if (ExternalSensorSimulation.isSimulatedAddress(linkedAddress)) {
            return@DisposableEffect onDispose {
                ExternalSensorConnectionStatus.update(linkedAddress, false)
                DebugTelemetry.log("ExternalHeartRate", "event=simulation_stop")
            }
        }
        val client =
            ExternalHeartRateClient(
                context = context.applicationContext,
                address = linkedAddress,
                onHeartRate = onHeartRate,
                onUnavailable = onUnavailable,
                autoReconnect = active && !paused,
            )
        DebugTelemetry.log("ExternalHeartRate", "event=bridge_start address=${linkedAddress.takeLast(5)}")
        client.connect()
        onDispose {
            client.disconnect()
            DebugTelemetry.log("ExternalHeartRate", "event=bridge_stop")
        }
    }
}

private const val SIMULATED_HEART_RATE_BASE_BPM = 132
private const val SIMULATED_HEART_RATE_VARIATION = 9
private const val SIMULATED_HEART_RATE_BATTERY_PERCENT = 86
private const val SIMULATED_SENSOR_SAMPLE_INTERVAL_MS = 1_000L
