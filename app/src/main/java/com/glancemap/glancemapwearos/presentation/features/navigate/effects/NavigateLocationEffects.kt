package com.glancemap.glancemapwearos.presentation.features.navigate.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.core.service.location.model.LocationScreenState
import com.glancemap.glancemapwearos.core.service.location.model.isNonInteractive
import com.glancemap.glancemapwearos.core.service.location.model.resolveLocationTimingProfile
import com.glancemap.glancemapwearos.core.service.location.policy.LocationFixPolicy
import com.glancemap.glancemapwearos.domain.sensors.CompassViewModel
import com.glancemap.glancemapwearos.presentation.features.maps.RotatableMarker
import com.glancemap.glancemapwearos.presentation.features.navigate.GpsFixIndicatorState
import com.glancemap.glancemapwearos.presentation.features.navigate.LocationViewModel
import com.glancemap.glancemapwearos.presentation.features.navigate.NavigateViewModel
import com.glancemap.glancemapwearos.presentation.features.navigate.UI_WAKE_REACQUIRE_TIMEOUT_SOURCE
import com.glancemap.glancemapwearos.presentation.features.navigate.motion.MarkerMotionController
import com.glancemap.glancemapwearos.presentation.features.navigate.requestLayerRedrawSafely
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidBitmap
import org.mapsforge.map.android.view.MapView

internal data class NavigateLocationUiState(
    val locationMarker: RotatableMarker?,
    val gpsIndicatorState: GpsFixIndicatorState,
    val showGpsIndicatorUnpinned: Boolean,
    val watchGpsDegradedWarning: Boolean,
)

internal data class WakeAnchorSeed(
    val latLong: LatLong,
    val fixElapsedMs: Long,
    val accuracyM: Float,
    val speedMps: Float,
    val bearingDeg: Float?,
)

