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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.R
import com.glancemap.glancemapwearos.presentation.navigation.WatchRoutes
import com.glancemap.glancemapwearos.presentation.ui.CompactIconHitTargetButton
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
    val exportUiState by gpxViewModel.exportUiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var isSendMode by remember { mutableStateOf(false) }
    var isDeleteMode by remember { mutableStateOf(false) }
    var isRenameMode by remember { mutableStateOf(false) }
    var selectedSendPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var fileToDelete by remember { mutableStateOf<GpxFileState?>(null) }
    var fileToRename by remember { mutableStateOf<GpxFileState?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInProgress by remember { mutableStateOf(false) }
    var renameError by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val helpPrefs =
        remember(context) {
            context.getSharedPreferences(GPX_HELP_PREFS, android.content.Context.MODE_PRIVATE)
        }
    val listHorizontalPadding =
        when (screenSize) {
            WearScreenSize.LARGE -> 16.dp
            WearScreenSize.MEDIUM -> 14.dp
            WearScreenSize.SMALL -> 12.dp
        }
    val listTopPadding =
        when (screenSize) {
            WearScreenSize.LARGE -> 1.dp
            WearScreenSize.MEDIUM -> 0.dp
            WearScreenSize.SMALL -> 0.dp
        }
    val listBottomPadding =
        when (screenSize) {
            WearScreenSize.LARGE -> 2.dp
            WearScreenSize.MEDIUM -> 1.dp
            WearScreenSize.SMALL -> 0.dp
        }
    val headerTopPadding =
        when (screenSize) {
            WearScreenSize.LARGE -> 8.dp
            WearScreenSize.MEDIUM -> 6.dp
            WearScreenSize.SMALL -> 4.dp
        }
    val headerBottomPadding = 0.dp
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
    val headerActionVisualOffsetY =
        when (screenSize) {
            WearScreenSize.LARGE -> 4.dp
            WearScreenSize.MEDIUM -> 4.dp
            WearScreenSize.SMALL -> 3.dp
        }
    val headerActionSpacing =
        when (screenSize) {
            WearScreenSize.LARGE -> 4.dp
            WearScreenSize.MEDIUM -> 3.dp
            WearScreenSize.SMALL -> 2.dp
        }
    val headerVerticalSpacing =
        when (screenSize) {
            WearScreenSize.LARGE -> (-14).dp
            WearScreenSize.MEDIUM -> (-15).dp
            WearScreenSize.SMALL -> (-16).dp
        }
    val emptyStatePadding =
        when (screenSize) {
            WearScreenSize.LARGE -> 16.dp
            WearScreenSize.MEDIUM -> 14.dp
            WearScreenSize.SMALL -> 12.dp
        }
    val settingsButtonSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 28.dp
            WearScreenSize.MEDIUM -> 26.dp
            WearScreenSize.SMALL -> 24.dp
        }
    val bottomActionBottomPadding = 0.dp
    val bottomActionVisualOffsetY =
        when (screenSize) {
            WearScreenSize.LARGE -> (-6).dp
            WearScreenSize.MEDIUM -> (-5).dp
            WearScreenSize.SMALL -> (-4).dp
        }
    val rowSpacing =
        when (screenSize) {
            WearScreenSize.LARGE -> 8.dp
            WearScreenSize.MEDIUM -> 7.dp
            WearScreenSize.SMALL -> 6.dp
        }
    val secondaryTextSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 10.sp
            WearScreenSize.MEDIUM -> 10.sp
            WearScreenSize.SMALL -> 10.sp
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

    LaunchedEffect(helpPrefs) {
        if (!helpPrefs.getBoolean(GPX_HELP_SHOWN_KEY, false)) {
            showHelpDialog = true
        }
    }
    LaunchedEffect(gpxFiles.size) {
        if (gpxFiles.isEmpty()) {
            isSendMode = false
            isDeleteMode = false
            isRenameMode = false
            selectedSendPaths = emptySet()
        }
    }
    LaunchedEffect(gpxFiles) {
        val existingPaths = gpxFiles.mapTo(mutableSetOf()) { it.path }
        selectedSendPaths = selectedSendPaths.filterTo(mutableSetOf()) { it in existingPaths }
    }

    fun dismissHelpDialog() {
        showHelpDialog = false
        helpPrefs.edit().putBoolean(GPX_HELP_SHOWN_KEY, true).apply()
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
            onDismiss = { dismissHelpDialog() },
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
                    verticalArrangement = Arrangement.spacedBy(headerVerticalSpacing),
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
                        CompactIconHitTargetButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showHelpDialog = true
                            },
                            visualSize = headerActionButtonSize,
                            visualOffsetY = headerActionVisualOffsetY,
                            containerColor = Color.Black.copy(alpha = 0.7f),
                            contentColor = Color.White,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "GPX actions help",
                                modifier = Modifier.size(headerActionIconSize),
                            )
                        }
                        if (gpxFiles.isNotEmpty()) {
                            CompactIconHitTargetButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val nextSendMode = !isSendMode
                                    isSendMode = nextSendMode
                                    if (nextSendMode) {
                                        isRenameMode = false
                                        isDeleteMode = false
                                    } else {
                                        selectedSendPaths = emptySet()
                                    }
                                },
                                visualSize = headerActionButtonSize,
                                visualOffsetY = headerActionVisualOffsetY,
                                containerColor =
                                    if (isSendMode) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        Color.Black.copy(alpha = 0.7f)
                                    },
                                contentColor =
                                    if (isSendMode) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        Color.White
                                    },
                            ) {
                                if (isSendMode) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Exit send mode",
                                        modifier = Modifier.size(headerActionIconSize),
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_mobile_arrow_right),
                                        contentDescription = "Send GPX to phone",
                                        modifier = Modifier.size(headerActionIconSize),
                                    )
                                }
                            }
                        }
                    }
                    if (isSendMode) {
                        Text(
                            text =
                                if (selectedSendPaths.isEmpty()) {
                                    "Select GPX"
                                } else {
                                    "${selectedSendPaths.size} selected"
                                },
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    } else if (isRenameMode) {
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
                                text = "Send GPX files from the companion phone app.",
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
                            onSend = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedSendPaths =
                                    if (gpxFile.path in selectedSendPaths) {
                                        selectedSendPaths - gpxFile.path
                                    } else {
                                        selectedSendPaths + gpxFile.path
                                    }
                            },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                gpxViewModel.showElevationProfile(gpxFile.path)
                            },
                            showSend = isSendMode,
                            isSendSelected = gpxFile.path in selectedSendPaths,
                            showDelete = isDeleteMode,
                            showRename = isRenameMode,
                            exportState = exportUiState.takeIf { it.filePath == gpxFile.path },
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
                        .padding(bottom = bottomActionBottomPadding),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (gpxFiles.isNotEmpty()) {
                        CompactIconHitTargetButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val nextRenameMode = !isRenameMode
                                isRenameMode = nextRenameMode
                                if (nextRenameMode) {
                                    isSendMode = false
                                    selectedSendPaths = emptySet()
                                    isDeleteMode = false
                                }
                            },
                            visualSize = headerActionButtonSize,
                            visualOffsetY = bottomActionVisualOffsetY,
                            containerColor =
                                if (isRenameMode) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Black.copy(alpha = 0.8f)
                                },
                            contentColor =
                                if (isRenameMode) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    Color.White
                                },
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
                    }
                    CompactIconHitTargetButton(
                        onClick = {
                            if (isSendMode) {
                                val paths = selectedSendPaths.toList()
                                if (paths.isNotEmpty()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedSendPaths = emptySet()
                                    isSendMode = false
                                    gpxViewModel.sendGpxFilesToPhone(paths)
                                }
                            } else {
                                navController.navigate(WatchRoutes.GPX_SETTINGS)
                            }
                        },
                        enabled = !isSendMode || (selectedSendPaths.isNotEmpty() && exportUiState.isSending != true),
                        visualSize = settingsButtonSize,
                        visualOffsetY = bottomActionVisualOffsetY,
                        containerColor =
                            if (isSendMode) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Black.copy(alpha = 0.8f)
                            },
                        contentColor =
                            if (isSendMode) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                Color.White
                            },
                        disabledContainerColor = Color.Black.copy(alpha = 0.5f),
                        disabledContentColor = Color.White.copy(alpha = 0.45f),
                    ) {
                        if (isSendMode) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mobile_arrow_right),
                                contentDescription = "Send selected GPX to phone",
                            )
                        } else {
                            Icon(Icons.Default.Settings, contentDescription = "GPX Settings")
                        }
                    }
                    if (gpxFiles.isNotEmpty()) {
                        CompactIconHitTargetButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val nextDeleteMode = !isDeleteMode
                                isDeleteMode = nextDeleteMode
                                if (nextDeleteMode) {
                                    isSendMode = false
                                    selectedSendPaths = emptySet()
                                    isRenameMode = false
                                }
                            },
                            visualSize = headerActionButtonSize,
                            visualOffsetY = bottomActionVisualOffsetY,
                            containerColor =
                                if (isDeleteMode) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    Color.Black.copy(alpha = 0.8f)
                                },
                            contentColor =
                                if (isDeleteMode) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    Color.White
                                },
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
            }
        }
    }
}

