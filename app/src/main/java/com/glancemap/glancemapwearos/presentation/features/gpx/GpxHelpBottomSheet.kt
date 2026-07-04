package com.glancemap.glancemapwearos.presentation.features.gpx

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AssistantDirection
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Route
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.R
import com.glancemap.glancemapwearos.presentation.ui.WearInfoDialog
import com.glancemap.glancemapwearos.presentation.ui.WearInfoText
import com.glancemap.glancemapwearos.presentation.ui.cappedFontScale

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
            WearInfoText("Toggle tracks on the map.")
        }
        item {
            GpxHelpInlineText(
                iconContent =
                    mapOf(
                        "route" to Icons.Default.Route,
                        "hike" to Icons.AutoMirrored.Filled.DirectionsRun,
                        "bike" to Icons.AutoMirrored.Filled.DirectionsBike,
                    ),
            ) {
                append("Tap ")
                appendInlineContent("route", "[route]")
                append(" to switch between GPX, REC hike ")
                appendInlineContent("hike", "[hike]")
                append(" and REC bike ")
                appendInlineContent("bike", "[bike]")
                append(".")
            }
        }
        item {
            GpxHelpInlineText(iconContent = mapOf("guidance" to Icons.AutoMirrored.Filled.AssistantDirection)) {
                append("Use ")
                appendInlineContent("guidance", "[guidance]")
                append(" to start turn-by-turn.")
            }
        }
        item {
            WearInfoText("Long press for elevation.")
        }
        item {
            cappedFontScale(maxFontScale = 1.08f) {
                Text(
                    text =
                        buildAnnotatedString {
                            append("Use ")
                            appendInlineContent("sendToPhone", "[send]")
                            append(" to send GPX to phone.")
                        },
                    textAlign = TextAlign.Center,
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
        }
        item {
            GpxHelpInlineText(
                iconContent =
                    mapOf(
                        "edit" to Icons.Default.Edit,
                        "delete" to Icons.Default.Delete,
                    ),
            ) {
                append("Use ")
                appendInlineContent("edit", "[edit]")
                append(" and ")
                appendInlineContent("delete", "[delete]")
                append(" to edit or delete tracks.")
            }
        }
    }
}

@Composable
private fun GpxHelpInlineText(
    iconContent: Map<String, ImageVector>,
    textBuilder: androidx.compose.ui.text.AnnotatedString.Builder.() -> Unit,
) {
    cappedFontScale(maxFontScale = 1.08f) {
        Text(
            text = buildAnnotatedString(textBuilder),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            inlineContent =
                iconContent.mapValues { (_, imageVector) ->
                    InlineTextContent(
                        placeholder =
                            Placeholder(
                                width = 16.sp,
                                height = 16.sp,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                            ),
                    ) {
                        Icon(
                            imageVector = imageVector,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
        )
    }
}
