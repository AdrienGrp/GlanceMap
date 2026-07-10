package com.glancemap.glancemapwearos.core.service.location.service

import android.content.Context
import android.os.SystemClock
import com.glancemap.glancemapwearos.core.service.diagnostics.EnergyDiagnostics
import com.glancemap.glancemapwearos.core.service.location.config.HIGH_ACCURACY_BURST_DURATION
import com.glancemap.glancemapwearos.core.service.location.config.HIGH_ACCURACY_BURST_INITIAL_DURATION
import com.glancemap.glancemapwearos.core.service.location.engine.EndBurstResult
import com.glancemap.glancemapwearos.core.service.location.engine.ImmediateBurstDecision
import com.glancemap.glancemapwearos.core.service.location.engine.LocationEngine
import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode
import com.glancemap.glancemapwearos.core.service.location.telemetry.LocationServiceTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class ImmediateLocationCoordinator(
    private val context: Context,
    private val serviceScope: CoroutineScope,
    private val engine: LocationEngine,
    private val telemetry: LocationServiceTelemetry,
    private val requestLocationUpdateIfNeeded: () -> Unit,
    private val passiveExperimentSourceMode: () -> LocationSourceMode?,
) {
    private var burstJob: Job? = null

    fun requestImmediateLocation(source: String = "service_unknown") {
        val passiveSourceMode = passiveExperimentSourceMode()
        if (passiveSourceMode != null) {
            telemetry.logImmediateRequestSkippedPassiveExperiment(
                source = source,
                backend = passiveSourceMode.telemetryValue,
            )
            requestLocationUpdateIfNeeded()
            return
        }

        val now = SystemClock.elapsedRealtime()
        when (val decision = engine.requestImmediateBurst(nowElapsedMs = now, source = source)) {
            is ImmediateBurstDecision.SkipActiveBurst,
            is ImmediateBurstDecision.SkipCooldown,
            -> {
                return
            }
            is ImmediateBurstDecision.Started -> {
                EnergyDiagnostics.recordSample(
                    context = context,
                    reason = "gps_burst_start",
                    detail = "burstId=${decision.burstId} source=$source durationMs=$HIGH_ACCURACY_BURST_DURATION",
                )

                burstJob?.cancel()
                requestLocationUpdateIfNeeded()

                burstJob =
                    serviceScope.launch {
                        delay(HIGH_ACCURACY_BURST_INITIAL_DURATION)
                        val signal = engine.gpsSignalSnapshot
                        val fixAgeMs =
                            (SystemClock.elapsedRealtime() - signal.lastFixElapsedRealtimeMs).coerceAtLeast(0L)
                        if (
                            signal.lastFixElapsedRealtimeMs > 0L &&
                            fixAgeMs <= 6_000L &&
                            signal.lastFixAccuracyM <= 35f
                        ) {
                            endHighAccuracyBurst(reason = "timer_good_enough", expectedBurstId = decision.burstId)
                        } else {
                            delay(HIGH_ACCURACY_BURST_DURATION - HIGH_ACCURACY_BURST_INITIAL_DURATION)
                            endHighAccuracyBurst(reason = "timer_extended", expectedBurstId = decision.burstId)
                        }
                    }
            }
        }
    }

    fun endHighAccuracyBurst(
        reason: String,
        expectedBurstId: Long? = null,
        requestLocationUpdate: Boolean = true,
    ): EndBurstResult? {
        val endedBurst =
            engine.endHighAccuracyBurst(
                reason = reason,
                expectedBurstId = expectedBurstId,
            ) ?: return null

        burstJob?.cancel()
        burstJob = null

        EnergyDiagnostics.recordSample(
            context = context,
            reason = "gps_burst_end",
            detail = "burstId=${endedBurst.burstId} source=${endedBurst.source} reason=$reason",
        )
        telemetry.logBurstSummary(
            burstId = endedBurst.burstId,
            source = endedBurst.source,
            reason = reason,
            endedAtElapsedMs = SystemClock.elapsedRealtime(),
        )

        if (requestLocationUpdate) {
            requestLocationUpdateIfNeeded()
        }
        return endedBurst
    }

    fun cancelImmediateLocationWork(reason: String) {
        val cancelledBurst = engine.isBurstActive()
        if (cancelledBurst) {
            endHighAccuracyBurst(reason = reason, requestLocationUpdate = false)
        }

        if (cancelledBurst) {
            telemetry.logImmediateLocationWorkCancelled(
                reason = reason,
                cancelledBurst = cancelledBurst,
                cancelledFetch = false,
            )
        }
    }

    fun shutdown(reason: String) {
        endHighAccuracyBurst(reason = reason, requestLocationUpdate = false)
        burstJob?.cancel()
        burstJob = null
    }
}

internal fun shouldSuppressActiveImmediateLocationForPassiveExperiment(
    passiveLocationExperiment: Boolean,
    sourceMode: LocationSourceMode,
): Boolean = passiveLocationExperiment && sourceMode == LocationSourceMode.PASSIVE_EXTERNAL
