package com.glancemap.glancemapwearos.presentation.features.settings

import java.util.Locale

internal object GpxElevationFilterUi {
    const val SMOOTHING_LABEL = "Smoothing distance"
    const val NOISE_THRESHOLD_LABEL = "Noise threshold"
    const val TREND_THRESHOLD_LABEL = "Trend threshold"

    fun formatSmoothingDistance(valueMeters: Float): String = String.format(Locale.getDefault(), "%.0f m", valueMeters)

    fun formatThreshold(valueMeters: Float): String = String.format(Locale.getDefault(), "%.1f m", valueMeters)
}
