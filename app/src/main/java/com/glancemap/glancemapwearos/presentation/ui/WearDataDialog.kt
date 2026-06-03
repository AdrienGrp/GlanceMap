@file:Suppress("FunctionName")

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
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f)),
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onPreRotaryScrollEvent { event ->
                            coroutineScope.launch {
                                listState.scrollBy(event.verticalScrollPixels)
                            }
                            abs(event.verticalScrollPixels) > 0.5f
                        }.focusRequester(focusRequester)
                        .focusable(),
                state = listState,
                contentPadding =
                    PaddingValues(
                        start = adaptive.dialogHorizontalPadding,
                        top = adaptive.dialogVerticalPadding + adaptive.headerTopSafeInset + 18.dp,
                        end = adaptive.dialogHorizontalPadding + 10.dp,
                        bottom =
                            adaptive.dialogVerticalPadding +
                                if (bottomAction != null) {
                                    76.dp
                                } else if (adaptive.isRound) {
                                    42.dp
                                } else {
                                    18.dp
                                },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    DataDialogDragHandle(onDismiss = onDismiss)
                }
                item {
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
                content()
            }

            WearLazyListScreenEdgeScrollIndicator(listState = listState)

            if (bottomAction != null) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = adaptive.dialogVerticalPadding + 14.dp),
                    contentAlignment = Alignment.Center,
                    content = bottomAction,
                )
            }
        }
    }
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
