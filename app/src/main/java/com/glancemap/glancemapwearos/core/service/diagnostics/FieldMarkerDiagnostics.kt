package com.glancemap.glancemapwearos.core.service.diagnostics

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque

internal object FieldMarkerDiagnostics {
    private const val TAG = "FieldMarker"
    private const val MAX_LINES = 300

    private val lock = Any()
    private val lines = ArrayDeque<String>()
    private var droppedLines: Int = 0
    private val tsFormatter: DateTimeFormatter =
        DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault())

    fun clear() {
        synchronized(lock) {
            lines.clear()
            droppedLines = 0
        }
    }

    fun snapshotLines(): List<String> = synchronized(lock) { lines.toList() }

    fun droppedLineCount(): Int = synchronized(lock) { droppedLines }

    fun maxBufferedLines(): Int = MAX_LINES

    fun recordMarker(
        type: String,
        note: String = "",
    ) {
        val safeType = sanitizeToken(type, fallback = "manual")
        val safeNote = sanitizeToken(note, fallback = "na")
        val payload = "type=$safeType note=$safeNote"
        push(payload)
        if (DebugTelemetry.isEnabled()) {
            DebugTelemetry.log(TAG, "marker $payload")
        }
    }

    private fun push(payload: String) {
        val nowEpochMs = System.currentTimeMillis()
        val line = "${tsFormatter.format(Instant.ofEpochMilli(nowEpochMs))} $payload"
        synchronized(lock) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) {
                lines.removeFirst()
                droppedLines += 1
            }
        }
    }

    private fun sanitizeToken(
        value: String,
        fallback: String,
    ): String {
        val cleaned =
            value
                .trim()
                .replace(Regex("\\s+"), "_")
                .replace(Regex("[^A-Za-z0-9_\\-.:]"), "")
                .take(64)
        return if (cleaned.isBlank()) fallback else cleaned
    }
}
