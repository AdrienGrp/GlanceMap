package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.material.Chip
import com.google.android.horologist.compose.material.ToggleChip
import com.google.android.horologist.compose.material.ToggleChipToggleControl

private val PickerChipBackground = Color(0xFF2B2F36)
private val PickerChipContent = Color(0xFFF1F5FB)
private val PickerChipSecondary = Color(0xFFBAC5D4)
private val PickerChipIcon = Color(0xFF9FB2C9)

private val SectionChipBackground = Color(0xFF1F3554)
private val SectionChipContent = Color(0xFFF4F7FB)
private val SectionChipSecondary = Color(0xFFC9D7EA)
private val SectionChipIcon = Color(0xFFF6C453)

@OptIn(ExperimentalHorologistApi::class)
@Composable
internal fun SettingsToggleChip(
    checked: Boolean,
    onCheckedChanged: (Boolean) -> Unit,
    label: String,
    secondaryLabel: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    ToggleChip(
        modifier = modifier,
        enabled = enabled,
        checked = checked,
        onCheckedChanged = onCheckedChanged,
        label = label,
        secondaryLabel = secondaryLabel,
        toggleControl = ToggleChipToggleControl.Switch
    )
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
internal fun SettingsPickerChip(
    label: String,
    onClick: () -> Unit,
    secondaryLabel: String? = null,
    iconImageVector: ImageVector? = Icons.Filled.UnfoldMore,
    modifier: Modifier = Modifier
) {
    if (iconImageVector != null) {
        Chip(
            modifier = modifier,
            label = label,
            secondaryLabel = secondaryLabel,
            icon = {
                Icon(
                    imageVector = iconImageVector,
                    contentDescription = null,
                    modifier = Modifier.size(ChipDefaults.IconSize)
                )
            },
            colors = ChipDefaults.secondaryChipColors(
                backgroundColor = PickerChipBackground,
                contentColor = PickerChipContent,
                secondaryContentColor = PickerChipSecondary,
                iconColor = PickerChipIcon
            ),
            onClick = onClick
        )
    } else {
        Chip(
            modifier = modifier,
            label = label,
            secondaryLabel = secondaryLabel,
            colors = ChipDefaults.secondaryChipColors(
                backgroundColor = PickerChipBackground,
                contentColor = PickerChipContent,
                secondaryContentColor = PickerChipSecondary
            ),
            onClick = onClick
        )
    }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
internal fun SettingsSectionChip(
    label: String,
    onClick: () -> Unit,
    iconImageVector: ImageVector = Icons.Filled.Folder,
    secondaryLabel: String? = null,
    modifier: Modifier = Modifier
) {
    Chip(
        modifier = modifier,
        label = label,
        secondaryLabel = secondaryLabel,
        icon = {
            Icon(
                imageVector = iconImageVector,
                contentDescription = null,
                modifier = Modifier.size(ChipDefaults.IconSize)
            )
        },
        colors = ChipDefaults.secondaryChipColors(
            backgroundColor = SectionChipBackground,
            contentColor = SectionChipContent,
            secondaryContentColor = SectionChipSecondary,
            iconColor = SectionChipIcon
        ),
        onClick = onClick
    )
}
