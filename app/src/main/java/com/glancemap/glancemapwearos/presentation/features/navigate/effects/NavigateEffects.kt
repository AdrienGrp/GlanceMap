@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "ReturnCount",
)

package com.glancemap.glancemapwearos.presentation.features.navigate

import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.domain.model.maps.theme.mapsforge.MapsforgeThemeCatalog
import com.glancemap.glancemapwearos.domain.sensors.COMPASS_TELEMETRY_TAG
import com.glancemap.glancemapwearos.domain.sensors.CompassProviderType
import com.glancemap.glancemapwearos.domain.sensors.CompassRenderState
import com.glancemap.glancemapwearos.domain.sensors.HeadingSource
import com.glancemap.glancemapwearos.domain.sensors.hasRecentGoogleFusedCachedHeading
import com.glancemap.glancemapwearos.presentation.features.maps.MapRenderer
import com.glancemap.glancemapwearos.presentation.features.maps.RotatableMarker
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Rotation
import org.mapsforge.map.android.view.MapView
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * Synchronized Map + Marker rotation for Compass, North-Up and Panning modes.
 *
 * Works with your RotatableMarker implementation (it compensates map rotation internally).
 */
@Composable
fun NavigationOrientationEffect(
    isCompassMode: Boolean,
    isAutoCentering: Boolean,
    forceNorthUpInPanning: Boolean,
    renderStateFlow: StateFlow<CompassRenderState>,
    mapView: MapView?,
    showRealMarkerInCompassMode: Boolean,
    locationMarker: RotatableMarker?,
    navigationMarkerAnchorMode: String,
    onRenderedHeadingChanged: (Float) -> Unit,
    onRenderedMapRotationChanged: (Float) -> Unit,
    requestMapRedraw: () -> Unit,
) {
    val mv = mapView ?: return
    val marker = locationMarker
    val latestNavigationMarkerAnchorMode = rememberUpdatedState(navigationMarkerAnchorMode)

    val navMode =
        remember(isCompassMode, isAutoCentering) {
            when {
                !isAutoCentering -> NavMode.PANNING
                isCompassMode -> NavMode.COMPASS_FOLLOW
                else -> NavMode.NORTH_UP_FOLLOW
            }
        }

    val displayedHeading = remember { mutableFloatStateOf(normalize360(renderStateFlow.value.headingDeg)) }
    val displayedMapRot = remember { mutableFloatStateOf(0f) }
    val frozenRotationDeg = remember { mutableFloatStateOf(0f) }
    val lastMapsforgeRotationAppliedAtMs = remember(mv) { mutableLongStateOf(Long.MIN_VALUE) }

    fun syncDisplayedMapRotationFromMap(): Float {
        val actualRotationDeg = mv.mapRotation.degrees
        displayedMapRot.floatValue = actualRotationDeg
        return actualRotationDeg
    }

    fun recenterLowerMarkerAnchor() {
        if (navMode == NavMode.PANNING) return
        val markerLatLong = marker?.latLong ?: return
        val anchorMode = latestNavigationMarkerAnchorMode.value
        if (anchorMode != SettingsRepository.NAVIGATION_MARKER_ANCHOR_LOWER) return
        val desiredCenter = mv.resolveMapCenterForNavigationMarker(markerLatLong, anchorMode)
        if (shouldUpdateMapCenter(desiredCenter, mv.model.mapViewPosition.center)) {
            mv.setCenter(desiredCenter)
        }
    }

    fun applyMapRotation(
        targetRotationDeg: Float,
        highFrequencyRotation: Boolean = false,
    ) {
        recenterLowerMarkerAnchor()
        val currentRotationDeg = syncDisplayedMapRotationFromMap()
        val applyEpsilonDeg =
            if (highFrequencyRotation) {
                MAP_ROTATION_ACTIVE_TURN_APPLY_EPSILON_DEG
            } else {
                MAP_ROTATION_APPLY_EPSILON_DEG
            }
        if (abs(angleDeltaDeg(targetRotationDeg, currentRotationDeg)) < applyEpsilonDeg) {
            CompassRenderPerfTelemetry.recordRotationSkipped(navMode)
            onRenderedMapRotationChanged(currentRotationDeg)
            return
        }
        val nowElapsedMs = SystemClock.elapsedRealtime()
        if (
            shouldThrottleMapsforgeRotation(
                navMode = navMode,
                nowElapsedMs = nowElapsedMs,
                lastAppliedAtElapsedMs = lastMapsforgeRotationAppliedAtMs.longValue,
                highFrequencyRotation = highFrequencyRotation,
            )
        ) {
            CompassRenderPerfTelemetry.recordRotationThrottled(navMode)
            onRenderedMapRotationChanged(currentRotationDeg)
            return
        }
        val anchor = mv.resolveNavigationMarkerScreenAnchor(latestNavigationMarkerAnchorMode.value)
        if (mv.trySetMapsforgeRotation(targetRotationDeg, anchor)) {
            lastMapsforgeRotationAppliedAtMs.longValue = nowElapsedMs
            CompassRenderPerfTelemetry.recordRotationApplied(navMode)
            val appliedRotationDeg = syncDisplayedMapRotationFromMap()
            onRenderedMapRotationChanged(appliedRotationDeg)
        }
    }

    fun applyMarkersForMode(targetNavMode: NavMode) {
        val markerState =
            markerRenderStateForMode(
                navMode = targetNavMode,
                displayedHeadingDeg = displayedHeading.floatValue,
                displayedMapRotationDeg = displayedMapRot.floatValue,
                frozenMapRotationDeg = frozenRotationDeg.floatValue,
                showRealMarkerInCompassMode = showRealMarkerInCompassMode,
            )
        applyMarkerRenderState(
            marker = marker,
            state = markerState,
        )
        CompassRenderPerfTelemetry.recordMarkerUpdate(targetNavMode)
    }

    LaunchedEffect(mv) {
        // Clear any legacy Android view rotation so map orientation is driven only by Mapsforge.
        mv.rotation = 0f
        onRenderedMapRotationChanged(syncDisplayedMapRotationFromMap())
    }

    LaunchedEffect(
        navMode,
        mv,
        forceNorthUpInPanning,
        navigationMarkerAnchorMode,
    ) {
        val renderStateNow = renderStateFlow.value
        val headingNow = normalize360(renderStateNow.headingDeg)
        val shouldDriveHeadingNow = shouldDriveHeadingForNavMode(navMode, renderStateNow)
        val shouldSeedCachedHeading =
            when (navMode) {
                NavMode.COMPASS_FOLLOW ->
                    shouldSeedCompassFollowMapWithCachedHeading(
                        renderState = renderStateNow,
                        nowElapsedMs = SystemClock.elapsedRealtime(),
                    )
                NavMode.NORTH_UP_FOLLOW ->
                    shouldSeedNorthUpMarkerWithCachedHeading(
                        renderState = renderStateNow,
                        nowElapsedMs = SystemClock.elapsedRealtime(),
                    )
                NavMode.PANNING -> false
            }
        if (shouldDriveHeadingNow || shouldSeedCachedHeading) {
            displayedHeading.floatValue = headingNow
            onRenderedHeadingChanged(headingNow)
        }

        when (navMode) {
            NavMode.COMPASS_FOLLOW -> {
                if (shouldDriveHeadingNow || shouldSeedCachedHeading) {
                    val rot = -displayedHeading.floatValue
                    applyMapRotation(rot)
                } else {
                    val heldMapRotation = syncDisplayedMapRotationFromMap()
                    val heldHeading = normalize360(-heldMapRotation)
                    displayedHeading.floatValue = heldHeading
                    onRenderedHeadingChanged(heldHeading)
                    onRenderedMapRotationChanged(heldMapRotation)
                }
            }

            NavMode.NORTH_UP_FOLLOW -> {
                applyMapRotation(0f)
            }

            NavMode.PANNING -> {
                val frozen = if (forceNorthUpInPanning) 0f else mv.mapRotation.degrees
                frozenRotationDeg.floatValue = frozen
                applyMapRotation(frozen)
            }
        }

        requestMapRedraw()
    }

    LaunchedEffect(
        navMode,
        marker,
        showRealMarkerInCompassMode,
        forceNorthUpInPanning,
    ) {
        applyMarkersForMode(navMode)
        requestMapRedraw()
    }

    // Heading updates — animated at display frame rate for smooth 60fps rotation.
    // A child coroutine tracks the latest sensor heading; the frame loop chases it
    // using an exponential ease so motion appears fluid between ~20Hz sensor updates.
    LaunchedEffect(
        navMode,
        mv,
        renderStateFlow,
        requestMapRedraw,
        showRealMarkerInCompassMode,
        forceNorthUpInPanning,
        navigationMarkerAnchorMode,
    ) {
        // Local var: safe because both coroutines run on Main (single-threaded).
        var liveTarget = displayedHeading.floatValue
        var latestRenderState = renderStateFlow.value
        var lastTargetUpdateAtElapsedMs = SystemClock.elapsedRealtime()
        var fastTurnUntilElapsedMs = 0L
        var activeMapTurnUntilElapsedMs = 0L
        var headingDriveWasReady = shouldDriveHeadingForNavMode(navMode, latestRenderState)
        var startupHandoffActive = false
        var startupHandoffStartedAtElapsedMs = 0L
        var startupHandoffUntilElapsedMs = 0L
        var startupSettlingWasActive = false
        var startupSettlingStartedAtElapsedMs = 0L
        var previousSettlingRawHeadingDeg: Float? = null
        var previousSettlingRawAtElapsedMs = 0L
        var settlingAcceptedRotationDeg = 0f
        var settlingRejectedJumpCount = 0
        var settlingRejectedJumpMaxDeg = 0f
        var lastAnimationFrameAtElapsedMs = SystemClock.elapsedRealtime()

        fun completeStartupHandoff(
            nowElapsedMs: Long,
            renderedHeadingDeg: Float,
        ) {
            startupHandoffActive = false
            DebugTelemetry.log(
                COMPASS_TELEMETRY_TAG,
                "map_heading_handoff stage=complete " +
                    "durationMs=${(nowElapsedMs - startupHandoffStartedAtElapsedMs).coerceAtLeast(0L)} " +
                    "heading=${renderedHeadingDeg.formatTelemetry(1)} " +
                    "target=${liveTarget.formatTelemetry(1)}",
            )
        }

        // Keep liveTarget current without blocking the animation loop.
        launch {
            renderStateFlow.collect { state ->
                latestRenderState = state
                val canDriveHeading = shouldDriveHeadingForNavMode(navMode, state)
                if (!canDriveHeading) {
                    headingDriveWasReady = false
                    return@collect
                }
                val heading = normalize360(state.headingDeg)
                val startupRawHeading = normalize360(state.startupRawHeadingDeg ?: state.headingDeg)
                val nowElapsedMs = SystemClock.elapsedRealtime()
                val elapsedSinceTargetMs = nowElapsedMs - lastTargetUpdateAtElapsedMs
                if (state.startupSettling) {
                    val previousRawHeading = previousSettlingRawHeadingDeg
                    val previousRawAt = previousSettlingRawAtElapsedMs
                    if (!startupSettlingWasActive) {
                        startupSettlingWasActive = true
                        startupSettlingStartedAtElapsedMs = nowElapsedMs
                        settlingAcceptedRotationDeg = 0f
                        settlingRejectedJumpCount = 0
                        settlingRejectedJumpMaxDeg = 0f
                        DebugTelemetry.log(
                            COMPASS_TELEMETRY_TAG,
                            "map_heading_settling stage=start " +
                                "anchor=${displayedHeading.floatValue.formatTelemetry(1)} " +
                                "rawHeading=${startupRawHeading.formatTelemetry(1)} " +
                                "mapRotation=${displayedMapRot.floatValue.formatTelemetry(1)}",
                        )
                    } else if (previousRawHeading != null && previousRawAt > 0L) {
                        val rawDeltaDeg = angleDeltaDeg(startupRawHeading, previousRawHeading)
                        val responsiveDeltaDeg =
                            resolveCompassSettlingRelativeDelta(
                                previousRawHeadingDeg = previousRawHeading,
                                nextRawHeadingDeg = startupRawHeading,
                                elapsedMs = nowElapsedMs - previousRawAt,
                            )
                        if (responsiveDeltaDeg == 0f && abs(rawDeltaDeg) >= SETTLING_REJECT_TELEMETRY_MIN_DEG) {
                            settlingRejectedJumpCount += 1
                            settlingRejectedJumpMaxDeg = maxOf(settlingRejectedJumpMaxDeg, abs(rawDeltaDeg))
                        } else if (responsiveDeltaDeg != 0f) {
                            val responsiveTarget = normalize360(liveTarget + responsiveDeltaDeg)
                            settlingAcceptedRotationDeg += abs(responsiveDeltaDeg)
                            if (
                                isFastHeadingTurn(
                                    previousHeadingDeg = liveTarget,
                                    nextHeadingDeg = responsiveTarget,
                                    elapsedMs = elapsedSinceTargetMs,
                                )
                            ) {
                                fastTurnUntilElapsedMs = nowElapsedMs + FAST_TURN_RENDER_HOLD_MS
                            }
                            if (
                                isActiveMapHeadingTurn(
                                    previousHeadingDeg = liveTarget,
                                    nextHeadingDeg = responsiveTarget,
                                    elapsedMs = elapsedSinceTargetMs,
                                )
                            ) {
                                activeMapTurnUntilElapsedMs = nowElapsedMs + ACTIVE_MAP_TURN_RENDER_HOLD_MS
                            }
                            liveTarget = responsiveTarget
                            lastTargetUpdateAtElapsedMs = nowElapsedMs
                            CompassRenderPerfTelemetry.recordTargetUpdate(navMode)
                        }
                    }
                    previousSettlingRawHeadingDeg = startupRawHeading
                    previousSettlingRawAtElapsedMs = nowElapsedMs
                    headingDriveWasReady = true
                    return@collect
                }
                if (startupSettlingWasActive) {
                    startupSettlingWasActive = false
                    headingDriveWasReady = false
                    DebugTelemetry.log(
                        COMPASS_TELEMETRY_TAG,
                        "map_heading_settling stage=release " +
                            "durationMs=${
                                (nowElapsedMs - startupSettlingStartedAtElapsedMs).coerceAtLeast(0L)
                            } " +
                            "fusedHeading=${heading.formatTelemetry(1)} " +
                            "renderedHeading=${displayedHeading.floatValue.formatTelemetry(1)} " +
                            "mapRotation=${displayedMapRot.floatValue.formatTelemetry(1)} " +
                            "handoffDelta=${angleDeltaDeg(heading, displayedHeading.floatValue).formatTelemetry(1)} " +
                            "relativeRotation=${settlingAcceptedRotationDeg.formatTelemetry(1)} " +
                            "rejectedJumps=$settlingRejectedJumpCount " +
                            "rejectedMaxDeg=${settlingRejectedJumpMaxDeg.formatTelemetry(1)}",
                    )
                    previousSettlingRawHeadingDeg = null
                    previousSettlingRawAtElapsedMs = 0L
                }
                if (
                    !headingDriveWasReady &&
                    state.providerType == CompassProviderType.GOOGLE_FUSED
                ) {
                    val handoffDeltaDeg = angleDeltaDeg(heading, displayedHeading.floatValue)
                    startupHandoffActive =
                        abs(handoffDeltaDeg) >= HEADING_ANIMATION_DONE_DEG
                    if (startupHandoffActive) {
                        startupHandoffStartedAtElapsedMs = nowElapsedMs
                        startupHandoffUntilElapsedMs =
                            nowElapsedMs + COMPASS_STARTUP_HANDOFF_MIN_DURATION_MS
                        DebugTelemetry.log(
                            COMPASS_TELEMETRY_TAG,
                            "map_heading_handoff stage=start " +
                                "from=${displayedHeading.floatValue.formatTelemetry(1)} " +
                                "target=${heading.formatTelemetry(1)} " +
                                "delta=${handoffDeltaDeg.formatTelemetry(1)}",
                        )
                    }
                }
                headingDriveWasReady = true
                if (
                    isFastHeadingTurn(
                        previousHeadingDeg = liveTarget,
                        nextHeadingDeg = heading,
                        elapsedMs = elapsedSinceTargetMs,
                    )
                ) {
                    fastTurnUntilElapsedMs = nowElapsedMs + FAST_TURN_RENDER_HOLD_MS
                }
                if (
                    isActiveMapHeadingTurn(
                        previousHeadingDeg = liveTarget,
                        nextHeadingDeg = heading,
                        elapsedMs = elapsedSinceTargetMs,
                    )
                ) {
                    activeMapTurnUntilElapsedMs = nowElapsedMs + ACTIVE_MAP_TURN_RENDER_HOLD_MS
                }
                liveTarget = heading
                lastTargetUpdateAtElapsedMs = nowElapsedMs
                CompassRenderPerfTelemetry.recordTargetUpdate(navMode)
            }
        }

        // Animate toward liveTarget on every display frame.
        while (true) {
            withFrameNanos {
                if (navMode == NavMode.PANNING) return@withFrameNanos
                if (!shouldDriveHeadingForNavMode(navMode, latestRenderState)) {
                    return@withFrameNanos
                }
                CompassRenderPerfTelemetry.recordFrame(navMode)
                val nowElapsedMs = SystemClock.elapsedRealtime()
                val frameElapsedMs =
                    (nowElapsedMs - lastAnimationFrameAtElapsedMs)
                        .coerceIn(1L, COMPASS_ANIMATION_MAX_FRAME_GAP_MS)
                lastAnimationFrameAtElapsedMs = nowElapsedMs
                val current = displayedHeading.floatValue
                val diff = angleDeltaDeg(liveTarget, current)
                if (abs(diff) < HEADING_ANIMATION_DONE_DEG) {
                    if (startupHandoffActive && nowElapsedMs >= startupHandoffUntilElapsedMs) {
                        completeStartupHandoff(
                            nowElapsedMs = nowElapsedMs,
                            renderedHeadingDeg = current,
                        )
                    }
                    return@withFrameNanos
                }

                val handoffActiveForFrame = startupHandoffActive
                val animationDelta =
                    resolveHeadingAnimationDelta(
                        diffDeg = diff,
                        fastTurn = nowElapsedMs <= fastTurnUntilElapsedMs,
                        startupHandoff = handoffActiveForFrame,
                        frameElapsedMs = frameElapsedMs,
                    )
                val next = normalize360(current + animationDelta)
                if (
                    handoffActiveForFrame &&
                    nowElapsedMs >= startupHandoffUntilElapsedMs &&
                    abs(diff) <= COMPASS_STARTUP_HANDOFF_COMPLETE_DELTA_DEG
                ) {
                    completeStartupHandoff(
                        nowElapsedMs = nowElapsedMs,
                        renderedHeadingDeg = next,
                    )
                }
                displayedHeading.floatValue = next
                onRenderedHeadingChanged(next)
                CompassRenderPerfTelemetry.recordHeadingRender(navMode)

                when (navMode) {
                    NavMode.COMPASS_FOLLOW -> {
                        applyMapRotation(
                            targetRotationDeg = -next,
                            highFrequencyRotation =
                                handoffActiveForFrame ||
                                    nowElapsedMs <= activeMapTurnUntilElapsedMs,
                        )
                    }
                    NavMode.NORTH_UP_FOLLOW -> {
                        applyMapRotation(0f)
                        applyMarkersForMode(navMode)
                    }
                    NavMode.PANNING -> Unit
                }
                requestMapRedraw()
                CompassRenderPerfTelemetry.recordRedraw(navMode)
            }
        }
    }
}

