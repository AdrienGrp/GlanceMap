package com.glancemap.glancemapwearos.presentation.features.navigate.effects

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
    val startupMetrics = remember { CompassStartupMetrics() }
    val interactive = isScreenResumed && screenState == LocationScreenState.INTERACTIVE && !isOfflineMode

    LaunchedEffect(interactive) {
        val now = SystemClock.elapsedRealtime()
        if (interactive) {
            sessionId += 1L
            startedAtMs = now
            firstSourceLogged = false
            fusedLogged = false
            renderedLogged = false
            if (DebugTelemetry.isEnabled()) {
                startupMetrics.start(
                    sessionId = sessionId,
                    nowElapsedMs = now,
                    initialHeadingDeg = renderState.headingDeg,
                    initialRenderedHeadingDeg = renderedHeadingDeg,
                    initialMapRotationDeg = renderedMapRotationDeg,
                )
            }
            logCompassWake(
                "wake_session stage=start id=$sessionId screenState=${screenState.name} " +
                    CompassSessionHistory.previousSessionDescription(
                        nextHeadingDeg = renderState.headingDeg,
                        nowElapsedMs = now,
                    ),
            )
        } else if (startedAtMs > 0L) {
            startupMetrics.finish(
                nowElapsedMs = now,
                finalHeadingDeg = renderState.headingDeg,
                finalRenderedHeadingDeg = renderedHeadingDeg,
                finalMapRotationDeg = renderedMapRotationDeg,
            )
            logCompassWake(
                "wake_session stage=end id=$sessionId durationMs=${(now - startedAtMs).coerceAtLeast(0L)} " +
                    "firstSource=$firstSourceLogged fused=$fusedLogged rendered=$renderedLogged " +
                    "screenState=${screenState.name} offline=$isOfflineMode",
            )
            startedAtMs = 0L
        }
    }

    LaunchedEffect(interactive, sessionId) {
        if (!interactive || startedAtMs <= 0L) return@LaunchedEffect
        delay(STARTUP_METRICS_WINDOW_MS)
        startupMetrics.logStartupSummary(SystemClock.elapsedRealtime())
    }

    SideEffect {
        if (interactive && startedAtMs > 0L) {
            startupMetrics.record(
                nowElapsedMs = SystemClock.elapsedRealtime(),
                headingDeg = renderState.headingDeg,
                renderedHeadingDeg = renderedHeadingDeg,
                mapRotationDeg = renderedMapRotationDeg,
                source = renderState.headingSource,
            )
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

private class CompassStartupMetrics {
    private var sessionId = 0L
    private var startedAtMs = 0L
    private var sampleCount = 0
    private var firstHeadingDeg: Float? = null
    private var lastHeadingDeg: Float? = null
    private var lastRenderedHeadingDeg: Float? = null
    private var lastMapRotationDeg: Float? = null
    private var minUnwrappedHeadingDeg = 0f
    private var maxUnwrappedHeadingDeg = 0f
    private var unwrappedHeadingDeg = 0f
    private var cumulativeHeadingRotationDeg = 0f
    private var cumulativeMapRotationDeg = 0f
    private var maxHeadingJumpDeg = 0f
    private var directionReversalCount = 0
    private var previousDirection = 0
    private var renderErrorTotalDeg = 0f
    private var renderErrorMaxDeg = 0f
    private var renderErrorSampleCount = 0
    private var stable3AtMs: Long? = null
    private var stable5AtMs: Long? = null
    private var stable3SinceMs = 0L
    private var stable5SinceMs = 0L
    private var stable3AnchorDeg: Float? = null
    private var stable5AnchorDeg: Float? = null
    private var firstFusedAtMs: Long? = null
    private var summaryLogged = false

    fun start(
        sessionId: Long,
        nowElapsedMs: Long,
        initialHeadingDeg: Float,
        initialRenderedHeadingDeg: Float,
        initialMapRotationDeg: Float,
    ) {
        this.sessionId = sessionId
        startedAtMs = nowElapsedMs
        sampleCount = 0
        firstHeadingDeg = initialHeadingDeg.takeIf(Float::isFinite)
        lastHeadingDeg = initialHeadingDeg.takeIf(Float::isFinite)
        lastRenderedHeadingDeg = initialRenderedHeadingDeg.takeIf(Float::isFinite)
        lastMapRotationDeg = initialMapRotationDeg.takeIf(Float::isFinite)
        minUnwrappedHeadingDeg = 0f
        maxUnwrappedHeadingDeg = 0f
        unwrappedHeadingDeg = 0f
        cumulativeHeadingRotationDeg = 0f
        cumulativeMapRotationDeg = 0f
        maxHeadingJumpDeg = 0f
        directionReversalCount = 0
        previousDirection = 0
        renderErrorTotalDeg = 0f
        renderErrorMaxDeg = 0f
        renderErrorSampleCount = 0
        stable3AtMs = null
        stable5AtMs = null
        stable3SinceMs = 0L
        stable5SinceMs = 0L
        stable3AnchorDeg = null
        stable5AnchorDeg = null
        firstFusedAtMs = null
        summaryLogged = false
    }

    fun record(
        nowElapsedMs: Long,
        headingDeg: Float,
        renderedHeadingDeg: Float,
        mapRotationDeg: Float,
        source: HeadingSource,
    ) {
        if (startedAtMs <= 0L || nowElapsedMs - startedAtMs > STARTUP_METRICS_WINDOW_MS) return
        if (!headingDeg.isFinite()) return
        if (source == HeadingSource.FUSED_ORIENTATION && firstFusedAtMs == null) {
            firstFusedAtMs = nowElapsedMs
        }

        val previousHeading = lastHeadingDeg
        if (previousHeading != null) {
            val signedDelta = signedHeadingDeltaDeg(headingDeg, previousHeading)
            val absoluteDelta = abs(signedDelta)
            cumulativeHeadingRotationDeg += absoluteDelta
            maxHeadingJumpDeg = max(maxHeadingJumpDeg, absoluteDelta)
            unwrappedHeadingDeg += signedDelta
            minUnwrappedHeadingDeg = min(minUnwrappedHeadingDeg, unwrappedHeadingDeg)
            maxUnwrappedHeadingDeg = max(maxUnwrappedHeadingDeg, unwrappedHeadingDeg)
            val direction =
                when {
                    signedDelta > DIRECTION_REVERSAL_MIN_DELTA_DEG -> 1
                    signedDelta < -DIRECTION_REVERSAL_MIN_DELTA_DEG -> -1
                    else -> 0
                }
            if (direction != 0) {
                if (previousDirection != 0 && direction != previousDirection) {
                    directionReversalCount += 1
                }
                previousDirection = direction
            }
        }
        lastHeadingDeg = headingDeg
        sampleCount += 1

        if (renderedHeadingDeg.isFinite()) {
            val renderError = shortestHeadingDeltaDeg(renderedHeadingDeg, headingDeg)
            renderErrorTotalDeg += renderError
            renderErrorMaxDeg = max(renderErrorMaxDeg, renderError)
            renderErrorSampleCount += 1
            lastRenderedHeadingDeg = renderedHeadingDeg
        }
        if (mapRotationDeg.isFinite()) {
            lastMapRotationDeg?.let {
                cumulativeMapRotationDeg += shortestHeadingDeltaDeg(mapRotationDeg, it)
            }
            lastMapRotationDeg = mapRotationDeg
        }

        val strictAnchor = stable3AnchorDeg
        if (
            strictAnchor == null ||
            shortestHeadingDeltaDeg(headingDeg, strictAnchor) > STABLE_STRICT_SPAN_DEG
        ) {
            stable3AnchorDeg = headingDeg
            stable3SinceMs = nowElapsedMs
        } else if (stable3AtMs == null && nowElapsedMs - stable3SinceMs >= STABILITY_WINDOW_MS) {
            stable3AtMs = nowElapsedMs
        }
        val relaxedAnchor = stable5AnchorDeg
        if (
            relaxedAnchor == null ||
            shortestHeadingDeltaDeg(headingDeg, relaxedAnchor) > STABLE_RELAXED_SPAN_DEG
        ) {
            stable5AnchorDeg = headingDeg
            stable5SinceMs = nowElapsedMs
        } else if (stable5AtMs == null && nowElapsedMs - stable5SinceMs >= STABILITY_WINDOW_MS) {
            stable5AtMs = nowElapsedMs
        }
    }

    fun finish(
        nowElapsedMs: Long,
        finalHeadingDeg: Float,
        finalRenderedHeadingDeg: Float,
        finalMapRotationDeg: Float,
    ) {
        if (startedAtMs <= 0L) return
        if (nowElapsedMs - startedAtMs <= STARTUP_METRICS_WINDOW_MS) {
            record(
                nowElapsedMs = nowElapsedMs,
                headingDeg = finalHeadingDeg,
                renderedHeadingDeg = finalRenderedHeadingDeg,
                mapRotationDeg = finalMapRotationDeg,
                source = HeadingSource.NONE,
            )
        }
        logStartupSummary(nowElapsedMs)
        CompassSessionHistory.recordEnd(
            endedAtMs = nowElapsedMs,
            headingDeg = finalHeadingDeg,
            renderedHeadingDeg = finalRenderedHeadingDeg,
            mapRotationDeg = finalMapRotationDeg,
        )
        startedAtMs = 0L
    }

    fun logStartupSummary(nowElapsedMs: Long) {
        if (startedAtMs <= 0L || summaryLogged) return
        summaryLogged = true
        val renderErrorAvg =
            if (renderErrorSampleCount > 0) renderErrorTotalDeg / renderErrorSampleCount else Float.NaN
        logCompassWake(
            "wake_session stage=startup_summary id=$sessionId " +
                "windowMs=${min((nowElapsedMs - startedAtMs).coerceAtLeast(0L), STARTUP_METRICS_WINDOW_MS)} " +
                "samples=$sampleCount headingSpanDeg=${(maxUnwrappedHeadingDeg - minUnwrappedHeadingDeg).format(1)} " +
                "maxJumpDeg=${maxHeadingJumpDeg.format(1)} " +
                "cumulativeHeadingRotationDeg=${cumulativeHeadingRotationDeg.format(1)} " +
                "directionReversals=$directionReversalCount " +
                "cumulativeMapRotationDeg=${cumulativeMapRotationDeg.format(1)} " +
                "renderErrorAvgDeg=${renderErrorAvg.formatOrNa(1)} renderErrorMaxDeg=${renderErrorMaxDeg.format(1)} " +
                "stable3Ms=${stableLatency(stable3AtMs)} stable5Ms=${stableLatency(stable5AtMs)} " +
                "fusedReadyMs=${stableLatency(firstFusedAtMs)} " +
                "startHeading=${firstHeadingDeg.formatOrNa(1)} endHeading=${lastHeadingDeg.formatOrNa(1)}",
        )
    }

    private fun stableLatency(atMs: Long?): String =
        atMs?.let { (it - startedAtMs).coerceAtLeast(0L).toString() } ?: "na"
}

private object CompassSessionHistory {
    private var previous: PreviousCompassSession? = null

    @Synchronized
    fun recordEnd(
        endedAtMs: Long,
        headingDeg: Float,
        renderedHeadingDeg: Float,
        mapRotationDeg: Float,
    ) {
        previous =
            PreviousCompassSession(
                endedAtMs = endedAtMs,
                headingDeg = headingDeg,
                renderedHeadingDeg = renderedHeadingDeg,
                mapRotationDeg = mapRotationDeg,
            )
    }

    @Synchronized
    fun previousSessionDescription(
        nextHeadingDeg: Float,
        nowElapsedMs: Long,
    ): String {
        val last = previous ?: return "previousSession=none"
        return "previousSessionAgeMs=${(nowElapsedMs - last.endedAtMs).coerceAtLeast(0L)} " +
            "previousHeading=${last.headingDeg.format(1)} " +
            "previousRendered=${last.renderedHeadingDeg.format(1)} " +
            "previousMapRotation=${last.mapRotationDeg.format(1)} " +
            "restartHeadingDeltaDeg=${shortestHeadingDeltaDeg(nextHeadingDeg, last.headingDeg).format(1)}"
    }
}

private data class PreviousCompassSession(
    val endedAtMs: Long,
    val headingDeg: Float,
    val renderedHeadingDeg: Float,
    val mapRotationDeg: Float,
)

private fun signedHeadingDeltaDeg(
    targetDeg: Float,
    currentDeg: Float,
): Float = ((targetDeg - currentDeg + 540f) % 360f) - 180f

private fun Float?.formatOrNa(decimals: Int): String =
    this?.takeIf(Float::isFinite)?.format(decimals) ?: "na"

private fun Float.format(decimals: Int): String = "%.${decimals}f".format(Locale.US, this)

private const val COMPASS_WAKE_RENDER_ALIGNMENT_DEG = 3f
private const val STARTUP_METRICS_WINDOW_MS = 5_000L
private const val STABILITY_WINDOW_MS = 1_000L
private const val STABLE_STRICT_SPAN_DEG = 3f
private const val STABLE_RELAXED_SPAN_DEG = 5f
private const val DIRECTION_REVERSAL_MIN_DELTA_DEG = 1f
