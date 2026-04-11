package com.glancemap.glancemapwearos.core.service.location.service

import android.annotation.SuppressLint
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Handler
import android.os.SystemClock
import com.glancemap.glancemapwearos.core.service.diagnostics.GnssDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class GnssDiagnosticsCoordinator(
    private val serviceScope: CoroutineScope,
    private val mainHandler: Handler,
    private val locationManagerProvider: () -> LocationManager?,
    private val hasFinePermission: () -> Boolean,
    private val hasCoarsePermission: () -> Boolean,
    private val trackingEnabled: () -> Boolean,
    private val bound: () -> Boolean,
    private val keepOpen: () -> Boolean,
    private val watchOnly: () -> Boolean,
    private val sourceMode: () -> String,
    private val ambientModeActive: () -> Boolean,
    private val debugTelemetryEnabled: () -> Boolean,
) {
    @Volatile
    private var collectorRegisteredAtElapsedMs: Long = 0L

    @Volatile
    private var statusSampleCount: Int = 0

    @Volatile
    private var lastStatusAtElapsedMs: Long = 0L

    private var statusWatchdogJob: Job? = null
    private var statusCallback: GnssStatus.Callback? = null

    @SuppressLint("MissingPermission")
    @Synchronized
    fun update(enabled: Boolean) {
        if (!enabled) {
            unregister(reason = "debug_disabled")
            return
        }

        if (!hasFinePermission()) {
            unregister(reason = "no_fine_permission")
            val manager = locationManagerProvider()
            GnssDiagnostics.recordEvent(
                "collector_inactive",
                "reason=no_fine_permission coarse=${hasCoarsePermission()} tracking=${trackingEnabled()} " +
                    "sourceMode=${sourceMode()} gpsProviderPresent=${gpsProviderPresent(manager)} " +
                    "gpsProviderEnabled=${gpsProviderEnabled(manager)}",
            )
            return
        }

        if (statusCallback != null) {
            scheduleStatusWatchdogIfNeeded()
            return
        }

        val manager = locationManagerProvider()
        if (manager == null) {
            GnssDiagnostics.recordEvent(
                "collector_inactive",
                "reason=no_location_manager tracking=${trackingEnabled()} sourceMode=${sourceMode()}",
            )
            return
        }

        collectorRegisteredAtElapsedMs = SystemClock.elapsedRealtime()
        statusSampleCount = 0
        lastStatusAtElapsedMs = 0L

        val callback =
            object : GnssStatus.Callback() {
                override fun onStarted() {
                    GnssDiagnostics.recordEvent("started")
                }

                override fun onStopped() {
                    GnssDiagnostics.recordEvent("stopped")
                }

                override fun onFirstFix(ttffMillis: Int) {
                    GnssDiagnostics.recordEvent("first_fix", "ttffMs=$ttffMillis")
                }

                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    val nowElapsedMs = SystemClock.elapsedRealtime()
                    statusSampleCount += 1
                    lastStatusAtElapsedMs = nowElapsedMs

                    if (statusSampleCount == 1) {
                        val sinceRegisterMs =
                            if (collectorRegisteredAtElapsedMs > 0L) {
                                (nowElapsedMs - collectorRegisteredAtElapsedMs).coerceAtLeast(0L)
                            } else {
                                -1L
                            }
                        GnssDiagnostics.recordEvent(
                            "status_stream_started",
                            "afterRegisterMs=${formatAgeMsForTelemetry(sinceRegisterMs)}",
                        )
                    }

                    val satellites = status.satelliteCount
                    var usedInFix = 0
                    var cn0Count = 0
                    var cn0Sum = 0f
                    var cn0Max = Float.NEGATIVE_INFINITY
                    var carrierFrequencySatelliteCount = 0
                    var l1SatelliteCount = 0
                    var l5SatelliteCount = 0
                    var gpsCount = 0
                    var galileoCount = 0
                    var glonassCount = 0
                    var beidouCount = 0
                    var qzssCount = 0
                    var sbasCount = 0
                    var unknownCount = 0

                    for (index in 0 until satellites) {
                        if (status.usedInFix(index)) {
                            usedInFix += 1
                        }
                        when (status.getConstellationType(index)) {
                            GnssStatus.CONSTELLATION_GPS -> gpsCount += 1
                            GnssStatus.CONSTELLATION_GALILEO -> galileoCount += 1
                            GnssStatus.CONSTELLATION_GLONASS -> glonassCount += 1
                            GnssStatus.CONSTELLATION_BEIDOU -> beidouCount += 1
                            GnssStatus.CONSTELLATION_QZSS -> qzssCount += 1
                            GnssStatus.CONSTELLATION_SBAS -> sbasCount += 1
                            else -> unknownCount += 1
                        }
                        val cn0 = status.getCn0DbHz(index)
                        if (cn0.isFinite() && cn0 > 0f) {
                            cn0Count += 1
                            cn0Sum += cn0
                            if (cn0 > cn0Max) {
                                cn0Max = cn0
                            }
                        }
                        if (status.hasCarrierFrequencyHz(index)) {
                            carrierFrequencySatelliteCount += 1
                            val carrierFrequencyHz = status.getCarrierFrequencyHz(index).toDouble()
                            if (carrierFrequencyHz in GNSS_L1_MIN_HZ..GNSS_L1_MAX_HZ) {
                                l1SatelliteCount += 1
                            }
                            if (carrierFrequencyHz in GNSS_L5_MIN_HZ..GNSS_L5_MAX_HZ) {
                                l5SatelliteCount += 1
                            }
                        }
                    }

                    val avgCn0 = if (cn0Count > 0) cn0Sum / cn0Count else null
                    val maxCn0 = if (cn0Count > 0 && cn0Max.isFinite()) cn0Max else null
                    GnssDiagnostics.recordStatus(
                        satellites = satellites,
                        usedInFix = usedInFix,
                        cn0AvgDbHz = avgCn0,
                        cn0MaxDbHz = maxCn0,
                        carrierFrequencySatelliteCount = carrierFrequencySatelliteCount,
                        l1SatelliteCount = l1SatelliteCount,
                        l5SatelliteCount = l5SatelliteCount,
                        dualBandObserved = l1SatelliteCount > 0 && l5SatelliteCount > 0,
                        gpsCount = gpsCount,
                        galileoCount = galileoCount,
                        glonassCount = glonassCount,
                        beidouCount = beidouCount,
                        qzssCount = qzssCount,
                        sbasCount = sbasCount,
                        unknownCount = unknownCount,
                    )
                }
            }

        val registered =
            runCatching {
                manager.registerGnssStatusCallback(callback, mainHandler)
            }.getOrDefault(false)

        if (!registered) {
            resetRuntimeState()
            GnssDiagnostics.recordEvent(
                "collector_register_failed",
                "tracking=${trackingEnabled()} keepOpen=${keepOpen()} sourceMode=${sourceMode()} " +
                    "gpsProviderPresent=${gpsProviderPresent(manager)} gpsProviderEnabled=${gpsProviderEnabled(manager)}",
            )
            return
        }

        statusCallback = callback
        GnssDiagnostics.recordEvent(
            "collector_registered",
            "tracking=${trackingEnabled()} bound=${bound()} keepOpen=${keepOpen()} " +
                "watchOnly=${watchOnly()} sourceMode=${sourceMode()} ambient=${ambientModeActive()} " +
                "gpsProviderPresent=${gpsProviderPresent(manager)} gpsProviderEnabled=${gpsProviderEnabled(manager)}",
        )
        scheduleStatusWatchdogIfNeeded()
    }

    @Synchronized
    fun unregister(reason: String = "unspecified") {
        statusWatchdogJob?.cancel()
        statusWatchdogJob = null

        val callback = statusCallback
        if (callback != null) {
            val manager = locationManagerProvider()
            runCatching { manager?.unregisterGnssStatusCallback(callback) }
            statusCallback = null

            val nowElapsedMs = SystemClock.elapsedRealtime()
            val runtimeMs =
                if (collectorRegisteredAtElapsedMs > 0L) {
                    (nowElapsedMs - collectorRegisteredAtElapsedMs).coerceAtLeast(0L)
                } else {
                    -1L
                }
            val lastStatusAgeMs =
                if (lastStatusAtElapsedMs > 0L) {
                    (nowElapsedMs - lastStatusAtElapsedMs).coerceAtLeast(0L)
                } else {
                    -1L
                }
            GnssDiagnostics.recordEvent(
                "collector_unregistered",
                "reason=$reason runtimeMs=${formatAgeMsForTelemetry(runtimeMs)} " +
                    "statusSamples=$statusSampleCount " +
                    "lastStatusAgeMs=${formatAgeMsForTelemetry(lastStatusAgeMs)}",
            )
        }
        resetRuntimeState()
    }

    private fun scheduleStatusWatchdogIfNeeded() {
        statusWatchdogJob?.cancel()
        val registeredAt = collectorRegisteredAtElapsedMs
        if (registeredAt <= 0L || statusCallback == null) return

        statusWatchdogJob =
            serviceScope.launch {
                delay(GNSS_STATUS_WATCHDOG_DELAY_MS)
                if (statusCallback == null || !debugTelemetryEnabled()) return@launch
                if (statusSampleCount > 0) return@launch

                val nowElapsedMs = SystemClock.elapsedRealtime()
                val sinceRegisterMs = (nowElapsedMs - registeredAt).coerceAtLeast(0L)
                GnssDiagnostics.recordEvent(
                    "collector_no_status",
                    "sinceRegisterMs=${formatAgeMsForTelemetry(sinceRegisterMs)} " +
                        "tracking=${trackingEnabled()} bound=${bound()} keepOpen=${keepOpen()} " +
                        "sourceMode=${sourceMode()}",
                )
            }
    }

    private fun resetRuntimeState() {
        collectorRegisteredAtElapsedMs = 0L
        statusSampleCount = 0
        lastStatusAtElapsedMs = 0L
    }

    private fun formatAgeMsForTelemetry(valueMs: Long): String {
        if (valueMs < 0L) return "na"
        return valueMs.toString()
    }

    private fun gpsProviderPresent(manager: LocationManager?): Boolean =
        runCatching { manager?.allProviders?.contains(LocationManager.GPS_PROVIDER) == true }
            .getOrDefault(false)

    private fun gpsProviderEnabled(manager: LocationManager?): Boolean =
        runCatching { manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true }
            .getOrDefault(false)
}

private const val GNSS_STATUS_WATCHDOG_DELAY_MS = 12_000L
private const val GNSS_L1_MIN_HZ = 1_559_000_000.0
private const val GNSS_L1_MAX_HZ = 1_610_000_000.0
private const val GNSS_L5_MIN_HZ = 1_160_000_000.0
private const val GNSS_L5_MAX_HZ = 1_200_000_000.0
