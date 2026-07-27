package com.glancemap.glancemapwearos.presentation

import android.os.Build
import android.os.PowerManager
import androidx.activity.ComponentActivity
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry

internal class ThermalTelemetryController(
    private val activity: ComponentActivity,
    private val runtimeContext: () -> String,
) {
    private var listener: PowerManager.OnThermalStatusChangedListener? = null

    fun register() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val powerManager = activity.getSystemService(PowerManager::class.java) ?: return
        val thermalListener =
            PowerManager.OnThermalStatusChangedListener { status ->
                log(event = "status", status = status)
            }
        listener = thermalListener
        powerManager.addThermalStatusListener(activity.mainExecutor, thermalListener)
        log(event = "initial", status = powerManager.currentThermalStatus)
    }

    fun unregister() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val thermalListener = listener ?: return
        activity.getSystemService(PowerManager::class.java)?.removeThermalStatusListener(thermalListener)
        listener = null
    }

    private fun log(
        event: String,
        status: Int,
    ) {
        DebugTelemetry.log(
            "ThermalTelemetry",
            "event=$event status=$status label=${statusLabel(status)} ${runtimeContext()}",
        )
    }

    private fun statusLabel(status: Int): String =
        when (status) {
            PowerManager.THERMAL_STATUS_NONE -> "none"
            PowerManager.THERMAL_STATUS_LIGHT -> "light"
            PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
            else -> "unknown"
        }
}
