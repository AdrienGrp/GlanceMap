package com.glancemap.glancemapwearos.core.service.diagnostics

import java.util.Locale

internal object TelemetryFormatters {
    fun booleanOrNa(value: Boolean?): String = value?.toString() ?: "na"

    fun decimal(
        value: Number,
        digits: Int,
    ): String = String.format(Locale.US, "%.${digits}f", value.toDouble())

    fun decimalOrNa(
        value: Number?,
        digits: Int,
    ): String = value?.let { decimal(it, digits) } ?: "na"

    fun average(
        total: Int,
        count: Int,
        digits: Int = 2,
    ): String {
        if (count <= 0) return "na"
        return decimal(total.toDouble() / count.toDouble(), digits)
    }

    fun ratePercent(
        numerator: Int,
        denominator: Int,
        digits: Int = 2,
    ): String {
        if (denominator <= 0) return "na"
        return decimal(numerator.toDouble() * 100.0 / denominator.toDouble(), digits)
    }

    fun bytesToMb(bytes: Long): String = decimal(bytes.toDouble() / BYTES_PER_MEGABYTE, 1)

    private const val BYTES_PER_MEGABYTE = 1024.0 * 1024.0
}
