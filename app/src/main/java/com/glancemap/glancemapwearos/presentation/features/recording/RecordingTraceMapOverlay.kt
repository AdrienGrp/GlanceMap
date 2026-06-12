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
    points: List<LatLong>,
) {
    val paint =
        remember {
            AndroidGraphicFactory.INSTANCE.createPaint().apply {
                setStyle(Style.STROKE)
                color = Color.argb(240, 0, 200, 83)
                strokeWidth = 5f
            }
        }
    val polyline =
        remember(mapView) {
            Polyline(paint, AndroidGraphicFactory.INSTANCE)
        }

    LaunchedEffect(mapView, points) {
        mapView.mutateLayers { layers ->
            var changed = false
            val attached = layers.contains(polyline)
            if (points.size >= MIN_RECORDING_TRACE_POINTS) {
                if (!attached) {
                    layers.add(polyline)
                    changed = true
                }
                if (!sameLatLongs(polyline.latLongs, points)) {
                    polyline.latLongs.clear()
                    polyline.latLongs.addAll(points)
                    changed = true
                }
            } else if (attached) {
                layers.remove(polyline)
                polyline.latLongs.clear()
                changed = true
            }
            if (changed) {
                mapView.requestLayerRedrawSafely()
            }
        }
    }

    DisposableEffect(mapView) {
        onDispose {
            mapView.mutateLayers { layers ->
                if (layers.remove(polyline)) {
                    polyline.latLongs.clear()
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
