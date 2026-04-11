package com.glancemap.glancemapwearos.core.service.diagnostics

import android.content.Context
import android.os.Build
import com.glancemap.glancemapwearos.core.service.location.config.ENABLE_STRICT_FIX_FILTERING
import com.glancemap.glancemapwearos.presentation.features.maps.MapRenderer
import com.glancemap.glancemapwearos.presentation.features.navigate.motion.FusionReplayTelemetry
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val threadtimeLineRegex =
    Regex(
        "^(\\d{2})-(\\d{2}) (\\d{2}:\\d{2}:\\d{2}\\.\\d{3})(\\s+.*)$",
    )
private val threadtimeTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
private val normalizedLogcatTimestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
private val skippedFramesRegex = Regex("""Skipped (\d+) frames!""")
private val daveyDurationRegex = Regex("""Davey!\s+duration=(\d+)ms""")
private val gcDurationRegex = Regex("""total ([0-9]+(?:\.[0-9]+)?)(ms|s)""")
private val gcFreedRegex = Regex("""freed ([0-9]+)(KB|MB|GB)""")

data class DiagnosticsSettingsSnapshot(
    val gpsIntervalMs: Long,
    val watchGpsOnly: Boolean,
    val keepAppOpen: Boolean,
    val gpsInAmbientMode: Boolean,
    val gpsDebugTelemetry: Boolean,
)

object DiagnosticsExporter {
    private const val SESSION_DURATION_MISMATCH_THRESHOLD_MS = 5_000L

    internal data class TelemetryInsights(
        val burstStartCount: Int = 0,
        val burstEndCount: Int = 0,
        val availabilityTrueCount: Int = 0,
        val availabilityFalseCount: Int = 0,
        val availabilityInferredFromFixCount: Int = 0,
        val screenResumeCount: Int = 0,
        val screenPauseCount: Int = 0,
        val ambientEnterCount: Int = 0,
        val ambientExitCount: Int = 0,
        val trackingEnabledTrueCount: Int = 0,
        val trackingEnabledFalseCount: Int = 0,
        val trackingDisabledByScreenPauseCount: Int = 0,
        val requestAppliedCount: Int = 0,
        val requestModeBurstCount: Int = 0,
        val requestModeStationaryBoundCount: Int = 0,
        val requestModeStationaryBackgroundCount: Int = 0,
        val requestModeOtherwiseCount: Int = 0,
        val requestModeBurstDurationMs: Long = 0L,
        val requestModeStationaryBoundDurationMs: Long = 0L,
        val requestModeStationaryBackgroundDurationMs: Long = 0L,
        val requestModeOtherwiseDurationMs: Long = 0L,
        val requestModeDurationCoverageMs: Long = 0L,
        val lastObservedBound: Boolean? = null,
        val lastObservedTrackingEnabled: Boolean? = null,
        val lastObservedKeepOpen: Boolean? = null,
        val startupBogusSampleIgnoredCount: Int = 0,
        val staleFixDropCount: Int = 0,
        val sourceMismatchDropCount: Int = 0,
        val gpsFreshTrueCount: Int = 0,
        val gpsFreshFalseCount: Int = 0,
        val watchGpsDegradedEnteredCount: Int = 0,
        val watchGpsDegradedClearedCount: Int = 0,
        val watchGpsDegradedSampleCount: Int = 0,
        val watchGpsDegradedLastObserved: Boolean? = null,
        val batchEventCount: Int = 0,
        val batchOriginAutoFusedCount: Int = 0,
        val batchOriginWatchGpsCount: Int = 0,
        val batchFallbackCount: Int = 0,
        val batchDuplicateCandidatesDroppedTotal: Int = 0,
        val batchRawCandidatesTotal: Int = 0,
        val batchNormalizedCandidatesTotal: Int = 0,
        val batchAcceptedCandidatesTotal: Int = 0,
        val batchRawCandidatesMax: Int = 0,
        val batchNormalizedCandidatesMax: Int = 0,
        val callbackAcceptedFixCount: Int = 0,
        val immediateAcceptedFixCount: Int = 0,
        val acceptedFixOriginAutoFusedCount: Int = 0,
        val acceptedFixOriginWatchGpsCount: Int = 0,
        val requestBackendAutoFusedCount: Int = 0,
        val requestBackendWatchGpsCount: Int = 0,
        val requestBackendSwitchCount: Int = 0,
        val requestBackendAutoFusedDurationMs: Long = 0L,
        val requestBackendWatchGpsDurationMs: Long = 0L,
        val requestBackendDurationCoverageMs: Long = 0L,
        val failoverAutoToWatchAccuracyCount: Int = 0,
        val failoverAutoToWatchNoFixCount: Int = 0,
        val failoverWatchToAutoCount: Int = 0,
        val failoverClearedTrackingDisabledCount: Int = 0,
        val failoverClearedOtherCount: Int = 0,
        val fixProviderGpsCount: Int = 0,
        val fixProviderFusedCount: Int = 0,
        val screenOnFixGapSampleCount: Int = 0,
        val screenOnFixGapAvgMs: Long? = null,
        val screenOnFixGapMaxMs: Long = 0L,
    )

    internal data class GnssInsights(
        val statusSampleCount: Int = 0,
        val startedCount: Int = 0,
        val stoppedCount: Int = 0,
        val firstFixCount: Int = 0,
        val firstFixTtffAvgMs: Long = 0L,
        val firstFixTtffMinMs: Int = 0,
        val firstFixTtffMaxMs: Int = 0,
        val satellitesAvg: Double = 0.0,
        val satellitesMax: Int = 0,
        val usedInFixAvg: Double = 0.0,
        val usedInFixMax: Int = 0,
        val cn0AvgDbHz: Double? = null,
        val cn0MaxDbHz: Float? = null,
        val carrierFrequencyStatusCount: Int = 0,
        val l1ObservedStatusCount: Int = 0,
        val l5ObservedStatusCount: Int = 0,
        val dualBandObservedStatusCount: Int = 0,
        val l1SatelliteMax: Int = 0,
        val l5SatelliteMax: Int = 0,
    )

