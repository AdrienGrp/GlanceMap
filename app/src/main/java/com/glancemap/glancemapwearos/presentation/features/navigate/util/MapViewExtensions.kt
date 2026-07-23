package com.glancemap.glancemapwearos.presentation.features.navigate

import android.os.Looper
import org.mapsforge.map.android.view.MapView
import java.util.WeakHashMap

internal fun MapView.requestLayerRedrawSafely() {
    MapViewRedrawCoordinator.request(this)
}

/** Coalesces overlay redraw requests from compass, marker, trace, and accuracy layers per frame. */
private object MapViewRedrawCoordinator {
    private val lock = Any()
    private val pendingMapViews = WeakHashMap<MapView, Boolean>()

    fun request(mapView: MapView) {
        val shouldSchedule =
            synchronized(lock) {
                if (pendingMapViews.containsKey(mapView)) {
                    false
                } else {
                    pendingMapViews[mapView] = true
                    true
                }
            }
        if (!shouldSchedule) return

        val scheduleOnAnimationFrame = {
            mapView.postOnAnimation {
                synchronized(lock) {
                    pendingMapViews.remove(mapView)
                }
                try {
                    mapView.layerManager.redrawLayers()
                } catch (_: Throwable) {
                    mapView.postInvalidate()
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            scheduleOnAnimationFrame()
        } else {
            mapView.post { scheduleOnAnimationFrame() }
        }
    }
}
