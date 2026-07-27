package com.glancemap.glancemapwearos.core.service.location.service

import android.annotation.SuppressLint
import android.location.Location
import android.os.SystemClock
import com.glancemap.glancemapwearos.core.service.location.adapters.CurrentLocationRequestParams
import com.glancemap.glancemapwearos.core.service.location.adapters.LocationGateway
import com.glancemap.glancemapwearos.core.service.location.engine.LocationEngine
import com.glancemap.glancemapwearos.core.service.location.model.LocationPermissionSnapshot
import com.glancemap.glancemapwearos.core.service.location.policy.FixAcceptancePolicy
import com.glancemap.glancemapwearos.core.service.location.policy.LocationFixPolicy
import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode
import com.glancemap.glancemapwearos.core.service.location.telemetry.LocationServiceTelemetry
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

@Suppress("LongParameterList")
internal class NavigateOneShotCoordinator(
    private val serviceScope: CoroutineScope,
    private val engine: LocationEngine,
    private val telemetry: LocationServiceTelemetry,
    private val readAndStoreLocationPermissions: () -> LocationPermissionSnapshot,
    private val resolveFixAcceptancePolicy: (LocationPermissionSnapshot, LocationSourceMode) -> FixAcceptancePolicy,
    private val strictFreshMaxAgeMs: () -> Long,
    private val hardMaxAcceptedFixAgeMs: () -> Long,
    private val currentLocationSourceMode: () -> LocationSourceMode,
    private val locationGatewayFor: (LocationSourceMode) -> LocationGateway,
    private val emitGpsSignalSnapshot: () -> Unit,
    private val emitAcceptedImmediateLocation: (Location, Long) -> Unit,
    private val endHighAccuracyBurst: (String, Long) -> Unit,
    private val timeoutMs: Long,
) {
    private val requestLock = Any()
    private var activeRequest: ActiveRequest? = null

    fun request(
        source: String,
        burstId: Long,
    ) {
        cancel(reason = "superseded")
        val sourceMode = currentLocationSourceMode()
        if (sourceMode != LocationSourceMode.AUTO_FUSED) return

        val maxUpdateAgeMs = strictFreshMaxAgeMs()
        lateinit var request: ActiveRequest
        val job =
            serviceScope.launch(start = CoroutineStart.LAZY) {
                try {
                    execute(
                        request = request,
                        sourceMode = sourceMode,
                        burstId = burstId,
                        maxUpdateAgeMs = maxUpdateAgeMs,
                    )
                } finally {
                    synchronized(requestLock) {
                        if (activeRequest === request) activeRequest = null
                    }
                }
            }
        request =
            ActiveRequest(
                job = job,
                source = source,
                backend = sourceMode.telemetryValue,
                requestedAtElapsedMs = SystemClock.elapsedRealtime(),
                telemetrySource = "navigateOneShot_$source",
            )
        synchronized(requestLock) {
            activeRequest = request
        }
        telemetry.logNavigateOneShotRequested(
            source = source,
            backend = sourceMode.telemetryValue,
            maxUpdateAgeMs = maxUpdateAgeMs,
            timeoutMs = timeoutMs,
        )
        job.start()
    }

    fun onGoodStreamFixAccepted() {
        cancel(
            reason = "good_stream_fix",
            outcome = "stream_won",
        )
    }

    fun cancel(
        reason: String,
        outcome: String = "cancelled",
    ): Boolean {
        val request =
            synchronized(requestLock) {
                activeRequest?.also { activeRequest = null }
            } ?: return false
        request.job.cancel()
        logOutcome(request, outcome, reason)
        return true
    }

    private suspend fun execute(
        request: ActiveRequest,
        sourceMode: LocationSourceMode,
        burstId: Long,
        maxUpdateAgeMs: Long,
    ) {
        val permissions = readAndStoreLocationPermissions()
        if (!permissions.hasAnyPermission) {
            finish(request, outcome = "cancelled", reason = "no_permission")
            endHighAccuracyBurst("no_permission", burstId)
        } else {
            fetchLocation(request, sourceMode, permissions, maxUpdateAgeMs)?.let { location ->
                processFetchedLocation(
                    request = request,
                    sourceMode = sourceMode,
                    burstId = burstId,
                    maxUpdateAgeMs = maxUpdateAgeMs,
                    permissions = permissions,
                    location = location,
                )
            }
        }
    }

    @Suppress("LongParameterList")
    private suspend fun processFetchedLocation(
        request: ActiveRequest,
        sourceMode: LocationSourceMode,
        burstId: Long,
        maxUpdateAgeMs: Long,
        permissions: LocationPermissionSnapshot,
        location: Location,
    ) {
        currentCoroutineContext().ensureActive()
        val evaluatedAtElapsedMs = SystemClock.elapsedRealtime()
        val outcome =
            engine.processImmediateCandidate(
                location = location,
                nowElapsedMs = evaluatedAtElapsedMs,
                acceptance = resolveFixAcceptancePolicy(permissions, sourceMode),
                strictMaxAgeMs = maxUpdateAgeMs,
                hardMaxAgeMs = hardMaxAcceptedFixAgeMs(),
                source = request.telemetrySource,
                sourceMode = sourceMode,
            )
        emitGpsSignalSnapshot()
        val acceptedLocation = outcome.acceptedLocation
        if (acceptedLocation == null) {
            finish(
                request = request,
                outcome = "rejected",
                reason = "fix_policy",
                fixAgeMs = LocationFixPolicy.locationAgeMs(location, evaluatedAtElapsedMs),
                accuracyM = location.accuracy,
            )
        } else {
            val fixAgeMs = LocationFixPolicy.locationAgeMs(acceptedLocation, evaluatedAtElapsedMs)
            val ownsResult =
                finish(request, "succeeded", "accepted", fixAgeMs, acceptedLocation.accuracy)
            if (ownsResult) {
                emitAcceptedLocation(
                    source = request.source,
                    sourceMode = sourceMode,
                    location = acceptedLocation,
                    acceptedAtElapsedMs = evaluatedAtElapsedMs,
                    fixAgeMs = fixAgeMs,
                )
                if (outcome.shouldEndBurstEarly) {
                    endHighAccuracyBurst("early_fix", burstId)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("TooGenericExceptionCaught")
    private suspend fun fetchLocation(
        request: ActiveRequest,
        sourceMode: LocationSourceMode,
        permissions: LocationPermissionSnapshot,
        maxUpdateAgeMs: Long,
    ): Location? =
        try {
            val priority =
                if (permissions.hasFinePermission) {
                    Priority.PRIORITY_HIGH_ACCURACY
                } else {
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY
                }
            locationGatewayFor(sourceMode).getCurrentLocation(
                CurrentLocationRequestParams(
                    priority = priority,
                    maxUpdateAgeMs = maxUpdateAgeMs,
                    durationMs = timeoutMs,
                ),
            ) ?: run {
                telemetry.logGetCurrentLocationFailed(
                    source = request.telemetrySource,
                    backend = sourceMode.telemetryValue,
                    errorType = "null_result",
                )
                finish(request, outcome = "timed_out", reason = "null_result")
                null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            telemetry.logGetCurrentLocationFailed(
                source = request.telemetrySource,
                backend = sourceMode.telemetryValue,
                errorType = error.javaClass.simpleName,
                errorDetail = error.message,
            )
            finish(request, outcome = "failed", reason = error.javaClass.simpleName)
            null
        }

    private fun emitAcceptedLocation(
        source: String,
        sourceMode: LocationSourceMode,
        location: Location,
        acceptedAtElapsedMs: Long,
        fixAgeMs: Long,
    ) {
        emitAcceptedImmediateLocation(
            engine.filterLocationForOutput(
                location = location,
                nowElapsedMs = acceptedAtElapsedMs,
                deliveredSourceMode = sourceMode,
            ),
            acceptedAtElapsedMs,
        )
        telemetry.onImmediateFixAccepted(
            nowElapsedMs = acceptedAtElapsedMs,
            activityState = engine.activityState(),
            burst = engine.isBurstActive(),
            source = source,
            ageMs = fixAgeMs,
            accuracyM = location.accuracy,
            provider = location.provider,
            origin = sourceMode.telemetryValue,
        )
    }

    private fun finish(
        request: ActiveRequest,
        outcome: String,
        reason: String,
        fixAgeMs: Long? = null,
        accuracyM: Float? = null,
    ): Boolean {
        val owned =
            synchronized(requestLock) {
                if (activeRequest !== request) {
                    false
                } else {
                    activeRequest = null
                    true
                }
            }
        if (owned) logOutcome(request, outcome, reason, fixAgeMs, accuracyM)
        return owned
    }

    private fun logOutcome(
        request: ActiveRequest,
        outcome: String,
        reason: String,
        fixAgeMs: Long? = null,
        accuracyM: Float? = null,
    ) {
        telemetry.logNavigateOneShotOutcome(
            source = request.source,
            backend = request.backend,
            outcome = outcome,
            reason = reason,
            durationMs =
                (SystemClock.elapsedRealtime() - request.requestedAtElapsedMs).coerceAtLeast(0L),
            fixAgeMs = fixAgeMs,
            accuracyM = accuracyM,
        )
    }

    private data class ActiveRequest(
        val job: Job,
        val source: String,
        val backend: String,
        val requestedAtElapsedMs: Long,
        val telemetrySource: String,
    )
}
