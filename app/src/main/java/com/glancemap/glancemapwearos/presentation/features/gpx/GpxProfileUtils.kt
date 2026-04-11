package com.glancemap.glancemapwearos.presentation.features.gpx

import com.glancemap.glancemapwearos.core.gpx.GpxElevationFilterConfig
import com.glancemap.glancemapwearos.core.gpx.GpxElevationFilterDefaults
import org.mapsforge.core.model.LatLong
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class FileSig(val lastModified: Long, val length: Long)

internal data class TrackProfile(
    val sig: FileSig,
    val elevationFilterConfig: GpxElevationFilterConfig,
    val points: List<TrackPoint>,
    val segLen: DoubleArray,
    val cumDist: DoubleArray,
    val cumAscent: DoubleArray,
    val cumDescent: DoubleArray
)

internal data class ParsedGpxData(
    val title: String?,
    val points: List<TrackPoint>,
    val totalDistance: Double
)

private val B_ROUTER_DISPLAY_REGEX = Regex("brouter", RegexOption.IGNORE_CASE)

internal fun sigOf(file: File): FileSig = FileSig(
    lastModified = file.lastModified(),
    length = file.length()
)

internal fun <K, V> LinkedHashMap<K, V>.trimTo(max: Int) {
    while (size > max) {
        val it = entries.iterator()
        if (!it.hasNext()) break
        it.next()
        it.remove()
    }
}

internal fun buildProfile(
    sig: FileSig,
    pts: List<TrackPoint>,
    elevationFilterConfig: GpxElevationFilterConfig = GpxElevationFilterDefaults.defaultConfig()
): TrackProfile {
    val n = pts.size
    val segLen = DoubleArray((n - 1).coerceAtLeast(0))
    val cumDist = DoubleArray(n)

    if (n <= 1) {
        val cumAsc = DoubleArray(n)
        val cumDesc = DoubleArray(n)
        return TrackProfile(sig, elevationFilterConfig, pts, segLen, cumDist, cumAsc, cumDesc)
    }

    var dist = 0.0

    cumDist[0] = 0.0

    for (i in 0 until n - 1) {
        val a = pts[i]
        val b = pts[i + 1]

        val d = haversine(
            a.latLong.latitude,
            a.latLong.longitude,
            b.latLong.latitude,
            b.latLong.longitude
        )

        segLen[i] = d
        dist += d
        cumDist[i + 1] = dist
    }

    val (cumAsc, cumDesc) = buildCanonicalElevationCumulative(
        points = pts,
        segmentLengths = segLen,
        cumulativeDistances = cumDist,
        elevationFilterConfig = elevationFilterConfig
    )

    return TrackProfile(sig, elevationFilterConfig, pts, segLen, cumDist, cumAsc, cumDesc)
}

internal fun readBestGpxTitle(file: File): String? = parseGpxData(file).title

internal fun parseGpxPoints(file: File): List<TrackPoint> = parseGpxData(file).points

internal fun normalizeUserFacingGpxText(value: String?): String? {
    return value
        ?.takeIf { it.isNotBlank() }
        ?.replace(B_ROUTER_DISPLAY_REGEX, "BRouter")
}

