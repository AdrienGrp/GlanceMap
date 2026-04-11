package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.material.Chip

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun ResetDefaultsConfirmScreen(
    onCancel: () -> Unit,
    onConfirmReset: () -> Unit,
) {
    val listTokens = rememberSettingsListTokens()
    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding =
                PaddingValues(
                    start = listTokens.horizontalPadding,
                    end = listTokens.horizontalPadding,
                    top = listTokens.topPadding,
                    bottom = listTokens.bottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(listTokens.itemSpacing),
        ) {
            item {
                Text(
                    text = "Reset to defaults",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
            item {
                Text(
                    text = "This resets settings and theme preferences.",
                    textAlign = TextAlign.Center,
                )
            }
            item {
                Chip(
                    label = "Reset now",
                    secondaryLabel = "Cannot be undone",
                    onClick = onConfirmReset,
                )
            }
            item {
                Chip(
                    label = "Cancel",
                    onClick = onCancel,
                )
            }
        }
    }
}
