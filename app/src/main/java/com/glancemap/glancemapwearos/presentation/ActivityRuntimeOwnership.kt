package com.glancemap.glancemapwearos.presentation

internal fun activityOwnsNavigationRuntime(
    isNavigateScreen: Boolean,
    isAmbient: Boolean,
): Boolean = !isNavigateScreen || isAmbient
