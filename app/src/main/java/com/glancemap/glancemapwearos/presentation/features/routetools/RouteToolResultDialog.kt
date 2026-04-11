package com.glancemap.glancemapwearos.presentation.features.routetools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.presentation.formatting.DurationFormatter
import com.glancemap.glancemapwearos.presentation.formatting.UnitFormatter
import com.glancemap.glancemapwearos.presentation.ui.RenameValueDialog
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec

@Composable
internal fun RouteToolResultDialog(
    visible: Boolean,
    result: RouteToolSaveResult?,
    isMetric: Boolean,
    renameInProgress: Boolean,
    renameError: String?,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onRenameOpen: () -> Unit,
    onRenameConfirm: (String) -> Unit,
) {
    if (!visible || result == null) return

    val adaptive = rememberWearAdaptiveSpec()
    var showRenameDialog by remember(result.filePath) { mutableStateOf(false) }

    if (showRenameDialog) {
        RenameValueDialog(
            visible = true,
            title = "Rename GPX",
            initialValue = result.displayTitle,
            isSaving = renameInProgress,
            error = renameError,
            onDismiss = {
                if (!renameInProgress) {
                    showRenameDialog = false
                }
            },
            onConfirm = onRenameConfirm,
        )
    }

    val (distanceValue, distanceUnit) = UnitFormatter.formatDistance(result.distanceMeters, isMetric)
    val (gainValue, gainUnit) = UnitFormatter.formatElevation(result.elevationGainMeters, isMetric)
    val (lossValue, lossUnit) = UnitFormatter.formatElevation(result.elevationLossMeters, isMetric)
    val etaValue = DurationFormatter.formatDurationShort(result.estimatedDurationSec)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (adaptive.isRound) 18.dp else 14.dp,
                        vertical = if (adaptive.isRound) 16.dp else 14.dp,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Color.Black.copy(alpha = 0.90f),
                            RoundedCornerShape(adaptive.dialogCornerRadius),
                        ).padding(
                            horizontal = adaptive.dialogHorizontalPadding,
                            vertical = adaptive.dialogVerticalPadding,
                        ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(
                            text = result.displayTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Start,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(34.dp),
                            enabled = !renameInProgress,
                            colors =
                                IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete GPX",
                            )
                        }
                        IconButton(
                            onClick = {
                                onRenameOpen()
                                showRenameDialog = true
                            },
                            modifier = Modifier.size(34.dp),
                            enabled = !renameInProgress,
                            colors =
                                IconButtonDefaults.iconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.10f),
                                    contentColor = Color.White,
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Rename GPX",
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RouteStatTile(
                        modifier = Modifier.weight(1f),
                        label = "Dist",
                        value = "$distanceValue $distanceUnit",
                    )
                    RouteStatTile(
                        modifier = Modifier.weight(1f),
                        label = "Time",
                        value = etaValue,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RouteStatTile(
                        modifier = Modifier.weight(1f),
                        labelIcon = Icons.Default.ArrowUpward,
                        value = "$gainValue $gainUnit",
                    )
                    RouteStatTile(
                        modifier = Modifier.weight(1f),
                        labelIcon = Icons.Default.ArrowDownward,
                        value = "$lossValue $lossUnit",
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(34.dp),
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun RouteStatTile(
    modifier: Modifier,
    label: String? = null,
    labelIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    value: String,
) {
    Column(
        modifier =
            modifier
                .background(
                    Color.White.copy(alpha = 0.10f),
                    RoundedCornerShape(12.dp),
                ).padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        if (labelIcon != null) {
            Icon(
                imageVector = labelIcon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = Color.White.copy(alpha = 0.72f),
            )
        } else if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.72f),
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}
