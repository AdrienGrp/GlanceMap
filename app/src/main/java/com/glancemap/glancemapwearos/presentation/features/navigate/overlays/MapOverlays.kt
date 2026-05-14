package com.glancemap.glancemapwearos.presentation.features.navigate

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.glancemap.glancemapwearos.data.repository.PoiType
import com.glancemap.glancemapwearos.data.repository.PoiViewport
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.domain.sensors.CompassRenderState
import com.glancemap.glancemapwearos.presentation.features.gpx.GpxInspectionUiState
import com.glancemap.glancemapwearos.presentation.features.gpx.GpxTrackDetails
import com.glancemap.glancemapwearos.presentation.features.gpx.InspectionABUiState
import com.glancemap.glancemapwearos.presentation.features.gpx.InspectionAUiState
import com.glancemap.glancemapwearos.presentation.features.maps.GpxInspectionPopupA
import com.glancemap.glancemapwearos.presentation.features.maps.GpxInspectionPopupAB
import com.glancemap.glancemapwearos.presentation.features.maps.MapHolder
import com.glancemap.glancemapwearos.presentation.features.maps.RotatableMarker
import com.glancemap.glancemapwearos.presentation.features.navigate.coverage.OfflineCoverageLayer
import com.glancemap.glancemapwearos.presentation.features.navigate.coverage.OfflineMapCoverageArea
import com.glancemap.glancemapwearos.presentation.features.poi.PoiOverlaySource
import com.glancemap.glancemapwearos.presentation.features.poi.PoiViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.withContext
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidBitmap
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Marker
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.map.model.common.Observer

private const val ELEVATION_TRACK_OUTLINE_ALPHA = 176
private const val ELEVATION_TRACK_OUTLINE_WIDTH_EXTRA_PX = 3f

