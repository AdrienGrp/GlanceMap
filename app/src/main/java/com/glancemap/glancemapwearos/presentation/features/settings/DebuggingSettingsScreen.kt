package com.glancemap.glancemapwearos.presentation.features.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.core.service.diagnostics.CrashDiagnosticsStore
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.core.service.diagnostics.DiagnosticsEmailHandoff
import com.glancemap.glancemapwearos.core.service.diagnostics.DiagnosticsExporter
import com.glancemap.glancemapwearos.core.service.diagnostics.DiagnosticsSettingsSnapshot
import com.glancemap.glancemapwearos.core.service.diagnostics.EnergyDiagnostics
import com.glancemap.glancemapwearos.core.service.diagnostics.FieldMarkerDiagnostics
import com.glancemap.glancemapwearos.core.service.diagnostics.GnssDiagnostics
import com.glancemap.glancemapwearos.core.service.diagnostics.MapHotPathDiagnostics
import com.glancemap.glancemapwearos.domain.sensors.CompassViewModel
import com.glancemap.glancemapwearos.presentation.features.navigate.motion.MarkerMotionTelemetry
import com.glancemap.glancemapwearos.presentation.features.routetools.RouteToolBusySpinner
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec
import com.glancemap.glancemapwearos.presentation.ui.rememberWearScreenSize
import com.glancemap.shared.transfer.TransferDataLayerContract
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.material.Chip
import com.google.android.horologist.compose.material.ToggleChip
import com.google.android.horologist.compose.material.ToggleChipToggleControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val DEBUG_HELP_PREFS = "debug_settings_help_prefs"
private const val DEBUG_EXPORT_INFO_SHOWN_KEY = "debug_export_info_shown"
private const val DEBUG_INFO_DRAG_DISMISS_PX = 55f
private const val CLEAN_CAPTURE_DEFAULT_LABEL = "Clear all captured logs"
private const val CLEAN_CAPTURE_CLEARED_LABEL = "All captured logs have been cleared."
private const val CLEAN_CAPTURE_RESET_DELAY_MS = 3500L
private const val DIAGNOSTICS_DEFAULT_STATUS = "Export to support email"

