package com.glancemap.glancemapwearos.presentation.features.navigate.motion

import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry

internal enum class MarkerMotionMode(
    val label: String,
) {
    IDLE("idle"),
    FIXED("fixed"),
    BLEND("blend"),
    PREDICT("predict"),
}

internal data class MarkerMotionSummary(
    val acceptedFixes: Int = 0,
    val outlierDrops: Int = 0,
    val predictionUpdates: Int = 0,
    val blendStarts: Int = 0,
    val clampedCorrections: Int = 0,
    val blockedTransitions: Int = 0,
    val latestMode: MarkerMotionMode = MarkerMotionMode.IDLE,
    val latestReason: String? = null,
) {
    fun summaryLabel(): String =
        buildString {
            append("mode=${latestMode.label}")
            append(" fix=$acceptedFixes")
            append(" pred=$predictionUpdates")
            append(" blend=$blendStarts")
            append(" clamp=$clampedCorrections")
            append(" drop=$outlierDrops")
        }
}

internal data class MarkerMotionSnapshot(
    val mode: MarkerMotionMode = MarkerMotionMode.IDLE,
    val reason: String? = null,
    val fixAgeMs: Long? = null,
    val accuracyM: Float? = null,
    val speedMps: Float? = null,
    val bearingDeg: Float? = null,
    val correctionDistanceM: Float? = null,
    val updatedAtElapsedMs: Long = 0L,
) {
    fun compactLabel(): String {
        if (mode == MarkerMotionMode.IDLE && updatedAtElapsedMs <= 0L) return "idle"

        val parts = mutableListOf("mode=${mode.label}")
        reasonLabel(reason)?.let { parts += "why=$it" }
        fixAgeMs?.let { parts += "age=${it}ms" }
        accuracyM?.let { parts += "acc=${it.format(0)}m" }
        speedMps?.let { parts += "v=${it.format(1)}" }
        bearingDeg?.let { parts += "brg=${it.format(0)}" }
        return parts.joinToString(" ")
    }

    fun overlayLabel(): String? {
        if (mode == MarkerMotionMode.IDLE && updatedAtElapsedMs <= 0L) return null

        val header =
            buildString {
                append(mode.label.uppercase())
                reasonLabel(reason)?.let {
                    append(' ')
                    append(it)
                }
            }
        val details = mutableListOf<String>()
        fixAgeMs?.let { details += "age ${it}ms" }
        accuracyM?.let { details += "acc ${it.format(0)}m" }
        speedMps?.let { details += "v ${it.format(1)}" }
        bearingDeg?.let { details += "brg ${it.format(0)}" }
        correctionDistanceM?.let { details += "corr ${it.format(1)}m" }
        return if (details.isEmpty()) header else "$header\n${details.joinToString(" ")}"
    }
}

internal data class CorrectionClampTelemetryEvent(
    val nowElapsedMs: Long,
    val actualCorrectionDistanceM: Float,
    val visibleCorrectionDistanceM: Float,
    val accuracyM: Float,
    val speedMps: Float,
    val bearingDeg: Float?,
)

internal object MarkerMotionTelemetry {
    private const val TAG = "MarkerMotion"

    private val lock = Any()
    private var latestSnapshot = MarkerMotionSnapshot()
    private var acceptedFixes: Int = 0
    private var outlierDrops: Int = 0
    private var predictionUpdates: Int = 0
    private var blendStarts: Int = 0
    private var clampedCorrections: Int = 0
    private var blockedTransitions: Int = 0
    private var lastLoggedStateSignature: String? = null

    fun clear() {
        synchronized(lock) {
            latestSnapshot = MarkerMotionSnapshot()
            acceptedFixes = 0
            outlierDrops = 0
            predictionUpdates = 0
            blendStarts = 0
            clampedCorrections = 0
            blockedTransitions = 0
            lastLoggedStateSignature = null
        }
    }

    fun latestSnapshot(): MarkerMotionSnapshot =
        synchronized(lock) {
            latestSnapshot
        }

    fun latestStatusLabel(): String = latestSnapshot().compactLabel()

    fun summary(): MarkerMotionSummary =
        synchronized(lock) {
            MarkerMotionSummary(
                acceptedFixes = acceptedFixes,
                outlierDrops = outlierDrops,
                predictionUpdates = predictionUpdates,
                blendStarts = blendStarts,
                clampedCorrections = clampedCorrections,
                blockedTransitions = blockedTransitions,
                latestMode = latestSnapshot.mode,
                latestReason = latestSnapshot.reason,
            )
        }

    fun summaryLabel(): String = summary().summaryLabel()

    fun recordIdle(
        nowElapsedMs: Long,
        reason: String,
    ) {
        recordStateTransition(
            snapshot =
                MarkerMotionSnapshot(
                    mode = MarkerMotionMode.IDLE,
                    reason = reason,
                    updatedAtElapsedMs = nowElapsedMs,
                ),
            logMessage = "idle reason=${reasonLabel(reason) ?: reason}",
        )
    }

