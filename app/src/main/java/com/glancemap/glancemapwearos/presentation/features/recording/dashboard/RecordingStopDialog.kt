package com.glancemap.glancemapwearos.presentation.features.recording.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.features.recording.TraceRecordingUiState
import com.glancemap.glancemapwearos.presentation.features.recording.buildRecordingTitle
import com.glancemap.glancemapwearos.presentation.ui.CompactIconHitTargetButton
import com.glancemap.glancemapwearos.presentation.ui.RenameValueDialog
import com.glancemap.glancemapwearos.presentation.ui.WearActionButtonRole
import com.glancemap.glancemapwearos.presentation.ui.WearActionDialog
import com.glancemap.glancemapwearos.presentation.ui.WearActionDialogButton

@Composable
internal fun RecordingStopPromptCard(
    state: TraceRecordingUiState,
    snapshot: RecordingDashboardSnapshot,
    isMetric: Boolean,
    onDiscard: () -> Unit,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val distance = formattedRecordingMetric(SettingsRepository.RECORDING_METRIC_DISTANCE, snapshot, isMetric)
    val duration = formattedRecordingMetric(SettingsRepository.RECORDING_METRIC_DURATION, snapshot, isMetric)
    val elevationGain = formattedRecordingMetric(SettingsRepository.RECORDING_METRIC_ELEVATION_GAIN, snapshot, isMetric)
    val elevationLoss = formattedRecordingMetric(SettingsRepository.RECORDING_METRIC_ELEVATION_LOSS, snapshot, isMetric)
    val calories = formattedRecordingMetric(SettingsRepository.RECORDING_METRIC_CALORIES, snapshot, isMetric)
    val defaultTitle =
        remember(state.startedAtMillis) {
            buildRecordingTitle(state.startedAtMillis ?: System.currentTimeMillis())
        }
    var draftTitle by remember(defaultTitle) { mutableStateOf(defaultTitle) }
    val shortRecording = isShortRecording(snapshot, state)
    var showRenameDialog by remember(defaultTitle) { mutableStateOf(false) }

    if (showRenameDialog) {
        RenameValueDialog(
            visible = true,
            title = "Rename activity",
            initialValue = draftTitle,
            isSaving = false,
            error = null,
            autoFocusInput = false,
            onDismiss = { showRenameDialog = false },
            onConfirm = { title ->
                val sanitizedTitle = title.trim().take(MAX_RECORDING_TITLE_LENGTH)
                draftTitle = sanitizedTitle.ifBlank { defaultTitle }
                showRenameDialog = false
            },
        )
    }

    WearActionDialog(
        visible = true,
        title = if (shortRecording) "Short recording" else "Save recording",
        onDismissRequest = onCancel,
        backgroundColor = Color.Black,
        buttons =
            listOf(
                WearActionDialogButton(
                    text = "Save",
                    onClick = { onSave(draftTitle.ifBlank { defaultTitle }) },
                ),
                WearActionDialogButton(
                    text = "Discard",
                    onClick = onDiscard,
                    role = WearActionButtonRole.Destructive,
                ),
                WearActionDialogButton(
                    text = "Cancel",
                    onClick = onCancel,
                    role = WearActionButtonRole.Secondary,
                ),
            ),
    ) {
        RecordingDialogTitleRow(
            title = draftTitle,
            onRename = { showRenameDialog = true },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RecordingDialogStatTile(
                modifier = Modifier.weight(1f),
                label = "Dist",
                value = "${distance.value} ${distance.unit.orEmpty()}".trim(),
            )
            RecordingDialogStatTile(
                modifier = Modifier.weight(1f),
                label = "Time",
                value = duration.value,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RecordingDialogStatTile(
                modifier = Modifier.weight(1f),
                labelIcon = Icons.Default.ArrowUpward,
                value = "${elevationGain.value} ${elevationGain.unit.orEmpty()}".trim(),
            )
            RecordingDialogStatTile(
                modifier = Modifier.weight(1f),
                labelIcon = Icons.Default.ArrowDownward,
                value = "${elevationLoss.value} ${elevationLoss.unit.orEmpty()}".trim(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RecordingDialogStatTile(
                modifier = Modifier.weight(1f),
                label = "Calories",
                value = "${calories.value} ${calories.unit.orEmpty()}".trim(),
            )
        }
    }
}

@Composable
private fun RecordingDialogTitleRow(
    title: String,
    onRename: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title.ifBlank { "Recording" },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        CompactIconHitTargetButton(
            onClick = onRename,
            visualSize = 34.dp,
            containerColor = Color.White.copy(alpha = 0.10f),
            contentColor = Color.White,
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Rename activity",
            )
        }
    }
}

@Composable
private fun RecordingDialogStatTile(
    modifier: Modifier,
    label: String? = null,
    labelIcon: ImageVector? = null,
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun isShortRecording(
    snapshot: RecordingDashboardSnapshot,
    state: TraceRecordingUiState,
): Boolean =
    state.points.size < MIN_SAVE_POINT_COUNT ||
        snapshot.distanceMeters < SHORT_RECORDING_DISTANCE_METERS ||
        snapshot.durationSeconds < SHORT_RECORDING_DURATION_SECONDS

private const val MIN_SAVE_POINT_COUNT = 2
private const val SHORT_RECORDING_DISTANCE_METERS = 20.0
private const val SHORT_RECORDING_DURATION_SECONDS = 10.0
private const val MAX_RECORDING_TITLE_LENGTH = 64
