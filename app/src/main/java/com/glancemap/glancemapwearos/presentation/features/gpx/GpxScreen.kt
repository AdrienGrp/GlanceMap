@file:OptIn(
    com.google.android.horologist.annotations.ExperimentalHorologistApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.glancemap.glancemapwearos.presentation.features.gpx

import android.view.ViewConfiguration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.presentation.navigation.WatchRoutes
import com.glancemap.glancemapwearos.presentation.ui.DeleteConfirmationDialog
import com.glancemap.glancemapwearos.presentation.ui.RenameValueDialog
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec
import com.glancemap.glancemapwearos.presentation.ui.rememberWearScreenSize
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun GpxScreen(
    navController: NavHostController,
    gpxViewModel: GpxViewModel,
    isMetric: Boolean,
) {
    val screenSize = rememberWearScreenSize()
    val adaptive = rememberWearAdaptiveSpec()
    val gpxFiles by gpxViewModel.gpxFiles.collectAsState()
    val elevationProfileUiState by gpxViewModel.elevationProfileUiState.collectAsState()
    val showLongPressTip by gpxViewModel.showLongPressTip.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var isDeleteMode by remember { mutableStateOf(false) }
    var isRenameMode by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<GpxFileState?>(null) }
    var fileToRename by remember { mutableStateOf<GpxFileState?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInProgress by remember { mutableStateOf(false) }
    var renameError by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current
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
    val secondaryTextSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 9.sp
            WearScreenSize.MEDIUM -> 8.sp
            WearScreenSize.SMALL -> 8.sp
        }
    val deleteButtonSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 36.dp
            WearScreenSize.MEDIUM -> 34.dp
            WearScreenSize.SMALL -> 32.dp
        }
    val dialogTextTopPadding =
        when (screenSize) {
            WearScreenSize.LARGE -> 8.dp
            WearScreenSize.MEDIUM -> 6.dp
            WearScreenSize.SMALL -> 4.dp
        }
    val dialogTextBottomPadding =
        when (screenSize) {
            WearScreenSize.LARGE -> 16.dp
            WearScreenSize.MEDIUM -> 14.dp
            WearScreenSize.SMALL -> 12.dp
        }
    val headerTopSafePadding = headerTopPadding + adaptive.headerTopSafeInset

    LaunchedEffect(gpxFiles.size) {
        if (gpxFiles.isEmpty()) {
            isDeleteMode = false
            isRenameMode = false
        }
    }
    LaunchedEffect(showLongPressTip, gpxFiles.isNotEmpty()) {
        if (showLongPressTip && gpxFiles.isNotEmpty()) {
            delay(4000L)
            gpxViewModel.dismissLongPressTipForever()
        }
    }

    val columnState =
        rememberResponsiveColumnState(
            contentPadding = {
                PaddingValues(
                    start = listHorizontalPadding,
                    end = listHorizontalPadding,
                    top = listTopPadding,
                    bottom = listBottomPadding,
                )
            },
        )

    ScreenScaffold(scrollState = columnState) {
        DeleteConfirmationDialog(
            visible = showDeleteDialog,
            title = "Delete Track?",
            message = "Delete '${fileToDelete?.title ?: fileToDelete?.name}'?",
            messageTopPadding = dialogTextTopPadding,
            messageBottomPadding = dialogTextBottomPadding,
            onConfirm = {
                fileToDelete?.path?.let {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    gpxViewModel.deleteGpxFile(it)
                }
                showDeleteDialog = false
                fileToDelete = null
            },
            onDismiss = {
                showDeleteDialog = false
                fileToDelete = null
            },
        )

        RenameValueDialog(
            visible = showRenameDialog && fileToRename != null,
            title = "Rename GPX",
            initialValue = fileToRename?.displayTitle.orEmpty(),
            isSaving = renameInProgress,
            error = renameError,
            onDismiss = {
                if (!renameInProgress) {
                    showRenameDialog = false
                    fileToRename = null
                    renameError = null
                }
            },
            onConfirm = { newName ->
                val target = fileToRename ?: return@RenameValueDialog
                if (renameInProgress) return@RenameValueDialog
                renameInProgress = true
                renameError = null
                gpxViewModel.renameGpxFile(
                    filePath = target.path,
                    newName = newName,
                ) { result ->
                    renameInProgress = false
                    result
                        .onSuccess {
                            showRenameDialog = false
                            fileToRename = null
                            renameError = null
                        }.onFailure { error ->
                            renameError = error.localizedMessage?.takeIf { it.isNotBlank() }
                                ?: "Failed to rename the GPX."
                        }
                }
            },
        )

        elevationProfileUiState?.let { profile ->
            GpxElevationProfileDialog(
                profile = profile,
                isMetric = isMetric,
                onDismiss = gpxViewModel::dismissElevationProfile,
            )
        }

        GpxHelpBottomSheet(
            visible = showHelpDialog,
            onDismiss = { showHelpDialog = false },
        )

        GpxLongPressTipBottomSheet(
            visible = showLongPressTip && gpxFiles.isNotEmpty(),
            onDismiss = { gpxViewModel.dismissLongPressTipForever() },
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
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
                        text = "GPX Tracks",
                        style =
                            if (adaptive.isCompact) {
                                MaterialTheme.typography.titleSmall
                            } else {
                                MaterialTheme.typography.titleMedium
                            },
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
                                contentDescription = "GPX actions help",
                                modifier = Modifier.size(headerActionIconSize),
                            )
                        }
                        if (gpxFiles.isNotEmpty()) {
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

            // Middle list (takes all remaining space)
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
                    if (gpxFiles.isEmpty()) {
                        item {
                            Text(
                                text = "Use the companion phone app to send GPX files to your watch.",
                                modifier = Modifier.padding(emptyStatePadding),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    items(gpxFiles, key = { it.path }) { gpxFile ->
                        GpxTrackItem(
                            gpxFile = gpxFile,
                            onToggle = {
                                // ignore bool, just let VM toggle internally
                                gpxViewModel.toggleGpxFile(gpxFile.path)
                            },
                            onDelete = {
                                fileToDelete = gpxFile
                                showDeleteDialog = true
                            },
                            onRename = {
                                fileToRename = gpxFile
                                showRenameDialog = true
                                renameError = null
                            },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                gpxViewModel.showElevationProfile(gpxFile.path)
                            },
                            showDelete = isDeleteMode,
                            showRename = isRenameMode,
                            isMetric = isMetric,
                            rowSpacing = rowSpacing,
                            secondaryTextSize = secondaryTextSize,
                            deleteButtonSize = deleteButtonSize,
                        )
                    }
                }
            }

            // Bottom actions
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = settingsBottomPadding),
            ) {
                IconButton(
                    onClick = { navController.navigate(WatchRoutes.GPX_SETTINGS) },
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
                    Icon(Icons.Default.Settings, contentDescription = "GPX Settings")
                }
            }
        }
    }
}