internal fun parseGpxData(file: File): ParsedGpxData {
    var trkName: String? = null
    var metaName: String? = null
    val points = mutableListOf<TrackPoint>()
    var totalDistance = 0.0
    var lastPoint: LatLong? = null

    var inTrk = false
    var inMetadata = false
    var trkDepth = -1
    var metadataDepth = -1

    var inTrackPoint = false
    var currentLat: Double? = null
    var currentLon: Double? = null
    var currentElevation: Double? = null
    var currentHasTimestamp = false

    return try {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        FileInputStream(file).use { input ->
            parser.setInput(input, "UTF-8")

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "trk" -> {
                                inTrk = true
                                trkDepth = parser.depth
                            }
                            "metadata" -> {
                                inMetadata = true
                                metadataDepth = parser.depth
                            }
                            "name" -> {
                                val depth = parser.depth
                                val text = parser.nextText()?.trim()?.takeIf { it.isNotBlank() }
                                if (text != null) {
                                    if (inTrk && trkName == null && depth == trkDepth + 1) {
                                        trkName = text
                                    }
                                    if (inMetadata && metaName == null && depth == metadataDepth + 1) {
                                        metaName = text
                                    }
                                }
                            }
                            "trkpt" -> {
                                inTrackPoint = true
                                currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                                currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                                currentElevation = null
                                currentHasTimestamp = false
                            }
                            "ele" -> {
                                if (inTrackPoint) {
                                    currentElevation = parser.nextText()?.trim()?.toDoubleOrNull()
                                }
                            }
                            "time" -> {
                                if (inTrackPoint) {
                                    currentHasTimestamp = !parser.nextText().isNullOrBlank()
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "trk" -> inTrk = false
                            "metadata" -> inMetadata = false
                            "trkpt" -> {
                                if (inTrackPoint) {
                                    val lat = currentLat
                                    val lon = currentLon
                                    if (lat != null && lon != null) {
                                        val latLong = LatLong(lat, lon)
                                        points += TrackPoint(
                                            latLong = latLong,
                                            elevation = currentElevation,
                                            hasTimestamp = currentHasTimestamp
                                        )

                                        lastPoint?.let { previous ->
                                            totalDistance += haversine(
                                                previous.latitude,
                                                previous.longitude,
                                                latLong.latitude,
                                                latLong.longitude
                                            )
                                        }
                                        lastPoint = latLong
                                    }
                                }

                                inTrackPoint = false
                                currentLat = null
                                currentLon = null
                                currentElevation = null
                                currentHasTimestamp = false
                            }
                        }
                    }
                }
                event = parser.next()
            }
        }

        ParsedGpxData(
            title = normalizeUserFacingGpxText(trkName ?: metaName),
            points = points,
            totalDistance = totalDistance
        )
    } catch (_: Exception) {
        ParsedGpxData(
            title = null,
            points = emptyList(),
            totalDistance = 0.0
        )
    }
}

internal val TrackProfile.totalDistance: Double
    get() = cumDist.lastOrNull() ?: 0.0

internal val TrackProfile.totalAscent: Double
    get() = cumAscent.lastOrNull() ?: 0.0

internal val TrackProfile.totalDescent: Double
    get() = cumDescent.lastOrNull() ?: 0.0

private data class EffectiveElevationFilter(
    val smoothingDistanceMeters: Double,
    val neutralDiffThresholdMeters: Double,
    val trendActivationThresholdMeters: Double,
    val minimumGradePercent: Double
)

private fun buildCanonicalElevationCumulative(
    points: List<TrackPoint>,
    segmentLengths: DoubleArray,
    cumulativeDistances: DoubleArray,
    elevationFilterConfig: GpxElevationFilterConfig
): Pair<DoubleArray, DoubleArray> {
    val count = points.size
    val cumAsc = DoubleArray(count)
    val cumDesc = DoubleArray(count)
    if (count <= 1) return cumAsc to cumDesc

    val normalizedElevations = normalizeElevations(
        points = points,
        cumulativeDistances = cumulativeDistances
    ) ?: return cumAsc to cumDesc

    val effectiveFilter = resolveEffectiveElevationFilter(
        points = points,
        normalizedElevations = normalizedElevations,
        segmentLengths = segmentLengths,
        cumulativeDistances = cumulativeDistances,
        baseConfig = elevationFilterConfig
    )

    val smoothedElevations = smoothElevationsByDistance(
        elevations = normalizedElevations,
        segmentLengths = segmentLengths,
        smoothingDistanceMeters = effectiveFilter.smoothingDistanceMeters
    )

    val neutralThresholdMeters = effectiveFilter.neutralDiffThresholdMeters
    val trendActivationThresholdMeters = effectiveFilter.trendActivationThresholdMeters

    var ascent = 0.0
    var descent = 0.0
    var pendingAscent = 0.0
    var pendingDescent = 0.0
    var ascentActive = false
    var descentActive = false

    for (index in 1 until count) {
        val stepMeters = segmentLengths.getOrElse(index - 1) { 0.0 }.coerceAtLeast(0.0)
        val diff = applyMinimumGradeGate(
            diffMeters = smoothedElevations[index] - smoothedElevations[index - 1],
            stepMeters = stepMeters,
            minimumGradePercent = effectiveFilter.minimumGradePercent
        )
        when {
            diff > neutralThresholdMeters -> {
                pendingDescent = 0.0
                descentActive = false
                if (ascentActive) {
                    ascent += diff
                } else {
                    pendingAscent += diff
                    if (pendingAscent >= trendActivationThresholdMeters) {
                        ascent += pendingAscent
                        pendingAscent = 0.0
                        ascentActive = true
                    }
                }
            }

            diff < -neutralThresholdMeters -> {
                val loss = -diff
                pendingAscent = 0.0
                ascentActive = false
                if (descentActive) {
                    descent += loss
                } else {
                    pendingDescent += loss
                    if (pendingDescent >= trendActivationThresholdMeters) {
                        descent += pendingDescent
                        pendingDescent = 0.0
                        descentActive = true
                    }
                }
            }
        }

        cumAsc[index] = ascent
        cumDesc[index] = descent
    }

    return cumAsc to cumDesc
}