private const val GPX_HELP_PREFS = "gpx_screen_help_prefs"
private const val GPX_HELP_SHOWN_KEY = "gpx_help_shown"

@Suppress("CyclomaticComplexMethod", "FunctionNaming", "LongMethod", "LongParameterList")
@Composable
private fun GpxTrackItem(
    gpxFile: GpxFileState,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onSend: () -> Unit,
    onLongPress: () -> Unit,
    showSend: Boolean,
    isSendSelected: Boolean,
    showDelete: Boolean,
    showRename: Boolean,
    exportState: GpxExportUiState?,
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
            if (showSend || showDelete || showRename) {
                Arrangement.spacedBy(rowSpacing)
            } else {
                Arrangement.Start
            },
    ) {
        SwitchButton(
            modifier = Modifier.weight(1f),
            checked = if (showSend) isSendSelected else gpxFile.isActive,
            onCheckedChange = { checked ->
                if (suppressNextToggle) {
                    suppressNextToggle = false
                    return@SwitchButton
                }
                if (showSend) {
                    onSend()
                } else {
                    onToggle(checked)
                }
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
                val exportMessage = exportState?.message
                Text(
                    text = exportMessage ?: "$distValue $distUnit · D+ $elevValue $elevUnit · $eta",
                    modifier = Modifier.basicMarquee(),
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    fontSize = secondaryTextSize,
                )
            },
        )

        if (showSend) {
            CompactIconHitTargetButton(
                onClick = onSend,
                enabled = exportState?.isSending != true,
                visualSize = deleteButtonSize,
                containerColor =
                    if (isSendSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Black.copy(alpha = 0.72f)
                    },
                contentColor =
                    if (isSendSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        Color.White
                    },
            ) {
                if (isSendSelected) {
                    Icon(Icons.Default.Check, contentDescription = "Selected for send")
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_mobile_arrow_right),
                        contentDescription = "Select for send",
                    )
                }
            }
        } else if (showRename) {
            CompactIconHitTargetButton(
                onClick = onRename,
                visualSize = deleteButtonSize,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Rename")
            }
        } else if (showDelete) {
            CompactIconHitTargetButton(
                onClick = onDelete,
                visualSize = deleteButtonSize,
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}
