package com.glancemap.glancemapwearos.presentation.features.navigate

import android.content.Context
import android.text.format.DateFormat as AndroidDateFormat
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun navigateTimePattern(context: Context, format: String): String {
    val locale = Locale.getDefault()
    return when (format) {
        SettingsRepository.TIME_FORMAT_24_HOUR ->
            AndroidDateFormat.getBestDateTimePattern(locale, "HH:mm")

        SettingsRepository.TIME_FORMAT_12_HOUR ->
            AndroidDateFormat.getBestDateTimePattern(locale, "h:mm a")

        else -> AndroidDateFormat.getBestDateTimePattern(locale, "HH:mm")
    }
}

internal fun formatNavigateClockTime(context: Context, timeMs: Long, format: String): String {
    val formatter = SimpleDateFormat(navigateTimePattern(context, format), Locale.getDefault())
    return formatter.format(Date(timeMs))
}
