@file:Suppress("MatchingDeclarationName")

package com.glancemap.glancemapwearos.presentation.features.maps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.presentation.ui.WearInfoDialog

enum class DemSetupReason {
    GENERIC,
    HILL_SHADING,
    LIVE_ELEVATION,
    SLOPE_OVERLAY,
}

@Suppress("FunctionName", "LongMethod")
@Composable
fun DemSetupBottomSheet(
    visible: Boolean,
    reason: DemSetupReason = DemSetupReason.GENERIC,
    onDismiss: () -> Unit,
) {
    if (!visible) return
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

    WearInfoDialog(
        visible = visible,
        title = title,
        onDismiss = onDismiss,
    ) {
        if (reason == DemSetupReason.GENERIC) {
            item {
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
        }
        item {
            Text(
                text = message,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