private object CompassRenderPerfTelemetry {
    private var windowStartElapsedMs: Long = 0L
    private var frameCount: Int = 0
    private var targetUpdateCount: Int = 0
    private var headingRenderCount: Int = 0
    private var rotationAppliedCount: Int = 0
    private var rotationSkippedCount: Int = 0
    private var rotationThrottledCount: Int = 0
    private var markerUpdateCount: Int = 0
    private var redrawCount: Int = 0

    fun recordFrame(navMode: NavMode) = record(navMode) { frameCount += 1 }

    fun recordTargetUpdate(navMode: NavMode) = record(navMode) { targetUpdateCount += 1 }

    fun recordHeadingRender(navMode: NavMode) = record(navMode) { headingRenderCount += 1 }

    fun recordRotationApplied(navMode: NavMode) = record(navMode) { rotationAppliedCount += 1 }

    fun recordRotationSkipped(navMode: NavMode) = record(navMode) { rotationSkippedCount += 1 }

    fun recordRotationThrottled(navMode: NavMode) = record(navMode) { rotationThrottledCount += 1 }

    fun recordMarkerUpdate(navMode: NavMode) = record(navMode) { markerUpdateCount += 1 }

    fun recordRedraw(navMode: NavMode) = record(navMode) { redrawCount += 1 }