@Composable
internal fun rememberNavigateLocationUiState(
    mapView: MapView,
    locationViewModel: LocationViewModel,
    compassViewModel: CompassViewModel,
    navigateViewModel: NavigateViewModel,
    shouldTrackLocation: Boolean,
    shouldFollowPosition: Boolean,
    screenState: LocationScreenState,
    expectedGpsIntervalMs: Long,
    navigationMarkerBitmap: AndroidBitmap,
    suppressLocationMarker: Boolean,
): NavigateLocationUiState {
    val timingProfile =
        remember(expectedGpsIntervalMs) {
            resolveLocationTimingProfile(expectedGpsIntervalMs)
        }
    val markerMotionController =
        remember(timingProfile.intervalMs) {
            MarkerMotionController(
                predictionFreshnessMaxAgeMs = timingProfile.markerPredictionFreshnessMaxAgeMs,
                maxAcceptedFixAgeMs = timingProfile.stabilizerMaxAcceptedFixAgeMs,
            )
        }

    val latestShouldFollowPosition = rememberUpdatedState(shouldFollowPosition)
    val latestSuppressLocationMarker = rememberUpdatedState(suppressLocationMarker)

    var locationMarker by remember { mutableStateOf<RotatableMarker?>(null) }
    var lastRenderedMarkerLatLong by remember { mutableStateOf<LatLong?>(null) }
    var indicatorFixAtElapsedMs by remember { mutableLongStateOf(0L) }
    var indicatorFixAccuracyM by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
    var indicatorFixFreshMaxAgeMs by remember { mutableLongStateOf(0L) }
    var indicatorLocationAvailable by remember { mutableStateOf(true) }
    var indicatorUnavailableSinceElapsedMs by remember { mutableLongStateOf(0L) }
    var indicatorWatchGpsDegraded by remember { mutableStateOf(false) }
    var holdMarkerUntilFreshFix by
        remember(shouldTrackLocation, screenState) {
            mutableStateOf(shouldTrackLocation && !screenState.isNonInteractive)
        }
    var holdMarkerStartedAtElapsedMs by
        remember(shouldTrackLocation, screenState) { mutableLongStateOf(0L) }
    var trackingActivatedAtElapsedMs by
        remember(shouldTrackLocation, screenState) { mutableLongStateOf(0L) }
    var postWakePredictionHoldUntilElapsedMs by
        remember(shouldTrackLocation, screenState) { mutableLongStateOf(0L) }
    var lastAcceptedLocationFixElapsedMs by remember { mutableLongStateOf(0L) }
    var lastMarkerVisualUpdateAtElapsedMs by remember { mutableLongStateOf(0L) }
    var lastInteractiveStaleRefreshAtElapsedMs by remember { mutableLongStateOf(Long.MIN_VALUE) }
    var lastWakeReacquireStartedAtElapsedMs by remember { mutableLongStateOf(Long.MIN_VALUE) }
    var activeWakeSessionId by remember { mutableLongStateOf(0L) }
    var nextWakeSessionId by remember { mutableLongStateOf(0L) }
    var wakeAnchorSeeded by
        remember(shouldTrackLocation, screenState) { mutableStateOf(false) }
    var wasInteractiveTrackingActive by remember { mutableStateOf(false) }

    var gpsIndicatorClockMs by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }

    LaunchedEffect(shouldTrackLocation) {
        if (shouldTrackLocation) return@LaunchedEffect
        // Always clear motion memory when tracking stops to avoid stale-gap carry-over.
        holdMarkerUntilFreshFix = false
        holdMarkerStartedAtElapsedMs = 0L
        trackingActivatedAtElapsedMs = 0L
        postWakePredictionHoldUntilElapsedMs = 0L
        lastAcceptedLocationFixElapsedMs = 0L
        lastMarkerVisualUpdateAtElapsedMs = 0L
        lastInteractiveStaleRefreshAtElapsedMs = Long.MIN_VALUE
        activeWakeSessionId = 0L
        wakeAnchorSeeded = false
        wasInteractiveTrackingActive = false
        markerMotionController.reset(reason = "tracking_stopped")
    }

    val interactiveTrackingActive = shouldTrackLocation && !screenState.isNonInteractive

    // Only re-enter wake handling when interactive tracking actually starts again.
    LaunchedEffect(interactiveTrackingActive) {
        val shouldStartWakeSession =
            shouldStartInteractiveWakeSession(
                wasInteractiveTrackingActive = wasInteractiveTrackingActive,
                shouldTrackLocation = shouldTrackLocation,
                screenState = screenState,
            )
        wasInteractiveTrackingActive = interactiveTrackingActive
        if (!shouldStartWakeSession) return@LaunchedEffect

        val nowElapsedMs = android.os.SystemClock.elapsedRealtime()
        trackingActivatedAtElapsedMs = nowElapsedMs
        postWakePredictionHoldUntilElapsedMs = 0L
        wakeAnchorSeeded = false
        markerMotionController.reset(reason = "interactive_start")
        resolveWakeAnchorSeedOrNull(
            location = locationViewModel.currentLocation.value,
            receivedAtElapsedMs = nowElapsedMs,
            nowWallClockMs = System.currentTimeMillis(),
            maxAgeMs = computeWakeAnchorMaxAgeMs(expectedGpsIntervalMs),
            maxAccuracyM = WAKE_ANCHOR_MAX_ACCURACY_M,
        )?.let { anchor ->
            markerMotionController.seedAnchor(
                latLong = anchor.latLong,
                fixElapsedMs = anchor.fixElapsedMs,
                accuracyM = anchor.accuracyM,
                speedMps = anchor.speedMps,
                bearingDeg = anchor.bearingDeg,
            )
            lastRenderedMarkerLatLong = anchor.latLong
            wakeAnchorSeeded = true
            if (!latestSuppressLocationMarker.value && locationMarker == null) {
                removeAllRotatableMarkers(mapView)
                locationMarker =
                    RotatableMarker(
                        anchor.latLong,
                        navigationMarkerBitmap,
                        -navigationMarkerBitmap.width / 2,
                        -navigationMarkerBitmap.height / 2,
                    ).also { marker ->
                        mapView.layerManager.layers.add(marker)
                        lastMarkerVisualUpdateAtElapsedMs = nowElapsedMs
                        mapView.requestLayerRedrawSafely()
                    }
            }
        }
        val wakeReacquireInCooldown =
            isWakeReacquireCooldownActive(
                nowElapsedMs = nowElapsedMs,
                lastStartedAtElapsedMs = lastWakeReacquireStartedAtElapsedMs,
                cooldownMs = WAKE_REACQUIRE_COOLDOWN_MS,
            )
        if (wakeReacquireInCooldown) {
            holdMarkerUntilFreshFix = false
            holdMarkerStartedAtElapsedMs = 0L
            activeWakeSessionId = 0L
            return@LaunchedEffect
        }
        nextWakeSessionId += 1L
        activeWakeSessionId = nextWakeSessionId
        lastWakeReacquireStartedAtElapsedMs = nowElapsedMs
        holdMarkerUntilFreshFix = true
        holdMarkerStartedAtElapsedMs = nowElapsedMs
        logWakeSessionEvent(
            stage = "start",
            sessionId = activeWakeSessionId,
            nowElapsedMs = nowElapsedMs,
            reason = if (wakeAnchorSeeded) "seeded" else "no_anchor",
        )
        markerMotionController.requireFreshFixForPrediction()
        locationViewModel.requestImmediateLocation(source = "ui_startup_fresh_fix")
    }

    LaunchedEffect(activeWakeSessionId, shouldTrackLocation, screenState, locationViewModel) {
        val wakeSessionId = activeWakeSessionId
        if (wakeSessionId <= 0L || !shouldTrackLocation || screenState.isNonInteractive) {
            return@LaunchedEffect
        }
        delay(WAKE_REACQUIRE_TIMEOUT_MS)
        val wakeSessionStillActive =
            activeWakeSessionId == wakeSessionId && holdMarkerUntilFreshFix
        val interactiveTrackingActive = shouldTrackLocation && !screenState.isNonInteractive
        if (!wakeSessionStillActive || !interactiveTrackingActive) {
            return@LaunchedEffect
        }
        logWakeSessionEvent(
            stage = "timeout_refresh",
            sessionId = wakeSessionId,
            nowElapsedMs = android.os.SystemClock.elapsedRealtime(),
        )
        locationViewModel.requestImmediateLocation(
            source = UI_WAKE_REACQUIRE_TIMEOUT_SOURCE,
        )
    }

    LaunchedEffect(screenState) {
        if (screenState.isNonInteractive) return@LaunchedEffect
        while (isActive) {
            gpsIndicatorClockMs = android.os.SystemClock.elapsedRealtime()
            delay(1000L)
        }
    }

    LaunchedEffect(locationViewModel) {
        locationViewModel.gpsSignalSnapshot.collect { signal ->
            indicatorFixAtElapsedMs = signal.lastFixElapsedRealtimeMs
            indicatorFixAccuracyM = signal.lastFixAccuracyM
            indicatorFixFreshMaxAgeMs = signal.lastFixFreshMaxAgeMs
            indicatorLocationAvailable = signal.isLocationAvailable
            indicatorUnavailableSinceElapsedMs = signal.unavailableSinceElapsedMs
            indicatorWatchGpsDegraded = signal.watchGpsOnlyActive && signal.watchGpsDegraded
        }
    }

    val gpsStaleIndicatorThresholdMs = timingProfile.indicatorStaleThresholdMs
    val gpsIndicatorRawState =
        resolveGpsIndicatorState(
            isLocationAvailable = indicatorLocationAvailable,
            unavailableSinceElapsedMs = indicatorUnavailableSinceElapsedMs,
            lastFixAtElapsedMs = indicatorFixAtElapsedMs,
            accuracyM = indicatorFixAccuracyM,
            nowElapsedMs = gpsIndicatorClockMs,
            staleThresholdMs = gpsStaleIndicatorThresholdMs,
        )
    val gpsIndicatorDisplayRawState =
        resolveGpsIndicatorDisplayState(
            rawState = gpsIndicatorRawState,
        )
    val gpsIndicatorState = gpsIndicatorDisplayRawState
    val watchGpsDegradedWarning = shouldTrackLocation && indicatorWatchGpsDegraded
    val showGpsIndicatorUnpinned =
        shouldShowGpsIndicatorUnpinned(
            gpsIndicatorState = gpsIndicatorState,
            watchGpsDegradedWarning = watchGpsDegradedWarning,
        )

    LaunchedEffect(mapView, navigationMarkerBitmap) {
        if (latestSuppressLocationMarker.value) {
            locationMarker?.let { marker -> mapView.layerManager.layers.remove(marker) }
            locationMarker = null
            lastRenderedMarkerLatLong = null
            mapView.requestLayerRedrawSafely()
            return@LaunchedEffect
        }
        val currentMarker = locationMarker
        if (currentMarker == null) {
            val fallbackLatLong = lastRenderedMarkerLatLong ?: return@LaunchedEffect
            locationMarker =
                RotatableMarker(
                    fallbackLatLong,
                    navigationMarkerBitmap,
                    -navigationMarkerBitmap.width / 2,
                    -navigationMarkerBitmap.height / 2,
                ).also { marker ->
                    mapView.layerManager.layers.add(marker)
                    lastMarkerVisualUpdateAtElapsedMs = android.os.SystemClock.elapsedRealtime()
                    mapView.requestLayerRedrawSafely()
                }
            return@LaunchedEffect
        }

        val latLong = currentMarker.latLong ?: return@LaunchedEffect
        val heading = currentMarker.heading
        val isVisible = currentMarker.isVisible
        mapView.layerManager.layers.remove(currentMarker)
        locationMarker =
            RotatableMarker(
                latLong,
                navigationMarkerBitmap,
                -navigationMarkerBitmap.width / 2,
                -navigationMarkerBitmap.height / 2,
            ).also { marker ->
                marker.heading = heading
                marker.isVisible = isVisible
                mapView.layerManager.layers.add(marker)
                lastMarkerVisualUpdateAtElapsedMs = android.os.SystemClock.elapsedRealtime()
                mapView.requestLayerRedrawSafely()
            }
    }

    LaunchedEffect(suppressLocationMarker, mapView) {
        if (!suppressLocationMarker) return@LaunchedEffect
        locationMarker?.let { marker ->
            mapView.layerManager.layers.remove(marker)
        }
        locationMarker = null
        lastRenderedMarkerLatLong = null
        holdMarkerUntilFreshFix = false
        holdMarkerStartedAtElapsedMs = 0L
        trackingActivatedAtElapsedMs = 0L
        postWakePredictionHoldUntilElapsedMs = 0L
        lastAcceptedLocationFixElapsedMs = 0L
        lastMarkerVisualUpdateAtElapsedMs = 0L
        lastInteractiveStaleRefreshAtElapsedMs = Long.MIN_VALUE
        activeWakeSessionId = 0L
        wakeAnchorSeeded = false
        markerMotionController.reset(reason = "marker_hidden")
        mapView.requestLayerRedrawSafely()
    }

    // Restores old working behavior: center only when shouldFollowPosition is true.
    LaunchedEffect(locationViewModel, mapView) {
        locationViewModel.currentLocation
            .filterNotNull()
            .collect { loc ->
                if (latestSuppressLocationMarker.value) return@collect

                val receivedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
                val fixElapsedMs =
                    resolveLocationFixElapsedRealtimeMs(
                        elapsedRealtimeNanos = loc.elapsedRealtimeNanos,
                        utcTimeMs = loc.time,
                        receivedAtElapsedMs = receivedAtElapsedMs,
                        nowWallClockMs = System.currentTimeMillis(),
                    )
                val localFixAgeMs =
                    if (fixElapsedMs > 0L) {
                        (receivedAtElapsedMs - fixElapsedMs).coerceAtLeast(0L)
                    } else {
                        Long.MAX_VALUE
                    }
                val wakeSnapEligible =
                    localFixAgeMs <= computeWakeReacquireSnapMaxAgeMs(expectedGpsIntervalMs) &&
                        loc.accuracy.isFinite() &&
                        loc.accuracy <= WAKE_REACQUIRE_SNAP_MAX_ACCURACY_M
                val wakeReleaseEligible =
                    localFixAgeMs <= computeWakeReacquireReleaseMaxAgeMs(expectedGpsIntervalMs)
                val previousAcceptedFixGapMs =
                    resolveAcceptedFixGapMs(
                        previousFixElapsedMs = lastAcceptedLocationFixElapsedMs,
                        currentFixElapsedMs = fixElapsedMs,
                    )
                val fixFromCurrentTrackingSession =
                    trackingActivatedAtElapsedMs <= 0L ||
                        (
                            fixElapsedMs > 0L &&
                                fixElapsedMs + TRACKING_SESSION_FIX_MAX_SKEW_MS >= trackingActivatedAtElapsedMs
                        )
                val holdTimedOut =
                    holdMarkerStartedAtElapsedMs > 0L &&
                        (receivedAtElapsedMs - holdMarkerStartedAtElapsedMs)
                            .coerceAtLeast(0L) >= WAKE_REACQUIRE_TIMEOUT_MS
                val releaseFromWakeHold =
                    shouldReleaseWakeReacquireHold(
                        holdMarkerUntilFreshFix = holdMarkerUntilFreshFix,
                        fixFromCurrentTrackingSession = fixFromCurrentTrackingSession,
                        wakeSnapEligible = wakeSnapEligible,
                        wakeReleaseEligible = wakeReleaseEligible,
                        holdTimedOut = holdTimedOut,
                    )
                if (holdMarkerUntilFreshFix && !releaseFromWakeHold) {
                    return@collect
                }
                val keepWakeAnchorForCorrection = releaseFromWakeHold && wakeAnchorSeeded
                if (releaseFromWakeHold) {
                    val releasedWakeSessionId = activeWakeSessionId
                    holdMarkerUntilFreshFix = false
                    holdMarkerStartedAtElapsedMs = 0L
                    postWakePredictionHoldUntilElapsedMs =
                        receivedAtElapsedMs + POST_WAKE_PREDICTION_GRACE_MS
                    activeWakeSessionId = 0L
                    if (releasedWakeSessionId > 0L) {
                        logWakeSessionEvent(
                            stage = "cancel",
                            sessionId = releasedWakeSessionId,
                            nowElapsedMs = receivedAtElapsedMs,
                            reason = if (wakeSnapEligible) "fresh_fix" else "timeout_release",
                            fixAgeMs = localFixAgeMs,
                        )
                    }
                    if (keepWakeAnchorForCorrection) {
                        wakeAnchorSeeded = false
                    } else {
                        markerMotionController.reset(reason = "fresh_fix_release")
                    }
                }

                compassViewModel.updateDeclinationFromLocation(loc)

                val ll = toValidLatLongOrNull(loc.latitude, loc.longitude) ?: return@collect
                navigateViewModel.onLocationUpdate(ll)
                val motionSpeedMps =
                    if (loc.hasSpeed() && loc.speed.isFinite()) {
                        loc.speed
                    } else {
                        0f
                    }

                val displayLatLong =
                    markerMotionController.onGpsFix(
                        latLong = ll,
                        nowElapsedMs = receivedAtElapsedMs,
                        fixElapsedMs = fixElapsedMs,
                        accuracyM = loc.accuracy,
                        rawSpeedMps = motionSpeedMps,
                        rawBearingDeg = if (loc.hasBearing()) loc.bearing else null,
                        allowLargeCorrection =
                            shouldBypassCorrectionClamp(
                                releaseFromWakeHold = releaseFromWakeHold,
                                previousAcceptedFixGapMs = previousAcceptedFixGapMs,
                                expectedGpsIntervalMs = expectedGpsIntervalMs,
                            ),
                    )
                lastAcceptedLocationFixElapsedMs =
                    fixElapsedMs.takeIf { it > 0L } ?: receivedAtElapsedMs

                if (locationMarker == null) {
                    removeAllRotatableMarkers(mapView)
                    locationMarker =
                        RotatableMarker(
                            displayLatLong,
                            navigationMarkerBitmap,
                            -navigationMarkerBitmap.width / 2,
                            -navigationMarkerBitmap.height / 2,
                        ).also { marker ->
                            mapView.layerManager.layers.add(marker)
                        }
                } else {
                    locationMarker?.latLong = displayLatLong
                }
                lastRenderedMarkerLatLong = displayLatLong
                lastMarkerVisualUpdateAtElapsedMs = receivedAtElapsedMs

                if (
                    shouldCenterOnRenderedMarker(
                        shouldFollowPosition = latestShouldFollowPosition.value,
                        target = displayLatLong,
                        currentCenter = mapView.model.mapViewPosition.center,
                    )
                ) {
                    mapView.setCenter(displayLatLong)
                }

                mapView.requestLayerRedrawSafely()
            }
    }

    // Cleanup marker when leaving screen.
    DisposableEffect(mapView) {
        onDispose {
            locationMarker?.let { marker -> mapView.layerManager.layers.remove(marker) }
            locationMarker = null
            lastRenderedMarkerLatLong = null
            holdMarkerUntilFreshFix = false
            holdMarkerStartedAtElapsedMs = 0L
            trackingActivatedAtElapsedMs = 0L
            postWakePredictionHoldUntilElapsedMs = 0L
            lastAcceptedLocationFixElapsedMs = 0L
            lastMarkerVisualUpdateAtElapsedMs = 0L
            lastInteractiveStaleRefreshAtElapsedMs = Long.MIN_VALUE
            activeWakeSessionId = 0L
            wakeAnchorSeeded = false
            markerMotionController.reset(reason = "dispose")
        }
    }

    // Motion prediction loop.
    LaunchedEffect(
        shouldTrackLocation,
        screenState,
        mapView,
        markerMotionController,
    ) {
        if (!shouldTrackLocation) return@LaunchedEffect
        if (screenState.isNonInteractive) return@LaunchedEffect

        while (isActive) {
            if (latestSuppressLocationMarker.value) {
                delay(80L)
                continue
            }
            delay(markerMotionController.suggestedPredictionTickMs())
            val nowElapsedMs = android.os.SystemClock.elapsedRealtime()
            if (
                holdMarkerUntilFreshFix ||
                isPostWakePredictionHoldActive(
                    nowElapsedMs = nowElapsedMs,
                    holdUntilElapsedMs = postWakePredictionHoldUntilElapsedMs,
                )
            ) {
                continue
            }
            if (!indicatorLocationAvailable) continue
            val predicted =
                markerMotionController.predict(
                    nowElapsedMs = nowElapsedMs,
                    serviceFreshnessMaxAgeMs = indicatorFixFreshMaxAgeMs,
                    watchGpsDegraded = indicatorWatchGpsDegraded,
                ) ?: continue
            val marker = locationMarker ?: continue

            lastRenderedMarkerLatLong?.let { last ->
                val dLat = predicted.latitude - last.latitude
                val dLon = predicted.longitude - last.longitude
                if ((dLat * dLat + dLon * dLon) < MARKER_UPDATE_EPSILON_DEG2) continue
            }
            lastRenderedMarkerLatLong = predicted
            lastMarkerVisualUpdateAtElapsedMs = nowElapsedMs

            marker.latLong = predicted
            if (
                shouldCenterOnRenderedMarker(
                    shouldFollowPosition = latestShouldFollowPosition.value,
                    target = predicted,
                    currentCenter = mapView.model.mapViewPosition.center,
                )
            ) {
                mapView.setCenter(predicted)
            }
            mapView.requestLayerRedrawSafely()
        }
    }

    LaunchedEffect(
        shouldTrackLocation,
        screenState,
        locationViewModel,
    ) {
        if (!shouldTrackLocation) return@LaunchedEffect
        if (screenState.isNonInteractive) return@LaunchedEffect

        while (isActive) {
            delay(INTERACTIVE_STALE_REFRESH_CHECK_MS)
            val nowElapsedMs = android.os.SystemClock.elapsedRealtime()
            val refreshDecision =
                resolveInteractiveStaleRefreshDecision(
                    input =
                        InteractiveStaleRefreshInput(
                            shouldTrackLocation = shouldTrackLocation,
                            screenState = screenState,
                            holdMarkerUntilFreshFix = holdMarkerUntilFreshFix,
                            postWakePredictionHoldActive =
                                isPostWakePredictionHoldActive(
                                    nowElapsedMs = nowElapsedMs,
                                    holdUntilElapsedMs = postWakePredictionHoldUntilElapsedMs,
                                ),
                            activeWakeSessionId = activeWakeSessionId,
                            lastFixAtElapsedMs = indicatorFixAtElapsedMs,
                            lastFixFreshMaxAgeMs = indicatorFixFreshMaxAgeMs,
                            lastVisualUpdateAtElapsedMs = lastMarkerVisualUpdateAtElapsedMs,
                            lastRefreshRequestAtElapsedMs = lastInteractiveStaleRefreshAtElapsedMs,
                            nowElapsedMs = nowElapsedMs,
                        ),
                )
            if (!refreshDecision.shouldRequest) continue

            lastInteractiveStaleRefreshAtElapsedMs = nowElapsedMs
            logInteractiveStaleRefresh(
                nowElapsedMs = nowElapsedMs,
                fixAgeMs = refreshDecision.fixAgeMs ?: Long.MAX_VALUE,
                visualAgeMs = refreshDecision.visualAgeMs ?: Long.MAX_VALUE,
            )
            locationViewModel.requestImmediateLocation(source = UI_INTERACTIVE_STALE_REFRESH_SOURCE)
        }
    }

    return NavigateLocationUiState(
        locationMarker = if (suppressLocationMarker) null else locationMarker,
        gpsIndicatorState = gpsIndicatorState,
        showGpsIndicatorUnpinned = showGpsIndicatorUnpinned,
        watchGpsDegradedWarning = watchGpsDegradedWarning,
    )
}

