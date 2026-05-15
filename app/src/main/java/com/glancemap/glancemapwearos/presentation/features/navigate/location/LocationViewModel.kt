package com.glancemap.glancemapwearos.presentation.features.navigate

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.core.service.location.model.GpsSignalSnapshot
import com.glancemap.glancemapwearos.core.service.location.model.LocationScreenState
import com.glancemap.glancemapwearos.core.service.location.model.resolveLocationTimingProfile
import com.glancemap.glancemapwearos.core.service.location.service.LocationService
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class LocationViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val _currentLocation = MutableStateFlow<android.location.Location?>(null)
    val currentLocation = _currentLocation.asStateFlow()
    private val _gpsSignalSnapshot = MutableStateFlow(GpsSignalSnapshot())
    val gpsSignalSnapshot = _gpsSignalSnapshot.asStateFlow()
    private val _effectiveGpsIntervalMs = MutableStateFlow(SettingsRepository.DEFAULT_GPS_INTERVAL_MS)
    val effectiveGpsIntervalMs = _effectiveGpsIntervalMs.asStateFlow()

    private var locationService: LocationService? = null
    private var isBound = false
    private var isTrackingEnabled = false

    private var locationJob: Job? = null
    private var gpsSignalJob: Job? = null
    private var intervalJob: Job? = null
    private var desiredKeepAppOpen: Boolean = false
    private var desiredScreenState: LocationScreenState = LocationScreenState.INTERACTIVE
    private var pendingImmediateLocationRequestSource: String? = null
    private var lastImmediateRequestAtMs: Long = Long.MIN_VALUE
    private var lastStartupImmediateRequestAtMs: Long = Long.MIN_VALUE
    private var screenOffStartedAtMs: Long = Long.MIN_VALUE
    private var lastScreenOffDurationMs: Long? = null
    private var reconnectJob: Job? = null
    private var connectionWatchdogJob: Job? = null
    private var isBindingInProgress: Boolean = false
    private var lastBindAttemptAtMs: Long = 0L
    private var reconnectAttempt: Int = 0

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                className: ComponentName,
                service: IBinder,
            ) {
                val binder = service as LocationService.LocalBinder
                locationService = binder.getService()
                isBound = true
                isBindingInProgress = false
                lastBindAttemptAtMs = 0L
                reconnectAttempt = 0
                reconnectJob?.cancel()
                reconnectJob = null
                logConnection("service connected")

                locationJob?.cancel()
                locationJob =
                    locationService
                        ?.currentLocation
                        ?.onEach { _currentLocation.value = it }
                        ?.launchIn(viewModelScope)
                gpsSignalJob?.cancel()
                gpsSignalJob =
                    locationService
                        ?.gpsSignalSnapshot
                        ?.onEach { _gpsSignalSnapshot.value = it }
                        ?.launchIn(viewModelScope)
                intervalJob?.cancel()
                intervalJob =
                    locationService
                        ?.effectiveUpdateIntervalMs
                        ?.onEach { _effectiveGpsIntervalMs.value = it }
                        ?.launchIn(viewModelScope)

                locationService?.setKeepAppOpenState(desiredKeepAppOpen)
                locationService?.setRuntimeState(
                    screenState = desiredScreenState,
                    trackingEnabled = isTrackingEnabled,
                )
                pendingImmediateLocationRequestSource?.let { pendingSource ->
                    if (isTrackingEnabled) {
                        locationService?.requestImmediateLocation(source = "${pendingSource}_after_bind")
                    }
                    pendingImmediateLocationRequestSource = null
                }

                if (isTrackingEnabled) {
                    ensureConnectionWatchdog()
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                locationJob?.cancel()
                locationJob = null
                gpsSignalJob?.cancel()
                gpsSignalJob = null
                intervalJob?.cancel()
                intervalJob = null

                isBound = false
                isBindingInProgress = false
                lastBindAttemptAtMs = 0L
                locationService = null
                logConnection("service disconnected")
                // Keep last location to avoid flicker; UI can optionally show "disconnected" state
                if (shouldMaintainConnection()) {
                    scheduleReconnect(reason = "disconnected")
                }
            }
        }

    fun syncRuntimeState(
        screenState: LocationScreenState,
        trackingEnabled: Boolean,
    ) {
        val screenStateChanged = desiredScreenState != screenState
        val trackingChanged = isTrackingEnabled != trackingEnabled
        if (!screenStateChanged && !trackingChanged) return

        if (screenStateChanged) {
            updateScreenOffTelemetryState(
                previousScreenState = desiredScreenState,
                nextScreenState = screenState,
                nowElapsedMs = SystemClock.elapsedRealtime(),
            )
        }

        desiredScreenState = screenState

        if (!trackingChanged) {
            locationService?.setRuntimeState(
                screenState = screenState,
                trackingEnabled = trackingEnabled,
            )
            return
        }

        isTrackingEnabled = trackingEnabled

        if (trackingEnabled) {
            startService(keepAppOpen = desiredKeepAppOpen, trackingEnabled = true)
            bindService()
            locationService?.setRuntimeState(
                screenState = screenState,
                trackingEnabled = true,
            )
            dispatchPendingImmediateLocationRequestIfTrackingEnabled(suffix = "after_tracking_enable")
            ensureConnectionWatchdog()
        } else {
            locationService?.setRuntimeState(
                screenState = screenState,
                trackingEnabled = false,
            )
            pendingImmediateLocationRequestSource = null
            if (desiredKeepAppOpen && locationService == null) {
                startService(keepAppOpen = true, trackingEnabled = false)
            }
            stopConnectionRecovery()
            unbindService()
            if (!desiredKeepAppOpen) stopService()
        }
    }

    fun setTrackingEnabled(enabled: Boolean) {
        syncRuntimeState(
            screenState = desiredScreenState,
            trackingEnabled = enabled,
        )
    }

    fun setKeepAppOpen(enabled: Boolean) {
        desiredKeepAppOpen = enabled

        if (enabled) {
            // Start the service shell so it can keep the app pinned if needed.
            startService(keepAppOpen = true, trackingEnabled = isTrackingEnabled)
            if (isTrackingEnabled) {
                bindService()
            } else {
                stopConnectionRecovery()
                unbindService()
            }
        }

        locationService?.setKeepAppOpenState(enabled)

        if (!enabled && !isTrackingEnabled) {
            stopConnectionRecovery()
            unbindService()
            stopService()
        }
    }

    fun setScreenState(state: LocationScreenState) {
        syncRuntimeState(
            screenState = state,
            trackingEnabled = isTrackingEnabled,
        )
    }

    fun requestImmediateLocation(source: String = "ui_unknown") {
        val now = SystemClock.elapsedRealtime()
        val forceImmediateRequest = shouldForceUiImmediateLocationRequest(source)
        if (lastImmediateRequestAtMs != Long.MIN_VALUE) {
            val elapsedSinceLastRequestMs = (now - lastImmediateRequestAtMs).coerceAtLeast(0L)
            if (elapsedSinceLastRequestMs < UI_IMMEDIATE_REQUEST_DEBOUNCE_MS) {
                return
            }
        }

        if (
            source.startsWith("ui_") &&
            !forceImmediateRequest &&
            shouldSkipUiImmediateRequest(nowElapsedMs = now)
        ) {
            return
        }

        if (source.startsWith(UI_STARTUP_REQUEST_SOURCE_PREFIX)) {
            if (lastStartupImmediateRequestAtMs != Long.MIN_VALUE) {
                val startupElapsedMs = (now - lastStartupImmediateRequestAtMs).coerceAtLeast(0L)
                if (startupElapsedMs < UI_STARTUP_IMMEDIATE_REQUEST_COOLDOWN_MS) {
                    return
                }
            }
            lastStartupImmediateRequestAtMs = now
        }

        logWakeBurstCandidateTelemetry(
            source = source,
            nowElapsedMs = now,
        )

        lastImmediateRequestAtMs = now

        if (!isTrackingEnabled) {
            pendingImmediateLocationRequestSource = source
            return
        }

        val service = locationService
        if (service != null) {
            service.requestImmediateLocation(source = source)
            pendingImmediateLocationRequestSource = null
        } else {
            pendingImmediateLocationRequestSource = source
        }
    }

    private fun dispatchPendingImmediateLocationRequestIfTrackingEnabled(suffix: String) {
        if (!isTrackingEnabled) return
        val service = locationService ?: return
        val pendingSource = pendingImmediateLocationRequestSource ?: return
        service.requestImmediateLocation(source = "${pendingSource}_$suffix")
        pendingImmediateLocationRequestSource = null
    }

    private fun shouldSkipUiImmediateRequest(nowElapsedMs: Long): Boolean {
        val snapshot = _gpsSignalSnapshot.value
        val timingProfile = resolveLocationTimingProfile(_effectiveGpsIntervalMs.value)
        val fixAgeMs = snapshot.resolveLastFixAgeMs(nowElapsedMs = nowElapsedMs)
        if (fixAgeMs <= 0L || fixAgeMs == Long.MAX_VALUE) return false
        val serviceFreshnessMaxAgeMs =
            snapshot.lastFixFreshMaxAgeMs
                .takeIf { it > 0L }
                ?: timingProfile.strictFreshFixMaxAgeMs
        val freshnessMaxAgeMs =
            minOf(
                serviceFreshnessMaxAgeMs,
                timingProfile.uiImmediateSkipMaxAgeMs,
            )
        if (fixAgeMs > freshnessMaxAgeMs) return false
        return fixAgeMs <= timingProfile.uiImmediateSkipMaxAgeMs
    }

    private fun updateScreenOffTelemetryState(
        previousScreenState: LocationScreenState,
        nextScreenState: LocationScreenState,
        nowElapsedMs: Long,
    ) {
        val wasInteractive = previousScreenState == LocationScreenState.INTERACTIVE
        val isInteractive = nextScreenState == LocationScreenState.INTERACTIVE
        if (wasInteractive && !isInteractive) {
            screenOffStartedAtMs = nowElapsedMs
            return
        }
        if (!wasInteractive && isInteractive && screenOffStartedAtMs != Long.MIN_VALUE) {
            lastScreenOffDurationMs = (nowElapsedMs - screenOffStartedAtMs).coerceAtLeast(0L)
            screenOffStartedAtMs = Long.MIN_VALUE
        }
    }

    private fun logWakeBurstCandidateTelemetry(
        source: String,
        nowElapsedMs: Long,
    ) {
        if (!source.startsWith(UI_STARTUP_REQUEST_SOURCE_PREFIX)) return

        val snapshot = _gpsSignalSnapshot.value
        val fixAgeMs = snapshot.resolveLastFixAgeMs(nowElapsedMs = nowElapsedMs)
        val accuracyM = snapshot.lastFixAccuracyM.takeIf { it.isFinite() }
        val screenOffMs = lastScreenOffDurationMs
        val decision =
            evaluateWakeBurstSkipCandidate(
                fixAgeMs = fixAgeMs,
                accuracyM = accuracyM,
                screenOffMs = screenOffMs,
            )
        DebugTelemetry.log(
            CONNECTION_TELEMETRY_TAG,
            "wakeBurstCandidate source=$source wouldSkip=${decision.wouldSkip} " +
                "reason=${decision.reason} fixAgeMs=${fixAgeMs.telemetryValue()} " +
                "accuracyM=${accuracyM.telemetryValue()} screenOffMs=${screenOffMs.telemetryValue()} " +
                "fixMaxAgeMs=$WAKE_BURST_SKIP_FIX_MAX_AGE_MS " +
                "accuracyMaxM=${WAKE_BURST_SKIP_MAX_ACCURACY_M.telemetryValue()} " +
                "screenOffMaxMs=$WAKE_BURST_SKIP_SCREEN_OFF_MAX_MS",
        )
    }

    private fun bindService() {
        if (isBound || isBindingInProgress) return
        Intent(getApplication(), LocationService::class.java).also { intent ->
            val bound =
                runCatching {
                    getApplication<Application>().bindService(intent, connection, Context.BIND_AUTO_CREATE)
                }.getOrDefault(false)
            if (bound) {
                isBindingInProgress = true
                lastBindAttemptAtMs = SystemClock.elapsedRealtime()
                logConnection("bind requested")
            } else {
                isBindingInProgress = false
                lastBindAttemptAtMs = 0L
                logConnection("bind request failed")
                if (shouldMaintainConnection()) {
                    scheduleReconnect(reason = "bind_failed")
                }
            }
        }
    }

    private fun unbindService() {
        if (!isBound && !isBindingInProgress) return

        locationJob?.cancel()
        locationJob = null
        gpsSignalJob?.cancel()
        gpsSignalJob = null
        intervalJob?.cancel()
        intervalJob = null

        if (isBound) {
            runCatching { getApplication<Application>().unbindService(connection) }
        }
        isBound = false
        isBindingInProgress = false
        lastBindAttemptAtMs = 0L
        locationService = null
    }

    private fun startService(
        keepAppOpen: Boolean,
        trackingEnabled: Boolean,
    ) {
        val app = getApplication<Application>()
        Intent(app, LocationService::class.java).also { intent ->
            intent.putExtra(LocationService.EXTRA_KEEP_APP_OPEN, keepAppOpen)
            intent.putExtra(LocationService.EXTRA_TRACKING_ENABLED, trackingEnabled)
            intent.putExtra(LocationService.EXTRA_SCREEN_STATE, desiredScreenState.name)
            val shouldUseForegroundStart = keepAppOpen && trackingEnabled
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && shouldUseForegroundStart) {
                ContextCompat.startForegroundService(app, intent)
            } else {
                app.startService(intent)
            }
        }
    }

    private fun stopService() {
        Intent(getApplication(), LocationService::class.java).also { intent ->
            getApplication<Application>().stopService(intent)
        }
    }

    override fun onCleared() {
        stopConnectionRecovery()
        unbindService()
        locationJob?.cancel()
        locationJob = null
        gpsSignalJob?.cancel()
        gpsSignalJob = null
        intervalJob?.cancel()
        intervalJob = null
        locationService = null
        super.onCleared()
    }

    private fun shouldMaintainConnection(): Boolean = isTrackingEnabled

    private fun ensureConnectionWatchdog() {
        if (!shouldMaintainConnection()) return
        if (connectionWatchdogJob?.isActive == true) return
        connectionWatchdogJob =
            viewModelScope.launch {
                while (isTrackingEnabled) {
                    delay(CONNECTION_WATCHDOG_INTERVAL_MS)
                    if (!isTrackingEnabled) break
                    if (isBound) continue
                    if (isBindingInProgress) {
                        val bindAgeMs =
                            if (lastBindAttemptAtMs > 0L) {
                                (SystemClock.elapsedRealtime() - lastBindAttemptAtMs).coerceAtLeast(0L)
                            } else {
                                0L
                            }
                        if (bindAgeMs < BIND_ATTEMPT_TIMEOUT_MS) {
                            continue
                        }
                        isBindingInProgress = false
                        lastBindAttemptAtMs = 0L
                        logConnection("bind timeout, scheduling reconnect")
                    } else {
                        logConnection("watchdog detected unbound state, scheduling reconnect")
                    }
                    scheduleReconnect(reason = "watchdog")
                }
            }
    }

    private fun scheduleReconnect(reason: String) {
        if (!shouldMaintainConnection()) return
        if (isBound) return
        if (reconnectJob?.isActive == true) return

        reconnectAttempt += 1
        val attempt = reconnectAttempt
        val delayMs = reconnectDelayMs(attempt)
        reconnectJob =
            viewModelScope.launch {
                if (delayMs > 0L) {
                    delay(delayMs)
                }
                if (!shouldMaintainConnection() || isBound) {
                    reconnectJob = null
                    return@launch
                }
                logConnection(
                    "reconnect attempt=$attempt reason=$reason keepOpen=$desiredKeepAppOpen",
                )
                startService(keepAppOpen = desiredKeepAppOpen, trackingEnabled = isTrackingEnabled)
                bindService()
                reconnectJob = null
            }
    }

    private fun stopConnectionRecovery() {
        reconnectJob?.cancel()
        reconnectJob = null
        connectionWatchdogJob?.cancel()
        connectionWatchdogJob = null
        reconnectAttempt = 0
    }

    private fun reconnectDelayMs(attempt: Int): Long =
        when (attempt) {
            1 -> 0L
            2 -> 2_000L
            3 -> 5_000L
            4 -> 10_000L
            else -> 15_000L
        }

    private fun logConnection(message: String) {
        DebugTelemetry.log(CONNECTION_TELEMETRY_TAG, message)
    }
}