    fun recordSeedAnchor(
        nowElapsedMs: Long,
        accuracyM: Float,
        speedMps: Float,
        bearingDeg: Float?,
    ) {
        val snapshot =
            MarkerMotionSnapshot(
                mode = MarkerMotionMode.FIXED,
                reason = "wake_anchor",
                fixAgeMs = 0L,
                accuracyM = accuracyM,
                speedMps = speedMps,
                bearingDeg = bearingDeg,
                updatedAtElapsedMs = nowElapsedMs,
            )
        synchronized(lock) {
            latestSnapshot = snapshot
            lastLoggedStateSignature = stateSignature(snapshot)
        }
        DebugTelemetry.log(
            TAG,
            buildString {
                append("seed reason=wake")
                append(" acc=${accuracyM.format(1)}")
                append(" speed=${speedMps.format(2)}")
                append(" bearing=${bearingDeg.formatOrNa(1)}")
            },
        )
    }

    fun recordFixAccepted(
        mode: MarkerMotionMode,
        reason: String,
        nowElapsedMs: Long,
        fixAgeMs: Long,
        accuracyM: Float,
        speedMps: Float,
        bearingDeg: Float?,
        correctionDistanceM: Float?,
        blendDurationMs: Long?,
    ) {
        val snapshot =
            MarkerMotionSnapshot(
                mode = mode,
                reason = reason,
                fixAgeMs = fixAgeMs,
                accuracyM = accuracyM,
                speedMps = speedMps,
                bearingDeg = bearingDeg,
                correctionDistanceM = correctionDistanceM,
                updatedAtElapsedMs = nowElapsedMs,
            )
        synchronized(lock) {
            acceptedFixes += 1
            if (mode == MarkerMotionMode.BLEND) {
                blendStarts += 1
            }
            latestSnapshot = snapshot
            lastLoggedStateSignature = stateSignature(snapshot)
        }
        DebugTelemetry.log(
            TAG,
            buildString {
                append("fix mode=${mode.label}")
                append(" reason=${reasonLabel(reason) ?: reason}")
                append(" age=${fixAgeMs}ms")
                append(" acc=${accuracyM.format(1)}")
                append(" speed=${speedMps.format(2)}")
                append(" bearing=${bearingDeg.formatOrNa(1)}")
                correctionDistanceM?.let { append(" corr=${it.format(1)}") }
                blendDurationMs?.let { append(" blendMs=$it") }
            },
        )
    }

    fun recordBlendState(
        nowElapsedMs: Long,
        fixAgeMs: Long,
        accuracyM: Float,
        speedMps: Float,
        bearingDeg: Float?,
        correctionDistanceM: Float?,
    ) {
        val snapshot =
            MarkerMotionSnapshot(
                mode = MarkerMotionMode.BLEND,
                reason = "gps_correction",
                fixAgeMs = fixAgeMs,
                accuracyM = accuracyM,
                speedMps = speedMps,
                bearingDeg = bearingDeg,
                correctionDistanceM = correctionDistanceM,
                updatedAtElapsedMs = nowElapsedMs,
            )
        synchronized(lock) {
            latestSnapshot = snapshot
        }
    }

    fun recordCorrectionClamped(event: CorrectionClampTelemetryEvent) {
        synchronized(lock) {
            clampedCorrections += 1
        }
        DebugTelemetry.log(
            TAG,
            buildString {
                append("clamp actual=${event.actualCorrectionDistanceM.format(1)}")
                append(" visible=${event.visibleCorrectionDistanceM.format(1)}")
                append(" acc=${event.accuracyM.format(1)}")
                append(" speed=${event.speedMps.format(2)}")
                append(" bearing=${event.bearingDeg.formatOrNa(1)}")
                append(" at=${event.nowElapsedMs}ms")
            },
        )
    }

    fun recordOutlierDropped(
        nowElapsedMs: Long,
        fixAgeMs: Long,
        accuracyM: Float,
        jumpMeters: Float,
        impliedSpeedMps: Float,
        dtSec: Float,
    ) {
        val snapshot =
            MarkerMotionSnapshot(
                mode = MarkerMotionMode.FIXED,
                reason = "outlier_drop",
                fixAgeMs = fixAgeMs,
                accuracyM = accuracyM,
                speedMps = impliedSpeedMps,
                correctionDistanceM = jumpMeters,
                updatedAtElapsedMs = nowElapsedMs,
            )
        synchronized(lock) {
            outlierDrops += 1
            latestSnapshot = snapshot
            lastLoggedStateSignature = stateSignature(snapshot)
        }
        DebugTelemetry.log(
            TAG,
            buildString {
                append("drop reason=outlier")
                append(" jump=${jumpMeters.format(1)}")
                append(" impliedSpeed=${impliedSpeedMps.format(1)}")
                append(" dt=${dtSec.format(2)}")
                append(" acc=${accuracyM.format(1)}")
            },
        )
    }