    internal data class AcceptedFixSummary(
        val acceptedFixCount: Int = 0,
        val callbackFixCount: Int = 0,
        val immediateFixCount: Int = 0,
        val providerGpsCount: Int = 0,
        val providerFusedCount: Int = 0,
        val reportedAccuracyMedianM: Float? = null,
        val reportedAccuracyP90M: Float? = null,
        val reportedAccuracyMinM: Float? = null,
        val reportedAccuracyMaxM: Float? = null,
        val reportedAccuracyDistinctCount: Int = 0,
        val reportedAccuracyAllSame: Boolean = false,
        val ageMedianMs: Long? = null,
        val ageP90Ms: Long? = null,
        val ageMaxMs: Long? = null,
    )

    internal data class AcceptedFixSummaries(
        val overall: AcceptedFixSummary = AcceptedFixSummary(),
        val autoFused: AcceptedFixSummary = AcceptedFixSummary(),
        val watchGps: AcceptedFixSummary = AcceptedFixSummary(),
    )

    internal data class ObservedFixQualitySummary(
        val quality: String = "unknown",
        val confidence: String = "low",
        val reportedAccuracyReliability: String = "unknown",
        val reason: String = "no accepted fixes",
    )

    private val filenameFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault())
    private val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault())
    private val telemetryLineTimestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    fun export(
        context: Context,
        settings: DiagnosticsSettingsSnapshot,
        reuseLatestIfAvailable: Boolean = false,
    ): File {
        val now = Instant.now()
        val dir = diagnosticsDir(context)
        if (reuseLatestIfAvailable) {
            latestExportFile(context)?.let { return it }
        }

        val allTelemetryLines = DebugTelemetry.snapshot()
        val captureSession = DebugTelemetry.captureSessionSnapshot()
        val file =
            File(
                dir,
                buildString {
                    append("glancemap_diagnostics_")
                    append(filenameFormatter.format(now))
                    append('_')
                    append(buildDeviceSlug())
                    captureSession.sessionId.takeIf { it > 0L }?.let {
                        append("_s")
                        append(it)
                    }
                    append(".txt")
                },
            )
        val captureWindowEndEpochMs = resolveCaptureWindowEndEpochMs(captureSession, now.toEpochMilli())
        val telemetryWindow =
            toTelemetryWindow(
                lines = allTelemetryLines,
                startEpochMs = captureSession.startedAtMs,
                endEpochMs = captureWindowEndEpochMs,
            )
        val telemetryLines = telemetryWindow.lines
        val telemetryInsights =
            deriveTelemetryInsights(
                lines = telemetryLines,
                captureWindowEndEpochMs = captureWindowEndEpochMs,
            )
        val acceptedFixSummaries = deriveAcceptedFixSummaries(telemetryLines)
        val captureDurationMs =
            captureDurationMs(
                startedAtMs = captureSession.startedAtMs,
                endedAtMs = captureSession.endedAtMs,
                active = captureSession.active,
            )
        val bufferedSpanMs =
            bufferedSpanMs(
                firstBufferedAtMs = telemetryWindow.firstAtMs,
                lastBufferedAtMs = telemetryWindow.lastAtMs,
            )
        val sessionVsBufferedMismatch =
            captureDurationMs != null &&
                bufferedSpanMs != null &&
                !captureSession.active &&
                kotlin.math.abs(captureDurationMs - bufferedSpanMs) > SESSION_DURATION_MISMATCH_THRESHOLD_MS
        val energyLines = EnergyDiagnostics.snapshotLines()
        val energyDroppedLines = EnergyDiagnostics.droppedLineCount()
        val fusionReplaySummary = FusionReplayTelemetry.summary()
        val fusionReplayLines = FusionReplayTelemetry.snapshotLines()
        val fusionReplayDroppedLines = FusionReplayTelemetry.droppedLineCount()
        val mapHotPathSummary = MapHotPathDiagnostics.summary()
        val mapHotPathLines = MapHotPathDiagnostics.snapshotLines()
        val mapHotPathDroppedLines = MapHotPathDiagnostics.droppedLineCount()
        val gnssLines = GnssDiagnostics.snapshotLines()
        val gnssDroppedLines = GnssDiagnostics.droppedLineCount()
        val gnssInsights = deriveGnssInsights(gnssLines)
        val fieldMarkerLines = FieldMarkerDiagnostics.snapshotLines()
        val fieldMarkerDroppedLines = FieldMarkerDiagnostics.droppedLineCount()
        val telemetryTruncated = captureSession.droppedLines > 0
        val energyTruncated = energyDroppedLines > 0
        val fusionReplayTruncated = fusionReplayDroppedLines > 0
        val mapHotPathTruncated = mapHotPathDroppedLines > 0
        val gnssTruncated = gnssDroppedLines > 0
        val fieldMarkerTruncated = fieldMarkerDroppedLines > 0
        val lastCrash = CrashDiagnosticsStore.read(context)
        val logcatSnapshot = captureAppLogcat(capturedAt = now)
        val performanceSummary = summarizePerformanceFromLogcat(logcatSnapshot.lines)
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val appInfo = context.applicationInfo
        val sensorInventory = captureSensorInventory(context)
        val locationPermission = captureLocationPermissionSnapshot(context)
        val memorySnapshot = captureMemorySnapshot(context)
        val cacheSnapshot = MapRenderer.captureCacheDiagnostics(context)
        val historicalExitReasons = captureHistoricalProcessExitReasons(context)

        file.bufferedWriter().use { writer ->
            writer.appendLine("GlanceMap Wear Diagnostics")
            writer.appendLine("Generated: ${timestampFormatter.format(now)}")
            writer.appendLine()
            writer.appendLine("App")
            writer.appendLine("Package: ${context.packageName}")
            writer.appendLine("VersionName: ${packageInfo.versionName}")
            writer.appendLine("VersionCode: ${packageInfo.longVersionCode}")
            writer.appendLine("TargetSdk: ${appInfo.targetSdkVersion}")
            writer.appendLine("FirstInstall: ${formatInstallTime(packageInfo.firstInstallTime)}")
            writer.appendLine("LastUpdate: ${formatInstallTime(packageInfo.lastUpdateTime)}")
            writer.appendLine()
            writer.appendLine("Device")
            writer.appendLine("Manufacturer: ${Build.MANUFACTURER}")
            writer.appendLine("Model: ${Build.MODEL}")
            writer.appendLine("SDK: ${Build.VERSION.SDK_INT}")
            writer.appendLine()
            writer.appendLine("Memory Snapshot")
            writer.appendLine("runtimeMaxHeapMb=${formatBytesToMb(memorySnapshot.runtimeMaxHeapBytes)}")
            writer.appendLine("runtimeTotalHeapMb=${formatBytesToMb(memorySnapshot.runtimeTotalHeapBytes)}")
            writer.appendLine("runtimeUsedHeapMb=${formatBytesToMb(memorySnapshot.runtimeUsedHeapBytes)}")
            writer.appendLine("runtimeFreeHeapMb=${formatBytesToMb(memorySnapshot.runtimeFreeHeapBytes)}")
            writer.appendLine("nativeHeapSizeMb=${formatBytesToMb(memorySnapshot.nativeHeapSizeBytes)}")
            writer.appendLine("nativeHeapAllocatedMb=${formatBytesToMb(memorySnapshot.nativeHeapAllocatedBytes)}")
            writer.appendLine("nativeHeapFreeMb=${formatBytesToMb(memorySnapshot.nativeHeapFreeBytes)}")
            writer.appendLine("memoryClassMb=${memorySnapshot.memoryClassMb?.toString() ?: "na"}")
            writer.appendLine("largeMemoryClassMb=${memorySnapshot.largeMemoryClassMb?.toString() ?: "na"}")
            writer.appendLine("processTotalPssKb=${memorySnapshot.totalPssKb?.toString() ?: "na"}")
            writer.appendLine("processDalvikPssKb=${memorySnapshot.dalvikPssKb?.toString() ?: "na"}")
            writer.appendLine("processNativePssKb=${memorySnapshot.nativePssKb?.toString() ?: "na"}")
            writer.appendLine("processOtherPssKb=${memorySnapshot.otherPssKb?.toString() ?: "na"}")
            writer.appendLine("processTotalPrivateDirtyKb=${memorySnapshot.totalPrivateDirtyKb?.toString() ?: "na"}")
            writer.appendLine("processTotalSharedDirtyKb=${memorySnapshot.totalSharedDirtyKb?.toString() ?: "na"}")
            writer.appendLine("systemAvailMemMb=${formatNullableBytesToMb(memorySnapshot.availMemBytes)}")
            writer.appendLine("systemTotalMemMb=${formatNullableBytesToMb(memorySnapshot.totalMemBytes)}")
            writer.appendLine("systemLowMemory=${formatNullableBoolean(memorySnapshot.lowMemory)}")
            writer.appendLine("systemLowMemoryThresholdMb=${formatNullableBytesToMb(memorySnapshot.thresholdBytes)}")
            writer.appendLine()
            writer.appendLine("Cache Summary")
            writer.appendLine("activeTileCacheId=${cacheSnapshot.activeTileCacheId ?: "na"}")
            writer.appendLine("activeTileCacheLastUsedAt=${formatCaptureTime(cacheSnapshot.activeTileCacheLastUsedMs)}")
            writer.appendLine(
                "activeTileCacheLastUsedAgeMs=${
                    formatAgeMs(
                        nowMs = now.toEpochMilli(),
                        pastMs = cacheSnapshot.activeTileCacheLastUsedMs,
                    )
                }",
            )
            writer.appendLine("tileCacheBucketCount=${cacheSnapshot.tileCacheBucketCount}")
            writer.appendLine("tileCacheTotalSizeMb=${formatBytesToMb(cacheSnapshot.tileCacheTotalSizeBytes)}")
            writer.appendLine("activeTileCacheSizeMb=${formatNullableBytesToMb(cacheSnapshot.activeTileCacheSizeBytes)}")
            writer.appendLine("cacheLastCleanupAt=${formatCaptureTime(cacheSnapshot.lastCleanupMs)}")
            writer.appendLine(
                "cacheLastCleanupAgeMs=${
                    formatAgeMs(
                        nowMs = now.toEpochMilli(),
                        pastMs = cacheSnapshot.lastCleanupMs,
                    )
                }",
            )
            writer.appendLine("reliefOverlayNamespaceCount=${cacheSnapshot.reliefOverlayNamespaceCount}")
            writer.appendLine("reliefOverlayCacheSizeMb=${formatBytesToMb(cacheSnapshot.reliefOverlayCacheSizeBytes)}")
            writer.appendLine("bundledThemeCacheDirCount=${cacheSnapshot.bundledThemeCacheDirCount}")
            writer.appendLine("bundledThemeCacheTotalSizeMb=${formatBytesToMb(cacheSnapshot.bundledThemeCacheTotalSizeBytes)}")
            writer.appendLine()
            writer.appendLine("Sensor Inventory")
            writer.appendLine("headingPublicApiSupported=${sensorInventory.headingPublicApiSupported}")
            writer.appendLine("typeHeadingAvailable=${sensorInventory.headingAvailable}")
            writer.appendLine("rotationVectorAvailable=${sensorInventory.rotationVectorAvailable}")
            writer.appendLine("magnetometerAvailable=${sensorInventory.magnetometerAvailable}")
            writer.appendLine("accelerometerAvailable=${sensorInventory.accelerometerAvailable}")
            writer.appendLine("typeHeading=${formatSensorDescriptor(sensorInventory.headingSensor)}")
            writer.appendLine("rotationVector=${formatSensorDescriptor(sensorInventory.rotationVectorSensor)}")
            writer.appendLine("magnetometer=${formatSensorDescriptor(sensorInventory.magnetometerSensor)}")
            writer.appendLine("accelerometer=${formatSensorDescriptor(sensorInventory.accelerometerSensor)}")
            writer.appendLine("allSensorCount=${sensorInventory.allSensors.size}")
            if (sensorInventory.allSensors.isEmpty()) {
                writer.appendLine("No public sensors reported by SensorManager.")
            } else {
                sensorInventory.allSensors.forEachIndexed { index, sensor ->
                    writer.appendLine("sensor[$index]=${formatSensorDescriptor(sensor)}")
                }
            }
            writer.appendLine()
            writer.appendLine("GPS Settings")
            writer.appendLine("gpsIntervalMs=${settings.gpsIntervalMs}")
            writer.appendLine("watchGpsOnly=${settings.watchGpsOnly}")
            writer.appendLine("keepAppOpen=${settings.keepAppOpen}")
            writer.appendLine("gpsInAmbientMode=${settings.gpsInAmbientMode}")
            writer.appendLine("gpsDebugTelemetry=${settings.gpsDebugTelemetry}")
            writer.appendLine("locationFinePermissionGranted=${locationPermission.hasFinePermission}")
            writer.appendLine("locationCoarsePermissionGranted=${locationPermission.hasCoarsePermission}")
            writer.appendLine("locationPermissionMode=${locationPermission.mode}")
            writer.appendLine("gpsPositionFilterEnabled=$ENABLE_STRICT_FIX_FILTERING")
            writer.appendLine()
            writer.appendLine("Capture Session")
            writer.appendLine("sessionId=${captureSessionIdText(captureSession.sessionId)}")
            writer.appendLine("activeAtExport=${captureSession.active}")
            writer.appendLine("startedAt=${formatCaptureTime(captureSession.startedAtMs)}")
            writer.appendLine("endedAt=${formatCaptureEndTime(captureSession.endedAtMs, captureSession.active)}")
            writer.appendLine(
                "durationMs=${
                    formatCaptureDurationMs(
                        startedAtMs = captureSession.startedAtMs,
                        endedAtMs = captureSession.endedAtMs,
                        active = captureSession.active,
                    )
                }",
            )
            writer.appendLine("telemetryTotalLoggedLines=${captureSession.totalLoggedLines}")
            writer.appendLine("telemetryBufferedLines=${telemetryLines.size}")
            writer.appendLine("telemetryBufferMaxLines=${DebugTelemetry.maxBufferedLines()}")
            writer.appendLine("telemetryDroppedLines=${captureSession.droppedLines}")
            writer.appendLine("telemetryTruncated=$telemetryTruncated")
            writer.appendLine("telemetryBufferedFirstAt=${formatCaptureTime(telemetryWindow.firstAtMs)}")
            writer.appendLine("telemetryBufferedLastAt=${formatCaptureTime(telemetryWindow.lastAtMs)}")
            writer.appendLine("telemetryBufferedSpanMs=${formatBufferedSpanMs(telemetryWindow.firstAtMs, telemetryWindow.lastAtMs)}")
            writer.appendLine("energyBufferedLines=${energyLines.size}")
            writer.appendLine("energyBufferMaxLines=${EnergyDiagnostics.maxBufferedLines()}")
            writer.appendLine("energyDroppedLines=$energyDroppedLines")
            writer.appendLine("energyTruncated=$energyTruncated")
            writer.appendLine("fusionReplayBufferedLines=${fusionReplayLines.size}")
            writer.appendLine("fusionReplayBufferMaxLines=${FusionReplayTelemetry.maxBufferedLines()}")
            writer.appendLine("fusionReplayDroppedLines=$fusionReplayDroppedLines")
            writer.appendLine("fusionReplayTruncated=$fusionReplayTruncated")
            writer.appendLine("mapHotPathBufferedLines=${mapHotPathLines.size}")
            writer.appendLine("mapHotPathBufferMaxLines=${MapHotPathDiagnostics.maxBufferedLines()}")
            writer.appendLine("mapHotPathDroppedLines=$mapHotPathDroppedLines")
            writer.appendLine("mapHotPathTruncated=$mapHotPathTruncated")
            writer.appendLine("gnssBufferedLines=${gnssLines.size}")
            writer.appendLine("gnssBufferMaxLines=${GnssDiagnostics.maxBufferedLines()}")
            writer.appendLine("gnssDroppedLines=$gnssDroppedLines")
            writer.appendLine("gnssTruncated=$gnssTruncated")
            writer.appendLine("fieldMarkerBufferedLines=${fieldMarkerLines.size}")
            writer.appendLine("fieldMarkerBufferMaxLines=${FieldMarkerDiagnostics.maxBufferedLines()}")
            writer.appendLine("fieldMarkerDroppedLines=$fieldMarkerDroppedLines")
            writer.appendLine("fieldMarkerTruncated=$fieldMarkerTruncated")
            writer.appendLine(
                "anyCaptureBufferTruncated=${
                    telemetryTruncated ||
                        energyTruncated ||
                        fusionReplayTruncated ||
                        mapHotPathTruncated ||
                        gnssTruncated ||
                        fieldMarkerTruncated
                }",
            )
            writer.appendLine()
            writer.appendLine("Telemetry Integrity")
            writer.appendLine("burstStartCount=${telemetryInsights.burstStartCount}")
            writer.appendLine("burstEndCount=${telemetryInsights.burstEndCount}")
            writer.appendLine(
                "burstStartsWithoutEnd=${
                    (telemetryInsights.burstStartCount - telemetryInsights.burstEndCount).coerceAtLeast(0)
                }",
            )
            writer.appendLine("availabilityTrueCount=${telemetryInsights.availabilityTrueCount}")
            writer.appendLine("availabilityFalseCount=${telemetryInsights.availabilityFalseCount}")
            writer.appendLine(
                "availabilityEventCount=${telemetryInsights.availabilityTrueCount + telemetryInsights.availabilityFalseCount}",
            )
            writer.appendLine(
                "availabilityInferredFromFixCount=${telemetryInsights.availabilityInferredFromFixCount}",
            )
            writer.appendLine("screenResumeCount=${telemetryInsights.screenResumeCount}")
            writer.appendLine("screenPauseCount=${telemetryInsights.screenPauseCount}")
            writer.appendLine("ambientEnterCount=${telemetryInsights.ambientEnterCount}")
            writer.appendLine("ambientExitCount=${telemetryInsights.ambientExitCount}")
            writer.appendLine("trackingEnabledTrueCount=${telemetryInsights.trackingEnabledTrueCount}")
            writer.appendLine("trackingEnabledFalseCount=${telemetryInsights.trackingEnabledFalseCount}")
            writer.appendLine(
                "trackingDisabledByScreenPauseCount=${telemetryInsights.trackingDisabledByScreenPauseCount}",
            )
            writer.appendLine("requestUpdatesAppliedCount=${telemetryInsights.requestAppliedCount}")
            writer.appendLine("requestModeBurstCount=${telemetryInsights.requestModeBurstCount}")
            writer.appendLine("requestModeStationaryBoundCount=${telemetryInsights.requestModeStationaryBoundCount}")
            writer.appendLine("requestModeStationaryBackgroundCount=${telemetryInsights.requestModeStationaryBackgroundCount}")
            writer.appendLine("requestModeOtherwiseCount=${telemetryInsights.requestModeOtherwiseCount}")
            writer.appendLine("requestBackendAutoFusedCount=${telemetryInsights.requestBackendAutoFusedCount}")
            writer.appendLine("requestBackendWatchGpsCount=${telemetryInsights.requestBackendWatchGpsCount}")
            writer.appendLine("requestBackendSwitchCount=${telemetryInsights.requestBackendSwitchCount}")
            writer.appendLine(
                "requestBackendAutoFusedDurationMs=${telemetryInsights.requestBackendAutoFusedDurationMs}",
            )
            writer.appendLine(
                "requestBackendWatchGpsDurationMs=${telemetryInsights.requestBackendWatchGpsDurationMs}",
            )
            writer.appendLine(
                "requestBackendDurationCoverageMs=${telemetryInsights.requestBackendDurationCoverageMs}",
            )
            writer.appendLine("requestModeBurstDurationMs=${telemetryInsights.requestModeBurstDurationMs}")
            writer.appendLine("requestModeStationaryBoundDurationMs=${telemetryInsights.requestModeStationaryBoundDurationMs}")
            writer.appendLine("requestModeStationaryBackgroundDurationMs=${telemetryInsights.requestModeStationaryBackgroundDurationMs}")
            writer.appendLine("requestModeOtherwiseDurationMs=${telemetryInsights.requestModeOtherwiseDurationMs}")
            writer.appendLine("requestModeDurationCoverageMs=${telemetryInsights.requestModeDurationCoverageMs}")
            writer.appendLine("foregroundPinnedSetting=${settings.keepAppOpen}")
            writer.appendLine(
                "foregroundPinnedLastObserved=${
                    formatBooleanToken(telemetryInsights.lastObservedKeepOpen)
                }",
            )
            writer.appendLine("boundLastObserved=${formatBooleanToken(telemetryInsights.lastObservedBound)}")
            writer.appendLine(
                "trackingEnabledLastObserved=${
                    formatBooleanToken(telemetryInsights.lastObservedTrackingEnabled)
                }",
            )
            val gpsTrackingExpectedLastObserved =
                telemetryInsights.lastObservedTrackingEnabled
                    ?: telemetryInsights.lastObservedBound?.let { bound ->
                        bound || settings.gpsInAmbientMode
                    }
            writer.appendLine(
                "gpsTrackingExpectedByPolicyLastObserved=${
                    formatBooleanToken(gpsTrackingExpectedLastObserved)
                }",
            )
            writer.appendLine(
                "startupBogusSampleIgnoredCount=${telemetryInsights.startupBogusSampleIgnoredCount}",
            )
            writer.appendLine("staleFixDropCount=${telemetryInsights.staleFixDropCount}")
            writer.appendLine("sourceMismatchDropCount=${telemetryInsights.sourceMismatchDropCount}")
            writer.appendLine(
                "failoverAutoToWatchAccuracyCount=${telemetryInsights.failoverAutoToWatchAccuracyCount}",
            )
            writer.appendLine(
                "failoverAutoToWatchNoFixCount=${telemetryInsights.failoverAutoToWatchNoFixCount}",
            )
            writer.appendLine("failoverWatchToAutoCount=${telemetryInsights.failoverWatchToAutoCount}")
            writer.appendLine(
                "failoverClearedTrackingDisabledCount=${telemetryInsights.failoverClearedTrackingDisabledCount}",
            )
            writer.appendLine(
                "failoverClearedOtherCount=${telemetryInsights.failoverClearedOtherCount}",
            )
            writer.appendLine("gpsFreshTrueCount=${telemetryInsights.gpsFreshTrueCount}")
            writer.appendLine("gpsFreshFalseCount=${telemetryInsights.gpsFreshFalseCount}")
            writer.appendLine(
                "watchGpsDegradedEnteredCount=${telemetryInsights.watchGpsDegradedEnteredCount}",
            )
            writer.appendLine(
                "watchGpsDegradedClearedCount=${telemetryInsights.watchGpsDegradedClearedCount}",
            )
            writer.appendLine(
                "watchGpsDegradedSampleCount=${telemetryInsights.watchGpsDegradedSampleCount}",
            )
            writer.appendLine(
                "watchGpsDegradedLastObserved=${
                    formatBooleanToken(telemetryInsights.watchGpsDegradedLastObserved)
                }",
            )
            writer.appendLine("batchEventCount=${telemetryInsights.batchEventCount}")
            writer.appendLine("batchOriginAutoFusedCount=${telemetryInsights.batchOriginAutoFusedCount}")
            writer.appendLine("batchOriginWatchGpsCount=${telemetryInsights.batchOriginWatchGpsCount}")
            writer.appendLine("batchFallbackCount=${telemetryInsights.batchFallbackCount}")
            writer.appendLine(
                "batchDuplicateCandidatesDroppedTotal=${telemetryInsights.batchDuplicateCandidatesDroppedTotal}",
            )
            writer.appendLine("batchRawCandidatesTotal=${telemetryInsights.batchRawCandidatesTotal}")
            writer.appendLine("batchNormalizedCandidatesTotal=${telemetryInsights.batchNormalizedCandidatesTotal}")
            writer.appendLine("batchAcceptedCandidatesTotal=${telemetryInsights.batchAcceptedCandidatesTotal}")
            writer.appendLine("batchRawCandidatesMax=${telemetryInsights.batchRawCandidatesMax}")
            writer.appendLine("batchNormalizedCandidatesMax=${telemetryInsights.batchNormalizedCandidatesMax}")
            writer.appendLine("callbackAcceptedFixCount=${telemetryInsights.callbackAcceptedFixCount}")
            writer.appendLine("immediateAcceptedFixCount=${telemetryInsights.immediateAcceptedFixCount}")
            writer.appendLine(
                "acceptedFixOriginAutoFusedCount=${telemetryInsights.acceptedFixOriginAutoFusedCount}",
            )
            writer.appendLine(
                "acceptedFixOriginWatchGpsCount=${telemetryInsights.acceptedFixOriginWatchGpsCount}",
            )
            writer.appendLine("fixProviderGpsCount=${telemetryInsights.fixProviderGpsCount}")
            writer.appendLine("fixProviderFusedCount=${telemetryInsights.fixProviderFusedCount}")
            writer.appendLine("screenOnFixGapSampleCount=${telemetryInsights.screenOnFixGapSampleCount}")
            writer.appendLine(
                "screenOnFixGapAvgMs=${
                    telemetryInsights.screenOnFixGapAvgMs?.toString() ?: "na"
                }",
            )
            writer.appendLine(
                "screenOnFixGapMaxMs=${
                    if (telemetryInsights.screenOnFixGapSampleCount > 0) {
                        telemetryInsights.screenOnFixGapMaxMs.toString()
                    } else {
                        "na"
                    }
                }",
            )
            writer.appendLine(
                "batchAvgRawCandidates=${
                    formatAverage(
                        total = telemetryInsights.batchRawCandidatesTotal,
                        count = telemetryInsights.batchEventCount,
                    )
                }",
            )
            writer.appendLine(
                "batchAvgAcceptedCandidates=${
                    formatAverage(
                        total = telemetryInsights.batchAcceptedCandidatesTotal,
                        count = telemetryInsights.batchEventCount,
                    )
                }",
            )
            writer.appendLine(
                "batchAcceptanceRatePct=${
                    formatRatePercent(
                        numerator = telemetryInsights.batchAcceptedCandidatesTotal,
                        denominator = telemetryInsights.batchNormalizedCandidatesTotal,
                    )
                }",
            )
            writer.appendLine("sessionDurationMs=${captureDurationMs?.toString() ?: "na"}")
            writer.appendLine("bufferedSpanMs=${bufferedSpanMs?.toString() ?: "na"}")
            writer.appendLine("sessionVsBufferedMismatch=$sessionVsBufferedMismatch")
            writer.appendLine()
            writer.appendLine("Reported Vs Observed Fix Quality")
            writeAcceptedFixQualitySection(
                writer = writer,
                prefix = "overall",
                summary = acceptedFixSummaries.overall,
                quality =
                    inferObservedFixQuality(
                        summary = acceptedFixSummaries.overall,
                        origin = null,
                        gnssInsights = gnssInsights,
                    ),
            )
            writeAcceptedFixQualitySection(
                writer = writer,
                prefix = "autoFused",
                summary = acceptedFixSummaries.autoFused,
                quality =
                    inferObservedFixQuality(
                        summary = acceptedFixSummaries.autoFused,
                        origin = "auto_fused",
                        gnssInsights = gnssInsights,
                    ),
            )
            writeAcceptedFixQualitySection(
                writer = writer,
                prefix = "watchGps",
                summary = acceptedFixSummaries.watchGps,
                quality =
                    inferObservedFixQuality(
                        summary = acceptedFixSummaries.watchGps,
                        origin = "watch_gps",
                        gnssInsights = gnssInsights,
                    ),
            )
            writer.appendLine()
            writer.appendLine("Telemetry")
            if (telemetryLines.isEmpty()) {
                writer.appendLine("No telemetry captured yet. Enable diagnostics capture and reproduce.")
            } else {
                telemetryLines.forEach { line -> writer.appendLine(line) }
            }
            writer.appendLine()
            writer.appendLine("Energy Diagnostics")
            if (energyLines.isEmpty()) {
                writer.appendLine("No energy diagnostics samples yet.")
            } else {
                energyLines.forEach { line -> writer.appendLine(line) }
            }
            writer.appendLine()
            writer.appendLine("GNSS Summary")
            writer.appendLine("statusSampleCount=${gnssInsights.statusSampleCount}")
            writer.appendLine("startedCount=${gnssInsights.startedCount}")
            writer.appendLine("stoppedCount=${gnssInsights.stoppedCount}")
            writer.appendLine("firstFixCount=${gnssInsights.firstFixCount}")
            writer.appendLine(
                "firstFixTtffAvgMs=${
                    if (gnssInsights.firstFixCount > 0) gnssInsights.firstFixTtffAvgMs.toString() else "na"
                }",
            )
            writer.appendLine(
                "firstFixTtffMinMs=${
                    if (gnssInsights.firstFixCount > 0) gnssInsights.firstFixTtffMinMs.toString() else "na"
                }",
            )
            writer.appendLine(
                "firstFixTtffMaxMs=${
                    if (gnssInsights.firstFixCount > 0) gnssInsights.firstFixTtffMaxMs.toString() else "na"
                }",
            )
            writer.appendLine(
                "satellitesAvg=${
                    if (gnssInsights.statusSampleCount > 0) "%.2f".format(gnssInsights.satellitesAvg) else "na"
                }",
            )
            writer.appendLine(
                "satellitesMax=${
                    if (gnssInsights.statusSampleCount > 0) gnssInsights.satellitesMax.toString() else "na"
                }",
            )
            writer.appendLine(
                "usedInFixAvg=${
                    if (gnssInsights.statusSampleCount > 0) "%.2f".format(gnssInsights.usedInFixAvg) else "na"
                }",
            )
            writer.appendLine(
                "usedInFixMax=${
                    if (gnssInsights.statusSampleCount > 0) gnssInsights.usedInFixMax.toString() else "na"
                }",
            )
            writer.appendLine(
                "cn0AvgDbHz=${
                    gnssInsights.cn0AvgDbHz?.let { "%.2f".format(it) } ?: "na"
                }",
            )
            writer.appendLine(
                "cn0MaxDbHz=${
                    gnssInsights.cn0MaxDbHz?.let { "%.1f".format(it) } ?: "na"
                }",
            )
            writer.appendLine("carrierFrequencyStatusCount=${gnssInsights.carrierFrequencyStatusCount}")
            writer.appendLine("l1ObservedStatusCount=${gnssInsights.l1ObservedStatusCount}")
            writer.appendLine("l5ObservedStatusCount=${gnssInsights.l5ObservedStatusCount}")
            writer.appendLine("dualBandObservedStatusCount=${gnssInsights.dualBandObservedStatusCount}")
            writer.appendLine(
                "l1SatelliteMax=${
                    if (gnssInsights.statusSampleCount > 0) gnssInsights.l1SatelliteMax.toString() else "na"
                }",
            )
            writer.appendLine(
                "l5SatelliteMax=${
                    if (gnssInsights.statusSampleCount > 0) gnssInsights.l5SatelliteMax.toString() else "na"
                }",
            )
            writer.appendLine()
            writer.appendLine("GNSS Events")
            if (gnssLines.isEmpty()) {
                writer.appendLine("No GNSS diagnostics samples captured yet.")
            } else {
                gnssLines.forEach { line -> writer.appendLine(line) }
            }
            writer.appendLine()
            writer.appendLine("Field Markers")
            if (fieldMarkerLines.isEmpty()) {
                writer.appendLine("No field markers captured.")
            } else {
                fieldMarkerLines.forEach { line -> writer.appendLine(line) }
            }
            writer.appendLine()
            writer.appendLine("Fusion Replay Summary")
            writer.appendLine("acceptedFixes=${fusionReplaySummary.acceptedFixes}")
            writer.appendLine("outlierDrops=${fusionReplaySummary.outlierDrops}")
            writer.appendLine("predictions=${fusionReplaySummary.predictions}")
            writer.appendLine("blendStarts=${fusionReplaySummary.blendStarts}")
            writer.appendLine("avgBlendDurationMs=${fusionReplaySummary.avgBlendDurationMs}")
            writer.appendLine("toStationary=${fusionReplaySummary.toStationary}")
            writer.appendLine("toMoving=${fusionReplaySummary.toMoving}")
            writer.appendLine("maxJumpMeters=${"%.1f".format(fusionReplaySummary.maxJumpMeters)}")
            writer.appendLine("maxImpliedSpeedMps=${"%.1f".format(fusionReplaySummary.maxImpliedSpeedMps)}")
            writer.appendLine()
            writer.appendLine("Fusion Replay Events")
            if (fusionReplayLines.isEmpty()) {
                writer.appendLine("No fusion replay events captured yet.")
            } else {
                fusionReplayLines.forEach { line -> writer.appendLine(line) }
            }
            writer.appendLine()
            writer.appendLine("Performance Summary")
            writer.appendLine("skippedFrameEventCount=${performanceSummary.skippedFrameEventCount}")
            writer.appendLine("skippedFrameWarningCount=${performanceSummary.skippedFrameWarningCount}")
            writer.appendLine(
                "skippedFramesMax=${
                    if (performanceSummary.skippedFrameEventCount > 0) {
                        performanceSummary.skippedFramesMax.toString()
                    } else {
                        "na"
                    }
                }",
            )
            writer.appendLine("daveyCount=${performanceSummary.daveyCount}")
            writer.appendLine(
                "daveyMaxDurationMs=${
                    if (performanceSummary.daveyCount > 0) {
                        performanceSummary.daveyMaxDurationMs.toString()
                    } else {
                        "na"
                    }
                }",
            )
            writer.appendLine(
                "daveyAvgDurationMs=${
                    if (performanceSummary.daveyCount > 0) {
                        (performanceSummary.daveyTotalDurationMs / performanceSummary.daveyCount).toString()
                    } else {
                        "na"
                    }
                }",
            )
            writer.appendLine("gcEventCount=${performanceSummary.gcEventCount}")
            writer.appendLine(
                "gcMaxDurationMs=${
                    if (performanceSummary.gcEventCount > 0) {
                        performanceSummary.gcMaxDurationMs.toString()
                    } else {
                        "na"
                    }
                }",
            )
            writer.appendLine(
                "gcAvgDurationMs=${
                    if (performanceSummary.gcEventCount > 0) {
                        (performanceSummary.gcTotalDurationMs / performanceSummary.gcEventCount).toString()
                    } else {
                        "na"
                    }
                }",
            )
            writer.appendLine(
                "gcMaxFreedKb=${
                    if (performanceSummary.gcEventCount > 0) {
                        performanceSummary.gcMaxFreedKb.toString()
                    } else {
                        "na"
                    }
                }",
            )
            writer.appendLine("resourceCloseWarningCount=${performanceSummary.resourceCloseWarningCount}")
            writer.appendLine()
            writer.appendLine("Map Hot Path Summary")
            writer.appendLine("eventCount=${mapHotPathSummary.eventCount}")
            writer.appendLine("bufferMaxLines=${mapHotPathSummary.maxBufferedLines}")
            writer.appendLine("droppedLines=${mapHotPathSummary.droppedLineCount}")
            writer.appendLine("truncated=$mapHotPathTruncated")
            writer.appendLine("stageCount=${mapHotPathSummary.stageCount}")
            writer.appendLine("slowEventCount=${mapHotPathSummary.slowEventCount}")
            writer.appendLine("errorEventCount=${mapHotPathSummary.errorEventCount}")
            writer.appendLine(
                "maxDurationMs=${
                    if (mapHotPathSummary.eventCount > 0) {
                        mapHotPathSummary.maxDurationMs.toString()
                    } else {
                        "na"
                    }
                }",
            )
            if (mapHotPathSummary.stageSummaries.isEmpty()) {
                writer.appendLine("No map hot path timings captured.")
            } else {
                mapHotPathSummary.stageSummaries.forEachIndexed { index, stage ->
                    writer.appendLine(
                        "stage[$index]=name=${stage.stage} count=${stage.count} avgMs=${stage.avgDurationMs} maxMs=${stage.maxDurationMs} slowCount=${stage.slowCount} errorCount=${stage.errorCount}",
                    )
                }
            }
            writer.appendLine()
            writer.appendLine("Map Hot Path Events")
            if (mapHotPathLines.isEmpty()) {
                writer.appendLine("No map hot path events captured yet.")
            } else {
                mapHotPathLines.forEach { line -> writer.appendLine(line) }
            }
            writer.appendLine()
            writer.appendLine("Historical Process Exit Reasons")
            writer.appendLine("apiSupported=${historicalExitReasons.apiSupported}")
            historicalExitReasons.captureError?.let { writer.appendLine("captureError=$it") }
            writer.appendLine("entryCount=${historicalExitReasons.entries.size}")
            if (historicalExitReasons.entries.isEmpty()) {
                writer.appendLine("No historical process exit reasons reported.")
            } else {
                historicalExitReasons.entries.forEachIndexed { index, entry ->
                    writer.appendLine(
                        "exit[$index]=timestamp=${formatCaptureTime(entry.timestampMs)} reason=${entry.reason} subReason=${entry.subReason} importance=${entry.importance} status=${entry.status} pssKb=${entry.pssKb} rssKb=${entry.rssKb} description=${entry.description ?: "na"}",
                    )
                }
            }
            writer.appendLine()
            writer.appendLine("Last Fatal Crash")
            if (lastCrash.isNullOrBlank()) {
                writer.appendLine("No crash recorded.")
            } else {
                writer.appendLine(lastCrash)
            }
            writer.appendLine()
            writer.appendLine("App Logcat")
            writer.appendLine("pid=${logcatSnapshot.pid}")
            writer.appendLine("capturedLines=${logcatSnapshot.lines.size}")
            writer.appendLine("totalReadLines=${logcatSnapshot.totalReadLines}")
            writer.appendLine("truncated=${logcatSnapshot.truncated}")
            logcatSnapshot.captureError?.let { writer.appendLine("captureError=$it") }
            if (logcatSnapshot.lines.isEmpty()) {
                writer.appendLine("No app logcat lines captured.")
            } else {
                logcatSnapshot.lines.forEach { line -> writer.appendLine(line) }
            }
        }

        return file
    }

    fun latestExportFile(context: Context): File? {
        val dir = existingDiagnosticsDir(context) ?: return null
        return dir
            .listFiles()
            ?.asSequence()
            ?.filter { file ->
                file.isFile &&
                    file.name.startsWith("glancemap_diagnostics_") &&
                    file.name.endsWith(".txt")
            }?.maxByOrNull { it.lastModified() }
    }

    fun exportedFileCount(context: Context): Int {
        val dir = existingDiagnosticsDir(context) ?: return 0
        return dir
            .listFiles()
            ?.count { file ->
                file.isFile &&
                    file.name.startsWith("glancemap_diagnostics_") &&
                    file.name.endsWith(".txt")
            }
            ?: 0
    }

    fun clearExportedFiles(context: Context) {
        val dir = existingDiagnosticsDir(context) ?: return
        dir.listFiles()?.forEach { file ->
            runCatching { if (file.isFile) file.delete() }
        }
    }

    private fun diagnosticsDir(context: Context): File {
        val dir = File(context.filesDir, "diagnostics")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun buildDeviceSlug(): String {
        val raw = "${Build.MANUFACTURER}_${Build.MODEL}"
        val normalized =
            raw
                .lowercase(Locale.US)
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "wear_watch" }
        return normalized.take(40)
    }

    private fun existingDiagnosticsDir(context: Context): File? {
        val dir = File(context.filesDir, "diagnostics")
        return if (dir.exists() && dir.isDirectory) dir else null
    }

    internal fun deriveAcceptedFixSummaries(lines: List<String>): AcceptedFixSummaries = deriveAcceptedFixSummariesFromLines(lines)

    internal fun inferObservedFixQuality(
        summary: AcceptedFixSummary,
        origin: String?,
        gnssInsights: GnssInsights,
    ): ObservedFixQualitySummary =
        inferObservedFixQualityFromSummary(
            summary = summary,
            origin = origin,
            gnssInsights = gnssInsights,
        )
}

