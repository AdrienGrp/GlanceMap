package com.glancemap.glancemapwearos.presentation.features.navigate.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.glancemap.glancemapwearos.core.service.location.config.FINE_FIX_MAX_ACCURACY_M
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.core.service.location.model.resolveLocationTimingProfile
import com.glancemap.glancemapwearos.core.service.location.model.LocationScreenState
import com.glancemap.glancemapwearos.core.service.location.model.isNonInteractive
import com.glancemap.glancemapwearos.domain.sensors.COMPASS_TELEMETRY_TAG
import com.glancemap.glancemapwearos.domain.sensors.CompassViewModel
import com.glancemap.glancemapwearos.domain.sensors.format
import com.glancemap.glancemapwearos.core.service.location.policy.LocationFixPolicy
import com.glancemap.glancemapwearos.presentation.features.maps.RotatableMarker
import com.glancemap.glancemapwearos.presentation.features.navigate.GpsFixIndicatorState
import com.glancemap.glancemapwearos.presentation.features.navigate.LocationViewModel
import com.glancemap.glancemapwearos.presentation.features.navigate.NavigateViewModel
import com.glancemap.glancemapwearos.presentation.features.navigate.NavMode
import com.glancemap.glancemapwearos.presentation.features.navigate.CompassMarkerQuality
import com.glancemap.glancemapwearos.presentation.features.navigate.UI_WAKE_REACQUIRE_TIMEOUT_SOURCE
import com.glancemap.glancemapwearos.presentation.features.navigate.motion.BetweenFixMotionInputs
import com.glancemap.glancemapwearos.presentation.features.navigate.motion.BetweenFixMotionPolicy
import com.glancemap.glancemapwearos.presentation.features.navigate.motion.CompassPredictionOverrideEvaluation
import com.glancemap.glancemapwearos.presentation.features.navigate.motion.LowConfidenceCompassPredictionOverrideTracker
import com.glancemap.glancemapwearos.presentation.features.navigate.motion.LocationFusionEngine
import com.glancemap.glancemapwearos.presentation.features.navigate.motion.MarkerStabilizer
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
    val watchGpsDegradedWarning: Boolean
)

internal data class WakeAnchorSeed(
    val latLong: LatLong,
    val fixElapsedMs: Long,
    val accuracyM: Float,
    val speedMps: Float,
    val bearingDeg: Float?
)