internal fun shouldForceUiImmediateLocationRequest(source: String): Boolean =
    source.startsWith(UI_STARTUP_REQUEST_SOURCE_PREFIX) ||
        source == UI_WAKE_REACQUIRE_TIMEOUT_SOURCE

private data class WakeBurstSkipCandidate(
    val wouldSkip: Boolean,
    val reason: String,
)

private fun evaluateWakeBurstSkipCandidate(
    fixAgeMs: Long,
    accuracyM: Float?,
    screenOffMs: Long?,
): WakeBurstSkipCandidate =
    when {
        fixAgeMs <= 0L || fixAgeMs == Long.MAX_VALUE -> {
            WakeBurstSkipCandidate(wouldSkip = false, reason = "no_recent_fix")
        }
        fixAgeMs > WAKE_BURST_SKIP_FIX_MAX_AGE_MS -> {
            WakeBurstSkipCandidate(wouldSkip = false, reason = "fix_too_old")
        }
        accuracyM == null -> {
            WakeBurstSkipCandidate(wouldSkip = false, reason = "accuracy_unknown")
        }
        accuracyM > WAKE_BURST_SKIP_MAX_ACCURACY_M -> {
            WakeBurstSkipCandidate(wouldSkip = false, reason = "accuracy_too_low")
        }
        screenOffMs == null -> {
            WakeBurstSkipCandidate(wouldSkip = false, reason = "screen_off_unknown")
        }
        screenOffMs > WAKE_BURST_SKIP_SCREEN_OFF_MAX_MS -> {
            WakeBurstSkipCandidate(wouldSkip = false, reason = "screen_off_too_long")
        }
        else -> {
            WakeBurstSkipCandidate(wouldSkip = true, reason = "fresh_fix_after_short_screen_off")
        }
    }

