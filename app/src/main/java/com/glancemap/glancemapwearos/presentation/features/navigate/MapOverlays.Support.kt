package com.glancemap.glancemapwearos.presentation.features.navigate

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.caverock.androidsvg.PreserveAspectRatio
import com.caverock.androidsvg.SVG
import com.glancemap.glancemapwearos.data.repository.PoiType
import com.glancemap.glancemapwearos.presentation.features.maps.RotatableMarker
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.Layer
import org.mapsforge.map.layer.Layers
import org.mapsforge.map.layer.overlay.Marker
import kotlin.math.roundToInt

internal data class PoiMarkerEntry(
    val marker: Marker,
    val type: PoiType,
    val markerSizePx: Int
)

private val PoiType.markerLabel: String
    get() = when (this) {
        PoiType.PEAK -> "M"
        PoiType.WATER -> "W"
        PoiType.HUT -> "H"
        PoiType.CAMP -> "C"
        PoiType.FOOD -> "F"
        PoiType.TOILET -> "T"
        PoiType.TRANSPORT -> "R"
        PoiType.BIKE -> "B"
        PoiType.VIEWPOINT -> "V"
        PoiType.PARKING -> "P"
        PoiType.SHOP -> "S"
        PoiType.GENERIC -> ""
        PoiType.CUSTOM -> ""
    }

private val PoiType.markerColorArgb: Int
    get() = when (this) {
        PoiType.PEAK -> android.graphics.Color.rgb(121, 85, 72)
        PoiType.WATER -> android.graphics.Color.rgb(3, 169, 244)
        PoiType.HUT -> android.graphics.Color.rgb(121, 85, 72)
        PoiType.CAMP -> android.graphics.Color.rgb(76, 175, 80)
        PoiType.FOOD -> android.graphics.Color.rgb(255, 152, 0)
        PoiType.TOILET -> android.graphics.Color.rgb(156, 39, 176)
        PoiType.TRANSPORT -> android.graphics.Color.rgb(33, 150, 243)
        PoiType.BIKE -> android.graphics.Color.rgb(0, 188, 212)
        PoiType.VIEWPOINT -> android.graphics.Color.rgb(255, 193, 7)
        PoiType.PARKING -> android.graphics.Color.rgb(63, 81, 181)
        PoiType.SHOP -> android.graphics.Color.rgb(96, 125, 139)
        PoiType.GENERIC -> android.graphics.Color.rgb(96, 125, 139)
        PoiType.CUSTOM -> android.graphics.Color.rgb(255, 213, 79)
    }

private val PoiType.osmIconAssetName: String
    get() = when (this) {
        PoiType.PEAK -> "peak.svg"
        PoiType.WATER -> "water.svg"
        PoiType.HUT -> "hut.svg"
        PoiType.CAMP -> "camp.svg"
        PoiType.FOOD -> "food.svg"
        PoiType.TOILET -> "toilet.svg"
        PoiType.TRANSPORT -> "transport.svg"
        PoiType.BIKE -> "bike.svg"
        PoiType.VIEWPOINT -> "viewpoint.svg"
        PoiType.PARKING -> "parking.svg"
        PoiType.SHOP -> "shop.svg"
        PoiType.GENERIC -> "generic.svg"
        PoiType.CUSTOM -> "custom.svg"
    }

internal fun loadOsmPoiIconBitmapOrNull(
    mapView: MapView,
    type: PoiType,
    sizePx: Int = 20
): Bitmap? {
    val assetPath = "poi/osm/${type.osmIconAssetName}"
    return runCatching {
        mapView.context.assets.open(assetPath).use { input ->
            val svg = SVG.getFromInputStream(input)
            svg.setDocumentPreserveAspectRatio(PreserveAspectRatio.LETTERBOX)
            svg.setDocumentWidth("${sizePx}px")
            svg.setDocumentHeight("${sizePx}px")
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            svg.renderToCanvas(canvas, RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()))
            bitmap
        }
    }.getOrNull()
}

