@file:Suppress(
    "FunctionName",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
)

package com.glancemap.glancemapwearos.presentation.features.download

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

internal fun ScalingLazyListScope.downloadAreaPickerItems(
    areaSearchQueryNormalized: String,
    selectedAreaLabel: String,
    visiblePickerAreas: List<OamDownloadArea>,
    areaFolders: List<Pair<String, List<OamDownloadArea>>>,
    selectedAreaFolder: String?,
    selectedAreaIds: Set<String>,
    selection: OamDownloadSelection,
    onDone: () -> Unit,
    onOpenSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onSelectedAreaFolderChange: (String?) -> Unit,
    onToggleArea: (String) -> Unit,
) {
    item {
        DownloadChip(
            label = "Done",
            secondaryLabel =
                if (areaSearchQueryNormalized.isBlank()) {
                    selectedAreaLabel
                } else {
                    "${visiblePickerAreas.size} result(s)"
                },
            icon = Icons.Filled.Check,
            onClick = onDone,
        )
    }

    item {
        DownloadChip(
            label =
                if (areaSearchQueryNormalized.isBlank()) {
                    "Search area"
                } else {
                    "Search: $areaSearchQueryNormalized"
                },
            secondaryLabel =
                if (areaSearchQueryNormalized.isBlank()) {
                    "Type to filter"
                } else {
                    "Tap to edit"
                },
            icon = Icons.Filled.Search,
            onClick = onOpenSearch,
        )
    }

    when {
        areaSearchQueryNormalized.isNotBlank() -> {
            item {
                DownloadChip(
                    label = "Clear search",
                    secondaryLabel = "${visiblePickerAreas.size} area(s)",
                    icon = Icons.Filled.Close,
                    onClick = onClearSearch,
                )
            }
        }
        selectedAreaFolder == null -> {
            areaFolders.forEach { (folder, folderAreas) ->
                val selectedCount = folderAreas.count { it.id in selectedAreaIds }
                item {
                    DownloadChip(
                        label = folder,
                        secondaryLabel =
                            buildString {
                                append(folderAreas.size).append(" area(s)")
                                if (selectedCount > 0) {
                                    append(" - ").append(selectedCount).append(" selected")
                                }
                            },
                        icon = Icons.Filled.Folder,
                        selected = selectedCount > 0,
                        onClick = { onSelectedAreaFolderChange(folder) },
                    )
                }
            }
        }
        else -> {
            item {
                DownloadChip(
                    label = "All regions",
                    secondaryLabel = selectedAreaFolder.orEmpty(),
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = { onSelectedAreaFolderChange(null) },
                )
            }
        }
    }

    if (areaSearchQueryNormalized.isNotBlank() || selectedAreaFolder != null) {
        downloadAreaResultItems(
            visiblePickerAreas = visiblePickerAreas,
            selectedAreaIds = selectedAreaIds,
            selection = selection,
            onToggleArea = onToggleArea,
        )
    }
}

private fun ScalingLazyListScope.downloadAreaResultItems(
    visiblePickerAreas: List<OamDownloadArea>,
    selectedAreaIds: Set<String>,
    selection: OamDownloadSelection,
    onToggleArea: (String) -> Unit,
) {
    if (visiblePickerAreas.isEmpty()) {
        item {
            NoAreaFoundText()
        }
    }
    visiblePickerAreas.forEach { area ->
        val selected = area.id in selectedAreaIds
        item {
            DownloadChip(
                label = area.region,
                secondaryLabel = area.areaSizeLabel(selection),
                icon = if (selected) Icons.Filled.Check else Icons.Filled.Map,
                selected = selected,
                onClick = {
                    onToggleArea(area.id)
                },
            )
        }
    }
}

@Composable
private fun NoAreaFoundText() {
    Text(
        text = "No area found",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