private fun GpsSignalSnapshot.resolveLastFixAgeMs(nowElapsedMs: Long): Long =
    if (lastFixElapsedRealtimeMs > 0L) {
        (nowElapsedMs - lastFixElapsedRealtimeMs).coerceAtLeast(0L)
    } else {
        lastFixAgeMs
    }

private fun Long.telemetryValue(): String =
    if (this == Long.MAX_VALUE) {
        "na"
    } else {
        toString()
    }

private fun Long?.telemetryValue(): String = this?.telemetryValue() ?: "na"

private fun Float?.telemetryValue(): String = this?.let { "%.1f".format(it) } ?: "na"

private const val UI_IMMEDIATE_REQUEST_DEBOUNCE_MS = 1_500L

// Keep wake startup responsive while still avoiding repeated duplicate bursts.
private const val UI_STARTUP_IMMEDIATE_REQUEST_COOLDOWN_MS = 6_000L
private const val UI_STARTUP_REQUEST_SOURCE_PREFIX = "ui_startup_fresh_fix"
internal const val UI_WAKE_REACQUIRE_TIMEOUT_SOURCE = "ui_wake_reacquire_timeout"
private const val WAKE_BURST_SKIP_FIX_MAX_AGE_MS = 2_000L
private const val WAKE_BURST_SKIP_MAX_ACCURACY_M = 35f
private const val WAKE_BURST_SKIP_SCREEN_OFF_MAX_MS = 10_000L
private const val CONNECTION_WATCHDOG_INTERVAL_MS = 10_000L
private const val BIND_ATTEMPT_TIMEOUT_MS = 15_000L
private const val CONNECTION_TELEMETRY_TAG = "LocationVM"
