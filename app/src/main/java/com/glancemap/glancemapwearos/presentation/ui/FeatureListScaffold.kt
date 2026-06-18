package com.glancemap.glancemapwearos.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

@Composable
fun FeatureListScaffold(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

@Composable
fun FeatureListHeader(
    title: String,
    topPadding: Dp,
    bottomPadding: Dp,
    actionSpacing: Dp,
    verticalSpacing: Dp,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    statusText: String? = null,
    statusColor: Color = MaterialTheme.colorScheme.onBackground,
    statusTopPadding: Dp = 0.dp,
    actions: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = topPadding, bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        ) {
            Text(
                text = title,
                style = titleStyle,
                textAlign = TextAlign.Center,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(actionSpacing),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
            statusText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = statusTopPadding),
                )
            }
        }
    }
}
