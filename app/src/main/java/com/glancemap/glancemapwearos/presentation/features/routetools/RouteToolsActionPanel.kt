package com.glancemap.glancemapwearos.presentation.features.routetools

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.presentation.features.poi.PoiSearchUiState
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec
import org.mapsforge.core.model.LatLong

private const val ROUTE_TOOLS_DRAG_DISMISS_PX = 55f

@Composable
internal fun RouteToolsActionPanel(
    visible: Boolean,
    canModifyActiveGpx: Boolean,
    coordinateSeed: LatLong?,
    poiSearchState: PoiSearchUiState,
    options: RouteToolOptions,
    preflightMessage: String?,
    onOptionsChange: (RouteToolOptions) -> Unit,
    onSearchPoi: (String) -> Unit,
    onClearPoiSearch: () -> Unit,
    onDismiss: () -> Unit,
    onStartSelection: (RouteToolSession) -> Unit,
) {
    if (!visible) return

    val adaptive = rememberWearAdaptiveSpec()
    val scrollState = rememberScrollState()
    val routeToolsBottomActionSafeInset =
        if (!adaptive.isRound) {
            0.dp
        } else {
            when (adaptive.screenSize) {
                WearScreenSize.LARGE -> 8.dp
                WearScreenSize.MEDIUM -> 10.dp
                WearScreenSize.SMALL -> 12.dp
            }
        } / 2
    val routeToolsContentBottomInset =
        if (!adaptive.isRound) {
            8.dp
        } else {
            when (adaptive.screenSize) {
                WearScreenSize.LARGE -> 12.dp
                WearScreenSize.MEDIUM -> 14.dp
                WearScreenSize.SMALL -> 16.dp
            }
        }
    val routeToolsContentMaxHeight =
        (
            adaptive.helpDialogMaxHeight +
                if (!adaptive.isRound) {
                    28.dp
                } else {
                    when (adaptive.screenSize) {
                        WearScreenSize.LARGE -> 22.dp
                        WearScreenSize.MEDIUM -> 18.dp
                        WearScreenSize.SMALL -> 14.dp
                    }
                }
        ) - routeToolsBottomActionSafeInset
    var showCoordinateEditor by remember(visible) { mutableStateOf(false) }
    var showPoiSearchDialog by remember(visible) { mutableStateOf(false) }
    var coordinateDraftLat by remember(visible) { mutableStateOf(0.0) }
    var coordinateDraftLon by remember(visible) { mutableStateOf(0.0) }
    var coordinateStep by remember(visible) { mutableStateOf(CoordinateStep.ONE_THOUSANDTH) }
    val startSelection: (RouteToolSession) -> Unit = startSelection@{ session ->
        val canStart = !session.options.requiresSingleActiveGpx || canModifyActiveGpx
        if (!canStart) return@startSelection
        onOptionsChange(session.options)
        onStartSelection(session)
    }
    val openCoordinateEditor: (RouteToolOptions) -> Unit = openCoordinateEditor@{ updatedOptions ->
        val seededOptions = updatedOptions.seedCoordinateTarget(coordinateSeed)
        onOptionsChange(seededOptions)
        coordinateDraftLat = seededOptions.coordinateLatitude ?: 0.0
        coordinateDraftLon = seededOptions.coordinateLongitude ?: 0.0
        showCoordinateEditor = true
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Color.Black.copy(alpha = 0.86f),
                        RoundedCornerShape(adaptive.dialogCornerRadius),
                    ).padding(
                        horizontal = adaptive.dialogHorizontalPadding,
                        vertical = adaptive.dialogVerticalPadding,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            var totalDrag = 0f
                            detectVerticalDragGestures(
                                onDragEnd = { totalDrag = 0f },
                                onDragCancel = { totalDrag = 0f },
                            ) { _, dragAmount ->
                                totalDrag += dragAmount
                                if (totalDrag > ROUTE_TOOLS_DRAG_DISMISS_PX) {
                                    onDismiss()
                                    totalDrag = 0f
                                }
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(26.dp)
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.42f), RoundedCornerShape(50)),
                )
            }

            Text(
                text = "GPX Tools",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = routeToolsContentMaxHeight),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(end = 10.dp, bottom = routeToolsContentBottomInset)
                            .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!preflightMessage.isNullOrBlank()) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Color(0xFFF6E4A6).copy(alpha = 0.96f),
                                        RoundedCornerShape(14.dp),
                                    ).padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = preflightMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF3C2500),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    RouteToolKindSelector(
                        selected = options.toolKind,
                        onSelected = { kind ->
                            onOptionsChange(options.copy(toolKind = kind))
                        },
                    )

                    if (!canModifyActiveGpx && options.requiresSingleActiveGpx) {
                        Text(
                            text = "Activate exactly one GPX first to use this tool.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFCC80),
                            textAlign = TextAlign.Center,
                        )
                    }

                    RouteActionSelector(
                        options = options,
                        canModifyActiveGpx = canModifyActiveGpx,
                        coordinateSeed = coordinateSeed,
                        onOptionsChange = onOptionsChange,
                        onStartSelection = startSelection,
                        onOpenCoordinateEditor = openCoordinateEditor,
                        onOpenPoiSearchDialog = { showPoiSearchDialog = true },
                    )

                    if (options.toolKind == RouteToolKind.CREATE &&
                        options.createMode == RouteCreateMode.SEARCH
                    ) {
                        RouteSettingRow(
                            title = "Offline POI",
                            value = poiSearchSummary(poiSearchState),
                            onClick = { showPoiSearchDialog = true },
                        )
                    }

                    if (options.toolKind == RouteToolKind.CREATE &&
                        options.createMode == RouteCreateMode.COORDINATES
                    ) {
                        RouteSettingRow(
                            title = "Destination",
                            value = options.coordinatesSummary(),
                            onClick = { openCoordinateEditor(options) },
                        )
                        Button(
                            onClick = {
                                val seededOptions = options.seedCoordinateTarget(coordinateSeed)
                                val lat = seededOptions.coordinateLatitude
                                val lon = seededOptions.coordinateLongitude
                                if (lat == null || lon == null) {
                                    openCoordinateEditor(seededOptions)
                                } else {
                                    startSelection(
                                        RouteToolSession(
                                            options = seededOptions,
                                            destination = LatLong(lat, lon),
                                        ),
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Create route")
                        }
                    }

                    if (options.toolKind == RouteToolKind.CREATE &&
                        options.createMode == RouteCreateMode.LOOP_AROUND_HERE
                    ) {
                        LoopTargetModeSelector(
                            selected = options.loopTargetMode,
                            onSelected = { targetMode ->
                                onOptionsChange(options.copy(loopTargetMode = targetMode))
                            },
                        )
                        LoopTargetEditor(
                            targetMode = options.loopTargetMode,
                            distanceKm = options.loopDistanceKm,
                            durationMinutes = options.loopDurationMinutes,
                            onDecrease = {
                                onOptionsChange(
                                    when (options.loopTargetMode) {
                                        LoopTargetMode.DISTANCE -> {
                                            options.copy(
                                                loopDistanceKm =
                                                    (options.loopDistanceKm - 1)
                                                        .coerceAtLeast(2),
                                            )
                                        }

                                        LoopTargetMode.TIME -> {
                                            options.copy(
                                                loopDurationMinutes =
                                                    (options.loopDurationMinutes - 15)
                                                        .coerceAtLeast(30),
                                            )
                                        }
                                    },
                                )
                            },
                            onIncrease = {
                                onOptionsChange(
                                    when (options.loopTargetMode) {
                                        LoopTargetMode.DISTANCE -> {
                                            options.copy(
                                                loopDistanceKm =
                                                    (options.loopDistanceKm + 1)
                                                        .coerceAtMost(60),
                                            )
                                        }

                                        LoopTargetMode.TIME -> {
                                            options.copy(
                                                loopDurationMinutes =
                                                    (options.loopDurationMinutes + 15)
                                                        .coerceAtMost(480),
                                            )
                                        }
                                    },
                                )
                            },
                        )
                        LoopStartModeSelector(
                            selected = options.loopStartMode,
                            onSelected = { startMode ->
                                onOptionsChange(options.copy(loopStartMode = startMode))
                            },
                        )
                        Button(
                            onClick = {
                                startSelection(RouteToolSession(options = options))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (options.loopStartMode == LoopStartMode.CURRENT_LOCATION) {
                                    "Create loop"
                                } else {
                                    "Pick start on map"
                                },
                            )
                        }
                    }

                    Text(
                        text = options.activeSummary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = Color.White.copy(alpha = 0.84f),
                    )

                    Text(
                        text = routeToolsHintText(options),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = Color.White.copy(alpha = 0.68f),
                    )

                    RouteSettingRow(
                        title = "Route style",
                        value = options.routeStyle.title,
                        onClick = {
                            onOptionsChange(
                                options.copy(routeStyle = options.routeStyle.next()),
                            )
                        },
                    )
                    Text(
                        text = options.routeStyle.summary,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = Color.White.copy(alpha = 0.72f),
                    )

                    if (options.toolKind == RouteToolKind.MODIFY) {
                        RouteSaveBehaviorSelector(
                            selected = options.saveBehavior,
                            onSelected = { behavior ->
                                onOptionsChange(options.copy(saveBehavior = behavior))
                            },
                        )
                    }

                    if (options.toolKind == RouteToolKind.CREATE) {
                        RouteSettingRow(
                            title =
                                if (options.showAdvancedOptions) {
                                    "Hide options"
                                } else {
                                    "More options"
                                },
                            value = if (options.showAdvancedOptions) "Advanced" else "Basic",
                            onClick = {
                                onOptionsChange(
                                    options.copy(showAdvancedOptions = !options.showAdvancedOptions),
                                )
                            },
                        )
                    }

                    if (options.toolKind == RouteToolKind.CREATE && options.showAdvancedOptions) {
                        SwitchButton(
                            modifier = Modifier.fillMaxWidth(),
                            checked = options.useElevation,
                            onCheckedChange = { enabled ->
                                onOptionsChange(options.copy(useElevation = enabled))
                            },
                            label = { Text("Use elevation") },
                        )
                        SwitchButton(
                            modifier = Modifier.fillMaxWidth(),
                            checked = options.allowFerries,
                            onCheckedChange = { enabled ->
                                onOptionsChange(options.copy(allowFerries = enabled))
                            },
                            label = { Text("Allow ferries") },
                        )
                    }
                }

                RouteToolsScrollIndicator(
                    scrollState = scrollState,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 1.dp),
                )
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(routeToolsBottomActionSafeInset),
            )
        }
    }

    CoordinateEntryDialog(
        visible = showCoordinateEditor,
        latitude = coordinateDraftLat,
        longitude = coordinateDraftLon,
        step = coordinateStep,
        hasSeed = coordinateSeed != null,
        onLatitudeChange = { coordinateDraftLat = it.coerceIn(-90.0, 90.0) },
        onLongitudeChange = { coordinateDraftLon = normalizeLongitude(it) },
        onStepChange = { coordinateStep = it },
        onUseSeed = {
            coordinateSeed?.let { seed ->
                coordinateDraftLat = seed.latitude
                coordinateDraftLon = seed.longitude
            }
        },
        onDismiss = { showCoordinateEditor = false },
        onConfirm = {
            onOptionsChange(
                options.copy(
                    createMode = RouteCreateMode.COORDINATES,
                    coordinateLatitude = coordinateDraftLat,
                    coordinateLongitude = coordinateDraftLon,
                ),
            )
            showCoordinateEditor = false
        },
    )

    RoutePoiSearchDialog(
        visible = showPoiSearchDialog,
        state = poiSearchState,
        onDismiss = {
            showPoiSearchDialog = false
            onClearPoiSearch()
        },
        onSearch = onSearchPoi,
        onSelectResult = { result ->
            showPoiSearchDialog = false
            onClearPoiSearch()
            startSelection(
                RouteToolSession(
                    options = options,
                    destination = LatLong(result.lat, result.lon),
                ),
            )
        },
    )
}
