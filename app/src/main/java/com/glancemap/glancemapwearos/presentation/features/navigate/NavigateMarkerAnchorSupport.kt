package com.glancemap.glancemapwearos.presentation.features.navigate

import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.view.MapView

internal fun MapView.setCenterForNavigationMarker(markerLatLong: LatLong) {
    setCenter(markerLatLong)
}
