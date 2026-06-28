package com.glancemap.glancemapwearos.presentation.features.recording.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.features.navigate.formatNavigateClockTime
import kotlinx.coroutines.delay

@Composable
internal fun RecordingFullscreenPageShell(
    pageIndex: Int,
    pageCount: Int,
    dragKey: Any?,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onShowActions: () -> Unit,
    telemetryTag: String = "TraceRecording",
    content: @Composable BoxScope.() -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var rotaryAccumulator by remember(pageCount) { mutableFloatStateOf(0f) }

    LaunchedEffect(pageCount, pageIndex) {
        var focusAcquired = false
        var attempt = 0
        while (!focusAcquired && attempt < 3) {
            withFrameNanos { }
            focusAcquired = focusRequester.requestFocus()
            attempt += 1
        }
        DebugTelemetry.log(
            telemetryTag,
            "event=dashboard_rotary_focus_requested acquired=$focusAcquired " +
                "page=${pageIndex + 1} pageCount=$pageCount",
        )
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onShowActions,
                ).pointerInput(dragKey, pageIndex, pageCount) {
                    var totalDragY = 0f
                    detectVerticalDragGestures(
                        onDragEnd = {
                            when {
                                totalDragY < -POPUP_PAGE_DRAG_THRESHOLD_PX -> onNextPage()
                                totalDragY > POPUP_PAGE_DRAG_THRESHOLD_PX -> onPreviousPage()
                            }
                            totalDragY = 0f
                        },
                        onDragCancel = {
                            totalDragY = 0f
                        },
                    ) { _, dragAmount ->
                        totalDragY += dragAmount
                    }
                }.onPreRotaryScrollEvent { event ->
                    handleRecordingRotaryPageEvent(
                        delta = event.verticalScrollPixels,
                        pageCount = pageCount,
                        accumulator = rotaryAccumulator,
                        onAccumulatorChange = { rotaryAccumulator = it },
                        onPreviousPage = onPreviousPage,
                        onNextPage = onNextPage,
                    )
                }.focusRequester(focusRequester)
                .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        content()
        FullscreenPopupTimeChip(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp),
        )
        RecordingDashboardPageIndicator(
            pageIndex = pageIndex,
            pageCount = pageCount,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp),
        )
    }
}

internal val LocalFullscreenPopupTimeFormat =
    staticCompositionLocalOf { SettingsRepository.TIME_FORMAT_24_HOUR }

@Composable
private fun FullscreenPopupTimeChip(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    Box(
        modifier =
            modifier
                .height(20.dp)
                .widthIn(min = 62.dp)
                .background(Color.Black.copy(alpha = 0.74f), RoundedCornerShape(percent = 50))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.96f), RoundedCornerShape(percent = 50))
                .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatNavigateClockTime(context, nowMillis, LocalFullscreenPopupTimeFormat.current),
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                ),
            color = Color.White,
            maxLines = 1,
        )
    }
}

internal fun logRecordingDashboardPageChange(
    pageIndex: Int,
    pageCount: Int,
    source: String,
) {
    DebugTelemetry.log(
        "TraceRecording",
        "event=dashboard_page_change page=${pageIndex + 1} pageCount=$pageCount source=$source",
    )
}

@Composable
private fun RecordingDashboardPageIndicator(
    pageIndex: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 1) return
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier =
                    Modifier
                        .width(4.dp)
                        .height(if (index == pageIndex) 18.dp else 6.dp)
                        .background(
                            color = Color.White.copy(alpha = if (index == pageIndex) 0.72f else 0.28f),
                            shape = RoundedCornerShape(percent = 50),
                        ),
            )
        }
    }
}

private fun handleRecordingRotaryPageEvent(
    delta: Float,
    pageCount: Int,
    accumulator: Float,
    onAccumulatorChange: (Float) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
): Boolean {
    if (!delta.isFinite() || delta == 0f) return false
    if (pageCount <= 1) return true
    var nextAccumulator =
        if (accumulator != 0f && (accumulator > 0f) != (delta > 0f)) {
            0f
        } else {
            accumulator
        }
    nextAccumulator += delta
    var consumed = false
    if (nextAccumulator >= POPUP_ROTARY_PAGE_THRESHOLD_PX) {
        onNextPage()
        nextAccumulator = 0f
        consumed = true
    }
    if (nextAccumulator <= -POPUP_ROTARY_PAGE_THRESHOLD_PX) {
        onPreviousPage()
        nextAccumulator = 0f
        consumed = true
    }
    onAccumulatorChange(nextAccumulator)
    return consumed || nextAccumulator != 0f
}

private const val POPUP_PAGE_DRAG_THRESHOLD_PX = 24f
private const val POPUP_ROTARY_PAGE_THRESHOLD_PX = 56f

internal fun Int.floorMod(modulus: Int): Int =
    if (modulus <= 0) {
        0
    } else {
        ((this % modulus) + modulus) % modulus
    }

internal const val POPUP_MINIMIZE_DRAG_THRESHOLD_PX = 24f
internal const val POPUP_EXPAND_DRAG_THRESHOLD_PX = 24f
