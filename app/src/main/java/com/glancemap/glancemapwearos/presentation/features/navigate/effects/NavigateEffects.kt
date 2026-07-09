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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    fun applyMapRotation(targetRotationDeg: Float) {
        recenterLowerMarkerAnchor()
        val currentRotationDeg = syncDisplayedMapRotationFromMap()
        if (abs(angleDeltaDeg(targetRotationDeg, currentRotationDeg)) < MAP_ROTATION_APPLY_EPSILON_DEG) {
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
                    onRenderedMapRotationChanged(syncDisplayedMapRotationFromMap())
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
        var liveTarget = normalize360(renderStateFlow.value.headingDeg)
        var latestRenderState = renderStateFlow.value

        // Keep liveTarget current without blocking the animation loop.
        launch {
            renderStateFlow
                .map { state ->
                    latestRenderState = state
                    normalize360(state.headingDeg)
                }.distinctUntilChanged()
                .collect { heading ->
                    if (shouldDriveHeadingForNavMode(navMode, latestRenderState)) {
                        liveTarget = heading
                        CompassRenderPerfTelemetry.recordTargetUpdate(navMode)
                    }
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
                val current = displayedHeading.floatValue
                val diff = angleDeltaDeg(liveTarget, current)
                if (abs(diff) < HEADING_ANIMATION_DONE_DEG) return@withFrameNanos

                val next = normalize360(current + diff * HEADING_ANIMATION_ALPHA)
                displayedHeading.floatValue = next
                onRenderedHeadingChanged(next)
                CompassRenderPerfTelemetry.recordHeadingRender(navMode)

                when (navMode) {
                    NavMode.COMPASS_FOLLOW -> {
                        applyMapRotation(-next)
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
        if (renderState.headingSource == HeadingSource.FUSED_ORIENTATION) {
            if (renderState.headingSampleStale || renderState.headingSampleElapsedRealtimeMs == null) {
                return false
            }
        } else {
            return true
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
): Boolean =
    navMode == NavMode.COMPASS_FOLLOW &&
        lastAppliedAtElapsedMs != Long.MIN_VALUE &&
        nowElapsedMs - lastAppliedAtElapsedMs < MAP_ROTATION_MIN_APPLY_INTERVAL_MS

// Small heading noise is visible as left/right map shimmer in compass-follow.
// Keep the compass pipeline responsive, but avoid applying sub-degree Mapsforge rotations.
private const val MAP_ROTATION_APPLY_EPSILON_DEG = 0.8f

// Animate the Compose heading every display frame, but avoid asking Mapsforge to redraw/rotate
// more than 30 times per second. This preserves the compass pipeline while reducing map work.
private const val MAP_ROTATION_MIN_APPLY_INTERVAL_MS = 33L

// Interpolation factor per display frame (~60fps). At 0.5, closes half the remaining
// gap each frame: a 10° step reaches <0.1° in ~7 frames (~117ms). Tracks 50Hz sensor
// updates with at most 1-2 frames of visual lag.
private const val HEADING_ANIMATION_ALPHA = 0.5f

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
