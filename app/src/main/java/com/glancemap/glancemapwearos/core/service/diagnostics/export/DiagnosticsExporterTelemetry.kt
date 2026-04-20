package com.glancemap.glancemapwearos.core.service.diagnostics

import com.glancemap.glancemapwearos.core.service.diagnostics.DiagnosticsExporter.AcceptedFixSummaries
import com.glancemap.glancemapwearos.core.service.diagnostics.DiagnosticsExporter.AcceptedFixSummary
import com.glancemap.glancemapwearos.core.service.diagnostics.DiagnosticsExporter.GnssInsights
import com.glancemap.glancemapwearos.core.service.diagnostics.DiagnosticsExporter.ObservedFixQualitySummary
import com.glancemap.glancemapwearos.core.service.diagnostics.DiagnosticsExporter.TelemetryInsights
import java.io.BufferedWriter
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val telemetryLineTimestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

private enum class LocationRequestMode {
    BURST,
    STATIONARY_BOUND,
    STATIONARY_BACKGROUND,
    OTHERWISE,
}

private data class ModeSample(
    val atEpochMs: Long,
    val mode: LocationRequestMode,
)

private enum class RequestBackendMode {
    AUTO_FUSED,
    WATCH_GPS,
}

private data class BackendSample(
    val atEpochMs: Long,
    val backend: RequestBackendMode,
)

internal data class TelemetryWindow(
    val lines: List<String>,
    val firstAtMs: Long?,
    val lastAtMs: Long?,
)

private data class ModeDurations(
    val burstMs: Long,
    val stationaryBoundMs: Long,
    val stationaryBackgroundMs: Long,
    val otherwiseMs: Long,
    val coverageMs: Long,
)

private data class BackendDurations(
    val autoFusedMs: Long,
    val watchGpsMs: Long,
    val coverageMs: Long,
    val switchCount: Int,
)

