package com.glancemap.glancemapwearos.presentation.features.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec
import kotlin.math.abs

private const val DEM_SETUP_DRAG_DISMISS_PX = 55f

enum class DemSetupReason {
    GENERIC,
    HILL_SHADING,
    LIVE_ELEVATION,
    SLOPE_OVERLAY,
}

@Composable
fun DemSetupBottomSheet(
    visible: Boolean,
    reason: DemSetupReason = DemSetupReason.GENERIC,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val adaptive = rememberWearAdaptiveSpec()
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val title =
        when (reason) {
            DemSetupReason.GENERIC -> "DEM Setup"
            DemSetupReason.HILL_SHADING -> "Elevation data needed"
            DemSetupReason.LIVE_ELEVATION -> "Elevation data needed"
            DemSetupReason.SLOPE_OVERLAY -> "Elevation data needed"
        }
    val message =
        when (reason) {
            DemSetupReason.GENERIC ->
                "For each offline map, use the DEM icon to download elevation data (DEM).\n" +
                    "Grey icon means not downloaded.\n" +
                    "Green icon means ready for hill/slope layers."
            DemSetupReason.HILL_SHADING ->
                "Hill shading needs DEM data for this map.\n" +
                    "Open Maps and tap the DEM icon to download it.\n" +
                    "When it is ready, come back and enable Hill shading again."
            DemSetupReason.LIVE_ELEVATION ->
                "Live elevation needs DEM data for this map.\n" +
                    "Open Maps and tap the DEM icon to download it.\n" +
                    "When it is ready, come back and enable Live elevation again."
            DemSetupReason.SLOPE_OVERLAY ->
                "Slope overlay needs DEM data for this map.\n" +
                    "Open Maps and tap the DEM icon to download it.\n" +
                    "When it is ready, come back and enable Slope overlay again."
        }
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
            horizontalAlignment = Alignment.CenterHorizontally,
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
                                if (totalDrag > DEM_SETUP_DRAG_DISMISS_PX) {
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = adaptive.dialogBodyMaxHeight)
                        .onPreRotaryScrollEvent { event ->
                            val consumed = scrollState.dispatchRawDelta(event.verticalScrollPixels)
                            abs(consumed) > 0.5f
                        }.focusRequester(focusRequester)
                        .focusable()
                        .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (reason == DemSetupReason.GENERIC) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Landscape,
                            contentDescription = "Elevation icon",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "DEM",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "DEM means elevation data",
                            tint = Color.White.copy(alpha = 0.82f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Text(
                    text = message,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
