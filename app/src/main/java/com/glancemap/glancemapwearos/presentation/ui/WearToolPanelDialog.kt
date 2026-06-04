@file:Suppress("FunctionName", "MatchingDeclarationName")

package com.glancemap.glancemapwearos.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.Text

private const val TOOL_PANEL_DRAG_DISMISS_PX = 55f

@Composable
fun WearToolPanelDialog(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    backgroundColor: Color = Color.Black.copy(alpha = 0.90f),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        WearToolPanelSurface(
            title = title,
            onDismiss = onDismiss,
            backgroundColor = backgroundColor,
            content = content,
        )
    }
}

@Composable
private fun WearToolPanelSurface(
    title: String,
    onDismiss: () -> Unit,
    backgroundColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    val adaptive = rememberWearAdaptiveSpec()
    val scrollState = rememberScrollState()
    val highFontRound = adaptive.isRound && adaptive.fontScale >= 1.25f
    val highFontTopInset =
        if (highFontRound) {
            8.dp
        } else {
            0.dp
        }
    val horizontalPadding =
        adaptive.dialogHorizontalPadding +
            when {
                highFontRound -> 18.dp
                adaptive.isRound -> 8.dp
                else -> 4.dp
            }
    val topPadding =
        adaptive.dialogVerticalPadding +
            adaptive.headerTopSafeInset +
            (if (adaptive.isRound) 10.dp else 0.dp) +
            highFontTopInset
    val bottomPadding =
            adaptive.dialogVerticalPadding +
            when {
                highFontRound -> 120.dp
                adaptive.isRound -> 42.dp
                else -> 12.dp
            }

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
                    .padding(horizontal = horizontalPadding)
                    .padding(top = topPadding, bottom = bottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
        ) {
            ToolPanelDragHandle(onDismiss = onDismiss)

            cappedFontScale(maxFontScale = 1.12f) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                )
                content()
            }
        }
        ScrollIndicator(
            state = scrollState,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun ToolPanelDragHandle(onDismiss: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    detectVerticalDragGestures(
                        onDragEnd = { totalDrag = 0f },
                        onDragCancel = { totalDrag = 0f },
                    ) { _, dragAmount ->
                        totalDrag += dragAmount
                        if (totalDrag > TOOL_PANEL_DRAG_DISMISS_PX) {
                            onDismiss()
                            totalDrag = 0f
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .width(26.dp)
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.42f), RoundedCornerShape(50)),
        )
    }
}