private const val MARKER_UPDATE_EPSILON_DEG2 = 1e-11
private const val WAKE_REACQUIRE_TIMEOUT_MS = 6_000L
private const val WAKE_REACQUIRE_COOLDOWN_MS = 60_000L
private const val POST_WAKE_PREDICTION_GRACE_MS = 700L
private const val INTERACTIVE_STALE_REFRESH_CHECK_MS = 1_000L
private const val INTERACTIVE_STALE_REFRESH_MIN_FIX_AGE_MS = 6_000L
private const val INTERACTIVE_STALE_REFRESH_MIN_VISUAL_AGE_MS = 5_000L
private const val INTERACTIVE_STALE_REFRESH_COOLDOWN_MS = 12_000L
private const val WAKE_REACQUIRE_SNAP_MAX_ACCURACY_M = 35f
private const val WAKE_ANCHOR_MAX_ACCURACY_M = 35f
private const val TRACKING_SESSION_FIX_MAX_SKEW_MS = 400L
private const val NAV_MARKER_TELEMETRY_TAG = "MarkerMotion"
private const val UI_INTERACTIVE_STALE_REFRESH_SOURCE = "ui_interactive_stale_refresh"

private fun removeAllRotatableMarkers(mapView: MapView) {
    val layers = mapView.layerManager.layers
    for (i in layers.size() - 1 downTo 0) {
        val layer = layers[i]
        if (layer is RotatableMarker) {
            layers.remove(layer)
        }
    }
}

