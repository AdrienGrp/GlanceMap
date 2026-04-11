package com.glancemap.glancemapcompanionapp

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class CompanionWindowClass {
    COMPACT,
    STANDARD,
    EXPANDED
}

data class CompanionAdaptiveSpec(
    val windowClass: CompanionWindowClass,
    val isCompactScreen: Boolean,
    val usePageScrollForSmallScreen: Boolean,
    val downloadButtonWidth: Dp,
    val downloadButtonHeight: Dp,
    val pagePadding: Dp,
    val titleGap: Dp,
    val sectionGap: Dp,
    val helpIconButtonSize: Dp,
    val helpIconSize: Dp,
    val quickGuideDialogMaxHeight: Dp,
    val refugesDialogMaxHeight: Dp
)

fun companionAdaptiveSpec(
    windowWidth: Dp,
    windowHeight: Dp,
    fontScale: Float
): CompanionAdaptiveSpec {
    val normalizedHeight = windowHeight.value / fontScale.coerceAtLeast(1f)
    val normalizedWidth = windowWidth.value / fontScale.coerceAtLeast(1f)

    val windowClass = when {
        normalizedHeight < 760f || normalizedWidth < 360f || fontScale > 1.05f -> CompanionWindowClass.COMPACT
        normalizedHeight > 960f && normalizedWidth >= 420f && fontScale <= 1.0f -> CompanionWindowClass.EXPANDED
        else -> CompanionWindowClass.STANDARD
    }

    return when (windowClass) {
        CompanionWindowClass.COMPACT -> CompanionAdaptiveSpec(
            windowClass = windowClass,
            isCompactScreen = true,
            usePageScrollForSmallScreen = true,
            downloadButtonWidth = 92.dp,
            downloadButtonHeight = 72.dp,
            pagePadding = 6.dp,
            titleGap = 6.dp,
            sectionGap = 8.dp,
            helpIconButtonSize = 30.dp,
            helpIconSize = 18.dp,
            quickGuideDialogMaxHeight = 360.dp,
            refugesDialogMaxHeight = 380.dp
        )

        CompanionWindowClass.STANDARD -> CompanionAdaptiveSpec(
            windowClass = windowClass,
            isCompactScreen = false,
            usePageScrollForSmallScreen = false,
            downloadButtonWidth = 106.dp,
            downloadButtonHeight = 80.dp,
            pagePadding = 12.dp,
            titleGap = 8.dp,
            sectionGap = 8.dp,
            helpIconButtonSize = 34.dp,
            helpIconSize = 20.dp,
            quickGuideDialogMaxHeight = 460.dp,
            refugesDialogMaxHeight = 500.dp
        )

        CompanionWindowClass.EXPANDED -> CompanionAdaptiveSpec(
            windowClass = windowClass,
            isCompactScreen = false,
            usePageScrollForSmallScreen = false,
            downloadButtonWidth = 112.dp,
            downloadButtonHeight = 84.dp,
            pagePadding = 12.dp,
            titleGap = 8.dp,
            sectionGap = 8.dp,
            helpIconButtonSize = 36.dp,
            helpIconSize = 21.dp,
            quickGuideDialogMaxHeight = 560.dp,
            refugesDialogMaxHeight = 620.dp
        )
    }
}
