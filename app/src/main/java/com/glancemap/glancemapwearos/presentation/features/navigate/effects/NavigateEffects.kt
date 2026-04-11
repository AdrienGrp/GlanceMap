package com.glancemap.glancemapwearos.presentation.features.navigate

import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.glancemap.glancemapwearos.domain.model.maps.theme.mapsforge.MapsforgeThemeCatalog
import com.glancemap.glancemapwearos.domain.sensors.CompassProviderType
import com.glancemap.glancemapwearos.domain.sensors.CompassRenderState
import com.glancemap.glancemapwearos.domain.sensors.HeadingSource
import com.glancemap.glancemapwearos.presentation.features.maps.MapRenderer
import com.glancemap.glancemapwearos.presentation.features.maps.RotatableMarker
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.mapsforge.core.model.Rotation
import org.mapsforge.map.android.view.MapView
import java.io.File
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
    onRenderedHeadingChanged: (Float) -> Unit,
    onRenderedMapRotationChanged: (Float) -> Unit,
    requestMapRedraw: () -> Unit,
) {
    val mv = mapView ?: return
    val marker = locationMarker

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

    fun syncDisplayedMapRotationFromMap(): Float {
        val actualRotationDeg = mv.mapRotation.degrees
        displayedMapRot.floatValue = actualRotationDeg
        return actualRotationDeg
    }

    fun applyMapRotation(targetRotationDeg: Float) {
        val currentRotationDeg = syncDisplayedMapRotationFromMap()
        if (abs(angleDeltaDeg(targetRotationDeg, currentRotationDeg)) < MAP_ROTATION_APPLY_EPSILON_DEG) {
            onRenderedMapRotationChanged(currentRotationDeg)
            return
        }
        if (mv.trySetMapsforgeRotation(targetRotationDeg)) {
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
    ) {
        val renderStateNow = renderStateFlow.value
        val headingNow = normalize360(renderStateNow.headingDeg)
        val shouldSeedCachedHeading =
            navMode == NavMode.COMPASS_FOLLOW &&
                shouldSeedCompassFollowMapWithCachedHeading(
                    renderState = renderStateNow,
                    nowElapsedMs = SystemClock.elapsedRealtime(),
                )
        if (
            navMode != NavMode.COMPASS_FOLLOW ||
            shouldDriveCompassFollowMap(renderStateNow) ||
            shouldSeedCachedHeading
        ) {
            displayedHeading.floatValue = headingNow
            onRenderedHeadingChanged(headingNow)
        }

        when (navMode) {
            NavMode.COMPASS_FOLLOW -> {
                if (shouldDriveCompassFollowMap(renderStateNow) || shouldSeedCachedHeading) {
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
                    if (navMode != NavMode.COMPASS_FOLLOW || shouldDriveCompassFollowMap(latestRenderState)) {
                        liveTarget = heading
                    }
                }
        }

        // Animate toward liveTarget on every display frame.
        while (true) {
            withFrameNanos {
                if (navMode == NavMode.PANNING) return@withFrameNanos
                if (navMode == NavMode.COMPASS_FOLLOW &&
                    !shouldDriveCompassFollowMap(latestRenderState)
                ) {
                    return@withFrameNanos
                }
                val current = displayedHeading.floatValue
                val diff = angleDeltaDeg(liveTarget, current)
                if (abs(diff) < HEADING_ANIMATION_DONE_DEG) return@withFrameNanos

                val next = normalize360(current + diff * HEADING_ANIMATION_ALPHA)
                displayedHeading.floatValue = next
                onRenderedHeadingChanged(next)

                when (navMode) {
                    NavMode.COMPASS_FOLLOW -> {
                        applyMapRotation(-next)
                        applyMarkersForMode(navMode)
                    }
                    NavMode.NORTH_UP_FOLLOW -> {
                        applyMapRotation(0f)
                        applyMarkersForMode(navMode)
                    }
                    NavMode.PANNING -> Unit
                }
                requestMapRedraw()
            }
        }
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

internal fun shouldSeedCompassFollowMapWithCachedHeading(
    renderState: CompassRenderState,
    nowElapsedMs: Long,
): Boolean {
    if (renderState.providerType != CompassProviderType.GOOGLE_FUSED) return false
    val sampleAtElapsedMs = renderState.headingSampleElapsedRealtimeMs ?: return false
    if (!renderState.headingDeg.isFinite()) return false
    val ageMs = (nowElapsedMs - sampleAtElapsedMs).coerceAtLeast(0L)
    return ageMs <= GOOGLE_FUSED_CACHED_HEADING_SEED_MAX_AGE_MS
}

private fun normalize360(deg: Float): Float = (deg % 360f + 360f) % 360f

private const val MAP_ROTATION_APPLY_EPSILON_DEG = 0.05f

// Interpolation factor per display frame (~60fps). At 0.5, closes half the remaining
// gap each frame: a 10° step reaches <0.1° in ~7 frames (~117ms). Tracks 50Hz sensor
// updates with at most 1-2 frames of visual lag.
private const val HEADING_ANIMATION_ALPHA = 0.5f

// Stop animating when within this threshold — sub-pixel on any WearOS display.
private const val HEADING_ANIMATION_DONE_DEG = 0.05f
private const val GOOGLE_FUSED_CACHED_HEADING_SEED_MAX_AGE_MS = 30_000L

private fun MapView.trySetMapsforgeRotation(degrees: Float): Boolean {
    if (width <= 0 || height <= 0) return false
    val px = width * 0.5f
    val py = height * 0.5f
    rotate(Rotation(degrees, px, py))
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
