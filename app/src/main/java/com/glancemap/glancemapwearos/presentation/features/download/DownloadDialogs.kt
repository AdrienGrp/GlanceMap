@file:Suppress(
    "FunctionName",
    "FunctionNaming",
    "LongMethod",
)

package com.glancemap.glancemapwearos.presentation.features.download

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
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
                    .fillMaxWidth(0.86f)
                    .background(
                        Color.Black,
                        RoundedCornerShape(adaptive.dialogCornerRadius),
                    ).padding(
                        horizontal = adaptive.dialogHorizontalPadding,
                        vertical = adaptive.dialogVerticalPadding,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                        .fillMaxWidth()
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
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apply")
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
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
        title = { Text("OpenAndroMaps") },
        text = {
            Text(
                text =
                    "Thanks to OpenAndroMaps for providing free offline maps and POIs.\n\n" +
                        "Large map files can take a long time to download. Keep the watch on its charger.\n\n" +
                        "https://www.openandromaps.org",
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
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
