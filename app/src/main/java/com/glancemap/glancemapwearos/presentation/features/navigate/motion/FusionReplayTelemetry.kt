package com.glancemap.glancemapwearos.presentation.features.navigate.motion

import org.mapsforge.core.model.LatLong
import java.util.ArrayDeque
import kotlin.math.max

internal data class FusionReplaySummary(
    val acceptedFixes: Int,
    val outlierDrops: Int,
    val predictions: Int,
    val blendStarts: Int,
    val avgBlendDurationMs: Long,
    val toStationary: Int,
    val toMoving: Int,
    val maxJumpMeters: Float,
    val maxImpliedSpeedMps: Float,
)

internal object FusionReplayTelemetry {
    private val lock = Any()
    private val lines = ArrayDeque<String>()
    private var droppedLines: Int = 0

    private var sessionStartElapsedMs: Long = 0L
    private var acceptedFixes: Int = 0
    private var outlierDrops: Int = 0
    private var predictions: Int = 0
    private var blendStarts: Int = 0
    private var blendDurationTotalMs: Long = 0L
    private var toStationary: Int = 0
    private var toMoving: Int = 0
    private var maxJumpMeters: Float = 0f
    private var maxImpliedSpeedMps: Float = 0f

    private const val MAX_LINES = 6000

    fun clear() {
        synchronized(lock) {
            lines.clear()
            droppedLines = 0
            sessionStartElapsedMs = 0L
            acceptedFixes = 0
            outlierDrops = 0
            predictions = 0
            blendStarts = 0
            blendDurationTotalMs = 0L
            toStationary = 0
            toMoving = 0
            maxJumpMeters = 0f
            maxImpliedSpeedMps = 0f
        }
    }

    fun summary(): FusionReplaySummary =
        synchronized(lock) {
            val avgBlend = if (blendStarts > 0) blendDurationTotalMs / blendStarts else 0L
            FusionReplaySummary(
                acceptedFixes = acceptedFixes,
                outlierDrops = outlierDrops,
                predictions = predictions,
                blendStarts = blendStarts,
                avgBlendDurationMs = avgBlend,
                toStationary = toStationary,
                toMoving = toMoving,
                maxJumpMeters = maxJumpMeters,
                maxImpliedSpeedMps = maxImpliedSpeedMps,
            )
        }

    fun summaryLabel(): String {
        val s = summary()
        return "fix=${s.acceptedFixes} drop=${s.outlierDrops} pred=${s.predictions} blend=${s.blendStarts}"
    }

    fun snapshotLines(): List<String> = synchronized(lock) { lines.toList() }

    fun droppedLineCount(): Int = synchronized(lock) { droppedLines }

    fun maxBufferedLines(): Int = MAX_LINES

    fun recordFixAccepted(
        nowElapsedMs: Long,
        latLong: LatLong,
        accuracyM: Float,
        speedMps: Float,
        bearingDeg: Float,
        motionState: String,
        jumpMeters: Float,
        blendDurationMs: Long?,
    ) {
        synchronized(lock) {
            ensureSession(nowElapsedMs)
            acceptedFixes += 1
            maxJumpMeters = max(maxJumpMeters, jumpMeters)
            if (blendDurationMs != null) {
                blendStarts += 1
                blendDurationTotalMs += blendDurationMs
            }
            addLine(
                nowElapsedMs = nowElapsedMs,
                payload =
                    "FIX_ACCEPT lat=${latLong.latitude.format(6)} lon=${latLong.longitude.format(6)} " +
                        "acc=${accuracyM.format(1)} speed=${speedMps.format(2)} bearing=${bearingDeg.format(1)} " +
                        "state=$motionState jumpM=${jumpMeters.format(1)} blendMs=${blendDurationMs ?: 0L}",
            )
        }
    }

    fun recordOutlierDropped(
        nowElapsedMs: Long,
        jumpMeters: Float,
        impliedSpeedMps: Float,
        accuracyM: Float,
        dtSec: Float,
    ) {
        synchronized(lock) {
            ensureSession(nowElapsedMs)
            outlierDrops += 1
            maxJumpMeters = max(maxJumpMeters, jumpMeters)
            maxImpliedSpeedMps = max(maxImpliedSpeedMps, impliedSpeedMps)
            addLine(
                nowElapsedMs = nowElapsedMs,
                payload =
                    "FIX_DROP jumpM=${jumpMeters.format(1)} impliedSpeed=${impliedSpeedMps.format(1)} " +
                        "acc=${accuracyM.format(1)} dt=${dtSec.format(2)}",
            )
        }
    }

    fun recordMotionTransition(
        nowElapsedMs: Long,
        fromState: String,
        toState: String,
        speedMps: Float,
    ) {
        synchronized(lock) {
            ensureSession(nowElapsedMs)
            if (toState == "STATIONARY") {
                toStationary += 1
            } else if (toState == "MOVING") {
                toMoving += 1
            }
            addLine(
                nowElapsedMs = nowElapsedMs,
                payload = "MOTION from=$fromState to=$toState speed=${speedMps.format(2)}",
            )
        }
    }

    fun recordPrediction(
        nowElapsedMs: Long,
        latLong: LatLong,
        speedMps: Float,
        bearingDeg: Float,
        motionState: String,
        staleMs: Long,
    ) {
        synchronized(lock) {
            ensureSession(nowElapsedMs)
            predictions += 1
            addLine(
                nowElapsedMs = nowElapsedMs,
                payload =
                    "PREDICT lat=${latLong.latitude.format(6)} lon=${latLong.longitude.format(6)} " +
                        "speed=${speedMps.format(2)} bearing=${bearingDeg.format(1)} state=$motionState staleMs=$staleMs",
            )
        }
    }

    private fun addLine(
        nowElapsedMs: Long,
        payload: String,
    ) {
        val relativeMs = (nowElapsedMs - sessionStartElapsedMs).coerceAtLeast(0L)
        lines.addLast("+${relativeMs}ms $payload")
        while (lines.size > MAX_LINES) {
            lines.removeFirst()
            droppedLines += 1
        }
    }

    private fun ensureSession(nowElapsedMs: Long) {
        if (sessionStartElapsedMs != 0L) return
        sessionStartElapsedMs = nowElapsedMs
        lines.addLast("+0ms SESSION_START")
    }
}

private fun Double.format(digits: Int): String = "%.${digits}f".format(this)

private fun Float.format(digits: Int): String = "%.${digits}f".format(this)
