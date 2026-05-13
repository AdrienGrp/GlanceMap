@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionName",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "ReturnCount",
    "TooManyFunctions",
)

package com.glancemap.glancemapwearos.presentation.features.download

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.presentation.ui.DeleteConfirmationDialog
import com.glancemap.glancemapwearos.presentation.ui.KeepScreenOnEffect
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec
import com.glancemap.glancemapwearos.presentation.ui.rememberWearScreenSize
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.material.Chip
import androidx.wear.compose.material3.Icon as Material3Icon

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel,
    areaPickerOpen: Boolean,
    onAreaPickerOpenChange: (Boolean) -> Unit,
    selectedAreaFolder: String?,
    onSelectedAreaFolderChange: (String?) -> Unit,
    areaSearchQuery: String,
    onAreaSearchQueryChange: (String) -> Unit,
    onLibraryChanged: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val screenSize = rememberWearScreenSize()
    val adaptive = rememberWearAdaptiveSpec()
    val uiState by viewModel.uiState.collectAsState()
    if (uiState.isDownloading) {
        KeepScreenOnEffect()
    }
    val showAreaPicker = areaPickerOpen
    var bundlePendingDelete by remember { mutableStateOf<OamInstalledBundle?>(null) }
    var showOamInfoDialog by remember { mutableStateOf(false) }
    var deleteMode by remember { mutableStateOf(false) }
    var refreshMode by remember { mutableStateOf(false) }
    var showAreaSearchDialog by remember { mutableStateOf(false) }
    val infoPrefs =
        remember(context) {
            context.getSharedPreferences(DOWNLOAD_INFO_PREFS, android.content.Context.MODE_PRIVATE)
        }
    val listState = rememberScalingLazyListState()
    val selectedAreas = uiState.selectedAreas
    val estimatedSize =
        selectedAreas.estimatedSizeLabel(uiState.selection)
    val selectedAreaLabel = selectedAreas.selectedAreaLabel()
    val selectedAreaSecondaryLabel = selectedAreas.selectedAreaSecondaryLabel()
    val listHorizontalPadding =
        when (screenSize) {
            WearScreenSize.LARGE -> 16.dp
            WearScreenSize.MEDIUM -> 14.dp
            WearScreenSize.SMALL -> 12.dp
        }
    val listTopPadding =
        when (screenSize) {
            WearScreenSize.LARGE -> 6.dp
            WearScreenSize.MEDIUM -> 5.dp
            WearScreenSize.SMALL -> 4.dp
        }
    val listBottomPadding =
        when (screenSize) {
            WearScreenSize.LARGE -> 8.dp
            WearScreenSize.MEDIUM -> 7.dp
            WearScreenSize.SMALL -> 6.dp
        }
    val rowSpacing =
        when (screenSize) {
            WearScreenSize.LARGE -> 8.dp
            WearScreenSize.MEDIUM -> 7.dp
            WearScreenSize.SMALL -> 5.dp
        }
    val areaFolders =
        remember(uiState.areas) {
            uiState.areas
                .groupBy { it.continent }
                .toSortedMap()
                .map { (continent, areas) -> continent to areas.sortedBy { it.region } }
        }
    val areaSearchQueryNormalized = areaSearchQuery.trim()
    val visiblePickerAreas =
        remember(uiState.areas, selectedAreaFolder, areaSearchQueryNormalized) {
            val query = areaSearchQueryNormalized.lowercase()
            uiState.areas
                .asSequence()
                .filter { area -> selectedAreaFolder == null || area.continent == selectedAreaFolder }
                .filter { area ->
                    query.isBlank() ||
                        area.region.lowercase().contains(query) ||
                        area.continent.lowercase().contains(query)
                }.sortedWith(compareBy<OamDownloadArea> { it.continent }.thenBy { it.region })
                .toList()
        }
    val headerTopPadding =
        when (screenSize) {
            WearScreenSize.LARGE -> 8.dp
            WearScreenSize.MEDIUM -> 6.dp
            WearScreenSize.SMALL -> 4.dp
        }
    val headerBottomPadding =
        when (screenSize) {
            WearScreenSize.LARGE -> 2.dp
            WearScreenSize.MEDIUM -> 2.dp
            WearScreenSize.SMALL -> 1.dp
        }
    val headerActionButtonSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 24.dp
            WearScreenSize.MEDIUM -> 22.dp
            WearScreenSize.SMALL -> 20.dp
        }
    val headerActionIconSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 14.dp
            WearScreenSize.MEDIUM -> 13.dp
            WearScreenSize.SMALL -> 12.dp
        }
    val headerActionSpacing =
        when (screenSize) {
            WearScreenSize.LARGE -> 4.dp
            WearScreenSize.MEDIUM -> 3.dp
            WearScreenSize.SMALL -> 2.dp
        }
    val headerTopSafePadding = headerTopPadding + adaptive.headerTopSafeInset
    val actionButtonHeight =
        when (screenSize) {
            WearScreenSize.LARGE -> 44.dp
            WearScreenSize.MEDIUM -> 42.dp
            WearScreenSize.SMALL -> 38.dp
        }
    val actionButtonIconSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 18.dp
            WearScreenSize.MEDIUM -> 17.dp
            WearScreenSize.SMALL -> 16.dp
        }
    val settingsBottomPadding =
        when (screenSize) {
            WearScreenSize.LARGE -> 5.dp
            WearScreenSize.MEDIUM -> 4.dp
            WearScreenSize.SMALL -> 3.dp
        }
    val settingsButtonSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 28.dp
            WearScreenSize.MEDIUM -> 26.dp
            WearScreenSize.SMALL -> 24.dp
        }
    val footerTextSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 9.sp
            WearScreenSize.MEDIUM -> 8.sp
            WearScreenSize.SMALL -> 7.sp
        }
    val footerLineHeight =
        when (screenSize) {
            WearScreenSize.LARGE -> 10.sp
            WearScreenSize.MEDIUM -> 9.sp
            WearScreenSize.SMALL -> 8.sp
        }
    val footerHorizontalPadding =
        when (screenSize) {
            WearScreenSize.LARGE -> 34.dp
            WearScreenSize.MEDIUM -> 32.dp
            WearScreenSize.SMALL -> 30.dp
        }

    LaunchedEffect(uiState.lastLibraryChangedAtMillis) {
        if (uiState.lastLibraryChangedAtMillis > 0L) {
            onLibraryChanged()
        }
    }
    LaunchedEffect(infoPrefs) {
        if (!infoPrefs.getBoolean(DOWNLOAD_INFO_SHOWN_KEY, false)) {
            showOamInfoDialog = true
        }
    }
    LaunchedEffect(showAreaPicker) {
        if (!showAreaPicker) {
            onSelectedAreaFolderChange(null)
            onAreaSearchQueryChange("")
            showAreaSearchDialog = false
        }
    }

    fun dismissOamInfoDialog() {
        showOamInfoDialog = false
        infoPrefs.edit().putBoolean(DOWNLOAD_INFO_SHOWN_KEY, true).apply()
    }
    BackHandler(enabled = showAreaPicker) {
        when {
            areaSearchQuery.isNotBlank() -> onAreaSearchQueryChange("")
            selectedAreaFolder != null -> onSelectedAreaFolderChange(null)
            else -> onAreaPickerOpenChange(false)
        }
    }

    DeleteConfirmationDialog(
        visible = bundlePendingDelete != null,
        title = "Delete bundle?",
        message = "This will remove the downloaded files for ${bundlePendingDelete?.areaLabel.orEmpty()}.",
        onConfirm = {
            bundlePendingDelete?.let(viewModel::deleteBundle)
            bundlePendingDelete = null
        },
        onDismiss = { bundlePendingDelete = null },
    )
    OamAttributionDialog(
        visible = showOamInfoDialog,
        onDismiss = { dismissOamInfoDialog() },
    )
    DownloadNetworkWarningDialog(
        message = uiState.networkWarningMessage,
        onContinue = viewModel::continueDownloadWithoutWifi,
        onDismiss = viewModel::dismissNetworkWarning,
    )
    RefreshBundleDialog(
        check = uiState.refreshPrompt,
        onConfirm = viewModel::confirmRefreshBundle,
        onDismiss = viewModel::dismissRefreshPrompt,
    )
    AreaSearchDialog(
        visible = showAreaSearchDialog,
        initialQuery = areaSearchQuery,
        onDismiss = { showAreaSearchDialog = false },
        onApply = { query ->
            onAreaSearchQueryChange(query.trim())
            onSelectedAreaFolderChange(null)
            showAreaSearchDialog = false
        },
    )

    ScreenScaffold(scrollState = listState) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DownloadHeader(
                isDownloading = uiState.isDownloading,
                isCheckingUpdates = uiState.isCheckingUpdates,
                hasInstalledBundles = uiState.installedBundles.isNotEmpty(),
                refreshMode = refreshMode,
                deleteMode = deleteMode,
                topPadding = headerTopSafePadding,
                bottomPadding = headerBottomPadding,
                actionButtonSize = headerActionButtonSize,
                actionIconSize = headerActionIconSize,
                actionSpacing = headerActionSpacing,
                onInfoClick = { showOamInfoDialog = true },
                onRefreshModeClick = {
                    refreshMode = !refreshMode
                    if (refreshMode) {
                        deleteMode = false
                    }
                },
                onDeleteModeClick = {
                    deleteMode = !deleteMode
                    if (deleteMode) {
                        refreshMode = false
                    }
                },
            )

            ScalingLazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                state = listState,
                contentPadding =
                    PaddingValues(
                        start = listHorizontalPadding,
                        end = listHorizontalPadding,
                        top = listTopPadding,
                        bottom = listBottomPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(rowSpacing),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showAreaPicker) {
                    downloadAreaPickerItems(
                        areaSearchQueryNormalized = areaSearchQueryNormalized,
                        selectedAreaLabel = selectedAreaLabel,
                        visiblePickerAreas = visiblePickerAreas,
                        areaFolders = areaFolders,
                        selectedAreaFolder = selectedAreaFolder,
                        selectedAreaIds = uiState.selectedAreaIds,
                        selection = uiState.selection,
                        onDone = { onAreaPickerOpenChange(false) },
                        onOpenSearch = { showAreaSearchDialog = true },
                        onClearSearch = { onAreaSearchQueryChange("") },
                        onSelectedAreaFolderChange = onSelectedAreaFolderChange,
                        onToggleArea = viewModel::toggleArea,
                    )
                } else {
                    item {
                        DownloadChip(
                            label = selectedAreaLabel,
                            secondaryLabel = selectedAreaSecondaryLabel,
                            icon = Icons.Filled.UnfoldMore,
                            onClick = {
                                if (!uiState.isDownloading) {
                                    onAreaPickerOpenChange(true)
                                }
                            },
                        )
                    }

                    item {
                        DownloadSummary(
                            areas = selectedAreas,
                            selection = uiState.selection,
                            estimatedSize = estimatedSize,
                        )
                    }

                    if (uiState.isDownloading) {
                        item {
                            DownloadProgress(uiState)
                        }
                        item {
                            DownloadActionButton(
                                label = "Pause",
                                icon = Icons.Filled.Pause,
                                enabled = true,
                                height = actionButtonHeight,
                                iconSize = actionButtonIconSize,
                                onClick = viewModel::pauseDownload,
                            )
                        }
                        item {
                            DownloadActionButton(
                                label = "Cancel",
                                icon = Icons.Filled.Close,
                                enabled = true,
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                height = actionButtonHeight,
                                iconSize = actionButtonIconSize,
                                onClick = viewModel::cancelDownload,
                            )
                        }
                    } else {
                        item {
                            DownloadActionButton(
                                label = if (uiState.isPausedDownload) "Resume" else "Download",
                                icon = Icons.Filled.Download,
                                enabled = uiState.selection.canDownload && selectedAreas.isNotEmpty(),
                                height = actionButtonHeight,
                                iconSize = actionButtonIconSize,
                                onClick = viewModel::downloadSelectedBundle,
                            )
                        }
                    }

                    item {
                        Text(
                            text = "Installed bundles",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                            maxLines = 1,
                        )
                    }

                    if (uiState.installedBundles.isEmpty()) {
                        item {
                            Text(
                                text = "No bundles installed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        if (refreshMode) {
                            item {
                                Text(
                                    text =
                                        if (uiState.isCheckingUpdates) {
                                            "Checking bundle"
                                        } else {
                                            "Tap a bundle to check updates"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        if (deleteMode) {
                            item {
                                Text(
                                    text = "Tap a bundle to delete it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        uiState.installedBundles.forEach { bundle ->
                            item {
                                InstalledBundleRow(
                                    bundle = bundle,
                                    refreshMode = refreshMode,
                                    deleteMode = deleteMode,
                                    onRefresh = {
                                        if (!uiState.isDownloading && refreshMode) {
                                            viewModel.checkBundleForRefresh(bundle)
                                        }
                                    },
                                    onDelete = {
                                        if (!uiState.isDownloading && deleteMode) {
                                            bundlePendingDelete = bundle
                                        }
                                    },
                                )
                            }
                        }
                    }

                    if (!uiState.isDownloading) {
                        uiState.statusMessage?.let { message ->
                            item {
                                StatusText(
                                    text = message,
                                    error = false,
                                )
                            }
                        }
                    }
                    uiState.errorMessage?.let { message ->
                        item {
                            StatusText(
                                text = message,
                                error = true,
                            )
                        }
                    }
                }
            }

            Text(
                text = "Bundle: ${uiState.selection.compactLabel()}",
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize = footerTextSize,
                        lineHeight = footerLineHeight,
                    ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = footerHorizontalPadding, vertical = 1.dp),
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = settingsBottomPadding),
            ) {
                IconButton(
                    onClick = onOpenSettings,
                    enabled = !uiState.isDownloading,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(settingsButtonSize),
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.8f),
                            contentColor = Color.White,
                            disabledContainerColor = Color.Black.copy(alpha = 0.32f),
                            disabledContentColor = Color.White.copy(alpha = 0.38f),
                        ),
                ) {
                    Material3Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Download settings",
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadHeader(
    isDownloading: Boolean,
    isCheckingUpdates: Boolean,
    hasInstalledBundles: Boolean,
    refreshMode: Boolean,
    deleteMode: Boolean,
    topPadding: Dp,
    bottomPadding: Dp,
    actionButtonSize: Dp,
    actionIconSize: Dp,
    actionSpacing: Dp,
    onInfoClick: () -> Unit,
    onRefreshModeClick: () -> Unit,
    onDeleteModeClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = topPadding, bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(actionSpacing),
        ) {
            Text(
                text = "Download",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(actionSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderActionButton(
                    icon = Icons.Filled.Info,
                    contentDescription = "OpenAndroMaps info",
                    buttonSize = actionButtonSize,
                    iconSize = actionIconSize,
                    onClick = onInfoClick,
                )
                HeaderActionButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = if (refreshMode) "Exit refresh mode" else "Enter refresh mode",
                    buttonSize = actionButtonSize,
                    iconSize = actionIconSize,
                    enabled = hasInstalledBundles && !isDownloading && !isCheckingUpdates,
                    selected = refreshMode,
                    onClick = onRefreshModeClick,
                )
                HeaderActionButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = if (deleteMode) "Exit delete mode" else "Enter delete mode",
                    buttonSize = actionButtonSize,
                    iconSize = actionIconSize,
                    enabled = hasInstalledBundles && !isDownloading,
                    selected = deleteMode,
                    danger = true,
                    onClick = onDeleteModeClick,
                )
            }
        }
    }
}

@Composable
private fun HeaderActionButton(
    icon: ImageVector,
    contentDescription: String,
    buttonSize: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
    danger: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(buttonSize),
        colors =
            IconButtonDefaults.iconButtonColors(
                containerColor =
                    when {
                        selected && danger -> MaterialTheme.colorScheme.errorContainer
                        selected -> MaterialTheme.colorScheme.primaryContainer
                        else -> Color.Black.copy(alpha = 0.7f)
                    },
                contentColor =
                    when {
                        selected && danger -> MaterialTheme.colorScheme.onErrorContainer
                        selected -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> Color.White
                    },
                disabledContainerColor = Color.Black.copy(alpha = 0.32f),
                disabledContentColor = Color.White.copy(alpha = 0.38f),
            ),
    ) {
        Material3Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun DownloadActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    height: Dp,
    iconSize: Dp,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                disabledContentColor = Color.White.copy(alpha = 0.38f),
            ),
    ) {
        Material3Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DownloadSummary(
    areas: List<OamDownloadArea>,
    selection: OamDownloadSelection,
    estimatedSize: String,
) {
    val fileCountLine =
        if (areas.size > 1) {
            "\nFiles: ${areas.fileCountLabel(selection)}"
        } else {
            ""
        }

    Text(
        text = "Size: $estimatedSize$fileCountLine",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DownloadProgress(uiState: DownloadUiState) {
    val totalBytes = uiState.totalBytes
    val progressText =
        if (totalBytes != null && totalBytes > 0L) {
            "${formatBytes(uiState.bytesDone)} / ${formatBytes(totalBytes)}"
        } else {
            formatBytes(uiState.bytesDone)
        }
    Text(
        text =
            listOfNotNull(
                uiState.phase,
                uiState.detail,
                progressText.takeIf { uiState.bytesDone > 0L || totalBytes != null },
            ).joinToString("\n"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun InstalledBundleRow(
    bundle: OamInstalledBundle,
    refreshMode: Boolean,
    deleteMode: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    DownloadChip(
        label = bundle.areaLabel,
        secondaryLabel = installedBundleSubtitle(bundle),
        icon =
            when {
                refreshMode -> Icons.Filled.Refresh
                deleteMode -> Icons.Filled.Delete
                else -> Icons.Filled.Check
            },
        selected = !deleteMode && !refreshMode,
        onClick =
            when {
                refreshMode -> onRefresh
                deleteMode -> onDelete
                else -> ({})
            },
    )
}

private fun installedBundleSubtitle(bundle: OamInstalledBundle): String =
    listOfNotNull(
        "Map".takeIf { bundle.mapFileName != null },
        "POI".takeIf { bundle.poiFileName != null },
        "Routing".takeIf { bundle.routingFileNames.isNotEmpty() },
    ).joinToString(" + ").ifBlank { bundle.bundleChoice.label }

private fun OamDownloadSelection.compactLabel(): String =
    when {
        includeMap && includePoi && includeRouting -> "Maps + POI + Routing"
        includeMap && includePoi -> "Maps + POI"
        includeMap && includeRouting -> "Maps + Routing"
        includePoi && includeRouting -> "POI + Routing"
        includeMap -> "Maps"
        includePoi -> "POI"
        includeRouting -> "Routing"
        else -> "None"
    }

private fun List<OamDownloadArea>.estimatedSizeLabel(
    selection: OamDownloadSelection,
): String = estimatedBytes(selection).toSizeLabel(selection)

internal fun OamDownloadArea.areaSizeLabel(
    selection: OamDownloadSelection,
): String = "$continent - ${estimatedBytes(selection).toSizeLabel(selection)}"

private fun List<OamDownloadArea>.estimatedBytes(
    selection: OamDownloadSelection,
): Long = sumOf { it.estimatedBytes(selection) }

private fun OamDownloadArea.estimatedBytes(selection: OamDownloadSelection): Long =
    (if (selection.includeMap) mapSizeBytes else 0L) +
        (if (selection.includePoi) poiSizeBytes else 0L)

private fun List<OamDownloadArea>.fileCountLabel(selection: OamDownloadSelection): String {
    val knownCount =
        size *
            (
                (if (selection.includeMap) 1 else 0) +
                    (if (selection.includePoi) 1 else 0)
            )
    return when {
        selection.includeRouting && knownCount == 0 -> "routing"
        selection.includeRouting -> "$knownCount+"
        else -> knownCount.toString()
    }
}

private fun Long.toSizeLabel(selection: OamDownloadSelection): String =
    when {
        selection.includeRouting && this > 0L -> "${formatBytes(this)} + routing"
        selection.includeRouting -> "routing"
        else -> formatBytes(this)
    }

private fun List<OamDownloadArea>.selectedAreaLabel(): String =
    when (size) {
        0 -> "Pick area"
        1 -> first().region
        2 -> joinToString(" + ") { it.region }
        else -> "$size areas selected"
    }

private fun List<OamDownloadArea>.selectedAreaSecondaryLabel(): String =
    when (size) {
        0 -> "No area selected"
        1 -> "1 area selected"
        else -> "$size areas selected"
    }

@Composable
private fun StatusText(
    text: String,
    error: Boolean,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color =
            if (error) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f)
            },
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
internal fun DownloadChip(
    label: String,
    secondaryLabel: String,
    icon: ImageVector,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        label = label,
        secondaryLabel = secondaryLabel,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(ChipDefaults.IconSize),
            )
        },
        colors =
            ChipDefaults.secondaryChipColors(
                backgroundColor = if (selected) SelectedChipBackground else ChipBackground,
                contentColor = ChipContent,
                secondaryContentColor = ChipSecondaryContent,
                iconColor = if (selected) SelectedChipIcon else ChipIcon,
            ),
        onClick = onClick,
    )
}

private fun formatBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    if (safeBytes < 1024L) return "$safeBytes B"
    val kib = safeBytes / 1024.0
    if (kib < 1024.0) return "${kib.formatOneDecimal()} KB"
    val mib = kib / 1024.0
    if (mib < 1024.0) return "${mib.formatOneDecimal()} MB"
    return "${(mib / 1024.0).formatOneDecimal()} GB"
}

private fun Double.formatOneDecimal(): String = String.format(java.util.Locale.US, "%.1f", this)

private val ChipBackground = Color(0xFF222A33)
private val SelectedChipBackground = Color(0xFF1F4656)
private val ChipContent = Color(0xFFF4F7FB)
private val ChipSecondaryContent = Color(0xFFC7D2DE)
private val ChipIcon = Color(0xFF9DB1C7)
private val SelectedChipIcon = Color(0xFF7FE4C8)

private const val DOWNLOAD_INFO_PREFS = "download_screen_info_prefs"
private const val DOWNLOAD_INFO_SHOWN_KEY = "oam_info_shown"