@Composable
internal fun rememberNavigateLocationUiState(
    mapView: MapView,
    locationViewModel: LocationViewModel,
    compassViewModel: CompassViewModel,
    navigateViewModel: NavigateViewModel,
    navMode: NavMode,
    shouldTrackLocation: Boolean,
    shouldFollowPosition: Boolean,
    screenState: LocationScreenState,
    compassQuality: CompassMarkerQuality,
    expectedGpsIntervalMs: Long,
    navigationMarkerBitmap: AndroidBitmap,
    suppressLocationMarker: Boolean
): NavigateLocationUiState {
    val timingProfile = remember(expectedGpsIntervalMs) {
        resolveLocationTimingProfile(expectedGpsIntervalMs)
    }
    val locationFusionEngine = remember(timingProfile.intervalMs) {
        LocationFusionEngine(
            staleGpsThresholdMs = timingProfile.fusionStaleThresholdMs,
            predictionHorizonMs = timingProfile.fusionPredictionHorizonMs,
            correctionStaleGapMs = timingProfile.correctionStaleGapMs,
            minBlendDurationMs = MIN_GPS_FIX_BLEND_MS,
            maxBlendDurationMs = MAX_GPS_FIX_BLEND_MS
        )
    }
    val markerStabilizer = remember(timingProfile.intervalMs) {
        MarkerStabilizer(
            maxAcceptedAccuracyM = FINE_FIX_MAX_ACCURACY_M,
            maxAcceptedFixAgeMs = timingProfile.stabilizerMaxAcceptedFixAgeMs,
            minOutlierWindowMs = 1_000L,
            maxOutlierImpliedSpeedMps = 14f,
            maxPredictionWithoutGpsMs = timingProfile.stabilizerMaxPredictionWithoutGpsMs,
            wakeCorrectionMinGapMs = timingProfile.wakeCorrectionMinGapMs
        )
    }

    val latestShouldFollowPosition = rememberUpdatedState(shouldFollowPosition)
    val latestSuppressLocationMarker = rememberUpdatedState(suppressLocationMarker)
    val latestCompassQuality = rememberUpdatedState(compassQuality)
    val lowConfidenceCompassPredictionOverrideTracker = remember {
        LowConfidenceCompassPredictionOverrideTracker()
    }

    var locationMarker by remember { mutableStateOf<RotatableMarker?>(null) }
    var lastRenderedMarkerLatLong by remember { mutableStateOf<LatLong?>(null) }
    var indicatorFixAtElapsedMs by remember { mutableLongStateOf(0L) }
    var indicatorFixAgeMs by remember { mutableLongStateOf(Long.MAX_VALUE) }
    var indicatorFixAccuracyM by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
    var indicatorFixFreshMaxAgeMs by remember { mutableLongStateOf(0L) }
    var indicatorLocationAvailable by remember { mutableStateOf(true) }
    var indicatorUnavailableSinceElapsedMs by remember { mutableLongStateOf(0L) }
    var indicatorWatchGpsDegraded by remember { mutableStateOf(false) }
    var holdMarkerUntilFreshFix by remember { mutableStateOf(false) }
    var holdMarkerStartedAtElapsedMs by remember { mutableLongStateOf(0L) }
    var trackingActivatedAtElapsedMs by remember { mutableLongStateOf(0L) }
    var hasFirstFreshFix by remember { mutableStateOf(false) }
    var postWakePredictionHoldFixesRemaining by remember { mutableIntStateOf(0) }
    var lastWakeReacquireStartedAtElapsedMs by remember { mutableLongStateOf(Long.MIN_VALUE) }
    var wakeAnchorSeeded by remember { mutableStateOf(false) }
    var allowLowQualityCompassPrediction by remember { mutableStateOf(false) }

    var gpsIndicatorClockMs by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    val latestAllowLowQualityCompassPrediction =
        rememberUpdatedState(allowLowQualityCompassPrediction)

    LaunchedEffect(shouldTrackLocation, locationFusionEngine) {
        if (shouldTrackLocation) return@LaunchedEffect
        // Always clear motion memory when tracking stops to avoid stale-gap carry-over.
        holdMarkerUntilFreshFix = false
        holdMarkerStartedAtElapsedMs = 0L
        trackingActivatedAtElapsedMs = 0L
        hasFirstFreshFix = false
        postWakePredictionHoldFixesRemaining = 0
        wakeAnchorSeeded = false
        allowLowQualityCompassPrediction = resetCompassPredictionOverrideState(
            wasActive = allowLowQualityCompassPrediction,
            tracker = lowConfidenceCompassPredictionOverrideTracker,
            reason = "tracking_disabled"
        )
        locationFusionEngine.reset()
        markerStabilizer.reset()
    }

    // On every tracking start, require one post-start fresh fix before rendering.
    LaunchedEffect(screenState, shouldTrackLocation, locationFusionEngine) {
        if (screenState.isNonInteractive || !shouldTrackLocation) return@LaunchedEffect
        val nowElapsedMs = android.os.SystemClock.elapsedRealtime()
        trackingActivatedAtElapsedMs = nowElapsedMs
        hasFirstFreshFix = false
        postWakePredictionHoldFixesRemaining = 0
        wakeAnchorSeeded = false
        allowLowQualityCompassPrediction = resetCompassPredictionOverrideState(
            wasActive = allowLowQualityCompassPrediction,
            tracker = lowConfidenceCompassPredictionOverrideTracker,
            reason = "tracking_started"
        )
        locationFusionEngine.reset()
        markerStabilizer.reset()
        resolveWakeAnchorSeedOrNull(
            location = locationViewModel.currentLocation.value,
            receivedAtElapsedMs = nowElapsedMs,
            nowWallClockMs = System.currentTimeMillis(),
            maxAgeMs = computeWakeAnchorMaxAgeMs(expectedGpsIntervalMs),
            maxAccuracyM = WAKE_ANCHOR_MAX_ACCURACY_M
        )?.let { anchor ->
            locationFusionEngine.seedAnchor(
                latLong = anchor.latLong,
                fixElapsedMs = anchor.fixElapsedMs,
                speedMps = anchor.speedMps,
                accuracyM = anchor.accuracyM,
                bearingDeg = anchor.bearingDeg,
                nowElapsedMs = nowElapsedMs
            )
            markerStabilizer.seedAnchor(
                latLong = anchor.latLong,
                fixElapsedMs = anchor.fixElapsedMs,
                accuracyM = anchor.accuracyM,
                speedMps = anchor.speedMps
            )
            lastRenderedMarkerLatLong = anchor.latLong
            wakeAnchorSeeded = true
        }
        val wakeReacquireInCooldown = isWakeReacquireCooldownActive(
            nowElapsedMs = nowElapsedMs,
            lastStartedAtElapsedMs = lastWakeReacquireStartedAtElapsedMs,
            cooldownMs = WAKE_REACQUIRE_COOLDOWN_MS
        )
        if (wakeReacquireInCooldown) {
            holdMarkerUntilFreshFix = false
            holdMarkerStartedAtElapsedMs = 0L
            return@LaunchedEffect
        }
        lastWakeReacquireStartedAtElapsedMs = nowElapsedMs
        holdMarkerUntilFreshFix = true
        holdMarkerStartedAtElapsedMs = nowElapsedMs
        locationFusionEngine.requireFreshFixForPrediction()
        locationViewModel.requestImmediateLocation(source = "ui_startup_fresh_fix")
    }

    LaunchedEffect(holdMarkerUntilFreshFix, shouldTrackLocation, screenState, locationViewModel) {
        if (!holdMarkerUntilFreshFix || !shouldTrackLocation || screenState.isNonInteractive) {
            return@LaunchedEffect
        }
        delay(WAKE_REACQUIRE_TIMEOUT_MS)
        if (holdMarkerUntilFreshFix && shouldTrackLocation && !screenState.isNonInteractive) {
            locationViewModel.requestImmediateLocation(
                source = UI_WAKE_REACQUIRE_TIMEOUT_SOURCE
            )
        }
    }

    LaunchedEffect(compassViewModel, locationFusionEngine) {
        var lastHeading = Float.NaN
        var lastAccuracy = Int.MIN_VALUE
        var lastMagneticInterference: Boolean? = null
        compassViewModel.renderState.collect { state ->
            val heading = state.headingDeg
            if (!lastHeading.isFinite() || heading != lastHeading) {
                locationFusionEngine.onHeading(
                    headingDeg = heading,
                    nowElapsedMs = android.os.SystemClock.elapsedRealtime()
                )
                lastHeading = heading
            }
            if (state.accuracy != lastAccuracy) {
                locationFusionEngine.onHeadingAccuracy(state.accuracy)
                lastAccuracy = state.accuracy
            }
            if (lastMagneticInterference == null || state.magneticInterference != lastMagneticInterference) {
                locationFusionEngine.onMagneticInterference(state.magneticInterference)
                lastMagneticInterference = state.magneticInterference
            }
            val overrideEvaluation =
                lowConfidenceCompassPredictionOverrideTracker.update(
                    renderState = state,
                    nowElapsedMs = android.os.SystemClock.elapsedRealtime()
                )
            if (allowLowQualityCompassPrediction != overrideEvaluation.active) {
                logCompassPredictionOverrideTransition(overrideEvaluation)
            }
            allowLowQualityCompassPrediction = overrideEvaluation.active
        }
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
            indicatorFixAgeMs = signal.lastFixAgeMs
            indicatorFixAccuracyM = signal.lastFixAccuracyM
            indicatorFixFreshMaxAgeMs = signal.lastFixFreshMaxAgeMs
            indicatorLocationAvailable = signal.isLocationAvailable
            indicatorUnavailableSinceElapsedMs = signal.unavailableSinceElapsedMs
            indicatorWatchGpsDegraded = signal.watchGpsOnlyActive && signal.watchGpsDegraded
        }
    }

    val markerPredictionFreshnessMaxAgeMs = timingProfile.markerPredictionFreshnessMaxAgeMs
    val gpsStaleIndicatorThresholdMs = timingProfile.indicatorStaleThresholdMs
    val gpsIndicatorRawState = resolveGpsIndicatorState(
        isLocationAvailable = indicatorLocationAvailable,
        unavailableSinceElapsedMs = indicatorUnavailableSinceElapsedMs,
        lastFixAtElapsedMs = indicatorFixAtElapsedMs,
        accuracyM = indicatorFixAccuracyM,
        nowElapsedMs = gpsIndicatorClockMs,
        staleThresholdMs = gpsStaleIndicatorThresholdMs
    )
    val gpsIndicatorDisplayRawState = resolveGpsIndicatorDisplayState(
        rawState = gpsIndicatorRawState
    )
    val gpsIndicatorState = gpsIndicatorDisplayRawState
    val watchGpsDegradedWarning = shouldTrackLocation && indicatorWatchGpsDegraded
    val showGpsIndicatorUnpinned = shouldShowGpsIndicatorUnpinned(
        gpsIndicatorState = gpsIndicatorState,
        watchGpsDegradedWarning = watchGpsDegradedWarning
    )

    LaunchedEffect(mapView, navigationMarkerBitmap) {
        if (latestSuppressLocationMarker.value) {
            locationMarker?.let { marker -> mapView.layerManager.layers.remove(marker) }
            locationMarker = null
            lastRenderedMarkerLatLong = null
            return@LaunchedEffect
        }
        val currentMarker = locationMarker
        if (currentMarker == null) {
            val fallbackLatLong = lastRenderedMarkerLatLong
                ?: locationViewModel.currentLocation.value?.let { loc ->
                    toValidLatLongOrNull(loc.latitude, loc.longitude)
                }
                ?: return@LaunchedEffect
            locationMarker = RotatableMarker(
                fallbackLatLong,
                navigationMarkerBitmap,
                -navigationMarkerBitmap.width / 2,
                -navigationMarkerBitmap.height / 2
            ).also { marker ->
                mapView.layerManager.layers.add(marker)
                if (marker.isVisible) {
                    marker.requestRedraw()
                }
            }
            return@LaunchedEffect
        }

        val latLong = currentMarker.latLong ?: return@LaunchedEffect
        val heading = currentMarker.heading
        val isVisible = currentMarker.isVisible
        mapView.layerManager.layers.remove(currentMarker)
        locationMarker = RotatableMarker(
            latLong,
            navigationMarkerBitmap,
            -navigationMarkerBitmap.width / 2,
            -navigationMarkerBitmap.height / 2
        ).also { marker ->
            marker.heading = heading
            marker.isVisible = isVisible
            mapView.layerManager.layers.add(marker)
            if (marker.isVisible) {
                marker.requestRedraw()
            }
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
        postWakePredictionHoldFixesRemaining = 0
        wakeAnchorSeeded = false
        allowLowQualityCompassPrediction = resetCompassPredictionOverrideState(
            wasActive = allowLowQualityCompassPrediction,
            tracker = lowConfidenceCompassPredictionOverrideTracker,
            reason = "marker_suppressed"
        )
        locationFusionEngine.reset()
        markerStabilizer.reset()
    }

    // Restores old working behavior: center only when shouldFollowPosition is true.
    LaunchedEffect(locationViewModel, mapView) {
        locationViewModel.currentLocation
            .filterNotNull()
            .collect { loc ->
                if (latestSuppressLocationMarker.value) return@collect

                val receivedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
                val fixElapsedMs = resolveLocationFixElapsedRealtimeMs(
                    elapsedRealtimeNanos = loc.elapsedRealtimeNanos,
                    utcTimeMs = loc.time,
                    receivedAtElapsedMs = receivedAtElapsedMs,
                    nowWallClockMs = System.currentTimeMillis()
                )
                val startupFreshMaxAgeMs = resolveStartupFreshFixMaxAgeMs(
                    expectedGpsIntervalMs = expectedGpsIntervalMs,
                    serviceFreshMaxAgeMs = indicatorFixFreshMaxAgeMs
                )
                val localFixAgeMs = if (fixElapsedMs > 0L) {
                    (receivedAtElapsedMs - fixElapsedMs).coerceAtLeast(0L)
                } else {
                    Long.MAX_VALUE
                }
                val localFixFresh = localFixAgeMs <= startupFreshMaxAgeMs &&
                    loc.accuracy.isFinite() &&
                    loc.accuracy <= STARTUP_FRESH_FIX_MAX_ACCURACY_M
                val wakeSnapEligible =
                    localFixAgeMs <= computeWakeReacquireSnapMaxAgeMs(expectedGpsIntervalMs) &&
                    loc.accuracy.isFinite() &&
                    loc.accuracy <= WAKE_REACQUIRE_SNAP_MAX_ACCURACY_M
                val wakeReleaseEligible =
                    localFixAgeMs <= computeWakeReacquireReleaseMaxAgeMs(expectedGpsIntervalMs)
                val fixFromCurrentTrackingSession = trackingActivatedAtElapsedMs <= 0L ||
                    (fixElapsedMs > 0L &&
                        fixElapsedMs + TRACKING_SESSION_FIX_MAX_SKEW_MS >= trackingActivatedAtElapsedMs)
                val holdTimedOut = holdMarkerStartedAtElapsedMs > 0L &&
                    (receivedAtElapsedMs - holdMarkerStartedAtElapsedMs)
                        .coerceAtLeast(0L) >= WAKE_REACQUIRE_TIMEOUT_MS
                val releaseFromWakeHold = shouldReleaseWakeReacquireHold(
                    holdMarkerUntilFreshFix = holdMarkerUntilFreshFix,
                    fixFromCurrentTrackingSession = fixFromCurrentTrackingSession,
                    wakeSnapEligible = wakeSnapEligible,
                    wakeReleaseEligible = wakeReleaseEligible,
                    holdTimedOut = holdTimedOut
                )
                if (holdMarkerUntilFreshFix && !releaseFromWakeHold) {
                    return@collect
                }
                val keepWakeAnchorForCorrection = releaseFromWakeHold && wakeAnchorSeeded
                if (releaseFromWakeHold) {
                    holdMarkerUntilFreshFix = false
                    holdMarkerStartedAtElapsedMs = 0L
                    hasFirstFreshFix = true
                    postWakePredictionHoldFixesRemaining = 1
                    if (keepWakeAnchorForCorrection) {
                        wakeAnchorSeeded = false
                    } else {
                        // First accepted post-wake fix should anchor directly with no legacy blending.
                        locationFusionEngine.reset()
                        markerStabilizer.reset()
                    }
                }
                if (localFixFresh) {
                    hasFirstFreshFix = true
                }

                compassViewModel.updateDeclinationFromLocation(loc)

                val ll = toValidLatLongOrNull(loc.latitude, loc.longitude) ?: return@collect
                navigateViewModel.onLocationUpdate(ll)
                val motionSpeedMps = if (loc.hasSpeed() && loc.speed.isFinite()) {
                    loc.speed
                } else {
                    0f
                }

                val fusionLatLong = locationFusionEngine.onGpsFix(
                    latLong = ll,
                    speedMps = motionSpeedMps,
                    accuracyM = loc.accuracy,
                    bearingDeg = if (loc.hasBearing()) loc.bearing else null,
                    nowElapsedMs = receivedAtElapsedMs
                )
                val stabilizedLatLong = markerStabilizer.onGpsFix(
                    candidate = fusionLatLong,
                    nowElapsedMs = receivedAtElapsedMs,
                    fixElapsedMs = fixElapsedMs,
                    accuracyM = loc.accuracy,
                    speedMps = motionSpeedMps
                )
                val displayLatLong = when {
                    keepWakeAnchorForCorrection -> stabilizedLatLong
                    releaseFromWakeHold -> ll
                    else -> stabilizedLatLong
                }
                if (postWakePredictionHoldFixesRemaining > 0 && !releaseFromWakeHold) {
                    postWakePredictionHoldFixesRemaining -= 1
                }

                if (locationMarker == null) {
                    removeAllRotatableMarkers(mapView)
                    locationMarker = RotatableMarker(
                        displayLatLong,
                        navigationMarkerBitmap,
                        -navigationMarkerBitmap.width / 2,
                        -navigationMarkerBitmap.height / 2
                    ).also { marker ->
                        mapView.layerManager.layers.add(marker)
                    }
                } else {
                    locationMarker?.latLong = displayLatLong
                }
                lastRenderedMarkerLatLong = displayLatLong

                if (
                    shouldCenterOnRenderedMarker(
                        shouldFollowPosition = latestShouldFollowPosition.value,
                        target = displayLatLong,
                        currentCenter = mapView.model.mapViewPosition.center
                    )
                ) {
                    mapView.setCenter(displayLatLong)
                }

                locationMarker?.let { marker ->
                    if (marker.isVisible) marker.requestRedraw()
                }
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
            postWakePredictionHoldFixesRemaining = 0
            wakeAnchorSeeded = false
            allowLowQualityCompassPrediction = resetCompassPredictionOverrideState(
                wasActive = allowLowQualityCompassPrediction,
                tracker = lowConfidenceCompassPredictionOverrideTracker,
                reason = "dispose"
            )
            locationFusionEngine.reset()
            markerStabilizer.reset()
        }
    }

    // Motion prediction loop.
    LaunchedEffect(
        shouldTrackLocation,
        screenState,
        mapView,
        locationFusionEngine
    ) {
        if (!shouldTrackLocation) return@LaunchedEffect
        if (screenState.isNonInteractive) return@LaunchedEffect

        while (isActive) {
            if (latestSuppressLocationMarker.value) {
                delay(80L)
                continue
            }
            delay(locationFusionEngine.suggestedPredictionTickMs())
            if (holdMarkerUntilFreshFix || postWakePredictionHoldFixesRemaining > 0) continue

            val nowElapsedMs = android.os.SystemClock.elapsedRealtime()
            val latestFixAgeMs = if (indicatorFixAtElapsedMs > 0L) {
                (nowElapsedMs - indicatorFixAtElapsedMs).coerceAtLeast(0L)
            } else {
                Long.MAX_VALUE
            }
            val allowBetweenFixPrediction = BetweenFixMotionPolicy.allowPrediction(
                inputs = BetweenFixMotionInputs(
                    compassQuality = latestCompassQuality.value,
                    gpsAccuracyM = indicatorFixAccuracyM,
                    gpsFixAgeMs = latestFixAgeMs,
                    gpsFreshMaxAgeMs = indicatorFixFreshMaxAgeMs,
                    predictionFreshnessMaxAgeMs = markerPredictionFreshnessMaxAgeMs,
                    watchGpsDegraded = indicatorWatchGpsDegraded,
                    allowLowQualityCompassPrediction =
                        latestAllowLowQualityCompassPrediction.value
                )
            )
            if (!allowBetweenFixPrediction) continue

            val predictedFusion = locationFusionEngine.predict(nowElapsedMs) ?: continue
            val predicted = markerStabilizer.onPrediction(
                candidate = predictedFusion,
                nowElapsedMs = nowElapsedMs
            )
            val marker = locationMarker ?: continue

            lastRenderedMarkerLatLong?.let { last ->
                val dLat = predicted.latitude - last.latitude
                val dLon = predicted.longitude - last.longitude
                if ((dLat * dLat + dLon * dLon) < MARKER_UPDATE_EPSILON_DEG2) continue
            }
            lastRenderedMarkerLatLong = predicted

            marker.latLong = predicted
            if (
                shouldCenterOnRenderedMarker(
                    shouldFollowPosition = latestShouldFollowPosition.value,
                    target = predicted,
                    currentCenter = mapView.model.mapViewPosition.center
                )
            ) {
                mapView.setCenter(predicted)
            }
            if (marker.isVisible) marker.requestRedraw()
        }
    }

    return NavigateLocationUiState(
        locationMarker = if (suppressLocationMarker) null else locationMarker,
        gpsIndicatorState = gpsIndicatorState,
        showGpsIndicatorUnpinned = showGpsIndicatorUnpinned,
        watchGpsDegradedWarning = watchGpsDegradedWarning
    )
}

