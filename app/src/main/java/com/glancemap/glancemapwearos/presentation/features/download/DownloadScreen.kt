package com.glancemap.glancemapwearos.presentation.features.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon as Material3Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.presentation.ui.DeleteConfirmationDialog
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.material.Chip

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel,
    onLibraryChanged: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showAreaPicker by remember { mutableStateOf(false) }
    var bundlePendingDelete by remember { mutableStateOf<OamInstalledBundle?>(null) }
    var showOamInfoDialog by remember { mutableStateOf(false) }
    val infoPrefs =
        remember(context) {
            context.getSharedPreferences(DOWNLOAD_INFO_PREFS, android.content.Context.MODE_PRIVATE)
        }
    val listState = rememberScalingLazyListState()
    val selectedAreas = uiState.selectedAreas
    val estimatedSize =
        selectedAreas.estimatedSizeLabel(uiState.selection)
    val selectedAreaLabel = selectedAreas.selectedAreaLabel()

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

    fun dismissOamInfoDialog() {
        showOamInfoDialog = false
        infoPrefs.edit().putBoolean(DOWNLOAD_INFO_SHOWN_KEY, true).apply()
    }

    DeleteConfirmationDialog(
        visible = bundlePendingDelete != null,
        title = "Delete bundle?",
        message = "This will remove the map and POI files for ${bundlePendingDelete?.areaLabel.orEmpty()}.",
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

    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding =
                PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 26.dp,
                    bottom = 32.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (showAreaPicker) "Select area" else "Download",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = { showOamInfoDialog = true },
                        modifier = Modifier.size(26.dp),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.7f),
                                contentColor = Color.White,
                            ),
                    ) {
                        Material3Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "OpenAndroMaps info",
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            if (!showAreaPicker) {
                item {
                    Text(
                        text = "Maps and POIs by OpenAndroMaps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (showAreaPicker) {
                item {
                    DownloadChip(
                        label = "Back to bundle",
                        secondaryLabel = selectedAreaLabel,
                        icon = Icons.Filled.Check,
                        onClick = { showAreaPicker = false },
                    )
                }
                uiState.areas.forEach { area ->
                    val selected = area.id in uiState.selectedAreaIds
                    item {
                        DownloadChip(
                            label = area.region,
                            secondaryLabel = area.areaSizeLabel(uiState.selection),
                            icon = if (selected) Icons.Filled.Check else Icons.Filled.Map,
                            selected = selected,
                            onClick = {
                                viewModel.toggleArea(area.id)
                            },
                        )
                    }
                }
            } else {
                item {
                    DownloadChip(
                        label = selectedAreaLabel,
                        secondaryLabel = "${selectedAreas.size} area(s) selected",
                        icon = Icons.Filled.UnfoldMore,
                        onClick = {
                            if (!uiState.isDownloading) {
                                showAreaPicker = true
                            }
                        },
                    )
                }

                item {
                    DownloadChip(
                        label = "Download settings",
                        secondaryLabel = uiState.selection.label(),
                        icon = Icons.Filled.Settings,
                        onClick = {
                            if (!uiState.isDownloading) {
                                onOpenSettings()
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
                        ActionButton(
                            label = "Pause",
                            icon = Icons.Filled.Close,
                            onClick = viewModel::cancelDownload,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                } else {
                    item {
                        ActionButton(
                            label = "Download",
                            icon = Icons.Filled.Download,
                            onClick = viewModel::downloadSelectedBundle,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            enabled = uiState.selection.canDownload && selectedAreas.isNotEmpty(),
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
                    uiState.installedBundles.forEach { bundle ->
                        item {
                            InstalledBundleRow(
                                bundle = bundle,
                                onDelete = {
                                    if (!uiState.isDownloading) {
                                        bundlePendingDelete = bundle
                                    }
                                },
                            )
                        }
                    }
                }

                uiState.statusMessage?.let { message ->
                    item {
                        StatusText(
                            text = message,
                            error = false,
                        )
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
    }
}

@Composable
private fun OamAttributionDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    AlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = { Text("OpenAndroMaps") },
        text = {
            Text(
                text =
                    "Thanks to OpenAndroMaps for providing free offline maps and POIs.\n\n" +
                        "These downloads come from OpenAndroMaps and are not charged by GlanceMap.\n\n" +
                        "https://www.openandromaps.org",
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        },
    )
}

@Composable
private fun DownloadSummary(
    areas: List<OamDownloadArea>,
    selection: OamDownloadSelection,
    estimatedSize: String,
) {
    val poiLine =
        if (selection.includePoi) {
            "POIs: ${areas.size}"
        } else {
            "POI not included"
        }
    val mapLine =
        if (selection.includeMap) {
            "Maps: ${areas.size}"
        } else {
            "Map not included"
        }
    val routingLine =
        if (selection.includeRouting) {
            "Routing: selected"
        } else {
            "Routing: later"
        }

    Text(
        text =
            "Source: OpenAndroMaps\n" +
                "Contours: ${areas.contourLabel()}\n" +
                "$mapLine\n" +
                "$poiLine\n" +
                "$routingLine\n" +
                "Files: ${areas.fileCount(selection)}\n" +
                "Size: $estimatedSize\n" +
                "Large files take longer. Put the watch on its charger.",
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
    onDelete: () -> Unit,
) {
    DownloadChip(
        label = bundle.areaLabel,
        secondaryLabel = installedBundleSubtitle(bundle),
        icon = Icons.Filled.Check,
        selected = true,
        onClick = {},
    )
    Spacer(Modifier.height(3.dp))
    ActionButton(
        label = "Delete bundle",
        icon = Icons.Filled.Delete,
        onClick = onDelete,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    )
}

private fun installedBundleSubtitle(bundle: OamInstalledBundle): String =
    when {
        bundle.poiFileName != null -> "Map + POI"
        bundle.mapFileName != null -> "Map only"
        else -> bundle.bundleChoice.label
    }

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(42.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
            ),
    ) {
        Material3Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun List<OamDownloadArea>.estimatedSizeLabel(selection: OamDownloadSelection): String =
    formatBytes(sumOf { it.estimatedBytes(selection) })

private fun OamDownloadArea.areaSizeLabel(selection: OamDownloadSelection): String =
    "$continent - ${formatBytes(estimatedBytes(selection))}"

private fun OamDownloadArea.estimatedBytes(selection: OamDownloadSelection): Long =
    (if (selection.includeMap) mapSizeBytes else 0L) +
        (if (selection.includePoi) poiSizeBytes else 0L)

private fun List<OamDownloadArea>.fileCount(selection: OamDownloadSelection): Int =
    size *
        ((if (selection.includeMap) 1 else 0) +
            (if (selection.includePoi) 1 else 0))

private fun List<OamDownloadArea>.selectedAreaLabel(): String =
    when (size) {
        0 -> "Select areas"
        1 -> first().region
        2 -> joinToString(" + ") { it.region }
        else -> "${size} areas selected"
    }

private fun List<OamDownloadArea>.contourLabel(): String =
    firstOrNull()?.contourLabel ?: "included"

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
private fun DownloadChip(
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