@Composable
@OptIn(FlowPreview::class)
@Suppress("FunctionNaming", "LongMethod", "LongParameterList")
internal fun MapOverlays(
    mapHolder: MapHolder,
    activeGpxDetails: List<GpxTrackDetails>,
    routeToolPreviewPoints: List<LatLong>,
    routeToolCreatePreviewActive: Boolean,
    routeToolDraftPoints: List<LatLong>,
    poiViewModel: PoiViewModel,
    activePoiOverlaySources: List<PoiOverlaySource>,
    offlineCoverageAreas: List<OfflineMapCoverageArea>,
    poiMarkerSizePx: Int,
    gpxTrackColor: Int,
    gpxTrackColorMode: String,
    gpxTrackWidth: Float,
    gpxTrackOpacityPercent: Int,
    compassRenderStateFlow: StateFlow<CompassRenderState>,
    navMode: NavMode,
    forceNorthUpInPanning: Boolean,
    showRealMarkerInCompassMode: Boolean,
    showCompassConeOverlay: Boolean,
    compassConeBaseSizePx: Int,
    compassQuality: CompassMarkerQuality,
    compassHeadingErrorDeg: Float?,
    gpsAccuracyCircleEnabled: Boolean,
    gpsFixAccuracyM: Float,
    gpsFixFresh: Boolean,
    renderedHeadingDeg: Float,
    locationMarker: RotatableMarker?,
    inspectionUiState: GpxInspectionUiState?,
    selectedPointA: LatLong?,
    selectedPointB: LatLong?,
    onDismissInspection: () -> Unit,
    onStartSelectB: () -> Unit,
    isMetric: Boolean,
    onRenderedHeadingChanged: (Float) -> Unit,
    onRenderedMapRotationChanged: (Float) -> Unit,
    onPoiMarkersSnapshotChanged: (List<com.glancemap.glancemapwearos.presentation.features.poi.PoiOverlayMarker>) -> Unit,
) {
    val mapView = mapHolder.mapView
    val gpsAccuracyCircleLayer =
        remember(mapView) {
            val fill =
                AndroidGraphicFactory.INSTANCE.createPaint().apply {
                    setStyle(Style.FILL)
                    color = Color.argb(54, 66, 153, 245)
                }
            val stroke =
                AndroidGraphicFactory.INSTANCE.createPaint().apply {
                    setStyle(Style.STROKE)
                    color = Color.argb(176, 21, 101, 192)
                    strokeWidth = 2f
                }
            GpsAccuracyCircleLayer(
                fillPaint = fill,
                strokePaint = stroke,
            )
        }
    val compassConeLayer =
        remember(mapView) {
            findExistingCompassConeLayer(mapView) ?: CompassConeLayer()
        }
    val offlineCoverageLayer =
        remember(mapView) {
            OfflineCoverageLayer(
                poiFillPaint =
                    AndroidGraphicFactory.INSTANCE.createPaint().apply {
                        setStyle(Style.FILL)
                        color = Color.argb(38, 42, 177, 109)
                    },
                poiStrokePaint =
                    AndroidGraphicFactory.INSTANCE.createPaint().apply {
                        setStyle(Style.STROKE)
                        color = Color.argb(186, 42, 177, 109)
                        strokeWidth = 2f
                    },
                routingFillPaint =
                    AndroidGraphicFactory.INSTANCE.createPaint().apply {
                        setStyle(Style.FILL)
                        color = Color.argb(34, 90, 128, 255)
                    },
                routingStrokePaint =
                    AndroidGraphicFactory.INSTANCE.createPaint().apply {
                        setStyle(Style.STROKE)
                        color = Color.argb(186, 90, 128, 255)
                        strokeWidth = 2f
                    },
            )
        }
    val markerAHolder = remember(mapView) { arrayOfNulls<Marker>(1) }
    val markerBHolder = remember(mapView) { arrayOfNulls<Marker>(1) }
    val topOverlayCoordinator =
        remember(
            mapView,
            gpsAccuracyCircleLayer,
            compassConeLayer,
            markerAHolder,
            markerBHolder,
        ) {
            MapTopOverlayCoordinator(
                layers = mapView.layerManager.layers,
                accuracyCircleLayer = gpsAccuracyCircleLayer,
                coneLayer = compassConeLayer,
                markerAHolder = markerAHolder,
                markerBHolder = markerBHolder,
            )
        }
    val redrawSignals =
        remember(mapView) {
            MutableSharedFlow<Unit>(
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        }
    val requestMapRedraw =
        remember(redrawSignals) {
            {
                redrawSignals.tryEmit(Unit)
                Unit
            }
        }

    LaunchedEffect(mapView, redrawSignals, navMode) {
        val frameBudgetMs =
            when (navMode) {
                NavMode.COMPASS_FOLLOW -> 40L
                NavMode.NORTH_UP_FOLLOW -> 40L
                NavMode.PANNING -> 50L
            }
        redrawSignals
            .sample(frameBudgetMs)
            .collect { mapView.requestLayerRedrawSafely() }
    }

    LaunchedEffect(locationMarker) {
        topOverlayCoordinator.updateLocationMarker(locationMarker)
    }

    NavigationOrientationEffect(
        isCompassMode = navMode == NavMode.COMPASS_FOLLOW,
        isAutoCentering = navMode != NavMode.PANNING,
        forceNorthUpInPanning = forceNorthUpInPanning,
        renderStateFlow = compassRenderStateFlow,
        mapView = mapView,
        showRealMarkerInCompassMode = showRealMarkerInCompassMode,
        locationMarker = locationMarker,
        onRenderedHeadingChanged = onRenderedHeadingChanged,
        onRenderedMapRotationChanged = onRenderedMapRotationChanged,
        requestMapRedraw = requestMapRedraw,
    )

    GpsAccuracyCircleLayerEffect(
        mapView = mapView,
        gpsAccuracyCircleEnabled = gpsAccuracyCircleEnabled,
        gpsFixAccuracyM = gpsFixAccuracyM,
        gpsFixFresh = gpsFixFresh,
        locationMarker = locationMarker,
        accuracyCircleLayer = gpsAccuracyCircleLayer,
        topOverlayCoordinator = topOverlayCoordinator,
        requestMapRedraw = requestMapRedraw,
    )

    offlineCoverageLayerEffect(
        mapView = mapView,
        navMode = navMode,
        coverageAreas = offlineCoverageAreas,
        coverageLayer = offlineCoverageLayer,
        requestMapRedraw = requestMapRedraw,
    )

    CompassConeLayerEffect(
        mapView = mapView,
        navMode = navMode,
        showCompassConeOverlay = showCompassConeOverlay,
        compassConeBaseSizePx = compassConeBaseSizePx,
        compassQuality = compassQuality,
        compassHeadingErrorDeg = compassHeadingErrorDeg,
        renderedHeadingDeg = renderedHeadingDeg,
        locationMarker = locationMarker,
        topOverlayCoordinator = topOverlayCoordinator,
        coneLayer = compassConeLayer,
        requestMapRedraw = requestMapRedraw,
    )

    PoiOverlayEffect(
        mapView = mapView,
        poiViewModel = poiViewModel,
        activePoiOverlaySources = activePoiOverlaySources,
        poiMarkerSizePx = poiMarkerSizePx,
        requestMapRedraw = requestMapRedraw,
        onPoiMarkersSnapshotChanged = onPoiMarkersSnapshotChanged,
        locationMarker = locationMarker,
        topOverlayCoordinator = topOverlayCoordinator,
    )

    GpxAndInspectionOverlayEffect(
        mapView = mapView,
        activeGpxDetails = activeGpxDetails,
        routeToolPreviewPoints = routeToolPreviewPoints,
        routeToolCreatePreviewActive = routeToolCreatePreviewActive,
        routeToolDraftPoints = routeToolDraftPoints,
        gpxTrackColor = gpxTrackColor,
        gpxTrackColorMode = gpxTrackColorMode,
        gpxTrackWidth = gpxTrackWidth,
        gpxTrackOpacityPercent = gpxTrackOpacityPercent,
        locationMarker = locationMarker,
        selectedPointA = selectedPointA,
        selectedPointB = selectedPointB,
        markerAHolder = markerAHolder,
        markerBHolder = markerBHolder,
        topOverlayCoordinator = topOverlayCoordinator,
        requestMapRedraw = requestMapRedraw,
    )

    inspectionUiState?.let { ui ->
        when (ui) {
            is InspectionAUiState ->
                GpxInspectionPopupA(
                    state = ui,
                    onDismiss = onDismissInspection,
                    onSelectB = onStartSelectB,
                    isMetric = isMetric,
                )

            is InspectionABUiState ->
                GpxInspectionPopupAB(
                    state = ui,
                    onDismiss = onDismissInspection,
                    isMetric = isMetric,
                )
        }
    }
}

@Composable
private fun GpsAccuracyCircleLayerEffect(
    mapView: MapView,
    gpsAccuracyCircleEnabled: Boolean,
    gpsFixAccuracyM: Float,
    gpsFixFresh: Boolean,
    locationMarker: RotatableMarker?,
    accuracyCircleLayer: GpsAccuracyCircleLayer,
    topOverlayCoordinator: MapTopOverlayCoordinator,
    requestMapRedraw: () -> Unit,
) {
    val layers = mapView.layerManager.layers
    val clampedAccuracyMeters = sanitizeGpsAccuracyMeters(gpsFixAccuracyM)
    val shouldShow =
        gpsAccuracyCircleEnabled &&
            gpsFixFresh &&
            clampedAccuracyMeters != null &&
            locationMarker != null

    LaunchedEffect(
        mapView,
        gpsAccuracyCircleEnabled,
        gpsFixAccuracyM,
        gpsFixFresh,
        locationMarker,
    ) {
        mapView.post {
            val hasLayer = layers.contains(accuracyCircleLayer)
            if (!hasLayer) {
                layers.add(accuracyCircleLayer)
            }
            accuracyCircleLayer.anchorMarker = locationMarker
            clampedAccuracyMeters?.let { safeRadius ->
                accuracyCircleLayer.radiusMeters = safeRadius
            }
            accuracyCircleLayer.isVisible = shouldShow
            val reordered = topOverlayCoordinator.sync()
            if (!hasLayer || reordered) {
                requestMapRedraw()
            } else {
                mapView.requestLayerRedrawSafely()
            }
        }
    }

    DisposableEffect(mapView) {
        onDispose {
            mapView.post {
                layers.remove(accuracyCircleLayer)
                mapView.requestLayerRedrawSafely()
            }
        }
    }
}

@Composable
private fun offlineCoverageLayerEffect(
    mapView: MapView,
    navMode: NavMode,
    coverageAreas: List<OfflineMapCoverageArea>,
    coverageLayer: OfflineCoverageLayer,
    requestMapRedraw: () -> Unit,
) {
    val layers = mapView.layerManager.layers
    val shouldShow = navMode == NavMode.PANNING && coverageAreas.isNotEmpty()

    LaunchedEffect(mapView, navMode, coverageAreas) {
        mapView.post {
            val hasLayer = layers.contains(coverageLayer)
            if (!hasLayer) {
                layers.add(coverageLayer)
            }
            coverageLayer.areas = coverageAreas
            coverageLayer.isVisible = shouldShow
            requestMapRedraw()
        }
    }

    DisposableEffect(mapView) {
        onDispose {
            mapView.post {
                layers.remove(coverageLayer)
                coverageLayer.areas = emptyList()
                mapView.requestLayerRedrawSafely()
            }
        }
    }
}

@Composable
private fun CompassConeLayerEffect(
    mapView: MapView,
    navMode: NavMode,
    showCompassConeOverlay: Boolean,
    compassConeBaseSizePx: Int,
    compassQuality: CompassMarkerQuality,
    compassHeadingErrorDeg: Float?,
    renderedHeadingDeg: Float,
    locationMarker: RotatableMarker?,
    topOverlayCoordinator: MapTopOverlayCoordinator,
    coneLayer: CompassConeLayer,
    requestMapRedraw: () -> Unit,
) {
    val layers = mapView.layerManager.layers
    val shouldShow =
        showCompassConeOverlay &&
            locationMarker != null &&
            (navMode == NavMode.COMPASS_FOLLOW || navMode == NavMode.NORTH_UP_FOLLOW)

    LaunchedEffect(
        mapView,
        navMode,
        showCompassConeOverlay,
        compassConeBaseSizePx,
        compassQuality,
        compassHeadingErrorDeg,
        renderedHeadingDeg,
        locationMarker,
    ) {
        mapView.post {
            val hasLayer = layers.contains(coneLayer)
            if (!hasLayer) {
                layers.add(coneLayer)
            }
            coneLayer.anchorMarker = locationMarker
            coneLayer.baseMarkerSizePx = compassConeBaseSizePx
            coneLayer.quality = compassQuality
            coneLayer.headingErrorDeg = compassHeadingErrorDeg
            coneLayer.headingDeg =
                when (navMode) {
                    NavMode.COMPASS_FOLLOW -> 0f
                    NavMode.NORTH_UP_FOLLOW -> renderedHeadingDeg
                    NavMode.PANNING -> 0f
                }
            coneLayer.isVisible = shouldShow
            val reordered = topOverlayCoordinator.sync()
            if (!hasLayer || reordered) {
                requestMapRedraw()
            } else {
                mapView.requestLayerRedrawSafely()
            }
        }
    }

    DisposableEffect(mapView) {
        onDispose {
            mapView.post {
                coneLayer.anchorMarker = null
                coneLayer.isVisible = false
                mapView.requestLayerRedrawSafely()
            }
        }
    }
}

private fun findExistingCompassConeLayer(mapView: MapView): CompassConeLayer? =
    mapView.layerManager.layers
        .firstOrNull { it is CompassConeLayer } as? CompassConeLayer

@Composable
@OptIn(FlowPreview::class)
private fun PoiOverlayEffect(
    mapView: MapView,
    poiViewModel: PoiViewModel,
    activePoiOverlaySources: List<PoiOverlaySource>,
    poiMarkerSizePx: Int,
    requestMapRedraw: () -> Unit,
    onPoiMarkersSnapshotChanged: (List<com.glancemap.glancemapwearos.presentation.features.poi.PoiOverlayMarker>) -> Unit,
    locationMarker: RotatableMarker?,
    topOverlayCoordinator: MapTopOverlayCoordinator,
) {
    val layers = mapView.layerManager.layers
    val markersByKey = remember(mapView) { mutableMapOf<String, PoiMarkerEntry>() }
    val iconSizePx =
        remember(poiMarkerSizePx) {
            (poiMarkerSizePx * 0.72f).toInt().coerceAtLeast(12)
        }
    val markerBitmapByType =
        remember(mapView, poiMarkerSizePx, iconSizePx) {
            PoiType.entries.associateWith { type ->
                val osmIcon = loadOsmPoiIconBitmapOrNull(mapView, type, sizePx = iconSizePx)
                AndroidBitmap(createPoiTypeMarkerBitmap(type, osmIcon, sizePx = poiMarkerSizePx))
            }
        }
    val latestSources = rememberUpdatedState(activePoiOverlaySources)
    val querySignals =
        remember(mapView) {
            MutableSharedFlow<Unit>(
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        }
    val requestQuery =
        remember(querySignals) {
            {
                querySignals.tryEmit(Unit)
                Unit
            }
        }

    fun clearAllMarkers() {
        mapView.post {
            if (markersByKey.isEmpty()) return@post
            markersByKey.values.forEach { entry -> layers.remove(entry.marker) }
            markersByKey.clear()
            requestMapRedraw()
        }
        onPoiMarkersSnapshotChanged(emptyList())
    }

    LaunchedEffect(activePoiOverlaySources, poiMarkerSizePx) {
        if (activePoiOverlaySources.isEmpty()) {
            clearAllMarkers()
        } else {
            requestQuery()
        }
    }

    LaunchedEffect(locationMarker) {
        mapView.post {
            val reordered = topOverlayCoordinator.sync()
            if (reordered) {
                requestMapRedraw()
            }
        }
    }

    DisposableEffect(mapView) {
        val observer = Observer { requestQuery() }
        mapView.model.mapViewPosition.addObserver(observer)
        requestQuery()
        onDispose { mapView.model.mapViewPosition.removeObserver(observer) }
    }

    LaunchedEffect(mapView, querySignals) {
        querySignals
            .sample(320L)
            .collect {
                if (latestSources.value.isEmpty()) {
                    clearAllMarkers()
                    return@collect
                }

                val width = mapView.width
                val height = mapView.height
                if (width <= 0 || height <= 0) return@collect

                val corners =
                    listOf(
                        runCatching { mapView.mapViewProjection.fromPixels(0.0, 0.0) }.getOrNull(),
                        runCatching { mapView.mapViewProjection.fromPixels(width.toDouble(), 0.0) }.getOrNull(),
                        runCatching { mapView.mapViewProjection.fromPixels(0.0, height.toDouble()) }.getOrNull(),
                        runCatching {
                            mapView.mapViewProjection.fromPixels(width.toDouble(), height.toDouble())
                        }.getOrNull(),
                    ).filterNotNull()
                if (corners.isEmpty()) return@collect

                val minLat = corners.minOf { it.latitude }
                val maxLat = corners.maxOf { it.latitude }
                val minLon = corners.minOf { it.longitude }
                val maxLon = corners.maxOf { it.longitude }
                val zoom =
                    mapView.model.mapViewPosition.zoomLevel
                        .toInt()

                val markers =
                    withContext(Dispatchers.IO) {
                        poiViewModel.queryVisibleMarkers(
                            viewport =
                                PoiViewport(
                                    minLat = minLat,
                                    maxLat = maxLat,
                                    minLon = minLon,
                                    maxLon = maxLon,
                                ),
                            zoomLevel = zoom,
                        )
                    }
                onPoiMarkersSnapshotChanged(markers)

                mapView.post {
                    val wantedKeys = markers.map { it.key }.toSet()
                    var changed = false

                    (markersByKey.keys - wantedKeys).forEach { key ->
                        markersByKey.remove(key)?.let { entry ->
                            layers.remove(entry.marker)
                            changed = true
                        }
                    }

                    markers.forEach { point ->
                        val latLong = LatLong(point.lat, point.lon)
                        val existing = markersByKey[point.key]
                        val bitmap =
                            markerBitmapByType[point.type]
                                ?: markerBitmapByType[PoiType.GENERIC]
                                ?: return@forEach
                        if (existing == null) {
                            val marker = Marker(latLong, bitmap, 0, 0)
                            markersByKey[point.key] = PoiMarkerEntry(marker, point.type, poiMarkerSizePx)
                            layers.add(marker)
                            changed = true
                        } else {
                            if (existing.type != point.type || existing.markerSizePx != poiMarkerSizePx) {
                                layers.remove(existing.marker)
                                val marker = Marker(latLong, bitmap, 0, 0)
                                markersByKey[point.key] =
                                    PoiMarkerEntry(
                                        marker = marker,
                                        type = point.type,
                                        markerSizePx = poiMarkerSizePx,
                                    )
                                layers.add(marker)
                                changed = true
                            } else if (setMarkerLatLongIfChanged(existing.marker, latLong)) {
                                changed = true
                            }
                        }
                    }

                    val reordered = topOverlayCoordinator.sync()

                    if (changed || reordered) {
                        requestMapRedraw()
                    }
                }
            }
    }

    DisposableEffect(mapView) {
        onDispose {
            onPoiMarkersSnapshotChanged(emptyList())
            mapView.post {
                markersByKey.values.forEach { entry -> layers.remove(entry.marker) }
                markersByKey.clear()
                mapView.requestLayerRedrawSafely()
            }
        }
    }
}

@Composable
@Suppress("CyclomaticComplexMethod", "FunctionNaming", "LongMethod", "LongParameterList")
private fun GpxAndInspectionOverlayEffect(
    mapView: MapView,
    activeGpxDetails: List<GpxTrackDetails>,
    routeToolPreviewPoints: List<LatLong>,
    routeToolCreatePreviewActive: Boolean,
    routeToolDraftPoints: List<LatLong>,
    gpxTrackColor: Int,
    gpxTrackColorMode: String,
    gpxTrackWidth: Float,
    gpxTrackOpacityPercent: Int,
    locationMarker: RotatableMarker?,
    selectedPointA: LatLong?,
    selectedPointB: LatLong?,
    markerAHolder: Array<Marker?>,
    markerBHolder: Array<Marker?>,
    topOverlayCoordinator: MapTopOverlayCoordinator,
    requestMapRedraw: () -> Unit,
) {
    val layers = mapView.layerManager.layers
    val useElevationTrackColors = gpxTrackColorMode == SettingsRepository.GPX_TRACK_COLOR_MODE_ELEVATION

    val trackPaint =
        remember {
            AndroidGraphicFactory.INSTANCE.createPaint().apply { setStyle(Style.STROKE) }
        }
    val previewPaint =
        remember {
            AndroidGraphicFactory.INSTANCE.createPaint().apply { setStyle(Style.STROKE) }
        }
    val draftPaint =
        remember {
            AndroidGraphicFactory.INSTANCE.createPaint().apply { setStyle(Style.STROKE) }
        }

    // Stable caches
    val polylinesById = remember(mapView) { mutableMapOf<String, Polyline>() }
    val elevationPolylinesById = remember(mapView) { mutableMapOf<String, List<Polyline>>() }
    val startMarkersById = remember(mapView) { mutableMapOf<String, Marker>() }
    val endMarkersById = remember(mapView) { mutableMapOf<String, Marker>() }
    val lodById = remember(mapView) { mutableMapOf<String, TrackLodLevels>() }
    val displayedLodBucketById = remember(mapView) { mutableMapOf<String, Int>() }
    val previewPolyline =
        remember(mapView) {
            Polyline(previewPaint, AndroidGraphicFactory.INSTANCE)
        }
    val draftPolyline =
        remember(mapView) {
            Polyline(draftPaint, AndroidGraphicFactory.INSTANCE)
        }

    // Bitmaps
    val markerBitmapA = remember { AndroidBitmap(makeLabeledYellowDotBitmap("A", 28, 3, 235)) }
    val markerBitmapB = remember { AndroidBitmap(makeLabeledYellowDotBitmap("B", 28, 3, 190)) }
    val startBitmap =
        remember {
            AndroidBitmap(
                makeLabeledDotBitmap(
                    label = "S",
                    sizePx = 20,
                    strokePx = 2,
                    fillColorArgb = android.graphics.Color.rgb(76, 175, 80),
                ),
            )
        }
    val endBitmap =
        remember {
            AndroidBitmap(
                makeLabeledDotBitmap(
                    label = "E",
                    sizePx = 20,
                    strokePx = 2,
                    fillColorArgb = android.graphics.Color.rgb(244, 67, 54),
                ),
            )
        }

    LaunchedEffect(gpxTrackColor, gpxTrackWidth, gpxTrackOpacityPercent, routeToolCreatePreviewActive) {
        trackPaint.color =
            applyOpacityToColor(
                color = gpxTrackColor,
                opacityPercent = gpxTrackOpacityPercent,
            )
        trackPaint.strokeWidth = gpxTrackWidth
        previewPaint.color =
            if (routeToolCreatePreviewActive) {
                applyOpacityToColor(
                    color = gpxTrackColor,
                    opacityPercent = maxOf(gpxTrackOpacityPercent, 88),
                )
            } else {
                Color.argb(228, 247, 201, 72)
            }
        previewPaint.strokeWidth = maxOf(gpxTrackWidth + 2f, 6f)
        draftPaint.color =
            applyOpacityToColor(
                color = gpxTrackColor,
                opacityPercent = maxOf(gpxTrackOpacityPercent, 74),
            )
        draftPaint.strokeWidth = maxOf(gpxTrackWidth, 4f)
        requestMapRedraw()
    }

    DisposableEffect(mapView, useElevationTrackColors, gpxTrackWidth, gpxTrackOpacityPercent) {
        val observer =
            Observer {
                val zoomNow =
                    mapView.model.mapViewPosition.zoomLevel
                        .toInt()
                val newBucket = zoomBucketFor(zoomNow)
                var changed = false

                lodById.forEach { (id, lod) ->
                    if (displayedLodBucketById[id] == newBucket) return@forEach
                    val renderPoints = lod.pointsForZoom(zoomNow)

                    if (useElevationTrackColors) {
                        elevationPolylinesById.remove(id)?.forEach { layers.remove(it) }
                        val segments =
                            buildElevationTrackSegments(
                                points = renderPoints,
                                opacityPercent = gpxTrackOpacityPercent,
                            )
                        elevationPolylinesById[id] =
                            createElevationTrackPolylines(
                                segments = segments,
                                strokeWidth = gpxTrackWidth,
                            ).also { polylines ->
                                polylines.forEach(layers::add)
                            }
                        changed = true
                    } else {
                        val polyline = polylinesById[id] ?: return@forEach
                        val renderLatLongs = renderPoints.latLongs()
                        if (!hasSameLatLongs(polyline.latLongs, renderLatLongs)) {
                            polyline.latLongs.clear()
                            polyline.latLongs.addAll(renderLatLongs)
                            changed = true
                        }
                    }
                    displayedLodBucketById[id] = newBucket
                }

                val reordered = if (changed) topOverlayCoordinator.sync() else false
                if (changed || reordered) requestMapRedraw()
            }

        mapView.model.mapViewPosition.addObserver(observer)
        onDispose { mapView.model.mapViewPosition.removeObserver(observer) }
    }

    // Update polylines + S/E markers
    LaunchedEffect(activeGpxDetails, useElevationTrackColors, gpxTrackWidth, gpxTrackOpacityPercent) {
        val wantedIds = activeGpxDetails.map { it.id }.toSet()
        val computedLodById =
            withContext(Dispatchers.Default) {
                activeGpxDetails.associate { details ->
                    details.id to buildTrackLodLevels(details.trackPoints)
                }
            }

        mapView.post {
            var changed = false
            // remove old layers
            (polylinesById.keys - wantedIds).forEach { id ->
                if (polylinesById.remove(id)?.let {
                        layers.remove(it)
                        true
                    } == true
                ) {
                    changed = true
                }
                elevationPolylinesById.remove(id)?.forEach { polyline ->
                    layers.remove(polyline)
                    changed = true
                }
                if (startMarkersById.remove(id)?.let {
                        layers.remove(it)
                        true
                    } == true
                ) {
                    changed = true
                }
                if (endMarkersById.remove(id)?.let {
                        layers.remove(it)
                        true
                    } == true
                ) {
                    changed = true
                }
                lodById.remove(id)
                displayedLodBucketById.remove(id)
            }

            // add/update polylines and S/E markers
            val zoomNow =
                mapView.model.mapViewPosition.zoomLevel
                    .toInt()
            val currentBucket = zoomBucketFor(zoomNow)
            activeGpxDetails.forEach { details ->
                val lod = computedLodById[details.id] ?: return@forEach
                val previousLod = lodById[details.id]
                lodById[details.id] = lod
                val renderPoints = lod.pointsForZoom(zoomNow)

                if (useElevationTrackColors) {
                    polylinesById.remove(details.id)?.let { solidPolyline ->
                        layers.remove(solidPolyline)
                        changed = true
                    }
                    elevationPolylinesById.remove(details.id)?.forEach { polyline ->
                        layers.remove(polyline)
                        changed = true
                    }
                    val segments =
                        buildElevationTrackSegments(
                            points = renderPoints,
                            opacityPercent = gpxTrackOpacityPercent,
                        )
                    elevationPolylinesById[details.id] =
                        createElevationTrackPolylines(
                            segments = segments,
                            strokeWidth = gpxTrackWidth,
                        ).also { polylines ->
                            polylines.forEach(layers::add)
                            if (polylines.isNotEmpty()) {
                                changed = true
                            }
                        }
                } else {
                    elevationPolylinesById.remove(details.id)?.forEach { polyline ->
                        layers.remove(polyline)
                        changed = true
                    }
                    val renderLatLongs = renderPoints.latLongs()
                    val polyline =
                        polylinesById.getOrPut(details.id) {
                            Polyline(trackPaint, AndroidGraphicFactory.INSTANCE).also { p ->
                                p.latLongs.addAll(renderLatLongs)
                                layers.add(p)
                                changed = true
                            }
                        }

                    val bucketChanged = displayedLodBucketById[details.id] != currentBucket
                    val sourceChanged = previousLod?.sourceSignature != lod.sourceSignature
                    if (
                        sourceChanged ||
                        bucketChanged ||
                        !hasSameLatLongs(polyline.latLongs, renderLatLongs)
                    ) {
                        polyline.latLongs.clear()
                        polyline.latLongs.addAll(renderLatLongs)
                        changed = true
                    }
                }
                displayedLodBucketById[details.id] = currentBucket

                // Start marker
                details.startPoint?.let { start ->
                    val existing = startMarkersById[details.id]
                    if (existing == null) {
                        val m = Marker(start, startBitmap, 0, 0)
                        startMarkersById[details.id] = m
                        layers.add(m)
                        changed = true
                    } else {
                        if (setMarkerLatLongIfChanged(existing, start)) changed = true
                    }
                }

                // End marker
                details.endPoint?.let { end ->
                    val existing = endMarkersById[details.id]
                    if (existing == null) {
                        val m = Marker(end, endBitmap, 0, 0)
                        endMarkersById[details.id] = m
                        layers.add(m)
                        changed = true
                    } else {
                        if (setMarkerLatLongIfChanged(existing, end)) changed = true
                    }
                }
            }

            val reordered = topOverlayCoordinator.sync()

            if (changed || reordered) requestMapRedraw()
        }
    }

    LaunchedEffect(routeToolPreviewPoints) {
        mapView.post {
            var changed = false
            val hasPreview = routeToolPreviewPoints.size >= 2
            val previewAttached = layers.contains(previewPolyline)

            if (hasPreview) {
                if (!previewAttached) {
                    layers.add(previewPolyline)
                    changed = true
                }
                if (!hasSameLatLongs(previewPolyline.latLongs, routeToolPreviewPoints)) {
                    previewPolyline.latLongs.clear()
                    previewPolyline.latLongs.addAll(routeToolPreviewPoints)
                    changed = true
                }
            } else if (previewAttached) {
                layers.remove(previewPolyline)
                previewPolyline.latLongs.clear()
                changed = true
            }

            val reordered = topOverlayCoordinator.sync()
            if (changed || reordered) requestMapRedraw()
        }
    }

    LaunchedEffect(routeToolDraftPoints) {
        mapView.post {
            var changed = false
            val hasDraft = routeToolDraftPoints.size >= 2
            val draftAttached = layers.contains(draftPolyline)

            if (hasDraft) {
                if (!draftAttached) {
                    layers.add(draftPolyline)
                    changed = true
                }
                if (!hasSameLatLongs(draftPolyline.latLongs, routeToolDraftPoints)) {
                    draftPolyline.latLongs.clear()
                    draftPolyline.latLongs.addAll(routeToolDraftPoints)
                    changed = true
                }
            } else if (draftAttached) {
                layers.remove(draftPolyline)
                draftPolyline.latLongs.clear()
                changed = true
            }

            val reordered = topOverlayCoordinator.sync()
            if (changed || reordered) requestMapRedraw()
        }
    }

    // Re-apply desired z-order when top overlays change.
    LaunchedEffect(locationMarker, selectedPointA, selectedPointB) {
        mapView.post {
            val changed = topOverlayCoordinator.sync()
            if (changed) requestMapRedraw()
        }
    }

    // ✅ Marker A (NO re-snap: GpxViewModel already provides a point ON the track)
    LaunchedEffect(selectedPointA, activeGpxDetails) {
        mapView.post {
            var changed = false
            markerAHolder[0]?.let {
                layers.remove(it)
                changed = true
            }
            markerAHolder[0] =
                selectedPointA?.let { ll ->
                    val snapped = snapToRenderedTrackOrNull(ll, activeGpxDetails) ?: ll
                    Marker(snapped, markerBitmapA, 0, 0)
                        .also {
                            layers.add(it)
                            changed = true
                        }
                }
            val reordered = topOverlayCoordinator.sync()
            if (changed || reordered) requestMapRedraw()
        }
    }

    // ✅ Marker B (NO re-snap: GpxViewModel already provides a point ON the track)
    LaunchedEffect(selectedPointB, activeGpxDetails) {
        mapView.post {
            var changed = false
            markerBHolder[0]?.let {
                layers.remove(it)
                changed = true
            }
            markerBHolder[0] =
                selectedPointB?.let { ll ->
                    val snapped = snapToRenderedTrackOrNull(ll, activeGpxDetails) ?: ll
                    Marker(snapped, markerBitmapB, 0, 0)
                        .also {
                            layers.add(it)
                            changed = true
                        }
                }
            val reordered = topOverlayCoordinator.sync()
            if (changed || reordered) requestMapRedraw()
        }
    }

    DisposableEffect(mapView) {
        onDispose {
            mapView.post {
                polylinesById.values.forEach(layers::remove)
                elevationPolylinesById.values.flatten().forEach(layers::remove)
                startMarkersById.values.forEach(layers::remove)
                endMarkersById.values.forEach(layers::remove)
                markerAHolder[0]?.let(layers::remove)
                markerBHolder[0]?.let(layers::remove)
                layers.remove(previewPolyline)

                polylinesById.clear()
                elevationPolylinesById.clear()
                startMarkersById.clear()
                endMarkersById.clear()
                lodById.clear()
                displayedLodBucketById.clear()
                previewPolyline.latLongs.clear()
                markerAHolder[0] = null
                markerBHolder[0] = null

                mapView.requestLayerRedrawSafely()
            }
        }
    }
}

private fun createElevationTrackPolylines(
    segments: List<ElevationTrackSegment>,
    strokeWidth: Float,
): List<Polyline> {
    val outlineWidth = strokeWidth + ELEVATION_TRACK_OUTLINE_WIDTH_EXTRA_PX
    val outlineColor = Color.argb(ELEVATION_TRACK_OUTLINE_ALPHA, 18, 24, 32)
    val outlines =
        segments.map { segment ->
            createElevationTrackPolyline(
                points = segment.points,
                color = outlineColor,
                strokeWidth = outlineWidth,
            )
        }
    val coloredSegments =
        segments.map { segment ->
            createElevationTrackPolyline(
                points = segment.points,
                color = segment.color,
                strokeWidth = strokeWidth,
            )
        }
    return outlines + coloredSegments
}

private fun createElevationTrackPolyline(
    points: List<LatLong>,
    color: Int,
    strokeWidth: Float,
): Polyline =
    Polyline(
        createGpxTrackPaint(
            color = color,
            strokeWidth = strokeWidth,
        ),
        AndroidGraphicFactory.INSTANCE,
    ).also { polyline ->
        polyline.latLongs.addAll(points)
    }
