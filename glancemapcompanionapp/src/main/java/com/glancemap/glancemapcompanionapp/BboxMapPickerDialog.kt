@file:Suppress("FunctionNaming", "TooManyFunctions")

package com.glancemap.glancemapcompanionapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val DEFAULT_PICKER_BBOX = "6.00,45.30,6.90,45.90"
private const val OSM_PICKER_WARNING_AREA_DEGREES = 4.0
private const val OSM_PICKER_WARNING_LON_SPAN_DEGREES = 2.5
private const val OSM_PICKER_WARNING_LAT_SPAN_DEGREES = 2.0
private const val OSM_PICKER_MAX_AREA_DEGREES = 12.0
private const val OSM_PICKER_MAX_LON_SPAN_DEGREES = 5.0
private const val OSM_PICKER_MAX_LAT_SPAN_DEGREES = 4.0
private const val REFUGES_PICKER_MAX_AREA_DEGREES = 120.0
private const val REFUGES_PICKER_MAX_LON_SPAN_DEGREES = 20.0
private const val REFUGES_PICKER_MAX_LAT_SPAN_DEGREES = 12.0

@Composable
internal fun BboxMapPickerDialog(
    initialBbox: String,
    selectedSource: PoiImportSource,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val initialPickerBbox =
        remember(initialBbox, selectedSource) {
            resolveInitialPickerBbox(initialBbox, selectedSource)
        }
    val hasUsableInitialBbox =
        remember(initialBbox, selectedSource) {
            hasUsableInitialPickerBbox(initialBbox, selectedSource)
        }
    var centerOnLocation by remember(initialBbox) { mutableStateOf(!hasUsableInitialBbox) }
    val locationAllowed = rememberMapPickerLocationAllowed(centerOnLocation)
    val useLocationDefault = centerOnLocation && locationAllowed
    var selectedBbox by remember(initialPickerBbox) { mutableStateOf(initialPickerBbox) }
    var mapReady by remember { mutableStateOf(false) }
    val sizeStatus = describeBboxSize(selectedBbox, selectedSource)

    LaunchedEffect(initialPickerBbox, useLocationDefault) {
        mapReady = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = true,
            ),
    ) {
        BboxMapPickerSurface(
            initialBbox = initialPickerBbox,
            useLocationDefault = useLocationDefault,
            state =
                BboxMapPickerState(
                    selectedBbox = selectedBbox,
                    centerOnLocation = centerOnLocation,
                    mapReady = mapReady,
                    sizeStatus = sizeStatus,
                ),
            actions =
                BboxMapPickerActions(
                    onDismiss = onDismiss,
                    onConfirm = onConfirm,
                    onReady = { mapReady = true },
                    onCenterOnLocationChanged = { checked -> centerOnLocation = checked },
                    onBboxChanged = { bbox -> selectedBbox = bbox },
                ),
        )
    }
}

private data class BboxMapPickerState(
    val selectedBbox: String,
    val centerOnLocation: Boolean,
    val mapReady: Boolean,
    val sizeStatus: BboxSizeStatus,
)

private data class BboxMapPickerActions(
    val onDismiss: () -> Unit,
    val onConfirm: (String) -> Unit,
    val onReady: () -> Unit,
    val onCenterOnLocationChanged: (Boolean) -> Unit,
    val onBboxChanged: (String) -> Unit,
)

private data class BboxMapPickerMapState(
    val fineControlEnabled: Boolean,
    val mapReady: Boolean,
)

@Composable
private fun BboxMapPickerSurface(
    initialBbox: String,
    useLocationDefault: Boolean,
    state: BboxMapPickerState,
    actions: BboxMapPickerActions,
) {
    var fineControlEnabled by remember { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
        modifier =
            Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.90f)
                .heightIn(max = 720.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
        ) {
            BboxMapPickerHeader()
            Spacer(modifier = Modifier.height(4.dp))
            BboxMapPickerControls(
                centerOnLocation = state.centerOnLocation,
                fineControlEnabled = fineControlEnabled,
                onCenterOnLocationChanged = actions.onCenterOnLocationChanged,
                onFineControlChanged = { checked -> fineControlEnabled = checked },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(2.dp))
            MapPickerHelpText(
                text = "Pan the map, tap to recenter, long-press the center icon to move the area, then resize it.",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            BboxMapPickerMap(
                initialBbox = initialBbox,
                useLocationDefault = useLocationDefault,
                mapState =
                    BboxMapPickerMapState(
                        fineControlEnabled = fineControlEnabled,
                        mapReady = state.mapReady,
                    ),
                actions = actions,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.height(10.dp))
            BboxMapPickerFooter(
                selectedBbox = state.selectedBbox,
                sizeStatus = state.sizeStatus,
                onDismiss = actions.onDismiss,
                onConfirm = actions.onConfirm,
            )
        }
    }
}