internal fun deriveTelemetryInsights(
    lines: List<String>,
    captureWindowEndEpochMs: Long?,
): TelemetryInsights {
    if (lines.isEmpty()) return TelemetryInsights()

    var burstStartCount = 0
    var burstEndCount = 0
    var availabilityTrueCount = 0
    var availabilityFalseCount = 0
    var availabilityInferredFromFixCount = 0
    var screenResumeCount = 0
    var screenPauseCount = 0
    var ambientEnterCount = 0
    var ambientExitCount = 0
    var trackingEnabledTrueCount = 0
    var trackingEnabledFalseCount = 0
    var trackingDisabledByScreenPauseCount = 0
    var requestAppliedCount = 0
    var requestModeBurstCount = 0
    var requestModeStationaryBoundCount = 0
    var requestModeStationaryBackgroundCount = 0
    var requestModeOtherwiseCount = 0
    var lastObservedBound: Boolean? = null
    var lastObservedTrackingEnabled: Boolean? = null
    var lastObservedKeepOpen: Boolean? = null
    var startupBogusSampleIgnoredCount = 0
    var staleFixDropCount = 0
    var sourceMismatchDropCount = 0
    var gpsFreshTrueCount = 0
    var gpsFreshFalseCount = 0
    var watchGpsDegradedEnteredCount = 0
    var watchGpsDegradedClearedCount = 0
    var watchGpsDegradedSampleCount = 0
    var watchGpsDegradedLastObserved: Boolean? = null
    var batchEventCount = 0
    var batchOriginAutoFusedCount = 0
    var batchOriginWatchGpsCount = 0
    var batchFallbackCount = 0
    var batchDuplicateCandidatesDroppedTotal = 0
    var batchRawCandidatesTotal = 0
    var batchNormalizedCandidatesTotal = 0
    var batchAcceptedCandidatesTotal = 0
    var batchRawCandidatesMax = 0
    var batchNormalizedCandidatesMax = 0
    var callbackAcceptedFixCount = 0
    var immediateAcceptedFixCount = 0
    var acceptedFixOriginAutoFusedCount = 0
    var acceptedFixOriginWatchGpsCount = 0
    var requestBackendAutoFusedCount = 0
    var requestBackendWatchGpsCount = 0
    var failoverAutoToWatchAccuracyCount = 0
    var failoverAutoToWatchNoFixCount = 0
    var failoverWatchToAutoCount = 0
    var failoverClearedTrackingDisabledCount = 0
    var failoverClearedOtherCount = 0
    var fixProviderGpsCount = 0
    var fixProviderFusedCount = 0
    var screenOnFixGapSampleCount = 0
    var screenOnFixGapSumMs = 0L
    var screenOnFixGapMaxMs = 0L
    var screenActive = false
    var pendingScreenPauseTrackingDisable = false
    var lastScreenFixAtMs: Long? = null
    val modeSamples = mutableListOf<ModeSample>()
    val backendSamples = mutableListOf<BackendSample>()
    val requestStopSamples = mutableListOf<Long>()

    lines.forEach { line ->
        val lineEpochMs = parseTelemetryLineEpochMs(line)
        val requestMode = parseRequestMode(line)
        if (requestMode != null) {
            requestAppliedCount += 1
            when (requestMode) {
                LocationRequestMode.BURST -> requestModeBurstCount += 1
                LocationRequestMode.STATIONARY_BOUND -> requestModeStationaryBoundCount += 1
                LocationRequestMode.STATIONARY_BACKGROUND -> requestModeStationaryBackgroundCount += 1
                LocationRequestMode.OTHERWISE -> requestModeOtherwiseCount += 1
            }
            lastObservedBound = parseBooleanToken(line, "bound=") ?: lastObservedBound
            lastObservedTrackingEnabled = parseBooleanToken(line, "trackingEnabled=")
                ?: lastObservedTrackingEnabled
            lastObservedKeepOpen = parseBooleanToken(line, "keepOpen=") ?: lastObservedKeepOpen
            val backendMode = parseBackendMode(extractTokenValue(line, "backend="))
            when (backendMode) {
                RequestBackendMode.AUTO_FUSED -> requestBackendAutoFusedCount += 1
                RequestBackendMode.WATCH_GPS -> requestBackendWatchGpsCount += 1
                null -> Unit
            }

            lineEpochMs?.let { ts ->
                modeSamples += ModeSample(atEpochMs = ts, mode = requestMode)
                if (backendMode != null) {
                    backendSamples += BackendSample(atEpochMs = ts, backend = backendMode)
                }
            }
        }
        if (isRequestStopLine(line)) {
            lineEpochMs?.let { requestStopSamples += it }
            lastObservedBound = parseBooleanToken(line, "bound=") ?: lastObservedBound
            lastObservedTrackingEnabled =
                parseBooleanToken(line, "trackingEnabled=")
                    ?: parseLegacyTrackingEnabled(line)
                    ?: lastObservedTrackingEnabled
            lastObservedKeepOpen = parseBooleanToken(line, "keepOpen=") ?: lastObservedKeepOpen
        }

        if ("locationBatch:" in line) {
            batchEventCount += 1
            val rawCandidates = parseIntToken(line, "raw=") ?: 0
            val normalizedCandidates = parseIntToken(line, "normalized=") ?: 0
            val acceptedCandidates = parseIntToken(line, "accepted=") ?: 0
            val fallback = parseBooleanToken(line, "fallback=") ?: false
            val duplicateCandidatesDropped = parseIntToken(line, "duplicatesDropped=") ?: 0
            if (fallback) {
                batchFallbackCount += 1
            }
            when (extractTokenValue(line, "origin=")) {
                "auto_fused" -> batchOriginAutoFusedCount += 1
                "watch_gps" -> batchOriginWatchGpsCount += 1
            }
            batchDuplicateCandidatesDroppedTotal += duplicateCandidatesDropped
            batchRawCandidatesTotal += rawCandidates
            batchNormalizedCandidatesTotal += normalizedCandidates
            batchAcceptedCandidatesTotal += acceptedCandidates
            batchRawCandidatesMax = maxOf(batchRawCandidatesMax, rawCandidates)
            batchNormalizedCandidatesMax = maxOf(batchNormalizedCandidatesMax, normalizedCandidates)
        }

        if ("fixAccepted: source=" in line) {
            availabilityInferredFromFixCount += 1
            when (extractTokenValue(line, "source=")) {
                "callback" -> callbackAcceptedFixCount += 1
                "immediate" -> immediateAcceptedFixCount += 1
            }
            when (extractTokenValue(line, "origin=")) {
                "auto_fused" -> acceptedFixOriginAutoFusedCount += 1
                "watch_gps" -> acceptedFixOriginWatchGpsCount += 1
            }
            when (extractTokenValue(line, "provider=")?.lowercase()) {
                "gps" -> fixProviderGpsCount += 1
                "fused" -> fixProviderFusedCount += 1
            }
            if (screenActive && lineEpochMs != null) {
                lastScreenFixAtMs?.let { previousFixAtMs ->
                    val gapMs = (lineEpochMs - previousFixAtMs).coerceAtLeast(0L)
                    screenOnFixGapSampleCount += 1
                    screenOnFixGapSumMs += gapMs
                    if (gapMs > screenOnFixGapMaxMs) {
                        screenOnFixGapMaxMs = gapMs
                    }
                }
                lastScreenFixAtMs = lineEpochMs
            }
        }

        if ("gpsSignal: sample" in line) {
            when (extractTokenValue(line, "watchGpsDegraded=")) {
                "true" -> {
                    watchGpsDegradedSampleCount += 1
                    watchGpsDegradedLastObserved = true
                }
                "false" -> watchGpsDegradedLastObserved = false
            }
        }

        when {
            "immediateRequest: burstStart" in line -> burstStartCount += 1
            "immediateRequest: burstEnd" in line -> burstEndCount += 1
            "locationAvailability: available=true" in line -> availabilityTrueCount += 1
            "locationAvailability: available=false" in line -> availabilityFalseCount += 1
            "tracking: enabled=true" in line -> {
                trackingEnabledTrueCount += 1
                pendingScreenPauseTrackingDisable = false
            }
            "tracking: enabled=false" in line -> {
                trackingEnabledFalseCount += 1
                if (pendingScreenPauseTrackingDisable) {
                    trackingDisabledByScreenPauseCount += 1
                    pendingScreenPauseTrackingDisable = false
                }
            }
            "sourceFailover: auto_fused->watch_gps reason=accuracy_plateau" in line -> {
                failoverAutoToWatchAccuracyCount += 1
            }
            "sourceFailover: auto_fused->watch_gps reason=no_fix_gap" in line -> {
                failoverAutoToWatchNoFixCount += 1
            }
            "sourceFailover: watch_gps->auto_fused" in line -> {
                failoverWatchToAutoCount += 1
            }
            "sourceFailover: cleared reason=tracking_disabled" in line -> {
                failoverClearedTrackingDisabledCount += 1
            }
            "sourceFailover: cleared reason=" in line -> {
                failoverClearedOtherCount += 1
            }
            "[ScreenTelemetry] event=activity_resume" in line -> {
                screenResumeCount += 1
                screenActive = true
                pendingScreenPauseTrackingDisable = false
                lastScreenFixAtMs = null
            }
            "[ScreenTelemetry] event=activity_pause" in line -> {
                screenPauseCount += 1
                screenActive = false
                pendingScreenPauseTrackingDisable = true
                lastScreenFixAtMs = null
            }
            "[ScreenTelemetry] event=ambient_enter" in line -> ambientEnterCount += 1
            "[ScreenTelemetry] event=ambient_exit" in line -> ambientExitCount += 1
            "startup_bogus_sample ignored" in line -> startupBogusSampleIgnoredCount += 1
            "staleFix: dropped" in line -> staleFixDropCount += 1
            "sourceMismatch: dropped" in line -> sourceMismatchDropCount += 1
            "gpsSignal: sample" in line && "fresh=true" in line -> gpsFreshTrueCount += 1
            "gpsSignal: sample" in line && "fresh=false" in line -> gpsFreshFalseCount += 1
            "watchGpsDegraded: state=entered" in line -> {
                watchGpsDegradedEnteredCount += 1
                watchGpsDegradedLastObserved = true
            }
            "watchGpsDegraded: state=cleared" in line -> {
                watchGpsDegradedClearedCount += 1
                watchGpsDegradedLastObserved = false
            }
        }
    }

    val modeDurations =
        accumulateModeDurations(
            samples = modeSamples,
            requestStopSamples = requestStopSamples,
            captureWindowEndEpochMs = captureWindowEndEpochMs,
        )
    val backendDurations =
        accumulateBackendDurations(
            samples = backendSamples,
            requestStopSamples = requestStopSamples,
            captureWindowEndEpochMs = captureWindowEndEpochMs,
        )

    return TelemetryInsights(
        burstStartCount = burstStartCount,
        burstEndCount = burstEndCount,
        availabilityTrueCount = availabilityTrueCount,
        availabilityFalseCount = availabilityFalseCount,
        availabilityInferredFromFixCount = availabilityInferredFromFixCount,
        screenResumeCount = screenResumeCount,
        screenPauseCount = screenPauseCount,
        ambientEnterCount = ambientEnterCount,
        ambientExitCount = ambientExitCount,
        trackingEnabledTrueCount = trackingEnabledTrueCount,
        trackingEnabledFalseCount = trackingEnabledFalseCount,
        trackingDisabledByScreenPauseCount = trackingDisabledByScreenPauseCount,
        requestAppliedCount = requestAppliedCount,
        requestModeBurstCount = requestModeBurstCount,
        requestModeStationaryBoundCount = requestModeStationaryBoundCount,
        requestModeStationaryBackgroundCount = requestModeStationaryBackgroundCount,
        requestModeOtherwiseCount = requestModeOtherwiseCount,
        requestModeBurstDurationMs = modeDurations.burstMs,
        requestModeStationaryBoundDurationMs = modeDurations.stationaryBoundMs,
        requestModeStationaryBackgroundDurationMs = modeDurations.stationaryBackgroundMs,
        requestModeOtherwiseDurationMs = modeDurations.otherwiseMs,
        requestModeDurationCoverageMs = modeDurations.coverageMs,
        lastObservedBound = lastObservedBound,
        lastObservedTrackingEnabled = lastObservedTrackingEnabled,
        lastObservedKeepOpen = lastObservedKeepOpen,
        startupBogusSampleIgnoredCount = startupBogusSampleIgnoredCount,
        staleFixDropCount = staleFixDropCount,
        sourceMismatchDropCount = sourceMismatchDropCount,
        gpsFreshTrueCount = gpsFreshTrueCount,
        gpsFreshFalseCount = gpsFreshFalseCount,
        watchGpsDegradedEnteredCount = watchGpsDegradedEnteredCount,
        watchGpsDegradedClearedCount = watchGpsDegradedClearedCount,
        watchGpsDegradedSampleCount = watchGpsDegradedSampleCount,
        watchGpsDegradedLastObserved = watchGpsDegradedLastObserved,
        batchEventCount = batchEventCount,
        batchOriginAutoFusedCount = batchOriginAutoFusedCount,
        batchOriginWatchGpsCount = batchOriginWatchGpsCount,
        batchFallbackCount = batchFallbackCount,
        batchDuplicateCandidatesDroppedTotal = batchDuplicateCandidatesDroppedTotal,
        batchRawCandidatesTotal = batchRawCandidatesTotal,
        batchNormalizedCandidatesTotal = batchNormalizedCandidatesTotal,
        batchAcceptedCandidatesTotal = batchAcceptedCandidatesTotal,
        batchRawCandidatesMax = batchRawCandidatesMax,
        batchNormalizedCandidatesMax = batchNormalizedCandidatesMax,
        callbackAcceptedFixCount = callbackAcceptedFixCount,
        immediateAcceptedFixCount = immediateAcceptedFixCount,
        acceptedFixOriginAutoFusedCount = acceptedFixOriginAutoFusedCount,
        acceptedFixOriginWatchGpsCount = acceptedFixOriginWatchGpsCount,
        requestBackendAutoFusedCount = requestBackendAutoFusedCount,
        requestBackendWatchGpsCount = requestBackendWatchGpsCount,
        requestBackendSwitchCount = backendDurations.switchCount,
        requestBackendAutoFusedDurationMs = backendDurations.autoFusedMs,
        requestBackendWatchGpsDurationMs = backendDurations.watchGpsMs,
        requestBackendDurationCoverageMs = backendDurations.coverageMs,
        failoverAutoToWatchAccuracyCount = failoverAutoToWatchAccuracyCount,
        failoverAutoToWatchNoFixCount = failoverAutoToWatchNoFixCount,
        failoverWatchToAutoCount = failoverWatchToAutoCount,
        failoverClearedTrackingDisabledCount = failoverClearedTrackingDisabledCount,
        failoverClearedOtherCount = failoverClearedOtherCount,
        fixProviderGpsCount = fixProviderGpsCount,
        fixProviderFusedCount = fixProviderFusedCount,
        screenOnFixGapSampleCount = screenOnFixGapSampleCount,
        screenOnFixGapAvgMs =
            if (screenOnFixGapSampleCount > 0) {
                screenOnFixGapSumMs / screenOnFixGapSampleCount
            } else {
                null
            },
        screenOnFixGapMaxMs = screenOnFixGapMaxMs,
    )
}

