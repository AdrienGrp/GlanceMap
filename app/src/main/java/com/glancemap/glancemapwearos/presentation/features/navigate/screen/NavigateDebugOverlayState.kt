package com.glancemap.glancemapwearos.presentation.features.navigate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.glancemap.glancemapwearos.presentation.features.navigate.motion.MarkerMotionTelemetry
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun rememberMarkerMotionDebugOverlayLabel(
    gpsDebugTelemetry: Boolean,
    gpsDebugTelemetryPopupEnabled: Boolean,
    offlineMode: Boolean,
): String? {
    var markerMotionDebugOverlayLabel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(gpsDebugTelemetry, gpsDebugTelemetryPopupEnabled, offlineMode) {
        if (!gpsDebugTelemetry || !gpsDebugTelemetryPopupEnabled || offlineMode) {
            markerMotionDebugOverlayLabel = null
            return@LaunchedEffect
        }

        while (isActive) {
            markerMotionDebugOverlayLabel = MarkerMotionTelemetry.latestSnapshot().overlayLabel()
            delay(250L)
        }
    }
    return markerMotionDebugOverlayLabel
}
