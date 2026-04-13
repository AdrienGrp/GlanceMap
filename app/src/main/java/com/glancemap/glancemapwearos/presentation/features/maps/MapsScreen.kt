@file:OptIn(
    com.google.android.horologist.annotations.ExperimentalHorologistApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.glancemap.glancemapwearos.presentation.features.maps

import android.content.Context
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.R
import com.glancemap.glancemapwearos.presentation.features.maps.theme.ThemeViewModel
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolBusySpinner
import com.glancemap.glancemapwearos.presentation.navigation.WatchRoutes
import com.glancemap.glancemapwearos.presentation.ui.DeleteConfirmationDialog
import com.glancemap.glancemapwearos.presentation.ui.RenameValueDialog
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec
import com.glancemap.glancemapwearos.presentation.ui.rememberWearScreenSize
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import kotlinx.coroutines.delay
import java.util.Locale

private const val MAPS_HELP_PREFS = "maps_screen_help_prefs"
private const val DEM_INTRO_SHOWN_KEY = "dem_intro_shown"
private val MAP_DATA_BADGE_SIZE = 26.dp
private val MAP_DATA_ICON_SIZE = 15.dp

@Composable
fun MapsScreen(
    navController: NavHostController,
    mapViewModel: MapViewModel,
    themeViewModel: ThemeViewModel,
) {
    val context = LocalContext.current
    val screenSize = rememberWearScreenSize()
    val adaptive = rememberWearAdaptiveSpec()
    val mapFiles by mapViewModel.mapFiles.collectAsState()
    val routingPackFiles by mapViewModel.routingPackFiles.collectAsState()
    val selectedMapPath by mapViewModel.selectedMapPath.collectAsState()
    val demDownloadState by themeViewModel.demDownloadUiState.collectAsState()

    // Explicit state (no "by" delegation to avoid the error you saw)
    val showDeleteDialogState = remember { mutableStateOf(false) }
    val mapToDeleteState = remember { mutableStateOf<MapFileState?>(null) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showFirstEntryDemDialog by remember { mutableStateOf(false) }
    var isDeleteMode by remember { mutableStateOf(false) }
    var isRenameMode by remember { mutableStateOf(false) }
    var visibleDemStatusMessage by remember { mutableStateOf("") }
    var lastShownDemCompletionAt by remember { mutableStateOf(demDownloadState.lastCompletedAtMillis) }
    var showDemNetworkErrorDialog by remember { mutableStateOf(false) }
    var demNetworkErrorMessage by remember { mutableStateOf("") }
    var mapToRename by remember { mutableStateOf<MapFileState?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInProgress by remember { mutableStateOf(false) }
    var renameError by remember { mutableStateOf<String?>(null) }
    var routingInfoMap by remember { mutableStateOf<MapFileState?>(null) }
    var showRoutingDataDialog by remember { mutableStateOf(false) }
    var routingPackToDelete by remember { mutableStateOf<RoutingPackFileState?>(null) }
    var showDeleteAllRoutingDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val helpPrefs =
        remember(context) {
            context.getSharedPreferences(MAPS_HELP_PREFS, Context.MODE_PRIVATE)
        }
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
    val emptyStatePadding =
        when (screenSize) {
            WearScreenSize.LARGE -> 16.dp
            WearScreenSize.MEDIUM -> 14.dp
            WearScreenSize.SMALL -> 12.dp
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
    val rowSpacing =
        when (screenSize) {
            WearScreenSize.LARGE -> 8.dp
            WearScreenSize.MEDIUM -> 7.dp
            WearScreenSize.SMALL -> 6.dp
        }
    val headerTopSafePadding = headerTopPadding + adaptive.headerTopSafeInset

    val columnState =
        rememberResponsiveColumnState(
            contentPadding = {
                PaddingValues(
                    top = listTopPadding,
                    start = listHorizontalPadding,
                    end = listHorizontalPadding,
                    bottom = listBottomPadding,
                )
            },
        )

    // 🔁 Ensure we always reload when this screen is first shown
    LaunchedEffect(Unit) {
        mapViewModel.loadMapFiles()
    }
    LaunchedEffect(helpPrefs) {
        val alreadyShown = helpPrefs.getBoolean(DEM_INTRO_SHOWN_KEY, false)
        if (!alreadyShown) {
            showFirstEntryDemDialog = true
        }
    }
    LaunchedEffect(mapFiles.size) {
        if (mapFiles.isEmpty()) {
            isDeleteMode = false
            isRenameMode = false
        }
    }
    LaunchedEffect(demDownloadState.lastCompletedAtMillis) {
        if (demDownloadState.lastCompletedAtMillis <= 0L) return@LaunchedEffect
        mapViewModel.loadMapFiles()
        if (demDownloadState.downloadedTiles > 0) {
            mapViewModel.refreshMapLayer()
        }
    }
    LaunchedEffect(
        demDownloadState.isDownloading,
        demDownloadState.statusMessage,
        demDownloadState.lastCompletedAtMillis,
    ) {
        val message = demDownloadState.statusMessage
        if (message.isBlank()) {
            visibleDemStatusMessage = ""
            return@LaunchedEffect
        }
        if (demDownloadState.isDownloading) {
            visibleDemStatusMessage = message
            return@LaunchedEffect
        }
        val completedAt = demDownloadState.lastCompletedAtMillis
        if (completedAt <= 0L || completedAt <= lastShownDemCompletionAt) {
            visibleDemStatusMessage = ""
            return@LaunchedEffect
        }
        lastShownDemCompletionAt = completedAt
        if (demDownloadState.networkUnavailable) {
            demNetworkErrorMessage =
                if (message.isBlank()) {
                    "No internet on watch. Connect Wi-Fi or phone internet, then retry DEM download."
                } else {
                    message
                }
            showDemNetworkErrorDialog = true
            visibleDemStatusMessage = ""
            return@LaunchedEffect
        }
        visibleDemStatusMessage = message
        delay(5_000L)
        visibleDemStatusMessage = ""
    }

    fun dismissFirstEntryDemDialog() {
        showFirstEntryDemDialog = false
        helpPrefs.edit().putBoolean(DEM_INTRO_SHOWN_KEY, true).apply()
    }

    ScreenScaffold(scrollState = columnState) {
        val showDeleteDialog = showDeleteDialogState.value
        val mapToDelete = mapToDeleteState.value

        DeleteConfirmationDialog(
            visible = showDeleteDialog && mapToDelete != null,
            title = "Delete Map?",
            message = "Delete '${mapToDelete?.name.orEmpty()}'?",
            onConfirm = {
                mapToDelete?.path?.let {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    mapViewModel.deleteMapFile(it)
                }
                showDeleteDialogState.value = false
                mapToDeleteState.value = null
            },
            onDismiss = {
                showDeleteDialogState.value = false
                mapToDeleteState.value = null
            },
        )

        RenameValueDialog(
            visible = showRenameDialog && mapToRename != null,
            title = "Rename map",
            initialValue = mapToRename?.name?.substringBeforeLast(".map") ?: "",
            isSaving = renameInProgress,
            error = renameError,
            onDismiss = {
                if (!renameInProgress) {
                    showRenameDialog = false
                    mapToRename = null
                    renameError = null
                }
            },
            onConfirm = { newName ->
                val target = mapToRename ?: return@RenameValueDialog
                if (renameInProgress) return@RenameValueDialog
                renameInProgress = true
                renameError = null
                mapViewModel.renameMapFile(
                    filePath = target.path,
                    newName = newName,
                ) { result ->
                    renameInProgress = false
                    result
                        .onSuccess {
                            showRenameDialog = false
                            mapToRename = null
                            renameError = null
                        }.onFailure { error ->
                            renameError = error.localizedMessage?.takeIf { it.isNotBlank() }
                                ?: "Failed to rename the map."
                        }
                }
            },
        )

        AlertDialog(
            visible = showDemNetworkErrorDialog,
            onDismissRequest = { showDemNetworkErrorDialog = false },
            title = { Text("DEM download failed") },
            text = {
                Text(
                    demNetworkErrorMessage.ifBlank {
                        "No internet on watch. Connect Wi-Fi or phone internet, then retry DEM download."
                    },
                )
            },
            confirmButton = {
                Button(onClick = { showDemNetworkErrorDialog = false }) {
                    Text("OK")
                }
            },
        )

        AlertDialog(
            visible = routingInfoMap != null,
            onDismissRequest = { routingInfoMap = null },
            title = { Text("Routing coverage") },
            text = {
                Text(routingCoverageMessage(routingInfoMap))
            },
            confirmButton = {
                Button(onClick = { routingInfoMap = null }) {
                    Text("OK")
                }
            },
        )

        DeleteConfirmationDialog(
            visible = routingPackToDelete != null,
            title = "Delete routing pack?",
            message = routingPackToDelete?.name ?: "",
            onConfirm = {
                routingPackToDelete?.let { mapViewModel.deleteRoutingPackFile(it.path) }
                routingPackToDelete = null
            },
            onDismiss = { routingPackToDelete = null },
        )

        DeleteConfirmationDialog(
            visible = showDeleteAllRoutingDialog,
            title = "Delete all routing data?",
            message = routingDeleteAllMessage(routingPackFiles),
            onConfirm = {
                mapViewModel.deleteAllRoutingPackFiles()
                showDeleteAllRoutingDialog = false
            },
            onDismiss = { showDeleteAllRoutingDialog = false },
        )

        AlertDialog(
            visible = showRoutingDataDialog,
            onDismissRequest = { showRoutingDataDialog = false },
            title = { Text("Routing data") },
            text = {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = adaptive.helpDialogMaxHeight)
                            .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = routingPackSummary(routingPackFiles),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    if (routingPackFiles.isEmpty()) {
                        Text(
                            text = "No routing packs installed on the watch.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        routingPackFiles.forEach { pack ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.Start,
                                ) {
                                    Text(
                                        text = pack.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = formatRoutingStorageSize(pack.sizeBytes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.68f),
                                    )
                                }
                                IconButton(
                                    onClick = { routingPackToDelete = pack },
                                    modifier = Modifier.size(26.dp),
                                    colors =
                                        IconButtonDefaults.iconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        ),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete routing pack",
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showRoutingDataDialog = false }) {
                    Text("Done")
                }
            },
            dismissButton = {
                if (routingPackFiles.isNotEmpty()) {
                    Button(
                        onClick = { showDeleteAllRoutingDialog = true },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                    ) {
                        Text("Delete all")
                    }
                }
            },
        )

        AlertDialog(
            visible = showHelpDialog,
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Map Actions") },
            text = {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = adaptive.helpDialogMaxHeight)
                            .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "Toggle a map to activate/deactivate.\n" +
                            "DEM badge: tap icon to download DEM.\n" +
                            "Route badge: tap icon to view routing coverage.\n" +
                            "Use the route button at the top to manage routing packs.\n" +
                            "Grey = missing.\n" +
                            "Amber = partial.\n" +
                            "Green = ready.\n" +
                            "Hill/slope layers need DEM files.\n" +
                            "Use rename mode to rename maps.\n" +
                            "Use delete mode to remove maps.\n" +
                            "Use the gear for map settings.",
                    )
                }
            },
        )
        DemSetupBottomSheet(
            visible = showFirstEntryDemDialog,
            onDismiss = { dismissFirstEntryDemDialog() },
        )

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Header + actions
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = headerTopSafePadding, bottom = headerBottomPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(headerActionSpacing),
                ) {
                    Text(
                        text = "Offline maps",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(headerActionSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showHelpDialog = true
                            },
                            modifier = Modifier.size(headerActionButtonSize),
                            colors =
                                IconButtonDefaults.iconButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.7f),
                                    contentColor = Color.White,
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Map actions help",
                                modifier = Modifier.size(headerActionIconSize),
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                mapViewModel.loadRoutingPackFiles()
                                showRoutingDataDialog = true
                            },
                            modifier = Modifier.size(headerActionButtonSize),
                            colors =
                                IconButtonDefaults.iconButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.7f),
                                    contentColor = Color.White,
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.CallSplit,
                                contentDescription = "Routing data",
                                modifier = Modifier.size(headerActionIconSize),
                            )
                        }
                        if (mapFiles.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val nextRenameMode = !isRenameMode
                                    isRenameMode = nextRenameMode
                                    if (nextRenameMode) {
                                        isDeleteMode = false
                                    }
                                },
                                modifier = Modifier.size(headerActionButtonSize),
                                colors =
                                    IconButtonDefaults.iconButtonColors(
                                        containerColor =
                                            if (isRenameMode) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                Color.Black.copy(alpha = 0.7f)
                                            },
                                        contentColor =
                                            if (isRenameMode) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                Color.White
                                            },
                                    ),
                            ) {
                                Icon(
                                    imageVector = if (isRenameMode) Icons.Default.Close else Icons.Default.Edit,
                                    contentDescription =
                                        if (isRenameMode) {
                                            "Exit rename mode"
                                        } else {
                                            "Enter rename mode"
                                        },
                                    modifier = Modifier.size(headerActionIconSize),
                                )
                            }
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val nextDeleteMode = !isDeleteMode
                                    isDeleteMode = nextDeleteMode
                                    if (nextDeleteMode) {
                                        isRenameMode = false
                                    }
                                },
                                modifier = Modifier.size(headerActionButtonSize),
                                colors =
                                    IconButtonDefaults.iconButtonColors(
                                        containerColor =
                                            if (isDeleteMode) {
                                                MaterialTheme.colorScheme.errorContainer
                                            } else {
                                                Color.Black.copy(alpha = 0.7f)
                                            },
                                        contentColor =
                                            if (isDeleteMode) {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            } else {
                                                Color.White
                                            },
                                    ),
                            ) {
                                Icon(
                                    imageVector = if (isDeleteMode) Icons.Default.Close else Icons.Default.Delete,
                                    contentDescription =
                                        if (isDeleteMode) {
                                            "Exit delete mode"
                                        } else {
                                            "Enter delete mode"
                                        },
                                    modifier = Modifier.size(headerActionIconSize),
                                )
                            }
                        }
                    }
                    if (isRenameMode) {
                        Text(
                            text = "Rename mode",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (isDeleteMode) {
                        Text(
                            text = "Delete mode",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // Middle list
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) {
                ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    columnState = columnState,
                ) {
                    if (mapFiles.isEmpty()) {
                        item {
                            Text(
                                text = "Use the companion phone app to add .map files to your watch.",
                                modifier = Modifier.padding(emptyStatePadding),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    items(mapFiles) { mapFile ->
                        MapItem(
                            mapFile = mapFile,
                            isSelected = selectedMapPath == mapFile.path,
                            onToggle = { isChecked ->
                                mapViewModel.selectMapPath(
                                    if (isChecked) mapFile.path else null,
                                )
                            },
                            onRename = {
                                mapToRename = mapFile
                                showRenameDialog = true
                                renameError = null
                            },
                            onDelete = {
                                mapToDeleteState.value = mapFile
                                showDeleteDialogState.value = true
                            },
                            showDelete = isDeleteMode,
                            showRename = isRenameMode,
                            rowSpacing = rowSpacing,
                            isDemDownloadRunning = demDownloadState.isDownloading,
                            isDemDownloadingForThisMap =
                                demDownloadState.isDownloading &&
                                    demDownloadState.activeMapPath == mapFile.path,
                            onDownloadDem = {
                                themeViewModel.downloadDemForMap(mapFile.path)
                            },
                            onShowRoutingInfo = {
                                routingInfoMap = mapFile
                            },
                        )
                    }
                }
            }

            val showDemStatusBlock =
                demDownloadState.isDownloading || visibleDemStatusMessage.isNotBlank()

            if (showDemStatusBlock) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (demDownloadState.isDownloading && demDownloadState.totalTiles > 0) {
                        RouteToolBusySpinner(size = 30.dp)

                        Text(
                            text = "DEM ${demDownloadState.processedTiles}/${demDownloadState.totalTiles}",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (visibleDemStatusMessage.isNotBlank()) {
                        Text(
                            text = visibleDemStatusMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // Bottom settings button
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = settingsBottomPadding),
            ) {
                IconButton(
                    onClick = { navController.navigate(WatchRoutes.MAP_SETTINGS) },
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(settingsButtonSize),
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.8f),
                            contentColor = Color.White,
                        ),
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Map Settings")
                }
            }
        }
    }
}

@Composable
private fun MapItem(
    mapFile: MapFileState,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    showDelete: Boolean,
    showRename: Boolean,
    rowSpacing: Dp,
    isDemDownloadRunning: Boolean,
    isDemDownloadingForThisMap: Boolean,
    onDownloadDem: () -> Unit,
    onShowRoutingInfo: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement =
            if (showDelete || showRename) {
                Arrangement.spacedBy(rowSpacing)
            } else {
                Arrangement.Start
            },
    ) {
        SwitchButton(
            modifier = Modifier.weight(1f),
            checked = isSelected,
            onCheckedChange = onToggle,
            label = {
                Text(
                    text = mapFile.name,
                    modifier = Modifier.basicMarquee(),
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                )
            },
        )

        if (showRename) {
            Button(
                onClick = onRename,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Rename",
                )
            }
        } else if (showDelete) {
            Button(
                onClick = onDelete,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                )
            }
        } else {
            val demIconTint =
                when {
                    isDemDownloadingForThisMap -> Color(0xFF6EC8FF)
                    mapFile.demReady -> Color(0xFF76E36A)
                    else -> Color(0xFF8E8E8E)
                }
            val routingIconTint =
                when {
                    !mapFile.routingCoverageKnown || mapFile.routingAvailableSegments == 0 -> Color(0xFF8E8E8E)
                    mapFile.routingReady -> Color(0xFF76E36A)
                    else -> Color(0xFFFFC857)
                }
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.wrapContentWidth(),
            ) {
                IconButton(
                    onClick = {
                        if (!mapFile.demReady && !isDemDownloadRunning) {
                            onDownloadDem()
                        }
                    },
                    enabled = !isDemDownloadRunning || mapFile.demReady,
                    modifier = Modifier.size(MAP_DATA_BADGE_SIZE),
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.72f),
                            contentColor = demIconTint,
                            disabledContainerColor = Color.Black.copy(alpha = 0.42f),
                            disabledContentColor = demIconTint.copy(alpha = 0.6f),
                        ),
                ) {
                    when {
                        isDemDownloadingForThisMap -> {
                            Icon(
                                imageVector = Icons.Default.Downloading,
                                contentDescription = "DEM downloading",
                                modifier = Modifier.size(MAP_DATA_ICON_SIZE),
                            )
                        }

                        mapFile.demReady -> {
                            Icon(
                                imageVector = Icons.Default.Landscape,
                                contentDescription = "DEM downloaded",
                                modifier = Modifier.size(MAP_DATA_ICON_SIZE),
                            )
                        }

                        else -> {
                            Icon(
                                painter = painterResource(R.drawable.ic_dem_download),
                                contentDescription = "Download DEM",
                                modifier = Modifier.size(MAP_DATA_ICON_SIZE),
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onShowRoutingInfo,
                    modifier = Modifier.size(MAP_DATA_BADGE_SIZE),
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.72f),
                            contentColor = routingIconTint,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CallSplit,
                        contentDescription = "Routing coverage",
                        modifier = Modifier.size(MAP_DATA_ICON_SIZE),
                    )
                }
            }
        }
    }
}