internal fun isWakeReacquireCooldownActive(
    nowElapsedMs: Long,
    lastStartedAtElapsedMs: Long,
    cooldownMs: Long,
): Boolean {
    if (lastStartedAtElapsedMs == Long.MIN_VALUE) return false
    val elapsedMs = (nowElapsedMs - lastStartedAtElapsedMs).coerceAtLeast(0L)
    return elapsedMs < cooldownMs.coerceAtLeast(0L)
}

internal fun toValidLatLongOrNull(
    latitude: Double,
    longitude: Double,
): LatLong? {
    if (!LocationFixPolicy.hasValidCoordinates(latitude = latitude, longitude = longitude)) {
        return null
    }
    return LatLong(latitude, longitude)
}

internal fun shouldCenterOnRenderedMarker(
    shouldFollowPosition: Boolean,
    target: LatLong,
    currentCenter: LatLong?,
): Boolean {
    if (!shouldFollowPosition) return false
    val center = currentCenter ?: return true
    val dLat = target.latitude - center.latitude
    val dLon = target.longitude - center.longitude
    return (dLat * dLat + dLon * dLon) >= MARKER_UPDATE_EPSILON_DEG2
}

internal fun resolveWakeAnchorSeedOrNull(
    location: android.location.Location?,
    receivedAtElapsedMs: Long,
    nowWallClockMs: Long,
    maxAgeMs: Long,
    maxAccuracyM: Float,
): WakeAnchorSeed? {
    if (location == null) return null
    if (!location.accuracy.isFinite() || location.accuracy > maxAccuracyM) return null
    val latLong = toValidLatLongOrNull(location.latitude, location.longitude) ?: return null
    val fixElapsedMs =
        resolveLocationFixElapsedRealtimeMs(
            elapsedRealtimeNanos = location.elapsedRealtimeNanos,
            utcTimeMs = location.time,
            receivedAtElapsedMs = receivedAtElapsedMs,
            nowWallClockMs = nowWallClockMs,
        )
    return resolveWakeAnchorSeedFromFixOrNull(
        latLong = latLong,
        fixElapsedMs = fixElapsedMs,
        receivedAtElapsedMs = receivedAtElapsedMs,
        accuracyM = location.accuracy,
        maxAgeMs = maxAgeMs,
        maxAccuracyM = maxAccuracyM,
        speedMps = if (location.hasSpeed() && location.speed.isFinite()) location.speed else 0f,
        bearingDeg = if (location.hasBearing()) location.bearing else null,
    )
}

