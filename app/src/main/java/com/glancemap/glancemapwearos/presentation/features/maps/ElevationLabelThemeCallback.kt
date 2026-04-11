package com.glancemap.glancemapwearos.presentation.features.maps

import org.mapsforge.core.model.Tag
import org.mapsforge.map.datastore.PointOfInterest
import org.mapsforge.map.layer.renderer.PolylineContainer
import org.mapsforge.map.rendertheme.ThemeCallbackAdapter
import kotlin.math.roundToInt

private const val METERS_TO_FEET = 3.28084

internal fun convertMapEleValueToDisplayText(
    rawEleValue: String,
    isMetric: Boolean,
): String? {
    if (isMetric) return rawEleValue

    val normalized = rawEleValue.trim().replace(',', '.')
    val meters = normalized.toDoubleOrNull() ?: return null
    val feet = (meters * METERS_TO_FEET).roundToInt()
    return feet.toString()
}

internal class ElevationLabelThemeCallback(
    private val isMetricProvider: () -> Boolean,
) : ThemeCallbackAdapter() {
    override fun getText(
        poi: PointOfInterest,
        text: String,
    ): String = convertIfElevationLabel(text = text, tags = poi.tags)

    override fun getText(
        way: PolylineContainer,
        text: String,
    ): String = convertIfElevationLabel(text = text, tags = way.getTags())

    private fun convertIfElevationLabel(
        text: String,
        tags: List<Tag>,
    ): String {
        val elevationValue = tags.firstOrNull { it.key == "ele" }?.value ?: return text
        if (text != elevationValue) return text
        return convertMapEleValueToDisplayText(elevationValue, isMetricProvider()) ?: text
    }
}
