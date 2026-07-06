package com.glancemap.glancemapwearos.presentation.features.navigate

import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import org.mapsforge.map.android.view.MapView

@Composable
internal fun NavigateMapReadyEffect(
    mapView: MapView?,
    onMapViewReadyForRendering: () -> Unit,
) {
    DisposableEffect(mapView, onMapViewReadyForRendering) {
        if (mapView == null) return@DisposableEffect onDispose {}

        val focusListener =
            ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                if (hasFocus && mapView.isAttachedToWindow && mapView.width > 0 && mapView.height > 0) {
                    onMapViewReadyForRendering()
                }
            }

        val observer = mapView.viewTreeObserver
        observer.addOnWindowFocusChangeListener(focusListener)

        onDispose {
            if (observer.isAlive) {
                observer.removeOnWindowFocusChangeListener(focusListener)
            }
        }
    }
}
