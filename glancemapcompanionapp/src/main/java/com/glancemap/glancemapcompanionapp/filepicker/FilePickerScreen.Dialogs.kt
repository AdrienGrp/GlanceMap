package com.glancemap.glancemapcompanionapp.filepicker

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ViewComfyAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
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

internal enum class QuickGuideMode {
    GENERAL,
    TRANSFER,
    LIVE_TRACKING,
    MAP_LEGEND,
}

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
    mode: QuickGuideMode,
    onDismiss: () -> Unit,
) {
    val pages =
        remember(mode) {
            when (mode) {
                QuickGuideMode.GENERAL ->
                    listOf(
                        QuickGuidePage(
                            title = "Welcome to GlanceMap Companion",
                            intro =
                                "Use this phone app to prepare and send maps, routes, POI, GPX files, " +
                                    "and routing data to your watch.",
                            lines =
                                listOf(
                                    QUICK_GUIDE_MENU_HEADER_LINE,
                                    "Send to Watch: transfers files to the watch.",
                                    "Live Tracking: shares your phone GPS location.",
                                    "Map Legend opens theme references.",
                                    "Credits & Legal contains privacy, licences, and acknowledgements.",
                                    QUICK_GUIDE_BOOK_ICON_LINE,
                                ),
                        ),
                    )

                QuickGuideMode.TRANSFER ->
                    listOf(
                        QuickGuidePage(
                            title = "Get files ready",
                            lines =
                                listOf(
                                    "Use 1. Download to get Mapsforge OSM .map, POI, GPX, or routing files.",
                                    "Tap 2. Select file(s) to add files from the phone:",
                                    ".map = offline map",
                                    ".poi = points of interest",
                                    ".gpx = route/track",
                                    ".rd5 = offline routing tile",
                                    ".hgt = elevation data for hill shading / slope",
                                ),
                        ),
                        QuickGuidePage(
                            title = "Prepare the watch",
                            lines =
                                listOf(
                                    "Open GlanceMap on the watch and keep it near the phone.",
                                    "For large transfers, charge the watch and use the same Wi-Fi or phone hotspot.",
                                    STAY_OPEN_GUIDE_LINE,
                                    "Without Wi-Fi, Bluetooth can send files up to 50 MB.",
                                ),
                        ),
                        QuickGuidePage(
                            title = "POI & routing areas",
                            lines =
                                listOf(
                                    "For POI and routing downloads, first choose the area you need.",
                                    "POI downloads OSM points of interest.",
                                    "Routing downloads BRouter tiles for offline route calculation.",
                                    "You can choose an area on the map, pick a region.",
                                    "Refresh last import repeats the previous area.",
                                ),
                        ),
                        QuickGuidePage(
                            title = "Send",
                            lines =
                                listOf(
                                    "Tap Send and keep phone and watch close until it finishes.",
                                    "If it stops, send the same file again; it usually resumes " +
                                        "from the partial file already on the watch.",
                                    "History shows each transfer status.",
                                ),
                        ),
                    )

                QuickGuideMode.LIVE_TRACKING ->
                    listOf(
                        QuickGuidePage(
                            title = "Live Tracking",
                            intro = "Use Live Tracking to share your phone GPS location through Arkluz.",
                            lines =
                                listOf(
                                    "Login or join a group before opening settings or starting a session.",
                                    "Choose a participant name, GPS update frequency, alert emails, and no-movement alert settings.",
                                    "Start tracking to begin sending GPS positions from the phone.",
                                    "While tracking is running, use Send to upload a selected GPX or comment.",
                                    "Use View & share tracks to open or share your participant track or the group view.",
                                ),
                        ),
                    )

                QuickGuideMode.MAP_LEGEND ->
                    listOf(
                        QuickGuidePage(
                            title = "Map Legend",
                            intro = "Use this area to open reference material for the map themes used by GlanceMap.",
                            lines =
                                listOf(
                                    "Select the theme you use on the watch.",
                                    "Open the legend PDF or reference page to understand symbols, colors, and paths.",
                                    "These links are external references and may open in your browser.",
                                ),
                        ),
                    )
            }
        }
    var pageIndex by rememberSaveable { mutableStateOf(0) }
    val page = pages[pageIndex]
    val isWelcomePage = mode == QuickGuideMode.GENERAL

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                contentAlignment =
                    if (isWelcomePage) {
                        Alignment.TopCenter
                    } else {
                        Alignment.TopStart
                    },
            ) {
                if (isWelcomePage) {
                    Text(
                        text = page.title,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Quick guide")
                        Text(
                            "Step ${pageIndex + 1} of ${pages.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(if (adaptive.isCompactScreen) 300.dp else 340.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!isWelcomePage) {
                    Text(
                        text = page.title,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Start,
                    )
                }
                page.intro?.let { intro ->
                    Text(
                        text = intro,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign =
                            if (isWelcomePage) {
                                TextAlign.Center
                            } else {
                                TextAlign.Start
                            },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    page.lines.forEach { line ->
                        quickGuideLineText(line = line)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (pages.size > 1) {
                    quickGuidePageIndicator(
                        pageCount = pages.size,
                        selectedPage = pageIndex,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pages.size > 1) {
                    TextButton(
                        onClick = { pageIndex -= 1 },
                        enabled = pageIndex > 0,
                    ) {
                        Text("Back")
                    }
                }
                Button(
                    onClick = {
                        if (pageIndex == pages.lastIndex) {
                            onDismiss()
                        } else {
                            pageIndex += 1
                        }
                    },
                ) {
                    Text(if (pageIndex == pages.lastIndex) "Done" else "Next")
                }
            }
        },
    )
}

@Composable
private fun quickGuideLineText(line: String) {
    when (line) {
        STAY_OPEN_GUIDE_LINE -> stayOpenGuideLineText()
        QUICK_GUIDE_BOOK_ICON_LINE -> quickGuideBookIconLineText()
        QUICK_GUIDE_MENU_HEADER_LINE ->
            Text(
                text = "Menu from the home screen:",
                style = MaterialTheme.typography.bodyMedium,
            )

        else ->
            Text(
                text = "• $line",
                style = MaterialTheme.typography.bodyMedium,
            )
    }
}

@Composable
private fun quickGuideBookIconLineText() {
    Text(
        text =
            buildAnnotatedString {
                append("Tap the ")
                appendInlineContent(GUIDE_BOOK_ICON_ID, "[book]")
                append(" book icon in the top-right corner of each area to open its quick guide again.")
            },
        inlineContent = bookGuideInlineContent(),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun stayOpenGuideLineText() {
    Text(
        text =
            buildAnnotatedString {
                append("• Tap ")
                appendInlineContent(GUIDE_TOOLS_ICON_ID, "[tools]")
                append(" tools, then ")
                appendInlineContent(GUIDE_STAY_ICON_ID, "[stay]")
                append(" Stay. You can also enable Always-on display.")
            },
        inlineContent = stayOpenGuideInlineContent(),
        style = MaterialTheme.typography.bodyMedium,
    )
}

private fun stayOpenGuideInlineContent(): Map<String, InlineTextContent> =
    mapOf(
        GUIDE_TOOLS_ICON_ID to guideInlineIcon(Icons.Filled.ViewComfyAlt, "Tools"),
        GUIDE_STAY_ICON_ID to guideInlineIcon(Icons.Filled.Visibility, "Stay"),
    )

private fun bookGuideInlineContent(): Map<String, InlineTextContent> =
    mapOf(
        GUIDE_BOOK_ICON_ID to guideInlineIcon(Icons.AutoMirrored.Filled.MenuBook, "Quick Guide"),
    )

private fun guideInlineIcon(
    imageVector: ImageVector,
    contentDescription: String,
): InlineTextContent =
    InlineTextContent(
        Placeholder(
            width = 1.1.em,
            height = 1.1.em,
            placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

@Composable
private fun quickGuidePageIndicator(
    pageCount: Int,
    selectedPage: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(pageCount) { index ->
            Text(
                text = "•",
                style = MaterialTheme.typography.titleMedium,
                color =
                    if (index == selectedPage) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    },
            )
        }
    }
}

private data class QuickGuidePage(
    val title: String,
    val intro: String? = null,
    val lines: List<String>,
)

private const val GUIDE_TOOLS_ICON_ID = "guide_tools_icon"
private const val GUIDE_STAY_ICON_ID = "guide_stay_icon"
private const val GUIDE_BOOK_ICON_ID = "guide_book_icon"
private const val STAY_OPEN_GUIDE_LINE = "__stay_open_guide_line__"
private const val QUICK_GUIDE_MENU_HEADER_LINE = "__quick_guide_menu_header_line__"
private const val QUICK_GUIDE_BOOK_ICON_LINE = "__quick_guide_book_icon_line__"

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
