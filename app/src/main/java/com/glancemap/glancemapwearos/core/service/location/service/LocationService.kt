package com.glancemap.glancemapwearos.core.service.location.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.glancemap.glancemapwearos.GlanceMapWearApp
import com.glancemap.glancemapwearos.core.service.diagnostics.EnergyDiagnostics
import com.glancemap.glancemapwearos.core.service.location.adapters.FusedLocationGateway
import com.glancemap.glancemapwearos.core.service.location.adapters.LocationGateway
import com.glancemap.glancemapwearos.core.service.location.adapters.LocationSettingsPreflight
import com.glancemap.glancemapwearos.core.service.location.adapters.LocationUpdateEvent
import com.glancemap.glancemapwearos.core.service.location.adapters.LocationUpdateRequestParams
import com.glancemap.glancemapwearos.core.service.location.adapters.LocationUpdateSink
import com.glancemap.glancemapwearos.core.service.location.adapters.WatchGpsLocationGateway
import com.glancemap.glancemapwearos.core.service.location.adapters.WearPhoneConnectionProbe
import com.glancemap.glancemapwearos.core.service.location.config.BIND_CACHED_FIX_MAX_ACCURACY_COARSE_M
import com.glancemap.glancemapwearos.core.service.location.config.BIND_CACHED_FIX_MAX_ACCURACY_M
import com.glancemap.glancemapwearos.core.service.location.config.BIND_CACHED_FIX_MAX_MAX_AGE_MS
import com.glancemap.glancemapwearos.core.service.location.config.BIND_CACHED_FIX_MIN_MAX_AGE_MS
import com.glancemap.glancemapwearos.core.service.location.config.CHANNEL_ID
import com.glancemap.glancemapwearos.core.service.location.config.COARSE_FIX_MAX_ACCURACY_M
import com.glancemap.glancemapwearos.core.service.location.config.ENERGY_SAMPLE_INTERVAL_MS
import com.glancemap.glancemapwearos.core.service.location.config.FINE_FIX_MAX_ACCURACY_M
import com.glancemap.glancemapwearos.core.service.location.config.FIX_MAX_AGE_COARSE_MAX_MS
import com.glancemap.glancemapwearos.core.service.location.config.FIX_MAX_AGE_FINE_MAX_MS
import com.glancemap.glancemapwearos.core.service.location.config.GpsSettingsState
import com.glancemap.glancemapwearos.core.service.location.config.NOTIFICATION_ID
import com.glancemap.glancemapwearos.core.service.location.config.TELEMETRY_SUMMARY_INTERVAL_MS
import com.glancemap.glancemapwearos.core.service.location.config.TELEMETRY_TAG
import com.glancemap.glancemapwearos.core.service.location.config.WATCH_GPS_AUTO_FALLBACK_INTERACTIVE_MAX_ACCURACY_M
import com.glancemap.glancemapwearos.core.service.location.config.WATCH_GPS_MAX_ACCEPTED_ACCURACY_M
import com.glancemap.glancemapwearos.core.service.location.engine.LocationEngine
import com.glancemap.glancemapwearos.core.service.location.engine.RequestSpec
import com.glancemap.glancemapwearos.core.service.location.model.LocationPermissionChecker
import com.glancemap.glancemapwearos.core.service.location.model.LocationPermissionSnapshot
import com.glancemap.glancemapwearos.core.service.location.model.LocationScreenState
import com.glancemap.glancemapwearos.core.service.location.model.isInteractive
import com.glancemap.glancemapwearos.core.service.location.model.isNonInteractive
import com.glancemap.glancemapwearos.core.service.location.model.resolveLocationTimingProfile
import com.glancemap.glancemapwearos.core.service.location.notifications.LocationNotificationFactory
import com.glancemap.glancemapwearos.core.service.location.policy.FixAcceptancePolicy
import com.glancemap.glancemapwearos.core.service.location.policy.LocationFixPolicy
import com.glancemap.glancemapwearos.core.service.location.policy.LocationRuntimeMode
import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode
import com.glancemap.glancemapwearos.core.service.location.telemetry.LocationServiceTelemetry
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LocationService : Service() {
    private enum class KeepAliveNotificationMode {
        OFF,
        PINNED_NOTIFICATION,
        LOCATION_FOREGROUND,
    }

    private val binder = LocalBinder()
    private lateinit var fusedLocationGateway: FusedLocationGateway
    private lateinit var watchGpsLocationGateway: WatchGpsLocationGateway
    private lateinit var locationSettingsPreflight: LocationSettingsPreflight
    private lateinit var phoneConnectionProbe: WearPhoneConnectionProbe
    private lateinit var locationUpdateSink: LocationUpdateSink
    private lateinit var callbackProcessor: LocationCallbackProcessor
    private lateinit var immediateLocationCoordinator: ImmediateLocationCoordinator
    private lateinit var requestCoordinator: LocationRequestCoordinator
    private lateinit var settingsRepository: SettingsRepository

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation = _currentLocation.asStateFlow()

    private val telemetry =
        LocationServiceTelemetry(
            tag = TELEMETRY_TAG,
            summaryIntervalMs = TELEMETRY_SUMMARY_INTERVAL_MS,
        )
    private val engine = LocationEngine(telemetry = telemetry)

    private val _gpsSignalSnapshot = MutableStateFlow(engine.gpsSignalSnapshot)
    val gpsSignalSnapshot = _gpsSignalSnapshot.asStateFlow()
    private val _effectiveUpdateIntervalMs = MutableStateFlow(SettingsRepository.DEFAULT_GPS_INTERVAL_MS)
    val effectiveUpdateIntervalMs = _effectiveUpdateIntervalMs.asStateFlow()

    private val isBound = MutableStateFlow(false)
    private val keepAppOpen = MutableStateFlow(false)
    private val notificationFactory by lazy { LocationNotificationFactory(this, CHANNEL_ID) }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val locationManager by lazy { getSystemService(LocationManager::class.java) }

    // Dedicated background thread for GPS callbacks — keeps location processing off the
    // UI thread so Compose recomposition and map rendering never delay fix delivery.
    // All downstream writes are either StateFlow (thread-safe) or @Volatile fields.
    private val locationCallbackThreadDelegate =
        lazy {
            HandlerThread("LocationCallbackThread").apply { start() }
        }
    private val locationCallbackThread: HandlerThread by locationCallbackThreadDelegate
    private val locationCallbackExecutor by lazy {
        java.util.concurrent.Executor { command ->
            Handler(locationCallbackThread.looper).post(command)
        }
    }

    @Volatile private var latestWatchGpsOnly: Boolean = false

    @Volatile private var latestAmbientGps: Boolean = false

    @Volatile private var latestScreenState: LocationScreenState = LocationScreenState.INTERACTIVE

    @Volatile private var latestGpsDebugTelemetry: Boolean = false

    @Volatile private var latestPassiveLocationExperiment: Boolean = false

    @Volatile private var latestPhoneConnected: Boolean? = null

    @Volatile private var latestUserIntervalMs: Long = SettingsRepository.DEFAULT_GPS_INTERVAL_MS

    @Volatile private var latestAmbientIntervalMs: Long = SettingsRepository.DEFAULT_AMBIENT_GPS_INTERVAL_MS

    @Volatile private var latestTrackingEnabled: Boolean = false

    @Volatile private var latestHasFinePermission: Boolean = false

    @Volatile private var latestHasCoarsePermission: Boolean = false

    @Volatile private var lastAnyAcceptedFixAtElapsedMs: Long = 0L

    @Volatile private var lastCallbackAcceptedFixAtElapsedMs: Long = 0L

    @Volatile private var lastRequestAppliedAtElapsedMs: Long = 0L

    @Volatile private var sourceModeWarmupUntilElapsedMs: Long = 0L

    @Volatile private var sourceModeWarmupExpectedOrigin: LocationSourceMode? = null

    private var energySampleJob: Job? = null
    private var keepAliveNotificationMode: KeepAliveNotificationMode = KeepAliveNotificationMode.OFF

    private val selfHealFailoverCoordinator by lazy {
        SelfHealFailoverCoordinator(
            serviceScope = serviceScope,
            isServiceActive = { serviceJob.isActive },
            engine = engine,
            telemetry = telemetry,
            requestLocationUpdateIfNeeded = { requestLocationUpdateIfNeeded() },
            requestImmediateLocation = { source -> requestImmediateLocation(source) },
            trackingEnabled = { latestTrackingEnabled },
            ambientModeActive = { isNonInteractiveScreenState() },
            hasFinePermission = { latestHasFinePermission },
            hasCoarsePermission = { latestHasCoarsePermission },
            watchGpsOnly = { latestWatchGpsOnly },
            passiveLocationExperiment = { latestGpsDebugTelemetry && latestPassiveLocationExperiment },
            phoneConnected = { latestPhoneConnected },
            lastAnyAcceptedFixAtElapsedMs = { lastAnyAcceptedFixAtElapsedMs },
            lastCallbackAcceptedFixAtElapsedMs = { lastCallbackAcceptedFixAtElapsedMs },
            lastRequestAppliedAtElapsedMs = { lastRequestAppliedAtElapsedMs },
            expectedIntervalMs = { _effectiveUpdateIntervalMs.value },
            strictFreshMaxAgeMs = { strictFreshMaxAgeMs() },
        )
    }
    private val gnssDiagnosticsCoordinator by lazy {
        GnssDiagnosticsCoordinator(
            serviceScope = serviceScope,
            mainHandler = mainHandler,
            locationManagerProvider = { locationManager },
            hasFinePermission = { latestHasFinePermission },
            hasCoarsePermission = { latestHasCoarsePermission },
            trackingEnabled = { latestTrackingEnabled },
            bound = { isBound.value },
            keepOpen = { keepAppOpen.value },
            watchOnly = { latestWatchGpsOnly },
            sourceMode = { currentLocationSourceMode().telemetryValue },
            ambientModeActive = { isNonInteractiveScreenState() },
            debugTelemetryEnabled = { latestGpsDebugTelemetry },
        )
    }

    override fun onCreate() {
        super.onCreate()
        val fused = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationGateway = FusedLocationGateway(fused)
        locationSettingsPreflight = LocationSettingsPreflight(LocationServices.getSettingsClient(this))
        phoneConnectionProbe = WearPhoneConnectionProbe(Wearable.getNodeClient(this))
        watchGpsLocationGateway =
            WatchGpsLocationGateway(
                locationManager = requireNotNull(locationManager) { "location_manager_unavailable" },
                packageManager = packageManager,
                callbackExecutor = locationCallbackExecutor,
            )
        settingsRepository = (application as GlanceMapWearApp).container.settingsRepository
        callbackProcessor =
            LocationCallbackProcessor(
                engine = engine,
                telemetry = telemetry,
                currentPermissions = { currentLocationPermissions() },
                resolveFixAcceptancePolicy = { permissions, sourceMode ->
                    resolveFixAcceptancePolicy(permissions, sourceMode)
                },
                strictFreshMaxAgeMs = { strictFreshMaxAgeMs() },
                hardMaxAcceptedFixAgeMs = { hardMaxAcceptedFixAgeMs() },
                sourceModeWarmupExpectedOrigin = { sourceModeWarmupExpectedOrigin },
                sourceModeWarmupUntilElapsedMs = { sourceModeWarmupUntilElapsedMs },
                emitGpsSignalSnapshot = { _gpsSignalSnapshot.value = engine.gpsSignalSnapshot },
                emitAcceptedLocation = { location, acceptedAtMs ->
                    _currentLocation.value = location
                    lastAnyAcceptedFixAtElapsedMs = acceptedAtMs
                    lastCallbackAcceptedFixAtElapsedMs = acceptedAtMs
                },
                maybeTriggerAutoFusedFailover = { acceptedLocation, callbackOrigin, nowElapsedMs ->
                    selfHealFailoverCoordinator.maybeTriggerAutoFusedFailover(
                        acceptedLocation = acceptedLocation,
                        callbackOrigin = callbackOrigin,
                        nowElapsedMs = nowElapsedMs,
                    )
                },
                endHighAccuracyBurstEarly = {
                    immediateLocationCoordinator.endHighAccuracyBurst(reason = "early_fix")
                },
            )
        immediateLocationCoordinator =
            ImmediateLocationCoordinator(
                context = this,
                serviceScope = serviceScope,
                engine = engine,
                telemetry = telemetry,
                readAndStoreLocationPermissions = { readAndStoreLocationPermissions() },
                resolveFixAcceptancePolicy = { permissions, sourceMode ->
                    resolveFixAcceptancePolicy(permissions, sourceMode)
                },
                strictFreshMaxAgeMs = { strictFreshMaxAgeMs() },
                hardMaxAcceptedFixAgeMs = { hardMaxAcceptedFixAgeMs() },
                currentLocationSourceMode = { currentLocationSourceMode() },
                locationGatewayFor = { sourceMode -> locationGatewayFor(sourceMode) },
                requestLocationUpdateIfNeeded = { requestLocationUpdateIfNeeded() },
                passiveLocationExperiment = { latestGpsDebugTelemetry && latestPassiveLocationExperiment },
                emitGpsSignalSnapshot = { _gpsSignalSnapshot.value = engine.gpsSignalSnapshot },
                emitAcceptedImmediateLocation = { location, acceptedAtMs ->
                    _currentLocation.value = location
                    lastAnyAcceptedFixAtElapsedMs = acceptedAtMs
                },
                immediateGetCurrentTimeoutMs = IMMEDIATE_GET_CURRENT_TIMEOUT_MS,
            )
        requestCoordinator =
            LocationRequestCoordinator(
                serviceScope = serviceScope,
                engine = engine,
                telemetry = telemetry,
                readAndStoreLocationPermissions = { readAndStoreLocationPermissions() },
                updateSelfHealMonitor = { selfHealFailoverCoordinator.updateSelfHealMonitor() },
                updateGnssDiagnostics = { updateGnssDiagnostics(enabled = latestGpsDebugTelemetry) },
                foregroundRefresh = { refreshKeepAliveNotificationState() },
                inspectLocationEnvironment = { requestSpec, state, permissions, nowElapsedMs ->
                    inspectLocationEnvironment(
                        requestSpec = requestSpec,
                        state = state,
                        permissions = permissions,
                        nowElapsedMs = nowElapsedMs,
                    )
                },
                cancelImmediateLocationWork = { reason ->
                    immediateLocationCoordinator.cancelImmediateLocationWork(reason = reason)
                },
                currentState = {
                    RequestUpdateState(
                        bound = isBound.value,
                        tracking = latestTrackingEnabled,
                        keepOpen = keepAppOpen.value,
                        watchOnlyRequested = latestWatchGpsOnly,
                        watchOnlyEffective =
                            latestWatchGpsOnly ||
                                selfHealFailoverCoordinator.isAutoFusedFallbackToWatchGps(),
                        screenState = latestScreenState,
                        backgroundGps = latestAmbientGps,
                        passiveLocationExperiment = latestGpsDebugTelemetry && latestPassiveLocationExperiment,
                        userIntervalMs = latestUserIntervalMs,
                        ambientIntervalMs = latestAmbientIntervalMs,
                    )
                },
                effectiveUpdateIntervalMs = { _effectiveUpdateIntervalMs.value },
                strictSourceWarmupMs = SOURCE_MODE_WARMUP_MS,
                setSourceModeWarmup = { expectedOrigin, untilElapsedMs ->
                    sourceModeWarmupExpectedOrigin = expectedOrigin
                    sourceModeWarmupUntilElapsedMs = untilElapsedMs
                },
                clearSourceModeWarmup = {
                    sourceModeWarmupExpectedOrigin = null
                    sourceModeWarmupUntilElapsedMs = 0L
                },
                locationGatewayFor = { sourceMode -> locationGatewayFor(sourceMode) },
                locationUpdateSink = { locationUpdateSink },
                removeAllLocationUpdates = { removeAllLocationUpdates() },
                onNoPermissions = { nowElapsedMs ->
                    engine.onNoPermissions(nowElapsedMs = nowElapsedMs)
                    lastAnyAcceptedFixAtElapsedMs = 0L
                    lastCallbackAcceptedFixAtElapsedMs = 0L
                    sourceModeWarmupUntilElapsedMs = 0L
                    sourceModeWarmupExpectedOrigin = null
                    _gpsSignalSnapshot.value = engine.gpsSignalSnapshot
                    _effectiveUpdateIntervalMs.value = SettingsRepository.DEFAULT_GPS_INTERVAL_MS
                },
                onNoRequestSpec = { keepOpen, tracking ->
                    engine.onTrackingDisabled()
                    lastAnyAcceptedFixAtElapsedMs = 0L
                    lastCallbackAcceptedFixAtElapsedMs = 0L
                    sourceModeWarmupUntilElapsedMs = 0L
                    sourceModeWarmupExpectedOrigin = null
                    _effectiveUpdateIntervalMs.value = SettingsRepository.DEFAULT_GPS_INTERVAL_MS
                    if (!keepOpen && !tracking) {
                        stopAllAndSelf()
                    }
                },
                onRequestApplied = { nowElapsedMs, intervalMs ->
                    lastRequestAppliedAtElapsedMs = nowElapsedMs
                    _effectiveUpdateIntervalMs.value = intervalMs
                },
                onRequestFailed = {
                    _effectiveUpdateIntervalMs.value = SettingsRepository.DEFAULT_GPS_INTERVAL_MS
                },
                maybeTriggerInteractiveSelfHealNow = { nowElapsedMs, interactiveTracking, expectedIntervalMs ->
                    selfHealFailoverCoordinator.maybeTriggerInteractiveSelfHealNow(
                        nowElapsedMs = nowElapsedMs,
                        interactiveTracking = interactiveTracking,
                        expectedIntervalMs = expectedIntervalMs,
                    )
                },
                recordEnergySample = { reason, detail ->
                    EnergyDiagnostics.recordSample(
                        context = this,
                        reason = reason,
                        detail = detail,
                    )
                },
            )

        setupLocationUpdateSink()

        serviceScope.launch {
            runCatching { latestWatchGpsOnly = settingsRepository.watchGpsOnly.first() }
            runCatching { latestAmbientGps = settingsRepository.gpsInAmbientMode.first() }
            runCatching { latestAmbientIntervalMs = settingsRepository.ambientGpsInterval.first() }
            runCatching { latestPassiveLocationExperiment = settingsRepository.gpsPassiveLocationExperiment.first() }
            runCatching {
                latestGpsDebugTelemetry = settingsRepository.gpsDebugTelemetry.first()
                telemetry.setDebugEnabled(latestGpsDebugTelemetry)
                updateEnergySampling(latestGpsDebugTelemetry)
                updateGnssDiagnostics(enabled = latestGpsDebugTelemetry)
            }
            runCatching { latestUserIntervalMs = settingsRepository.gpsInterval.first() }
            requestLocationUpdateIfNeeded()
        }

        observeGpsSettings()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val hasScreenState = intent?.hasExtra(EXTRA_SCREEN_STATE) == true
        val hasTrackingEnabled = intent?.hasExtra(EXTRA_TRACKING_ENABLED) == true
        if (hasScreenState || hasTrackingEnabled) {
            val screenStateName = intent.getStringExtra(EXTRA_SCREEN_STATE)
            val screenState =
                runCatching {
                    LocationScreenState.valueOf(screenStateName.orEmpty())
                }.getOrDefault(LocationScreenState.INTERACTIVE)
            setRuntimeState(
                screenState = if (hasScreenState) screenState else latestScreenState,
                trackingEnabled =
                    if (hasTrackingEnabled) {
                        intent.getBooleanExtra(EXTRA_TRACKING_ENABLED, false)
                    } else {
                        latestTrackingEnabled
                    },
            )
        }
        if (intent?.hasExtra(EXTRA_KEEP_APP_OPEN) == true) {
            setKeepAppOpenState(intent.getBooleanExtra(EXTRA_KEEP_APP_OPEN, false))
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopAllAndSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun setupLocationUpdateSink() {
        locationUpdateSink =
            object : LocationUpdateSink {
                override fun onLocationAvailability(isAvailable: Boolean) {
                    callbackProcessor.onLocationAvailability(
                        isAvailable = isAvailable,
                        nowElapsedMs = SystemClock.elapsedRealtime(),
                    )
                }

                override fun onLocations(event: LocationUpdateEvent) {
                    callbackProcessor.processLocationEvent(
                        event = event,
                        nowElapsedMsProvider = { SystemClock.elapsedRealtime() },
                    )
                }
            }
    }

    fun requestImmediateLocation(source: String = "service_unknown") {
        immediateLocationCoordinator.requestImmediateLocation(source)
    }

    fun setRuntimeState(
        screenState: LocationScreenState,
        trackingEnabled: Boolean,
    ) {
        val screenStateChanged = latestScreenState != screenState
        val trackingChanged = latestTrackingEnabled != trackingEnabled
        if (!screenStateChanged && !trackingChanged) return

        latestScreenState = screenState
        latestTrackingEnabled = trackingEnabled

        telemetry.logRuntimeStateApplied(
            screenState = screenState.name,
            trackingEnabled = trackingEnabled,
            screenStateChanged = screenStateChanged,
            trackingChanged = trackingChanged,
            backgroundGpsEnabled = latestAmbientGps,
        )
        if (screenStateChanged) {
            telemetry.logScreenState(screenState.name)
        }
        if (trackingChanged) {
            telemetry.logTrackingEnabled(trackingEnabled)
        }
        when {
            !trackingEnabled -> {
                immediateLocationCoordinator.cancelImmediateLocationWork(reason = "tracking_disabled")
            }
            screenState.isNonInteractive && !latestAmbientGps -> {
                immediateLocationCoordinator.cancelImmediateLocationWork(
                    reason = "non_interactive_without_gps",
                )
            }
        }
        selfHealFailoverCoordinator.updateSelfHealMonitor()
        refreshKeepAliveNotificationState()
        requestLocationUpdateIfNeeded()
    }

    fun setScreenState(screenState: LocationScreenState) {
        setRuntimeState(
            screenState = screenState,
            trackingEnabled = latestTrackingEnabled,
        )
    }

    fun setTrackingEnabled(enabled: Boolean) {
        setRuntimeState(
            screenState = latestScreenState,
            trackingEnabled = enabled,
        )
    }

    fun setKeepAppOpenState(enabled: Boolean) {
        if (keepAppOpen.value == enabled) return

        keepAppOpen.value = enabled
        telemetry.logKeepAppOpen(enabled)
        refreshKeepAliveNotificationState()

        if (!enabled) {
            serviceScope.launch {
                if (!latestTrackingEnabled) {
                    stopAllAndSelf()
                }
            }
        }
    }

    private fun requestLocationUpdateIfNeeded() {
        requestCoordinator.requestLocationUpdateIfNeeded()
    }

    private suspend fun inspectLocationEnvironment(
        requestSpec: RequestSpec,
        state: RequestUpdateState,
        permissions: LocationPermissionSnapshot,
        nowElapsedMs: Long,
    ): LocationEnvironmentAction {
        val locationSettings =
            if (requestSpec.sourceMode == LocationSourceMode.AUTO_FUSED) {
                locationSettingsPreflight.check(
                    LocationUpdateRequestParams(
                        priority = requestSpec.priority,
                        intervalMs = requestSpec.intervalMs,
                        minDistanceMeters = requestSpec.minDistanceMeters,
                        waitForAccurateLocation =
                            requestSpec.mode == LocationRuntimeMode.BURST &&
                                requestSpec.sourceMode == LocationSourceMode.AUTO_FUSED,
                        maxUpdateDelayMs = maxUpdateDelayMsFor(requestSpec),
                    ),
                )
            } else {
                null
            }
        val shouldCheckPhone =
            requestSpec.sourceMode == LocationSourceMode.AUTO_FUSED ||
                (requestSpec.sourceMode == LocationSourceMode.WATCH_GPS && !state.watchOnlyRequested)
        val phoneConnected =
            if (shouldCheckPhone) {
                phoneConnectionProbe.isPhoneConnected()
            } else {
                null
            }
        updateLatestPhoneConnection(
            phoneConnected = phoneConnected,
            nowElapsedMs = nowElapsedMs,
        )
        val shouldCheckWatchGps =
            requestSpec.sourceMode == LocationSourceMode.WATCH_GPS ||
                requestSpec.sourceMode == LocationSourceMode.AUTO_FUSED
        val watchGpsAvailability =
            if (shouldCheckWatchGps) {
                watchGpsLocationGateway.availabilityReason()
            } else {
                null
            }
        val decision =
            resolveLocationEnvironmentDecision(
                sourceMode = requestSpec.sourceMode,
                watchOnlyRequested = state.watchOnlyRequested,
                watchGpsAvailability = watchGpsAvailability,
                phoneConnected = phoneConnected,
                locationSettings = locationSettings,
                passiveLocationExperiment = state.passiveLocationExperiment,
            )
        val warningChanged =
            engine.updateEnvironmentWarning(
                warning = decision.warning,
                nowElapsedMs = nowElapsedMs,
            )
        if (
            warningChanged ||
            locationSettings?.satisfied == false ||
            phoneConnected == false
        ) {
            telemetry.logLocationEnvironmentPreflight(
                sourceMode = requestSpec.sourceMode.telemetryValue,
                locationSettingsSatisfied = locationSettings?.satisfied,
                locationSettingsStatusCode = locationSettings?.statusCode,
                phoneConnected = phoneConnected,
                watchGpsAvailability = watchGpsAvailability?.name,
                warning = decision.warning.name,
                action = decision.action.name,
            )
        }
        if (warningChanged) {
            telemetry.logLocationEnvironmentWarningChanged(decision.warning.name)
        }
        _gpsSignalSnapshot.value = engine.gpsSignalSnapshot

        if (decision.action != LocationEnvironmentAction.RESTART_REQUEST) {
            return LocationEnvironmentAction.CONTINUE
        }
        val forcedFallback =
            selfHealFailoverCoordinator.forceAutoFusedFallbackToWatchGps(
                reason = "phone_disconnected",
                nowElapsedMs = nowElapsedMs,
            )
        return if (forcedFallback) {
            LocationEnvironmentAction.RESTART_REQUEST
        } else {
            LocationEnvironmentAction.CONTINUE
        }
    }

    private fun updateLatestPhoneConnection(
        phoneConnected: Boolean?,
        nowElapsedMs: Long,
    ) {
        if (phoneConnected != null) {
            latestPhoneConnected = phoneConnected
            selfHealFailoverCoordinator.onPhoneConnectionStateChecked(
                phoneConnected = phoneConnected,
                nowElapsedMs = nowElapsedMs,
            )
        }
    }

    private fun maxUpdateDelayMsFor(requestSpec: RequestSpec): Long =
        when (requestSpec.mode) {
            LocationRuntimeMode.BURST,
            LocationRuntimeMode.INTERACTIVE,
            -> 0L
            LocationRuntimeMode.PASSIVE -> requestSpec.intervalMs * 2L
        }

    @SuppressLint("MissingPermission")
    private fun observeGpsSettings() {
        serviceScope.launch {
            combine(
                settingsRepository.watchGpsOnly,
                settingsRepository.gpsInterval,
                settingsRepository.ambientGpsInterval,
                settingsRepository.gpsInAmbientMode,
                settingsRepository.gpsDebugTelemetry,
            ) { watchOnly, interval, ambientInterval, ambientGps, debugTelemetry ->
                GpsSettingsState(
                    watchOnly = watchOnly,
                    intervalMs = interval,
                    ambientIntervalMs = ambientInterval,
                    ambientGps = ambientGps,
                    debugTelemetry = debugTelemetry,
                    passiveLocationExperiment = false,
                )
            }.combine(settingsRepository.gpsPassiveLocationExperiment) { state, passiveLocationExperiment ->
                state.copy(passiveLocationExperiment = passiveLocationExperiment)
            }.collectLatest { state ->
                onGpsSettingsStateChanged(state)
            }
        }
    }

    private fun onGpsSettingsStateChanged(state: GpsSettingsState) {
        val debugTelemetryEnabledNow = state.debugTelemetry
        val debugTelemetryJustEnabled = !latestGpsDebugTelemetry && debugTelemetryEnabledNow
        val passiveExperimentWasActive = latestGpsDebugTelemetry && latestPassiveLocationExperiment
        val passiveExperimentActiveNow = debugTelemetryEnabledNow && state.passiveLocationExperiment
        val passiveExperimentChanged = passiveExperimentWasActive != passiveExperimentActiveNow
        val watchOnlyChanged = latestWatchGpsOnly != state.watchOnly
        latestWatchGpsOnly = state.watchOnly
        latestUserIntervalMs = state.intervalMs
        latestAmbientIntervalMs = state.ambientIntervalMs
        latestAmbientGps = state.ambientGps
        latestGpsDebugTelemetry = debugTelemetryEnabledNow
        latestPassiveLocationExperiment = state.passiveLocationExperiment
        telemetry.setDebugEnabled(latestGpsDebugTelemetry)
        updateEnergySampling(latestGpsDebugTelemetry)
        updateGnssDiagnostics(enabled = latestGpsDebugTelemetry)
        if (debugTelemetryJustEnabled || passiveExperimentChanged) {
            engine.forceRequestRefresh()
        }
        if (watchOnlyChanged) {
            selfHealFailoverCoordinator.clearAutoFusedFailoverState(reason = "watch_setting_changed")
        }
        if (passiveExperimentChanged) {
            selfHealFailoverCoordinator.clearAutoFusedFailoverState(reason = "passive_experiment_changed")
        }

        requestLocationUpdateIfNeeded()
    }

    @SuppressLint("MissingPermission")
    override fun onBind(intent: Intent): IBinder {
        isBound.value = true
        requestLocationUpdateIfNeeded()
        serviceScope.launch {
            if (!latestTrackingEnabled) return@launch
            val permissions = readAndStoreLocationPermissions()
            val hasFinePermission = permissions.hasFinePermission

            if (permissions.hasAnyPermission) {
                val location = runCatching { currentLocationGateway().getLastLocation() }.getOrNull()
                if (location != null) {
                    val nowElapsedMs = SystemClock.elapsedRealtime()
                    val maxCachedAgeMs =
                        (latestUserIntervalMs * 2L)
                            .coerceIn(BIND_CACHED_FIX_MIN_MAX_AGE_MS, BIND_CACHED_FIX_MAX_MAX_AGE_MS)
                    val strictMaxAgeMs = strictFreshMaxAgeMs()
                    val effectiveMaxCachedAgeMs = minOf(maxCachedAgeMs, strictMaxAgeMs)
                    val ageMs = LocationFixPolicy.locationAgeMs(location, nowElapsedMs)

                    val accuracy = location.accuracy
                    val maxCachedAccuracyM =
                        if (hasFinePermission) {
                            BIND_CACHED_FIX_MAX_ACCURACY_M
                        } else {
                            BIND_CACHED_FIX_MAX_ACCURACY_COARSE_M
                        }
                    val acceptableCoordinates = LocationFixPolicy.hasValidCoordinates(location)
                    val acceptableAccuracy = accuracy.isFinite() && accuracy <= maxCachedAccuracyM
                    val acceptableAge = ageMs <= effectiveMaxCachedAgeMs

                    if (acceptableCoordinates && acceptableAccuracy && acceptableAge) {
                        engine.updateGpsSignalSample(
                            nowElapsedMs = nowElapsedMs,
                            ageMs = ageMs,
                            accuracyM = location.accuracy,
                            freshnessMaxAgeMs = strictMaxAgeMs,
                            sourceMode = currentLocationSourceMode(),
                            provider = location.provider,
                            accepted = null,
                        )
                        _gpsSignalSnapshot.value = engine.gpsSignalSnapshot
                        val outputLocation =
                            engine.acceptCachedLocation(
                                location = location,
                                nowElapsedMs = nowElapsedMs,
                                ageMs = ageMs,
                            )
                        _currentLocation.value = outputLocation
                        lastAnyAcceptedFixAtElapsedMs = nowElapsedMs
                        telemetry.logCachedLocationAccepted(
                            ageMs = ageMs,
                            accuracyM = accuracy,
                            provider = location.provider,
                        )
                    } else {
                        telemetry.logCachedLocationRejected(
                            ageMs = ageMs,
                            accuracyM = accuracy,
                            maxAgeMs = effectiveMaxCachedAgeMs,
                            maxAccuracyM = maxCachedAccuracyM,
                            provider = location.provider,
                        )
                        if (!acceptableCoordinates) {
                            telemetry.logInvalidCoordinatesDropped(
                                nowElapsedMs = nowElapsedMs,
                                activityState = engine.activityState(),
                                burst = engine.isBurstActive(),
                                source = "cached_on_bind",
                                latitude = location.latitude,
                                longitude = location.longitude,
                                provider = location.provider,
                            )
                        }
                        if (!acceptableAge) {
                            telemetry.logStaleFixDropped(
                                nowElapsedMs = nowElapsedMs,
                                activityState = engine.activityState(),
                                burst = engine.isBurstActive(),
                                source = "cached_on_bind",
                                ageMs = ageMs,
                                maxAgeMs = effectiveMaxCachedAgeMs,
                            )
                        }
                        requestImmediateLocation(source = "service_bind_cached_reject")
                    }
                } else {
                    requestImmediateLocation(source = "service_bind_no_cached")
                }
            }
        }

        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isBound.value = false
        requestLocationUpdateIfNeeded()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        energySampleJob?.cancel()
        energySampleJob = null
        selfHealFailoverCoordinator.stop()
        unregisterGnssDiagnostics(reason = "service_destroy")
        stopAllAndSelf(stopSelf = false)
        serviceJob.cancel()
        // Quit the callback thread after all location updates are removed so no
        // in-flight callbacks can fire after the service is torn down.
        if (locationCallbackThreadDelegate.isInitialized()) {
            locationCallbackThread.quitSafely()
        }
        super.onDestroy()
    }

    private fun stopAllAndSelf(stopSelf: Boolean = true) {
        requestCoordinator.cancel()
        immediateLocationCoordinator.shutdown(reason = "service_stop")
        energySampleJob?.cancel()
        energySampleJob = null
        selfHealFailoverCoordinator.stop()

        removeAllLocationUpdatesBestEffort()
        unregisterGnssDiagnostics(reason = "service_stop")
        engine.stopAndReset()
        lastAnyAcceptedFixAtElapsedMs = 0L
        lastCallbackAcceptedFixAtElapsedMs = 0L
        lastRequestAppliedAtElapsedMs = 0L
        sourceModeWarmupUntilElapsedMs = 0L
        sourceModeWarmupExpectedOrigin = null

        _gpsSignalSnapshot.value = engine.gpsSignalSnapshot
        _effectiveUpdateIntervalMs.value = SettingsRepository.DEFAULT_GPS_INTERVAL_MS

        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        notificationFactory.cancel(NOTIFICATION_ID)
        keepAliveNotificationMode = KeepAliveNotificationMode.OFF

        if (stopSelf) {
            runCatching { stopSelf() }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): LocationService = this@LocationService
    }

    private fun readAndStoreLocationPermissions(): LocationPermissionSnapshot {
        val permissions = LocationPermissionChecker.read(applicationContext)
        latestHasFinePermission = permissions.hasFinePermission
        latestHasCoarsePermission = permissions.hasCoarsePermission
        return permissions
    }

    private fun currentLocationPermissions(): LocationPermissionSnapshot =
        LocationPermissionSnapshot(
            hasFinePermission = latestHasFinePermission,
            hasCoarsePermission = latestHasCoarsePermission,
        )

    private fun resolveFixAcceptancePolicy(
        permissions: LocationPermissionSnapshot,
        sourceMode: LocationSourceMode,
    ): FixAcceptancePolicy {
        val expectedIntervalMs = engine.currentRequestIntervalOr(_effectiveUpdateIntervalMs.value)
        val basePolicy =
            LocationFixPolicy.resolveAcceptancePolicy(
                hasFinePermission = permissions.hasFinePermission,
                hasCoarsePermission = permissions.hasCoarsePermission,
                expectedIntervalMs = expectedIntervalMs,
                minMaxAgeMs = BIND_CACHED_FIX_MIN_MAX_AGE_MS,
                fineMaxAgeMs = FIX_MAX_AGE_FINE_MAX_MS,
                coarseMaxAgeMs = FIX_MAX_AGE_COARSE_MAX_MS,
                fineMaxAccuracyM = FINE_FIX_MAX_ACCURACY_M,
                coarseMaxAccuracyM = COARSE_FIX_MAX_ACCURACY_M,
            )
        val watchGpsMaxAccuracyM =
            LocationFixPolicy.resolveWatchGpsAcceptanceAccuracyM(
                sourceMode = sourceMode,
                watchGpsOnly = latestWatchGpsOnly,
                runtimeMode = engine.currentRuntimeModeOrNull(),
                watchGpsMaxAccuracyM = WATCH_GPS_MAX_ACCEPTED_ACCURACY_M,
                watchGpsAutoFallbackInteractiveMaxAccuracyM =
                WATCH_GPS_AUTO_FALLBACK_INTERACTIVE_MAX_ACCURACY_M,
            )
        return LocationFixPolicy.adaptAcceptanceForSourceMode(
            policy = basePolicy,
            sourceMode = sourceMode,
            watchGpsMaxAccuracyM = watchGpsMaxAccuracyM,
        )
    }

    private fun strictFreshMaxAgeMs(): Long {
        val expectedIntervalMs = engine.currentRequestIntervalOr(_effectiveUpdateIntervalMs.value)
        return resolveLocationTimingProfile(expectedIntervalMs).strictFreshFixMaxAgeMs
    }

    private fun hardMaxAcceptedFixAgeMs(): Long =
        when (engine.currentRuntimeModeOrNull()) {
            LocationRuntimeMode.PASSIVE -> HARD_STALE_FIX_MAX_AGE_PASSIVE_MS
            LocationRuntimeMode.INTERACTIVE,
            LocationRuntimeMode.BURST,
            null,
            -> HARD_STALE_FIX_MAX_AGE_INTERACTIVE_MS
        }

    private fun updateGnssDiagnostics(enabled: Boolean) {
        gnssDiagnosticsCoordinator.update(enabled)
    }

    private fun unregisterGnssDiagnostics(reason: String = "unspecified") {
        gnssDiagnosticsCoordinator.unregister(reason = reason)
    }

    private fun updateEnergySampling(enabled: Boolean) {
        if (!enabled) {
            energySampleJob?.cancel()
            energySampleJob = null
            return
        }

        if (energySampleJob?.isActive == true) return
        energySampleJob =
            serviceScope.launch {
                EnergyDiagnostics.recordSample(
                    context = this@LocationService,
                    reason = "capture_enabled",
                    detail = "source=location_service",
                )
                while (serviceJob.isActive && latestGpsDebugTelemetry) {
                    EnergyDiagnostics.recordSample(
                        context = this@LocationService,
                        reason = "periodic",
                        detail =
                            "effectiveIntervalMs=${_effectiveUpdateIntervalMs.value} " +
                                "burst=${engine.isBurstActive()} tracking=$latestTrackingEnabled " +
                                "bound=${isBound.value} keepOpen=${keepAppOpen.value}",
                    )
                    delay(ENERGY_SAMPLE_INTERVAL_MS)
                }
            }
    }

    private fun refreshKeepAliveNotificationState() {
        val desiredMode =
            when {
                !keepAppOpen.value -> KeepAliveNotificationMode.OFF
                shouldUseLocationForegroundMode() -> KeepAliveNotificationMode.LOCATION_FOREGROUND
                else -> KeepAliveNotificationMode.PINNED_NOTIFICATION
            }
        if (desiredMode == keepAliveNotificationMode) return

        when (desiredMode) {
            KeepAliveNotificationMode.OFF -> {
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                notificationFactory.cancel(NOTIFICATION_ID)
            }
            KeepAliveNotificationMode.PINNED_NOTIFICATION -> {
                if (keepAliveNotificationMode == KeepAliveNotificationMode.LOCATION_FOREGROUND) {
                    runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                }
                val notification =
                    notificationFactory.buildNotification(
                        isForegroundPinned = true,
                        notificationId = NOTIFICATION_ID,
                    )
                notificationFactory.show(NOTIFICATION_ID, notification)
            }
            KeepAliveNotificationMode.LOCATION_FOREGROUND -> {
                val notification =
                    notificationFactory.buildNotification(
                        isForegroundPinned = true,
                        notificationId = NOTIFICATION_ID,
                    )
                startForeground(NOTIFICATION_ID, notification)
            }
        }

        keepAliveNotificationMode = desiredMode
    }

    private fun currentLocationSourceMode(): LocationSourceMode = selfHealFailoverCoordinator.currentLocationSourceMode()

    private fun currentLocationGateway(): LocationGateway = locationGatewayFor(currentLocationSourceMode())

    private fun locationGatewayFor(sourceMode: LocationSourceMode): LocationGateway =
        when (sourceMode) {
            LocationSourceMode.AUTO_FUSED -> fusedLocationGateway
            LocationSourceMode.WATCH_GPS -> watchGpsLocationGateway
        }

    private suspend fun removeAllLocationUpdates() {
        var firstError: Exception? = null
        try {
            fusedLocationGateway.removeLocationUpdates()
        } catch (error: Exception) {
            firstError = error
        }
        try {
            watchGpsLocationGateway.removeLocationUpdates()
        } catch (error: Exception) {
            if (firstError == null) {
                firstError = error
            }
        }
        firstError?.let { throw it }
    }

    private fun removeAllLocationUpdatesBestEffort() {
        fusedLocationGateway.removeLocationUpdatesBestEffort()
        watchGpsLocationGateway.removeLocationUpdatesBestEffort()
    }

    private fun shouldUseLocationForegroundMode(): Boolean {
        val locationAllowedByUiState =
            latestTrackingEnabled &&
                (latestScreenState.isInteractive || latestAmbientGps)
        return locationAllowedByUiState
    }

    private fun isNonInteractiveScreenState(): Boolean = latestScreenState.isNonInteractive

    companion object {
        const val EXTRA_KEEP_APP_OPEN = "extra_keep_app_open"
        const val EXTRA_TRACKING_ENABLED = "extra_tracking_enabled"
        const val EXTRA_SCREEN_STATE = "extra_screen_state"
        private const val IMMEDIATE_GET_CURRENT_TIMEOUT_MS = 12_000L
        private const val HARD_STALE_FIX_MAX_AGE_INTERACTIVE_MS = 20_000L
        private const val HARD_STALE_FIX_MAX_AGE_PASSIVE_MS = 60_000L
        private const val SOURCE_MODE_WARMUP_MS = 1_500L
    }
}