private enum class DiagnosticsExportDialogMode {
    GENERATING,
    CHECK_PHONE,
    FAILED,
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun DebuggingSettingsScreen(
    viewModel: SettingsViewModel,
    compassViewModel: CompassViewModel,
    onOpenGeneralSettings: () -> Unit,
) {
    val screenSize = rememberWearScreenSize()
    val listTokens =
        rememberSettingsListTokens(
            compactTop = 12.dp,
            standardTop = 14.dp,
            expandedTop = 16.dp,
            compactBottom = 12.dp,
            standardBottom = 14.dp,
            expandedBottom = 16.dp,
        )
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val gpsIntervalMs by viewModel.gpsInterval.collectAsState()
    val isWatchGpsOnly by viewModel.watchGpsOnly.collectAsState()
    val keepAppOpen by viewModel.keepAppOpen.collectAsState(initial = false)
    val gpsInAmbientMode by viewModel.gpsInAmbientMode.collectAsState()
    val gpsDebugTelemetry by viewModel.gpsDebugTelemetry.collectAsState()
    val gpsPassiveLocationExperiment by viewModel.gpsPassiveLocationExperiment.collectAsState()
    val gpsDebugTelemetryPopupEnabled by viewModel.gpsDebugTelemetryPopupEnabled.collectAsState(initial = true)
    var diagnosticsExportStatus by remember {
        mutableStateOf(DIAGNOSTICS_DEFAULT_STATUS)
    }
    var cleanCaptureStatus by remember {
        mutableStateOf(CLEAN_CAPTURE_DEFAULT_LABEL)
    }
    var cleanCaptureResetToken by remember { mutableStateOf(0L) }
    var exportInProgress by remember { mutableStateOf(false) }
    var showExportInfoDialog by remember { mutableStateOf(false) }
    var exportedDiagnosticsCount by remember { mutableStateOf(0) }
    var exportDialogMode by remember { mutableStateOf<DiagnosticsExportDialogMode?>(null) }
    var exportDialogMessage by remember { mutableStateOf("") }
    val helpPrefs =
        remember(context) {
            context.getSharedPreferences(DEBUG_HELP_PREFS, Context.MODE_PRIVATE)
        }
    val hasExportedDiagnostics = exportedDiagnosticsCount > 0

    val listState = rememberScalingLazyListState()
    val infoButtonSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 24.dp
            WearScreenSize.MEDIUM -> 22.dp
            WearScreenSize.SMALL -> 20.dp
        }
    val infoIconSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 14.dp
            WearScreenSize.MEDIUM -> 13.dp
            WearScreenSize.SMALL -> 12.dp
        }

    LaunchedEffect(gpsDebugTelemetry) {
        DebugTelemetry.setEnabled(gpsDebugTelemetry)
        if (gpsDebugTelemetry) {
            DebugTelemetry.log("DiagnosticsFlow", "capture enabled")
            EnergyDiagnostics.recordSample(
                context = context,
                reason = "capture_toggle_on",
                detail = "source=debug_screen",
            )
        } else {
            EnergyDiagnostics.recordEvent(
                reason = "capture_toggle_off",
                detail = "source=debug_screen",
            )
        }
    }

    LaunchedEffect(helpPrefs) {
        val alreadyShown = helpPrefs.getBoolean(DEBUG_EXPORT_INFO_SHOWN_KEY, false)
        if (!alreadyShown) {
            showExportInfoDialog = true
        }
    }

    LaunchedEffect(context) {
        val existingExportCount =
            withContext(Dispatchers.IO) {
                DiagnosticsExporter.exportedFileCount(context)
            }
        exportedDiagnosticsCount = existingExportCount
        if (existingExportCount > 0 && diagnosticsExportStatus == DIAGNOSTICS_DEFAULT_STATUS) {
            diagnosticsExportStatus = buildRetryReadyLabel(existingExportCount)
        }
    }

    LaunchedEffect(cleanCaptureResetToken) {
        if (cleanCaptureResetToken <= 0L) return@LaunchedEffect
        delay(CLEAN_CAPTURE_RESET_DELAY_MS)
        if (cleanCaptureStatus == CLEAN_CAPTURE_CLEARED_LABEL) {
            cleanCaptureStatus = CLEAN_CAPTURE_DEFAULT_LABEL
        }
    }

    fun dismissExportInfoDialog() {
        showExportInfoDialog = false
        helpPrefs.edit().putBoolean(DEBUG_EXPORT_INFO_SHOWN_KEY, true).apply()
    }

    ScreenScaffold(scrollState = listState) {
        DiagnosticsExportInfoDialog(
            visible = showExportInfoDialog,
            onDismiss = { dismissExportInfoDialog() },
        )
        DiagnosticsExportStatusDialog(
            mode = exportDialogMode,
            message = exportDialogMessage,
            onDismiss = { exportDialogMode = null },
        )

        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            autoCentering = AutoCenteringParams(itemIndex = 2),
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
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = { showExportInfoDialog = true },
                        modifier =
                            Modifier
                                .size(infoButtonSize)
                                .wrapContentSize(align = Alignment.Center),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.7f),
                                contentColor = Color.White,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Diagnostics export info",
                            modifier = Modifier.size(infoIconSize),
                        )
                    }
                }
            }

            item {
                GeneralSettingsShortcutChip(onClick = onOpenGeneralSettings)
            }

            item {
                Chip(
                    label = "1. Clean Previous Logs",
                    secondaryLabel =
                        if (exportInProgress) {
                            "Export in progress..."
                        } else {
                            cleanCaptureStatus
                        },
                    onClick = {
                        if (exportInProgress) return@Chip
                        DebugTelemetry.clear()
                        MarkerMotionTelemetry.clear()
                        EnergyDiagnostics.clear()
                        FieldMarkerDiagnostics.clear()
                        GnssDiagnostics.clear()
                        MapHotPathDiagnostics.clear()
                        CrashDiagnosticsStore.clear(context)
                        DiagnosticsExporter.clearExportedFiles(context)
                        exportedDiagnosticsCount = 0
                        cleanCaptureStatus = CLEAN_CAPTURE_CLEARED_LABEL
                        cleanCaptureResetToken += 1L
                        diagnosticsExportStatus = "Cleared. Start capture."
                    },
                )
            }

            item {
                ToggleChip(
                    checked = gpsDebugTelemetry,
                    onCheckedChanged = {
                        if (exportInProgress) return@ToggleChip
                        viewModel.setGpsDebugTelemetry(it)
                        FieldMarkerDiagnostics.recordMarker(
                            type = if (it) "capture_start" else "capture_stop",
                            note = "source=capture_toggle",
                        )
                        diagnosticsExportStatus =
                            if (it) {
                                "Capturing..."
                            } else if (hasExportedDiagnostics) {
                                buildRetryReadyLabel(exportedDiagnosticsCount)
                            } else if (DebugTelemetry.size() > 0) {
                                "Send to phone"
                            } else {
                                "Capture off."
                            }
                    },
                    label = "2. Start Capturing",
                    secondaryLabel =
                        if (gpsDebugTelemetry) {
                            "Capturing..."
                        } else if (exportInProgress) {
                            "Export in progress..."
                        } else {
                            "Off - tap to start"
                        },
                    toggleControl = ToggleChipToggleControl.Switch,
                )
            }

            if (gpsDebugTelemetry) {
                item {
                    ToggleChip(
                        checked = gpsDebugTelemetryPopupEnabled,
                        onCheckedChanged = {
                            if (exportInProgress) return@ToggleChip
                            viewModel.setGpsDebugTelemetryPopupEnabled(it)
                        },
                        label = "Debug popup",
                        secondaryLabel =
                            if (gpsDebugTelemetryPopupEnabled) {
                                "On while capturing"
                            } else {
                                "Off while capturing"
                            },
                        toggleControl = ToggleChipToggleControl.Switch,
                    )
                }
            }

            item {
                Chip(
                    label =
                        if (hasExportedDiagnostics && !gpsDebugTelemetry) {
                            "3. Resend Diagnostic"
                        } else {
                            "3. Export Diagnostic"
                        },
                    secondaryLabel =
                        if (exportInProgress) {
                            "Exporting..."
                        } else if (gpsDebugTelemetry) {
                            "Stop & export"
                        } else {
                            diagnosticsExportStatus
                        },
                    onClick = {
                        if (exportInProgress) return@Chip
                        coroutineScope.launch {
                            exportInProgress = true
                            exportDialogMode = DiagnosticsExportDialogMode.GENERATING
                            exportDialogMessage = "Generating diagnostics file..."
                            val captureWasEnabled = DebugTelemetry.isEnabled()
                            try {
                                val hasBufferedLogs = DebugTelemetry.size() > 0
                                val hasExistingExport =
                                    withContext(Dispatchers.IO) {
                                        DiagnosticsExporter.latestExportFile(context) != null
                                    }
                                val canExport = captureWasEnabled || hasBufferedLogs || hasExistingExport
                                if (!canExport) {
                                    exportDialogMode = null
                                    diagnosticsExportStatus = "No logs to export. Start capturing first."
                                    return@launch
                                }

                                diagnosticsExportStatus = "Preparing file..."

                                // Freeze capture state immediately to avoid session churn while exporting.
                                if (captureWasEnabled) {
                                    viewModel.setGpsDebugTelemetry(false)
                                    DebugTelemetry.setEnabled(false)
                                }

                                val diagnosticsFile =
                                    withContext(Dispatchers.IO) {
                                        DiagnosticsExporter.export(
                                            context = context,
                                            settings =
                                                DiagnosticsSettingsSnapshot(
                                                    gpsIntervalMs = gpsIntervalMs,
                                                    watchGpsOnly = isWatchGpsOnly,
                                                    keepAppOpen = keepAppOpen,
                                                    gpsInAmbientMode = gpsInAmbientMode,
                                                    gpsDebugTelemetry = captureWasEnabled,
                                                    gpsPassiveLocationExperiment = gpsPassiveLocationExperiment,
                                                ),
                                            reuseLatestIfAvailable = !captureWasEnabled,
                                        )
                                    }
                                exportedDiagnosticsCount =
                                    withContext(Dispatchers.IO) {
                                        DiagnosticsExporter.exportedFileCount(context)
                                    }
                                exportDialogMessage = "Sending diagnostics to your phone..."

                                val subject =
                                    "${TransferDataLayerContract.DIAGNOSTICS_SUBJECT_PREFIX} ${diagnosticsFile.nameWithoutExtension}"

                                // Prefer phone handoff first because many watches have no mail/share app.
                                val handedOff =
                                    withContext(Dispatchers.IO) {
                                        DiagnosticsEmailHandoff.sendToPhone(
                                            context = context,
                                            diagnosticsFile = diagnosticsFile,
                                            subject = subject,
                                        )
                                    }
                                if (handedOff) {
                                    diagnosticsExportStatus = "Check phone. Use button 3 to resend."
                                    exportDialogMode = DiagnosticsExportDialogMode.CHECK_PHONE
                                    exportDialogMessage =
                                        "If you closed the phone prompt, tap button 3 again to resend."
                                    return@launch
                                }
                                exportDialogMode = null

                                val uri =
                                    FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        diagnosticsFile,
                                    )

                                val shareIntent =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_EMAIL,
                                            arrayOf(TransferDataLayerContract.DIAGNOSTICS_SUPPORT_EMAIL),
                                        )
                                        putExtra(Intent.EXTRA_SUBJECT, subject)
                                        putExtra(Intent.EXTRA_TEXT, "Diagnostics export attached from watch.")
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }

                                val hasWatchTargets =
                                    context.packageManager.queryIntentActivities(shareIntent, 0).isNotEmpty()
                                if (!hasWatchTargets) {
                                    diagnosticsExportStatus = buildRetryReadyLabel(exportedDiagnosticsCount)
                                    exportDialogMode = DiagnosticsExportDialogMode.FAILED
                                    exportDialogMessage =
                                        "Couldn't send diagnostics to your phone.\n" +
                                        "Tap Resend Diagnostic to try again."
                                    return@launch
                                }

                                val chooser =
                                    Intent.createChooser(shareIntent, "Send diagnostics").apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                runCatching { context.startActivity(chooser) }
                                    .onSuccess {
                                        diagnosticsExportStatus =
                                            if (captureWasEnabled) {
                                                "Confirm send on phone. Capture off."
                                            } else {
                                                "Confirm send on phone."
                                            }
                                    }.onFailure {
                                        diagnosticsExportStatus = buildRetryReadyLabel(exportedDiagnosticsCount)
                                        exportDialogMode = DiagnosticsExportDialogMode.FAILED
                                        exportDialogMessage =
                                            "Export failed.\nTap Resend Diagnostic to try again."
                                    }
                            } catch (_: ActivityNotFoundException) {
                                diagnosticsExportStatus = buildRetryReadyLabel(exportedDiagnosticsCount)
                                exportDialogMode = DiagnosticsExportDialogMode.FAILED
                                exportDialogMessage =
                                    "Export failed.\nTap Resend Diagnostic to try again."
                            } catch (_: Exception) {
                                diagnosticsExportStatus = buildRetryReadyLabel(exportedDiagnosticsCount)
                                exportDialogMode = DiagnosticsExportDialogMode.FAILED
                                exportDialogMessage =
                                    "Export failed.\nTap Resend Diagnostic to try again."
                            } finally {
                                viewModel.setGpsDebugTelemetry(false)
                                DebugTelemetry.setEnabled(false)
                                if (exportDialogMode == DiagnosticsExportDialogMode.GENERATING) {
                                    exportDialogMode = null
                                }
                                exportInProgress = false
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsExportStatusDialog(
    mode: DiagnosticsExportDialogMode?,
    message: String,
    onDismiss: () -> Unit,
) {
    if (mode == null) return

    val adaptive = rememberWearAdaptiveSpec()
    val dismissible = mode != DiagnosticsExportDialogMode.GENERATING
    val title =
        if (mode == DiagnosticsExportDialogMode.GENERATING) {
            "Preparing diagnostics"
        } else if (mode == DiagnosticsExportDialogMode.FAILED) {
            "Export failed"
        } else {
            "Diagnostic ready - check your phone"
        }

    AlertDialog(
        visible = true,
        onDismissRequest = {
            if (dismissible) {
                onDismiss()
            }
        },
        icon = {
            if (mode == DiagnosticsExportDialogMode.GENERATING) {
                RouteToolBusySpinner(size = 30.dp)
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = adaptive.dialogHorizontalPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = message,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            if (dismissible) {
                Button(onClick = onDismiss) {
                    Text("OK")
                }
            }
        },
    )
}

@Composable
private fun DiagnosticsExportInfoDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val adaptive = rememberWearAdaptiveSpec()
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.foundation.layout.Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Color.Black.copy(alpha = 0.82f),
                        RoundedCornerShape(adaptive.dialogCornerRadius),
                    ).padding(
                        horizontal = adaptive.dialogHorizontalPadding,
                        vertical = adaptive.dialogVerticalPadding,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            var totalDrag = 0f
                            detectVerticalDragGestures(
                                onDragEnd = { totalDrag = 0f },
                                onDragCancel = { totalDrag = 0f },
                            ) { _, dragAmount ->
                                totalDrag += dragAmount
                                if (totalDrag > DEBUG_INFO_DRAG_DISMISS_PX) {
                                    onDismiss()
                                    totalDrag = 0f
                                }
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(26.dp)
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.42f), RoundedCornerShape(50)),
                )
            }
            Text(
                text = "Diagnostics Export",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = adaptive.dialogBodyMaxHeight)
                        .onPreRotaryScrollEvent { event ->
                            val consumed = scrollState.dispatchRawDelta(event.verticalScrollPixels)
                            abs(consumed) > 0.5f
                        }.focusRequester(focusRequester)
                        .focusable()
                        .verticalScroll(scrollState),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        "After export, check your phone.\n" +
                            "The phone opens the email draft with diagnostics attached.\n" +
                            "If you dismiss the phone prompt, come back here and tap Resend.\n" +
                            "If phone handoff is unavailable, the watch needs a mail/share app.",
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun buildRetryReadyLabel(count: Int): String {
    val safeCount = count.coerceAtLeast(1)
    return if (safeCount == 1) {
        "Click to export again · 1 file ready"
    } else {
        "Click to export again · $safeCount files ready"
    }
}
