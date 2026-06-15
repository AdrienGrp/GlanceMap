package com.glancemap.glancemapwearos.presentation.features.recording.external

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry

@Composable
fun ExternalHeartRateSensorBridge(
    active: Boolean,
    paused: Boolean,
    address: String?,
    onHeartRate: (bpm: Int, timeMillis: Long) -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(context, active, paused, address) {
        val linkedAddress = address?.takeIf(String::isNotBlank)
        if (!active || paused || linkedAddress == null) {
            return@DisposableEffect onDispose {}
        }
        val client =
            ExternalHeartRateClient(
                context = context.applicationContext,
                address = linkedAddress,
                onHeartRate = onHeartRate,
            )
        DebugTelemetry.log("ExternalHeartRate", "event=bridge_start address=${linkedAddress.takeLast(5)}")
        client.connect()
        onDispose {
            client.disconnect()
            DebugTelemetry.log("ExternalHeartRate", "event=bridge_stop")
        }
    }
}