private fun routingCoverageMessage(mapFile: MapFileState?): String {
    mapFile ?: return "Routing coverage unavailable."
    if (!mapFile.routingCoverageKnown) {
        return "Routing coverage is unavailable for this map."
    }
    if (mapFile.routingRequiredSegments == 0) {
        return "No routing packs are required for this map."
    }

    val summary = "${mapFile.routingAvailableSegments}/${mapFile.routingRequiredSegments} routing packs available."
    return when {
        mapFile.routingReady -> {
            "Routing is ready for this map.\n$summary"
        }

        mapFile.routingAvailableSegments > 0 -> {
            "Routing is partially available for this map.\n$summary\nTransfer the missing .rd5 packs from the phone to complete coverage."
        }

        else -> {
            "Routing is not available for this map yet.\n$summary\nTransfer .rd5 packs from the phone to enable route creation in this area."
        }
    }
}

private fun routingPackSummary(packs: List<RoutingPackFileState>): String {
    if (packs.isEmpty()) return "No routing packs"
    val totalBytes = packs.sumOf { it.sizeBytes }
    return when (packs.size) {
        1 -> "1 pack · ${formatRoutingStorageSize(totalBytes)}"
        else -> "${packs.size} packs · ${formatRoutingStorageSize(totalBytes)}"
    }
}

private fun routingDeleteAllMessage(packs: List<RoutingPackFileState>): String {
    if (packs.isEmpty()) return "No routing packs installed."
    return "Deletes ${routingPackSummary(packs)} from the watch."
}

private fun formatRoutingStorageSize(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L).toDouble()
    val kb = 1024.0
    val mb = kb * 1024.0
    return when {
        safeBytes >= mb -> String.format(Locale.US, "%.1f MB", safeBytes / mb)
        safeBytes >= kb -> String.format(Locale.US, "%.0f KB", safeBytes / kb)
        else -> "${safeBytes.toLong()} B"
    }
}
