package com.glancemap.glancemapcompanionapp.filepicker

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.glancemap.glancemapcompanionapp.CompanionAdaptiveSpec
import com.glancemap.glancemapcompanionapp.FileTransferUiState
import com.glancemap.glancemapcompanionapp.FileTransferViewModel
import com.glancemap.glancemapcompanionapp.diagnostics.CompanionDiagnosticsEmailComposer
import com.glancemap.glancemapcompanionapp.diagnostics.PhoneDebugCaptureState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DebugCaptureDialog(
    context: Context,
    viewModel: FileTransferViewModel,
    debugCaptureState: PhoneDebugCaptureState,
    onDismiss: () -> Unit,
) {
    val hasSavedPhoneRecording =
        remember(debugCaptureState.active, debugCaptureState.sessionId) {
            if (debugCaptureState.active) {
                false
            } else {
                CompanionDiagnosticsEmailComposer.hasSavedPhoneDiagnostics(context)
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (debugCaptureState.active) {
                    "Phone debug capture"
                } else {
                    "Start phone debug capture?"
                },
            )
        },
        text = {
            Text(
                if (debugCaptureState.active) {
                    "Recording is active on the phone. Stop when you are done and an email draft will open for Glancemap@protonmail.com.\n\nCaptured lines: ${debugCaptureState.bufferedLines}"
                } else {
                    buildString {
                        append("This records companion app transfer logs on the phone. Start it before reproducing a long transfer issue, then stop it to open an email draft to Glancemap@protonmail.com.")
                        if (hasSavedPhoneRecording) {
                            append("\n\nA saved phone recording is available and can be resent.")
                        }
                    }
                },
            )
        },
        confirmButton = {
            if (debugCaptureState.active) {
                TextButton(
                    onClick = {
                        onDismiss()
                        viewModel.stopPhoneDebugCaptureAndSend(context)
                    },
                ) {
                    Text("Stop & email")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasSavedPhoneRecording) {
                        TextButton(
                            onClick = {
                                onDismiss()
                                viewModel.sendLastPhoneDebugCapture(context)
                            },
                        ) {
                            Text("Send last recording")
                        }
                    }
                    TextButton(
                        onClick = {
                            onDismiss()
                            viewModel.startPhoneDebugCapture(context)
                        },
                    ) {
                        Text("Start recording")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (debugCaptureState.active) "Keep recording" else "Cancel")
            }
        },
    )
}

