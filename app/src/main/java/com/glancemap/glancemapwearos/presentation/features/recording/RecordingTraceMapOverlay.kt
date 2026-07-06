package com.glancemap.glancemapwearos.presentation.features.recording

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.glancemap.glancemapwearos.presentation.features.maps.mutateLayers
import com.glancemap.glancemapwearos.presentation.features.navigate.requestLayerRedrawSafely
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Polyline

@Composable
internal fun RecordingTraceOverlayEffect(
    mapView: MapView,
    segments: List<List<LatLong>>,
) {
    val paint =
        remember {
            AndroidGraphicFactory.INSTANCE.createPaint().apply {
                setStyle(Style.STROKE)
                color = Color.argb(240, 0, 200, 83)
                strokeWidth = 5f
            }
        }
    val polylines = remember(mapView) { mutableListOf<Polyline>() }

    LaunchedEffect(mapView, segments) {
        mapView.mutateLayers { layers ->
            var changed = false
            val visibleSegments = segments.filter { it.size >= MIN_RECORDING_TRACE_POINTS }
            while (polylines.size < visibleSegments.size) {
                polylines += Polyline(paint, AndroidGraphicFactory.INSTANCE)
            }
            visibleSegments.forEachIndexed { index, points ->
                val polyline = polylines[index]
                if (!layers.contains(polyline)) {
                    layers.add(polyline)
                    changed = true
                }
                if (!sameLatLongs(polyline.latLongs, points)) {
                    polyline.latLongs.clear()
                    polyline.latLongs.addAll(points)
                    changed = true
                }
            }
            for (index in polylines.lastIndex downTo visibleSegments.size) {
                val polyline = polylines.removeAt(index)
                if (layers.remove(polyline)) {
                    changed = true
                }
                polyline.latLongs.clear()
            }
            if (changed) {
                mapView.requestLayerRedrawSafely()
            }
        }
    }

    DisposableEffect(mapView) {
        onDispose {
            mapView.mutateLayers { layers ->
                var changed = false
                polylines.forEach { polyline ->
                    if (layers.remove(polyline)) {
                        changed = true
                    }
                    polyline.latLongs.clear()
                }
                polylines.clear()
                if (changed) {
                    mapView.requestLayerRedrawSafely()
                }
            }
        }
    }
}

private fun sameLatLongs(
    current: List<LatLong>,
    next: List<LatLong>,
): Boolean {
    if (current.size != next.size) return false
    return current.indices.all { index ->
        current[index].latitude == next[index].latitude &&
            current[index].longitude == next[index].longitude
    }
}

private const val MIN_RECORDING_TRACE_POINTS = 2
