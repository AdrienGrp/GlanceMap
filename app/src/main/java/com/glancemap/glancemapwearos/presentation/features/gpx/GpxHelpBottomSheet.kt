package com.glancemap.glancemapwearos.presentation.features.gpx

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
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.R
import com.glancemap.glancemapwearos.presentation.ui.WearInfoDialog

@Composable
fun GpxHelpBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    WearInfoDialog(
        visible = visible,
        title = "GPX Actions",
        onDismiss = onDismiss,
    ) {
        item {
            Text(
                "Toggle tracks to show or hide them on the map.",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                "Long press a track to view elevation.",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                text =
                    buildAnnotatedString {
                        append("Use the ")
                        appendInlineContent("sendToPhone", "[send]")
                        append(" button to select one or more GPX, then send them to your phone.")
                    },
                modifier = Modifier.fillMaxWidth(),
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
        }
        item {
            Text(
                "Use edit or delete mode to rename or remove tracks.",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