internal fun createPoiTypeMarkerBitmap(
    type: PoiType,
    iconBitmap: Bitmap?,
    sizePx: Int
): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = sizePx / 2f
    val radius = center - 1f

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = type.markerColorArgb
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 1.6f
    }
    canvas.drawCircle(center, center, radius, fillPaint)
    canvas.drawCircle(center, center, radius - 0.8f, strokePaint)

    if (iconBitmap != null) {
        val iconShadowRect = RectF(
            sizePx * 0.14f,
            sizePx * 0.14f,
            sizePx * 0.86f,
            sizePx * 0.86f
        )
        val iconRect = RectF(
            sizePx * 0.17f,
            sizePx * 0.17f,
            sizePx * 0.83f,
            sizePx * 0.83f
        )
        val iconShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = 210
            colorFilter = android.graphics.PorterDuffColorFilter(
                android.graphics.Color.BLACK,
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = 255
            colorFilter = android.graphics.PorterDuffColorFilter(
                android.graphics.Color.WHITE,
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        }
        canvas.drawBitmap(iconBitmap, null, iconShadowRect, iconShadowPaint)
        canvas.drawBitmap(iconBitmap, null, iconRect, iconPaint)
    } else {
        val labelStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.45f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val labelFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.45f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val fm = labelFill.fontMetrics
        val textY = center - (fm.ascent + fm.descent) / 2f
        canvas.drawText(type.markerLabel, center, textY, labelStroke)
        canvas.drawText(type.markerLabel, center, textY, labelFill)
    }

    return bitmap
}

internal fun ensureTopOverlayOrder(
    layers: Layers,
    accuracyCircleLayer: GpsAccuracyCircleLayer?,
    coneLayer: CompassConeLayer?,
    locationMarker: RotatableMarker?,
    markerA: Marker?,
    markerB: Marker?
): Boolean {
    val desiredTopOrder = buildList<Layer>(5) {
        if (
            accuracyCircleLayer != null &&
            layers.contains(accuracyCircleLayer) &&
            accuracyCircleLayer.isVisible
        ) {
            add(accuracyCircleLayer)
        }
        if (coneLayer != null && layers.contains(coneLayer)) add(coneLayer)
        if (locationMarker != null && layers.contains(locationMarker)) add(locationMarker)
        if (markerA != null && layers.contains(markerA)) add(markerA)
        if (markerB != null && layers.contains(markerB)) add(markerB)
    }
    if (desiredTopOrder.isEmpty()) return false
    val layerCount = layers.size()
    if (layerCount < desiredTopOrder.size) return false

    val startIndex = layerCount - desiredTopOrder.size
    val alreadyOrdered = desiredTopOrder.indices.all { i ->
        layers.get(startIndex + i) === desiredTopOrder[i]
    }
    if (alreadyOrdered) return false

    desiredTopOrder.forEach { layer -> layers.remove(layer) }
    desiredTopOrder.forEach { layer -> layers.add(layer) }
    return true
}

internal fun setMarkerLatLongIfChanged(marker: Marker, target: LatLong): Boolean {
    val current = marker.latLong
    if (current.latitude == target.latitude && current.longitude == target.longitude) {
        return false
    }
    marker.latLong = target
    return true
}

internal fun applyOpacityToColor(
    color: Int,
    opacityPercent: Int
): Int {
    val clampedPercent = opacityPercent.coerceIn(0, 100)
    val alpha = ((clampedPercent / 100f) * 255f).roundToInt()
    return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}

internal fun sanitizeGpsAccuracyMeters(rawAccuracyMeters: Float): Float? {
    if (!rawAccuracyMeters.isFinite() || rawAccuracyMeters <= 0f) return null
    return rawAccuracyMeters.coerceIn(
        GPS_ACCURACY_CIRCLE_MIN_RADIUS_METERS,
        GPS_ACCURACY_CIRCLE_MAX_RADIUS_METERS
    )
}

private const val GPS_ACCURACY_CIRCLE_MIN_RADIUS_METERS = 3f
private const val GPS_ACCURACY_CIRCLE_MAX_RADIUS_METERS = 250f
