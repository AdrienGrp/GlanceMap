package com.glancemap.glancemapwearos.presentation.features.navigate

import org.mapsforge.map.android.view.MapView

internal fun MapView.requestLayerRedrawSafely() {
    try {
        layerManager.redrawLayers()
    } catch (_: Throwable) {
        postInvalidate()
    }
}
