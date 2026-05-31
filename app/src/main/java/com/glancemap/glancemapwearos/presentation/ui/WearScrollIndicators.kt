package com.glancemap.glancemapwearos.presentation.ui

import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun WearDialogScrollableColumn(
    maxHeight: Dp,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    contentEndPadding: Dp = 10.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight),
    ) {
        Column(
            modifier =
                contentModifier
                    .fillMaxWidth()
                    .padding(end = contentEndPadding)
                    .verticalScroll(scrollState),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

@Composable
fun WearVerticalScrollIndicator(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    if (scrollState.maxValue <= 0) return

    WearScrollIndicatorTrack(
        modifier = modifier,
        progress = scrollState.value.toFloat() / scrollState.maxValue.toFloat(),
        thumbFraction = 0.22f,
    )
}

@Composable
fun BoxScope.WearLazyListScrollIndicator(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    if (totalItems <= 0 || visibleItems.isEmpty() || visibleItems.size >= totalItems) return

    val firstVisible = visibleItems.first()
    val itemSpan = (firstVisible.size + layoutInfo.mainAxisItemSpacing).coerceAtLeast(1)
    val offsetProgress = (-firstVisible.offset).toFloat() / itemSpan.toFloat()
    val maxFirstIndex = (totalItems - visibleItems.size).coerceAtLeast(1)
    val progress = ((listState.firstVisibleItemIndex + offsetProgress) / maxFirstIndex).coerceIn(0f, 1f)
    val thumbFraction = (visibleItems.size.toFloat() / totalItems.toFloat()).coerceIn(0.22f, 0.85f)

    WearScrollIndicatorTrack(
        modifier = modifier.align(Alignment.CenterEnd),
        progress = progress,
        thumbFraction = thumbFraction,
    )
}

@Composable
private fun WearScrollIndicatorTrack(
    progress: Float,
    thumbFraction: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxHeight()
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
