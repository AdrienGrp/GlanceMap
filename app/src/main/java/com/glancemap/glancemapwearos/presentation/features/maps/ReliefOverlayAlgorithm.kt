package com.glancemap.glancemapwearos.presentation.features.maps

internal object ReliefOverlayAlgorithm {
    const val MODEL_VERSION = "SLOPE_V3"

    const val COARSE_STEP_LOW = 16
    const val COARSE_STEP_MID = 14
    const val COARSE_STEP_HIGH = 12
    const val FINE_STEP_LOW = 12
    const val FINE_STEP_MID = 10
    const val FINE_STEP_HIGH = 8

    const val SLOPE_BAND_1_DEG = 15.0
    const val SLOPE_BAND_2_DEG = 22.0
    const val SLOPE_BAND_3_DEG = 29.0
    const val SLOPE_BAND_4_DEG = 36.0
    const val SLOPE_BAND_5_DEG = 43.0

    fun cacheSignature(): String =
        buildString {
            append("MODEL:")
            append(MODEL_VERSION)
            append("|COARSE_STEP:")
            append("$COARSE_STEP_LOW,$COARSE_STEP_MID,$COARSE_STEP_HIGH")
            append("|FINE_STEP:")
            append("$FINE_STEP_LOW,$FINE_STEP_MID,$FINE_STEP_HIGH")
            append("|SLOPE_BANDS:")
            append(
                "$SLOPE_BAND_1_DEG,$SLOPE_BAND_2_DEG,$SLOPE_BAND_3_DEG," +
                    "$SLOPE_BAND_4_DEG,$SLOPE_BAND_5_DEG",
            )
            append("|ESTIMATOR:CENTRAL_HORN")
            append("|VOID:RENORMALIZED")
        }
}