@Composable
private fun GpxTrackItem(
    gpxFile: GpxFileState,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onLongPress: () -> Unit,
    showDelete: Boolean,
    showRename: Boolean,
    isMetric: Boolean,
    rowSpacing: Dp,
    secondaryTextSize: TextUnit,
    deleteButtonSize: Dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val longPressHandler by rememberUpdatedState(onLongPress)
    val longPressTimeoutMs = remember { ViewConfiguration.getLongPressTimeout().toLong() }
    var suppressNextToggle by remember(gpxFile.path) { mutableStateOf(false) }

    LaunchedEffect(interactionSource, gpxFile.path, longPressTimeoutMs) {
        var trackedPress: PressInteraction.Press? = null
        var longPressJob: Job? = null

        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    trackedPress = interaction
                    suppressNextToggle = false
                    longPressJob?.cancel()
                    longPressJob =
                        launch {
                            delay(longPressTimeoutMs)
                            if (trackedPress == interaction) {
                                suppressNextToggle = true
                                longPressHandler()
                            }
                        }
                }

                is PressInteraction.Release -> {
                    if (interaction.press == trackedPress) {
                        trackedPress = null
                        longPressJob?.cancel()
                        longPressJob = null
                    }
                }

                is PressInteraction.Cancel -> {
                    if (interaction.press == trackedPress) {
                        trackedPress = null
                        longPressJob?.cancel()
                        longPressJob = null
                    }
                }
            }
        }
    }

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
            checked = gpxFile.isActive,
            onCheckedChange = { checked ->
                if (suppressNextToggle) {
                    suppressNextToggle = false
                    return@SwitchButton
                }
                onToggle(checked)
            },
            interactionSource = interactionSource,
            label = {
                Text(
                    text = gpxFile.displayTitle,
                    modifier = Modifier.basicMarquee(),
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                )
            },
            secondaryLabel = {
                val (distValue, distUnit) = gpxFile.formattedDistance(isMetric)
                val (elevValue, elevUnit) = gpxFile.formattedElevation(isMetric)
                val eta = gpxFile.formattedEtaShort()
                Text(
                    text = "$distValue $distUnit, D+ $elevValue $elevUnit, $eta",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = secondaryTextSize,
                )
            },
        )

        if (showRename) {
            IconButton(
                onClick = onRename,
                modifier = Modifier.size(deleteButtonSize),
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Rename")
            }
        } else if (showDelete) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(deleteButtonSize),
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}
