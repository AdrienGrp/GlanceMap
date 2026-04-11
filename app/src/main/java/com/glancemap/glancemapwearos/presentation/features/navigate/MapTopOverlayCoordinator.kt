package com.glancemap.glancemapwearos.presentation.features.navigate

import com.glancemap.glancemapwearos.presentation.features.maps.RotatableMarker
import org.mapsforge.map.layer.Layers
import org.mapsforge.map.layer.overlay.Marker

internal class MapTopOverlayCoordinator(
    private val layers: Layers,
    private val accuracyCircleLayer: GpsAccuracyCircleLayer,
    private val coneLayer: CompassConeLayer,
    private val markerAHolder: Array<Marker?>,
    private val markerBHolder: Array<Marker?>,
) {
    private var locationMarker: RotatableMarker? = null

    fun updateLocationMarker(marker: RotatableMarker?) {
        locationMarker = marker
    }

    fun sync(): Boolean =
        ensureTopOverlayOrder(
            layers = layers,
            accuracyCircleLayer = accuracyCircleLayer,
            coneLayer = coneLayer,
            locationMarker = locationMarker,
            markerA = markerAHolder[0],
            markerB = markerBHolder[0],
        )
}
