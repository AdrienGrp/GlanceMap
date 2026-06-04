@file:Suppress("FunctionName", "LongMethod", "MatchingDeclarationName")

package com.glancemap.glancemapwearos.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.Text

data class WearFormDialogTokens(
    val controlModifier: Modifier,
    val buttonMinHeight: Dp,
    val textFieldVerticalPadding: Dp,
)

@Composable
fun WearFormDialog(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    backgroundColor: Color = Color.Black,
    content: @Composable ColumnScope.(WearFormDialogTokens) -> Unit,
) {
    if (!visible) return

    val adaptive = rememberWearAdaptiveSpec()
    val scrollState = rememberScrollState()
    val highFontTopInset =
        if (adaptive.isRound && adaptive.fontScale >= 1.25f) {
            8.dp
        } else {
            0.dp
        }
    val topPadding =
        adaptive.dialogVerticalPadding +
            adaptive.headerTopSafeInset +
            (if (adaptive.isRound) 18.dp else 0.dp) +
            highFontTopInset
    val bottomPadding =
        adaptive.dialogVerticalPadding +
            if (adaptive.isRound) 42.dp else 12.dp
    val controlWidthFraction = if (adaptive.isRound) 0.86f else 1f
    val tokens =
        WearFormDialogTokens(
            controlModifier = Modifier.fillMaxWidth(controlWidthFraction),
            buttonMinHeight = 44.dp,
            textFieldVerticalPadding = 10.dp,
        )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(backgroundColor),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = adaptive.dialogHorizontalPadding)
                        .padding(top = topPadding, bottom = bottomPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
            ) {
                cappedFontScale(maxFontScale = 1.12f) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                    content(tokens)
                }
            }
            ScrollIndicator(
                state = scrollState,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}
