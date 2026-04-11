package com.glancemap.glancemapwearos.core.service.diagnostics

import java.time.Instant
import java.time.ZoneId
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.roundToLong

private val skippedFramesRegex = Regex("""Skipped (\d+) frames!""")
private val daveyDurationRegex = Regex("""Davey!\s+duration=(\d+)ms""")
private val gcDurationRegex = Regex("""total ([0-9]+(?:\.[0-9]+)?)(ms|s)""")
private val gcFreedRegex = Regex("""freed ([0-9]+)(KB|MB|GB)""")

internal data class PerformanceLogcatSummary(
    val skippedFrameEventCount: Int = 0,
    val skippedFrameWarningCount: Int = 0,
    val skippedFramesMax: Int = 0,
    val daveyCount: Int = 0,
    val daveyMaxDurationMs: Long = 0L,
    val daveyTotalDurationMs: Long = 0L,
    val gcEventCount: Int = 0,
    val gcMaxDurationMs: Long = 0L,
    val gcTotalDurationMs: Long = 0L,
    val gcMaxFreedKb: Long = 0L,
    val resourceCloseWarningCount: Int = 0,
)

internal data class LogcatSnapshot(
    val pid: Int,
    val lines: List<String>,
    val totalReadLines: Int,
    val captureError: String?,
) {
    val truncated: Boolean
        get() = totalReadLines > lines.size
}

private const val MAX_DIAGNOSTICS_LOGCAT_LINES = 220

internal fun summarizePerformanceFromLogcat(lines: List<String>): PerformanceLogcatSummary {
    var skippedFrameEventCount = 0
    var skippedFrameWarningCount = 0
    var skippedFramesMax = 0
    var daveyCount = 0
    var daveyMaxDurationMs = 0L
    var daveyTotalDurationMs = 0L
    var gcEventCount = 0
    var gcMaxDurationMs = 0L
    var gcTotalDurationMs = 0L
    var gcMaxFreedKb = 0L
    var resourceCloseWarningCount = 0

    lines.forEach { line ->
        if (line.contains("skipframe too frequentlly", ignoreCase = true)) {
            skippedFrameWarningCount += 1
        }
        skippedFramesRegex
            .find(line)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { skipped ->
                skippedFrameEventCount += 1
                skippedFramesMax = maxOf(skippedFramesMax, skipped)
            }
        daveyDurationRegex
            .find(line)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?.let { durationMs ->
                daveyCount += 1
                daveyTotalDurationMs += durationMs
                daveyMaxDurationMs = maxOf(daveyMaxDurationMs, durationMs)
            }
        if (line.contains("GC freed")) {
            gcEventCount += 1
            gcDurationRegex.find(line)?.let { match ->
                parseDurationMs(
                    rawValue = match.groupValues[1],
                    unit = match.groupValues[2],
                )?.let { durationMs ->
                    gcTotalDurationMs += durationMs
                    gcMaxDurationMs = maxOf(gcMaxDurationMs, durationMs)
                }
            }
            gcFreedRegex.find(line)?.let { match ->
                parseSizeKb(
                    rawValue = match.groupValues[1],
                    unit = match.groupValues[2],
                )?.let { freedKb ->
                    gcMaxFreedKb = maxOf(gcMaxFreedKb, freedKb)
                }
            }
        }
        if (line.contains("A resource failed to call close.")) {
            resourceCloseWarningCount += 1
        }
    }

    return PerformanceLogcatSummary(
        skippedFrameEventCount = skippedFrameEventCount,
        skippedFrameWarningCount = skippedFrameWarningCount,
        skippedFramesMax = skippedFramesMax,
        daveyCount = daveyCount,
        daveyMaxDurationMs = daveyMaxDurationMs,
        daveyTotalDurationMs = daveyTotalDurationMs,
        gcEventCount = gcEventCount,
        gcMaxDurationMs = gcMaxDurationMs,
        gcTotalDurationMs = gcTotalDurationMs,
        gcMaxFreedKb = gcMaxFreedKb,
        resourceCloseWarningCount = resourceCloseWarningCount,
    )
}

private fun parseDurationMs(
    rawValue: String,
    unit: String,
): Long? {
    val value = rawValue.toDoubleOrNull() ?: return null
    val millis =
        when (unit.lowercase(Locale.US)) {
            "ms" -> value
            "s" -> value * 1000.0
            else -> return null
        }
    return millis.roundToLong()
}

private fun parseSizeKb(
    rawValue: String,
    unit: String,
): Long? {
    val value = rawValue.toLongOrNull() ?: return null
    return when (unit.uppercase(Locale.US)) {
        "KB" -> value
        "MB" -> value * 1024L
        "GB" -> value * 1024L * 1024L
        else -> null
    }
}

internal fun captureAppLogcat(capturedAt: Instant): LogcatSnapshot {
    val pid = android.os.Process.myPid()
    val primary =
        runLogcatCapture(
            command = listOf("logcat", "-d", "-v", "threadtime", "--pid=$pid"),
            pidFilter = null,
            pid = pid,
            capturedAt = capturedAt,
        )
    if (primary.lines.isNotEmpty() || primary.captureError == null) {
        return primary
    }

    val fallback =
        runLogcatCapture(
            command = listOf("logcat", "-d", "-v", "threadtime"),
            pidFilter = pid,
            pid = pid,
            capturedAt = capturedAt,
        )
    if (fallback.lines.isNotEmpty() || fallback.captureError == null) {
        return fallback
    }

    val combinedError = listOfNotNull(primary.captureError, fallback.captureError).joinToString(" | ")
    return primary.copy(captureError = combinedError.ifBlank { primary.captureError })
}

private fun runLogcatCapture(
    command: List<String>,
    pidFilter: Int?,
    pid: Int,
    capturedAt: Instant,
): LogcatSnapshot {
    return runCatching {
        val process =
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

        val ringBuffer = ArrayDeque<String>(MAX_DIAGNOSTICS_LOGCAT_LINES)
        var totalReadLines = 0
        process.inputStream.bufferedReader().useLines { sequence ->
            sequence.forEach { rawLine ->
                val line = rawLine.trimEnd()
                if (line.isEmpty()) return@forEach
                if (pidFilter != null && !matchesThreadtimePid(line = line, expectedPid = pidFilter)) {
                    return@forEach
                }
                totalReadLines += 1
                if (ringBuffer.size == MAX_DIAGNOSTICS_LOGCAT_LINES) {
                    ringBuffer.removeFirst()
                }
                ringBuffer.addLast(
                    normalizeThreadtimeLogcatLine(
                        line = line,
                        capturedAt = capturedAt,
                        zoneId = ZoneId.systemDefault(),
                    ),
                )
            }
        }

        val exitCode = process.waitFor()
        val error = if (exitCode == 0) null else "logcat_exit_$exitCode"
        LogcatSnapshot(
            pid = pid,
            lines = ringBuffer.toList(),
            totalReadLines = totalReadLines,
            captureError = error,
        )
    }.getOrElse { throwable ->
        LogcatSnapshot(
            pid = pid,
            lines = emptyList(),
            totalReadLines = 0,
            captureError = throwable.javaClass.simpleName,
        )
    }
}

private fun matchesThreadtimePid(
    line: String,
    expectedPid: Int,
): Boolean {
    val tokens = line.trim().split(Regex("\\s+"), limit = 4)
    if (tokens.size < 3) return false
    return tokens[2] == expectedPid.toString()
}
