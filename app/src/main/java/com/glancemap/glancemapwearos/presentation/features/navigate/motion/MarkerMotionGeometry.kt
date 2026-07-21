package com.glancemap.glancemapwearos.presentation.features.navigate.motion

import org.mapsforge.core.model.LatLong
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal object MarkerMotionGeometry {
    data class CorrectionComponents(
        val alongTrackM: Float,
        val crossTrackM: Float,
    )

    fun bearingBetweenDegrees(
        from: LatLong,
        to: LatLong,
    ): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x =
            cos(lat1) * sin(lat2) -
                sin(lat1) * cos(lat2) * cos(dLon)
        val bearingDeg = Math.toDegrees(atan2(y, x)).toFloat()
        return normalizeBearing360(bearingDeg)
    }

    fun correctionComponents(
        from: LatLong,
        to: LatLong,
        travelBearingDeg: Float,
    ): CorrectionComponents {
        val distanceM = distanceMeters(from, to)
        val correctionBearingDeg = bearingBetweenDegrees(from, to)
        val relativeBearingRad =
            Math.toRadians(
                ((((correctionBearingDeg - travelBearingDeg) + 540f) % 360f) - 180f).toDouble(),
            )
        return CorrectionComponents(
            alongTrackM = (distanceM * cos(relativeBearingRad)).toFloat(),
            crossTrackM = (distanceM * sin(relativeBearingRad)).toFloat(),
        )
    }

    fun distanceMeters(
        from: LatLong,
        to: LatLong,
    ): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val a =
            sin(dLat / 2.0) * sin(dLat / 2.0) +
                cos(lat1) * cos(lat2) * sin(dLon / 2.0) * sin(dLon / 2.0)
        val c = 2.0 * asin(sqrt(a))
        return (EARTH_RADIUS_METERS * c).toFloat()
    }

    private fun normalizeBearing360(angleDeg: Float): Float {
        var normalized = angleDeg % 360f
        if (normalized < 0f) normalized += 360f
        return normalized
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0
}