internal fun resolveCaptureWindowEndEpochMs(
    captureSession: DebugTelemetry.CaptureSessionSnapshot,
    exportNowEpochMs: Long,
): Long? =
    captureSession.endedAtMs
        ?: if (captureSession.active) exportNowEpochMs else null

internal fun toTelemetryWindow(
    lines: List<String>,
    startEpochMs: Long?,
    endEpochMs: Long?,
): TelemetryWindow {
    if (lines.isEmpty()) {
        return TelemetryWindow(lines = emptyList(), firstAtMs = null, lastAtMs = null)
    }
    if (startEpochMs == null) {
        return TelemetryWindow(
            lines = lines,
            firstAtMs = parseTelemetryLineEpochMs(lines.first()),
            lastAtMs = parseTelemetryLineEpochMs(lines.last()),
        )
    }

    val filtered =
        lines.filter { line ->
            val ts = parseTelemetryLineEpochMs(line) ?: return@filter false
            val afterStart = ts >= startEpochMs
            val beforeEnd = endEpochMs?.let { ts <= it } ?: true
            afterStart && beforeEnd
        }

    val firstAtMs = filtered.firstOrNull()?.let(::parseTelemetryLineEpochMs)
    val lastAtMs = filtered.lastOrNull()?.let(::parseTelemetryLineEpochMs)
    return TelemetryWindow(lines = filtered, firstAtMs = firstAtMs, lastAtMs = lastAtMs)
}

