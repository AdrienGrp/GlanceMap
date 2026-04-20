package com.glancemap.glancemapwearos.core.service.location.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.SystemClock
import com.glancemap.glancemapwearos.core.service.diagnostics.EnergyDiagnostics
import com.glancemap.glancemapwearos.core.service.location.adapters.CurrentLocationRequestParams
import com.glancemap.glancemapwearos.core.service.location.adapters.LocationGateway
import com.glancemap.glancemapwearos.core.service.location.config.HIGH_ACCURACY_BURST_DURATION
import com.glancemap.glancemapwearos.core.service.location.engine.EndBurstResult
import com.glancemap.glancemapwearos.core.service.location.engine.ImmediateBurstDecision
import com.glancemap.glancemapwearos.core.service.location.engine.LocationEngine
import com.glancemap.glancemapwearos.core.service.location.model.LocationPermissionSnapshot
import com.glancemap.glancemapwearos.core.service.location.policy.FixAcceptancePolicy
import com.glancemap.glancemapwearos.core.service.location.policy.LocationFixPolicy
import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode
import com.glancemap.glancemapwearos.core.service.location.processing.ProcessedLocationCandidate
import com.glancemap.glancemapwearos.core.service.location.telemetry.LocationServiceTelemetry
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class ImmediateLocationCoordinator(
    private val context: Context,
    private val serviceScope: CoroutineScope,
    private val engine: LocationEngine,
    private val telemetry: LocationServiceTelemetry,
    private val readAndStoreLocationPermissions: () -> LocationPermissionSnapshot,
    private val resolveFixAcceptancePolicy: (LocationPermissionSnapshot, LocationSourceMode) -> FixAcceptancePolicy,
    private val strictFreshMaxAgeMs: () -> Long,
    private val hardMaxAcceptedFixAgeMs: () -> Long,
    private val currentLocationSourceMode: () -> LocationSourceMode,
    private val locationGatewayFor: (LocationSourceMode) -> LocationGateway,
    private val requestLocationUpdateIfNeeded: () -> Unit,
    private val passiveLocationExperiment: () -> Boolean,
    private val emitGpsSignalSnapshot: () -> Unit,
    private val emitAcceptedImmediateLocation: (Location, Long) -> Unit,
    private val immediateGetCurrentTimeoutMs: Long,
) {
    private var burstJob: Job? = null
    private var immediateLocationJob: Job? = null

    fun requestImmediateLocation(source: String = "service_unknown") {
        val sourceMode = currentLocationSourceMode()
        if (
            shouldSuppressActiveImmediateLocationForPassiveExperiment(
                passiveLocationExperiment = passiveLocationExperiment(),
                sourceMode = sourceMode,
            )
        ) {
            telemetry.logImmediateRequestSkippedPassiveExperiment(
                source = source,
                backend = sourceMode.telemetryValue,
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
                        delay(HIGH_ACCURACY_BURST_DURATION)
                        endHighAccuracyBurst(reason = "timer", expectedBurstId = decision.burstId)
                    }

                var launchedImmediateJob: Job? = null
                launchedImmediateJob =
                    serviceScope.launch {
                        try {
                            val permissions = readAndStoreLocationPermissions()
                            if (!permissions.hasAnyPermission) {
                                endHighAccuracyBurst(
                                    reason = "no_permission",
                                    expectedBurstId = decision.burstId,
                                )
                                return@launch
                            }

                            val strictMaxAgeMs = strictFreshMaxAgeMs()
                            val immediateSourceMode = currentLocationSourceMode()
                            val outcome =
                                fetchAndProcessImmediateLocation(
                                    source = source,
                                    permissions = permissions,
                                    strictMaxAgeMs = strictMaxAgeMs,
                                    sourceMode = immediateSourceMode,
                                ) ?: return@launch

                            emitGpsSignalSnapshot()
                            val acceptedLocation = outcome.acceptedLocation ?: return@launch
                            val acceptedAtMs = SystemClock.elapsedRealtime()
                            emitAcceptedImmediateLocation(
                                engine.filterLocationForOutput(
                                    location = acceptedLocation,
                                    nowElapsedMs = acceptedAtMs,
                                ),
                                acceptedAtMs,
                            )
                            val ageMs = LocationFixPolicy.locationAgeMs(acceptedLocation, acceptedAtMs)
                            telemetry.onImmediateFixAccepted(
                                nowElapsedMs = acceptedAtMs,
                                activityState = engine.activityState(),
                                burst = engine.isBurstActive(),
                                source = source,
                                ageMs = ageMs,
                                accuracyM = acceptedLocation.accuracy,
                                provider = acceptedLocation.provider,
                                origin = immediateSourceMode.telemetryValue,
                            )
                            if (outcome.shouldEndBurstEarly) {
                                endHighAccuracyBurst(
                                    reason = "early_fix",
                                    expectedBurstId = decision.burstId,
                                )
                            }
                        } finally {
                            if (immediateLocationJob === launchedImmediateJob) {
                                immediateLocationJob = null
                            }
                        }
                    }
                immediateLocationJob = launchedImmediateJob
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

        if (requestLocationUpdate) {
            requestLocationUpdateIfNeeded()
        }
        return endedBurst
    }

    fun cancelImmediateLocationWork(reason: String) {
        val cancelledFetch = immediateLocationJob != null
        immediateLocationJob?.cancel()
        immediateLocationJob = null

        val cancelledBurst = engine.isBurstActive()
        if (cancelledBurst) {
            endHighAccuracyBurst(reason = reason, requestLocationUpdate = false)
        }

        if (cancelledFetch || cancelledBurst) {
            telemetry.logImmediateLocationWorkCancelled(
                reason = reason,
                cancelledBurst = cancelledBurst,
                cancelledFetch = cancelledFetch,
            )
        }
    }

    fun shutdown(reason: String) {
        endHighAccuracyBurst(reason = reason, requestLocationUpdate = false)
        burstJob?.cancel()
        burstJob = null
        immediateLocationJob?.cancel()
        immediateLocationJob = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchAndProcessImmediateLocation(
        source: String,
        permissions: LocationPermissionSnapshot,
        strictMaxAgeMs: Long,
        sourceMode: LocationSourceMode,
    ): ProcessedLocationCandidate? {
        val immediatePriority =
            if (permissions.hasFinePermission) {
                Priority.PRIORITY_HIGH_ACCURACY
            } else {
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            }
        val gateway = locationGatewayFor(sourceMode)
        val telemetrySource = "getCurrentLocation_$source"
        return try {
            val location =
                gateway.getCurrentLocation(
                    CurrentLocationRequestParams(
                        priority = immediatePriority,
                        maxUpdateAgeMs = strictMaxAgeMs,
                        durationMs = immediateGetCurrentTimeoutMs,
                    ),
                )
            if (location == null) {
                telemetry.logGetCurrentLocationFailed(
                    source = telemetrySource,
                    backend = sourceMode.telemetryValue,
                    errorType = "null_result",
                )
                return null
            }

            val acceptedAtMs = SystemClock.elapsedRealtime()
            val acceptance = resolveFixAcceptancePolicy(permissions, sourceMode)
            engine.processImmediateCandidate(
                location = location,
                nowElapsedMs = acceptedAtMs,
                acceptance = acceptance,
                strictMaxAgeMs = strictMaxAgeMs,
                hardMaxAgeMs = hardMaxAcceptedFixAgeMs(),
                source = telemetrySource,
                sourceMode = sourceMode,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            telemetry.logGetCurrentLocationFailed(
                source = telemetrySource,
                backend = sourceMode.telemetryValue,
                errorType = error.javaClass.simpleName,
                errorDetail = error.message,
            )
            null
        }
    }
}

internal fun shouldSuppressActiveImmediateLocationForPassiveExperiment(
    passiveLocationExperiment: Boolean,
    sourceMode: LocationSourceMode,
): Boolean =
    passiveLocationExperiment && sourceMode == LocationSourceMode.AUTO_FUSED
