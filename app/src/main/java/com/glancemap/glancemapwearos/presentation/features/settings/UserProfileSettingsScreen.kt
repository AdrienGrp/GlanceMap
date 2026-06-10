package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import kotlin.math.roundToInt

@Composable
fun UserProfileSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenGeneralSettings: () -> Unit,
) {
    val listTokens = rememberSettingsListTokens()
    val isMetric by viewModel.isMetric.collectAsState()
    val userWeightKg by viewModel.userWeightKg.collectAsState()
    val backpackWeightKg by viewModel.backpackWeightKg.collectAsState()
    var showUnitsPicker by remember { mutableStateOf(false) }
    var showWeightPicker by remember { mutableStateOf(false) }
    var showBackpackPicker by remember { mutableStateOf(false) }

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            GeneralSettingsShortcutChip(onClick = onOpenGeneralSettings)
        }
        item {
            SettingsPickerChip(
                label = "Units",
                secondaryLabel = if (isMetric) "Metric" else "Imperial",
                onClick = { showUnitsPicker = true },
            )
        }
        item {
            SettingsPickerChip(
                label = "Body weight",
                secondaryLabel = formatUserWeight(userWeightKg, isMetric),
                onClick = { showWeightPicker = true },
            )
        }
        item {
            SettingsPickerChip(
                label = "Backpack",
                secondaryLabel = formatUserWeight(backpackWeightKg, isMetric),
                onClick = { showBackpackPicker = true },
            )
        }
        item {
            PandolfInfoRow()
        }
    }

    OptionPickerDialog(
        visible = showUnitsPicker,
        title = "Units",
        selectedValue = isMetric,
        options =
            listOf(
                true to "Metric",
                false to "Imperial",
            ),
        onDismiss = { showUnitsPicker = false },
        onSelect = viewModel::setMetric,
    )
    OptionPickerDialog(
        visible = showWeightPicker,
        title = "Body weight",
        selectedValue = userWeightKg.roundToInt().toFloat(),
        options = USER_WEIGHT_OPTIONS_KG.map { weightKg -> weightKg to formatUserWeight(weightKg, isMetric) },
        onDismiss = { showWeightPicker = false },
        onSelect = viewModel::setUserWeightKg,
    )
    OptionPickerDialog(
        visible = showBackpackPicker,
        title = "Backpack",
        selectedValue = backpackWeightKg.roundToInt().toFloat(),
        options = BACKPACK_WEIGHT_OPTIONS_KG.map { weightKg -> weightKg to formatUserWeight(weightKg, isMetric) },
        onDismiss = { showBackpackPicker = false },
        onSelect = viewModel::setBackpackWeightKg,
    )
}

private val USER_WEIGHT_OPTIONS_KG =
    (
        SettingsRepository.MIN_USER_WEIGHT_KG.roundToInt()..SettingsRepository.MAX_USER_WEIGHT_KG.roundToInt()
    ).map { it.toFloat() }

private val BACKPACK_WEIGHT_OPTIONS_KG =
    (
        SettingsRepository.MIN_BACKPACK_WEIGHT_KG.roundToInt()..
            SettingsRepository.MAX_BACKPACK_WEIGHT_KG.roundToInt()
    ).map { it.toFloat() }

@Composable
private fun PandolfInfoRow() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Calories use segment Pandolf/Santee from weight, pack, speed and slope.",
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
            textAlign = TextAlign.Start,
        )
    }
}

private fun formatUserWeight(
    weightKg: Float,
    isMetric: Boolean,
): String =
    if (isMetric) {
        "${weightKg.roundToInt()} kg"
    } else {
        "${(weightKg * KG_TO_LB).roundToInt()} lb"
    }

private const val KG_TO_LB = 2.2046226218f
