package com.glancemap.glancemapwearos.presentation

import android.content.Intent
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.wear.ambient.AmbientLifecycleObserver
import com.glancemap.glancemapwearos.core.service.diagnostics.ScreenStateDiagnostics

/**
 * Owns the Wear ambient and display state used by the navigation runtime.
 */
internal class WearAmbientState(
    private val activity: ComponentActivity,
    private val onTelemetryEvent: (String) -> Unit,
) {
    var isAmbient by mutableStateOf(false)
        private set
    var ambientTickMs by mutableStateOf(0L)
        private set
    var isDeviceInteractive by mutableStateOf(true)
        private set
    var burnInProtectionRequired by mutableStateOf(false)
        private set
    var deviceHasLowBitAmbient by mutableStateOf(false)
        private set
    val observer =
        AmbientLifecycleObserver(
            activity,
            object : AmbientLifecycleObserver.AmbientLifecycleCallback {
                override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                    burnInProtectionRequired = ambientDetails.burnInProtectionRequired
                    deviceHasLowBitAmbient = ambientDetails.deviceHasLowBitAmbient
                    isAmbient = true
                    ambientTickMs = System.currentTimeMillis()
                    refreshDeviceInteractive(fallback = false)
                    onTelemetryEvent("ambient_enter")
                }

                override fun onExitAmbient() {
                    isAmbient = false
                    burnInProtectionRequired = false
                    deviceHasLowBitAmbient = false
                    ambientTickMs = System.currentTimeMillis()
                    refreshDeviceInteractive(fallback = true)
                    onTelemetryEvent("ambient_exit")
                }

                override fun onUpdateAmbient() {
                    ambientTickMs = System.currentTimeMillis()
                }
            },
        )

    fun refreshDeviceInteractive(fallback: Boolean) {
        isDeviceInteractive = activity.getSystemService(PowerManager::class.java)?.isInteractive ?: fallback
        ScreenStateDiagnostics.updateDisplayState(
            isInteractive = isDeviceInteractive,
            isAmbient = isAmbient,
        )
    }

    fun onScreenStateChanged(action: String?) {
        refreshDeviceInteractive(fallback = action != Intent.ACTION_SCREEN_OFF)
    }
}
