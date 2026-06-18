package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.presentation.ui.cappedFontScale

@Composable
internal fun RecordingDashboardPageCard(
    pageIndex: Int,
    pageCount: Int,
    metricPreview: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    applyTopPadding: Boolean = false,
) {
    val topPadding = if (applyTopPadding) rememberSettingsFirstItemTopPadding() else 0.dp
    val cardShape = RoundedCornerShape(20.dp)

    Column(
        modifier =
            modifier
                .padding(top = topPadding)
                .fillMaxWidth()
                .background(Color(0xFF213D63), cardShape)
                .border(
                    width = 1.5.dp,
                    color = Color(0xFFF6C453).copy(alpha = 0.9f),
                    shape = cardShape,
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "DASHBOARD PAGE",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFF6C453),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Page ${pageIndex + 1}",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        RecordingDashboardPageDots(
            pageIndex = pageIndex,
            pageCount = pageCount,
        )
        Text(
            text = "$metricPreview  •  Tap to select",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.82f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun RecordingDashboardPageManagerDialog(
    visible: Boolean,
    pageIndex: Int,
    pageCount: Int,
    metricPreview: String,
    canAddPage: Boolean,
    canDeletePage: Boolean,
    onSelectPage: (Int) -> Unit,
    onAddPage: () -> Unit,
    onDeletePage: () -> Unit,
    onEditPage: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val focusRequester = remember { FocusRequester() }
    var rotaryAccumulator by remember(pageCount) { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
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
                    .background(Color.Black.copy(alpha = 0.94f))
                    .onPreRotaryScrollEvent { event ->
                        val delta = event.verticalScrollPixels
                        if (!delta.isFinite() || delta == 0f) return@onPreRotaryScrollEvent false
                        rotaryAccumulator += delta
                        when {
                            rotaryAccumulator >= PAGE_ROTARY_THRESHOLD_PX -> {
                                onSelectPage((pageIndex + 1).floorMod(pageCount))
                                rotaryAccumulator = 0f
                            }
                            rotaryAccumulator <= -PAGE_ROTARY_THRESHOLD_PX -> {
                                onSelectPage((pageIndex - 1).floorMod(pageCount))
                                rotaryAccumulator = 0f
                            }
                        }
                        true
                    }.focusRequester(focusRequester)
                    .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            cappedFontScale(maxFontScale = 1.15f) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.78f)
                            .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        text = "Page ${pageIndex + 1}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    Text(
                        text = "${pageIndex + 1} / $pageCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.68f),
                    )
                    RecordingDashboardPageDots(
                        pageIndex = pageIndex,
                        pageCount = pageCount,
                    )
                    Text(
                        text = metricPreview,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.78f),
                        textAlign = TextAlign.Center,
                    )
                    DashboardManagerAction(
                        label = "Edit this page",
                        onClick = onEditPage,
                    )
                    if (canAddPage) {
                        DashboardManagerAction(
                            label = "Add page",
                            onClick = onAddPage,
                        )
                    }
                    if (canDeletePage) {
                        DashboardManagerAction(
                            label = "Delete current page",
                            onClick = onDeletePage,
                            destructive = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingDashboardPageDots(
    pageIndex: Int,
    pageCount: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier =
                    Modifier
                        .width(if (index == pageIndex) 14.dp else 5.dp)
                        .height(5.dp)
                        .background(
                            color = Color.White.copy(alpha = if (index == pageIndex) 0.9f else 0.32f),
                            shape = RoundedCornerShape(percent = 50),
                        ),
            )
        }
    }
}

@Composable
private fun DashboardManagerAction(
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = if (destructive) Color(0xFF4A2328) else Color(0xFF263C5A),
                    shape = RoundedCornerShape(18.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

private fun Int.floorMod(modulus: Int): Int =
    if (modulus <= 0) 0 else ((this % modulus) + modulus) % modulus

private const val PAGE_ROTARY_THRESHOLD_PX = 28f