internal fun normalizeThreadtimeLogcatLine(
    line: String,
    capturedAt: Instant,
    zoneId: ZoneId,
): String {
    val match = threadtimeLineRegex.matchEntire(line) ?: return line
    val month = match.groupValues[1].toIntOrNull() ?: return line
    val day = match.groupValues[2].toIntOrNull() ?: return line
    val time =
        runCatching {
            LocalTime.parse(match.groupValues[3], threadtimeTimeFormatter)
        }.getOrNull() ?: return line
    val suffix = match.groupValues[4]
    val capturedYear = capturedAt.atZone(zoneId).year
    val inferredDateTime =
        inferThreadtimeDateTime(
            month = month,
            day = day,
            time = time,
            capturedAt = capturedAt,
            zoneId = zoneId,
            baseYear = capturedYear,
        ) ?: return line
    return "${normalizedLogcatTimestampFormatter.format(inferredDateTime)}$suffix"
}

internal fun inferThreadtimeDateTime(
    month: Int,
    day: Int,
    time: LocalTime,
    capturedAt: Instant,
    zoneId: ZoneId,
    baseYear: Int,
): LocalDateTime? {
    val candidates =
        sequenceOf(baseYear - 1, baseYear, baseYear + 1)
            .mapNotNull { year ->
                runCatching {
                    ZonedDateTime.of(year, month, day, time.hour, time.minute, time.second, time.nano, zoneId)
                }.getOrNull()
            }.toList()
    if (candidates.isEmpty()) return null
    val best =
        candidates.minByOrNull { candidate ->
            kotlin.math.abs(Duration.between(candidate.toInstant(), capturedAt).toMillis())
        } ?: return null
    return best.toLocalDateTime()
}
