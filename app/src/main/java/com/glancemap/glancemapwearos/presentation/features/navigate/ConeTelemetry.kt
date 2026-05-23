package com.glancemap.glancemapwearos.presentation.features.navigate

import android.os.SystemClock
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.domain.sensors.CompassRenderState
import com.glancemap.glancemapwearos.domain.sensors.HeadingSource
import java.util.Locale
import kotlin.math.roundToInt

private const val CONE_TELEMETRY_TAG = "ConeTelemetry"
private const val CONE_TELEMETRY_MIN_INTERVAL_MS = 2_000L

internal data class ConeTelemetryDecision(
    val navMode: NavMode,
    val overlayEnabled: Boolean,
    val shouldShow: Boolean,
    val compass: ConeTelemetryCompass,
    val gps: ConeTelemetryGps,
    val marker: ConeTelemetryMarker,
)

internal data class ConeTelemetryCompass(
    val quality: CompassMarkerQuality,
    val headingErrorDeg: Float?,
    val renderState: CompassRenderState,
    val renderedHeadingDeg: Float,
)

internal data class ConeTelemetryGps(
    val accuracyM: Float,
    val fresh: Boolean,
    val speedMps: Float,
    val bearingDeg: Float?,
)

internal data class ConeTelemetryMarker(
    val present: Boolean,
    val headingDeg: Float?,
)

internal class ConeTelemetryLogger {
    private var lastSignature: String? = null
    private var lastStateKey: String? = null
    private var lastLoggedAtElapsedMs: Long = Long.MIN_VALUE

    fun log(decision: ConeTelemetryDecision) {
        if (!DebugTelemetry.isEnabled()) return

        val reason = coneDecisionReason(decision)
        val source = if (decision.shouldShow) "compass" else "none"
        val stateKey =
            "${decision.shouldShow}|$source|$reason|${decision.navMode}|${decision.compass.quality.name}"
        val signature = buildConeSignature(decision, stateKey)
        val nowElapsedMs = SystemClock.elapsedRealtime()
        if (shouldLog(signature, stateKey, nowElapsedMs)) {
            DebugTelemetry.log(CONE_TELEMETRY_TAG, decision.toLogMessage(source, reason))
        }
    }

    private fun shouldLog(
        signature: String,
        stateKey: String,
        nowElapsedMs: Long,
    ): Boolean {
        val stateChanged = stateKey != lastStateKey
        val intervalElapsed = nowElapsedMs - lastLoggedAtElapsedMs >= CONE_TELEMETRY_MIN_INTERVAL_MS
        val shouldLog = signature != lastSignature && (stateChanged || intervalElapsed)
        if (shouldLog) {
            lastSignature = signature
            lastStateKey = stateKey
            lastLoggedAtElapsedMs = nowElapsedMs
        }
        return shouldLog
    }
}

private fun buildConeSignature(
    decision: ConeTelemetryDecision,
    stateKey: String,
): String =
    stateKey +
        "|compass=${decision.compass.renderState.headingDeg.bucket(5f)}" +
        "|rendered=${decision.compass.renderedHeadingDeg.bucket(5f)}" +
        "|marker=${decision.marker.headingDeg?.bucket(5f) ?: "na"}" +
        "|gpsBrg=${decision.gps.bearingDeg?.bucket(5f) ?: "na"}" +
        "|gpsSpd=${decision.gps.speedMps.bucket(0.25f)}" +
        "|gpsAcc=${decision.gps.accuracyM.bucket(5f)}" +
        "|gpsFresh=${decision.gps.fresh}" +
        "|headingSource=${decision.compass.renderState.headingSource.name}"

private fun ConeTelemetryDecision.toLogMessage(
    source: String,
    reason: String,
): String =
    "event=decision visible=$shouldShow source=$source reason=$reason navMode=$navMode " +
        "coneHeading=${coneHeadingForTelemetry(navMode, compass.renderedHeadingDeg).formatOrNa(1)} " +
        "renderedHeading=${compass.renderedHeadingDeg.formatOrNa(1)} " +
        "compassHeading=${compass.renderState.headingDeg.formatOrNa(1)} " +
        "compassSource=${compass.renderState.headingSource.name.lowercase(Locale.US)} " +
        "compassAccuracy=${compass.renderState.accuracy} " +
        "headingError=${compass.headingErrorDeg.formatOrNa(1)} " +
        "quality=${compass.quality.name.lowercase(Locale.US)} " +
        "markerHeading=${marker.headingDeg.formatOrNa(1)} " +
        "gpsBearing=${gps.bearingDeg.formatOrNa(1)} " +
        "gpsSpeed=${gps.speedMps.formatOrNa(2)} " +
        "gpsAccuracy=${gps.accuracyM.formatOrNa(1)} " +
        "gpsFresh=${gps.fresh}"

private fun coneDecisionReason(decision: ConeTelemetryDecision): String =
    when {
        !decision.overlayEnabled -> "style_disabled"
        !decision.marker.present -> "no_marker"
        decision.navMode == NavMode.PANNING -> "panning"
        decision.compass.renderState.headingSource == HeadingSource.NONE -> "no_compass"
        !shouldDriveHeadingForNavMode(decision.navMode, decision.compass.renderState) -> {
            when {
                decision.compass.renderState.headingSampleStale -> "compass_stale"
                else -> "compass_not_driving"
            }
        }
        decision.shouldShow -> "visible"
        else -> "hidden"
    }

private fun coneHeadingForTelemetry(
    navMode: NavMode,
    renderedHeadingDeg: Float,
): Float =
    when (navMode) {
        NavMode.NORTH_UP_FOLLOW -> renderedHeadingDeg
        NavMode.COMPASS_FOLLOW,
        NavMode.PANNING,
        -> 0f
    }

private fun Float.bucket(step: Float): Int {
    if (!isFinite() || step <= 0f) return Int.MIN_VALUE
    return (this / step).roundToInt()
}

private fun Float?.formatOrNa(decimals: Int): String =
    if (this == null || !isFinite()) {
        "na"
    } else {
        "%.${decimals}f".format(Locale.US, this)
    }