private fun parseRequestMode(line: String): LocationRequestMode? {
    if ("requestUpdates applied:" !in line && "reason=gps_request_applied" !in line) return null

    val mode = extractTokenValue(line, "mode=")?.uppercase()
    if (mode != null) {
        return when (mode) {
            "BURST" -> LocationRequestMode.BURST
            "PASSIVE" -> LocationRequestMode.STATIONARY_BACKGROUND
            "INTERACTIVE" -> LocationRequestMode.OTHERWISE
            else -> null
        }
    }

    val burst = extractTokenValue(line, "burst=")?.toBooleanStrictOrNull() ?: false
    if (burst) return LocationRequestMode.BURST

    val state = extractTokenValue(line, "state=")
    val bound = extractTokenValue(line, "bound=")?.toBooleanStrictOrNull() ?: false
    return if (state == "STATIONARY" && bound) {
        LocationRequestMode.STATIONARY_BOUND
    } else if (state == "STATIONARY") {
        LocationRequestMode.STATIONARY_BACKGROUND
    } else {
        LocationRequestMode.OTHERWISE
    }
}

private fun isRequestStopLine(line: String): Boolean {
    if ("requestUpdates cleared:" in line || "reason=gps_request_cleared" in line) return true
    if ("tracking: enabled=false" in line) return true
    if ("runtimeState:" !in line) return false

    val trackingEnabled = parseBooleanToken(line, "trackingEnabled=")
    if (trackingEnabled == false) return true

    val screenState = extractTokenValue(line, "screenState=")
    val backgroundGpsEnabled = parseBooleanToken(line, "backgroundGpsEnabled=")
    return screenState in setOf("SCREEN_OFF", "AMBIENT") && backgroundGpsEnabled == false
}

