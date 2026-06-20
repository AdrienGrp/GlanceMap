package com.glancemap.glancemapwearos.presentation.features.navigate

import android.content.Context
import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.features.gpx.GpxViewModel
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.GpxGuidanceSession
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.GpxGuidanceTuning
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.TurnByTurnGuidanceState
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.computeTurnByTurnGuidanceState
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.haversineMeters
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.isGuidanceStartReached
import com.glancemap.glancemapwearos.presentation.features.navigate.guidance.projectLocationToRoute
import org.mapsforge.core.model.LatLong

internal data class NavigateGuidanceRuntime(
    val state: TurnByTurnGuidanceState,
    val guideBackToRouteActive: Boolean,
    val showGuideBackPrompt: Boolean,
    val startDecisionPrompt: GuidanceDecisionPrompt?,
    val onGuideBackToRoute: () -> Unit,
    val onDismissGuideBackPrompt: () -> Unit,
    val onAcceptStartDecisionPrompt: () -> Unit,
    val onDismissStartDecisionPrompt: () -> Unit,
)

@Composable
internal fun rememberNavigateGuidanceRuntime(
    context: Context,
    gpxViewModel: GpxViewModel,
    activeSession: GpxGuidanceSession?,
    session: GpxGuidanceSession?,
    paused: Boolean,
    rawCurrentLocation: Location?,
    recenterTarget: LatLong?,
    offlineMode: Boolean,
    routeStartBehavior: String,
    reverseSuggestionMode: String,
    offRouteThresholdMeters: Int,
    hapticsEnabled: Boolean,
    voiceGuidanceEnabled: Boolean,
    turnAlertsMode: String,
    offRouteAlertsEnabled: Boolean,
    offRouteRepeatSeconds: Int,
    guidanceGpsInAmbient: Boolean,
    brouterGuideBackEnabled: Boolean,
): NavigateGuidanceRuntime {
    val guidanceLocation: LatLong? =
        if (offlineMode) {
            null
        } else {
            rawCurrentLocation?.let { location ->
                LatLong(location.latitude, location.longitude)
            } ?: recenterTarget
        }
    val tuning =
        remember(offRouteThresholdMeters) {
            GpxGuidanceTuning(
                offRouteDistanceMeters = offRouteThresholdMeters.toDouble(),
            )
        }
    var previousGuidanceProgressMeters by
        remember(activeSession?.trackId, activeSession?.reversed) {
            mutableStateOf<Double?>(null)
        }
    val state =
        computeTurnByTurnGuidanceState(
            session = activeSession,
            currentLocation = guidanceLocation,
            tuning = tuning,
            previousDistanceFromStartMeters = previousGuidanceProgressMeters,
        )
    LaunchedEffect(activeSession?.trackId, activeSession?.reversed, state.distanceFromStartMeters) {
        state.distanceFromStartMeters?.let { previousGuidanceProgressMeters = it }
    }
    var guideBackToRouteActive by remember { mutableStateOf(false) }
    var brouterGuideBackRoute by remember { mutableStateOf<List<LatLong>>(emptyList()) }
    var dismissedGuideBackPromptTrackId by remember { mutableStateOf<String?>(null) }
    val guideBackTrackId = activeSession?.trackId
    val guideBackTargetPoint =
        nearestGuidanceRoutePoint(
            session = activeSession,
            currentLocation = guidanceLocation,
        )
    LaunchedEffect(
        state.active,
        state.offRoute,
        guideBackTrackId,
    ) {
        if (!state.active || !state.offRoute) {
            guideBackToRouteActive = false
            brouterGuideBackRoute = emptyList()
            dismissedGuideBackPromptTrackId = null
        }
    }
    val brouterGuideBackState =
        remember(guideBackToRouteActive, brouterGuideBackRoute, guidanceLocation, state) {
            buildBrouterGuideBackState(
                baseState = state,
                active = guideBackToRouteActive,
                route = brouterGuideBackRoute,
                currentLocation = guidanceLocation,
            )
        }
    val showGuideBackPrompt =
        state.active &&
            state.offRoute &&
            !guideBackToRouteActive &&
            dismissedGuideBackPromptTrackId != guideBackTrackId
    var pendingStartDecision by remember { mutableStateOf<GuidanceStartDecision?>(null) }
    var dismissedStartDecisionKey by remember { mutableStateOf<String?>(null) }
    var startHereStableSampleCount by remember { mutableStateOf(0) }
    val startDecisionKey =
        pendingStartDecision?.let { decision ->
            "$guideBackTrackId:${activeSession?.reversed}:$decision"
        }
    val startDecisionPrompt =
        pendingStartDecision?.let { decision ->
            when (decision) {
                GuidanceStartDecision.REVERSE_ROUTE ->
                    GuidanceDecisionPrompt(
                        title = "Closer to end",
                        detail = "Follow GPX in reverse?",
                        acceptText = "Reverse",
                        dismissText = "Start",
                    )
                GuidanceStartDecision.START_HERE ->
                    GuidanceDecisionPrompt(
                        title = "On route",
                        detail = "Start from nearest point?",
                        acceptText = "Start",
                        dismissText = "GPX start",
                    )
            }
        }

    LaunchedEffect(
        activeSession,
        guidanceLocation,
        rawCurrentLocation?.accuracy,
        routeStartBehavior,
        reverseSuggestionMode,
        offRouteThresholdMeters,
    ) {
        val currentSession = activeSession
        val location = guidanceLocation
        val rawGuidanceLocation =
            rawCurrentLocation?.let { rawLocation ->
                LatLong(rawLocation.latitude, rawLocation.longitude)
            }
        if (currentSession == null || location == null || currentSession.startReached) {
            pendingStartDecision = null
            dismissedStartDecisionKey = null
            startHereStableSampleCount = 0
            return@LaunchedEffect
        }

        val points = currentSession.trackPoints.map { it.latLong }
        val start = points.firstOrNull()
        val end = points.lastOrNull()
        val projection =
            projectLocationToRoute(
                points = points,
                cumulativeDistancesMeters = currentSession.cumulativeDistancesMeters,
                location = location,
            )
        if (start == null || end == null || projection == null) {
            pendingStartDecision = null
            startHereStableSampleCount = 0
            return@LaunchedEffect
        }

        val distanceToStart = haversineMeters(location, start)
        val distanceToEnd = haversineMeters(location, end)
        val closeToRoute = projection.distanceToRouteMeters <= offRouteThresholdMeters.toDouble()
        val midRouteCandidate =
            closeToRoute &&
                distanceToStart > tuning.startReachedDistanceMeters &&
                projection.distanceFromStartMeters > START_HERE_MIN_PROGRESS_METERS &&
                currentSession.totalDistanceMeters - projection.distanceFromStartMeters > START_HERE_MIN_REMAINING_METERS
        val locationAccurateEnough =
            rawCurrentLocation?.accuracy?.let { accuracy ->
                accuracy <= START_HERE_MAX_ACCURACY_METERS
            } ?: false
        val hasFreshGpsLocation = rawGuidanceLocation != null
        if (midRouteCandidate && locationAccurateEnough) {
            startHereStableSampleCount += 1
        } else {
            startHereStableSampleCount = 0
        }
        val stableMidRouteCandidate =
            midRouteCandidate &&
                hasFreshGpsLocation &&
                startHereStableSampleCount >= START_HERE_STABLE_SAMPLE_COUNT
        val reverseCandidate =
            !currentSession.reversed &&
                reverseSuggestionMode == SettingsRepository.TURN_BY_TURN_REVERSE_SUGGESTION_ASK &&
                distanceToEnd + REVERSE_SUGGESTION_DISTANCE_MARGIN_METERS < distanceToStart &&
                distanceToEnd <= REVERSE_SUGGESTION_MAX_DISTANCE_METERS

        val nextDecision =
            when {
                reverseCandidate -> GuidanceStartDecision.REVERSE_ROUTE
                stableMidRouteCandidate &&
                    routeStartBehavior == SettingsRepository.TURN_BY_TURN_ROUTE_START_NEAREST_POINT -> {
                    gpxViewModel.markTurnByTurnStartReached()
                    null
                }
                stableMidRouteCandidate &&
                    routeStartBehavior == SettingsRepository.TURN_BY_TURN_ROUTE_START_ASK ->
                    GuidanceStartDecision.START_HERE
                else -> null
            }

        val nextKey = nextDecision?.let { "${currentSession.trackId}:${currentSession.reversed}:$it" }
        pendingStartDecision =
            if (nextKey != null && dismissedStartDecisionKey != nextKey) {
                nextDecision
            } else {
                null
            }
    }

    LaunchedEffect(activeSession, guidanceLocation, tuning) {
        if (isGuidanceStartReached(activeSession, guidanceLocation, tuning)) {
            gpxViewModel.markTurnByTurnStartReached()
        }
    }

    TurnByTurnGuidanceHapticEffect(
        context = context,
        state = state,
        currentSpeedMps = rawCurrentLocation?.speed,
        hapticsEnabled = hapticsEnabled,
        turnAlertsMode = turnAlertsMode,
        offRouteAlertsEnabled = offRouteAlertsEnabled,
        offRouteRepeatSeconds = offRouteRepeatSeconds,
    )

    TurnByTurnGuidanceVoiceEffect(
        context = context,
        state = state,
        currentSpeedMps = rawCurrentLocation?.speed,
        voiceEnabled = voiceGuidanceEnabled,
        turnAlertsMode = turnAlertsMode,
        paused = paused,
    )

    LaunchedEffect(
        state.active,
        state.mode,
        state.nextInstruction?.trackPointIndex,
        state.distanceToInstructionMeters?.roundTelemetryMeters(),
        state.distanceToStartMeters?.roundTelemetryMeters(),
        state.distanceToRouteMeters?.roundTelemetryMeters(),
        state.distanceRemainingMeters?.roundTelemetryMeters(),
        state.routeProgressFraction?.roundTelemetryPercent(),
        state.offRoute,
        paused,
        session?.trackId,
        session?.reversed,
        session?.startReached,
        guideBackToRouteActive,
        showGuideBackPrompt,
        pendingStartDecision,
        routeStartBehavior,
        reverseSuggestionMode,
        offRouteThresholdMeters,
        hapticsEnabled,
        voiceGuidanceEnabled,
        turnAlertsMode,
        offRouteAlertsEnabled,
        guidanceGpsInAmbient,
    ) {
        if (!state.active && session == null) return@LaunchedEffect
        DebugTelemetry.log(
            "TurnByTurn",
            buildTurnByTurnTelemetryMessage(
                state = state,
                paused = paused,
                trackId = session?.trackId,
                reversed = session?.reversed,
                startReached = session?.startReached,
                guideBackToRouteActive = guideBackToRouteActive,
                showGuideBackPrompt = showGuideBackPrompt,
                pendingStartDecision = pendingStartDecision,
                routeStartBehavior = routeStartBehavior,
                reverseSuggestionMode = reverseSuggestionMode,
                offRouteThresholdMeters = offRouteThresholdMeters,
                hapticsEnabled = hapticsEnabled,
                voiceGuidanceEnabled = voiceGuidanceEnabled,
                turnAlertsMode = turnAlertsMode,
                offRouteAlertsEnabled = offRouteAlertsEnabled,
                guidanceGpsInAmbient = guidanceGpsInAmbient,
            ),
        )
    }

    return NavigateGuidanceRuntime(
        state = brouterGuideBackState,
        guideBackToRouteActive = guideBackToRouteActive && state.offRoute,
        showGuideBackPrompt = showGuideBackPrompt,
        startDecisionPrompt = startDecisionPrompt,
        onGuideBackToRoute = {
            guideBackToRouteActive = true
            dismissedGuideBackPromptTrackId = guideBackTrackId
            brouterGuideBackRoute = emptyList()
            val origin = guidanceLocation
            val destination = guideBackTargetPoint
            if (brouterGuideBackEnabled && origin != null && destination != null) {
                gpxViewModel.buildTurnByTurnGuideBackRoute(
                    origin = origin,
                    destination = destination,
                ) { result ->
                    result.onSuccess { route ->
                        if (guideBackToRouteActive) {
                            brouterGuideBackRoute = route
                        }
                    }
                }
            }
        },
        onDismissGuideBackPrompt = {
            dismissedGuideBackPromptTrackId = guideBackTrackId
        },
        onAcceptStartDecisionPrompt = {
            when (pendingStartDecision) {
                GuidanceStartDecision.REVERSE_ROUTE -> gpxViewModel.reverseTurnByTurnGuidance()
                GuidanceStartDecision.START_HERE -> gpxViewModel.markTurnByTurnStartReached()
                null -> Unit
            }
            dismissedStartDecisionKey = startDecisionKey
            pendingStartDecision = null
        },
        onDismissStartDecisionPrompt = {
            dismissedStartDecisionKey = startDecisionKey
            pendingStartDecision = null
        },
    )
}
