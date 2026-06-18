package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.material.ToggleChip
import com.google.android.horologist.compose.material.ToggleChipToggleControl

@Composable
internal fun <T> SettingsOptionPickerRow(
    label: String,
    selectedValue: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    dialogTitle: String = label,
    secondaryLabel: String = options.firstOrNull { it.first == selectedValue }?.second.orEmpty(),
    iconImageVector: ImageVector? = Icons.Filled.UnfoldMore,
    selected: Boolean = false,
) {
    SettingsOptionPickerHost(
        title = dialogTitle,
        selectedValue = selectedValue,
        options = options,
        onSelect = onSelect,
    ) { openPicker ->
        SettingsPickerChip(
            label = label,
            secondaryLabel = secondaryLabel,
            iconImageVector = iconImageVector,
            selected = selected,
            modifier = modifier,
            onClick = openPicker,
        )
    }
}

@Composable
internal fun <T> SettingsOptionPickerHost(
    title: String,
    selectedValue: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    content: @Composable (openPicker: () -> Unit) -> Unit,
) {
    var pickerVisible by remember { mutableStateOf(false) }

    content { pickerVisible = true }
    OptionPickerDialog(
        visible = pickerVisible,
        title = title,
        selectedValue = selectedValue,
        options = options,
        onDismiss = { pickerVisible = false },
        onSelect = onSelect,
    )
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
internal fun OptionPickerChoiceRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToggleChip(
        modifier = modifier.fillMaxWidth(),
        checked = selected,
        onCheckedChanged = { checked ->
            if (checked || selected) {
                onSelect()
            }
        },
        label = label,
        toggleControl = ToggleChipToggleControl.Radio,
    )
}