    @Synchronized
    private fun record(
        navMode: NavMode,
        mutate: () -> Unit,
    ) {
        if (!DebugTelemetry.isEnabled()) return
        val now = SystemClock.elapsedRealtime()
        if (windowStartElapsedMs == 0L) {
            windowStartElapsedMs = now
        }
        mutate()
        val windowMs = (now - windowStartElapsedMs).coerceAtLeast(0L)
        if (windowMs < COMPASS_RENDER_PERF_LOG_WINDOW_MS) return
        val seconds = (windowMs / 1000f).coerceAtLeast(0.001f)
        DebugTelemetry.log(
            COMPASS_TELEMETRY_TAG,
            "compass_render perf windowMs=$windowMs navMode=${navMode.name} " +
                "frames=$frameCount frameHz=${(frameCount / seconds).formatTelemetry(1)} " +
                "targetUpdates=$targetUpdateCount headingRenders=$headingRenderCount " +
                "renderHz=${(headingRenderCount / seconds).formatTelemetry(1)} " +
                "rotationApplied=$rotationAppliedCount rotationSkipped=$rotationSkippedCount " +
                "rotationThrottled=$rotationThrottledCount " +
                "markerUpdates=$markerUpdateCount redraws=$redrawCount",
        )
        reset(now)
    }

