package com.glancemap.glancemapwearos.presentation.features.navigate.effects

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.core.service.location.model.LocationScreenState
import com.glancemap.glancemapwearos.domain.sensors.COMPASS_TELEMETRY_TAG
import com.glancemap.glancemapwearos.domain.sensors.CompassRenderState
import com.glancemap.glancemapwearos.domain.sensors.HeadingSource
import kotlin.math.abs

@Composable
internal fun NavigateCompassWakeTelemetry(
    isScreenResumed: Boolean,
    screenState: LocationScreenState,
    isOfflineMode: Boolean,
    renderState: CompassRenderState,
    renderedHeadingDeg: Float,
    renderedMapRotationDeg: Float,
) {
    var sessionId by remember { mutableLongStateOf(0L) }
    var startedAtMs by remember { mutableLongStateOf(0L) }
    var firstSourceLogged by remember { mutableStateOf(false) }
    var fusedLogged by remember { mutableStateOf(false) }
    var renderedLogged by remember { mutableStateOf(false) }
    val interactive = isScreenResumed && screenState == LocationScreenState.INTERACTIVE && !isOfflineMode

    LaunchedEffect(interactive) {
        val now = SystemClock.elapsedRealtime()
        if (interactive) {
            sessionId += 1L
            startedAtMs = now
            firstSourceLogged = false
            fusedLogged = false
            renderedLogged = false
            logCompassWake(
                "wake_session stage=start id=$sessionId screenState=${screenState.name}",
            )
        } else if (startedAtMs > 0L) {
            logCompassWake(
                "wake_session stage=end id=$sessionId durationMs=${(now - startedAtMs).coerceAtLeast(0L)} " +
                    "firstSource=$firstSourceLogged fused=$fusedLogged rendered=$renderedLogged " +
                    "screenState=${screenState.name} offline=$isOfflineMode",
            )
            startedAtMs = 0L
        }
    }

    LaunchedEffect(
        interactive,
        renderState.headingSource,
        renderState.headingSampleElapsedRealtimeMs,
    ) {
        if (!interactive || startedAtMs <= 0L) return@LaunchedEffect
        val sampleAtMs = renderState.headingSampleElapsedRealtimeMs ?: return@LaunchedEffect
        if (sampleAtMs < startedAtMs || renderState.headingSource == HeadingSource.NONE) {
            return@LaunchedEffect
        }
        val latencyMs = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
        if (!firstSourceLogged) {
            firstSourceLogged = true
            logCompassWake(
                "wake_session stage=first_source id=$sessionId latencyMs=$latencyMs " +
                    "source=${renderState.headingSource.telemetryToken} heading=${renderState.headingDeg.format(1)}",
            )
        }
        if (!fusedLogged && renderState.headingSource == HeadingSource.FUSED_ORIENTATION) {
            fusedLogged = true
            logCompassWake(
                "wake_session stage=fused_ready id=$sessionId latencyMs=$latencyMs " +
                    "heading=${renderState.headingDeg.format(1)}",
            )
        }
    }

    LaunchedEffect(
        interactive,
        renderState.headingDeg,
        renderState.headingSource,
        renderState.headingSampleElapsedRealtimeMs,
        renderedHeadingDeg,
        renderedMapRotationDeg,
    ) {
        if (!interactive || startedAtMs <= 0L || renderedLogged) return@LaunchedEffect
        val sampleAtMs = renderState.headingSampleElapsedRealtimeMs ?: return@LaunchedEffect
        if (sampleAtMs < startedAtMs || renderState.headingSource == HeadingSource.NONE) {
            return@LaunchedEffect
        }
        val deltaDeg = shortestHeadingDeltaDeg(renderedHeadingDeg, renderState.headingDeg)
        if (deltaDeg > COMPASS_WAKE_RENDER_ALIGNMENT_DEG) return@LaunchedEffect
        renderedLogged = true
        logCompassWake(
            "wake_session stage=render_aligned id=$sessionId " +
                "latencyMs=${(SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)} " +
                "source=${renderState.headingSource.telemetryToken} " +
                "heading=${renderState.headingDeg.format(1)} rendered=${renderedHeadingDeg.format(1)} " +
                "mapRotation=${renderedMapRotationDeg.format(1)} deltaDeg=${deltaDeg.format(1)}",
        )
    }
}

internal fun shortestHeadingDeltaDeg(
    firstDeg: Float,
    secondDeg: Float,
): Float {
    val normalized = ((firstDeg - secondDeg + 540f) % 360f) - 180f
    return abs(normalized)
}

private fun logCompassWake(message: String) {
    if (!DebugTelemetry.isEnabled()) return
    DebugTelemetry.log(COMPASS_TELEMETRY_TAG, message)
}

private fun Float.format(decimals: Int): String = "%.${decimals}f".format(java.util.Locale.US, this)

private const val COMPASS_WAKE_RENDER_ALIGNMENT_DEG = 3f
