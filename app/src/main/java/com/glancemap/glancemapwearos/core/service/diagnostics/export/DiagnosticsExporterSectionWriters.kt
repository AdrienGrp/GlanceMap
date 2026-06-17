package com.glancemap.glancemapwearos.core.service.diagnostics.export

import com.glancemap.glancemapwearos.core.service.diagnostics.DemDownloadSummary
import com.glancemap.glancemapwearos.core.service.diagnostics.DiagnosticsExporter
import com.glancemap.glancemapwearos.core.service.diagnostics.EnergyDiagnostics

internal fun Appendable.writeLineDumpSection(
    title: String,
    emptyMessage: String,
    lines: List<String>,
) {
    appendLine()
    appendLine(title)
    if (lines.isEmpty()) {
        appendLine(emptyMessage)
    } else {
        lines.forEach { line -> appendLine(line) }
    }
}

internal fun Appendable.writeEnergyByModeSummarySection(energySummary: EnergyDiagnostics.Summary) {
    appendLine()
    appendLine("Energy By Mode Summary")
    if (energySummary.modes.isEmpty()) {
        appendLine("No energy diagnostics samples yet.")
    } else {
        energySummary.modes.forEach { (mode, stats) ->
            appendLine(
                "mode[$mode]=samples=${stats.sampleCount} currentSamples=${stats.currentSampleCount} " +
                    "avgCurNowUa=${stats.avgCurrentNowUa?.toString() ?: "na"} " +
                    "minCurNowUa=${stats.minCurrentNowUa?.toString() ?: "na"} " +
                    "maxCurNowUa=${stats.maxCurrentNowUa?.toString() ?: "na"} " +
                    "levelMin=${stats.minLevelPct?.toString() ?: "na"} " +
                    "levelMax=${stats.maxLevelPct?.toString() ?: "na"} " +
                    "levelAvg=${formatOneDecimal(stats.avgLevelPct)} " +
                    "tempMinC=${formatOneDecimal(stats.minTempC)} " +
                    "tempMaxC=${formatOneDecimal(stats.maxTempC)} " +
                    "tempAvgC=${formatOneDecimal(stats.avgTempC)}",
            )
        }
    }
}

internal fun Appendable.writeDemDownloadSections(
    demDownloadSummary: DemDownloadSummary,
    demDownloadLines: List<String>,
    demDownloadTruncated: Boolean,
) {
    appendLine()
    appendLine("DEM Download Summary")
    appendLine("eventCount=${demDownloadSummary.eventCount}")
    appendLine("bufferMaxLines=${demDownloadSummary.maxBufferedLines}")
    appendLine("droppedLines=${demDownloadSummary.droppedLineCount}")
    appendLine("truncated=$demDownloadTruncated")
    appendLine("startedCount=${demDownloadSummary.startedCount}")
    appendLine("completedCount=${demDownloadSummary.completedCount}")
    appendLine("downloadedCount=${demDownloadSummary.downloadedCount}")
    appendLine("skippedCount=${demDownloadSummary.skippedCount}")
    appendLine("missingCount=${demDownloadSummary.missingCount}")
    appendLine("failedCount=${demDownloadSummary.failedCount}")
    appendLine("resumeAttemptCount=${demDownloadSummary.resumeAttemptCount}")
    appendLine("resumeRestartCount=${demDownloadSummary.resumeRestartCount}")
    appendLine("validationFailureCount=${demDownloadSummary.validationFailureCount}")
    appendLine("networkUnavailableCount=${demDownloadSummary.networkUnavailableCount}")
    appendLine("activityState=${demDownloadSummary.activityState}")
    appendLine("diagnosticContext=${demDownloadSummary.diagnosticContext}")
    writeLineDumpSection(
        title = "DEM Download Events",
        emptyMessage = "No DEM download events captured yet.",
        lines = demDownloadLines,
    )
}

internal fun Appendable.writeGnssSections(
    gnssInsights: DiagnosticsExporter.GnssInsights,
    gnssLines: List<String>,
) {
    appendLine()
    appendLine("GNSS Summary")
    appendLine("statusSampleCount=${gnssInsights.statusSampleCount}")
    appendLine("startedCount=${gnssInsights.startedCount}")
    appendLine("stoppedCount=${gnssInsights.stoppedCount}")
    appendLine("firstFixCount=${gnssInsights.firstFixCount}")
    appendLine("firstFixTtffAvgMs=${if (gnssInsights.firstFixCount > 0) gnssInsights.firstFixTtffAvgMs else "na"}")
    appendLine("firstFixTtffMinMs=${if (gnssInsights.firstFixCount > 0) gnssInsights.firstFixTtffMinMs else "na"}")
    appendLine("firstFixTtffMaxMs=${if (gnssInsights.firstFixCount > 0) gnssInsights.firstFixTtffMaxMs else "na"}")
    appendLine("satellitesAvg=${if (gnssInsights.statusSampleCount > 0) "%.2f".format(gnssInsights.satellitesAvg) else "na"}")
    appendLine("satellitesMax=${if (gnssInsights.statusSampleCount > 0) gnssInsights.satellitesMax else "na"}")
    appendLine("usedInFixAvg=${if (gnssInsights.statusSampleCount > 0) "%.2f".format(gnssInsights.usedInFixAvg) else "na"}")
    appendLine("usedInFixMax=${if (gnssInsights.statusSampleCount > 0) gnssInsights.usedInFixMax else "na"}")
    appendLine("cn0AvgDbHz=${gnssInsights.cn0AvgDbHz?.let { "%.2f".format(it) } ?: "na"}")
    appendLine("cn0MaxDbHz=${gnssInsights.cn0MaxDbHz?.let { "%.1f".format(it) } ?: "na"}")
    appendLine("carrierFrequencyStatusCount=${gnssInsights.carrierFrequencyStatusCount}")
    appendLine("l1ObservedStatusCount=${gnssInsights.l1ObservedStatusCount}")
    appendLine("l5ObservedStatusCount=${gnssInsights.l5ObservedStatusCount}")
    appendLine("dualBandObservedStatusCount=${gnssInsights.dualBandObservedStatusCount}")
    appendLine("collectorRegisteredCount=${gnssInsights.collectorRegisteredCount}")
    appendLine("collectorUnregisteredCount=${gnssInsights.collectorUnregisteredCount}")
    appendLine("collectorInactiveCount=${gnssInsights.collectorInactiveCount}")
    appendLine("collectorPolicyDisabledCount=${gnssInsights.collectorPolicyDisabledCount}")
    appendLine("l1SatelliteMax=${if (gnssInsights.statusSampleCount > 0) gnssInsights.l1SatelliteMax else "na"}")
    appendLine("l5SatelliteMax=${if (gnssInsights.statusSampleCount > 0) gnssInsights.l5SatelliteMax else "na"}")
    writeLineDumpSection(
        title = "GNSS Events",
        emptyMessage = "No GNSS diagnostics samples captured yet.",
        lines = gnssLines,
    )
}

private fun formatOneDecimal(value: Double?): String =
    value?.let { "%.1f".format(it) } ?: "na"