@Composable
internal fun ManagePhoneFilesDialog(
    context: Context,
    viewModel: FileTransferViewModel,
    uiState: FileTransferUiState,
    uiLocked: Boolean,
    isLoadingPhoneStoredFiles: Boolean,
    isClearingPhoneStoredFiles: Boolean,
    onIsClearingPhoneStoredFilesChange: (Boolean) -> Unit,
    phoneStoredFilesSummary: PhoneStoredFilesSummary,
    onRefreshRequested: () -> Unit,
    onDismiss: () -> Unit,
    coroutineScope: CoroutineScope,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isClearingPhoneStoredFiles) onDismiss()
        },
        title = { Text("Manage downloaded files") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Generated POI and routing files stay in the companion app until you clear them.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (isLoadingPhoneStoredFiles) {
                    Text(
                        "Loading phone files...",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    PhoneStoredFilesSummaryRow(
                        label = "Imported POI",
                        group = phoneStoredFilesSummary.poi,
                        context = context,
                    )
                    PhoneStoredFilesSummaryRow(
                        label = "Routing packs (.rd5)",
                        group = phoneStoredFilesSummary.routing,
                        context = context,
                    )
                }
                if (isClearingPhoneStoredFiles) {
                    Text(
                        "Clearing phone files...",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            onIsClearingPhoneStoredFilesChange(true)
                            val result =
                                withContext(Dispatchers.IO) {
                                    clearPhoneStoredFiles(
                                        context = context,
                                        clearPoi = true,
                                        clearRouting = false,
                                    )
                                }
                            removeClearedGeneratedFilesFromSelection(
                                context = context,
                                viewModel = viewModel,
                                uiState = uiState,
                                removedFileNames = result.removedFileNames,
                            )
                            onRefreshRequested()
                            onIsClearingPhoneStoredFilesChange(false)
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled =
                        !uiLocked &&
                            !isLoadingPhoneStoredFiles &&
                            !isClearingPhoneStoredFiles &&
                            phoneStoredFilesSummary.poi.fileCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clear POI")
                }
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            onIsClearingPhoneStoredFilesChange(true)
                            val result =
                                withContext(Dispatchers.IO) {
                                    clearPhoneStoredFiles(
                                        context = context,
                                        clearPoi = false,
                                        clearRouting = true,
                                    )
                                }
                            removeClearedGeneratedFilesFromSelection(
                                context = context,
                                viewModel = viewModel,
                                uiState = uiState,
                                removedFileNames = result.removedFileNames,
                            )
                            onRefreshRequested()
                            onIsClearingPhoneStoredFilesChange(false)
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled =
                        !uiLocked &&
                            !isLoadingPhoneStoredFiles &&
                            !isClearingPhoneStoredFiles &&
                            phoneStoredFilesSummary.routing.fileCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clear routing")
                }
                Button(
                    onClick = {
                        coroutineScope.launch {
                            onIsClearingPhoneStoredFilesChange(true)
                            val result =
                                withContext(Dispatchers.IO) {
                                    clearPhoneStoredFiles(
                                        context = context,
                                        clearPoi = true,
                                        clearRouting = true,
                                    )
                                }
                            removeClearedGeneratedFilesFromSelection(
                                context = context,
                                viewModel = viewModel,
                                uiState = uiState,
                                removedFileNames = result.removedFileNames,
                            )
                            onRefreshRequested()
                            onIsClearingPhoneStoredFilesChange(false)
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled =
                        !uiLocked &&
                            !isLoadingPhoneStoredFiles &&
                            !isClearingPhoneStoredFiles &&
                            (
                                phoneStoredFilesSummary.poi.fileCount > 0 ||
                                    phoneStoredFilesSummary.routing.fileCount > 0
                            ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clear all")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isClearingPhoneStoredFiles,
            ) {
                Text("Close")
            }
        },
    )
}

@Composable
internal fun FilePickerQuickGuideDialog(
    adaptive: CompanionAdaptiveSpec,
    onDismiss: () -> Unit,
) {
    val keepAppOpenInlineContent =
        remember {
            mapOf(
                "keep_app_open_icon" to
                    InlineTextContent(
                        Placeholder(
                            width = 1.em,
                            height = 1.em,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Visibility,
                            contentDescription = "Keep app open",
                        )
                    },
            )
        }
    val keepAppOpenGuideText =
        remember {
            buildAnnotatedString {
                append("• On the watch, enable Keep app open ")
                appendInlineContent("keep_app_open_icon", "[keep app open]")
                append(" for long transfers.")
            }
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick guide") },
        text = {
            val quickGuideScrollState = rememberScrollState()
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = adaptive.quickGuideDialogMaxHeight),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(end = 10.dp)
                            .verticalScroll(quickGuideScrollState),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Best practices", style = MaterialTheme.typography.labelLarge)
                    Text("• For best speed and reliability, keep phone and watch on the same Wi-Fi network or on the phone hotspot.")
                    Text("• For large .map files, keep the watch on charger when possible.")
                    Text(
                        text = keepAppOpenGuideText,
                        inlineContent = keepAppOpenInlineContent,
                    )
                    Text("• The watch screen does not need to stay on, but keeping the app open helps more than keeping the display awake.")
                    Text("• If a transfer stops or stalls, send the same file again. It usually resumes from the partial file already on the watch instead of restarting from zero.")
                    Text("• Without Wi-Fi, Bluetooth fallback supports up to 50 MB per file.")
                    Text("• Check section 5 to confirm the final result.")
                    HorizontalDivider()
                    Text("Transfer flow", style = MaterialTheme.typography.labelLarge)
                    Text("• Section 1: download files if needed.")
                    Text("• Section 2: select your .gpx, .map, .poi, or .rd5 files.")
                    Text("• Section 3: select the watch.")
                    Text("• Section 4: send the files.")
                    Text("• Section 5: review the transfer result.")
                    HorizontalDivider()
                    Text("POI import (Refuges.info 🇫🇷 / OSM)", style = MaterialTheme.typography.labelLarge)
                    Text("• In section 1, tap POI, then \"Import POI (Refuges / OSM)\".")
                    Text("• This creates a .poi file for the selected area.")
                    Text("• Choose the area source: Auto from watch map, Choose refuges.info region, or Enter BBox manually.")
                    Text("• BBox = rectangle area written as west,south,east,north. Example: 5.50,45.10,6.50,45.60.")
                    Text("• Then choose the source and the point types to include.")
                    Text("• Use \"Refresh last import\" to re-import the previous Refuges.info area and types.")
                    HorizontalDivider()
                    Text("Routing data (BRouter)", style = MaterialTheme.typography.labelLarge)
                    Text("• In section 1, tap Routing.")
                    Text("• Choose area source: Auto from watch map or Enter BBox manually.")
                    Text("• The companion downloads the needed .rd5 routing packs.")
                    Text("• BBox = rectangle area written as west,south,east,north. Example: 1.40,42.43,1.79,42.66.")
                }
                PageScrollbar(
                    scrollState = quickGuideScrollState,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        },
    )
}

@Composable
internal fun CancelTransferDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel Transfer?") },
        text = { Text("Are you sure you want to stop sending files?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Yes, Stop", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("No, Continue")
            }
        },
    )
}