    private fun reset(nextWindowStartElapsedMs: Long) {
        windowStartElapsedMs = nextWindowStartElapsedMs
        frameCount = 0
        targetUpdateCount = 0
        headingRenderCount = 0
        rotationAppliedCount = 0
        rotationSkippedCount = 0
        rotationThrottledCount = 0
        markerUpdateCount = 0
        redrawCount = 0
    }
}

private fun angleDeltaDeg(
    target: Float,
    current: Float,
): Float {
    var d = (target - current) % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

internal fun shouldDriveCompassFollowMap(renderState: CompassRenderState): Boolean {
    if (renderState.headingSource == HeadingSource.NONE) return false
    if (renderState.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) return false
    if (renderState.providerType == CompassProviderType.GOOGLE_FUSED) {
        if (renderState.headingSource != HeadingSource.FUSED_ORIENTATION) return false
        if (renderState.headingSampleStale || renderState.headingSampleElapsedRealtimeMs == null) {
            return false
        }
    }
    return true
}

internal fun shouldDriveMarkerHeading(renderState: CompassRenderState): Boolean {
    val hasBaseHeading =
        renderState.headingSource != HeadingSource.NONE &&
            renderState.accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE
    val providerAllowsMarkerHeading =
        when (renderState.providerType) {
            CompassProviderType.SENSOR_MANAGER -> true
            CompassProviderType.GOOGLE_FUSED ->
                renderState.headingSource == HeadingSource.FUSED_ORIENTATION &&
                    renderState.headingSampleElapsedRealtimeMs != null &&
                    !renderState.headingSampleStale
        }
    return hasBaseHeading && providerAllowsMarkerHeading
}

internal fun shouldDriveHeadingForNavMode(
    navMode: NavMode,
    renderState: CompassRenderState,
): Boolean =
    when (navMode) {
        NavMode.COMPASS_FOLLOW -> shouldDriveCompassFollowMap(renderState)
        NavMode.NORTH_UP_FOLLOW -> shouldDriveMarkerHeading(renderState)
        NavMode.PANNING -> false
    }

internal fun shouldSeedCompassFollowMapWithCachedHeading(
    renderState: CompassRenderState,
    nowElapsedMs: Long,
): Boolean =
    hasRecentGoogleFusedCachedHeading(
        renderState = renderState,
        nowElapsedMs = nowElapsedMs,
        maxAgeMs = GOOGLE_FUSED_CACHED_HEADING_SEED_MAX_AGE_MS,
    )

internal fun shouldSeedNorthUpMarkerWithCachedHeading(
    renderState: CompassRenderState,
    nowElapsedMs: Long,
): Boolean =
    renderState.providerType == CompassProviderType.GOOGLE_FUSED &&
        hasRecentGoogleFusedCachedHeading(
            renderState = renderState,
            nowElapsedMs = nowElapsedMs,
            maxAgeMs = GOOGLE_FUSED_CACHED_HEADING_SEED_MAX_AGE_MS,
        )

internal fun resolveNavigateInitialRenderedHeadingDeg(
    renderState: CompassRenderState,
    nowElapsedMs: Long,
): Float =
    if (
        shouldDriveCompassFollowMap(renderState) ||
        shouldDriveMarkerHeading(renderState) ||
        shouldSeedCompassFollowMapWithCachedHeading(renderState, nowElapsedMs) ||
        shouldSeedNorthUpMarkerWithCachedHeading(renderState, nowElapsedMs)
    ) {
        normalize360(renderState.headingDeg)
    } else {
        0f
    }

private fun normalize360(deg: Float): Float = (deg % 360f + 360f) % 360f

internal fun shouldThrottleMapsforgeRotation(
    navMode: NavMode,
    nowElapsedMs: Long,
    lastAppliedAtElapsedMs: Long,
    highFrequencyRotation: Boolean = false,
): Boolean {
    val minimumIntervalMs =
        if (highFrequencyRotation) {
            MAP_ROTATION_ACTIVE_TURN_MIN_APPLY_INTERVAL_MS
        } else {
            MAP_ROTATION_MIN_APPLY_INTERVAL_MS
        }
    return navMode == NavMode.COMPASS_FOLLOW &&
        lastAppliedAtElapsedMs != Long.MIN_VALUE &&
        nowElapsedMs - lastAppliedAtElapsedMs < minimumIntervalMs
}

internal fun isFastHeadingTurn(
    previousHeadingDeg: Float,
    nextHeadingDeg: Float,
    elapsedMs: Long,
): Boolean =
    isHeadingTurnAtLeastRate(
        previousHeadingDeg = previousHeadingDeg,
        nextHeadingDeg = nextHeadingDeg,
        elapsedMs = elapsedMs,
        minimumRateDegPerSec = FAST_TURN_MIN_RATE_DEG_PER_SEC,
    )

internal fun isActiveMapHeadingTurn(
    previousHeadingDeg: Float,
    nextHeadingDeg: Float,
    elapsedMs: Long,
): Boolean =
    isHeadingTurnAtLeastRate(
        previousHeadingDeg = previousHeadingDeg,
        nextHeadingDeg = nextHeadingDeg,
        elapsedMs = elapsedMs,
        minimumRateDegPerSec = ACTIVE_MAP_TURN_MIN_RATE_DEG_PER_SEC,
    )

private fun isHeadingTurnAtLeastRate(
    previousHeadingDeg: Float,
    nextHeadingDeg: Float,
    elapsedMs: Long,
    minimumRateDegPerSec: Float,
): Boolean {
    if (!previousHeadingDeg.isFinite() || !nextHeadingDeg.isFinite()) return false
    if (elapsedMs <= 0L || elapsedMs > FAST_TURN_MAX_SAMPLE_GAP_MS) return false
    val rotationRateDegPerSec =
        abs(angleDeltaDeg(nextHeadingDeg, previousHeadingDeg)) * 1_000f / elapsedMs.toFloat()
    return rotationRateDegPerSec >= minimumRateDegPerSec
}

internal fun resolveHeadingAnimationAlpha(
    diffDeg: Float,
    fastTurn: Boolean,
): Float =
    when {
        !diffDeg.isFinite() -> 0f
        fastTurn && abs(diffDeg) >= FAST_TURN_LARGE_ERROR_DEG -> FAST_TURN_LARGE_ERROR_ALPHA
        fastTurn -> FAST_TURN_ANIMATION_ALPHA
        else -> HEADING_ANIMATION_ALPHA
    }

internal fun resolveHeadingAnimationDelta(
    diffDeg: Float,
    fastTurn: Boolean,
    startupHandoff: Boolean,
    frameElapsedMs: Long = 16L,
): Float {
    if (!diffDeg.isFinite()) return 0f
    if (startupHandoff) {
        val maximumStepDeg =
            COMPASS_STARTUP_HANDOFF_MAX_RATE_DEG_PER_SEC *
                frameElapsedMs.coerceAtLeast(1L).toFloat() /
                1_000f
        return diffDeg.coerceIn(
            minimumValue = -maximumStepDeg,
            maximumValue = maximumStepDeg,
        )
    }
    val animatedDelta = diffDeg * resolveHeadingAnimationAlpha(diffDeg = diffDeg, fastTurn = fastTurn)
    return animatedDelta.coerceIn(
        minimumValue = -HEADING_ANIMATION_MAX_STEP_DEG,
        maximumValue = HEADING_ANIMATION_MAX_STEP_DEG,
    )
}

internal fun resolveCompassSettlingRelativeDelta(
    previousRawHeadingDeg: Float,
    nextRawHeadingDeg: Float,
    elapsedMs: Long,
): Float {
    if (!previousRawHeadingDeg.isFinite() || !nextRawHeadingDeg.isFinite()) return 0f
    if (elapsedMs <= 0L || elapsedMs > SETTLING_MAX_SAMPLE_GAP_MS) return 0f
    val rawDeltaDeg = angleDeltaDeg(nextRawHeadingDeg, previousRawHeadingDeg)
    val maximumPlausibleDeltaDeg =
        (SETTLING_MAX_TURN_RATE_DEG_PER_SEC * elapsedMs.toFloat() / 1_000f)
            .coerceIn(
                minimumValue = SETTLING_MIN_PLAUSIBLE_DELTA_DEG,
                maximumValue = SETTLING_MAX_PLAUSIBLE_DELTA_DEG,
            )
    return rawDeltaDeg.takeIf { abs(it) <= maximumPlausibleDeltaDeg } ?: 0f
}

// Small heading noise is visible as left/right map shimmer in compass-follow.
// Keep the compass pipeline responsive, but avoid applying sub-degree Mapsforge rotations.
private const val MAP_ROTATION_APPLY_EPSILON_DEG = 0.8f
private const val MAP_ROTATION_ACTIVE_TURN_APPLY_EPSILON_DEG = 0.35f

// Animate the Compose heading every display frame, but avoid asking Mapsforge to redraw/rotate
// more than 30 times per second while stationary. During a deliberate turn, temporarily allow
// display-rate rotation so a 360-degree sweep stays fluid, then fall back to the lower-power rate.
private const val MAP_ROTATION_MIN_APPLY_INTERVAL_MS = 33L
private const val MAP_ROTATION_ACTIVE_TURN_MIN_APPLY_INTERVAL_MS = 16L

// Interpolation factor per display frame (~60fps). At 0.5, closes half the remaining
// gap each frame: a 10° step reaches <0.1° in ~7 frames (~117ms). Tracks 50Hz sensor
// updates with at most 1-2 frames of visual lag.
private const val HEADING_ANIMATION_ALPHA = 0.5f
private const val FAST_TURN_ANIMATION_ALPHA = 0.78f
private const val FAST_TURN_LARGE_ERROR_ALPHA = 0.9f
private const val FAST_TURN_LARGE_ERROR_DEG = 25f
private const val FAST_TURN_MIN_RATE_DEG_PER_SEC = 55f
private const val ACTIVE_MAP_TURN_MIN_RATE_DEG_PER_SEC = 24f
private const val FAST_TURN_MAX_SAMPLE_GAP_MS = 250L
private const val FAST_TURN_RENDER_HOLD_MS = 180L
private const val ACTIVE_MAP_TURN_RENDER_HOLD_MS = 180L
private const val HEADING_ANIMATION_MAX_STEP_DEG = 12f
private const val COMPASS_STARTUP_HANDOFF_MAX_RATE_DEG_PER_SEC = 120f
private const val COMPASS_STARTUP_HANDOFF_COMPLETE_DELTA_DEG = 2f
private const val COMPASS_STARTUP_HANDOFF_MIN_DURATION_MS = 500L
private const val COMPASS_ANIMATION_MAX_FRAME_GAP_MS = 50L
private const val SETTLING_MAX_TURN_RATE_DEG_PER_SEC = 240f
private const val SETTLING_MIN_PLAUSIBLE_DELTA_DEG = 4f
private const val SETTLING_MAX_PLAUSIBLE_DELTA_DEG = 12f
private const val SETTLING_MAX_SAMPLE_GAP_MS = 250L
private const val SETTLING_REJECT_TELEMETRY_MIN_DEG = 4f

// Stop animating when within this threshold — below the useful visual precision of a watch map.
private const val HEADING_ANIMATION_DONE_DEG = 0.2f
private const val GOOGLE_FUSED_CACHED_HEADING_SEED_MAX_AGE_MS = 30_000L
private const val COMPASS_RENDER_PERF_LOG_WINDOW_MS = 5_000L
private const val MAP_CENTER_UPDATE_EPSILON_DEG2 = 1e-11

private fun Float.formatTelemetry(decimals: Int): String = "%.${decimals}f".format(Locale.US, this)

private fun shouldUpdateMapCenter(
    target: LatLong,
    current: LatLong?,
): Boolean {
    val center = current ?: return true
    val dLat = target.latitude - center.latitude
    val dLon = target.longitude - center.longitude
    return (dLat * dLat + dLon * dLon) >= MAP_CENTER_UPDATE_EPSILON_DEG2
}

private fun MapView.trySetMapsforgeRotation(
    degrees: Float,
    anchor: ScreenAnchor,
): Boolean {
    if (width <= 0 || height <= 0) return false
    rotate(Rotation(degrees, anchor.x.toFloat(), anchor.y.toFloat()))
    return true
}

/**
 * Theme application (optional).
 */
@Composable
fun MapThemeEffect(
    mapRenderer: MapRenderer?,
    themeKey: String,
    themeFile: File?,
) {
    LaunchedEffect(mapRenderer, themeKey) {
        val renderer = mapRenderer ?: return@LaunchedEffect
        renderer.setThemeConfig(
            themeFile = themeFile,
            mapsforgeThemeName = null,
            bundledThemeId = MapsforgeThemeCatalog.ELEVATE_THEME_ID,
            hillShadingEnabled = false,
            reliefOverlayEnabled = false,
        )
    }
}