@Composable
private fun BboxMapPickerHeader() {
    Text(
        "Pick area",
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun BboxMapPickerMap(
    initialBbox: String,
    useLocationDefault: Boolean,
    mapState: BboxMapPickerMapState,
    actions: BboxMapPickerActions,
    modifier: Modifier = Modifier,
) {
    val initialBounds = remember(initialBbox) { MapPickerBounds.parseOrNull(initialBbox) ?: defaultPickerBounds() }
    var pickerView by remember(useLocationDefault, initialBbox) { mutableStateOf<MapLibreBboxPickerView?>(null) }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        key(useLocationDefault, initialBbox) {
            AndroidView(
                factory = { context ->
                    MapLibreBboxPickerView(
                        context = context,
                        initialBounds = initialBounds,
                        useLocationDefault = useLocationDefault,
                        onBoundsChanged = actions.onBboxChanged,
                        onReady = actions.onReady,
                    ).also { view -> pickerView = view }
                },
                update = { view ->
                    view.setFineControlEnabled(mapState.fineControlEnabled)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!mapState.mapReady) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        if (mapState.mapReady) {
            MapPickerZoomControls(
                onZoomIn = { pickerView?.zoomIn() },
                onZoomOut = { pickerView?.zoomOut() },
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp),
            )
        }
    }
}

@Composable
private fun BboxMapPickerControls(
    centerOnLocation: Boolean,
    fineControlEnabled: Boolean,
    onCenterOnLocationChanged: (Boolean) -> Unit,
    onFineControlChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        MapPickerControlRow(
            checked = centerOnLocation,
            onCheckedChange = onCenterOnLocationChanged,
            label = "Center on my location",
            modifier = Modifier.weight(1f),
        )
        MapPickerControlRow(
            checked = fineControlEnabled,
            onCheckedChange = onFineControlChanged,
            label = "Fine control",
        )
    }
}

@Composable
private fun BboxMapPickerFooter(
    selectedBbox: String,
    sizeStatus: BboxSizeStatus,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    Text(
        sizeStatus.label,
        style = MaterialTheme.typography.bodySmall,
        color = sizeStatus.color,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        OutlinedButton(onClick = onDismiss) {
            Text("Cancel")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = { onConfirm(selectedBbox) },
            enabled = MapPickerBounds.parseOrNull(selectedBbox) != null && sizeStatus.canConfirm,
        ) {
            Text("Use area")
        }
    }
}

private data class BboxSizeStatus(
    val label: String,
    val color: Color,
    val canConfirm: Boolean,
)

@Composable
private fun describeBboxSize(
    bbox: String,
    selectedSource: PoiImportSource,
): BboxSizeStatus {
    val bounds = MapPickerBounds.parseOrNull(bbox)
    val defaultColor = MaterialTheme.colorScheme.onSurfaceVariant
    val warningColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val labelAndColor =
        when {
            bounds == null ->
                Triple("Move or zoom the map to select an area.", defaultColor, false)
            bounds.isTooLargeForPoiImport(selectedSource) ->
                Triple("Too large for POI import. Zoom in or pick a smaller area.", errorColor, false)
            bounds.isLargeForOsm(selectedSource) ->
                Triple("Large for OSM. It may be slow or fail on dense areas.", warningColor, true)
            else ->
                Triple("Area size looks OK. POI count is checked during import.", defaultColor, true)
        }
    return BboxSizeStatus(
        label = labelAndColor.first,
        color = labelAndColor.second,
        canConfirm = labelAndColor.third,
    )
}

private fun resolveInitialPickerBbox(
    initialBbox: String,
    selectedSource: PoiImportSource,
): String =
    MapPickerBounds
        .parseOrNull(initialBbox)
        ?.takeUnless { it.isTooLargeForPoiImport(selectedSource) }
        ?.toBboxString()
        ?: DEFAULT_PICKER_BBOX

private fun hasUsableInitialPickerBbox(
    initialBbox: String,
    selectedSource: PoiImportSource,
): Boolean =
    MapPickerBounds
        .parseOrNull(initialBbox)
        ?.takeUnless { it.isTooLargeForPoiImport(selectedSource) } != null

private fun defaultPickerBounds(): MapPickerBounds =
    MapPickerBounds.parseOrNull(DEFAULT_PICKER_BBOX) ?: MapPickerBounds.defaultAround(45.6, 6.45)

private fun MapPickerBounds.isTooLargeForPoiImport(selectedSource: PoiImportSource): Boolean =
    if (selectedSource == PoiImportSource.OSM) {
        areaDegrees > OSM_PICKER_MAX_AREA_DEGREES ||
            lonSpan > OSM_PICKER_MAX_LON_SPAN_DEGREES ||
            latSpan > OSM_PICKER_MAX_LAT_SPAN_DEGREES
    } else {
        areaDegrees > REFUGES_PICKER_MAX_AREA_DEGREES ||
            lonSpan > REFUGES_PICKER_MAX_LON_SPAN_DEGREES ||
            latSpan > REFUGES_PICKER_MAX_LAT_SPAN_DEGREES
    }

private fun MapPickerBounds.isLargeForOsm(selectedSource: PoiImportSource): Boolean =
    selectedSource == PoiImportSource.OSM &&
        (
            areaDegrees > OSM_PICKER_WARNING_AREA_DEGREES ||
                lonSpan > OSM_PICKER_WARNING_LON_SPAN_DEGREES ||
                latSpan > OSM_PICKER_WARNING_LAT_SPAN_DEGREES
        )
