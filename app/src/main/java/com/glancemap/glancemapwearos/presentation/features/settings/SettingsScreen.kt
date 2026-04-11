package com.glancemap.glancemapwearos.presentation.features.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.core.service.transfer.storage.StalePartialTransferCleaner
import com.glancemap.glancemapwearos.presentation.features.gpx.GpxViewModel
import com.glancemap.glancemapwearos.presentation.features.maps.MapViewModel
import com.glancemap.glancemapwearos.presentation.navigation.WatchRoutes
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel,
    mapViewModel: MapViewModel,
    gpxViewModel: GpxViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listTokens = rememberSettingsListTokens()
    var showUnitsPicker by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearPartialTransfersDialog by remember { mutableStateOf(false) }
    var isClearingCache by remember { mutableStateOf(false) }
    var isLoadingPartialSummary by remember { mutableStateOf(true) }
    var isClearingPartialFiles by remember { mutableStateOf(false) }
    var partialSummary by remember {
        mutableStateOf(StalePartialTransferCleaner.PartialFilesSummary(count = 0, totalBytes = 0L))
    }
    val listState = rememberScalingLazyListState()
    val isMetric by viewModel.isMetric.collectAsState()
    val unitOptions =
        remember {
            listOf(
                true to "Metric",
                false to "Imperial",
            )
        }

    fun refreshPartialSummary() {
        scope.launch {
            isLoadingPartialSummary = true
            partialSummary =
                withContext(Dispatchers.IO) {
                    StalePartialTransferCleaner.scan(context)
                }
            isLoadingPartialSummary = false
        }
    }

    LaunchedEffect(Unit) {
        refreshPartialSummary()
    }

    val partialSummaryText =
        when {
            isLoadingPartialSummary -> "Scanning..."
            partialSummary.count <= 0 -> "No partial files"
            partialSummary.count == 1 -> "1 file · ${formatStorageSize(partialSummary.totalBytes)}"
            else -> "${partialSummary.count} files · ${formatStorageSize(partialSummary.totalBytes)}"
        }

    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding =
                PaddingValues(
                    start = listTokens.horizontalPadding,
                    end = listTokens.horizontalPadding,
                    top = listTokens.topPadding,
                    bottom = listTokens.bottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(listTokens.itemSpacing),
        ) {
            item {
                Text(
                    "General",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item {
                SettingsPickerChip(
                    label = "Units",
                    secondaryLabel = if (isMetric) "Metric" else "Imperial",
                    iconImageVector = Icons.Filled.UnfoldMore,
                    onClick = { showUnitsPicker = true },
                )
            }
            item {
                SettingsSectionChip(
                    label = "GPS settings",
                    onClick = { navController.navigate(WatchRoutes.GPS_SETTINGS) },
                )
            }
            item {
                SettingsSectionChip(
                    label = "Compass settings",
                    onClick = { navController.navigate(WatchRoutes.COMPASS_SETTINGS) },
                )
            }

            item { Text("Screen settings", style = MaterialTheme.typography.titleMedium) }
            item {
                SettingsSectionChip(
                    label = "POI settings",
                    onClick = { navController.navigate(WatchRoutes.POI_SETTINGS) },
                )
            }
            item {
                SettingsSectionChip(
                    label = "GPX settings",
                    onClick = { navController.navigate(WatchRoutes.GPX_SETTINGS) },
                )
            }
            item {
                SettingsSectionChip(
                    label = "Map settings",
                    onClick = { navController.navigate(WatchRoutes.MAP_SETTINGS) },
                )
            }

            item { Text("Advanced settings", style = MaterialTheme.typography.titleMedium) }
            item {
                SettingsSectionChip(
                    label = "Debugging",
                    onClick = { navController.navigate(WatchRoutes.DEBUG_SETTINGS) },
                )
            }
            item {
                SettingsPickerChip(
                    label = if (isClearingCache) "Clearing cache..." else "Clear cache",
                    iconImageVector = null,
                    onClick = {
                        if (!isClearingCache) {
                            showClearCacheDialog = true
                        }
                    },
                )
            }
            item {
                SettingsPickerChip(
                    label =
                        if (isClearingPartialFiles) {
                            "Clearing partial transfer..."
                        } else {
                            "Clear partial transfer"
                        },
                    secondaryLabel = partialSummaryText,
                    iconImageVector = null,
                    onClick = {
                        if (isClearingPartialFiles) return@SettingsPickerChip
                        if (partialSummary.count <= 0) {
                            Toast
                                .makeText(
                                    context,
                                    "No partial transfer files",
                                    Toast.LENGTH_SHORT,
                                ).show()
                        } else {
                            showClearPartialTransfersDialog = true
                        }
                    },
                )
            }
            item {
                SettingsPickerChip(
                    label = "Reset to Default",
                    iconImageVector = null,
                    onClick = { navController.navigate(WatchRoutes.RESET_DEFAULTS_CONFIRM) },
                )
            }
            item {
                SettingsSectionChip(
                    label = "Credits & Legal",
                    iconImageVector = Icons.Filled.Gavel,
                    onClick = { navController.navigate(WatchRoutes.LICENSES) },
                )
            }
        }
    }

    OptionPickerDialog(
        visible = showUnitsPicker,
        title = "Units",
        selectedValue = isMetric,
        options = unitOptions,
        onDismiss = { showUnitsPicker = false },
        onSelect = { selectedMetric -> viewModel.setMetric(selectedMetric) },
    )

    AlertDialog(
        visible = showClearCacheDialog,
        onDismissRequest = {
            if (!isClearingCache) {
                showClearCacheDialog = false
            }
        },
        title = { Text("Clear cache") },
        text = {
            Text(
                "Removes temporary map, GPX, relief and theme caches. Imported maps, GPX and DEM files stay on the watch.",
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isClearingCache) return@Button
                    isClearingCache = true
                    scope.launch {
                        runCatching {
                            val result = mapViewModel.clearDerivedCaches()
                            gpxViewModel.clearDerivedCaches()
                            result
                        }.onSuccess { result ->
                            val deletedMb =
                                if (result.deletedBytes > 0L) {
                                    " (${(result.deletedBytes / (1024.0 * 1024.0)).let { String.format(java.util.Locale.US, "%.1f", it) }} MB)"
                                } else {
                                    ""
                                }
                            Toast
                                .makeText(
                                    context,
                                    "Cache cleared$deletedMb",
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }.onFailure {
                            Toast
                                .makeText(
                                    context,
                                    "Cache cleanup failed",
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                        isClearingCache = false
                        showClearCacheDialog = false
                    }
                },
            ) {
                Text(if (isClearingCache) "Clearing..." else "Clear")
            }
        },
        dismissButton = {
            Button(
                onClick = { showClearCacheDialog = false },
                enabled = !isClearingCache,
            ) {
                Text("Cancel")
            }
        },
    )

    AlertDialog(
        visible = showClearPartialTransfersDialog,
        onDismissRequest = {
            if (!isClearingPartialFiles) {
                showClearPartialTransfersDialog = false
            }
        },
        title = { Text("Clear partial transfers") },
        text = {
            Text(
                "Deletes ${partialSummary.count} partial transfer file(s) and frees ${formatStorageSize(partialSummary.totalBytes)}. Finished files stay on the watch.",
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isClearingPartialFiles) return@Button
                    isClearingPartialFiles = true
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                StalePartialTransferCleaner.clearAll(context)
                            }
                        }.onSuccess { result ->
                            Toast
                                .makeText(
                                    context,
                                    if (result.removedFiles > 0) {
                                        "Cleared ${result.removedFiles} partial file(s)"
                                    } else {
                                        "No partial transfer files"
                                    },
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }.onFailure {
                            Toast
                                .makeText(
                                    context,
                                    "Partial transfer cleanup failed",
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                        isClearingPartialFiles = false
                        showClearPartialTransfersDialog = false
                        refreshPartialSummary()
                    }
                },
            ) {
                Text(if (isClearingPartialFiles) "Clearing..." else "Clear")
            }
        },
        dismissButton = {
            Button(
                onClick = { showClearPartialTransfersDialog = false },
                enabled = !isClearingPartialFiles,
            ) {
                Text("Cancel")
            }
        },
    )
}

private fun formatStorageSize(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L).toDouble()
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        safeBytes >= gb -> String.format(Locale.US, "%.2f GB", safeBytes / gb)
        safeBytes >= mb -> String.format(Locale.US, "%.1f MB", safeBytes / mb)
        safeBytes >= kb -> String.format(Locale.US, "%.0f KB", safeBytes / kb)
        else -> "${bytes.coerceAtLeast(0L)} B"
    }
}
