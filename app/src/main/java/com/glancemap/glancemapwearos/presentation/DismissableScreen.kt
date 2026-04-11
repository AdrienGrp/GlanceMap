package com.glancemap.glancemapwearos.presentation

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.wear.compose.material3.SwipeToDismissBox
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec

@Composable
fun DismissableScreen(
    onDismiss: () -> Unit,
    onSwipeLeftNavigate: () -> Unit,
    rightEdgeGestureWidthOverride: Dp? = null,
    edgeGestureTestTag: String? = null,
    content: @Composable () -> Unit
) {
    val adaptive = rememberWearAdaptiveSpec()
    val leftSwipeThresholdPx = with(LocalDensity.current) { adaptive.edgeSwipeThreshold.toPx() }
    val rightEdgeGestureWidth = rightEdgeGestureWidthOverride ?: adaptive.edgeSwipeWidth
    val gestureHeightFraction = adaptive.edgeSwipeHeightFraction

    SwipeToDismissBox(onDismissed = onDismiss) { isBackground ->
        if (!isBackground) {
            Box(modifier = Modifier.fillMaxSize()) {
                content()

                val edgeGestureModifier = if (edgeGestureTestTag != null) {
                    Modifier.testTag(edgeGestureTestTag)
                } else {
                    Modifier
                }

                Box(
                    modifier = edgeGestureModifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(gestureHeightFraction)
                        .width(rightEdgeGestureWidth)
                        .pointerInput(onSwipeLeftNavigate, leftSwipeThresholdPx) {
                            var totalDx = 0f
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    totalDx = 0f
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    totalDx += dragAmount
                                },
                                onDragCancel = {
                                    totalDx = 0f
                                },
                                onDragEnd = {
                                    if (totalDx <= -leftSwipeThresholdPx) {
                                        onSwipeLeftNavigate()
                                    }
                                    totalDx = 0f
                                }
                            )
                        }
                )
            }
        }
    }
}