internal fun computeWakeAnchorMaxAgeMs(expectedGpsIntervalMs: Long): Long = resolveLocationTimingProfile(expectedGpsIntervalMs).wakeAnchorMaxAgeMs

internal fun resolveStartupFreshFixMaxAgeMs(
    expectedGpsIntervalMs: Long,
    serviceFreshMaxAgeMs: Long,
): Long {
    val timingProfile = resolveLocationTimingProfile(expectedGpsIntervalMs)
    val effectiveServiceFreshMaxAgeMs =
        serviceFreshMaxAgeMs
            .takeIf { it > 0L }
            ?: timingProfile.strictFreshFixMaxAgeMs
    return maxOf(timingProfile.strictFreshFixMaxAgeMs, effectiveServiceFreshMaxAgeMs)
}

internal fun computeWakeReacquireSnapMaxAgeMs(expectedGpsIntervalMs: Long): Long = resolveLocationTimingProfile(expectedGpsIntervalMs).markerPredictionFreshnessMaxAgeMs

internal fun computeWakeReacquireReleaseMaxAgeMs(expectedGpsIntervalMs: Long): Long = resolveLocationTimingProfile(expectedGpsIntervalMs).strictFreshFixMaxAgeMs

internal fun shouldReleaseWakeReacquireHold(
    holdMarkerUntilFreshFix: Boolean,
    fixFromCurrentTrackingSession: Boolean,
    wakeSnapEligible: Boolean,
    wakeReleaseEligible: Boolean,
    holdTimedOut: Boolean,
): Boolean {
    if (!holdMarkerUntilFreshFix) return false
    if (fixFromCurrentTrackingSession && wakeSnapEligible) return true
    if (holdTimedOut && wakeReleaseEligible) return true
    return false
}

