package com.glancemap.glancemapwearos.presentation.features.routetools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.presentation.features.gpx.GpxTrackDetails
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.view.MapView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun BoxScope.RouteReshapeHandlesOverlay(
    session: RouteToolSession,
    activeTrack: GpxTrackDetails?,
    mapView: MapView,
    mapRotationDeg: Float,
    viewportRevision: Int,
) {
    if (
        session.options.toolKind != RouteToolKind.MODIFY ||
        session.options.modifyMode != RouteModifyMode.RESHAPE_ROUTE ||
        activeTrack == null
    ) {
        return
    }

    if (session.currentSelectionTarget == RouteSelectionTarget.RESHAPE_POINT) return

    val selectedPoint = session.pointA ?: return
    val screenOffset =
        projectLatLongToScreenOffset(
            mapView = mapView,
            latLong = selectedPoint,
            mapRotationDeg = mapRotationDeg,
            viewportRevision = viewportRevision,
        ) ?: return

    Box(
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .offset {
                    IntOffset(
                        x = (screenOffset.x - 11f).toInt(),
                        y = (screenOffset.y - 11f).toInt(),
                    )
                }.size(26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.20f), CircleShape),
        )
        Box(
            modifier =
                Modifier
                    .size(22.dp)
                    .background(Color(0xFF4FC3F7).copy(alpha = 0.94f), CircleShape)
                    .border(3.dp, Color.Black.copy(alpha = 0.86f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "1",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun projectLatLongToScreenOffset(
    mapView: MapView,
    latLong: LatLong,
    mapRotationDeg: Float,
    viewportRevision: Int,
): Offset? {
    viewportRevision.hashCode()
    if (mapView.width <= 0 || mapView.height <= 0) return null

    val mapPoint =
        runCatching {
            mapView.mapViewProjection.toPixels(latLong)
        }.getOrNull() ?: return null

    val (screenX, screenY) =
        rotateMapSpaceToScreen(
            x = mapPoint.x,
            y = mapPoint.y,
            mapWidth = mapView.width.toDouble(),
            mapHeight = mapView.height.toDouble(),
            mapRotationDeg = mapRotationDeg.toDouble(),
        )
    return Offset(screenX.toFloat(), screenY.toFloat())
}

private fun rotateMapSpaceToScreen(
    x: Double,
    y: Double,
    mapWidth: Double,
    mapHeight: Double,
    mapRotationDeg: Double,
): Pair<Double, Double> {
    if (mapWidth <= 0.0 || mapHeight <= 0.0) return x to y
    if (abs(mapRotationDeg) < 0.001) return x to y

    val cx = mapWidth / 2.0
    val cy = mapHeight / 2.0
    val rad = Math.toRadians(mapRotationDeg)
    val c = cos(rad)
    val s = sin(rad)

    val dx = x - cx
    val dy = y - cy

    val rx = dx * c - dy * s
    val ry = dx * s + dy * c

    return (cx + rx) to (cy + ry)
}
