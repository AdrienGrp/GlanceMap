package com.glancemap.glancemapwearos.presentation.features.navigate.motion

import com.glancemap.glancemapwearos.presentation.features.navigate.CompassMarkerQuality

internal data class BetweenFixMotionInputs(
    val compassQuality: CompassMarkerQuality,
    val gpsAccuracyM: Float,
    val gpsFixAgeMs: Long,
    val gpsFreshMaxAgeMs: Long,
    val predictionFreshnessMaxAgeMs: Long,
    val watchGpsDegraded: Boolean,
    val allowLowQualityCompassPrediction: Boolean,
)

internal object BetweenFixMotionPolicy {
    fun allowPrediction(inputs: BetweenFixMotionInputs): Boolean {
        if (inputs.watchGpsDegraded) return false

        if (!inputs.gpsAccuracyM.isFinite() || inputs.gpsAccuracyM > MAX_GPS_ACCURACY_M) {
            return false
        }
        if (inputs.gpsFixAgeMs == Long.MAX_VALUE) return false
        val serviceFreshnessGateMs = inputs.gpsFreshMaxAgeMs.takeIf { it > 0L }
        val uiFreshnessGateMs = inputs.predictionFreshnessMaxAgeMs.takeIf { it > 0L }
        val freshnessGateMs =
            when {
                serviceFreshnessGateMs != null && uiFreshnessGateMs != null ->
                    minOf(serviceFreshnessGateMs, uiFreshnessGateMs)
                serviceFreshnessGateMs != null -> serviceFreshnessGateMs
                uiFreshnessGateMs != null -> uiFreshnessGateMs
                else -> DEFAULT_FRESHNESS_GATE_MS
            }
        if (inputs.gpsFixAgeMs > freshnessGateMs) return false

        return when (inputs.compassQuality) {
            CompassMarkerQuality.GOOD,
            CompassMarkerQuality.MEDIUM,
            -> true
            CompassMarkerQuality.LOW ->
                inputs.allowLowQualityCompassPrediction &&
                    inputs.gpsAccuracyM <= LOW_QUALITY_COMPASS_MAX_GPS_ACCURACY_M
            CompassMarkerQuality.UNRELIABLE -> false
        }
    }
}

private const val MAX_GPS_ACCURACY_M = 35f
private const val LOW_QUALITY_COMPASS_MAX_GPS_ACCURACY_M = 18f
private const val DEFAULT_FRESHNESS_GATE_MS = 6_000L
