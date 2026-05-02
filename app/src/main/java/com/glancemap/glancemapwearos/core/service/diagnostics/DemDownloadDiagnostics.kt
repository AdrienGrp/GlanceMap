package com.glancemap.glancemapwearos.core.service.diagnostics

import java.util.ArrayDeque

internal data class DemDownloadSummary(
    val eventCount: Int,
    val droppedLineCount: Int,
    val maxBufferedLines: Int,
    val startedCount: Int,
    val completedCount: Int,
    val downloadedCount: Int,
    val skippedCount: Int,
    val missingCount: Int,
    val failedCount: Int,
    val resumeAttemptCount: Int,
    val resumeRestartCount: Int,
    val validationFailureCount: Int,
    val networkUnavailableCount: Int,
)

internal object DemDownloadDiagnostics {
    private const val TAG = "DemDownload"
    private const val MAX_LINES = 1200

    private val lock = Any()
    private val lines = ArrayDeque<String>()
    private var droppedLines: Int = 0

    fun clear() {
        synchronized(lock) {
            lines.clear()
            droppedLines = 0
        }
    }

    fun snapshotLines(): List<String> = synchronized(lock) { lines.toList() }

    fun droppedLineCount(): Int = synchronized(lock) { droppedLines }

    fun maxBufferedLines(): Int = MAX_LINES

    fun summary(): DemDownloadSummary =
        synchronized(lock) {
            DemDownloadSummary(
                eventCount = lines.size,
                droppedLineCount = droppedLines,
                maxBufferedLines = MAX_LINES,
                startedCount = lines.count { it.startsWith("event=start ") },
                completedCount = lines.count { it.startsWith("event=complete ") },
                downloadedCount = lines.count { it.startsWith("event=tile_downloaded ") },
                skippedCount = lines.count { it.startsWith("event=tile_skipped ") },
                missingCount = lines.count { it.startsWith("event=tile_missing ") },
                failedCount = lines.count { it.startsWith("event=tile_failed ") },
                resumeAttemptCount = lines.count { it.startsWith("event=tile_resume_attempt ") },
                resumeRestartCount = lines.count { it.startsWith("event=tile_resume_restart ") },
                validationFailureCount = lines.count { it.startsWith("event=tile_validation_failed ") },
                networkUnavailableCount = lines.count { it.contains(" networkUnavailable=true") },
            )
        }

    fun record(
        event: String,
        detail: String = "",
    ) {
        val line =
            buildString {
                append("event=").append(event)
                if (detail.isNotBlank()) {
                    append(' ').append(detail)
                }
            }
        push(line)
        DebugTelemetry.log(TAG, line)
    }

    private fun push(line: String) {
        synchronized(lock) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) {
                lines.removeFirst()
                droppedLines += 1
            }
        }
    }
}
