package com.glancemap.glancemapwearos.domain.sensors

import android.content.Context
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.DeviceOrientation
import com.google.android.gms.location.DeviceOrientationListener
import com.google.android.gms.location.DeviceOrientationRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.Executor
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
internal class FusedOrientationProviderAdapter(
    context: Context,
    private val fallbackProvider: CompassOrientationProvider,
) : CompassOrientationProvider {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fusedOrientationClient by lazy(LazyThreadSafetyMode.NONE) {
        LocationServices.getFusedOrientationProviderClient(appContext)
    }

    override val providerType: CompassProviderType = CompassProviderType.GOOGLE_FUSED

    private val _heading = MutableStateFlow(0f)
    private val _accuracy = MutableStateFlow(SensorManager.SENSOR_STATUS_UNRELIABLE)
    private val _headingErrorDeg = MutableStateFlow<Float?>(null)
    private val _conservativeHeadingErrorDeg = MutableStateFlow<Float?>(null)
    private val _headingSampleElapsedRealtimeMs = MutableStateFlow<Long?>(null)
    private val _headingSampleStale = MutableStateFlow(false)
    private val _headingSource = MutableStateFlow(HeadingSource.NONE)
    private val _headingSourceStatus =
        MutableStateFlow(
            HeadingSourceStatus(
                requestedMode = CompassHeadingSourceMode.AUTO,
                activeSource = HeadingSource.NONE,
                headingSensorAvailable = false,
                rotationVectorAvailable = false,
                magAccelFallbackAvailable = false,
            ),
        )
    private val _northReferenceStatus =
        MutableStateFlow(
            NorthReferenceStatus(
                requestedMode = NorthReferenceMode.TRUE,
                effectiveMode = NorthReferenceMode.MAGNETIC,
                declinationAvailable = false,
                waitingForDeclination = true,
                pipeline = HeadingPipeline.NONE,
            ),
        )
    private val _magneticInterference = MutableStateFlow(false)
    private val _useFallbackProvider = MutableStateFlow(false)
    private val _useBootstrapFallbackProvider = MutableStateFlow(false)

    private val baseRenderState =
        combine(
            _heading,
            _accuracy,
            _headingSource,
            _headingSourceStatus,
            _northReferenceStatus,
        ) { heading, accuracy, headingSource, headingSourceStatus, northReferenceStatus ->
            CompassRenderState(
                providerType = providerType,
                headingDeg = heading,
                accuracy = accuracy,
                headingSource = headingSource,
                headingSourceStatus = headingSourceStatus,
                northReferenceStatus = northReferenceStatus,
                magneticInterference = false,
            )
        }

    private val renderStateWithInterference =
        combine(
            baseRenderState,
            _magneticInterference,
        ) { baseState, magneticInterference ->
            baseState.copy(magneticInterference = magneticInterference)
        }

    private val ownRenderState =
        combine(
            renderStateWithInterference,
            _headingErrorDeg,
            _conservativeHeadingErrorDeg,
            _headingSampleElapsedRealtimeMs,
            _headingSampleStale,
        ) {
            baseState,
            headingErrorDeg,
            conservativeHeadingErrorDeg,
            headingSampleElapsedRealtimeMs,
            headingSampleStale,
            ->
            baseState.copy(
                headingErrorDeg = headingErrorDeg,
                conservativeHeadingErrorDeg = conservativeHeadingErrorDeg,
                headingSampleElapsedRealtimeMs = headingSampleElapsedRealtimeMs,
                headingSampleStale = headingSampleStale,
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(0),
            initialValue = initialCompassRenderState(providerType = providerType),
        )

    override val renderState: StateFlow<CompassRenderState> =
        combine(
            ownRenderState,
            fallbackProvider.renderState,
            _useFallbackProvider,
            _useBootstrapFallbackProvider,
        ) { ownState, fallbackState, useFallback, useBootstrapFallback ->
            val nowElapsedMs = SystemClock.elapsedRealtime()
            when {
                useFallback -> fallbackState
                useBootstrapFallback &&
                    shouldUseFusedBootstrapHeading(
                        fusedRenderState = ownState,
                        bootstrapRenderState = fallbackState,
                        nowElapsedMs = nowElapsedMs,
                    ) ->
                    bootstrapFusedRenderState(
                        fusedRenderState = ownState,
                        bootstrapRenderState = fallbackState,
                    )
                else -> ownState
            }
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(0),
            initialValue = ownRenderState.value,
        )

    private val declinationController =
        CompassDeclinationController(
            appContext = appContext,
            locationManager = locationManager,
            onStatusChanged = ::publishNorthReferenceStatus,
            logDiagnostics = ::logDiagnostics,
        )

    @Volatile private var started = false

    @Volatile private var lowPowerMode = false

    @Volatile private var startAtMs = 0L

    @Volatile private var headingRelockUntilElapsedMs = 0L

    @Volatile private var northReferenceMode = NorthReferenceMode.TRUE

    @Volatile private var orientationUpdatesRegistered = false

    // Cached once per start() — GoogleApiAvailability.isGooglePlayServicesAvailable() is a
    // cross-process binder call; caching avoids IPC overhead on every heading update.
    @Volatile private var googleOrientationAvailable = false

    @Volatile private var lastOrientationRequestAtElapsedMs = 0L

    @Volatile private var lastOrientationRequestReason: String = "idle"

    @Volatile private var awaitingFirstOrientationSample = false

    @Volatile private var awaitingRestartHeadingConfirmation = false

    @Volatile private var pendingRestartHeading: Float? = null

    @Volatile private var pendingRestartHeadingAtElapsedMs: Long = 0L

    @Volatile private var pendingRestartHeadingSampleCount = 0

    @Volatile private var lastFusedSampleLogAtElapsedMs = 0L

    @Volatile private var fusedSampleFreshnessGeneration = 0L

    @Volatile private var recalibrationBoostUntilElapsedMs = 0L

    @Volatile private var recalibrationBoostGeneration = 0L

    // Jump rejection state — replaces the full smoothing pipeline.
    // Google's fusion already outputs a clean, smoothed heading; double-filtering adds lag.
    @Volatile private var fusedPendingJumpHeading: Float? = null

    @Volatile private var fusedPendingJumpAtMs: Long = 0L

    @Volatile private var callbackThread: HandlerThread? = null

    @Volatile private var callbackHandler: Handler? = null
    private val callbackExecutor: Executor =
        Executor { runnable ->
            val h = callbackHandler
            if (h == null || !h.post(runnable)) runnable.run()
        }

    private fun ensureCallbackHandler() {
        if (callbackHandler?.looper?.thread?.isAlive == true) return
        val t = HandlerThread(FUSED_ORIENTATION_THREAD_NAME).apply { start() }
        callbackThread = t
        callbackHandler = Handler(t.looper)
    }

    private val orientationListener =
        DeviceOrientationListener { orientation ->
            handleDeviceOrientation(orientation)
        }

    override fun start(lowPower: Boolean) {
        if (started) {
            if (lowPowerMode == lowPower) return
            lowPowerMode = lowPower
            if (_useFallbackProvider.value) {
                fallbackProvider.start(lowPower = lowPower)
            } else {
                requestOrientationUpdates(
                    forceRestart = true,
                    reason = "low_power_mode_change",
                )
            }
            return
        }

        lowPowerMode = lowPower
        started = true
        startAtMs = SystemClock.elapsedRealtime()
        declinationController.maybeInitializeFromCache()
        declinationController.maybeInitializeFromLastKnownLocation()
        publishNorthReferenceStatus()
        _magneticInterference.value = false

        googleOrientationAvailable = isGoogleOrientationAvailable()
        if (!googleOrientationAvailable) {
            logDiagnostics("google_fused unavailable; using sensor backup")
            startFallbackProvider(reason = "google_unavailable")
            return
        }

        _useFallbackProvider.value = false
        _useBootstrapFallbackProvider.value = false
        updateHeadingSourceState(HeadingSource.NONE)
        requestOrientationUpdates(forceRestart = true, reason = "start")
    }

    override fun stop() {
        started = false
        stopOrientationUpdates()
        callbackThread?.quitSafely()
        callbackThread = null
        callbackHandler = null
        stopBootstrapFallbackProvider(reason = "stop")
        stopFallbackProvider()
        _useFallbackProvider.value = false
        _useBootstrapFallbackProvider.value = false
        markHeadingPendingRestart()
        _magneticInterference.value = false
        headingRelockUntilElapsedMs = 0L
        fusedPendingJumpHeading = null
        fusedPendingJumpAtMs = 0L
        recalibrationBoostUntilElapsedMs = 0L
        recalibrationBoostGeneration += 1L
        lastOrientationRequestAtElapsedMs = 0L
        lastOrientationRequestReason = "idle"
        awaitingFirstOrientationSample = false
        clearRestartHeadingConfirmationState()
        lastFusedSampleLogAtElapsedMs = 0L
        publishNorthReferenceStatus()
    }

    override fun recalibrate() {
        if (_useFallbackProvider.value) {
            fallbackProvider.recalibrate()
            return
        }
        fusedPendingJumpHeading = null
        fusedPendingJumpAtMs = 0L
        if (started) {
            activateTemporaryHighPowerBoost()
            requestOrientationUpdates(forceRestart = true, reason = "recalibrate")
        }
        logDiagnostics("recalibrate requested")
    }

    override fun setNorthReferenceMode(
        mode: NorthReferenceMode,
        forceRefresh: Boolean,
    ) {
        val previousMode = northReferenceMode
        val modeChanged = previousMode != mode
        if (!modeChanged && !forceRefresh) return

        northReferenceMode = mode
        if (modeChanged) {
            val remappedHeading =
                remapHeadingForNorthReferenceSwitch(
                    currentHeadingDeg = _heading.value,
                    fromMode = previousMode,
                    toMode = mode,
                    declinationDeg = declinationController.currentDeclination,
                )
            if (remappedHeading.isFinite()) {
                _heading.value = remappedHeading
            }
        }
        publishNorthReferenceStatus()
    }

    override fun setHeadingSourceMode(
        mode: CompassHeadingSourceMode,
        forceRefresh: Boolean,
    ) = Unit

    override fun primeDeclinationFromApproximateLocation(
        latitude: Double,
        longitude: Double,
        altitudeM: Float,
    ) {
        declinationController.primeFromApproximateLocation(
            latitude = latitude,
            longitude = longitude,
            altitudeM = altitudeM,
        )
    }

    override fun updateDeclinationFromLocation(location: Location) {
        declinationController.updateFromLocation(location)
    }

    override fun setLowPowerMode(enabled: Boolean) {
        lowPowerMode = enabled
        if (!started) return
        if (_useFallbackProvider.value) {
            fallbackProvider.setLowPowerMode(enabled)
        } else {
            requestOrientationUpdates(
                forceRestart = true,
                reason = "set_low_power_mode",
            )
        }
    }

    private fun requestOrientationUpdates(
        forceRestart: Boolean,
        reason: String,
    ) {
        if (!started || _useFallbackProvider.value) return
        if (!forceRestart && orientationUpdatesRegistered) return

        stopOrientationUpdates()
        ensureCallbackHandler()
        refreshBootstrapFallbackProvider(reason = reason)
        val cachedHeadingAgeMs =
            googleFusedCachedHeadingAgeMs(
                renderState = ownRenderState.value,
                nowElapsedMs = SystemClock.elapsedRealtime(),
            )?.takeIf { it <= FUSED_WARM_RESTART_CACHED_HEADING_MAX_AGE_MS }

        headingRelockUntilElapsedMs = SystemClock.elapsedRealtime() + HEADING_RELOCK_WINDOW_MS
        fusedPendingJumpHeading = null
        fusedPendingJumpAtMs = 0L
        markHeadingPendingRestart()
        lastOrientationRequestAtElapsedMs = SystemClock.elapsedRealtime()
        lastOrientationRequestReason = reason
        awaitingFirstOrientationSample = true
        awaitingRestartHeadingConfirmation = true
        pendingRestartHeading = null
        pendingRestartHeadingAtElapsedMs = 0L
        pendingRestartHeadingSampleCount = 0
        lastFusedSampleLogAtElapsedMs = 0L

        val samplingPeriodMicros = currentSamplingPeriodMicros()
        val usingBoost = isRecalibrationBoostActive()
        logDiagnostics(
            "google_fused request reason=$reason forceRestart=$forceRestart " +
                "samplingMicros=$samplingPeriodMicros lowPower=$lowPowerMode " +
                "boostActive=$usingBoost cachedHeadingAgeMs=${cachedHeadingAgeMs ?: "na"}",
        )
        val request = DeviceOrientationRequest.Builder(samplingPeriodMicros).build()
        fusedOrientationClient
            .requestOrientationUpdates(
                request,
                callbackExecutor,
                orientationListener,
            ).addOnSuccessListener {
                if (!started || _useFallbackProvider.value) {
                    fusedOrientationClient.removeOrientationUpdates(orientationListener)
                    return@addOnSuccessListener
                }
                orientationUpdatesRegistered = true
                logDiagnostics(
                    "google_fused started reason=$reason samplingMicros=$samplingPeriodMicros " +
                        "boostActive=$usingBoost",
                )
            }.addOnFailureListener { error ->
                orientationUpdatesRegistered = false
                awaitingFirstOrientationSample = false
                clearRestartHeadingConfirmationState()
                logDiagnostics(
                    "google_fused start failed ${error.javaClass.simpleName}: ${error.message ?: "unknown"}",
                )
                startFallbackProvider(reason = "start_failed")
            }
    }

    private fun handleDeviceOrientation(orientation: DeviceOrientation) {
        if (!started || _useFallbackProvider.value) return
        val now = SystemClock.elapsedRealtime()
        val liveHeadingErrorDeg = orientation.headingErrorDegrees
        val conservativeHeadingErrorDeg =
            if (orientation.hasConservativeHeadingErrorDegrees()) {
                orientation.conservativeHeadingErrorDegrees
            } else {
                Float.NaN
            }
        val headingErrorDeg = resolveHeadingErrorDegrees(orientation)
        val displayHeading = fusedHeadingWithNorthReference(orientation.headingDegrees)
        val mappedAccuracy = headingAccuracyFromUncertainty(headingErrorDeg)

        if (awaitingFirstOrientationSample) {
            awaitingFirstOrientationSample = false
            logDiagnostics(
                "google_fused first_sample reason=$lastOrientationRequestReason " +
                    "latencyMs=${(now - lastOrientationRequestAtElapsedMs).coerceAtLeast(0L)} " +
                    "heading=${displayHeading.format(1)} errorDeg=${headingErrorDeg.formatOrNA(1)} " +
                    "liveErrorDeg=${liveHeadingErrorDeg.formatOrNA(1)} " +
                    "conservativeErrorDeg=${conservativeHeadingErrorDeg.formatOrNA(1)} " +
                    "acc=$mappedAccuracy",
            )
        }
        logFusedSample(
            nowElapsedMs = now,
            displayHeading = displayHeading,
            headingErrorDeg = headingErrorDeg,
            liveHeadingErrorDeg = liveHeadingErrorDeg,
            conservativeHeadingErrorDeg = conservativeHeadingErrorDeg,
            mappedAccuracy = mappedAccuracy,
        )

        if (awaitingRestartHeadingConfirmation) {
            val timeoutMs = restartHeadingConfirmationTimeoutMs()
            val decision =
                resolveFusedRestartHeadingDecision(
                    pendingHeadingDeg = pendingRestartHeading,
                    displayHeadingDeg = displayHeading,
                    pendingAtElapsedMs = pendingRestartHeadingAtElapsedMs,
                    nowElapsedMs = now,
                    pendingSampleCount = pendingRestartHeadingSampleCount,
                    timeoutMs = timeoutMs,
                )
            when (decision.action) {
                FusedRestartHeadingAction.IGNORE_FIRST -> {
                    pendingRestartHeading = decision.nextPendingHeadingDeg
                    pendingRestartHeadingAtElapsedMs = decision.nextPendingAtElapsedMs
                    pendingRestartHeadingSampleCount = decision.nextPendingSampleCount
                    logDiagnostics(
                        "google_fused restart_first_heading_ignored reason=$lastOrientationRequestReason " +
                            "heading=${displayHeading.format(1)}",
                    )
                    return
                }

                FusedRestartHeadingAction.AWAIT_PENDING -> {
                    pendingRestartHeading = decision.nextPendingHeadingDeg
                    pendingRestartHeadingAtElapsedMs = decision.nextPendingAtElapsedMs
                    pendingRestartHeadingSampleCount = decision.nextPendingSampleCount
                    if (decision.nextPendingSampleCount == 2) {
                        logDiagnostics(
                            "google_fused restart_heading_pending reason=$lastOrientationRequestReason " +
                                "heading=${displayHeading.format(1)} " +
                                "delta=${decision.deltaDeg.format(1)} " +
                                "delayMs=${decision.pendingAgeMs} timeoutMs=$timeoutMs",
                        )
                    }
                    return
                }

                FusedRestartHeadingAction.CONFIRM -> {
                    val pendingHeading = pendingRestartHeading
                    clearRestartHeadingConfirmationState()
                    stopBootstrapFallbackProvider(reason = "fused_confirmed")
                    logDiagnostics(
                        "google_fused restart_heading_confirmed reason=$lastOrientationRequestReason " +
                            "confirmReason=${decision.confirmReason} " +
                            "ignoredHeading=${pendingHeading.formatOrNA(1)} " +
                            "confirmedHeading=${displayHeading.format(1)} " +
                            "delta=${decision.deltaDeg.format(1)} " +
                            "delayMs=${decision.pendingAgeMs} timeoutMs=$timeoutMs " +
                            "samples=${decision.sampleCount}",
                    )
                }
            }
        }

        publishConfirmedFusedSampleState(
            nowElapsedMs = now,
            mappedAccuracy = mappedAccuracy,
            headingErrorDeg = headingErrorDeg,
            conservativeHeadingErrorDeg = conservativeHeadingErrorDeg,
        )

        // Pass through directly during settle window — Google's fusion needs a brief warmup.
        if ((now - startAtMs) < FUSED_ORIENTATION_SETTLE_WINDOW_MS) {
            _heading.value = displayHeading
            return
        }

        // Google's fusion is already smoothed internally; we only apply large-jump rejection
        // to guard against sensor glitches (≥120° spikes), not for smoothing.
        val inRelock = now < headingRelockUntilElapsedMs
        val currentHeading = _heading.value
        val jump = abs(shortestAngleDiffDeg(target = displayHeading, current = currentHeading))

        val pendingJump = fusedPendingJumpHeading
        val hasPendingJump =
            pendingJump != null &&
                (now - fusedPendingJumpAtMs) <= HEADING_LARGE_JUMP_CONFIRM_WINDOW_MS
        val pendingDelta =
            if (hasPendingJump) {
                abs(shortestAngleDiffDeg(target = displayHeading, current = pendingJump))
            } else {
                Float.NaN
            }

        when (resolveLargeJumpAction(jump, inRelock, hasPendingJump, pendingDelta)) {
            LargeJumpAction.ACCEPT_IMMEDIATE, LargeJumpAction.ACCEPT_CONFIRMED -> {
                if (!inRelock && jump > HEADING_LARGE_JUMP_REJECT_DEG) {
                    logDiagnostics(
                        "google_fused large_jump accepted jump=${jump.format(1)} " +
                            "pendingDelta=${pendingDelta.formatOrNA(1)}",
                    )
                }
                fusedPendingJumpHeading = null
                fusedPendingJumpAtMs = 0L
                _heading.value = displayHeading
            }
            LargeJumpAction.REJECT_PENDING -> {
                if (!hasPendingJump) {
                    logDiagnostics(
                        "google_fused large_jump pending jump=${jump.format(1)} " +
                            "heading=${displayHeading.format(1)} current=${currentHeading.format(1)}",
                    )
                }
                fusedPendingJumpHeading = displayHeading
                fusedPendingJumpAtMs = now
            }
            LargeJumpAction.NONE -> {
                if (fusedPendingJumpHeading != null) {
                    fusedPendingJumpHeading = null
                    fusedPendingJumpAtMs = 0L
                }
                _heading.value = displayHeading
            }
        }
    }

    private fun fusedHeadingWithNorthReference(headingDeg: Float): Float {
        val normalized = normalize360Deg(headingDeg)
        return when (northReferenceMode) {
            NorthReferenceMode.TRUE -> normalized
            NorthReferenceMode.MAGNETIC -> {
                val correction = declinationController.currentDeclination
                if (correction != null) {
                    normalize360Deg(normalized - correction)
                } else {
                    normalized
                }
            }
        }
    }

    private fun publishNorthReferenceStatus() {
        val declinationAvailable = declinationController.hasDeclination
        val effectiveMode =
            when (northReferenceMode) {
                NorthReferenceMode.TRUE ->
                    if (declinationAvailable) NorthReferenceMode.TRUE else NorthReferenceMode.MAGNETIC
                NorthReferenceMode.MAGNETIC -> NorthReferenceMode.MAGNETIC
            }
        val status =
            NorthReferenceStatus(
                requestedMode = northReferenceMode,
                effectiveMode = effectiveMode,
                declinationAvailable = declinationAvailable,
                waitingForDeclination = effectiveMode != northReferenceMode,
                pipeline = HeadingPipeline.NONE,
            )
        if (_northReferenceStatus.value == status) return
        _northReferenceStatus.value = status
        logDiagnostics(
            "north_reference_status requested=${status.requestedMode.name} " +
                "effective=${status.effectiveMode.name} declReady=${status.declinationAvailable} " +
                "waitingDecl=${status.waitingForDeclination} pipeline=${status.pipeline.name}",
        )
    }

    private fun updateHeadingSourceState(activeSource: HeadingSource) {
        if (_headingSource.value != activeSource) {
            _headingSource.value = activeSource
            logDiagnostics("heading_source ${activeSource.telemetryToken}")
        }
        val nextStatus =
            HeadingSourceStatus(
                requestedMode = CompassHeadingSourceMode.AUTO,
                activeSource = activeSource,
                headingSensorAvailable = googleOrientationAvailable,
                rotationVectorAvailable = false,
                magAccelFallbackAvailable = false,
            )
        if (_headingSourceStatus.value != nextStatus) {
            _headingSourceStatus.value = nextStatus
            logDiagnostics(
                "heading_source_status requested=${nextStatus.requestedMode.name} " +
                    "active=${nextStatus.activeSource.telemetryToken} " +
                    "headingAvailable=${nextStatus.headingSensorAvailable} " +
                    "rotVecAvailable=${nextStatus.rotationVectorAvailable} " +
                    "magFallbackAvailable=${nextStatus.magAccelFallbackAvailable}",
            )
        }
    }

    private fun startFallbackProvider(reason: String) {
        _useBootstrapFallbackProvider.value = false
        if (_useFallbackProvider.value) {
            logDiagnostics("google_fused fallback refresh reason=$reason")
            fallbackProvider.setLowPowerMode(lowPowerMode)
            fallbackProvider.setNorthReferenceMode(
                mode = northReferenceMode,
                forceRefresh = true,
            )
            fallbackProvider.setHeadingSourceMode(
                mode = CompassHeadingSourceMode.AUTO,
                forceRefresh = true,
            )
            if (started) {
                fallbackProvider.start(lowPower = lowPowerMode)
            }
            return
        }
        stopOrientationUpdates()
        awaitingFirstOrientationSample = false
        _useFallbackProvider.value = true
        logDiagnostics("google_fused fallback activate reason=$reason")
        fallbackProvider.setLowPowerMode(lowPowerMode)
        fallbackProvider.setNorthReferenceMode(
            mode = northReferenceMode,
            forceRefresh = true,
        )
        fallbackProvider.setHeadingSourceMode(
            mode = CompassHeadingSourceMode.AUTO,
            forceRefresh = true,
        )
        if (started) {
            fallbackProvider.start(lowPower = lowPowerMode)
        }
    }

    private fun stopFallbackProvider() {
        if (!_useFallbackProvider.value) return
        fallbackProvider.stop()
    }

    private fun refreshBootstrapFallbackProvider(reason: String) {
        if (!started || _useFallbackProvider.value) return
        val wasActive = _useBootstrapFallbackProvider.value
        _useBootstrapFallbackProvider.value = true
        fallbackProvider.setLowPowerMode(lowPowerMode)
        fallbackProvider.setNorthReferenceMode(
            mode = northReferenceMode,
            forceRefresh = true,
        )
        fallbackProvider.setHeadingSourceMode(
            mode = CompassHeadingSourceMode.AUTO,
            forceRefresh = true,
        )
        fallbackProvider.start(lowPower = lowPowerMode)
        logDiagnostics(
            "google_fused bootstrap ${if (wasActive) "refresh" else "activate"} reason=$reason",
        )
    }

    private fun stopBootstrapFallbackProvider(reason: String) {
        if (!_useBootstrapFallbackProvider.value || _useFallbackProvider.value) return
        _useBootstrapFallbackProvider.value = false
        fallbackProvider.stop()
        logDiagnostics("google_fused bootstrap stop reason=$reason")
    }

    private fun stopOrientationUpdates() {
        awaitingFirstOrientationSample = false
        clearRestartHeadingConfirmationState()
        if (!orientationUpdatesRegistered) return
        orientationUpdatesRegistered = false
        fusedOrientationClient.removeOrientationUpdates(orientationListener)
    }

    private fun clearRestartHeadingConfirmationState() {
        awaitingRestartHeadingConfirmation = false
        pendingRestartHeading = null
        pendingRestartHeadingAtElapsedMs = 0L
        pendingRestartHeadingSampleCount = 0
    }

    private fun restartHeadingConfirmationTimeoutMs(): Long =
        if (lowPowerMode && !isRecalibrationBoostActive()) {
            FUSED_RESTART_CONFIRM_TIMEOUT_LOW_POWER_MS
        } else {
            FUSED_RESTART_CONFIRM_TIMEOUT_HIGH_POWER_MS
        }

    private fun currentSamplingPeriodMicros(): Long =
        if (lowPowerMode && !isRecalibrationBoostActive()) {
            FUSED_ORIENTATION_LOW_POWER_SAMPLING_MICROS
        } else {
            FUSED_ORIENTATION_HIGH_POWER_SAMPLING_MICROS
        }

    private fun isGoogleOrientationAvailable(): Boolean =
        GoogleApiAvailability
            .getInstance()
            .isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS

    private fun logDiagnostics(message: String) {
        if (!DebugTelemetry.isEnabled()) return
        DebugTelemetry.log(COMPASS_TELEMETRY_TAG, message)
    }

    private fun markHeadingPendingRestart() {
        fusedSampleFreshnessGeneration += 1L
        _accuracy.value = SensorManager.SENSOR_STATUS_UNRELIABLE
        _headingErrorDeg.value = null
        _conservativeHeadingErrorDeg.value = null
        _headingSampleStale.value = _headingSampleElapsedRealtimeMs.value != null
        updateHeadingSourceState(HeadingSource.NONE)
    }

    private fun scheduleFusedSampleFreshnessTimeout(sampleAtElapsedMs: Long) {
        fusedSampleFreshnessGeneration += 1L
        val generation = fusedSampleFreshnessGeneration
        val handler = callbackHandler ?: return
        handler.postDelayed({
            if (generation != fusedSampleFreshnessGeneration) return@postDelayed
            if (!started || _useFallbackProvider.value) return@postDelayed
            if (_headingSampleElapsedRealtimeMs.value != sampleAtElapsedMs) return@postDelayed
            _headingSampleStale.value = true
            _accuracy.value = SensorManager.SENSOR_STATUS_UNRELIABLE
            logDiagnostics(
                "google_fused sample_stale ageMs=" +
                    (SystemClock.elapsedRealtime() - sampleAtElapsedMs).coerceAtLeast(0L),
            )
        }, FUSED_ORIENTATION_SAMPLE_STALE_MS)
    }

    private fun activateTemporaryHighPowerBoost() {
        if (!lowPowerMode) return
        val now = SystemClock.elapsedRealtime()
        recalibrationBoostUntilElapsedMs = now + FUSED_RECALIBRATION_HIGH_POWER_WINDOW_MS
        recalibrationBoostGeneration += 1L
        val generation = recalibrationBoostGeneration
        ensureCallbackHandler()
        val handler = callbackHandler ?: return
        logDiagnostics(
            "google_fused high_power_boost start durationMs=$FUSED_RECALIBRATION_HIGH_POWER_WINDOW_MS",
        )
        handler.postDelayed({
            if (generation != recalibrationBoostGeneration) return@postDelayed
            if (!started || _useFallbackProvider.value || !lowPowerMode) return@postDelayed
            if (isRecalibrationBoostActive()) return@postDelayed
            logDiagnostics("google_fused high_power_boost end")
            requestOrientationUpdates(forceRestart = true, reason = "high_power_boost_end")
        }, FUSED_RECALIBRATION_HIGH_POWER_WINDOW_MS)
    }

    private fun isRecalibrationBoostActive(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean = nowElapsedMs < recalibrationBoostUntilElapsedMs

    private fun resolveHeadingErrorDegrees(orientation: DeviceOrientation): Float {
        val liveErrorDeg = orientation.headingErrorDegrees
        if (liveErrorDeg.isFinite() && liveErrorDeg in 0f..<FUSED_INVALID_HEADING_ERROR_DEG) {
            return liveErrorDeg
        }
        if (orientation.hasConservativeHeadingErrorDegrees()) {
            val conservativeErrorDeg = orientation.conservativeHeadingErrorDegrees
            if (conservativeErrorDeg.isFinite() && conservativeErrorDeg >= 0f) {
                return conservativeErrorDeg
            }
        }
        return liveErrorDeg
    }

    private fun publishConfirmedFusedSampleState(
        nowElapsedMs: Long,
        mappedAccuracy: Int,
        headingErrorDeg: Float,
        conservativeHeadingErrorDeg: Float,
    ) {
        _accuracy.value = mappedAccuracy
        _headingErrorDeg.value = headingErrorDeg.takeIf { it.isFinite() && it >= 0f }
        _conservativeHeadingErrorDeg.value =
            conservativeHeadingErrorDeg.takeIf { it.isFinite() && it >= 0f }
        _headingSampleElapsedRealtimeMs.value = nowElapsedMs
        _headingSampleStale.value = false
        scheduleFusedSampleFreshnessTimeout(sampleAtElapsedMs = nowElapsedMs)
        updateHeadingSourceState(HeadingSource.FUSED_ORIENTATION)
    }

    private fun logFusedSample(
        nowElapsedMs: Long,
        displayHeading: Float,
        headingErrorDeg: Float,
        liveHeadingErrorDeg: Float,
        conservativeHeadingErrorDeg: Float,
        mappedAccuracy: Int,
    ) {
        if (nowElapsedMs - lastFusedSampleLogAtElapsedMs < HEADING_DEBUG_SAMPLE_MS) return
        lastFusedSampleLogAtElapsedMs = nowElapsedMs
        val northStatus = _northReferenceStatus.value
        logDiagnostics(
            "google_fused sample heading=${displayHeading.format(1)} " +
                "errorDeg=${headingErrorDeg.formatOrNA(1)} " +
                "liveErrorDeg=${liveHeadingErrorDeg.formatOrNA(1)} " +
                "conservativeErrorDeg=${conservativeHeadingErrorDeg.formatOrNA(1)} " +
                "acc=$mappedAccuracy requestedRef=${northStatus.requestedMode.name} " +
                "effectiveRef=${northStatus.effectiveMode.name} " +
                "declReady=${northStatus.declinationAvailable} " +
                "waitingDecl=${northStatus.waitingForDeclination} " +
                "relock=${nowElapsedMs < headingRelockUntilElapsedMs}",
        )
    }
}

internal fun shouldUseFusedBootstrapHeading(
    fusedRenderState: CompassRenderState,
    bootstrapRenderState: CompassRenderState,
    nowElapsedMs: Long,
): Boolean {
    val hasFreshFusedHeading =
        fusedRenderState.headingSource == HeadingSource.FUSED_ORIENTATION &&
            fusedRenderState.headingSampleElapsedRealtimeMs != null &&
            !fusedRenderState.headingSampleStale
    val hasRecentCachedFusedHeading =
        hasRecentGoogleFusedCachedHeading(
            renderState = fusedRenderState,
            nowElapsedMs = nowElapsedMs,
            maxAgeMs = FUSED_WARM_RESTART_CACHED_HEADING_MAX_AGE_MS,
        )
    val hasUsableBootstrapHeading =
        bootstrapRenderState.headingSource != HeadingSource.NONE &&
            bootstrapRenderState.accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE &&
            !bootstrapRenderState.magneticInterference
    return fusedRenderState.providerType == CompassProviderType.GOOGLE_FUSED &&
        !hasFreshFusedHeading &&
        !hasRecentCachedFusedHeading &&
        hasUsableBootstrapHeading
}

internal fun bootstrapFusedRenderState(
    fusedRenderState: CompassRenderState,
    bootstrapRenderState: CompassRenderState,
): CompassRenderState =
    fusedRenderState.copy(
        headingDeg = bootstrapRenderState.headingDeg,
        accuracy = bootstrapRenderState.accuracy,
        headingSource = bootstrapRenderState.headingSource,
        headingSourceStatus = bootstrapRenderState.headingSourceStatus,
        northReferenceStatus = bootstrapRenderState.northReferenceStatus,
        magneticInterference = bootstrapRenderState.magneticInterference,
    )

private const val FUSED_ORIENTATION_THREAD_NAME = "FusedOrientationThread"
private const val FUSED_ORIENTATION_SETTLE_WINDOW_MS = 250L
private const val FUSED_ORIENTATION_HIGH_POWER_SAMPLING_MICROS = 20_000L // 50 Hz
private const val FUSED_ORIENTATION_LOW_POWER_SAMPLING_MICROS = 200_000L // 5 Hz
private const val FUSED_INVALID_HEADING_ERROR_DEG = 180f
private const val FUSED_ORIENTATION_SAMPLE_STALE_MS = 1_500L
private const val FUSED_RECALIBRATION_HIGH_POWER_WINDOW_MS = 6_000L
private const val FUSED_WARM_RESTART_CACHED_HEADING_MAX_AGE_MS = 5_000L
private const val FUSED_RESTART_CONFIRM_DELTA_DEG = 2f
private const val FUSED_RESTART_CONFIRM_TIMEOUT_HIGH_POWER_MS = 160L
private const val FUSED_RESTART_CONFIRM_TIMEOUT_LOW_POWER_MS = 350L

internal enum class FusedRestartHeadingAction {
    IGNORE_FIRST,
    AWAIT_PENDING,
    CONFIRM,
}

internal data class FusedRestartHeadingDecision(
    val action: FusedRestartHeadingAction,
    val nextPendingHeadingDeg: Float?,
    val nextPendingAtElapsedMs: Long,
    val nextPendingSampleCount: Int,
    val sampleCount: Int,
    val deltaDeg: Float,
    val pendingAgeMs: Long,
    val confirmReason: String?,
)

internal fun resolveFusedRestartHeadingDecision(
    pendingHeadingDeg: Float?,
    displayHeadingDeg: Float,
    pendingAtElapsedMs: Long,
    nowElapsedMs: Long,
    pendingSampleCount: Int,
    timeoutMs: Long,
): FusedRestartHeadingDecision {
    if (pendingHeadingDeg == null) {
        return FusedRestartHeadingDecision(
            action = FusedRestartHeadingAction.IGNORE_FIRST,
            nextPendingHeadingDeg = displayHeadingDeg,
            nextPendingAtElapsedMs = nowElapsedMs,
            nextPendingSampleCount = 1,
            sampleCount = 1,
            deltaDeg = Float.NaN,
            pendingAgeMs = 0L,
            confirmReason = null,
        )
    }
    val pendingAgeMs = (nowElapsedMs - pendingAtElapsedMs).coerceAtLeast(0L)
    val deltaDeg =
        abs(
            shortestAngleDiffDeg(
                target = displayHeadingDeg,
                current = pendingHeadingDeg,
            ),
        )
    val sampleCount = pendingSampleCount + 1
    if (deltaDeg < FUSED_RESTART_CONFIRM_DELTA_DEG && pendingAgeMs < timeoutMs) {
        return FusedRestartHeadingDecision(
            action = FusedRestartHeadingAction.AWAIT_PENDING,
            nextPendingHeadingDeg = pendingHeadingDeg,
            nextPendingAtElapsedMs = pendingAtElapsedMs,
            nextPendingSampleCount = sampleCount,
            sampleCount = sampleCount,
            deltaDeg = deltaDeg,
            pendingAgeMs = pendingAgeMs,
            confirmReason = null,
        )
    }
    return FusedRestartHeadingDecision(
        action = FusedRestartHeadingAction.CONFIRM,
        nextPendingHeadingDeg = null,
        nextPendingAtElapsedMs = 0L,
        nextPendingSampleCount = 0,
        sampleCount = sampleCount,
        deltaDeg = deltaDeg,
        pendingAgeMs = pendingAgeMs,
        confirmReason =
            if (deltaDeg >= FUSED_RESTART_CONFIRM_DELTA_DEG) {
                "changed"
            } else {
                "timeout"
            },
    )
}