internal fun shouldStartInteractiveWakeSession(
    wasInteractiveTrackingActive: Boolean,
    shouldTrackLocation: Boolean,
    screenState: LocationScreenState,
): Boolean = shouldTrackLocation && !screenState.isNonInteractive && !wasInteractiveTrackingActive

internal fun isPostWakePredictionHoldActive(
    nowElapsedMs: Long,
    holdUntilElapsedMs: Long,
): Boolean = holdUntilElapsedMs > 0L && nowElapsedMs < holdUntilElapsedMs

internal fun resolveAcceptedFixGapMs(
    previousFixElapsedMs: Long,
    currentFixElapsedMs: Long,
): Long {
    if (previousFixElapsedMs <= 0L || currentFixElapsedMs <= 0L) return Long.MAX_VALUE
    return (currentFixElapsedMs - previousFixElapsedMs).coerceAtLeast(0L)
}

internal fun shouldBypassCorrectionClamp(
    releaseFromWakeHold: Boolean,
    previousAcceptedFixGapMs: Long,
    expectedGpsIntervalMs: Long,
): Boolean {
    if (releaseFromWakeHold) return true
    return previousAcceptedFixGapMs >= computeCorrectionClampBypassGapMs(expectedGpsIntervalMs)
}

internal fun computeCorrectionClampBypassGapMs(expectedGpsIntervalMs: Long): Long = resolveLocationTimingProfile(expectedGpsIntervalMs).correctionStaleGapMs