    fun recordPredictionBlocked(
        reason: String,
        nowElapsedMs: Long,
        fixAgeMs: Long?,
        accuracyM: Float?,
        speedMps: Float?,
        bearingDeg: Float?,
    ) {
        val snapshot =
            MarkerMotionSnapshot(
                mode = MarkerMotionMode.FIXED,
                reason = reason,
                fixAgeMs = fixAgeMs,
                accuracyM = accuracyM,
                speedMps = speedMps,
                bearingDeg = bearingDeg,
                updatedAtElapsedMs = nowElapsedMs,
            )
        recordStateTransition(
            snapshot = snapshot,
            incrementBlockedTransitions = true,
            logMessage =
                buildString {
                    append("hold reason=${reasonLabel(reason) ?: reason}")
                    fixAgeMs?.let { append(" age=${it}ms") }
                    accuracyM?.let { append(" acc=${it.format(1)}") }
                    speedMps?.let { append(" speed=${it.format(2)}") }
                    bearingDeg?.let { append(" bearing=${it.format(1)}") }
                },
        )
    }

    fun recordPredictionDisplayed(
        nowElapsedMs: Long,
        fixAgeMs: Long,
        accuracyM: Float,
        speedMps: Float,
        bearingDeg: Float,
        predictedDistanceM: Float,
    ) {
        val snapshot =
            MarkerMotionSnapshot(
                mode = MarkerMotionMode.PREDICT,
                reason = "between_fixes",
                fixAgeMs = fixAgeMs,
                accuracyM = accuracyM,
                speedMps = speedMps,
                bearingDeg = bearingDeg,
                correctionDistanceM = predictedDistanceM,
                updatedAtElapsedMs = nowElapsedMs,
            )
        var shouldLog = false
        synchronized(lock) {
            predictionUpdates += 1
            latestSnapshot = snapshot
            val signature = stateSignature(snapshot)
            if (signature != lastLoggedStateSignature) {
                lastLoggedStateSignature = signature
                shouldLog = true
            }
        }
        if (shouldLog) {
            DebugTelemetry.log(
                TAG,
                buildString {
                    append("predict age=${fixAgeMs}ms")
                    append(" acc=${accuracyM.format(1)}")
                    append(" speed=${speedMps.format(2)}")
                    append(" bearing=${bearingDeg.format(1)}")
                    append(" dist=${predictedDistanceM.format(1)}")
                },
            )
        }
    }

    private fun recordStateTransition(
        snapshot: MarkerMotionSnapshot,
        incrementBlockedTransitions: Boolean = false,
        logMessage: String,
    ) {
        var shouldLog = false
        synchronized(lock) {
            latestSnapshot = snapshot
            val signature = stateSignature(snapshot)
            if (signature != lastLoggedStateSignature) {
                if (incrementBlockedTransitions) {
                    blockedTransitions += 1
                }
                lastLoggedStateSignature = signature
                shouldLog = true
            }
        }
        if (shouldLog) {
            DebugTelemetry.log(TAG, logMessage)
        }
    }

    private fun stateSignature(snapshot: MarkerMotionSnapshot): String =
        buildString {
            append(snapshot.mode.label)
            append(':')
            append(snapshot.reason.orEmpty())
        }
}

private fun reasonLabel(reason: String?): String? =
    when (reason) {
        null -> null
        "await_fresh_fix" -> "wait fix"
        "wake_anchor" -> "wake"
        "initial_fix" -> "first"
        "gps_correction" -> "correct"
        "correction_clamped" -> "clamp"
        "stationary_jitter" -> "steady"
        "deadband_snap" -> "snap"
        "stale_fix" -> "stale fix"
        "outlier_drop" -> "outlier"
        "prediction_delay" -> "delay"
        "stale" -> "stale"
        "bad_accuracy" -> "bad acc"
        "no_bearing" -> "no brg"
        "slow" -> "slow"
        "too_close" -> "tiny"
        "between_fixes" -> "between"
        "degraded_gps" -> "gps weak"
        "watch_gps_catch_up" -> "watch catch up"
        "reset" -> "reset"
        "interactive_start" -> "screen wake"
        "tracking_stopped" -> "track off"
        "fresh_fix_release" -> "fresh start"
        "marker_hidden" -> "hidden"
        "dispose" -> "dispose"
        else -> reason.replace('_', ' ')
    }

private fun Float.format(digits: Int): String = "%.${digits}f".format(this)

private fun Float?.formatOrNa(digits: Int): String = this?.let { "%.${digits}f".format(it) } ?: "na"
