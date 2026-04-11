package com.glancemap.glancemapcompanionapp

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glancemap.glancemapcompanionapp.filepicker.PageScrollbar
import com.glancemap.glancemapcompanionapp.filepicker.RoutingAreaMethod
import com.glancemap.glancemapcompanionapp.routing.BRouterTileMath

@Composable
internal fun RoutingDownloadDialog(
    context: Context,
    adaptive: CompanionAdaptiveSpec,
    viewModel: FileTransferViewModel,
    uiState: FileTransferUiState,
    isDownloadingRouting: Boolean,
    routingDownloadProgress: RoutingDownloadProgressState,
    watchInstalledMaps: List<WatchInstalledMap>,
    isLoadingWatchInstalledMaps: Boolean,
    watchInstalledMapsStatusMessage: String?,
    lastRoutingDownloadedFiles: List<GeneratedPhoneFile>,
    saveGeneratedFilesOnPhone: (List<GeneratedPhoneFile>) -> Unit,
    onDismiss: () -> Unit,
) {
    var bboxInput by remember { mutableStateOf("") }
    var mapMenuExpanded by remember { mutableStateOf(false) }
    var watchMenuExpanded by remember { mutableStateOf(false) }
    var areaMethodMenuExpanded by remember { mutableStateOf(false) }
    var areaMethod by remember { mutableStateOf(RoutingAreaMethod.WATCH_MAP) }
    var selectedWatchMapKey by remember(uiState.selectedWatch?.id) {
        mutableStateOf(watchInstalledMaps.firstOrNull()?.filePath.orEmpty())
    }

    val selectedRoutingMapCandidate =
        remember(uiState.selectedWatch?.id, selectedWatchMapKey, watchInstalledMaps) {
            if (uiState.selectedWatch == null) {
                null
            } else {
                watchInstalledMaps.firstOrNull { it.filePath == selectedWatchMapKey }
                    ?: watchInstalledMaps.firstOrNull()
            }
        }
    val resolvedRoutingBbox =
        remember(areaMethod, bboxInput, selectedRoutingMapCandidate) {
            when (areaMethod) {
                RoutingAreaMethod.WATCH_MAP -> selectedRoutingMapCandidate?.bbox.orEmpty()
                RoutingAreaMethod.MANUAL_BBOX -> bboxInput.trim()
            }
        }
    val routingTiles =
        remember(resolvedRoutingBbox) {
            runCatching {
                if (resolvedRoutingBbox.isBlank()) {
                    emptyList()
                } else {
                    BRouterTileMath.tileFileNamesForBbox(resolvedRoutingBbox)
                }
            }.getOrDefault(emptyList())
        }
    val routingBboxError =
        remember(resolvedRoutingBbox) {
            if (resolvedRoutingBbox.isBlank()) {
                null
            } else {
                runCatching {
                    BRouterTileMath.parseBbox(resolvedRoutingBbox)
                }.exceptionOrNull()?.localizedMessage
            }
        }
    val routingAreaMethodLabel =
        when (areaMethod) {
            RoutingAreaMethod.WATCH_MAP -> "Auto from watch map"
            RoutingAreaMethod.MANUAL_BBOX -> "Enter BBox manually"
        }
    val routingAreaMethodDescription =
        when (areaMethod) {
            RoutingAreaMethod.WATCH_MAP -> "Use the bbox of a .map already present on the watch."
            RoutingAreaMethod.MANUAL_BBOX -> "Enter west,south,east,north manually."
        }
    val routingWatchIsSelected = uiState.selectedWatch != null
    val selectedWatchReachable =
        isSelectedWatchReachable(
            selectedWatch = uiState.selectedWatch,
            availableWatches = uiState.availableWatches,
        )
    val routingWatchSelectionLabel =
        when {
            uiState.selectedWatch == null && uiState.availableWatches.isEmpty() -> "No watch found"
            uiState.selectedWatch == null -> "Select watch"
            selectedWatchReachable -> uiState.selectedWatch.displayName
            else -> "${uiState.selectedWatch.displayName} (Disconnected)"
        }
    val routingMapSelectionLabel =
        selectedRoutingMapCandidate?.fileName ?: when {
            uiState.selectedWatch == null -> "Select watch first"
            !selectedWatchReachable -> "Reconnect watch first"
            isLoadingWatchInstalledMaps -> "Loading watch maps..."
            else -> "Select map on watch"
        }
    val routingCompletedSuccessfully =
        routingDownloadProgress.completed && routingDownloadProgress.success
    val canDownloadRouting =
        !isDownloadingRouting &&
            routingBboxError == null &&
            resolvedRoutingBbox.isNotBlank() &&
            routingTiles.isNotEmpty()
    val routingScrollState = rememberScrollState()
    val routingStatusBringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(
        routingDownloadProgress.isRunning,
        routingDownloadProgress.completed,
        routingDownloadProgress.message,
    ) {
        if (routingDownloadProgress.isRunning || routingDownloadProgress.completed) {
            routingStatusBringIntoViewRequester.bringIntoView()
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isDownloadingRouting) onDismiss()
        },
        title = { Text("Download routing data") },
        text = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = adaptive.refugesDialogMaxHeight),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(end = 10.dp)
                            .verticalScroll(routingScrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "BRouter routing packs (.rd5) are downloaded on the phone, then added to section 2 so you can decide if you want to send them.",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Text(
                        "Select watch",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    androidx.compose.foundation.layout
                        .Spacer(modifier = Modifier.size(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { watchMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isDownloadingRouting && uiState.availableWatches.isNotEmpty(),
                                colors =
                                    ButtonDefaults.outlinedButtonColors(
                                        contentColor =
                                            when {
                                                selectedWatchReachable -> Color(0xFF2E7D32)
                                                routingWatchIsSelected -> MaterialTheme.colorScheme.error
                                                else -> MaterialTheme.colorScheme.onSurface
                                            },
                                        containerColor =
                                            when {
                                                selectedWatchReachable -> Color(0x1A2E7D32)
                                                routingWatchIsSelected -> MaterialTheme.colorScheme.errorContainer
                                                else -> Color.Transparent
                                            },
                                    ),
                            ) {
                                Text(
                                    routingWatchSelectionLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            DropdownMenu(
                                expanded = watchMenuExpanded,
                                onDismissRequest = { watchMenuExpanded = false },
                            ) {
                                uiState.availableWatches.forEach { watch ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                watch.displayName,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        onClick = {
                                            viewModel.onWatchSelected(context, watch)
                                            selectedWatchMapKey = ""
                                            mapMenuExpanded = false
                                            watchMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = { viewModel.findWatchNodes() },
                            enabled = !isDownloadingRouting,
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh Watch List",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    if (uiState.selectedWatch != null && !selectedWatchReachable) {
                        Text(
                            "${selectedWatchDisconnectedStatusMessage()} You can also switch to manual BBox.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (uiState.availableWatches.isEmpty()) {
                        Text(
                            "No watches found. Keep the watch app open and tap refresh.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else if (uiState.selectedWatch == null) {
                        Text(
                            "Select a watch first if you want to use Auto from watch map.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Text(
                        "Select area source",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { areaMethodMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isDownloadingRouting,
                        ) {
                            Text(routingAreaMethodLabel)
                        }
                        DropdownMenu(
                            expanded = areaMethodMenuExpanded,
                            onDismissRequest = { areaMethodMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Auto from watch map") },
                                onClick = {
                                    areaMethod = RoutingAreaMethod.WATCH_MAP
                                    areaMethodMenuExpanded = false
                                },
                                enabled = selectedWatchReachable,
                            )
                            DropdownMenuItem(
                                text = { Text("Enter BBox manually") },
                                onClick = {
                                    areaMethod = RoutingAreaMethod.MANUAL_BBOX
                                    areaMethodMenuExpanded = false
                                },
                            )
                        }
                    }
                    Text(
                        routingAreaMethodDescription,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    when (areaMethod) {
                        RoutingAreaMethod.WATCH_MAP -> {
                            Text(
                                "Select map on watch",
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        onClick = { mapMenuExpanded = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = selectedWatchReachable && !isLoadingWatchInstalledMaps,
                                    ) {
                                        Text(routingMapSelectionLabel)
                                    }
                                    DropdownMenu(
                                        expanded = mapMenuExpanded,
                                        onDismissRequest = { mapMenuExpanded = false },
                                    ) {
                                        watchInstalledMaps.forEach { candidate ->
                                            DropdownMenuItem(
                                                text = { Text(candidate.fileName) },
                                                onClick = {
                                                    selectedWatchMapKey = candidate.filePath
                                                    mapMenuExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.refreshWatchInstalledMaps(
                                            context = context,
                                            showToastIfUnavailable = true,
                                        )
                                    },
                                    enabled = selectedWatchReachable && !isLoadingWatchInstalledMaps,
                                    modifier = Modifier.size(30.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Refresh Watch Maps",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            when {
                                uiState.selectedWatch != null && !selectedWatchReachable -> {
                                    Text(
                                        "${selectedWatchDisconnectedStatusMessage()} Use manual BBox while the watch is disconnected.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }

                                isLoadingWatchInstalledMaps -> {
                                    Text(
                                        "Loading watch maps...",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }

                                uiState.selectedWatch == null -> {
                                    Text(
                                        "Select a watch in section 3 first, or switch to manual BBox.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }

                                !watchInstalledMapsStatusMessage.isNullOrBlank() -> {
                                    Text(
                                        watchInstalledMapsStatusMessage.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }

                                watchInstalledMaps.isEmpty() -> {
                                    Text(
                                        "No .map files found on watch. Transfer a map first, or switch area method.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }

                                selectedRoutingMapCandidate != null -> {
                                    Text(
                                        "Area BBox: ${selectedRoutingMapCandidate.bbox}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }

                        RoutingAreaMethod.MANUAL_BBOX -> {
                            Text(
                                "Enter BBox (rectangle) as west,south,east,north (minLon,minLat,maxLon,maxLat). Example: 1.40,42.43,1.79,42.66",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            OutlinedTextField(
                                value = bboxInput,
                                onValueChange = { bboxInput = it },
                                label = { Text("BBox") },
                                singleLine = true,
                            )
                        }
                    }

                    if (routingBboxError != null) {
                        Text(
                            routingBboxError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    if (routingTiles.isNotEmpty()) {
                        Text(
                            "Routing packs (${routingTiles.size})",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            routingTiles.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    if (routingDownloadProgress.isRunning || routingDownloadProgress.completed) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(routingStatusBringIntoViewRequester),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val progressValue =
                                (routingDownloadProgress.progressPercent / 100f).coerceIn(0f, 1f)
                            Text(
                                routingDownloadProgress.status.ifBlank {
                                    if (routingDownloadProgress.isRunning) {
                                        "Downloading routing packs..."
                                    } else {
                                        "Routing download finished."
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            LinearProgressIndicator(
                                progress = { progressValue },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (routingDownloadProgress.detail.isNotBlank()) {
                                    Text(
                                        routingDownloadProgress.detail,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                } else {
                                    Spacer(modifier = Modifier)
                                }
                                Text(
                                    "${routingDownloadProgress.progressPercent}%",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            if (routingDownloadProgress.message.isNotBlank()) {
                                Text(
                                    routingDownloadProgress.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color =
                                        if (routingDownloadProgress.success) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                )
                            }
                            if (routingCompletedSuccessfully) {
                                Text(
                                    "Download complete. You can save the routing file(s) on phone or send them to the watch.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                PageScrollbar(
                    scrollState = routingScrollState,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                )
            }
        },
        confirmButton = {
            if (routingCompletedSuccessfully) {
                TextButton(
                    onClick = {
                        onDismiss()
                        viewModel.resetRoutingDownloadProgress()
                        viewModel.sendFiles(context)
                    },
                ) {
                    Text("Send")
                }
            } else {
                TextButton(
                    onClick = {
                        viewModel.resetRoutingDownloadProgress()
                        viewModel.downloadRoutingByBbox(
                            context = context,
                            bbox = resolvedRoutingBbox,
                            appendToSelection = true,
                        )
                    },
                    enabled = canDownloadRouting && !isDownloadingRouting,
                ) {
                    Text(if (isDownloadingRouting) "Downloading..." else "Download")
                }
            }
        },
        dismissButton = {
            if (routingCompletedSuccessfully) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.resetRoutingDownloadProgress()
                            onDismiss()
                        },
                    ) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = { saveGeneratedFilesOnPhone(lastRoutingDownloadedFiles) },
                        enabled = lastRoutingDownloadedFiles.isNotEmpty(),
                    ) {
                        Text("Save on phone")
                    }
                }
            } else {
                TextButton(
                    onClick = {
                        if (isDownloadingRouting) {
                            viewModel.cancelRoutingDownload()
                        } else {
                            viewModel.resetRoutingDownloadProgress()
                            onDismiss()
                        }
                    },
                    enabled = true,
                ) {
                    Text("Cancel")
                }
            }
        },
    )
}
