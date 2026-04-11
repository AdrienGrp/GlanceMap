package com.glancemap.glancemapwearos.core.service.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class DiagnosticsExporterLogcatTest {
    private val zoneId: ZoneId = ZoneId.of("Europe/Berlin")

    @Test
    fun normalizesThreadtimeLinesToFullDatePrefix() {
        val capturedAt = Instant.parse("2026-03-18T10:15:30Z")
        val normalized =
            normalizeThreadtimeLogcatLine(
                line = "03-15 12:41:56.762  4633  7863 D LocTelemetry: requestUpdates applied",
                capturedAt = capturedAt,
                zoneId = zoneId,
            )

        assertEquals(
            "2026-03-15 12:41:56.762  4633  7863 D LocTelemetry: requestUpdates applied",
            normalized,
        )
    }

    @Test
    fun infersPreviousYearForNewYearBoundary() {
        val capturedAt = Instant.parse("2026-01-01T00:10:00Z")
        val inferred =
            inferThreadtimeDateTime(
                month = 12,
                day = 31,
                time = LocalTime.of(23, 59, 58, 123_000_000),
                capturedAt = capturedAt,
                zoneId = zoneId,
                baseYear = 2026,
            )

        assertEquals("2025-12-31T23:59:58.123", inferred.toString())
    }

    @Test
    fun keepsOriginalLineWhenFormatDoesNotMatchThreadtime() {
        val line = "2026-03-15 12:41:56.762 [LocTelemetry] requestUpdates applied"

        val normalized =
            normalizeThreadtimeLogcatLine(
                line = line,
                capturedAt = Instant.parse("2026-03-18T10:15:30Z"),
                zoneId = zoneId,
            )

        assertEquals(line, normalized)
    }
}
