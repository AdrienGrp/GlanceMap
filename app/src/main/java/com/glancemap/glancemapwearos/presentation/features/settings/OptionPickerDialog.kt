package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.material.ToggleChip
import com.google.android.horologist.compose.material.ToggleChipToggleControl
import kotlin.math.abs

private const val PICKER_DRAG_DISMISS_PX = 55f

@OptIn(ExperimentalHorologistApi::class)
@Composable
internal fun <T> OptionPickerDialog(
    visible: Boolean,
    title: String,
    selectedValue: T,
    options: List<Pair<T, String>>,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    if (!visible) return

    val adaptive = rememberWearAdaptiveSpec()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Color.Black.copy(alpha = 0.82f),
                        RoundedCornerShape(adaptive.dialogCornerRadius),
                    ).padding(
                        horizontal = adaptive.dialogHorizontalPadding,
                        vertical = adaptive.dialogVerticalPadding,
                    ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
                                if (totalDrag > PICKER_DRAG_DISMISS_PX) {
                                    onDismiss()
                                    totalDrag = 0f
                                }
                            }
                        },
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(26.dp)
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.42f), RoundedCornerShape(50))
                            .align(androidx.compose.ui.Alignment.Center),
                )
            }
            Text(
                text = title,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp, max = adaptive.helpDialogMaxHeight)
                        .onPreRotaryScrollEvent { event ->
                            val consumed = listState.dispatchRawDelta(event.verticalScrollPixels)
                            abs(consumed) > 0.5f
                        }.focusRequester(focusRequester)
                        .focusable(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                userScrollEnabled = true,
            ) {
                items(options) { (value, label) ->
                    ToggleChip(
                        modifier = Modifier.fillMaxWidth(),
                        checked = value == selectedValue,
                        onCheckedChanged = { checked ->
                            if (value == selectedValue) {
                                onDismiss()
                                return@ToggleChip
                            }
                            if (!checked) return@ToggleChip
                            onSelect(value)
                            onDismiss()
                        },
                        label = label,
                        toggleControl = ToggleChipToggleControl.Radio,
                    )
                }
            }
        }
    }
}
