package com.glancemap.glancemapwearos.presentation.features.gpx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.R
import com.glancemap.glancemapwearos.presentation.ui.WearDialogScrollBottomSpacer
import com.glancemap.glancemapwearos.presentation.ui.WearDialogScrollableColumn
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
            WearDialogScrollableColumn(
                maxHeight = adaptive.helpDialogMaxHeight,
                modifier =
                    Modifier
                        .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Toggle tracks to show or hide them on the map.")
                Text("Long press a track to view elevation.")
                Text(
                    text =
                        buildAnnotatedString {
                            append("Use the ")
                            appendInlineContent("sendToPhone", "[send]")
                            append(" button to select one or more GPX, then send them to your phone.")
                        },
                    inlineContent =
                        mapOf(
                            "sendToPhone" to
                                InlineTextContent(
                                    placeholder =
                                        Placeholder(
                                            width = 16.sp,
                                            height = 16.sp,
                                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                                        ),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_mobile_arrow_right),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                        ),
                )
                Text("Use edit or delete mode to rename or remove tracks.")
                WearDialogScrollBottomSpacer()
            }
        },
    )
}