private fun parseLegacyTrackingEnabled(line: String): Boolean? =
    when {
        "tracking: enabled=true" in line -> true
        "tracking: enabled=false" in line -> false
        else -> null
    }

private fun parseBackendMode(token: String?): RequestBackendMode? =
    when (token?.lowercase()) {
        "auto_fused" -> RequestBackendMode.AUTO_FUSED
        "watch_gps" -> RequestBackendMode.WATCH_GPS
        else -> null
    }

private fun parseTelemetryLineEpochMs(line: String): Long? {
    val separatorIndex = line.indexOf(" [")
    if (separatorIndex <= 0) return null
    val timestampText = line.substring(0, separatorIndex).trim()
    return runCatching {
        val localDateTime = LocalDateTime.parse(timestampText, telemetryLineTimestampFormatter)
        localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
}

private fun extractTokenValue(
    line: String,
    key: String,
): String? {
    val index = line.indexOf(key)
    if (index < 0) return null
    val start = index + key.length
    if (start >= line.length) return null
    val end = line.indexOf(' ', start).let { if (it < 0) line.length else it }
    return line.substring(start, end).trim()
}

private fun parseBooleanToken(
    line: String,
    key: String,
): Boolean? = extractTokenValue(line, key)?.toBooleanStrictOrNull()

private fun parseIntToken(
    line: String,
    key: String,
): Int? = extractTokenValue(line, key)?.toIntOrNull()

private fun parseFloatToken(
    line: String,
    key: String,
): Float? = extractTokenValue(line, key)?.toFloatOrNull()

internal fun formatBooleanToken(value: Boolean?): String = value?.toString() ?: "na"

internal fun formatAverage(
    total: Int,
    count: Int,
): String {
    if (count <= 0) return "na"
    return "%.2f".format(total.toDouble() / count.toDouble())
}

internal fun formatRatePercent(
    numerator: Int,
    denominator: Int,
): String {
    if (denominator <= 0) return "na"
    val pct = numerator.toDouble() * 100.0 / denominator.toDouble()
    return "%.2f".format(pct)
}

private fun formatOneDecimalOrNa(value: Number?): String = value?.let { String.format(Locale.US, "%.1f", it.toDouble()) } ?: "na"

internal fun writeAcceptedFixQualitySection(
    writer: BufferedWriter,
    prefix: String,
    summary: AcceptedFixSummary,
    quality: ObservedFixQualitySummary,
) {
    writer.appendLine("${prefix}AcceptedFixCount=${summary.acceptedFixCount}")
    writer.appendLine("${prefix}CallbackFixCount=${summary.callbackFixCount}")
    writer.appendLine("${prefix}ImmediateFixCount=${summary.immediateFixCount}")
    writer.appendLine("${prefix}ProviderGpsCount=${summary.providerGpsCount}")
    writer.appendLine("${prefix}ProviderFusedCount=${summary.providerFusedCount}")
    writer.appendLine("${prefix}ReportedAccuracyMedianM=${formatOneDecimalOrNa(summary.reportedAccuracyMedianM)}")
    writer.appendLine("${prefix}ReportedAccuracyP90M=${formatOneDecimalOrNa(summary.reportedAccuracyP90M)}")
    writer.appendLine("${prefix}ReportedAccuracyMinM=${formatOneDecimalOrNa(summary.reportedAccuracyMinM)}")
    writer.appendLine("${prefix}ReportedAccuracyMaxM=${formatOneDecimalOrNa(summary.reportedAccuracyMaxM)}")
    writer.appendLine("${prefix}ReportedAccuracyDistinctCount=${summary.reportedAccuracyDistinctCount}")
    writer.appendLine("${prefix}ReportedAccuracyAllSame=${summary.reportedAccuracyAllSame}")
    writer.appendLine("${prefix}AcceptedFixAgeMedianMs=${summary.ageMedianMs?.toString() ?: "na"}")
    writer.appendLine("${prefix}AcceptedFixAgeP90Ms=${summary.ageP90Ms?.toString() ?: "na"}")
    writer.appendLine("${prefix}AcceptedFixAgeMaxMs=${summary.ageMaxMs?.toString() ?: "na"}")
    writer.appendLine("${prefix}ReportedAccuracyReliability=${quality.reportedAccuracyReliability}")
    writer.appendLine("${prefix}ObservedFixQuality=${quality.quality}")
    writer.appendLine("${prefix}ObservedFixQualityConfidence=${quality.confidence}")
    writer.appendLine("${prefix}ObservedFixQualityReason=${quality.reason}")
}

internal fun deriveAcceptedFixSummariesFromLines(lines: List<String>): AcceptedFixSummaries =
    AcceptedFixSummaries(
        overall = summarizeAcceptedFixes(lines = lines, originFilter = null),
        autoFused = summarizeAcceptedFixes(lines = lines, originFilter = "auto_fused"),
        watchGps = summarizeAcceptedFixes(lines = lines, originFilter = "watch_gps"),
    )

private fun summarizeAcceptedFixes(
    lines: List<String>,
    originFilter: String?,
): AcceptedFixSummary {
    val relevantLines =
        lines.filter { line ->
            "fixAccepted: source=" in line &&
                (originFilter == null || extractTokenValue(line, "origin=") == originFilter)
        }
    if (relevantLines.isEmpty()) return AcceptedFixSummary()

    val accuracies = mutableListOf<Float>()
    val ages = mutableListOf<Long>()
    var callbackFixCount = 0
    var immediateFixCount = 0
    var providerGpsCount = 0
    var providerFusedCount = 0

    relevantLines.forEach { line ->
        when (extractTokenValue(line, "source=")) {
            "callback" -> callbackFixCount += 1
            "immediate" -> immediateFixCount += 1
        }
        when (extractTokenValue(line, "provider=")?.lowercase()) {
            "gps" -> providerGpsCount += 1
            "fused" -> providerFusedCount += 1
        }
        extractTokenValue(line, "accuracyM=")?.toFloatOrNull()?.takeIf { it.isFinite() }?.let { accuracies += it }
        extractTokenValue(line, "ageMs=")?.toLongOrNull()?.takeIf { it >= 0L }?.let { ages += it }
    }

    val sortedAccuracies = accuracies.sorted()
    val sortedAges = ages.sorted()
    return AcceptedFixSummary(
        acceptedFixCount = relevantLines.size,
        callbackFixCount = callbackFixCount,
        immediateFixCount = immediateFixCount,
        providerGpsCount = providerGpsCount,
        providerFusedCount = providerFusedCount,
        reportedAccuracyMedianM = percentileFloat(sortedAccuracies, 0.5),
        reportedAccuracyP90M = percentileFloat(sortedAccuracies, 0.9),
        reportedAccuracyMinM = sortedAccuracies.firstOrNull(),
        reportedAccuracyMaxM = sortedAccuracies.lastOrNull(),
        reportedAccuracyDistinctCount = sortedAccuracies.distinct().size,
        reportedAccuracyAllSame = sortedAccuracies.isNotEmpty() && sortedAccuracies.first() == sortedAccuracies.last(),
        ageMedianMs = percentileLong(sortedAges, 0.5),
        ageP90Ms = percentileLong(sortedAges, 0.9),
        ageMaxMs = sortedAges.lastOrNull(),
    )
}

internal fun inferObservedFixQualityFromSummary(
    summary: AcceptedFixSummary,
    origin: String?,
    gnssInsights: GnssInsights,
): ObservedFixQualitySummary {
    if (summary.acceptedFixCount <= 0) {
        return ObservedFixQualitySummary()
    }

    val reportedAccuracyReliability =
        when {
            origin == "watch_gps" &&
                summary.reportedAccuracyAllSame &&
                summary.reportedAccuracyMedianM == 125f -> "suspect_constant_watch_gps"
            summary.reportedAccuracyAllSame && summary.acceptedFixCount >= 3 -> "suspect_constant"
            summary.reportedAccuracyDistinctCount <= 1 -> "low_variation"
            else -> "variable"
        }

    var score = 0
    summary.ageP90Ms?.let { ageP90 ->
        when {
            ageP90 <= 100L -> score += 2
            ageP90 <= 250L -> score += 1
        }
    }
    summary.ageMaxMs?.let { ageMax ->
        when {
            ageMax <= 250L -> score += 2
            ageMax <= 1_000L -> score += 1
        }
    }
    if (summary.acceptedFixCount >= 5) {
        score += 1
    }

    if (origin == "watch_gps") {
        when {
            gnssInsights.statusSampleCount >= 3 &&
                gnssInsights.usedInFixAvg >= 12.0 &&
                (gnssInsights.cn0AvgDbHz ?: 0.0) >= 20.0 -> score += 2
            gnssInsights.firstFixCount > 0 && gnssInsights.usedInFixAvg >= 6.0 -> score += 1
        }
    }

    val quality =
        when {
            score >= 6 -> "good"
            score >= 3 -> "moderate"
            else -> "weak"
        }
    val confidence =
        when {
            origin == "watch_gps" && summary.acceptedFixCount >= 5 && gnssInsights.statusSampleCount >= 3 -> "high"
            summary.acceptedFixCount >= 3 -> "medium"
            else -> "low"
        }

    return ObservedFixQualitySummary(
        quality = quality,
        confidence = confidence,
        reportedAccuracyReliability = reportedAccuracyReliability,
        reason =
            buildObservedFixQualityReason(
                summary = summary,
                origin = origin,
                gnssInsights = gnssInsights,
                reportedAccuracyReliability = reportedAccuracyReliability,
            ),
    )
}

private fun buildObservedFixQualityReason(
    summary: AcceptedFixSummary,
    origin: String?,
    gnssInsights: GnssInsights,
    reportedAccuracyReliability: String,
): String {
    val freshness =
        buildString {
            append("fresh accepted fixes")
            summary.ageP90Ms?.let { append(" p90AgeMs=").append(it) }
            summary.ageMaxMs?.let { append(" maxAgeMs=").append(it) }
        }
    if (origin != "watch_gps") {
        return freshness
    }
    val gnssSupport =
        buildString {
            append("gnss usedInFixAvg=").append("%.1f".format(gnssInsights.usedInFixAvg))
            append(" cn0AvgDbHz=").append(gnssInsights.cn0AvgDbHz?.let { "%.1f".format(it) } ?: "na")
            append(" firstFixCount=").append(gnssInsights.firstFixCount)
        }
    return if (reportedAccuracyReliability == "suspect_constant_watch_gps") {
        "$freshness with $gnssSupport despite constant reported accuracy"
    } else {
        "$freshness with $gnssSupport"
    }
}

private fun percentileFloat(
    sortedValues: List<Float>,
    fraction: Double,
): Float? {
    if (sortedValues.isEmpty()) return null
    val index = ((sortedValues.lastIndex) * fraction).toInt().coerceIn(0, sortedValues.lastIndex)
    return sortedValues[index]
}

private fun percentileLong(
    sortedValues: List<Long>,
    fraction: Double,
): Long? {
    if (sortedValues.isEmpty()) return null
    val index = ((sortedValues.lastIndex) * fraction).toInt().coerceIn(0, sortedValues.lastIndex)
    return sortedValues[index]
}

internal fun deriveGnssInsights(lines: List<String>): GnssInsights {
    if (lines.isEmpty()) return GnssInsights()

    var statusSampleCount = 0
    var startedCount = 0
    var stoppedCount = 0
    var firstFixCount = 0

    var firstFixTtffTotalMs = 0L
    var firstFixTtffMinMs = Int.MAX_VALUE
    var firstFixTtffMaxMs = 0

    var satellitesTotal = 0L
    var satellitesMax = 0
    var usedInFixTotal = 0L
    var usedInFixMax = 0

    var cn0SampleCount = 0
    var cn0Total = 0.0
    var cn0Max: Float? = null
    var carrierFrequencyStatusCount = 0
    var l1ObservedStatusCount = 0
    var l5ObservedStatusCount = 0
    var dualBandObservedStatusCount = 0
    var l1SatelliteMax = 0
    var l5SatelliteMax = 0

    lines.forEach { line ->
        when {
            " event=started" in line -> startedCount += 1
            " event=stopped" in line -> stoppedCount += 1
            " event=first_fix" in line -> {
                firstFixCount += 1
                val ttffMs = parseIntToken(line, "ttffMs=")
                if (ttffMs != null && ttffMs >= 0) {
                    firstFixTtffTotalMs += ttffMs.toLong()
                    if (ttffMs < firstFixTtffMinMs) firstFixTtffMinMs = ttffMs
                    if (ttffMs > firstFixTtffMaxMs) firstFixTtffMaxMs = ttffMs
                }
            }
            " status " in line || line.endsWith(" status") -> {
                val sats = parseIntToken(line, "sats=") ?: 0
                val used = parseIntToken(line, "used=") ?: 0
                statusSampleCount += 1
                satellitesTotal += sats.toLong()
                usedInFixTotal += used.toLong()
                if (sats > satellitesMax) satellitesMax = sats
                if (used > usedInFixMax) usedInFixMax = used

                val cn0Avg = parseFloatToken(line, "cn0Avg=")
                if (cn0Avg != null && cn0Avg.isFinite()) {
                    cn0SampleCount += 1
                    cn0Total += cn0Avg.toDouble()
                }
                val cn0LineMax = parseFloatToken(line, "cn0Max=")
                if (cn0LineMax != null && cn0LineMax.isFinite()) {
                    cn0Max = maxOf(cn0Max ?: cn0LineMax, cn0LineMax)
                }
                val carrierSatellites = parseIntToken(line, "carrier=") ?: 0
                if (carrierSatellites > 0) {
                    carrierFrequencyStatusCount += 1
                }
                val l1Satellites = parseIntToken(line, "l1=") ?: 0
                if (l1Satellites > 0) {
                    l1ObservedStatusCount += 1
                }
                if (l1Satellites > l1SatelliteMax) {
                    l1SatelliteMax = l1Satellites
                }
                val l5Satellites = parseIntToken(line, "l5=") ?: 0
                if (l5Satellites > 0) {
                    l5ObservedStatusCount += 1
                }
                if (l5Satellites > l5SatelliteMax) {
                    l5SatelliteMax = l5Satellites
                }
                if (parseBooleanToken(line, "dual=") == true) {
                    dualBandObservedStatusCount += 1
                }
            }
        }
    }

    val firstFixTtffAvgMs =
        if (firstFixCount > 0) {
            (firstFixTtffTotalMs / firstFixCount).coerceAtLeast(0L)
        } else {
            0L
        }
    val satellitesAvg =
        if (statusSampleCount > 0) {
            satellitesTotal.toDouble() / statusSampleCount.toDouble()
        } else {
            0.0
        }
    val usedInFixAvg =
        if (statusSampleCount > 0) {
            usedInFixTotal.toDouble() / statusSampleCount.toDouble()
        } else {
            0.0
        }
    val cn0AvgDbHz =
        if (cn0SampleCount > 0) {
            cn0Total / cn0SampleCount.toDouble()
        } else {
            null
        }

    return GnssInsights(
        statusSampleCount = statusSampleCount,
        startedCount = startedCount,
        stoppedCount = stoppedCount,
        firstFixCount = firstFixCount,
        firstFixTtffAvgMs = firstFixTtffAvgMs,
        firstFixTtffMinMs =
            if (firstFixCount > 0 && firstFixTtffMinMs != Int.MAX_VALUE) {
                firstFixTtffMinMs
            } else {
                0
            },
        firstFixTtffMaxMs = if (firstFixCount > 0) firstFixTtffMaxMs else 0,
        satellitesAvg = satellitesAvg,
        satellitesMax = satellitesMax,
        usedInFixAvg = usedInFixAvg,
        usedInFixMax = usedInFixMax,
        cn0AvgDbHz = cn0AvgDbHz,
        cn0MaxDbHz = cn0Max,
        carrierFrequencyStatusCount = carrierFrequencyStatusCount,
        l1ObservedStatusCount = l1ObservedStatusCount,
        l5ObservedStatusCount = l5ObservedStatusCount,
        dualBandObservedStatusCount = dualBandObservedStatusCount,
        l1SatelliteMax = l1SatelliteMax,
        l5SatelliteMax = l5SatelliteMax,
    )
}

private fun accumulateModeDurations(
    samples: List<ModeSample>,
    requestStopSamples: List<Long>,
    captureWindowEndEpochMs: Long?,
): ModeDurations {
    if (samples.isEmpty()) {
        return ModeDurations(
            burstMs = 0L,
            stationaryBoundMs = 0L,
            stationaryBackgroundMs = 0L,
            otherwiseMs = 0L,
            coverageMs = 0L,
        )
    }

    var burstMs = 0L
    var stationaryBoundMs = 0L
    var stationaryBackgroundMs = 0L
    var otherwiseMs = 0L
    val sortedStopSamples = requestStopSamples.sorted()

    for (index in samples.indices) {
        val current = samples[index]
        val nextSampleAtMs =
            if (index < samples.lastIndex) {
                samples[index + 1].atEpochMs
            } else {
                captureWindowEndEpochMs ?: current.atEpochMs
            }
        val nextAtMs =
            firstRequestStopBetween(
                sortedStopSamples = sortedStopSamples,
                currentAtMs = current.atEpochMs,
                nextSampleAtMs = nextSampleAtMs,
            ) ?: nextSampleAtMs
        val deltaMs = (nextAtMs - current.atEpochMs).coerceAtLeast(0L)
        when (current.mode) {
            LocationRequestMode.BURST -> burstMs += deltaMs
            LocationRequestMode.STATIONARY_BOUND -> stationaryBoundMs += deltaMs
            LocationRequestMode.STATIONARY_BACKGROUND -> stationaryBackgroundMs += deltaMs
            LocationRequestMode.OTHERWISE -> otherwiseMs += deltaMs
        }
    }

    return ModeDurations(
        burstMs = burstMs,
        stationaryBoundMs = stationaryBoundMs,
        stationaryBackgroundMs = stationaryBackgroundMs,
        otherwiseMs = otherwiseMs,
        coverageMs = burstMs + stationaryBoundMs + stationaryBackgroundMs + otherwiseMs,
    )
}

private fun accumulateBackendDurations(
    samples: List<BackendSample>,
    requestStopSamples: List<Long>,
    captureWindowEndEpochMs: Long?,
): BackendDurations {
    if (samples.isEmpty()) {
        return BackendDurations(
            autoFusedMs = 0L,
            watchGpsMs = 0L,
            coverageMs = 0L,
            switchCount = 0,
        )
    }

    var autoFusedMs = 0L
    var watchGpsMs = 0L
    var switchCount = 0
    val sortedStopSamples = requestStopSamples.sorted()

    for (index in samples.indices) {
        val current = samples[index]
        if (index > 0 && samples[index - 1].backend != current.backend) {
            switchCount += 1
        }
        val nextSampleAtMs =
            if (index < samples.lastIndex) {
                samples[index + 1].atEpochMs
            } else {
                captureWindowEndEpochMs ?: current.atEpochMs
            }
        val nextAtMs =
            firstRequestStopBetween(
                sortedStopSamples = sortedStopSamples,
                currentAtMs = current.atEpochMs,
                nextSampleAtMs = nextSampleAtMs,
            ) ?: nextSampleAtMs
        val deltaMs = (nextAtMs - current.atEpochMs).coerceAtLeast(0L)
        when (current.backend) {
            RequestBackendMode.AUTO_FUSED -> autoFusedMs += deltaMs
            RequestBackendMode.WATCH_GPS -> watchGpsMs += deltaMs
        }
    }

    return BackendDurations(
        autoFusedMs = autoFusedMs,
        watchGpsMs = watchGpsMs,
        coverageMs = autoFusedMs + watchGpsMs,
        switchCount = switchCount,
    )
}

private fun firstRequestStopBetween(
    sortedStopSamples: List<Long>,
    currentAtMs: Long,
    nextSampleAtMs: Long,
): Long? =
    sortedStopSamples.firstOrNull { stopAtMs ->
        stopAtMs > currentAtMs && stopAtMs < nextSampleAtMs
    }
