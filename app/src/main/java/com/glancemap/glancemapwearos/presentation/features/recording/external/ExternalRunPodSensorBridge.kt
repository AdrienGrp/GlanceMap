package com.glancemap.glancemapwearos.presentation.features.recording.external

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry

@Composable
fun ExternalRunPodSensorBridge(
    active: Boolean,
    paused: Boolean,
    address: String?,
    onMeasurement: (ExternalRunPodMeasurement) -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(context, active, paused, address) {
        val linkedAddress = address?.takeIf(String::isNotBlank)
        if (!active || paused || linkedAddress == null) {
            return@DisposableEffect onDispose {}
        }
        val client =
            ExternalRunPodClient(
                context = context.applicationContext,
                address = linkedAddress,
                onMeasurement = onMeasurement,
            )
        DebugTelemetry.log("ExternalRunPod", "event=bridge_start address=${linkedAddress.takeLast(5)}")
        client.connect()
        onDispose {
            client.disconnect()
            DebugTelemetry.log("ExternalRunPod", "event=bridge_stop")
        }
    }
}