private const val MIN_GPS_FIX_BLEND_MS = 250L
private const val MAX_GPS_FIX_BLEND_MS = 850L
private const val MARKER_UPDATE_EPSILON_DEG2 = 1e-11
private const val STARTUP_FRESH_FIX_MAX_ACCURACY_M = 35f
private const val WAKE_REACQUIRE_TIMEOUT_MS = 6_000L
private const val WAKE_REACQUIRE_COOLDOWN_MS = 60_000L
private const val WAKE_REACQUIRE_SNAP_MAX_ACCURACY_M = 35f
private const val WAKE_ANCHOR_MAX_ACCURACY_M = 35f
private const val TRACKING_SESSION_FIX_MAX_SKEW_MS = 400L
private fun resetCompassPredictionOverrideState(
    wasActive: Boolean,
    tracker: LowConfidenceCompassPredictionOverrideTracker,
    reason: String
): Boolean {
    val evaluation = tracker.reset(reason = reason)
    if (wasActive) {
        logCompassPredictionOverrideTransition(evaluation)
    }
    return evaluation.active
}

private fun logCompassPredictionOverrideTransition(
    evaluation: CompassPredictionOverrideEvaluation
) {
    if (!DebugTelemetry.isEnabled()) return
    DebugTelemetry.log(
        COMPASS_TELEMETRY_TAG,
        "prediction_override active=${evaluation.active} reason=${evaluation.reason} " +
            "headingErrorDeg=${evaluation.headingErrorDeg?.format(1) ?: "na"} " +
            "spreadDeg=${evaluation.spreadDeg?.format(1) ?: "na"} " +
            "sampleCount=${evaluation.sampleCount} spanMs=${evaluation.spanMs}"
    )
}

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
    cooldownMs: Long
): Boolean {
    if (lastStartedAtElapsedMs == Long.MIN_VALUE) return false
    val elapsedMs = (nowElapsedMs - lastStartedAtElapsedMs).coerceAtLeast(0L)
    return elapsedMs < cooldownMs.coerceAtLeast(0L)
}