private fun resolveEffectiveElevationFilter(
    points: List<TrackPoint>,
    normalizedElevations: DoubleArray,
    segmentLengths: DoubleArray,
    cumulativeDistances: DoubleArray,
    baseConfig: GpxElevationFilterConfig
): EffectiveElevationFilter {
    if (!baseConfig.autoAdjustPerGpx) {
        return EffectiveElevationFilter(
            smoothingDistanceMeters = baseConfig.smoothingDistanceMeters.toDouble(),
            neutralDiffThresholdMeters = baseConfig.neutralDiffThresholdMeters.toDouble(),
            trendActivationThresholdMeters = baseConfig.trendActivationThresholdMeters.toDouble(),
            minimumGradePercent = 0.0
        )
    }
    val totalDistanceMeters = cumulativeDistances.lastOrNull() ?: 0.0
    val totalDistanceKm = totalDistanceMeters / 1000.0
    val reliefMeters = ((normalizedElevations.maxOrNull() ?: 0.0) -
        (normalizedElevations.minOrNull() ?: 0.0)).coerceAtLeast(0.0)
    val reliefPerKm = if (totalDistanceKm > 0.0) reliefMeters / totalDistanceKm else 0.0
    val coarseHighReliefFactor = resolveCoarseHighReliefFactor(points, reliefPerKm)
    val lowReliefPerKmFactor = (
        (LOW_RELIEF_REFERENCE_METERS_PER_KM - reliefPerKm) / LOW_RELIEF_BLEND_RANGE_METERS_PER_KM
        ).coerceIn(0.0, 1.0)
    // Avoid classifying very long mountain traverses as "low relief" just because the route is long.
    val lowReliefFactor = lowReliefPerKmFactor * resolveLowReliefAbsoluteReliefFactor(reliefMeters)
    val recordedLowReliefFactor = resolveRecordedLowReliefFactor(points, lowReliefFactor)
    val editedLowReliefDenseTrackFactor = resolveEditedLowReliefDensityFactor(segmentLengths)
    val editedLowReliefDensityFactor = if (recordedLowReliefFactor > 0.0) {
        0.0
    } else {
        lowReliefFactor * editedLowReliefDenseTrackFactor
    }

    return EffectiveElevationFilter(
        smoothingDistanceMeters = (
            baseConfig.smoothingDistanceMeters.toDouble() -
                (editedLowReliefDensityFactor * LOW_RELIEF_SMOOTHING_REDUCTION_METERS) -
                (recordedLowReliefFactor * RECORDED_LOW_RELIEF_SMOOTHING_REDUCTION_METERS) +
                (coarseHighReliefFactor * COARSE_HIGH_RELIEF_SMOOTHING_BOOST_METERS)
            ).coerceAtLeast(GpxElevationFilterDefaults.MIN_SMOOTHING_DISTANCE_METERS.toDouble()),
        neutralDiffThresholdMeters = baseConfig.neutralDiffThresholdMeters.toDouble(),
        trendActivationThresholdMeters = (
            baseConfig.trendActivationThresholdMeters.toDouble() -
                (editedLowReliefDensityFactor * LOW_RELIEF_TREND_REDUCTION_METERS) -
                (recordedLowReliefFactor * RECORDED_LOW_RELIEF_TREND_REDUCTION_METERS) +
                (coarseHighReliefFactor * COARSE_HIGH_RELIEF_TREND_BOOST_METERS)
            ).coerceAtLeast(
            GpxElevationFilterDefaults.MIN_TREND_ACTIVATION_THRESHOLD_METERS.toDouble()
        ),
        minimumGradePercent = if (recordedLowReliefFactor > 0.0) {
            0.0
        } else {
            lowReliefFactor * (
                LOW_RELIEF_SPARSE_MIN_GRADE_PERCENT +
                    (editedLowReliefDenseTrackFactor *
                        LOW_RELIEF_DENSE_MIN_GRADE_BOOST_PERCENT)
                )
        }
    )
}

