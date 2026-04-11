package com.glancemap.glancemapwearos.presentation.features.routetools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.view.MapView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun BoxScope.RouteMultiPointPointsOverlay(
    session: RouteToolSession,
    mapView: MapView,
    mapRotationDeg: Float,
    viewportRevision: Int,
    gpxTrackColor: Int
) {
    if (!session.isMultiPointCreate) return

    val trackColor = Color(gpxTrackColor)

    val projectedPoints = session.chainPoints.mapNotNull { point ->
        projectRouteToolPointToScreenOffset(
            mapView = mapView,
            latLong = point,
            mapRotationDeg = mapRotationDeg,
            viewportRevision = viewportRevision
        )
    }
    if (projectedPoints.isNotEmpty()) {
        val candidateOffset = if (session.usesCrosshair) {
            Offset(
                x = mapView.width / 2f,
                y = mapView.height / 2f
            )
        } else {
            null
        }

        Canvas(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxSize()
        ) {
            projectedPoints.zipWithNext().forEach { (start, end) ->
                drawLine(
                    color = trackColor.copy(alpha = 0.72f),
                    start = start,
                    end = end,
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
            }
            if (candidateOffset != null) {
                drawLine(
                    color = trackColor.copy(alpha = 0.52f),
                    start = projectedPoints.last(),
                    end = candidateOffset,
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
            }
        }
    }

    projectedPoints.forEachIndexed { index, screenOffset ->
        val isLast = index == projectedPoints.lastIndex
        val badgeSize = if (isLast) 20.dp else 18.dp
        val haloSize = badgeSize + 8.dp
        val halfSizePx = if (isLast) 10f else 9f
        val fill = if (isLast) {
            trackColor.copy(alpha = 0.94f)
        } else {
            Color(0xFFF7C948).copy(alpha = 0.92f)
        }
        val labelColor = if (isLast && fill.luminance() < 0.5f) Color.White else Color.Black

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    IntOffset(
                        x = (screenOffset.x - halfSizePx).toInt(),
                        y = (screenOffset.y - halfSizePx).toInt()
                    )
                }
                .size(haloSize),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(haloSize)
                    .background(Color.Black.copy(alpha = 0.24f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .background(fill, CircleShape)
                    .border(2.dp, Color.Black.copy(alpha = 0.82f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (index == 0) "1" else (index + 1).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun projectRouteToolPointToScreenOffset(
    mapView: MapView,
    latLong: LatLong,
    mapRotationDeg: Float,
    viewportRevision: Int
): Offset? {
    viewportRevision.hashCode()
    if (mapView.width <= 0 || mapView.height <= 0) return null

    val mapPoint = runCatching {
        mapView.mapViewProjection.toPixels(latLong)
    }.getOrNull() ?: return null

    val (screenX, screenY) = rotateRouteToolMapSpaceToScreen(
        x = mapPoint.x,
        y = mapPoint.y,
        mapWidth = mapView.width.toDouble(),
        mapHeight = mapView.height.toDouble(),
        mapRotationDeg = mapRotationDeg.toDouble()
    )
    return Offset(screenX.toFloat(), screenY.toFloat())
}

private fun rotateRouteToolMapSpaceToScreen(
    x: Double,
    y: Double,
    mapWidth: Double,
    mapHeight: Double,
    mapRotationDeg: Double
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
