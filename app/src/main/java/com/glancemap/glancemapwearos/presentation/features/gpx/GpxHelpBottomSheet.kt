package com.glancemap.glancemapwearos.presentation.features.gpx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec

@Composable
fun GpxHelpBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val adaptive = rememberWearAdaptiveSpec()

    AlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = { Text("GPX Actions") },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = adaptive.helpDialogMaxHeight)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Toggle tracks to show or hide them on the map.")
                Text("Long press a track to view elevation.")
                Text("Use the phone-send button to select one or more GPX, then send them to your phone.")
                Text("Use edit or delete mode to rename or remove tracks.")
            }
        },
    )
}
