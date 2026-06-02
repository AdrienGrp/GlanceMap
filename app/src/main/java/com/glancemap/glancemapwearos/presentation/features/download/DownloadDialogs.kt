@file:Suppress(
    "FunctionName",
    "FunctionNaming",
    "LongMethod",
)

package com.glancemap.glancemapwearos.presentation.features.download

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.presentation.ui.WearDialogScrollBottomSpacer
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec

@Composable
internal fun AreaSearchDialog(
    visible: Boolean,
    initialQuery: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    if (!visible) return

    val adaptive = rememberWearAdaptiveSpec()
    var draftQuery by remember(visible, initialQuery) { mutableStateOf(initialQuery) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val topPadding =
        adaptive.dialogVerticalPadding +
            adaptive.headerTopSafeInset +
            if (adaptive.isRound) 18.dp else 0.dp
    val bottomPadding =
        adaptive.dialogVerticalPadding +
            if (adaptive.isRound) 42.dp else 12.dp
    val controlWidthFraction = if (adaptive.isRound) 0.86f else 1f
    val buttonMinHeight = 44.dp

    LaunchedEffect(visible) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .verticalScroll(scrollState)
                    .padding(
                        horizontal = adaptive.dialogHorizontalPadding,
                    ).padding(
                        top = topPadding,
                        bottom = bottomPadding,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
        ) {
            Text(
                text = "Search area",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )

            BasicTextField(
                value = draftQuery,
                onValueChange = { draftQuery = it.take(32) },
                singleLine = true,
                textStyle =
                    TextStyle(
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier =
                    Modifier
                        .fillMaxWidth(controlWidthFraction)
                        .focusRequester(focusRequester)
                        .background(
                            Color(0xFF1F1F1F),
                            RoundedCornerShape(12.dp),
                        ).padding(horizontal = 12.dp, vertical = 10.dp),
                decorationBox = { innerTextField ->
                    if (draftQuery.isBlank()) {
                        Text(
                            text = "France, Alps...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.45f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    innerTextField()
                },
            )

            Button(
                onClick = { onApply(draftQuery) },
                modifier =
                    Modifier
                        .fillMaxWidth(controlWidthFraction)
                        .heightIn(min = buttonMinHeight),
            ) {
                Text("Apply")
            }

            Button(
                onClick = onDismiss,
                modifier =
                    Modifier
                        .fillMaxWidth(controlWidthFraction)
                        .heightIn(min = buttonMinHeight),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White,
                    ),
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
internal fun OamAttributionDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    AlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = { Text("Download") },
        content = {
            item {
                Text(
                    text = "Connect the watch to Wi-Fi and keep it on the charger.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = "Thank you to OpenAndroMaps for providing the offline maps and POIs.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = "Routing enables offline route calculation.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = "Elevation adds altitude, slope, and terrain shading.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Update,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text("Use update button to refresh bundles.")
                }
            }
            item {
                WearDialogScrollBottomSpacer()
            }
        },
    )
}

@Composable
internal fun DownloadNetworkWarningDialog(
    message: String?,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (message == null) return

    AlertDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = { Text("Wi-Fi recommended") },
        text = {
            Text(
                text = message,
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            Button(onClick = onContinue) {
                Text("Continue")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White,
                    ),
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
internal fun RefreshBundleDialog(
    check: OamBundleUpdateCheck?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (check == null) return

    val updateAvailable = check.status == OamBundleUpdateStatus.UPDATE_AVAILABLE
    val upToDate = check.status == OamBundleUpdateStatus.UP_TO_DATE
    AlertDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text =
                    when {
                        updateAvailable -> "Update available"
                        upToDate -> "Already up to date"
                        check.checkedFileCount == 0 -> "Update info missing"
                        else -> "Check incomplete"
                    },
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = refreshBundleDialogText(check),
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            Button(onClick = if (upToDate) onDismiss else onConfirm) {
                Text(if (upToDate) "OK" else "Refresh")
            }
        },
        dismissButton = {
            if (!upToDate) {
                Button(
                    onClick = onDismiss,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White,
                        ),
                ) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
internal fun RefreshBundleSummaryDialog(
    summary: OamBundleRefreshSummary?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (summary == null) return

    val refreshCount = summary.bundlesToRefresh.size
    val hasUpdates = refreshCount > 0
    AlertDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text =
                    when {
                        hasUpdates -> "Refresh bundles?"
                        summary.unknownCount > 0 -> "Check incomplete"
                        else -> "All up to date"
                    },
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = refreshSummaryDialogText(summary),
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            Button(onClick = if (hasUpdates) onConfirm else onDismiss) {
                Text(if (hasUpdates) "Refresh $refreshCount" else "OK")
            }
        },
        dismissButton = {
            if (hasUpdates) {
                Button(
                    onClick = onDismiss,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White,
                        ),
                ) {
                    Text("Cancel")
                }
            }
        },
    )
}

private fun refreshBundleDialogText(check: OamBundleUpdateCheck): String =
    when (check.status) {
        OamBundleUpdateStatus.UPDATE_AVAILABLE ->
            buildString {
                append(check.bundle.areaLabel)
                append(" has newer files available.")
                if (check.changedFileNames.isNotEmpty()) {
                    append("\n\nChanged: ")
                    append(check.changedFileNames.take(MAX_DIALOG_FILE_NAMES).joinToString(", "))
                    if (check.changedFileNames.size > MAX_DIALOG_FILE_NAMES) {
                        append(" +")
                        append(check.changedFileNames.size - MAX_DIALOG_FILE_NAMES)
                    }
                }
                append("\n\nExisting files will be replaced after the download completes.")
            }
        OamBundleUpdateStatus.UNKNOWN ->
            if (check.checkedFileCount == 0) {
                "${check.bundle.areaLabel} has no saved update info yet.\n\n" +
                    "Refresh once to enable future checks."
            } else {
                "Some files for ${check.bundle.areaLabel} could not be checked.\n\n" +
                    "Refresh anyway?"
            }
        OamBundleUpdateStatus.UP_TO_DATE -> "${check.bundle.areaLabel} is already up to date."
    }

private fun refreshSummaryDialogText(summary: OamBundleRefreshSummary): String =
    when {
        summary.bundlesToRefresh.isNotEmpty() ->
            buildString {
                append("${summary.totalCount} checked")
                if (summary.updateAvailableCount > 0) {
                    append("\n${summary.updateAvailableCount} update available")
                }
                if (summary.unknownCount > 0) {
                    append("\n${summary.unknownCount} check incomplete")
                }
                if (summary.upToDateCount > 0) {
                    append("\n${summary.upToDateCount} up to date")
                }
                append("\n\nRefresh ${summary.bundlesToRefresh.size} bundle(s)?")
            }
        summary.unknownCount > 0 ->
            buildString {
                append("${summary.totalCount} checked")
                append("\n${summary.unknownCount} check incomplete")
                if (summary.upToDateCount > 0) {
                    append("\n${summary.upToDateCount} up to date")
                }
                append("\n\nNo confirmed updates were found.")
            }
        else -> "${summary.totalCount} selected bundle(s) are up to date."
    }

private const val MAX_DIALOG_FILE_NAMES = 3
