package com.glancemap.glancemapwearos.presentation.features.routetools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ViewComfyAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

@Composable
internal fun BoxScope.RouteShortcutTray(
    expanded: Boolean,
    keepAppOpen: Boolean,
    edgePadding: Dp,
    anchorSize: Dp,
    adjacentAccessoryWidth: Dp,
    actionHeight: Dp,
    iconSize: Dp,
    onToggleExpanded: () -> Unit,
    onKeepAppOpenClick: () -> Unit,
    onGpxToolsClick: () -> Unit,
    onCreatePoiClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = edgePadding),
    ) {
        AnimatedVisibility(
            visible = expanded,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(y = (-6).dp)
                    .padding(end = anchorSize + adjacentAccessoryWidth + 18.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                ShortcutActionChip(
                    text = "Stay",
                    height = actionHeight,
                    onClick = onKeepAppOpenClick,
                ) {
                    Icon(
                        imageVector =
                            if (keepAppOpen) {
                                Icons.Default.Visibility
                            } else {
                                Icons.Default.VisibilityOff
                            },
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint =
                            if (keepAppOpen) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.White
                            },
                    )
                }
                ShortcutActionChip(
                    text = "GPX",
                    height = actionHeight,
                    onClick = onGpxToolsClick,
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = Color.White,
                    )
                }
                ShortcutActionChip(
                    text = "POI",
                    height = actionHeight,
                    onClick = onCreatePoiClick,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = Color(0xFFFFD54F),
                    )
                }
            }
        }

        IconButton(
            onClick = onToggleExpanded,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(anchorSize),
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.78f),
                    contentColor = Color.White,
                ),
        ) {
            if (expanded) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close shortcuts",
                    modifier = Modifier.size(iconSize),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ViewComfyAlt,
                    contentDescription = "Open shortcuts",
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

@Composable
private fun ShortcutActionChip(
    text: String,
    height: Dp,
    onClick: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
) {
    val hitTargetHeight = height.coerceAtLeast(48.dp)
    val visualHeight = (height + 10.dp).coerceIn(36.dp, 40.dp)

    Box(
        modifier =
            Modifier
                .width(88.dp)
                .height(hitTargetHeight)
                .clickable(
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(
            modifier =
                Modifier
                    .width(80.dp)
                    .height(visualHeight)
                    .background(
                        color = Color.Black.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(percent = 50),
                    ).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.invoke()
            Text(
                text = text,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
