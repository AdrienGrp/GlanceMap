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
    private val ownRenderState =
        MutableStateFlow(initialCompassRenderState(providerType = providerType))

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

    @Volatile private var orientationRequestGeneration = 0L

    @Volatile private var activeOrientationListener: DeviceOrientationListener? = null

    @Volatile private var dispatchedOrientationRequestGeneration = 0L

    // Cached once per start() — GoogleApiAvailability.isGooglePlayServicesAvailable() is a
    // cross-process binder call; caching avoids IPC overhead on every heading update.
    @Volatile private var googleOrientationAvailable = false

    @Volatile private var lastOrientationRequestAtElapsedMs = 0L

    @Volatile private var lastOrientationRequestReason: String = "idle"

    @Volatile private var firstSampleTimeoutMs = FUSED_FIRST_SAMPLE_TIMEOUT_MS

    @Volatile private var awaitingFirstOrientationSample = false

    @Volatile private var firstOrientationSampleLogged = false

    @Volatile private var awaitingRestartHeadingConfirmation = false

    @Volatile private var pendingRestartHeading: Float? = null

    @Volatile private var pendingRestartHeadingAtElapsedMs: Long = 0L

    @Volatile private var pendingRestartHeadingSampleCount = 0

    @Volatile private var lastFusedSampleLogAtElapsedMs = 0L

    @Volatile private var fusedPerfWindowStartElapsedMs = 0L

    @Volatile private var fusedPerfCallbackCount = 0

    @Volatile private var fusedPerfConfirmedCount = 0

    @Volatile private var fusedPerfUnusableCount = 0

    @Volatile private var fusedPerfHeadingPublishCount = 0

    @Volatile private var startupOverlapSampleCount = 0

    @Volatile private var startupOverlapDeltaTotalDeg = 0f

    @Volatile private var startupOverlapMaxDeltaDeg = 0f

    @Volatile private var startupOverlapFirstDeltaDeg: Float? = null

    @Volatile private var startupOverlapLastDeltaDeg: Float? = null

    @Volatile private var previousStartupOverlapFinalDeltaDeg: Float? = null

    @Volatile private var startupRestartReseedCount = 0

    @Volatile private var startupRestartMaxReseedDeltaDeg = 0f

    @Volatile private var unstableStartupRestartAttempted = false

    @Volatile private var startupBootstrapHoldUntilElapsedMs = 0L

    @Volatile private var consecutiveUnusableFusedSamples = 0

    @Volatile private var firstUnusableFusedSampleAtElapsedMs = 0L

    @Volatile private var recalibrationBoostUntilElapsedMs = 0L

    @Volatile private var recalibrationBoostGeneration = 0L

    // Jump rejection state — replaces the full smoothing pipeline.
    // Google's fusion already outputs a clean, smoothed heading; double-filtering adds lag.
    @Volatile private var fusedPendingJumpHeading: Float? = null

    @Volatile private var fusedPendingJumpAtMs: Long = 0L

    @Volatile private var fusedPendingJumpConsistentSampleCount = 0

    @Volatile private var lastFusedHeadingPublishAtElapsedMs = 0L

    @Volatile private var lastConfirmedFusedSampleElapsedRealtimeMs = 0L

    @Volatile private var warmRestartContinuityActive = false

    @Volatile private var fusedStaleRecoveryAttempted = false

    @Volatile private var fusedStaleRecoveryStartedAtElapsedMs = 0L

    @Volatile private var fusedFreshnessCheckScheduled = false

    @Volatile private var pendingBootstrapCompletion: FusedBootstrapCompletion? = null

    @Volatile private var pendingBootstrapPublishedSampleAtElapsedMs = 0L

    @Volatile private var callbackThread: HandlerThread? = null

    @Volatile private var callbackHandler: Handler? = null

    private val fusedSampleFreshnessRunnable: Runnable =
        Runnable {
            fusedFreshnessCheckScheduled = false
            if (!started || _useFallbackProvider.value) return@Runnable
            val sampleAtElapsedMs = lastConfirmedFusedSampleElapsedRealtimeMs
            if (sampleAtElapsedMs <= 0L) return@Runnable
            val sampleAgeMs =
                (SystemClock.elapsedRealtime() - sampleAtElapsedMs).coerceAtLeast(0L)
            if (sampleAgeMs < FUSED_ORIENTATION_SAMPLE_STALE_MS) {
                fusedFreshnessCheckScheduled = true
                callbackHandler?.postDelayed(
                    fusedSampleFreshnessRunnable,
                    FUSED_ORIENTATION_SAMPLE_STALE_MS - sampleAgeMs,
                )
                return@Runnable
            }
            _headingSampleStale.value = true
            if (fusedStaleRecoveryAttempted) {
                _accuracy.value = SensorManager.SENSOR_STATUS_UNRELIABLE
            }
            publishOwnRenderState()
            logDiagnostics(
                "google_fused sample_stale ageMs=$sampleAgeMs " +
                    "recoveryAttempted=$fusedStaleRecoveryAttempted",
            )
            if (!fusedStaleRecoveryAttempted) {
                fusedStaleRecoveryAttempted = true
                fusedStaleRecoveryStartedAtElapsedMs = SystemClock.elapsedRealtime()
                requestOrientationUpdates(
                    forceRestart = true,
                    reason = FUSED_STALE_SAMPLE_RETRY_REASON,
                )
            } else {
                startFallbackProvider(reason = "sample_stale")
            }
        }

    private val fusedFirstSampleTimeoutRunnable: Runnable =
        Runnable {
            if (!started || _useFallbackProvider.value || !awaitingFirstOrientationSample) {
                return@Runnable
            }
            val requestAgeMs =
                (SystemClock.elapsedRealtime() - lastOrientationRequestAtElapsedMs)
                    .coerceAtLeast(0L)
            val timeoutMs = firstSampleTimeoutMs
            if (requestAgeMs < timeoutMs) {
                callbackHandler?.postDelayed(
                    fusedFirstSampleTimeoutRunnable,
                    timeoutMs - requestAgeMs,
                )
                return@Runnable
            }
            logDiagnostics(
                "google_fused first_confirmed_sample_timeout reason=$lastOrientationRequestReason " +
                    "ageMs=$requestAgeMs timeoutMs=$timeoutMs",
            )
            startFallbackProvider(reason = "first_sample_timeout")
        }

    private val fusedBootstrapReleaseRunnable: Runnable =
        Runnable {
            val completion = pendingBootstrapCompletion ?: return@Runnable
            val publishedSampleAtElapsedMs = pendingBootstrapPublishedSampleAtElapsedMs
            val visibleState = renderState.value
            val visibleFusedSampleIsReady =
                visibleState.headingSource == HeadingSource.FUSED_ORIENTATION &&
                    !visibleState.headingSampleStale &&
                    (visibleState.headingSampleElapsedRealtimeMs ?: 0L) >=
                    publishedSampleAtElapsedMs
            val waitAgeMs =
                (SystemClock.elapsedRealtime() - publishedSampleAtElapsedMs).coerceAtLeast(0L)
            val handler = callbackHandler
            if (
                !visibleFusedSampleIsReady &&
                waitAgeMs < FUSED_BOOTSTRAP_RELEASE_MAX_WAIT_MS &&
                handler != null
            ) {
                handler.postDelayed(
                    fusedBootstrapReleaseRunnable,
                    FUSED_BOOTSTRAP_RELEASE_POLL_MS,
                )
                return@Runnable
            }
            pendingBootstrapCompletion = null
            pendingBootstrapPublishedSampleAtElapsedMs = 0L
            when (completion) {
                FusedBootstrapCompletion.STOP ->
                    stopBootstrapFallbackProvider(reason = "fused_confirmed")
                FusedBootstrapCompletion.HOLD_AFTER_UNSTABLE_START ->
                    holdBootstrapFallbackProvider(reason = "unstable_fused_confirmed")
            }
        }
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
        unstableStartupRestartAttempted = false
        startupBootstrapHoldUntilElapsedMs = 0L
        fusedStaleRecoveryAttempted = false
        fusedStaleRecoveryStartedAtElapsedMs = 0L
        fusedFreshnessCheckScheduled = false
        pendingBootstrapCompletion = null
        pendingBootstrapPublishedSampleAtElapsedMs = 0L
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
        if (recentUsableFusedHeadingAgeMs(startAtMs) == null) {
            updateHeadingSourceState(HeadingSource.NONE)
        }
        requestOrientationUpdates(forceRestart = true, reason = "start")
    }

    override fun stop() {
        val preserveRecentFusedHeading =
            recentUsableFusedHeadingAgeMs(SystemClock.elapsedRealtime()) != null
        started = false
        logStartupOverlapSummary(reason = "stop", confirmed = false)
        stopOrientationUpdates()
        callbackThread?.quitSafely()
        callbackThread = null
        callbackHandler = null
        stopBootstrapFallbackProvider(reason = "stop")
        stopFallbackProvider()
        _useFallbackProvider.value = false
        _useBootstrapFallbackProvider.value = false
        markHeadingPendingRestart(preserveRecentFusedHeading = preserveRecentFusedHeading)
        _magneticInterference.value = false
        headingRelockUntilElapsedMs = 0L
        fusedPendingJumpHeading = null
        fusedPendingJumpAtMs = 0L
        fusedPendingJumpConsistentSampleCount = 0
        lastFusedHeadingPublishAtElapsedMs = 0L
        lastConfirmedFusedSampleElapsedRealtimeMs = 0L
        warmRestartContinuityActive = false
        fusedStaleRecoveryAttempted = false
        fusedStaleRecoveryStartedAtElapsedMs = 0L
        fusedFreshnessCheckScheduled = false
        pendingBootstrapCompletion = null
        pendingBootstrapPublishedSampleAtElapsedMs = 0L
        recalibrationBoostUntilElapsedMs = 0L
        recalibrationBoostGeneration += 1L
        lastOrientationRequestAtElapsedMs = 0L
        lastOrientationRequestReason = "idle"
        firstSampleTimeoutMs = FUSED_FIRST_SAMPLE_TIMEOUT_MS
        unstableStartupRestartAttempted = false
        resetStartupRestartMetrics()
        startupBootstrapHoldUntilElapsedMs = 0L
        awaitingFirstOrientationSample = false
        firstOrientationSampleLogged = false
        clearRestartHeadingConfirmationState()
        lastFusedSampleLogAtElapsedMs = 0L
        resetStartupOverlapMetrics()
        resetUnusableFusedSampleState()
        resetFusedPerfCounters()
        publishNorthReferenceStatus()
    }

    override fun recalibrate() {
        if (_useFallbackProvider.value) {
            fallbackProvider.recalibrate()
            return
        }
        fusedPendingJumpHeading = null
        fusedPendingJumpAtMs = 0L
        fusedPendingJumpConsistentSampleCount = 0
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
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val cachedHeadingAgeMs = recentUsableFusedHeadingAgeMs(nowElapsedMs)
        warmRestartContinuityActive = cachedHeadingAgeMs != null

        prepareOrientationRequestState(
            reason = reason,
            preserveRecentFusedHeading = cachedHeadingAgeMs != null,
        )

        val samplingPeriodMicros = currentSamplingPeriodMicros()
        val usingBoost = isRecalibrationBoostActive()
        logDiagnostics(
            "google_fused request reason=$reason forceRestart=$forceRestart " +
                "samplingMicros=$samplingPeriodMicros lowPower=$lowPowerMode " +
                "boostActive=$usingBoost cachedHeadingAgeMs=${cachedHeadingAgeMs ?: "na"} " +
                "continuityHeld=$warmRestartContinuityActive",
        )
        registerOrientationRequest(
            samplingPeriodMicros = samplingPeriodMicros,
            usingBoost = usingBoost,
            reason = reason,
        )
    }

    private fun registerOrientationRequest(
        samplingPeriodMicros: Long,
        usingBoost: Boolean,
        reason: String,
    ) {
        val request = DeviceOrientationRequest.Builder(samplingPeriodMicros).build()
        val requestGeneration = orientationRequestGeneration + 1L
        orientationRequestGeneration = requestGeneration
        val requestListener =
            DeviceOrientationListener { orientation ->
                if (requestGeneration == orientationRequestGeneration) {
                    dispatchedOrientationRequestGeneration = requestGeneration
                    handleDeviceOrientation(orientation)
                }
            }
        activeOrientationListener = requestListener
        fusedOrientationClient
            .requestOrientationUpdates(
                request,
                callbackExecutor,
                requestListener,
            ).addOnSuccessListener {
                handleOrientationRequestStarted(
                    requestGeneration = requestGeneration,
                    requestListener = requestListener,
                    samplingPeriodMicros = samplingPeriodMicros,
                    usingBoost = usingBoost,
                    reason = reason,
                )
            }.addOnFailureListener { error ->
                handleOrientationRequestFailed(
                    requestGeneration = requestGeneration,
                    requestListener = requestListener,
                    error = error,
                )
            }
    }

    private fun handleOrientationRequestStarted(
        requestGeneration: Long,
        requestListener: DeviceOrientationListener,
        samplingPeriodMicros: Long,
        usingBoost: Boolean,
        reason: String,
    ) {
        if (!isCurrentOrientationRequest(requestGeneration, requestListener)) {
            fusedOrientationClient.removeOrientationUpdates(requestListener)
            return
        }
        if (!started || _useFallbackProvider.value) {
            activeOrientationListener = null
            fusedOrientationClient.removeOrientationUpdates(requestListener)
            return
        }
        orientationUpdatesRegistered = true
        logDiagnostics(
            "google_fused started reason=$reason samplingMicros=$samplingPeriodMicros " +
                "boostActive=$usingBoost",
        )
    }

    private fun handleOrientationRequestFailed(
        requestGeneration: Long,
        requestListener: DeviceOrientationListener,
        error: Exception,
    ) {
        if (!isCurrentOrientationRequest(requestGeneration, requestListener)) return
        activeOrientationListener = null
        orientationUpdatesRegistered = false
        awaitingFirstOrientationSample = false
        callbackHandler?.removeCallbacks(fusedFirstSampleTimeoutRunnable)
        clearRestartHeadingConfirmationState()
        logDiagnostics(
            "google_fused start failed ${error.javaClass.simpleName}: ${error.message ?: "unknown"}",
        )
        startFallbackProvider(reason = "start_failed")
    }

    private fun isCurrentOrientationRequest(
        requestGeneration: Long,
        requestListener: DeviceOrientationListener,
    ): Boolean =
        requestGeneration == orientationRequestGeneration &&
            activeOrientationListener === requestListener

    private fun prepareOrientationRequestState(
        reason: String,
        preserveRecentFusedHeading: Boolean,
    ) {
        if (reason != FUSED_UNSTABLE_STARTUP_RESTART_REASON) {
            resetStartupRestartMetrics()
            unstableStartupRestartAttempted = false
        }
        headingRelockUntilElapsedMs = SystemClock.elapsedRealtime() + HEADING_RELOCK_WINDOW_MS
        fusedPendingJumpHeading = null
        fusedPendingJumpAtMs = 0L
        fusedPendingJumpConsistentSampleCount = 0
        lastFusedHeadingPublishAtElapsedMs = 0L
        pendingBootstrapCompletion = null
        pendingBootstrapPublishedSampleAtElapsedMs = 0L
        markHeadingPendingRestart(
            preserveRecentFusedHeading = preserveRecentFusedHeading,
        )
        lastOrientationRequestAtElapsedMs = SystemClock.elapsedRealtime()
        lastOrientationRequestReason = reason
        firstSampleTimeoutMs =
            resolveFusedFirstConfirmedSampleTimeoutMs(
                requestReason = reason,
                lowPowerMode = lowPowerMode,
                recalibrationBoostActive = isRecalibrationBoostActive(),
            )
        awaitingFirstOrientationSample = true
        firstOrientationSampleLogged = false
        awaitingRestartHeadingConfirmation = true
        pendingRestartHeading = null
        pendingRestartHeadingAtElapsedMs = 0L
        pendingRestartHeadingSampleCount = 0
        lastFusedSampleLogAtElapsedMs = 0L
        startupBootstrapHoldUntilElapsedMs = 0L
        resetStartupOverlapMetrics()
        resetUnusableFusedSampleState()
        callbackHandler?.removeCallbacks(fusedFirstSampleTimeoutRunnable)
        callbackHandler?.postDelayed(
            fusedFirstSampleTimeoutRunnable,
            firstSampleTimeoutMs,
        )
    }

    private fun handleDeviceOrientation(orientation: DeviceOrientation) {
        val requestGeneration = dispatchedOrientationRequestGeneration
        if (!isActiveOrientationRequest(requestGeneration)) return
        val now = SystemClock.elapsedRealtime()
        recordFusedPerfCallback(now)
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

        if (!firstOrientationSampleLogged) {
            firstOrientationSampleLogged = true
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
        recordStartupOverlap(displayHeadingDeg = displayHeading)

        if (!isActiveOrientationRequest(requestGeneration)) return

        if (!isUsableGoogleFusedHeadingError(headingErrorDeg)) {
            publishUnusableFusedSampleState(
                headingErrorDeg = headingErrorDeg,
                conservativeHeadingErrorDeg = conservativeHeadingErrorDeg,
            )
            val unusableUpdate =
                computeFusedUnusableHeadingUpdate(
                    nowElapsedMs = now,
                    consecutiveUnusableSamples = consecutiveUnusableFusedSamples,
                    firstUnusableSampleAtElapsedMs = firstUnusableFusedSampleAtElapsedMs,
                    minSamples = FUSED_UNUSABLE_HEADING_FALLBACK_MIN_SAMPLES,
                    minDurationMs = FUSED_UNUSABLE_HEADING_FALLBACK_MIN_DURATION_MS,
                )
            consecutiveUnusableFusedSamples = unusableUpdate.state.consecutiveSamples
            firstUnusableFusedSampleAtElapsedMs = unusableUpdate.state.firstSampleAtElapsedMs
            if (unusableUpdate.shouldFallback) {
                logDiagnostics(
                    "google_fused unusable_heading fallback " +
                        "samples=${unusableUpdate.state.consecutiveSamples} " +
                        "durationMs=${unusableUpdate.durationMs} " +
                        "errorDeg=${headingErrorDeg.formatOrNA(1)} " +
                        "conservativeErrorDeg=${conservativeHeadingErrorDeg.formatOrNA(1)}",
                )
                startFallbackProvider(reason = "unusable_heading")
            }
            return
        }
        resetUnusableFusedSampleState()

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
                    headingErrorDeg = headingErrorDeg,
                    conservativeHeadingErrorDeg = conservativeHeadingErrorDeg,
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
                    val previousPendingHeading = pendingRestartHeading
                    val previousPendingSampleCount = pendingRestartHeadingSampleCount
                    val reseededPendingHeading =
                        previousPendingHeading != null &&
                            decision.nextPendingSampleCount == 1 &&
                            decision.nextPendingHeadingDeg != previousPendingHeading
                    if (reseededPendingHeading) {
                        startupRestartReseedCount += 1
                        startupRestartMaxReseedDeltaDeg =
                            maxOf(startupRestartMaxReseedDeltaDeg, decision.deltaDeg)
                    }
                    pendingRestartHeading = decision.nextPendingHeadingDeg
                    pendingRestartHeadingAtElapsedMs = decision.nextPendingAtElapsedMs
                    pendingRestartHeadingSampleCount = decision.nextPendingSampleCount
                    when {
                        reseededPendingHeading ->
                            logDiagnostics(
                                "google_fused restart_heading_reseed reason=$lastOrientationRequestReason " +
                                    "candidateHeading=${previousPendingHeading.formatOrNA(1)} " +
                                    "replacementHeading=${displayHeading.format(1)} heading=${displayHeading.format(1)} " +
                                    "delta=${decision.deltaDeg.format(1)} " +
                                    "stableSamples=$previousPendingSampleCount " +
                                    "stableAgeMs=${decision.pendingAgeMs} delayMs=${decision.pendingAgeMs} timeoutMs=$timeoutMs " +
                                    "errorDeg=${headingErrorDeg.formatOrNA(1)} " +
                                    "conservativeErrorDeg=${conservativeHeadingErrorDeg.formatOrNA(1)}",
                            )
                        decision.nextPendingSampleCount == 2 ->
                            logDiagnostics(
                                "google_fused restart_heading_pending reason=$lastOrientationRequestReason " +
                                    "heading=${displayHeading.format(1)} " +
                                    "delta=${decision.deltaDeg.format(1)} " +
                                    "stableSamples=${decision.sampleCount} " +
                                    "stableAgeMs=${decision.pendingAgeMs} delayMs=${decision.pendingAgeMs} timeoutMs=$timeoutMs " +
                                    "errorDeg=${headingErrorDeg.formatOrNA(1)} " +
                                    "conservativeErrorDeg=${conservativeHeadingErrorDeg.formatOrNA(1)}",
                            )
                    }
                    return
                }

                FusedRestartHeadingAction.CONFIRM -> {
                    val pendingHeading = pendingRestartHeading
                    val unstableStartup =
                        isUnstableFusedStartup(
                            decision = decision,
                            overlapMaxDeltaDeg = startupOverlapMaxDeltaDeg,
                            overlapFinalDeltaDeg = startupOverlapLastDeltaDeg ?: Float.NaN,
                            reseedCount = startupRestartReseedCount,
                            maxReseedDeltaDeg = startupRestartMaxReseedDeltaDeg,
                        )
                    if (unstableStartup && !unstableStartupRestartAttempted) {
                        unstableStartupRestartAttempted = true
                        logStartupOverlapSummary(reason = lastOrientationRequestReason, confirmed = false)
                        logDiagnostics(
                            "google_fused startup_unstable_restart reason=$lastOrientationRequestReason " +
                                "confirmedHeading=${displayHeading.format(1)} " +
                                "ignoredHeading=${pendingHeading.formatOrNA(1)} " +
                                "stableAgeMs=${decision.pendingAgeMs} timeoutMs=$timeoutMs " +
                                "stableSamples=${decision.sampleCount} " +
                                "reseedCount=$startupRestartReseedCount " +
                                "maxReseedDeltaDeg=${startupRestartMaxReseedDeltaDeg.format(1)} " +
                                "overlapMaxDeltaDeg=${startupOverlapMaxDeltaDeg.format(1)} " +
                                "errorDeg=${headingErrorDeg.formatOrNA(1)} " +
                                "conservativeErrorDeg=${conservativeHeadingErrorDeg.formatOrNA(1)}",
                        )
                        restartOrientationAfterUnstableStartup()
                        return
                    }
                    clearRestartHeadingConfirmationState()
                    seedFusedHandoffFromVisibleBootstrapIfNeeded()
                    logStartupOverlapSummary(reason = lastOrientationRequestReason, confirmed = true)
                    pendingBootstrapCompletion =
                        if (unstableStartup) {
                            FusedBootstrapCompletion.HOLD_AFTER_UNSTABLE_START
                        } else {
                            FusedBootstrapCompletion.STOP
                        }
                    logDiagnostics(
                        "google_fused restart_heading_confirmed reason=$lastOrientationRequestReason " +
                            "confirmReason=${decision.confirmReason} " +
                            "ignoredHeading=${pendingHeading.formatOrNA(1)} " +
                            "confirmedHeading=${displayHeading.format(1)} " +
                            "delta=${decision.deltaDeg.format(1)} " +
                            "stableAgeMs=${decision.pendingAgeMs} delayMs=${decision.pendingAgeMs} timeoutMs=$timeoutMs " +
                            "stableSamples=${decision.sampleCount} samples=${decision.sampleCount} " +
                            "errorDeg=${headingErrorDeg.formatOrNA(1)} " +
                            "conservativeErrorDeg=${conservativeHeadingErrorDeg.formatOrNA(1)}",
                    )
                }
            }
        }

        if (!isActiveOrientationRequest(requestGeneration)) return
        recordConfirmedFusedSample(nowElapsedMs = now)

        // Google's fusion needs a brief warmup. The first confirmed sample is always
        // published; later samples are coalesced so over-delivering devices cannot drive
        // Compose and Mapsforge at their callback rate.
        if ((now - startAtMs) < FUSED_ORIENTATION_SETTLE_WINDOW_MS) {
            publishFusedHeadingIfDue(
                displayHeading = displayHeading,
                nowElapsedMs = now,
                mappedAccuracy = mappedAccuracy,
                headingErrorDeg = headingErrorDeg,
                conservativeHeadingErrorDeg = conservativeHeadingErrorDeg,
            )
            return
        }

        // Google's fusion is already smoothed internally; we only apply large-jump rejection
        // to guard against sensor glitches (≥120° spikes), not for smoothing.
        val inRelock = now < headingRelockUntilElapsedMs
        val currentHeading = _heading.value
        val jump = abs(shortestAngleDiffDeg(target = displayHeading, current = currentHeading))

        val pendingJump = fusedPendingJumpHeading
        val hasPendingJump = pendingJump != null
        val pendingDelta =
            if (hasPendingJump) {
                abs(shortestAngleDiffDeg(target = displayHeading, current = pendingJump))
            } else {
                Float.NaN
            }
        val pendingAgeMs =
            if (hasPendingJump) {
                (now - fusedPendingJumpAtMs).coerceAtLeast(0L)
            } else {
                0L
            }
        val consistentPendingSampleCount =
            when {
                !hasPendingJump -> 0
                pendingDelta.isFinite() && pendingDelta <= FUSED_FAST_TURN_CONFIRM_MAX_DELTA_DEG ->
                    fusedPendingJumpConsistentSampleCount + 1
                else -> 1
            }

        val largeJumpAction =
            resolveFusedLargeJumpAction(
                FusedLargeJumpInput(
                    jumpDeg = jump,
                    inRelock = inRelock,
                    hasPendingLargeJump = hasPendingJump,
                    pendingDeltaDeg = pendingDelta,
                    pendingAgeMs = pendingAgeMs,
                    pendingConsistentSampleCount = consistentPendingSampleCount,
                    headingErrorDeg = headingErrorDeg,
                    conservativeHeadingErrorDeg = conservativeHeadingErrorDeg,
                ),
            )

        when (largeJumpAction) {
            LargeJumpAction.ACCEPT_IMMEDIATE, LargeJumpAction.ACCEPT_CONFIRMED -> {
                if (!inRelock && jump > HEADING_LARGE_JUMP_REJECT_DEG) {
                    val acceptanceReason =
                        fusedLargeJumpAcceptanceReason(
                            action = largeJumpAction,
                            pendingAgeMs = pendingAgeMs,
                        )
                    logDiagnostics(
                        "google_fused large_jump accepted jump=${jump.format(1)} " +
                            "pendingDelta=${pendingDelta.formatOrNA(1)} " +
                            "pendingAgeMs=$pendingAgeMs " +
                            "consistentSamples=$consistentPendingSampleCount " +
                            "acceptReason=${acceptanceReason.telemetryToken}",
                    )
                }
                fusedPendingJumpHeading = null
                fusedPendingJumpAtMs = 0L
                fusedPendingJumpConsistentSampleCount = 0
                forcePublishFusedHeading(
                    displayHeading = displayHeading,
                    nowElapsedMs = now,
                    mappedAccuracy = mappedAccuracy,
                    headingErrorDeg = headingErrorDeg,
                    conservativeHeadingErrorDeg = conservativeHeadingErrorDeg,
                )
            }
            LargeJumpAction.REJECT_PENDING -> {
                if (!hasPendingJump) {
                    logDiagnostics(
                        "google_fused large_jump pending jump=${jump.format(1)} " +
                            "heading=${displayHeading.format(1)} current=${currentHeading.format(1)} " +
                            "consistentSamples=1",
                    )
                }
                fusedPendingJumpHeading = displayHeading
                fusedPendingJumpConsistentSampleCount =
                    if (hasPendingJump) consistentPendingSampleCount else 1
                if (!hasPendingJump) {
                    fusedPendingJumpAtMs = now
                }
            }
            LargeJumpAction.NONE -> {
                if (fusedPendingJumpHeading != null) {
                    fusedPendingJumpHeading = null
                    fusedPendingJumpAtMs = 0L
                    fusedPendingJumpConsistentSampleCount = 0
                }
                publishFusedHeadingIfDue(
                    displayHeading = displayHeading,
                    nowElapsedMs = now,
                    mappedAccuracy = mappedAccuracy,
                    headingErrorDeg = headingErrorDeg,
                    conservativeHeadingErrorDeg = conservativeHeadingErrorDeg,
                )
            }
        }
    }

    private fun publishFusedHeadingIfDue(
        displayHeading: Float,
        nowElapsedMs: Long,
        mappedAccuracy: Int,
        headingErrorDeg: Float,
        conservativeHeadingErrorDeg: Float,
    ) {
        if (
            !shouldPublishFusedHeading(
                nowElapsedMs = nowElapsedMs,
                lastPublishAtElapsedMs = lastFusedHeadingPublishAtElapsedMs,
                lowPowerMode = lowPowerMode,
                force = false,
            )
        ) {
            return
        }
        val wasWarmRestartContinuityActive = warmRestartContinuityActive
        val headingToPublish =
            if (wasWarmRestartContinuityActive) {
                boundedFusedHandoffHeading(
                    currentHeadingDeg = _heading.value,
                    targetHeadingDeg = displayHeading,
                    maxStepDeg = FUSED_WARM_RESTART_MAX_HANDOFF_STEP_DEG,
                )
            } else {
                displayHeading
            }
        if (wasWarmRestartContinuityActive) {
            val remainingDeltaDeg =
                abs(
                    shortestAngleDiffDeg(
                        target = displayHeading,
                        current = headingToPublish,
                    ),
                )
            warmRestartContinuityActive = remainingDeltaDeg >= 0.001f
            if (!warmRestartContinuityActive) {
                logDiagnostics("google_fused warm_handoff_complete heading=${headingToPublish.format(1)}")
            }
        }
        _heading.value = headingToPublish
        _accuracy.value = mappedAccuracy
        _headingErrorDeg.value = headingErrorDeg.takeIf { it.isFinite() && it >= 0f }
        _conservativeHeadingErrorDeg.value =
            conservativeHeadingErrorDeg.takeIf { it.isFinite() && it >= 0f }
        _headingSampleElapsedRealtimeMs.value = nowElapsedMs
        _headingSampleStale.value = false
        updateHeadingSourceState(HeadingSource.FUSED_ORIENTATION)
        lastFusedHeadingPublishAtElapsedMs = nowElapsedMs
        recordFusedPerfHeadingPublish(nowElapsedMs)
        completePendingBootstrapAfterFusedPublish(sampleAtElapsedMs = nowElapsedMs)
    }

    private fun forcePublishFusedHeading(
        displayHeading: Float,
        nowElapsedMs: Long,
        mappedAccuracy: Int,
        headingErrorDeg: Float,
        conservativeHeadingErrorDeg: Float,
    ) {
        lastFusedHeadingPublishAtElapsedMs = 0L
        publishFusedHeadingIfDue(
            displayHeading = displayHeading,
            nowElapsedMs = nowElapsedMs,
            mappedAccuracy = mappedAccuracy,
            headingErrorDeg = headingErrorDeg,
            conservativeHeadingErrorDeg = conservativeHeadingErrorDeg,
        )
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
        if (_northReferenceStatus.value != status) {
            _northReferenceStatus.value = status
            logDiagnostics(
                "north_reference_status requested=${status.requestedMode.name} " +
                    "effective=${status.effectiveMode.name} declReady=${status.declinationAvailable} " +
                    "waitingDecl=${status.waitingForDeclination} pipeline=${status.pipeline.name}",
            )
        }
        publishOwnRenderState()
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
        publishOwnRenderState()
    }

    private fun publishOwnRenderState() {
        ownRenderState.value =
            CompassRenderState(
                providerType = providerType,
                headingDeg = _heading.value,
                accuracy = _accuracy.value,
                headingErrorDeg = _headingErrorDeg.value,
                conservativeHeadingErrorDeg = _conservativeHeadingErrorDeg.value,
                headingSampleElapsedRealtimeMs = _headingSampleElapsedRealtimeMs.value,
                headingSampleStale = _headingSampleStale.value,
                headingSource = _headingSource.value,
                headingSourceStatus = _headingSourceStatus.value,
                northReferenceStatus = _northReferenceStatus.value,
                magneticInterference = _magneticInterference.value,
            )
    }

    private fun startFallbackProvider(reason: String) {
        pendingBootstrapCompletion = null
        pendingBootstrapPublishedSampleAtElapsedMs = 0L
        callbackHandler?.removeCallbacks(fusedBootstrapReleaseRunnable)
        warmRestartContinuityActive = false
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

    private fun holdBootstrapFallbackProvider(reason: String) {
        if (!_useBootstrapFallbackProvider.value || _useFallbackProvider.value) return
        val untilElapsedMs = SystemClock.elapsedRealtime() + FUSED_UNSTABLE_STARTUP_BOOTSTRAP_HOLD_MS
        startupBootstrapHoldUntilElapsedMs = maxOf(startupBootstrapHoldUntilElapsedMs, untilElapsedMs)
        logDiagnostics(
            "google_fused bootstrap hold reason=$reason " +
                "durationMs=$FUSED_UNSTABLE_STARTUP_BOOTSTRAP_HOLD_MS",
        )
        val stopRunnable =
            Runnable {
                val nowElapsedMs = SystemClock.elapsedRealtime()
                if (started && nowElapsedMs < startupBootstrapHoldUntilElapsedMs) return@Runnable
                startupBootstrapHoldUntilElapsedMs = 0L
                stopBootstrapFallbackProvider(reason = "${reason}_hold_elapsed")
            }
        if (callbackHandler?.postDelayed(stopRunnable, FUSED_UNSTABLE_STARTUP_BOOTSTRAP_HOLD_MS) != true) {
            stopRunnable.run()
        }
    }

    private fun restartOrientationAfterUnstableStartup() {
        clearRestartHeadingConfirmationState()
        fusedPendingJumpHeading = null
        fusedPendingJumpAtMs = 0L
        fusedPendingJumpConsistentSampleCount = 0
        val restartRunnable =
            Runnable {
                if (!started || _useFallbackProvider.value) return@Runnable
                requestOrientationUpdates(
                    forceRestart = true,
                    reason = FUSED_UNSTABLE_STARTUP_RESTART_REASON,
                )
            }
        if (callbackHandler?.post(restartRunnable) != true) {
            restartRunnable.run()
        }
    }

    private fun stopOrientationUpdates() {
        orientationRequestGeneration += 1L
        dispatchedOrientationRequestGeneration = 0L
        val listenerToRemove = activeOrientationListener
        activeOrientationListener = null
        awaitingFirstOrientationSample = false
        clearRestartHeadingConfirmationState()
        callbackHandler?.removeCallbacks(fusedSampleFreshnessRunnable)
        fusedFreshnessCheckScheduled = false
        callbackHandler?.removeCallbacks(fusedFirstSampleTimeoutRunnable)
        callbackHandler?.removeCallbacks(fusedBootstrapReleaseRunnable)
        pendingBootstrapCompletion = null
        pendingBootstrapPublishedSampleAtElapsedMs = 0L
        orientationUpdatesRegistered = false
        if (listenerToRemove != null) {
            fusedOrientationClient.removeOrientationUpdates(listenerToRemove)
        }
    }

    private fun clearRestartHeadingConfirmationState() {
        awaitingRestartHeadingConfirmation = false
        pendingRestartHeading = null
        pendingRestartHeadingAtElapsedMs = 0L
        pendingRestartHeadingSampleCount = 0
    }

    private fun resetStartupRestartMetrics() {
        startupRestartReseedCount = 0
        startupRestartMaxReseedDeltaDeg = 0f
    }

    private fun resetUnusableFusedSampleState() {
        consecutiveUnusableFusedSamples = 0
        firstUnusableFusedSampleAtElapsedMs = 0L
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

    private fun isActiveOrientationRequest(requestGeneration: Long): Boolean =
        started &&
            !_useFallbackProvider.value &&
            requestGeneration == orientationRequestGeneration

    private fun isGoogleOrientationAvailable(): Boolean =
        GoogleApiAvailability
            .getInstance()
            .isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS

    private fun logDiagnostics(message: String) {
        if (!DebugTelemetry.isEnabled()) return
        DebugTelemetry.log(COMPASS_TELEMETRY_TAG, message)
    }

    private fun markHeadingPendingRestart(preserveRecentFusedHeading: Boolean) {
        callbackHandler?.removeCallbacks(fusedSampleFreshnessRunnable)
        fusedFreshnessCheckScheduled = false
        _headingSampleStale.value = _headingSampleElapsedRealtimeMs.value != null
        if (!preserveRecentFusedHeading) {
            _accuracy.value = SensorManager.SENSOR_STATUS_UNRELIABLE
            _headingErrorDeg.value = null
            _conservativeHeadingErrorDeg.value = null
            updateHeadingSourceState(HeadingSource.NONE)
        } else {
            publishOwnRenderState()
        }
    }

    private fun scheduleFusedSampleFreshnessTimeout(sampleAtElapsedMs: Long) {
        val handler = callbackHandler
        if (
            handler != null &&
            lastConfirmedFusedSampleElapsedRealtimeMs == sampleAtElapsedMs &&
            !fusedFreshnessCheckScheduled
        ) {
            fusedFreshnessCheckScheduled = true
            handler.postDelayed(
                fusedSampleFreshnessRunnable,
                FUSED_ORIENTATION_SAMPLE_STALE_MS,
            )
        }
    }

    private fun recentUsableFusedHeadingAgeMs(nowElapsedMs: Long): Long? {
        val sampleAtElapsedMs = _headingSampleElapsedRealtimeMs.value
        val hasUsableFusedState =
            !_useFallbackProvider.value &&
                _headingSource.value == HeadingSource.FUSED_ORIENTATION
        val hasUsableAccuracy = _accuracy.value != SensorManager.SENSOR_STATUS_UNRELIABLE
        return if (
            hasUsableFusedState && hasUsableAccuracy && sampleAtElapsedMs != null
        ) {
            (nowElapsedMs - sampleAtElapsedMs)
                .coerceAtLeast(0L)
                .takeIf { it <= FUSED_WARM_RESTART_CACHED_HEADING_MAX_AGE_MS }
        } else {
            null
        }
    }

    private fun seedFusedHandoffFromVisibleBootstrapIfNeeded() {
        if (warmRestartContinuityActive || !_useBootstrapFallbackProvider.value) return
        val bootstrapState = fallbackProvider.renderState.value
        val canUseBootstrapHeading =
            bootstrapState.headingSource != HeadingSource.NONE &&
                bootstrapState.accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE &&
                bootstrapState.headingSampleElapsedRealtimeMs != null &&
                !bootstrapState.headingSampleStale &&
                !bootstrapState.magneticInterference &&
                bootstrapState.headingDeg.isFinite()
        if (!canUseBootstrapHeading) return
        _heading.value = bootstrapState.headingDeg
        warmRestartContinuityActive = true
        logDiagnostics(
            "google_fused handoff_seed bootstrapHeading=${bootstrapState.headingDeg.format(1)}",
        )
    }

    private fun completePendingBootstrapAfterFusedPublish(sampleAtElapsedMs: Long) {
        if (pendingBootstrapCompletion == null) return
        if (pendingBootstrapPublishedSampleAtElapsedMs > 0L) return
        pendingBootstrapPublishedSampleAtElapsedMs = sampleAtElapsedMs
        if (
            callbackHandler?.postDelayed(
                fusedBootstrapReleaseRunnable,
                FUSED_BOOTSTRAP_RELEASE_POLL_MS,
            ) != true
        ) {
            fusedBootstrapReleaseRunnable.run()
        }
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

    private fun recordConfirmedFusedSample(nowElapsedMs: Long) {
        // Keep freshness tied to the full fused callback stream, but only publish render state
        // alongside the rate-limited heading in publishFusedHeadingIfDue(). This prevents
        // over-delivering devices from invalidating the 25 Hz UI publication cap.
        awaitingFirstOrientationSample = false
        callbackHandler?.removeCallbacks(fusedFirstSampleTimeoutRunnable)
        lastConfirmedFusedSampleElapsedRealtimeMs = nowElapsedMs
        val staleRecoveryHealthyMs =
            (nowElapsedMs - fusedStaleRecoveryStartedAtElapsedMs).coerceAtLeast(0L)
        if (
            fusedStaleRecoveryAttempted &&
            fusedStaleRecoveryStartedAtElapsedMs > 0L &&
            staleRecoveryHealthyMs >= FUSED_STALE_RECOVERY_HEALTHY_RESET_MS
        ) {
            fusedStaleRecoveryAttempted = false
            fusedStaleRecoveryStartedAtElapsedMs = 0L
            logDiagnostics(
                "google_fused stale_recovery_healthy durationMs=$staleRecoveryHealthyMs",
            )
        }
        recordFusedPerfConfirmed(nowElapsedMs)
        scheduleFusedSampleFreshnessTimeout(sampleAtElapsedMs = nowElapsedMs)
    }

    private fun publishUnusableFusedSampleState(
        headingErrorDeg: Float,
        conservativeHeadingErrorDeg: Float,
    ) {
        _accuracy.value = SensorManager.SENSOR_STATUS_UNRELIABLE
        _headingErrorDeg.value = headingErrorDeg.takeIf { it.isFinite() && it >= 0f }
        _conservativeHeadingErrorDeg.value =
            conservativeHeadingErrorDeg.takeIf { it.isFinite() && it >= 0f }
        _headingSampleStale.value = _headingSampleElapsedRealtimeMs.value != null
        publishOwnRenderState()
        recordFusedPerfUnusable(SystemClock.elapsedRealtime())
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

    private fun recordFusedPerfCallback(nowElapsedMs: Long) {
        if (!DebugTelemetry.isEnabled()) return
        ensureFusedPerfWindow(nowElapsedMs)
        fusedPerfCallbackCount += 1
        maybeLogFusedPerf(nowElapsedMs)
    }

    private fun recordFusedPerfConfirmed(nowElapsedMs: Long) {
        if (!DebugTelemetry.isEnabled()) return
        ensureFusedPerfWindow(nowElapsedMs)
        fusedPerfConfirmedCount += 1
        maybeLogFusedPerf(nowElapsedMs)
    }

    private fun recordFusedPerfUnusable(nowElapsedMs: Long) {
        if (!DebugTelemetry.isEnabled()) return
        ensureFusedPerfWindow(nowElapsedMs)
        fusedPerfUnusableCount += 1
        maybeLogFusedPerf(nowElapsedMs)
    }

    private fun recordFusedPerfHeadingPublish(nowElapsedMs: Long) {
        if (!DebugTelemetry.isEnabled()) return
        ensureFusedPerfWindow(nowElapsedMs)
        fusedPerfHeadingPublishCount += 1
        maybeLogFusedPerf(nowElapsedMs)
    }

    private fun ensureFusedPerfWindow(nowElapsedMs: Long) {
        if (fusedPerfWindowStartElapsedMs == 0L) {
            fusedPerfWindowStartElapsedMs = nowElapsedMs
        }
    }

    private fun maybeLogFusedPerf(nowElapsedMs: Long) {
        val windowStart = fusedPerfWindowStartElapsedMs
        if (windowStart == 0L) return
        val windowMs = (nowElapsedMs - windowStart).coerceAtLeast(0L)
        if (windowMs < FUSED_PERF_LOG_WINDOW_MS) return
        val seconds = (windowMs / 1000f).coerceAtLeast(0.001f)
        logDiagnostics(
            "google_fused perf windowMs=$windowMs " +
                "callbacks=$fusedPerfCallbackCount callbackHz=${(fusedPerfCallbackCount / seconds).format(1)} " +
                "confirmed=$fusedPerfConfirmedCount confirmedHz=${(fusedPerfConfirmedCount / seconds).format(1)} " +
                "unusable=$fusedPerfUnusableCount " +
                "headingPublishes=$fusedPerfHeadingPublishCount " +
                "publishHz=${(fusedPerfHeadingPublishCount / seconds).format(1)}",
        )
        resetFusedPerfCounters(nowElapsedMs)
    }

    private fun resetFusedPerfCounters(windowStartElapsedMs: Long = 0L) {
        fusedPerfWindowStartElapsedMs = windowStartElapsedMs
        fusedPerfCallbackCount = 0
        fusedPerfConfirmedCount = 0
        fusedPerfUnusableCount = 0
        fusedPerfHeadingPublishCount = 0
    }

    private fun recordStartupOverlap(displayHeadingDeg: Float) {
        if (!_useBootstrapFallbackProvider.value || !displayHeadingDeg.isFinite()) return
        val fallbackState = fallbackProvider.renderState.value
        if (
            fallbackState.headingSource == HeadingSource.NONE ||
            !fallbackState.headingDeg.isFinite() ||
            fallbackState.headingSampleStale
        ) {
            return
        }
        val deltaDeg =
            abs(
                shortestAngleDiffDeg(
                    target = displayHeadingDeg,
                    current = fallbackState.headingDeg,
                ),
            )
        startupOverlapSampleCount += 1
        startupOverlapDeltaTotalDeg += deltaDeg
        startupOverlapMaxDeltaDeg = maxOf(startupOverlapMaxDeltaDeg, deltaDeg)
        if (startupOverlapFirstDeltaDeg == null) startupOverlapFirstDeltaDeg = deltaDeg
        startupOverlapLastDeltaDeg = deltaDeg
    }

    private fun logStartupOverlapSummary(
        reason: String,
        confirmed: Boolean,
    ) {
        val samples = startupOverlapSampleCount
        if (samples <= 0) return
        val finalDeltaDeg = startupOverlapLastDeltaDeg
        val previousFinalDeltaDeg = previousStartupOverlapFinalDeltaDeg
        val averageDeltaDeg = startupOverlapDeltaTotalDeg / samples
        logDiagnostics(
            "google_fused startup_overlap_summary reason=$reason confirmed=$confirmed " +
                "samples=$samples avgDeltaDeg=${averageDeltaDeg.format(1)} " +
                "maxDeltaDeg=${startupOverlapMaxDeltaDeg.format(1)} " +
                "firstDeltaDeg=${startupOverlapFirstDeltaDeg.formatOrNA(1)} " +
                "finalDeltaDeg=${finalDeltaDeg.formatOrNA(1)} " +
                "previousFinalDeltaDeg=${previousFinalDeltaDeg.formatOrNA(1)} " +
                "restartDeltaChangeDeg=${
                    if (finalDeltaDeg != null && previousFinalDeltaDeg != null) {
                        (finalDeltaDeg - previousFinalDeltaDeg).format(1)
                    } else {
                        "na"
                    }
                }",
        )
        previousStartupOverlapFinalDeltaDeg = finalDeltaDeg
        resetStartupOverlapMetrics()
    }

    private fun resetStartupOverlapMetrics() {
        startupOverlapSampleCount = 0
        startupOverlapDeltaTotalDeg = 0f
        startupOverlapMaxDeltaDeg = 0f
        startupOverlapFirstDeltaDeg = null
        startupOverlapLastDeltaDeg = null
    }
}

internal fun shouldUseFusedBootstrapHeading(
    fusedRenderState: CompassRenderState,
    bootstrapRenderState: CompassRenderState,
    nowElapsedMs: Long,
): Boolean {
    val hasUsableFusedAccuracy =
        fusedRenderState.accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE
    val hasFreshFusedHeading =
        fusedRenderState.headingSource == HeadingSource.FUSED_ORIENTATION &&
            fusedRenderState.headingSampleElapsedRealtimeMs != null &&
            !fusedRenderState.headingSampleStale &&
            hasUsableFusedAccuracy
    val hasRecentCachedFusedHeading =
        hasUsableFusedAccuracy &&
            hasRecentGoogleFusedCachedHeading(
                renderState = fusedRenderState,
                nowElapsedMs = nowElapsedMs,
                maxAgeMs = FUSED_WARM_RESTART_CACHED_HEADING_MAX_AGE_MS,
            )
    val hasUsableBootstrapHeading =
        bootstrapRenderState.headingSource != HeadingSource.NONE &&
            bootstrapRenderState.accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE &&
            bootstrapRenderState.headingSampleElapsedRealtimeMs != null &&
            !bootstrapRenderState.headingSampleStale &&
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
        headingErrorDeg = bootstrapRenderState.headingErrorDeg,
        conservativeHeadingErrorDeg = bootstrapRenderState.conservativeHeadingErrorDeg,
        headingSampleElapsedRealtimeMs = bootstrapRenderState.headingSampleElapsedRealtimeMs,
        headingSampleStale = bootstrapRenderState.headingSampleStale,
        headingSource = bootstrapRenderState.headingSource,
        headingSourceStatus = bootstrapRenderState.headingSourceStatus,
        northReferenceStatus = bootstrapRenderState.northReferenceStatus,
        magneticInterference = bootstrapRenderState.magneticInterference,
    )

internal data class FusedUnusableHeadingState(
    val consecutiveSamples: Int,
    val firstSampleAtElapsedMs: Long,
)

internal data class FusedUnusableHeadingUpdate(
    val state: FusedUnusableHeadingState,
    val durationMs: Long,
    val shouldFallback: Boolean,
)

internal fun isUsableGoogleFusedHeadingError(
    headingErrorDeg: Float,
): Boolean =
    headingAccuracyFromUncertainty(headingErrorDeg) !=
        SensorManager.SENSOR_STATUS_UNRELIABLE

internal fun computeFusedUnusableHeadingUpdate(
    nowElapsedMs: Long,
    consecutiveUnusableSamples: Int,
    firstUnusableSampleAtElapsedMs: Long,
    minSamples: Int,
    minDurationMs: Long,
): FusedUnusableHeadingUpdate {
    val nextFirstSampleAtElapsedMs =
        if (consecutiveUnusableSamples <= 0 || firstUnusableSampleAtElapsedMs <= 0L) {
            nowElapsedMs
        } else {
            firstUnusableSampleAtElapsedMs
        }
    val nextConsecutiveSamples = consecutiveUnusableSamples + 1
    val durationMs = (nowElapsedMs - nextFirstSampleAtElapsedMs).coerceAtLeast(0L)
    return FusedUnusableHeadingUpdate(
        state =
            FusedUnusableHeadingState(
                consecutiveSamples = nextConsecutiveSamples,
                firstSampleAtElapsedMs = nextFirstSampleAtElapsedMs,
            ),
        durationMs = durationMs,
        shouldFallback = nextConsecutiveSamples >= minSamples && durationMs >= minDurationMs,
    )
}

private const val FUSED_ORIENTATION_THREAD_NAME = "FusedOrientationThread"
private const val FUSED_ORIENTATION_SETTLE_WINDOW_MS = 250L
private const val FUSED_ORIENTATION_HIGH_POWER_SAMPLING_MICROS = 50_000L // 20 Hz
private const val FUSED_ORIENTATION_LOW_POWER_SAMPLING_MICROS = 200_000L // 5 Hz
private const val FUSED_INVALID_HEADING_ERROR_DEG = 180f
private const val FUSED_ORIENTATION_SAMPLE_STALE_MS = 1_500L
private const val FUSED_STALE_RECOVERY_HEALTHY_RESET_MS = 5_000L
private const val FUSED_BOOTSTRAP_RELEASE_POLL_MS = 16L
private const val FUSED_BOOTSTRAP_RELEASE_MAX_WAIT_MS = 250L
private const val FUSED_PERF_LOG_WINDOW_MS = 5_000L
private const val FUSED_HIGH_POWER_PUBLISH_MIN_INTERVAL_MS = 33L
private const val FUSED_LOW_POWER_PUBLISH_MIN_INTERVAL_MS = 180L
private const val FUSED_RECALIBRATION_HIGH_POWER_WINDOW_MS = 6_000L
private const val FUSED_WARM_RESTART_CACHED_HEADING_MAX_AGE_MS = 5_000L
private const val FUSED_WARM_RESTART_MAX_HANDOFF_STEP_DEG = 10f
private const val FUSED_UNUSABLE_HEADING_FALLBACK_MIN_SAMPLES = 5
private const val FUSED_UNUSABLE_HEADING_FALLBACK_MIN_DURATION_MS = 1_200L
private const val FUSED_RESTART_CONFIRM_TIMEOUT_HIGH_POWER_MS = 160L
private const val FUSED_RESTART_CONFIRM_TIMEOUT_LOW_POWER_MS = 350L
private const val FUSED_RESTART_STABLE_DELTA_DEG = 15f
private const val FUSED_RESTART_MIN_CONFIDENT_SAMPLES = 2
internal const val FUSED_RESTART_TRUSTED_LIVE_ERROR_DEG = 12f
internal const val FUSED_RESTART_TRUSTED_CONSERVATIVE_ERROR_DEG = 45f
internal const val FUSED_WEAK_CONFIDENCE_LARGE_JUMP_MIN_CONFIRM_AGE_MS = 120L
internal const val FUSED_WEAK_CONFIDENCE_LARGE_JUMP_MAX_DELTA_DEG = 18f
internal const val FUSED_FAST_TURN_CONFIRM_MIN_SAMPLES = 3
internal const val FUSED_FAST_TURN_CONFIRM_MAX_DELTA_DEG = 36f
internal const val FUSED_LARGE_JUMP_ABSOLUTE_TIMEOUT_MS = 500L
private const val FUSED_UNSTABLE_STARTUP_RESEED_COUNT = 4
private const val FUSED_UNSTABLE_STARTUP_RESEED_DELTA_DEG = 150f
private const val FUSED_UNSTABLE_STARTUP_OVERLAP_DELTA_DEG = 150f
private const val FUSED_UNSTABLE_STARTUP_FINAL_OVERLAP_DELTA_DEG = 120f
private const val FUSED_UNSTABLE_STARTUP_BOOTSTRAP_HOLD_MS = 1_000L
private const val FUSED_UNSTABLE_STARTUP_RESTART_REASON = "startup_unstable_restart"

private enum class FusedBootstrapCompletion {
    STOP,
    HOLD_AFTER_UNSTABLE_START,
}

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
    headingErrorDeg: Float,
    conservativeHeadingErrorDeg: Float,
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
    val stableWithPending = deltaDeg <= FUSED_RESTART_STABLE_DELTA_DEG
    val hasTrustedConservativeError =
        conservativeHeadingErrorDeg.isFinite() &&
            conservativeHeadingErrorDeg in 0f..FUSED_RESTART_TRUSTED_CONSERVATIVE_ERROR_DEG
    val hasTrustedLiveError =
        headingErrorDeg.isFinite() &&
            headingErrorDeg in 0f..FUSED_RESTART_TRUSTED_LIVE_ERROR_DEG
    val hasTrustedHeadingError = hasTrustedConservativeError || hasTrustedLiveError
    val hasUsableHeadingError = isUsableGoogleFusedHeadingError(headingErrorDeg)
    val hasEnoughWeakConfidenceSettleTime = pendingAgeMs >= timeoutMs
    val hasStableConfirmationBudget =
        hasTrustedHeadingError || hasEnoughWeakConfidenceSettleTime
    if (stableWithPending && hasUsableHeadingError && hasStableConfirmationBudget) {
        return FusedRestartHeadingDecision(
            action = FusedRestartHeadingAction.CONFIRM,
            nextPendingHeadingDeg = null,
            nextPendingAtElapsedMs = 0L,
            nextPendingSampleCount = 0,
            sampleCount = sampleCount,
            deltaDeg = deltaDeg,
            pendingAgeMs = pendingAgeMs,
            confirmReason =
                when {
                    hasTrustedHeadingError -> "confidence"
                    else -> "timeout"
                },
        )
    }
    if (stableWithPending) {
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

    if (hasTrustedHeadingError && sampleCount >= FUSED_RESTART_MIN_CONFIDENT_SAMPLES) {
        return FusedRestartHeadingDecision(
            action = FusedRestartHeadingAction.CONFIRM,
            nextPendingHeadingDeg = null,
            nextPendingAtElapsedMs = 0L,
            nextPendingSampleCount = 0,
            sampleCount = sampleCount,
            deltaDeg = deltaDeg,
            pendingAgeMs = pendingAgeMs,
            confirmReason = "confidence",
        )
    }

    return FusedRestartHeadingDecision(
        action = FusedRestartHeadingAction.AWAIT_PENDING,
        nextPendingHeadingDeg = displayHeadingDeg,
        nextPendingAtElapsedMs = nowElapsedMs,
        nextPendingSampleCount = 1,
        sampleCount = sampleCount,
        deltaDeg = deltaDeg,
        pendingAgeMs = pendingAgeMs,
        confirmReason = null,
    )
}

internal fun isUnstableFusedStartup(
    decision: FusedRestartHeadingDecision,
    overlapMaxDeltaDeg: Float,
    overlapFinalDeltaDeg: Float,
    reseedCount: Int,
    maxReseedDeltaDeg: Float,
): Boolean {
    if (decision.confirmReason != "timeout") return false
    val startupWasChaotic =
        reseedCount >= FUSED_UNSTABLE_STARTUP_RESEED_COUNT ||
            maxReseedDeltaDeg >= FUSED_UNSTABLE_STARTUP_RESEED_DELTA_DEG ||
            overlapMaxDeltaDeg >= FUSED_UNSTABLE_STARTUP_OVERLAP_DELTA_DEG
    val providersStillDisagree =
        overlapFinalDeltaDeg.isFinite() &&
            overlapFinalDeltaDeg >= FUSED_UNSTABLE_STARTUP_FINAL_OVERLAP_DELTA_DEG
    return startupWasChaotic && providersStillDisagree
}

internal data class FusedLargeJumpInput(
    val jumpDeg: Float,
    val inRelock: Boolean,
    val hasPendingLargeJump: Boolean,
    val pendingDeltaDeg: Float,
    val pendingAgeMs: Long,
    val pendingConsistentSampleCount: Int,
    val headingErrorDeg: Float,
    val conservativeHeadingErrorDeg: Float,
)

internal fun resolveFusedLargeJumpAction(input: FusedLargeJumpInput): LargeJumpAction {
    val hasTrustedError = hasTrustedFusedHeadingError(input)
    val pendingConfirmationReady = isFusedPendingJumpConfirmationReady(input)
    val pendingConfirmationTimedOut = isFusedPendingJumpTimedOut(input)

    return when {
        input.jumpDeg <= HEADING_LARGE_JUMP_REJECT_DEG -> LargeJumpAction.NONE
        pendingConfirmationTimedOut -> LargeJumpAction.ACCEPT_CONFIRMED
        !hasTrustedError && input.hasPendingLargeJump -> {
            if (pendingConfirmationReady) {
                LargeJumpAction.ACCEPT_CONFIRMED
            } else {
                LargeJumpAction.REJECT_PENDING
            }
        }
        else ->
            resolveLargeJumpAction(
                jumpDeg = input.jumpDeg,
                inRelock = input.inRelock && hasTrustedError,
                hasPendingLargeJump = input.hasPendingLargeJump,
                pendingDeltaDeg = input.pendingDeltaDeg,
            )
    }
}

internal fun shouldPublishFusedHeading(
    nowElapsedMs: Long,
    lastPublishAtElapsedMs: Long,
    lowPowerMode: Boolean,
    force: Boolean,
): Boolean {
    if (force || lastPublishAtElapsedMs <= 0L) return true
    val minimumIntervalMs =
        if (lowPowerMode) {
            FUSED_LOW_POWER_PUBLISH_MIN_INTERVAL_MS
        } else {
            FUSED_HIGH_POWER_PUBLISH_MIN_INTERVAL_MS
        }
    return nowElapsedMs - lastPublishAtElapsedMs >= minimumIntervalMs
}

internal enum class FusedLargeJumpAcceptanceReason(
    val telemetryToken: String,
) {
    STABLE("stable"),
    TIMEOUT_500_MS("500ms_timeout"),
}

internal fun fusedLargeJumpAcceptanceReason(
    action: LargeJumpAction,
    pendingAgeMs: Long,
): FusedLargeJumpAcceptanceReason {
    require(action == LargeJumpAction.ACCEPT_IMMEDIATE || action == LargeJumpAction.ACCEPT_CONFIRMED)
    return if (pendingAgeMs >= FUSED_LARGE_JUMP_ABSOLUTE_TIMEOUT_MS) {
        FusedLargeJumpAcceptanceReason.TIMEOUT_500_MS
    } else {
        FusedLargeJumpAcceptanceReason.STABLE
    }
}