internal fun toValidLatLongOrNull(latitude: Double, longitude: Double): LatLong? {
    if (!LocationFixPolicy.hasValidCoordinates(latitude = latitude, longitude = longitude)) {
        return null
    }
    return LatLong(latitude, longitude)
}

internal fun shouldCenterOnRenderedMarker(
    shouldFollowPosition: Boolean,
    target: LatLong,
    currentCenter: LatLong?
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
    maxAccuracyM: Float
): WakeAnchorSeed? {
    if (location == null) return null
    if (!location.accuracy.isFinite() || location.accuracy > maxAccuracyM) return null
    val latLong = toValidLatLongOrNull(location.latitude, location.longitude) ?: return null
    val fixElapsedMs = resolveLocationFixElapsedRealtimeMs(
        elapsedRealtimeNanos = location.elapsedRealtimeNanos,
        utcTimeMs = location.time,
        receivedAtElapsedMs = receivedAtElapsedMs,
        nowWallClockMs = nowWallClockMs
    )
    return resolveWakeAnchorSeedFromFixOrNull(
        latLong = latLong,
        fixElapsedMs = fixElapsedMs,
        receivedAtElapsedMs = receivedAtElapsedMs,
        accuracyM = location.accuracy,
        maxAgeMs = maxAgeMs,
        maxAccuracyM = maxAccuracyM,
        speedMps = if (location.hasSpeed() && location.speed.isFinite()) location.speed else 0f,
        bearingDeg = if (location.hasBearing()) location.bearing else null
    )
}