private fun resolveRecordedLowReliefFactor(
    points: List<TrackPoint>,
    lowReliefFactor: Double
): Double {
    val timestampFraction = points.count(TrackPoint::hasTimestamp).toDouble() /
        points.size.coerceAtLeast(1).toDouble()
    return if (timestampFraction >= RECORDED_LOW_RELIEF_MIN_TIMESTAMP_FRACTION) {
        lowReliefFactor
    } else {
        0.0
    }
}

private fun resolveLowReliefAbsoluteReliefFactor(reliefMeters: Double): Double {
    return (
        (LOW_RELIEF_ZERO_ABSOLUTE_RELIEF_METERS - reliefMeters) /
            (LOW_RELIEF_ZERO_ABSOLUTE_RELIEF_METERS - LOW_RELIEF_FULL_ABSOLUTE_RELIEF_METERS)
        ).coerceIn(0.0, 1.0)
}

private fun resolveEditedLowReliefDensityFactor(segmentLengths: DoubleArray): Double {
    if (segmentLengths.isEmpty()) return 0.0
    val sorted = segmentLengths.copyOf().apply { sort() }
    val middleIndex = sorted.size / 2
    val medianSegmentMeters = if (sorted.size % 2 == 0) {
        (sorted[middleIndex - 1] + sorted[middleIndex]) / 2.0
    } else {
        sorted[middleIndex]
    }
    return (
        (LOW_RELIEF_SPARSE_TRACK_MEDIAN_SEGMENT_METERS - medianSegmentMeters) /
            (LOW_RELIEF_SPARSE_TRACK_MEDIAN_SEGMENT_METERS - LOW_RELIEF_DENSE_TRACK_MEDIAN_SEGMENT_METERS)
        ).coerceIn(0.0, 1.0)
}

private fun resolveCoarseHighReliefFactor(
    points: List<TrackPoint>,
    reliefPerKm: Double
): Double {
    val knownElevations = points.mapNotNull(TrackPoint::elevation)
    if (knownElevations.isEmpty()) return 0.0
    val integerLikeFraction = knownElevations.count { elevation ->
        abs(elevation - kotlin.math.round(elevation)) <= COARSE_ELEVATION_INTEGER_TOLERANCE_METERS
    }.toDouble() / knownElevations.size.toDouble()
    if (integerLikeFraction < COARSE_ELEVATION_MIN_INTEGER_FRACTION) return 0.0
    return (
        (reliefPerKm - COARSE_HIGH_RELIEF_REFERENCE_METERS_PER_KM) /
            COARSE_HIGH_RELIEF_BLEND_RANGE_METERS_PER_KM
        ).coerceIn(0.0, 1.0)
}

private fun applyMinimumGradeGate(
    diffMeters: Double,
    stepMeters: Double,
    minimumGradePercent: Double
): Double {
    if (diffMeters == 0.0 || stepMeters <= 0.0 || minimumGradePercent <= 0.0) {
        return diffMeters
    }
    val gradePercent = (abs(diffMeters) / stepMeters) * 100.0
    return if (gradePercent < minimumGradePercent) 0.0 else diffMeters
}

