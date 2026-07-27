package com.glancemap.glancemapwearos.presentation.features.navigate

import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.layer.Layers
import org.mapsforge.map.layer.overlay.Polyline

internal data class SolidTrackPolylineSyncResult(
    val polylines: List<Polyline>,
    val changed: Boolean,
)

internal fun syncSolidTrackPolylines(
    layers: Layers,
    current: List<Polyline>,
    segments: List<List<LatLong>>,
    paint: Paint,
): SolidTrackPolylineSyncResult {
    var changed = false
    val polylines = current.toMutableList()
    while (polylines.size < segments.size) {
        polylines += Polyline(paint, AndroidGraphicFactory.INSTANCE)
    }
    while (polylines.size > segments.size) {
        val removed = polylines.removeAt(polylines.lastIndex)
        changed = layers.remove(removed) || changed
        removed.latLongs.clear()
    }
    segments.forEachIndexed { index, points ->
        val polyline = polylines[index]
        if (!layers.contains(polyline)) {
            layers.add(polyline)
            changed = true
        }
        if (!hasSameLatLongs(polyline.latLongs, points)) {
            polyline.latLongs.clear()
            polyline.latLongs.addAll(points)
            changed = true
        }
    }
    return SolidTrackPolylineSyncResult(polylines = polylines, changed = changed)
}
