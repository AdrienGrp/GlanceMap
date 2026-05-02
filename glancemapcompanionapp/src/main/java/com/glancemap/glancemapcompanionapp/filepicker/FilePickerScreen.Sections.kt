package com.glancemap.glancemapcompanionapp.filepicker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glancemap.glancemapcompanionapp.CompanionAdaptiveSpec
import com.glancemap.glancemapcompanionapp.CompanionWindowClass
import com.glancemap.glancemapcompanionapp.FileTransferUiState
import com.glancemap.glancemapcompanionapp.diagnostics.PhoneDebugCaptureState
import com.glancemap.glancemapcompanionapp.transfer.presentation.TransferTextFormatter

@Composable
internal fun FilePickerDownloadSection(
    context: Context,
    adaptive: CompanionAdaptiveSpec,
    uiLocked: Boolean,
    hasNotificationPermission: Boolean,
    hasBluetoothConnectPermission: Boolean,
    canRefreshLastRefuges: Boolean,
    canRefreshLastRouting: Boolean,
    mapDownloadSources: List<ExternalDownloadSource>,
    showMapSourcesMenu: Boolean,
    onShowMapSourcesMenuChange: (Boolean) -> Unit,
    showRefugesMenu: Boolean,
    onShowRefugesMenuChange: (Boolean) -> Unit,
    showRoutingMenu: Boolean,
    onShowRoutingMenuChange: (Boolean) -> Unit,
    onRequestMissingPermissions: () -> Unit,
    onShowManagePhoneFiles: () -> Unit,
    onShowRefugesDialog: () -> Unit,
    onShowRoutingDialog: () -> Unit,
    onRefreshLastRefuges: () -> Unit,
    onRefreshLastRouting: () -> Unit,
) {
    val isCompactScreen = adaptive.isCompactScreen
    val downloadButtonHeight = adaptive.downloadButtonHeight
    val downloadSectionMinHeight = downloadButtonHeight + 60.dp
    val openUrl: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { error ->
            Log.w("FilePickerScreen", "Unable to open URL: $url", error)
        }
    }

    SectionCard(
        title = "1. Download",
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = downloadSectionMinHeight),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= 33) {
                Text(
                    "Notification permission needed for background transfers.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (!hasBluetoothConnectPermission && Build.VERSION.SDK_INT >= 31) {
                Text(
                    "Bluetooth permission needed to detect and pair with watch.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if ((!hasNotificationPermission && Build.VERSION.SDK_INT >= 33) ||
                (!hasBluetoothConnectPermission && Build.VERSION.SDK_INT >= 31)
            ) {
                OutlinedButton(
                    onClick = onRequestMissingPermissions,
                    enabled = !uiLocked,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Grant missing permissions")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        if (isCompactScreen) 6.dp else 8.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                ) {
                    DownloadActionButton(
                        label = "Maps",
                        buttonHeight = downloadButtonHeight,
                        iconSize = 22.dp,
                        onClick = { onShowMapSourcesMenuChange(true) },
                        enabled = !uiLocked,
                        modifier = Modifier.fillMaxWidth(),
                        icon = {
                            Icon(Icons.Filled.Map, contentDescription = "Map sources")
                        },
                    )
                    DropdownMenu(
                        expanded = showMapSourcesMenu,
                        onDismissRequest = { onShowMapSourcesMenuChange(false) },
                    ) {
                        val orderedCategories =
                            listOf(
                                "Topographic maps",
                                "Non-topographic maps",
                                "Other",
                            )

                        orderedCategories.forEachIndexed { index, category ->
                            val sources = mapDownloadSources.filter { it.category == category }
                            if (sources.isEmpty()) return@forEachIndexed
                            if (index > 0) {
                                HorizontalDivider()
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        category,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                                onClick = {},
                                enabled = false,
                            )
                            sources.forEach { source ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(source.label)
                                            Text(
                                                source.url,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    },
                                    onClick = {
                                        onShowMapSourcesMenuChange(false)
                                        openUrl(source.url)
                                    },
                                )
                            }
                        }
                    }
                }

                DownloadActionButton(
                    label = "GPX",
                    buttonHeight = downloadButtonHeight,
                    iconSize = 22.dp,
                    onClick = { openUrl("https://www.visugpx.com/") },
                    enabled = !uiLocked,
                    modifier = Modifier.weight(1f),
                    icon = {
                        Icon(Icons.Filled.Timeline, contentDescription = "Download GPX")
                    },
                )

                Box(
                    modifier = Modifier.weight(1f),
                ) {
                    DownloadActionButton(
                        label = "POI",
                        buttonHeight = downloadButtonHeight,
                        iconSize = 22.dp,
                        onClick = { onShowRefugesMenuChange(true) },
                        enabled = !uiLocked,
                        modifier = Modifier.fillMaxWidth(),
                        icon = {
                            Icon(Icons.Filled.Place, contentDescription = "Download POI")
                        },
                    )
                    DropdownMenu(
                        expanded = showRefugesMenu,
                        onDismissRequest = { onShowRefugesMenuChange(false) },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Import POI (Refuges / OSM)") },
                            onClick = {
                                onShowRefugesMenuChange(false)
                                onShowRefugesDialog()
                            },
                            enabled = !uiLocked,
                        )
                        if (canRefreshLastRefuges) {
                            DropdownMenuItem(
                                text = { Text("Refresh last import") },
                                onClick = {
                                    onShowRefugesMenuChange(false)
                                    onRefreshLastRefuges()
                                },
                                enabled = !uiLocked,
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.weight(1f),
                ) {
                    DownloadActionButton(
                        label = "Routing",
                        buttonHeight = downloadButtonHeight,
                        iconSize = 22.dp,
                        onClick = { onShowRoutingMenuChange(true) },
                        enabled = !uiLocked,
                        modifier = Modifier.fillMaxWidth(),
                        icon = {
                            Icon(
                                Icons.Filled.Timeline,
                                contentDescription = "Download routing data",
                            )
                        },
                    )
                    DropdownMenu(
                        expanded = showRoutingMenu,
                        onDismissRequest = { onShowRoutingMenuChange(false) },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Download routing data") },
                            onClick = {
                                onShowRoutingMenuChange(false)
                                onShowRoutingDialog()
                            },
                            enabled = !uiLocked,
                        )
                        if (canRefreshLastRouting) {
                            DropdownMenuItem(
                                text = { Text("Refresh last download") },
                                onClick = {
                                    onShowRoutingMenuChange(false)
                                    onRefreshLastRouting()
                                },
                                enabled = !uiLocked,
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onShowManagePhoneFiles,
                enabled = !uiLocked,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Manage downloaded files")
            }
        }
    }
}

@Composable
internal fun FilePickerTransferSection(
    adaptive: CompanionAdaptiveSpec,
    uiState: FileTransferUiState,
    uiLocked: Boolean,
    isAllowedSelection: Boolean,
    transferSessionActive: Boolean,
    cancellingTransfer: Boolean,
    waitingForReconnect: Boolean,
    debugCaptureState: PhoneDebugCaptureState,
    onSend: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onCancelRequested: () -> Unit,
) {
    SectionCard(
        title = "4. Transfer",
        modifier =
            if (adaptive.useCompactPageLayout) {
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 190.dp)
            } else {
                Modifier.fillMaxWidth()
            },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onSend,
                enabled =
                    uiState.selectedFileUris.isNotEmpty() &&
                        uiState.selectedWatch != null &&
                        !uiLocked &&
                        isAllowedSelection,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        !transferSessionActive -> "Send"
                        cancellingTransfer -> "Stopping..."
                        waitingForReconnect -> "Waiting..."
                        else -> "Sending..."
                    },
                )
            }

            if (transferSessionActive) {
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier.fillMaxWidth(),
                )

                val rawProgressText =
                    when {
                        uiState.isPaused -> {
                            val pauseReason = uiState.pauseReason.trim()
                            val detail = uiState.progressText.trim()
                            when {
                                pauseReason.isNotBlank() &&
                                    detail.isNotBlank() &&
                                    !detail.equals(pauseReason, ignoreCase = true) ->
                                    "Paused • $pauseReason\n$detail"

                                pauseReason.isNotBlank() ->
                                    "Paused • $pauseReason"

                                detail.isNotBlank() ->
                                    "Paused\n$detail"

                                else ->
                                    "Paused"
                            }
                        }

                        else -> uiState.progressText
                    }
                val progressText =
                    TransferTextFormatter.formatCardText(
                        rawProgressText = rawProgressText,
                        statusMessage = uiState.statusMessage,
                        isPaused = uiState.isPaused,
                        canResume = uiState.canResume,
                        showTechnicalDetails = debugCaptureState.active,
                    )
                Text(
                    progressText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (uiState.isPaused && uiState.canResume) {
                        Button(
                            onClick = onResume,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Resume")
                        }
                    } else if (uiState.isPaused) {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Waiting...")
                        }
                    } else if (waitingForReconnect) {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Waiting...")
                        }
                    } else if (cancellingTransfer) {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Stopping...")
                        }
                    } else if (uiState.isTransferring) {
                        OutlinedButton(
                            onClick = onPause,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Pause")
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Button(
                        onClick = onCancelRequested,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
internal fun FilePickerHistorySection(
    adaptive: CompanionAdaptiveSpec,
    uiState: FileTransferUiState,
    historyListState: LazyListState,
    onClearHistory: () -> Unit,
) {
    val historyListHeight =
        if (adaptive.windowClass == CompanionWindowClass.EXPANDED) {
            280.dp
        } else {
            220.dp
        }

    SectionCard(
        title = "5. History",
        modifier =
            if (adaptive.useCompactPageLayout) {
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp)
            } else {
                Modifier.fillMaxWidth()
            },
        containerPadding = PaddingValues(start = 10.dp, top = 0.dp, end = 10.dp, bottom = 10.dp),
        titleContentSpacing = 2.dp,
        headerAction = {
            TextButton(
                onClick = onClearHistory,
                enabled = uiState.history.isNotEmpty(),
            ) {
                Text("Clear")
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Card(
                modifier =
                    if (adaptive.useCompactPageLayout) {
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .height(historyListHeight)
                    },
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    ),
            ) {
                if (uiState.history.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("No transfer history yet.", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        LazyColumn(
                            state = historyListState,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(end = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(uiState.history, key = { it.id }) { item ->
                                HistoryRow(item)
                            }
                        }
                        HistoryScrollbar(
                            listState = historyListState,
                            modifier =
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}
