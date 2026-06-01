package com.glancemap.glancemapwearos.presentation.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ScrollIndicator
import kotlin.math.min

private const val MIN_VISIBLE_SCROLL_RANGE_PX = 18

@Composable
fun WearDialogScrollableColumn(
    maxHeight: Dp,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    contentEndPadding: Dp = 10.dp,
    showScrollIndicator: Boolean = false,
    scrollable: Boolean = true,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .let { base ->
                    if (scrollable) {
                        base.heightIn(max = maxHeight)
                    } else {
                        base
                    }
                },
    ) {
        Column(
            modifier =
                contentModifier
                    .fillMaxWidth()
                    .let { base ->
                        if (scrollable) {
                            base
                                .padding(end = contentEndPadding)
                                .verticalScroll(scrollState)
                        } else {
                            base
                        }
                    },
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement,
            content = content,
        )
        if (showScrollIndicator && scrollable) {
            WearVerticalScrollIndicator(
                scrollState = scrollState,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
fun WearCustomDialogScrollableColumn(
    maxHeight: Dp,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    contentEndPadding: Dp = 10.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    WearDialogScrollableColumn(
        maxHeight = maxHeight,
        modifier = modifier,
        contentModifier = contentModifier,
        scrollState = scrollState,
        contentEndPadding = contentEndPadding,
        showScrollIndicator = true,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

@Composable
fun WearVerticalScrollIndicator(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    if (scrollState.maxValue <= MIN_VISIBLE_SCROLL_RANGE_PX) return

    WearScrollIndicatorTrack(
        modifier = modifier,
        progress = scrollState.value.toFloat() / scrollState.maxValue.toFloat(),
        thumbFraction = 0.22f,
    )
}

@Composable
fun BoxScope.WearScreenEdgeScrollIndicator(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    if (scrollState.maxValue <= MIN_VISIBLE_SCROLL_RANGE_PX) return

    val adaptive = rememberWearAdaptiveSpec()
    if (!adaptive.isRound) {
        WearVerticalScrollIndicator(
            scrollState = scrollState,
            modifier = modifier.align(Alignment.CenterEnd),
        )
        return
    }

    val progress = scrollState.value.toFloat() / scrollState.maxValue.toFloat()
    val trackSweep = 70f
    val trackStartAngle = -35f
    val thumbFraction = 0.32f

    Canvas(
        modifier =
            modifier
                .matchParentSize()
                .align(Alignment.CenterEnd),
    ) {
        val strokeWidth = 5.dp.toPx()
        val edgeInset = 7.dp.toPx()
        val diameter = min(size.width, size.height) - (edgeInset * 2f) - strokeWidth
        if (diameter <= 0f) return@Canvas

        val topLeft =
            Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f,
            )
        val arcSize = Size(diameter, diameter)
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        val thumbSweep = trackSweep * thumbFraction
        val thumbStartAngle =
            trackStartAngle +
                (trackSweep - thumbSweep) * progress.coerceIn(0f, 1f)

        drawArc(
            color = Color.White.copy(alpha = 0.24f),
            startAngle = trackStartAngle,
            sweepAngle = trackSweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
        drawArc(
            color = Color.White.copy(alpha = 0.82f),
            startAngle = thumbStartAngle,
            sweepAngle = thumbSweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
    }
}

@Composable
fun BoxScope.WearLazyListScrollIndicator(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    showWhenNotScrollable: Boolean = false,
) {
    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    if (totalItems <= 0 || visibleItems.isEmpty()) return

    val firstVisible = visibleItems.first()
    val lastVisible = visibleItems.last()
    val viewportSpan = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1)
    val contentBefore =
        firstVisible.index > 0 ||
            firstVisible.offset < layoutInfo.viewportStartOffset
    val contentAfter =
        lastVisible.index < totalItems - 1 ||
            lastVisible.offset + lastVisible.size > layoutInfo.viewportEndOffset
    if (!contentBefore && !contentAfter) {
        if (!showWhenNotScrollable || totalItems <= 1) return
        WearScrollIndicatorTrack(
            modifier = modifier.align(Alignment.CenterEnd),
            progress = 0f,
            thumbFraction = 0.85f,
        )
        return
    }

    val visibleContentExtent = (lastVisible.offset + lastVisible.size - firstVisible.offset).coerceAtLeast(1)
    val averageItemSpan =
        ((visibleContentExtent + layoutInfo.mainAxisItemSpacing * (visibleItems.size - 1)) / visibleItems.size)
            .coerceAtLeast(1)
    val estimatedContentExtent = (averageItemSpan * totalItems).coerceAtLeast(viewportSpan + 1)
    val scrolledBefore =
        (
            firstVisible.index * averageItemSpan +
                layoutInfo.viewportStartOffset -
                firstVisible.offset
        ).coerceAtLeast(0)
    val maxScroll = (estimatedContentExtent - viewportSpan).coerceAtLeast(1)
    val progress = (scrolledBefore.toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f)
    val thumbFraction = (viewportSpan.toFloat() / estimatedContentExtent.toFloat()).coerceIn(0.22f, 0.85f)

    WearScrollIndicatorTrack(
        modifier = modifier.align(Alignment.CenterEnd),
        progress = progress,
        thumbFraction = thumbFraction,
    )
}

@Composable
fun BoxScope.WearLazyListScreenEdgeScrollIndicator(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    showWhenNotScrollable: Boolean = false,
) {
    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    if (totalItems <= 0 || visibleItems.isEmpty()) return

    val firstVisible = visibleItems.first()
    val lastVisible = visibleItems.last()
    val contentBefore =
        firstVisible.index > 0 ||
            firstVisible.offset < layoutInfo.viewportStartOffset
    val contentAfter =
        lastVisible.index < totalItems - 1 ||
            lastVisible.offset + lastVisible.size > layoutInfo.viewportEndOffset
    if (!contentBefore && !contentAfter && !showWhenNotScrollable) return

    ScrollIndicator(
        state = listState,
        modifier =
            modifier
                .matchParentSize()
                .align(Alignment.CenterEnd),
    )
}

@Composable
private fun WearScrollIndicatorTrack(
    progress: Float,
    thumbFraction: Float,
    modifier: Modifier = Modifier,
) {
    val adaptive = rememberWearAdaptiveSpec()
    val topSafeInset =
        if (adaptive.isRound) {
            when (adaptive.screenSize) {
                WearScreenSize.LARGE -> 4.dp
                WearScreenSize.MEDIUM -> 5.dp
                WearScreenSize.SMALL -> 6.dp
            }
        } else {
            0.dp
        }
    val bottomSafeInset =
        if (adaptive.isRound) {
            when (adaptive.screenSize) {
                WearScreenSize.LARGE -> 10.dp
                WearScreenSize.MEDIUM -> 12.dp
                WearScreenSize.SMALL -> 14.dp
            }
        } else {
            0.dp
        }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxHeight()
                .padding(top = topSafeInset, bottom = bottomSafeInset)
                .width(6.dp),
    ) {
        val thumbHeight = (maxHeight * thumbFraction).coerceIn(24.dp, 46.dp)
        val travel = (maxHeight - thumbHeight).coerceAtLeast(0.dp)
        val thumbOffset = travel * progress.coerceIn(0f, 1f)

        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(50)),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = thumbOffset)
                    .width(6.dp)
                    .height(thumbHeight)
                    .background(Color.White.copy(alpha = 0.72f), RoundedCornerShape(50)),
        )
    }
}