internal fun computeWakeAnchorMaxAgeMs(expectedGpsIntervalMs: Long): Long {
    return resolveLocationTimingProfile(expectedGpsIntervalMs).wakeAnchorMaxAgeMs
}

internal fun resolveStartupFreshFixMaxAgeMs(
    expectedGpsIntervalMs: Long,
    serviceFreshMaxAgeMs: Long
): Long {
    val timingProfile = resolveLocationTimingProfile(expectedGpsIntervalMs)
    val effectiveServiceFreshMaxAgeMs = serviceFreshMaxAgeMs
        .takeIf { it > 0L }
        ?: timingProfile.strictFreshFixMaxAgeMs
    return maxOf(timingProfile.strictFreshFixMaxAgeMs, effectiveServiceFreshMaxAgeMs)
}

internal fun computeWakeReacquireSnapMaxAgeMs(expectedGpsIntervalMs: Long): Long {
    return resolveLocationTimingProfile(expectedGpsIntervalMs).markerPredictionFreshnessMaxAgeMs
}

internal fun computeWakeReacquireReleaseMaxAgeMs(expectedGpsIntervalMs: Long): Long {
    return resolveLocationTimingProfile(expectedGpsIntervalMs).strictFreshFixMaxAgeMs
}

internal fun shouldReleaseWakeReacquireHold(
    holdMarkerUntilFreshFix: Boolean,
    fixFromCurrentTrackingSession: Boolean,
    wakeSnapEligible: Boolean,
    wakeReleaseEligible: Boolean,
    holdTimedOut: Boolean
): Boolean {
    if (!holdMarkerUntilFreshFix) return false
    if (fixFromCurrentTrackingSession && wakeSnapEligible) return true
    if (holdTimedOut && wakeReleaseEligible) return true
    return false
}

