package com.glancemap.glancemapwearos.core.service.diagnostics

import android.os.SystemClock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque

internal object GnssDiagnostics {
    private const val TAG = "GnssTelemetry"
    private const val MAX_LINES = 1200
    private const val MIN_STATUS_LOG_INTERVAL_MS = 1500L

    private val lock = Any()
    private val lines = ArrayDeque<String>()
    private var droppedLines: Int = 0
    private var lastStatusLoggedElapsedMs: Long = 0L
    private val tsFormatter: DateTimeFormatter =
        DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault())

    fun clear() {
        synchronized(lock) {
            lines.clear()
            droppedLines = 0
            lastStatusLoggedElapsedMs = 0L
        }
    }

    fun snapshotLines(): List<String> = synchronized(lock) { lines.toList() }

    fun droppedLineCount(): Int = synchronized(lock) { droppedLines }

    fun maxBufferedLines(): Int = MAX_LINES

    fun recordEvent(
        event: String,
        detail: String = "",
    ) {
        if (!DebugTelemetry.isEnabled()) return
        val payload =
            if (detail.isBlank()) {
                "event=$event"
            } else {
                "event=$event $detail"
            }
        push(payload)
        DebugTelemetry.log(TAG, payload)
    }

    fun recordStatus(
        satellites: Int,
        usedInFix: Int,
        cn0AvgDbHz: Float?,
        cn0MaxDbHz: Float?,
        carrierFrequencySatelliteCount: Int,
        l1SatelliteCount: Int,
        l5SatelliteCount: Int,
        dualBandObserved: Boolean,
        gpsCount: Int,
        galileoCount: Int,
        glonassCount: Int,
        beidouCount: Int,
        qzssCount: Int,
        sbasCount: Int,
        unknownCount: Int,
    ) {
        if (!DebugTelemetry.isEnabled()) return

        val nowElapsedMs = SystemClock.elapsedRealtime()
        synchronized(lock) {
            if (lastStatusLoggedElapsedMs > 0L &&
                nowElapsedMs - lastStatusLoggedElapsedMs < MIN_STATUS_LOG_INTERVAL_MS
            ) {
                return
            }
            lastStatusLoggedElapsedMs = nowElapsedMs
        }

        val payload =
            buildString {
                append("status")
                append(" sats=").append(satellites.coerceAtLeast(0))
                append(" used=").append(usedInFix.coerceAtLeast(0))
                append(" cn0Avg=").append(cn0AvgDbHz?.let { "%.1f".format(it) } ?: "na")
                append(" cn0Max=").append(cn0MaxDbHz?.let { "%.1f".format(it) } ?: "na")
                append(" carrier=").append(carrierFrequencySatelliteCount.coerceAtLeast(0))
                append(" l1=").append(l1SatelliteCount.coerceAtLeast(0))
                append(" l5=").append(l5SatelliteCount.coerceAtLeast(0))
                append(" dual=").append(dualBandObserved)
                append(" gps=").append(gpsCount.coerceAtLeast(0))
                append(" gal=").append(galileoCount.coerceAtLeast(0))
                append(" glo=").append(glonassCount.coerceAtLeast(0))
                append(" bds=").append(beidouCount.coerceAtLeast(0))
                append(" qzss=").append(qzssCount.coerceAtLeast(0))
                append(" sbas=").append(sbasCount.coerceAtLeast(0))
                append(" unk=").append(unknownCount.coerceAtLeast(0))
            }
        push(payload)
        DebugTelemetry.log(TAG, payload)
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
}