internal data class InteractiveStaleRefreshDecision(
    val shouldRequest: Boolean,
    val fixAgeMs: Long? = null,
    val visualAgeMs: Long? = null,
)

internal data class InteractiveStaleRefreshInput(
    val shouldTrackLocation: Boolean,
    val screenState: LocationScreenState,
    val holdMarkerUntilFreshFix: Boolean,
    val postWakePredictionHoldActive: Boolean,
    val activeWakeSessionId: Long,
    val lastFixAtElapsedMs: Long,
    val lastFixFreshMaxAgeMs: Long,
    val lastVisualUpdateAtElapsedMs: Long,
    val lastRefreshRequestAtElapsedMs: Long,
    val nowElapsedMs: Long,
)

internal fun resolveInteractiveStaleRefreshDecision(
    input: InteractiveStaleRefreshInput,
): InteractiveStaleRefreshDecision {
    val fixAgeMs =
        if (input.lastFixAtElapsedMs > 0L) {
            (input.nowElapsedMs - input.lastFixAtElapsedMs).coerceAtLeast(0L)
        } else {
            Long.MAX_VALUE
        }
    val freshnessThresholdMs =
        maxOf(
            INTERACTIVE_STALE_REFRESH_MIN_FIX_AGE_MS,
            input.lastFixFreshMaxAgeMs.takeIf { it > 0L } ?: 0L,
        )
    val visualAgeMs =
        if (input.lastVisualUpdateAtElapsedMs > 0L) {
            (input.nowElapsedMs - input.lastVisualUpdateAtElapsedMs).coerceAtLeast(0L)
        } else {
            fixAgeMs
        }
    val interactiveTrackingActive =
        input.shouldTrackLocation && !input.screenState.isNonInteractive
    val wakeRecoveryActive =
        input.holdMarkerUntilFreshFix ||
            input.postWakePredictionHoldActive ||
            input.activeWakeSessionId > 0L
    val hasKnownFix = fixAgeMs != Long.MAX_VALUE
    val fixIsStale = hasKnownFix && fixAgeMs >= freshnessThresholdMs
    val visualIsStale = visualAgeMs >= INTERACTIVE_STALE_REFRESH_MIN_VISUAL_AGE_MS
    val refreshCooldownActive =
        input.lastRefreshRequestAtElapsedMs != Long.MIN_VALUE &&
            (input.nowElapsedMs - input.lastRefreshRequestAtElapsedMs).coerceAtLeast(0L) <
            INTERACTIVE_STALE_REFRESH_COOLDOWN_MS
    return InteractiveStaleRefreshDecision(
        shouldRequest =
            interactiveTrackingActive &&
                !wakeRecoveryActive &&
                hasKnownFix &&
                fixIsStale &&
                visualIsStale &&
                !refreshCooldownActive,
        fixAgeMs = fixAgeMs.takeUnless { it == Long.MAX_VALUE },
        visualAgeMs = visualAgeMs.takeUnless { it == Long.MAX_VALUE },
    )
}

internal fun resolveWakeAnchorSeedFromFixOrNull(
    latLong: LatLong?,
    fixElapsedMs: Long,
    receivedAtElapsedMs: Long,
    accuracyM: Float,
    maxAgeMs: Long,
    maxAccuracyM: Float,
    speedMps: Float = 0f,
    bearingDeg: Float? = null,
): WakeAnchorSeed? {
    if (latLong == null) return null
    if (fixElapsedMs <= 0L) return null
    if (!accuracyM.isFinite() || accuracyM > maxAccuracyM) return null
    val ageMs = (receivedAtElapsedMs - fixElapsedMs).coerceAtLeast(0L)
    if (ageMs > maxAgeMs) return null
    return WakeAnchorSeed(
        latLong = latLong,
        fixElapsedMs = fixElapsedMs,
        accuracyM = accuracyM,
        speedMps = speedMps,
        bearingDeg = bearingDeg,
    )
}

