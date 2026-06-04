@file:Suppress("FunctionName", "LongParameterList")

package com.glancemap.glancemapwearos.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val DATA_DIALOG_DRAG_DISMISS_PX = 55f

@Composable
fun WearDataDialog(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    viewportPadding: PaddingValues = PaddingValues(0.dp),
    bottomAction: (@Composable BoxScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    if (!visible) return

    val adaptive = rememberWearAdaptiveSpec()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(visible, title) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        WearDataDialogSurface(
            title = title,
            onDismiss = onDismiss,
            dialogState =
                DataDialogRuntime(
                    listState = listState,
                    focusRequester = focusRequester,
                    viewportPadding = viewportPadding,
                    contentPadding = dataDialogPadding(adaptive, bottomAction != null),
                    bottomActionPadding = adaptive.dialogVerticalPadding + 14.dp,
                    onRotaryScroll = { delta ->
                        coroutineScope.launch { listState.scrollBy(delta) }
                    },
                ),
            bottomAction = bottomAction,
            content = content,
        )
    }
}

private data class DataDialogRuntime(
    val listState: LazyListState,
    val focusRequester: FocusRequester,
    val viewportPadding: PaddingValues,
    val contentPadding: PaddingValues,
    val bottomActionPadding: Dp,
    val onRotaryScroll: (Float) -> Unit,
)

@Composable
private fun WearDataDialogSurface(
    title: String,
    onDismiss: () -> Unit,
    dialogState: DataDialogRuntime,
    bottomAction: (@Composable BoxScope.() -> Unit)?,
    content: LazyListScope.() -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.90f)),
    ) {
        DataDialogList(
            title = title,
            onDismiss = onDismiss,
            dialogState = dialogState,
            content = content,
        )
        WearLazyListScreenEdgeScrollIndicator(listState = dialogState.listState)
        if (bottomAction != null) {
            DataDialogBottomAction(bottomPadding = dialogState.bottomActionPadding, content = bottomAction)
        }
    }
}

private fun dataDialogPadding(
    adaptive: WearAdaptiveSpec,
    hasBottomAction: Boolean,
): PaddingValues {
    val highFontRound = adaptive.isRound && adaptive.fontScale >= 1.25f
    val horizontalPadding =
        adaptive.dialogHorizontalPadding +
            when {
                highFontRound -> 18.dp
                adaptive.isRound -> 8.dp
                else -> 4.dp
            }

    return PaddingValues(
        start = horizontalPadding,
        top = adaptive.dialogVerticalPadding + adaptive.headerTopSafeInset + 18.dp,
        end = horizontalPadding + 10.dp,
        bottom =
            adaptive.dialogVerticalPadding +
                when {
                    hasBottomAction -> 76.dp
                    highFontRound -> 260.dp
                    adaptive.isRound -> 42.dp
                    else -> 18.dp
                },
    )
}

@Composable
private fun DataDialogList(
    title: String,
    onDismiss: () -> Unit,
    dialogState: DataDialogRuntime,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(dialogState.viewportPadding)
                .onPreRotaryScrollEvent { event ->
                    dialogState.onRotaryScroll(event.verticalScrollPixels)
                    abs(event.verticalScrollPixels) > 0.5f
                }.focusRequester(dialogState.focusRequester)
                .focusable(),
        state = dialogState.listState,
        contentPadding = dialogState.contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            DataDialogDragHandle(onDismiss = onDismiss)
        }
        item {
            DataDialogTitle(title = title)
        }
        content()
    }
}

@Composable
private fun DataDialogTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
    )
}

@Composable
private fun BoxScope.DataDialogBottomAction(
    bottomPadding: Dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun DataDialogDragHandle(onDismiss: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    detectVerticalDragGestures(
                        onDragEnd = { totalDrag = 0f },
                        onDragCancel = { totalDrag = 0f },
                    ) { _, dragAmount ->
                        totalDrag += dragAmount
                        if (totalDrag > DATA_DIALOG_DRAG_DISMISS_PX) {
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
