package com.glancemap.glancemapwearos.presentation.features.recording.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
    val metrics =
        remember(snapshot, isMetric) {
            RECORDING_RECAP_METRIC_IDS.map { metricId ->
                formattedRecordingMetric(metricId, snapshot, isMetric)
            }
        }
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

        RecordingRecapMetricsGrid(metrics = metrics)
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
private fun RecordingRecapMetricsGrid(metrics: List<RecordingMetricValue>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        metrics.chunked(RECORDING_RECAP_COLUMNS).forEach { rowMetrics ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowMetrics.forEach { metric ->
                    RecordingDialogStatTile(
                        modifier = Modifier.weight(1f),
                        metric = metric,
                    )
                }
                repeat(RECORDING_RECAP_COLUMNS - rowMetrics.size) {
                    Column(modifier = Modifier.weight(1f)) {}
                }
            }
        }
    }
}

@Composable
private fun RecordingDialogStatTile(
    modifier: Modifier,
    metric: RecordingMetricValue,
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
        Text(
            text = metric.label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = metric.recapValueText(),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun RecordingMetricValue.recapValueText(): String =
    "${value}${unit?.let { " $it" }.orEmpty()}".trim()

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
private const val RECORDING_RECAP_COLUMNS = 2
private val RECORDING_RECAP_METRIC_IDS =
    listOf(
        SettingsRepository.RECORDING_METRIC_DISTANCE,
        SettingsRepository.RECORDING_METRIC_DURATION,
        SettingsRepository.RECORDING_METRIC_ELEVATION_GAIN,
        SettingsRepository.RECORDING_METRIC_ELEVATION_LOSS,
        SettingsRepository.RECORDING_METRIC_CURRENT_ELEVATION,
        SettingsRepository.RECORDING_METRIC_CURRENT_SPEED,
        SettingsRepository.RECORDING_METRIC_AVERAGE_SPEED,
        SettingsRepository.RECORDING_METRIC_CURRENT_PACE,
        SettingsRepository.RECORDING_METRIC_AVERAGE_PACE,
        SettingsRepository.RECORDING_METRIC_HEART_RATE,
        SettingsRepository.RECORDING_METRIC_STEPS,
        SettingsRepository.RECORDING_METRIC_CADENCE,
        SettingsRepository.RECORDING_METRIC_BAROMETRIC_PRESSURE,
        SettingsRepository.RECORDING_METRIC_CALORIES,
        SettingsRepository.RECORDING_METRIC_ACTIVE_CALORIES,
        SettingsRepository.RECORDING_METRIC_RESTING_CALORIES,
    )