internal fun resolveWakeAnchorSeedFromFixOrNull(
    latLong: LatLong?,
    fixElapsedMs: Long,
    receivedAtElapsedMs: Long,
    accuracyM: Float,
    maxAgeMs: Long,
    maxAccuracyM: Float,
    speedMps: Float = 0f,
    bearingDeg: Float? = null
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
        bearingDeg = bearingDeg
    )
}

private fun isFreshLocationFix(
    location: android.location.Location?,
    maxAgeMs: Long,
    maxAccuracyM: Float
): Boolean {
    if (location == null) return false
    if (!location.accuracy.isFinite() || location.accuracy > maxAccuracyM) return false

    val ageMs = when {
        location.elapsedRealtimeNanos > 0L -> {
            val nowNanos = android.os.SystemClock.elapsedRealtimeNanos()
            ((nowNanos - location.elapsedRealtimeNanos) / 1_000_000L).coerceAtLeast(0L)
        }
        location.time > 0L -> {
            (System.currentTimeMillis() - location.time).coerceAtLeast(0L)
        }
        else -> Long.MAX_VALUE
    }

    return ageMs <= maxAgeMs
}

internal fun resolveLocationFixElapsedRealtimeMs(
    elapsedRealtimeNanos: Long,
    utcTimeMs: Long,
    receivedAtElapsedMs: Long,
    nowWallClockMs: Long
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
    staleThresholdMs: Long
): GpsFixIndicatorState {
    val ageMs = if (lastFixAtElapsedMs > 0L) {
        (nowElapsedMs - lastFixAtElapsedMs).coerceAtLeast(0L)
    } else {
        Long.MAX_VALUE
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

    return when {
        accuracyM <= GOOD_FIX_ACCURACY_THRESHOLD_M -> GpsFixIndicatorState.GOOD
        else -> GpsFixIndicatorState.POOR
    }
}

internal fun resolveGpsIndicatorDisplayState(
    rawState: GpsFixIndicatorState
): GpsFixIndicatorState {
    return rawState
}

internal fun shouldShowGpsIndicatorUnpinned(
    gpsIndicatorState: GpsFixIndicatorState,
    watchGpsDegradedWarning: Boolean
): Boolean {
    return gpsIndicatorState == GpsFixIndicatorState.SEARCHING ||
        gpsIndicatorState == GpsFixIndicatorState.UNAVAILABLE ||
        watchGpsDegradedWarning
}

private fun computeGpsFixStaleThresholdMs(expectedGpsIntervalMs: Long): Long {
    return resolveLocationTimingProfile(expectedGpsIntervalMs).indicatorStaleThresholdMs
}

private fun computeUnavailableConfirmWindowMs(staleThresholdMs: Long): Long {
    val safeStaleThresholdMs = staleThresholdMs.coerceAtLeast(1_000L)
    return (safeStaleThresholdMs * 2L).coerceIn(20_000L, 60_000L)
}

internal fun computeMarkerPredictionFreshnessMaxAgeMs(expectedGpsIntervalMs: Long): Long {
    return resolveLocationTimingProfile(expectedGpsIntervalMs).markerPredictionFreshnessMaxAgeMs
}

private const val GOOD_FIX_ACCURACY_THRESHOLD_M = 12f
