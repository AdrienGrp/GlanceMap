package com.glancemap.glancemapwearos.presentation.features.navigate

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.core.service.location.model.LocationScreenState
import com.glancemap.glancemapwearos.domain.sensors.COMPASS_TELEMETRY_TAG
import com.glancemap.glancemapwearos.domain.sensors.CompassRenderState
import com.glancemap.glancemapwearos.presentation.features.navigate.motion.MarkerMotionTelemetry
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

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

internal fun reportCompassIssueNow(
    renderState: CompassRenderState,
    renderedHeadingDeg: Float,
    renderedMapRotationDeg: Float,
    screenState: LocationScreenState,
) {
    if (!DebugTelemetry.isEnabled()) return
    val nowElapsedMs = SystemClock.elapsedRealtime()
    val sampleAgeMs =
        renderState.headingSampleElapsedRealtimeMs
            ?.let { (nowElapsedMs - it).coerceAtLeast(0L) }
    DebugTelemetry.log(
        COMPASS_TELEMETRY_TAG,
        "user_report heading_looks_wrong " +
            "provider=${renderState.providerType.name} source=${renderState.headingSource.telemetryToken} " +
            "sourceReady=${
                renderState.headingSampleElapsedRealtimeMs != null &&
                    !renderState.headingSampleStale
            } screenState=${screenState.name} " +
            "heading=${renderState.headingDeg.formatDebug(1)} " +
            "rendered=${renderedHeadingDeg.formatDebug(1)} " +
            "mapRotation=${renderedMapRotationDeg.formatDebug(1)} " +
            "renderDelta=${compassDebugDeltaDeg(renderState.headingDeg, renderedHeadingDeg).formatDebug(1)} " +
            "headingError=${renderState.headingErrorDeg.formatDebugOrNa(1)} " +
            "conservativeError=${renderState.conservativeHeadingErrorDeg.formatDebugOrNa(1)} " +
            "sampleAgeMs=${sampleAgeMs ?: "na"} stale=${renderState.headingSampleStale} " +
            "magneticInterference=${renderState.magneticInterference}",
    )
}

private fun compassDebugDeltaDeg(
    firstDeg: Float,
    secondDeg: Float,
): Float {
    val normalized = ((firstDeg - secondDeg + 540f) % 360f) - 180f
    return kotlin.math.abs(normalized)
}

private fun Float.formatDebug(decimals: Int): String = "%.${decimals}f".format(Locale.US, this)

private fun Float?.formatDebugOrNa(decimals: Int): String = this?.takeIf(Float::isFinite)?.formatDebug(decimals) ?: "na"
