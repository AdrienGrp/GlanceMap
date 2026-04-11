package com.glancemap.glancemapwearos.presentation.formatting

import java.text.DecimalFormat

object UnitFormatter {

    private const val METERS_TO_MILES = 0.000621371
    private const val METERS_TO_FEET = 3.28084

    private val distanceFormat = DecimalFormat("#.##")
    private val elevationFormat = DecimalFormat("#,###")

    /**
     * Returns a pair of (formatted value, unit).
     */
    fun formatDistance(meters: Double, isMetric: Boolean): Pair<String, String> {
        return if (isMetric) {
            val km = meters / 1000.0
            distanceFormat.format(km) to "km"
        } else {
            val miles = meters * METERS_TO_MILES
            distanceFormat.format(miles) to "mi"
        }
    }

    /**
     * Returns a pair of (formatted value, unit).
     */
    fun formatElevation(meters: Double, isMetric: Boolean): Pair<String, String> {
        return if (isMetric) {
            elevationFormat.format(meters) to "m"
        } else {
            val feet = meters * METERS_TO_FEET
            elevationFormat.format(feet) to "ft"
        }
    }
}
