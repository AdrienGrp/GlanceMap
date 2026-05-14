@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
)

package com.glancemap.glancemapcompanionapp.livetracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glancemap.glancemapcompanionapp.CompanionAdaptiveSpec

@Composable
internal fun ColumnScope.MainTrackingContent(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    isConnected: Boolean,
    group: String,
    headerMessage: String?,
    hasSelectedGpx: Boolean,
    selectedGpxName: String,
    comments: String,
    onCommentsChange: (String) -> Unit,
    onPickGpx: () -> Unit,
    onClearGpx: () -> Unit,
    showSendPlan: Boolean,
    canSendPlan: Boolean,
    isSendingPlan: Boolean,
    onSendPlan: () -> Unit,
    sessionState: LiveTrackingUiState,
    updateIntervalSeconds: Int,
    isStartingSession: Boolean,
    validationMessage: String?,
    sendStatusMessage: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    userName: String,
    groupTrackUrl: String,
    userTrackUrl: String,
    recordedTrackDownloadStatusMessage: String?,
    isDownloadingRecordedTrack: Boolean,
    isDeletingTracks: Boolean,
    deleteTracksStatusMessage: String?,
    onDeleteRecordedTracks: () -> Unit,
    onDownloadUserTrack: () -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
    contentSpacing: androidx.compose.ui.unit.Dp,
    isCompactLayout: Boolean,
    isCompactScreen: Boolean,
    adaptive: CompanionAdaptiveSpec,
) {
    val context = LocalContext.current

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = adaptive.helpIconButtonSize),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.size(adaptive.helpIconButtonSize),
            colors = companionTonalIconButtonColors(),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to home",
                modifier = Modifier.size(adaptive.helpIconSize),
            )
        }
        Spacer(modifier = Modifier.size(adaptive.helpIconButtonSize))
        Text(
            text = "Live Tracking",
            style =
                if (isCompactScreen) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.headlineSmall
                },
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(
            onClick = onOpenGuide,
            modifier = Modifier.size(adaptive.helpIconButtonSize),
            colors = companionTonalIconButtonColors(),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = "Live Tracking Guide",
                modifier = Modifier.size(adaptive.helpIconSize),
            )
        }
    }

    ScrollableScreenContent(
        scrollState = scrollState,
        contentSpacing = contentSpacing,
    ) {
        TrackingPanel(title = "Planned route") {
            OutlinedButton(
                onClick = onPickGpx,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Route,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Select GPX")
            }
            Text(
                text = "Selected GPX",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedGpxName.ifBlank { "No GPX selected" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (hasSelectedGpx) {
                    IconButton(
                        onClick = onClearGpx,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear selected GPX",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (sessionState.isTracking) {
                Text(
                    text = "Choose another GPX anytime, then tap Send update to update the planned route.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        TrackingPanel(title = "Comments to send in notification email") {
            OutlinedTextField(
                value = comments,
                onValueChange = onCommentsChange,
                label = { Text("Comments") },
                placeholder = { Text("Estimated time of arrival") },
                minLines = if (isCompactLayout) 2 else 4,
                modifier = Modifier.fillMaxWidth(),
            )
            if (sessionState.isTracking) {
                Text(
                    text = "Tracking is active. You can change the GPX or comment, then tap Send update.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showSendPlan) {
                Button(
                    onClick = onSendPlan,
                    enabled = canSendPlan && !isSendingPlan,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isSendingPlan) "Sending" else "Send update")
                }
            }
            sendStatusMessage?.let { message ->
                val isSendError = message.startsWith("Send failed", ignoreCase = true)
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (isSendError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                if (isSendError) {
                    OutlinedButton(
                        onClick = { emailArkluzSupport(context, message) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Email Arkluz")
                    }
                }
            }
        }

        TrackingPanel(title = "Session") {
            Text(
                text = "GPS update frequency: every ${formatUpdateInterval(updateIntervalSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onStart,
                    enabled = !sessionState.isTracking && !isStartingSession,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(if (isStartingSession) "Starting" else "Start")
                }
                OutlinedButton(
                    onClick = onStop,
                    enabled = sessionState.isTracking,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Stop")
                }
            }
            Text(
                text = sessionStatusText(sessionState),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            validationMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            sessionState.lastError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(
                    onClick = { emailArkluzSupport(context, error) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Email Arkluz")
                }
            }
        }

        TrackingPanel(title = "View & share tracks") {
            TrackLinkRow(
                label = userName.trim().ifBlank { "Participant" },
                url = userTrackUrl,
                onView = { openUrl(context, userTrackUrl) },
                onShare = { shareUrl(context, userTrackUrl) },
            )
            TrackLinkRow(
                label = "Group",
                url = groupTrackUrl,
                onView = { openUrl(context, groupTrackUrl) },
                onShare = { shareUrl(context, groupTrackUrl) },
            )
            OutlinedButton(
                onClick = onDownloadUserTrack,
                enabled = !isDownloadingRecordedTrack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (isDownloadingRecordedTrack) "Downloading" else "Download my GPX",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            recordedTrackDownloadStatusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (message.startsWith("Download failed", ignoreCase = true)) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            OutlinedButton(
                onClick = onDeleteRecordedTracks,
                enabled = !isDeletingTracks,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(if (isDeletingTracks) "Deleting" else "Delete recorded tracks")
            }
            Text(
                text = "Tracks are automatically deleted from the server every 7 days.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            deleteTracksStatusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (message.startsWith("Delete failed", ignoreCase = true)) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onOpenLogin, modifier = Modifier.weight(1f)) {
            Text(
                text = if (isConnected) group.ifBlank { "Connected" } else "Login / Join",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint =
                    if (isConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = "Settings",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color =
                    if (isConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
    headerMessage?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TrackLinkRow(
    label: String,
    url: String,
    onView: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilledTonalIconButton(
            onClick = onView,
            enabled = url.isNotBlank(),
            modifier = Modifier.size(36.dp),
            colors = companionTonalIconButtonColors(),
        ) {
            Icon(
                imageVector = Icons.Filled.TravelExplore,
                contentDescription = "View $label track",
                modifier = Modifier.size(18.dp),
            )
        }
        FilledTonalIconButton(
            onClick = onShare,
            enabled = url.isNotBlank(),
            modifier = Modifier.size(36.dp),
            colors = companionTonalIconButtonColors(),
        ) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = "Share $label track",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun companionTonalIconButtonColors() =
    IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
