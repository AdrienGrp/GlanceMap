package com.glancemap.glancemapwearos.presentation.features.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon as Material3Icon
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
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAreaPicker by remember { mutableStateOf(false) }
    var bundlePendingDelete by remember { mutableStateOf<OamInstalledBundle?>(null) }
    val listState = rememberScalingLazyListState()
    val selectedArea = uiState.selectedArea
    val estimatedSize =
        when (uiState.selectedBundle) {
            OamBundleChoice.MAP_ONLY -> selectedArea.mapSizeLabel
            OamBundleChoice.MAP_AND_POI -> "${selectedArea.mapSizeLabel} + ${selectedArea.poiSizeLabel}"
        }

    LaunchedEffect(uiState.lastLibraryChangedAtMillis) {
        if (uiState.lastLibraryChangedAtMillis > 0L) {
            onLibraryChanged()
        }
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
                Text(
                    text = if (showAreaPicker) "Select area" else "Download",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (showAreaPicker) {
                item {
                    DownloadChip(
                        label = "Back to bundle",
                        secondaryLabel = "${selectedArea.continent} > ${selectedArea.region}",
                        icon = Icons.Filled.Check,
                        onClick = { showAreaPicker = false },
                    )
                }
                uiState.areas.forEach { area ->
                    item {
                        DownloadChip(
                            label = area.region,
                            secondaryLabel = "${area.continent} - ${area.mapSizeLabel}",
                            icon = if (area.id == uiState.selectedAreaId) Icons.Filled.Check else Icons.Filled.Map,
                            selected = area.id == uiState.selectedAreaId,
                            onClick = {
                                viewModel.selectArea(area.id)
                                showAreaPicker = false
                            },
                        )
                    }
                }
            } else {
                item {
                    DownloadChip(
                        label = selectedArea.region,
                        secondaryLabel = "${selectedArea.continent} - ${selectedArea.notes}",
                        icon = Icons.Filled.UnfoldMore,
                        onClick = {
                            if (!uiState.isDownloading) {
                                showAreaPicker = true
                            }
                        },
                    )
                }

                item {
                    Text(
                        text = "Bundle",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                        maxLines = 1,
                    )
                }

                OamBundleChoice.entries.forEach { choice ->
                    item {
                        DownloadChip(
                            label = choice.label,
                            secondaryLabel = choice.secondaryLabel,
                            icon = if (choice == OamBundleChoice.MAP_ONLY) Icons.Filled.Map else Icons.Filled.Place,
                            selected = choice == uiState.selectedBundle,
                            onClick = { viewModel.selectBundle(choice) },
                        )
                    }
                }

                item {
                    DownloadSummary(
                        area = selectedArea,
                        selectedBundle = uiState.selectedBundle,
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
private fun DownloadSummary(
    area: OamDownloadArea,
    selectedBundle: OamBundleChoice,
    estimatedSize: String,
) {
    val poiLine =
        when (selectedBundle) {
            OamBundleChoice.MAP_ONLY -> "POI not included"
            OamBundleChoice.MAP_AND_POI -> "POI: ${area.poiSizeLabel}"
        }

    Text(
        text =
            "Source: OpenAndroMaps\n" +
                "Contours: ${area.contourLabel}\n" +
                "Map: ${area.mapSizeLabel}\n" +
                "$poiLine\n" +
                "Size: $estimatedSize",
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
) {
    Button(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(42.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
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
