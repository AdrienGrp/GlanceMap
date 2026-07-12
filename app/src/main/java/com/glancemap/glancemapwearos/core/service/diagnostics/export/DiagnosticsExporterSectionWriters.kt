package com.glancemap.glancemapwearos.core.service.diagnostics.export

import com.glancemap.glancemapwearos.core.service.diagnostics.DemDownloadSummary
import com.glancemap.glancemapwearos.core.service.diagnostics.DiagnosticsExporter
import com.glancemap.glancemapwearos.core.service.diagnostics.EnergyDiagnostics
import com.glancemap.glancemapwearos.core.service.diagnostics.ScreenStateDiagnostics
import com.glancemap.glancemapwearos.core.service.diagnostics.TelemetryFormatters

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
    writeBatteryConsumptionSummary(energySummary.batteryUse)
    appendLine()
    appendLine("Energy By Mode Summary")
    if (energySummary.modes.isEmpty()) {
        appendLine("No energy diagnostics samples yet.")
    } else {
        energySummary.modes.forEach { (mode, stats) ->
            writeEnergyModeStats(mode, stats)
        }
    }
}

internal fun Appendable.writeScreenStateSummarySection(summary: ScreenStateDiagnostics.Summary) {
    appendLine()
    appendLine("Screen State Summary")
    appendLine("captureDurationMs=${summary.captureDurationMs}")
    appendLine("interactiveDurationMs=${summary.interactiveDurationMs}")
    appendLine("ambientDurationMs=${summary.ambientDurationMs}")
    appendLine("screenOffDurationMs=${summary.offDurationMs}")
    appendLine("appForegroundDurationMs=${summary.appForegroundDurationMs}")
    appendLine("displayTransitionCount=${summary.displayTransitionCount}")
    appendLine("appForegroundTransitionCount=${summary.appForegroundTransitionCount}")
    appendLine("currentDisplayState=${summary.currentDisplayState?.name ?: "na"}")
    appendLine("currentAppForeground=${summary.currentAppForeground?.toString() ?: "na"}")
    appendLine("openIntervalsIncluded=${summary.openIntervalsIncluded}")
}

private fun Appendable.writeBatteryConsumptionSummary(batteryUse: EnergyDiagnostics.BatteryUseStats?) {
    appendLine("Battery Consumption Summary")
    batteryUse?.let {
        appendLine("batteryUsedMah=${TelemetryFormatters.decimal(batteryUse.consumedMah, 2)}")
        appendLine("averageDrawMa=${TelemetryFormatters.decimal(batteryUse.averageDrawMa, 1)}")
        appendLine("durationMs=${batteryUse.durationMs}")
        appendLine("measurement=${batteryUse.measurement}")
        appendLine("confidence=${batteryUse.confidence}")
        appendLine("medianDrawMa=${TelemetryFormatters.decimalOrNa(batteryUse.medianDrawMa, 1)}")
        appendLine("p90DrawMa=${TelemetryFormatters.decimalOrNa(batteryUse.p90DrawMa, 1)}")
        appendLine("integratedCurrentMah=${TelemetryFormatters.decimalOrNa(batteryUse.integratedCurrentMah, 2)}")
        appendLine("chargeCounterStartUah=${batteryUse.chargeCounterStartUah?.toString() ?: "na"}")
        appendLine("chargeCounterEndUah=${batteryUse.chargeCounterEndUah?.toString() ?: "na"}")
    } ?: appendLine("No complete unplugged battery measurement yet.")
}

private fun Appendable.writeEnergyModeStats(
    mode: String,
    stats: EnergyDiagnostics.ModeStats,
) = appendLine(
    "mode[$mode]=samples=${stats.sampleCount} currentSamples=${stats.currentSampleCount} " +
        "avgCurNowUa=${stats.avgCurrentNowUa?.toString() ?: "na"} " +
        "medianAbsCurNowUa=${stats.medianAbsCurrentNowUa?.toString() ?: "na"} " +
        "medianAbsCurNowMa=${
            stats.medianAbsCurrentNowUa?.let { TelemetryFormatters.decimal(it / 1_000.0, 1) } ?: "na"
        } " +
        "minCurNowUa=${stats.minCurrentNowUa?.toString() ?: "na"} " +
        "maxCurNowUa=${stats.maxCurrentNowUa?.toString() ?: "na"} " +
        "levelMin=${stats.minLevelPct?.toString() ?: "na"} " +
        "levelMax=${stats.maxLevelPct?.toString() ?: "na"} " +
        "levelAvg=${TelemetryFormatters.decimalOrNa(stats.avgLevelPct, 1)} " +
        "tempMinC=${TelemetryFormatters.decimalOrNa(stats.minTempC, 1)} " +
        "tempMaxC=${TelemetryFormatters.decimalOrNa(stats.maxTempC, 1)} " +
        "tempAvgC=${TelemetryFormatters.decimalOrNa(stats.avgTempC, 1)}",
)

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
    appendLine("satellitesAvg=${if (gnssInsights.statusSampleCount > 0) TelemetryFormatters.decimal(gnssInsights.satellitesAvg, 2) else "na"}")
    appendLine("satellitesMax=${if (gnssInsights.statusSampleCount > 0) gnssInsights.satellitesMax else "na"}")
    appendLine("usedInFixAvg=${if (gnssInsights.statusSampleCount > 0) TelemetryFormatters.decimal(gnssInsights.usedInFixAvg, 2) else "na"}")
    appendLine("usedInFixMax=${if (gnssInsights.statusSampleCount > 0) gnssInsights.usedInFixMax else "na"}")
    appendLine("cn0AvgDbHz=${TelemetryFormatters.decimalOrNa(gnssInsights.cn0AvgDbHz, 2)}")
    appendLine("cn0MaxDbHz=${TelemetryFormatters.decimalOrNa(gnssInsights.cn0MaxDbHz, 1)}")
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
