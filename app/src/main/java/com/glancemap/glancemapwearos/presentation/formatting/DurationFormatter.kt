package com.glancemap.glancemapwearos.presentation.formatting

import kotlin.math.roundToInt

object DurationFormatter {
    fun formatDurationShort(seconds: Double?): String {
        if (seconds == null || !seconds.isFinite() || seconds <= 0.0) return "--"

        val totalMinutes = (seconds / 60.0).roundToInt().coerceAtLeast(1)
        if (totalMinutes < 60) return "${totalMinutes}m"

        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (minutes == 0) {
            "${hours}h"
        } else {
            "${hours}h${minutes.toString().padStart(2, '0')}"
        }
    }
}