internal fun resolveLocationFixElapsedRealtimeMs(
    elapsedRealtimeNanos: Long,
    utcTimeMs: Long,
    receivedAtElapsedMs: Long,
    nowWallClockMs: Long,
): Long {
    if (elapsedRealtimeNanos > 0L) {
        return (elapsedRealtimeNanos / 1_000_000L)
            .coerceIn(0L, receivedAtElapsedMs)
    }
    if (utcTimeMs > 0L) {
        val ageMs = (nowWallClockMs - utcTimeMs).coerceAtLeast(0L)
        return (receivedAtElapsedMs - ageMs).coerceAtLeast(0L)
    }
    // Unknown timestamp: keep indicator conservative instead of treating this fix as fresh.
    return 0L
}

internal fun resolveGpsIndicatorState(
    isLocationAvailable: Boolean,
    unavailableSinceElapsedMs: Long,
    lastFixAtElapsedMs: Long,
    accuracyM: Float,
    nowElapsedMs: Long,
    staleThresholdMs: Long,
): GpsFixIndicatorState {
    val ageMs =
        if (lastFixAtElapsedMs > 0L) {
            (nowElapsedMs - lastFixAtElapsedMs).coerceAtLeast(0L)
        } else {
            Long.MAX_VALUE
        }
    val hasFreshUsableFix =
        lastFixAtElapsedMs > 0L &&
            ageMs <= staleThresholdMs &&
            accuracyM.isFinite()

    if (hasFreshUsableFix) {
        return when {
            accuracyM <= GOOD_FIX_ACCURACY_THRESHOLD_M -> GpsFixIndicatorState.GOOD
            else -> GpsFixIndicatorState.POOR
        }
    }

    if (!isLocationAvailable) {
        if (unavailableSinceElapsedMs <= 0L) return GpsFixIndicatorState.SEARCHING
        val outageConfirmWindowMs = computeUnavailableConfirmWindowMs(staleThresholdMs)
        val unavailableForMs = (nowElapsedMs - unavailableSinceElapsedMs).coerceAtLeast(0L)
        return if (unavailableForMs >= outageConfirmWindowMs) {
            GpsFixIndicatorState.UNAVAILABLE
        } else {
            GpsFixIndicatorState.SEARCHING
        }
    }

    if (lastFixAtElapsedMs <= 0L) return GpsFixIndicatorState.SEARCHING
    if (ageMs > staleThresholdMs) return GpsFixIndicatorState.SEARCHING
    if (!accuracyM.isFinite()) return GpsFixIndicatorState.SEARCHING

    return GpsFixIndicatorState.SEARCHING
}

internal fun resolveGpsIndicatorDisplayState(
    rawState: GpsFixIndicatorState,
): GpsFixIndicatorState = rawState

internal fun shouldShowGpsIndicatorUnpinned(
    gpsIndicatorState: GpsFixIndicatorState,
    watchGpsDegradedWarning: Boolean,
): Boolean =
    gpsIndicatorState == GpsFixIndicatorState.SEARCHING ||
        gpsIndicatorState == GpsFixIndicatorState.UNAVAILABLE ||
        watchGpsDegradedWarning

private fun computeGpsFixStaleThresholdMs(expectedGpsIntervalMs: Long): Long = resolveLocationTimingProfile(expectedGpsIntervalMs).indicatorStaleThresholdMs

private fun computeUnavailableConfirmWindowMs(staleThresholdMs: Long): Long {
    val safeStaleThresholdMs = staleThresholdMs.coerceAtLeast(1_000L)
    return (safeStaleThresholdMs * 2L).coerceIn(20_000L, 60_000L)
}

internal fun computeMarkerPredictionFreshnessMaxAgeMs(expectedGpsIntervalMs: Long): Long = resolveLocationTimingProfile(expectedGpsIntervalMs).markerPredictionFreshnessMaxAgeMs

private fun logWakeSessionEvent(
    stage: String,
    sessionId: Long,
    nowElapsedMs: Long,
    reason: String? = null,
    fixAgeMs: Long? = null,
) {
    DebugTelemetry.log(
        NAV_MARKER_TELEMETRY_TAG,
        buildString {
            append("wakeSession stage=$stage")
            append(" id=$sessionId")
            append(" at=${nowElapsedMs}ms")
            reason?.let { append(" reason=$it") }
            fixAgeMs?.let { append(" fixAge=${it}ms") }
        },
    )
}

private fun logInteractiveStaleRefresh(
    nowElapsedMs: Long,
    fixAgeMs: Long,
    visualAgeMs: Long,
) {
    DebugTelemetry.log(
        NAV_MARKER_TELEMETRY_TAG,
        buildString {
            append("refresh reason=stale_screen")
            append(" at=${nowElapsedMs}ms")
            append(" fixAge=${fixAgeMs}ms")
            append(" visualAge=${visualAgeMs}ms")
        },
    )
}

private const val GOOD_FIX_ACCURACY_THRESHOLD_M = 12f