private fun normalizeElevations(
    points: List<TrackPoint>,
    cumulativeDistances: DoubleArray
): DoubleArray? {
    val knownIndices = points.indices.filter { points[it].elevation != null }
    if (knownIndices.isEmpty()) return null

    val normalized = DoubleArray(points.size)
    val firstKnownIndex = knownIndices.first()
    val firstKnownElevation = points[firstKnownIndex].elevation ?: return null
    for (index in 0..firstKnownIndex) {
        normalized[index] = firstKnownElevation
    }

    for (knownIndexPosition in 0 until knownIndices.lastIndex) {
        val startIndex = knownIndices[knownIndexPosition]
        val endIndex = knownIndices[knownIndexPosition + 1]
        val startElevation = points[startIndex].elevation ?: continue
        val endElevation = points[endIndex].elevation ?: continue
        val startDistance = cumulativeDistances.getOrElse(startIndex) { 0.0 }
        val endDistance = cumulativeDistances.getOrElse(endIndex) { startDistance }

        normalized[startIndex] = startElevation
        for (index in startIndex + 1 until endIndex) {
            val currentDistance = cumulativeDistances.getOrElse(index) { startDistance }
            val t = if (endDistance > startDistance) {
                ((currentDistance - startDistance) / (endDistance - startDistance)).coerceIn(0.0, 1.0)
            } else {
                (index - startIndex).toDouble() / (endIndex - startIndex).toDouble()
            }
            normalized[index] = startElevation + t * (endElevation - startElevation)
        }
        normalized[endIndex] = endElevation
    }

    val lastKnownIndex = knownIndices.last()
    val lastKnownElevation = points[lastKnownIndex].elevation ?: return normalized
    for (index in lastKnownIndex until points.size) {
        normalized[index] = lastKnownElevation
    }

    return normalized
}

private fun smoothElevationsByDistance(
    elevations: DoubleArray,
    segmentLengths: DoubleArray,
    smoothingDistanceMeters: Double
): DoubleArray {
    if (elevations.isEmpty()) return elevations
    val smoothed = DoubleArray(elevations.size)
    smoothed[0] = elevations[0]
    for (index in 1 until elevations.size) {
        val stepMeters = segmentLengths.getOrElse(index - 1) { 0.0 }.coerceAtLeast(0.0)
        val alpha = (stepMeters / (smoothingDistanceMeters + stepMeters))
            .coerceIn(MIN_ELEVATION_SMOOTHING_ALPHA, 1.0)
        smoothed[index] = smoothed[index - 1] + alpha * (elevations[index] - smoothed[index - 1])
    }
    return smoothed
}

internal fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val radiusMeters = 6371e3
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dPhi = Math.toRadians(lat2 - lat1)
    val dLambda = Math.toRadians(lon2 - lon1)

    val a = sin(dPhi / 2) * sin(dPhi / 2) +
        cos(phi1) * cos(phi2) * sin(dLambda / 2) * sin(dLambda / 2)

    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return radiusMeters * c
}

private const val MIN_ELEVATION_SMOOTHING_ALPHA = 0.18
private const val LOW_RELIEF_REFERENCE_METERS_PER_KM = 10.0
private const val LOW_RELIEF_BLEND_RANGE_METERS_PER_KM = 4.0
private const val LOW_RELIEF_FULL_ABSOLUTE_RELIEF_METERS = 150.0
private const val LOW_RELIEF_ZERO_ABSOLUTE_RELIEF_METERS = 400.0
private const val LOW_RELIEF_SMOOTHING_REDUCTION_METERS = 1.0
private const val LOW_RELIEF_TREND_REDUCTION_METERS = 0.5
private const val LOW_RELIEF_SPARSE_MIN_GRADE_PERCENT = 1.4
private const val LOW_RELIEF_DENSE_MIN_GRADE_BOOST_PERCENT = 0.8
private const val LOW_RELIEF_DENSE_TRACK_MEDIAN_SEGMENT_METERS = 40.0
private const val LOW_RELIEF_SPARSE_TRACK_MEDIAN_SEGMENT_METERS = 80.0
private const val RECORDED_LOW_RELIEF_MIN_TIMESTAMP_FRACTION = 0.95
private const val RECORDED_LOW_RELIEF_SMOOTHING_REDUCTION_METERS = 1.0
private const val RECORDED_LOW_RELIEF_TREND_REDUCTION_METERS = 2.5
private const val COARSE_ELEVATION_INTEGER_TOLERANCE_METERS = 0.01
private const val COARSE_ELEVATION_MIN_INTEGER_FRACTION = 0.95
private const val COARSE_HIGH_RELIEF_REFERENCE_METERS_PER_KM = 35.0
private const val COARSE_HIGH_RELIEF_BLEND_RANGE_METERS_PER_KM = 15.0
private const val COARSE_HIGH_RELIEF_SMOOTHING_BOOST_METERS = 5.0
private const val COARSE_HIGH_RELIEF_TREND_BOOST_METERS = 0.2
