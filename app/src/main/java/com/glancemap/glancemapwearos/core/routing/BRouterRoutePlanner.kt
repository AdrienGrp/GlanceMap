package com.glancemap.glancemapwearos.core.routing

import android.content.Context
import btools.router.FormatGpx
import btools.router.OsmNodeNamed
import btools.router.RoutingContext
import btools.router.RoutingEngine
import btools.util.CheapRuler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.mapsforge.core.model.LatLong
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max

private const val ROUTING_TILE_DEGREES = 5
private const val METERS_PER_LATITUDE_DEGREE = 111_320.0
private const val METERS_PER_LONGITUDE_DEGREE_FLOOR = 10_000.0
private const val LOOP_TILE_COVERAGE_MIN_RADIUS_METERS = 500.0
private const val LOOP_SEARCH_RADIUS_MIN_METERS = 250
private const val LOOP_SEARCH_RADIUS_MAX_METERS = 25_000
private const val BROUTER_DEFAULT_ROUND_TRIP_POINTS = 5
private const val CITY_LOOP_RETRY_POINT_COUNT = 4
private const val CITY_LOOP_RECT_SHORT_EDGE_FACTOR = 0.58
private val LOOP_RADIUS_MULTIPLIERS = listOf(1.0, 0.85, 1.15)
private val SAME_WAY_BACK_RADIUS_MULTIPLIERS = listOf(0.9, 1.0, 1.1)
private val LOOP_CARDINAL_START_DIRECTIONS = listOf(0, 90, 180, 270)
private val LOOP_DIAGONAL_START_DIRECTIONS = listOf(45, 135, 225, 315)

internal fun cityLoopRetryDirections(): List<Int> = LOOP_DIAGONAL_START_DIRECTIONS

internal fun cityLoopRetryPreset(basePreset: RoutePlannerPreset): RoutePlannerPreset {
    return if (basePreset == RoutePlannerPreset.PREFER_EASIEST) {
        basePreset
    } else {
        RoutePlannerPreset.PREFER_EASIEST
    }
}

internal fun <T> rotateLoopVariants(
    values: List<T>,
    variationIndex: Int
): List<T> {
    if (values.isEmpty()) return values
    val normalizedVariation = variationIndex.coerceAtLeast(0)
    val offset = normalizedVariation % values.size
    val rotated = values.drop(offset) + values.take(offset)
    return if (((normalizedVariation / values.size) % 2) == 1) {
        rotated.reversed()
    } else {
        rotated
    }
}

internal fun preferredLoopCandidateIndex(
    candidateCount: Int,
    variationIndex: Int
): Int {
    if (candidateCount <= 1) return 0
    return variationIndex.coerceAtLeast(0) % candidateCount
}

internal fun requiredRoutingSegmentFileNames(
    origin: LatLong,
    destination: LatLong
): List<String> {
    return requiredRoutingSegmentFileNames(listOf(origin, destination))
}

internal fun requiredRoutingSegmentFileNames(points: List<LatLong>): List<String> {
    require(points.isNotEmpty()) { "At least one point is required." }

    val minLat = points.minOf { it.latitude }
    val maxLat = points.maxOf { it.latitude }
    val minLon = points.minOf { it.longitude }
    val maxLon = points.maxOf { it.longitude }

    val latStart = routingTileOrigin(minLat)
    val latEnd = routingTileOrigin(maxLat)
    val lonStart = routingTileOrigin(minLon)
    val lonEnd = routingTileOrigin(maxLon)

    val tiles = linkedSetOf<String>()
    var lat = latStart
    while (lat <= latEnd) {
        var lon = lonStart
        while (lon <= lonEnd) {
            tiles += routingTileFileName(swLat = lat, swLon = lon)
            lon += ROUTING_TILE_DEGREES
        }
        lat += ROUTING_TILE_DEGREES
    }
    return tiles.toList()
}

internal fun requiredRoundTripSegmentFileNames(
    start: LatLong,
    searchRadiusMeters: Int
): List<String> {
    val radiusMeters = max(searchRadiusMeters.toDouble(), LOOP_TILE_COVERAGE_MIN_RADIUS_METERS)
    val latRadiusDeg = radiusMeters / METERS_PER_LATITUDE_DEGREE
    val lonRadiusDeg = radiusMeters / max(
        METERS_PER_LATITUDE_DEGREE * cos(Math.toRadians(start.latitude)),
        METERS_PER_LONGITUDE_DEGREE_FLOOR
    )

    return requiredRoutingSegmentFileNames(
        listOf(
            start,
            LatLong(start.latitude + latRadiusDeg, start.longitude),
            LatLong(start.latitude - latRadiusDeg, start.longitude),
            LatLong(start.latitude, start.longitude + lonRadiusDeg),
            LatLong(start.latitude, start.longitude - lonRadiusDeg)
        )
    )
}

internal fun estimateRoundTripSearchRadiusMeters(
    targetDistanceMeters: Int,
    pointCount: Int,
    allowSameWayBack: Boolean
): Int {
    val normalizedTarget = targetDistanceMeters.coerceIn(2_000, 60_000)
    val routeLengthFactor = when {
        allowSameWayBack -> 2.0
        pointCount <= 3 -> 3.0
        else -> {
            val chordFactor = 2.0 * kotlin.math.sin(Math.PI / (2.0 * pointCount))
            2.0 + (pointCount - 2) * chordFactor
        }
    }
    return (normalizedTarget / routeLengthFactor)
        .toInt()
        .coerceIn(LOOP_SEARCH_RADIUS_MIN_METERS, LOOP_SEARCH_RADIUS_MAX_METERS)
}

internal fun candidateLoopPointCounts(defaultPointCount: Int): List<Int> {
    return linkedSetOf(defaultPointCount, BROUTER_DEFAULT_ROUND_TRIP_POINTS, 4, 6, 3)
        .filter { it in 3..20 }
}

internal fun estimateLoopCoverageRadiusMeters(
    targetDistanceMeters: Int,
    defaultPointCount: Int,
    allowOutAndBack: Boolean
): Int {
    val circuitCoverageRadius = candidateLoopPointCounts(defaultPointCount)
        .maxOf { pointCount ->
            adjustedSearchRadii(
                baseSearchRadiusMeters = estimateRoundTripSearchRadiusMeters(
                    targetDistanceMeters = targetDistanceMeters,
                    pointCount = pointCount,
                    allowSameWayBack = false
                ),
                multipliers = LOOP_RADIUS_MULTIPLIERS
            ).maxOrNull() ?: LOOP_SEARCH_RADIUS_MIN_METERS
        }

    val sameWayBackCoverageRadius = if (allowOutAndBack) {
        adjustedSearchRadii(
            baseSearchRadiusMeters = estimateRoundTripSearchRadiusMeters(
                targetDistanceMeters = targetDistanceMeters,
                pointCount = 3,
                allowSameWayBack = true
            ),
            multipliers = SAME_WAY_BACK_RADIUS_MULTIPLIERS
        ).maxOrNull() ?: LOOP_SEARCH_RADIUS_MIN_METERS
    } else {
        LOOP_SEARCH_RADIUS_MIN_METERS
    }

    return max(circuitCoverageRadius, sameWayBackCoverageRadius)
}

private fun adjustedSearchRadii(
    baseSearchRadiusMeters: Int,
    multipliers: List<Double>
): List<Int> {
    return multipliers.map { multiplier ->
        (baseSearchRadiusMeters * multiplier)
            .toInt()
            .coerceIn(LOOP_SEARCH_RADIUS_MIN_METERS, LOOP_SEARCH_RADIUS_MAX_METERS)
    }.distinct()
}

internal fun normalizeRoutingErrorMessage(message: String): String {
    return when {
        message.isBlank() -> {
            "Could not create route."
        }

        message.startsWith("Missing routing data:", ignoreCase = true) -> {
            message
        }

        message.contains("lookup version mismatch", ignoreCase = true) -> {
            "Routing data is out of date. Refresh the routing packs."
        }

        message.contains("dummy.brf", ignoreCase = true) -> {
            "Routing profiles missing. Reopen route tools and try again."
        }

        message.contains("datafile", ignoreCase = true) -> {
            "Routing data missing"
        }

        message.contains("not found", ignoreCase = true) -> {
            "No route found."
        }

        message.contains("no track found", ignoreCase = true) -> {
            "No route found."
        }

        message.contains("fresh GPS fix", ignoreCase = true) -> {
            "Waiting for GPS"
        }

        else -> message
    }
}

class BRouterRoutePlanner(
    private val context: Context
) : RoutePlanner {

    override suspend fun createRoute(request: RoutePlannerRequest): RoutePlannerOutput {
        return withContext(Dispatchers.IO) {
            try {
                ensureBundledProfilesInstalled()
                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                val routedAttempt = executeRoutingRequest(request)
                buildRoutePlannerOutput(
                    routingContext = routedAttempt.routingContext,
                    engine = routedAttempt.engine,
                    title = "BRouter route $stamp",
                    fileName = "route-$stamp.gpx"
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val normalized = normalizeRoutingErrorMessage(error.message.orEmpty())
                throw IllegalStateException(normalized, error)
            }
        }
    }

    override suspend fun createLoop(request: RoundTripPlannerRequest): RoutePlannerOutput {
        return withContext(Dispatchers.IO) {
            try {
                ensureBundledProfilesInstalled()

                val searchCoverageRadiusMeters = estimateLoopCoverageRadiusMeters(
                    targetDistanceMeters = request.targetDistanceMeters,
                    defaultPointCount = request.pointCount,
                    allowOutAndBack = request.allowOutAndBack
                )
                val missingSegments = findMissingRoundTripSegments(
                    start = request.start,
                    searchRadiusMeters = searchCoverageRadiusMeters
                )
                require(missingSegments.isEmpty()) {
                    buildMissingSegmentsMessage(missingSegments)
                }

                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                val targetDistanceMeters = request.targetDistanceMeters
                val preferAlternativeLoop = request.variationIndex > 0
                val successfulAttempts = mutableListOf<LoopCandidate>()
                var lastErrorMessage: String? = null

                val selectedAttempt = run {
                    fun recordAttempt(attempt: LoopCandidate?) {
                        if (attempt == null) return
                        if (!attempt.hasUsableTrack()) {
                            lastErrorMessage = attempt.engine.errorMessage
                                ?.takeIf { it.isNotBlank() }
                                ?: lastErrorMessage
                            return
                        }
                        successfulAttempts += attempt
                    }

                    fun runAttemptStages(
                        stages: List<List<LoopAttemptSpec>>,
                        stopOnCloseEnough: Boolean = true,
                        runner: (LoopAttemptSpec) -> LoopCandidate?
                    ): LoopCandidate? {
                        for (stage in stages) {
                            for (spec in stage) {
                                recordAttempt(
                                    runCatching { runner(spec) }.getOrNull()
                                )
                            }
                            if (stopOnCloseEnough) {
                                successfulAttempts.preferredCloseEnoughCandidate(
                                    targetDistanceMeters = targetDistanceMeters,
                                    variationIndex = request.variationIndex
                                )?.let {
                                    return it
                                }
                            }
                        }
                        return null
                    }

                    val primaryStages = loopAttemptStages(
                        targetDistanceMeters = targetDistanceMeters,
                        defaultPointCount = request.pointCount,
                        allowOutAndBack = request.allowOutAndBack,
                        variationIndex = request.variationIndex
                    )
                    runAttemptStages(
                        stages = primaryStages.take(1),
                        stopOnCloseEnough = !preferAlternativeLoop
                    ) { spec ->
                        createLoopEngine(request = request, spec = spec)
                    }?.let { return@run it }

                    runAttemptStages(
                        loopCityRetryStages(
                            targetDistanceMeters = targetDistanceMeters,
                            requestPreset = request.preset,
                            variationIndex = request.variationIndex
                        ),
                        stopOnCloseEnough = !preferAlternativeLoop
                    ) { spec ->
                        createLoopFallbackRoute(request = request, spec = spec)
                    }?.let { return@run it }

                    runAttemptStages(
                        stages = primaryStages.drop(1),
                        stopOnCloseEnough = !preferAlternativeLoop
                    ) { spec ->
                        createLoopEngine(request = request, spec = spec)
                    }?.let { return@run it }

                    if (
                        successfulAttempts.isEmpty() ||
                        successfulAttempts.none { it.isCloseEnoughTo(targetDistanceMeters) }
                    ) {
                        runAttemptStages(
                            loopFallbackStages(
                                targetDistanceMeters = targetDistanceMeters,
                                defaultPointCount = request.pointCount,
                                allowOutAndBack = request.allowOutAndBack,
                                variationIndex = request.variationIndex
                            )
                        ) { spec ->
                            createLoopFallbackRoute(request = request, spec = spec)
                        }?.let { return@run it }
                    }

                    successfulAttempts.preferredCloseEnoughCandidate(
                        targetDistanceMeters = targetDistanceMeters,
                        variationIndex = request.variationIndex
                    )
                }

                if (selectedAttempt == null) {
                    val loopSuggestions = successfulAttempts.closestLoopSuggestions(targetDistanceMeters)
                    if (!request.allowOutAndBack || loopSuggestions.hasSuggestion()) {
                        throw LoopRouteSuggestionException(
                            lowerDistanceMeters = loopSuggestions.lowerDistanceMeters,
                            higherDistanceMeters = loopSuggestions.higherDistanceMeters
                        )
                    }
                    error(
                        lastErrorMessage
                            ?.takeIf { it.isNotBlank() }
                            ?: if (request.allowOutAndBack) {
                                "No loop found near the selected target. Try a different start or target."
                            } else {
                                "No circuit loop found near the selected target. Try a different start or target."
                            }
                    )
                }

                val errorMessage = selectedAttempt.engine.errorMessage
                require(errorMessage.isNullOrBlank()) {
                    normalizeRoutingErrorMessage(errorMessage)
                }

                buildRoutePlannerOutput(
                    routingContext = selectedAttempt.routingContext,
                    engine = selectedAttempt.engine,
                    title = "BRouter loop ${request.targetLabel} $stamp",
                    fileName = "loop-$stamp.gpx"
                )
            } catch (suggestion: LoopRouteSuggestionException) {
                throw suggestion
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val normalized = normalizeRoutingErrorMessage(error.message.orEmpty())
                throw IllegalStateException(normalized, error)
            }
        }
    }

    private fun createLoopEngine(
        request: RoundTripPlannerRequest,
        spec: LoopAttemptSpec
    ): LoopAttemptResult {
        val routingContext = RoutingContext().apply {
            localFunction = defaultRoutingProfileFile(context).absolutePath
            outputFormat = "gpx"
            keyValues = buildProfileParams(
                preset = spec.presetOverride ?: request.preset,
                useElevation = request.useElevation,
                allowFerries = request.allowFerries
            )
            roundTripDistance = spec.searchRadiusMeters
            roundTripPoints = spec.pointCount
            allowSamewayback = spec.allowSameWayBack
            startDirection = spec.startDirectionDegrees
        }

        val engine = RoutingEngine(
            null,
            null,
            routingSegmentsDir(context),
            listOf(request.start.toWaypoint("from")),
            routingContext,
            RoutingEngine.BROUTER_ENGINEMODE_ROUNDTRIP
        ).apply {
            quite = true
        }

        engine.doRun(LOOP_ROUND_TRIP_TIMEOUT_MS)
        return LoopAttemptResult(
            spec = spec,
            routingContext = routingContext,
            engine = engine
        )
    }

    private fun createLoopFallbackRoute(
        request: RoundTripPlannerRequest,
        spec: LoopAttemptSpec
    ): RoutedAttemptResult {
        return executeRoutingRequest(
            RoutePlannerRequest(
                origin = request.start,
                destination = request.start,
                viaPoints = buildLoopFallbackViaPoints(
                    start = request.start,
                    spec = spec
                ),
                preset = spec.presetOverride ?: request.preset,
                useElevation = request.useElevation,
                allowFerries = request.allowFerries
            ),
            loopSpec = spec,
            timeoutMs = LOOP_FALLBACK_TIMEOUT_MS
        )
    }

    private fun executeRoutingRequest(
        request: RoutePlannerRequest,
        loopSpec: LoopAttemptSpec? = null,
        timeoutMs: Long = ROUTE_TIMEOUT_MS
    ): RoutedAttemptResult {
        val missingSegments = findMissingSegments(
            listOf(request.origin) + request.viaPoints + request.destination
        )
        require(missingSegments.isEmpty()) {
            buildMissingSegmentsMessage(missingSegments)
        }

        val routingContext = RoutingContext().apply {
            localFunction = defaultRoutingProfileFile(context).absolutePath
            outputFormat = "gpx"
            keyValues = buildProfileParams(
                preset = request.preset,
                useElevation = request.useElevation,
                allowFerries = request.allowFerries
            )
        }

        val engine = RoutingEngine(
            null,
            null,
            routingSegmentsDir(context),
            buildRouteWaypoints(request),
            routingContext,
            RoutingEngine.BROUTER_ENGINEMODE_ROUTING
        ).apply {
            quite = true
        }

        engine.doRun(timeoutMs)

        val errorMessage = engine.errorMessage
        require(errorMessage.isNullOrBlank()) {
            normalizeRoutingErrorMessage(errorMessage)
        }

        return RoutedAttemptResult(
            spec = loopSpec,
            routingContext = routingContext,
            engine = engine
        )
    }

    private fun loopAttemptStages(
        targetDistanceMeters: Int,
        defaultPointCount: Int,
        allowOutAndBack: Boolean,
        variationIndex: Int
    ): List<List<LoopAttemptSpec>> {
        val normalizedPointCount = defaultPointCount.coerceIn(3, 20)
        val defaultBaseRadius = estimateRoundTripSearchRadiusMeters(
            targetDistanceMeters = targetDistanceMeters,
            pointCount = normalizedPointCount,
            allowSameWayBack = false
        )
        val alternatePointCounts = rotateLoopVariants(
            values = candidateLoopPointCounts(normalizedPointCount),
            variationIndex = variationIndex
        )
            .filterNot { it == normalizedPointCount }

        return buildList {
            add(
                rotateLoopVariants(
                    values = adjustedSearchRadii(
                        baseSearchRadiusMeters = defaultBaseRadius,
                        multipliers = LOOP_RADIUS_MULTIPLIERS
                    ),
                    variationIndex = variationIndex
                ).map { searchRadiusMeters ->
                    LoopAttemptSpec(
                        searchRadiusMeters = searchRadiusMeters,
                        pointCount = normalizedPointCount,
                        allowSameWayBack = false
                    )
                }
            )
            add(
                rotateLoopVariants(
                    values = LOOP_CARDINAL_START_DIRECTIONS,
                    variationIndex = variationIndex
                ).map { direction ->
                    LoopAttemptSpec(
                        searchRadiusMeters = defaultBaseRadius,
                        pointCount = normalizedPointCount,
                        allowSameWayBack = false,
                        startDirectionDegrees = direction
                    )
                }
            )
            alternatePointCounts.forEach { pointCount ->
                val baseSearchRadius = estimateRoundTripSearchRadiusMeters(
                    targetDistanceMeters = targetDistanceMeters,
                    pointCount = pointCount,
                    allowSameWayBack = false
                )
                add(
                    listOf(
                        LoopAttemptSpec(
                            searchRadiusMeters = baseSearchRadius,
                            pointCount = pointCount,
                            allowSameWayBack = false
                        )
                    )
                )
            }

            if (allowOutAndBack) {
                val sameWayBackBaseRadius = estimateRoundTripSearchRadiusMeters(
                    targetDistanceMeters = targetDistanceMeters,
                    pointCount = 3,
                    allowSameWayBack = true
                )
                add(
                    adjustedSearchRadii(
                        baseSearchRadiusMeters = sameWayBackBaseRadius,
                        multipliers = SAME_WAY_BACK_RADIUS_MULTIPLIERS
                    ).map { searchRadiusMeters ->
                        LoopAttemptSpec(
                            searchRadiusMeters = searchRadiusMeters,
                            pointCount = 3,
                            allowSameWayBack = true
                        )
                    }
                )
                add(
                    LOOP_CARDINAL_START_DIRECTIONS.map { direction ->
                        LoopAttemptSpec(
                            searchRadiusMeters = sameWayBackBaseRadius,
                            pointCount = 3,
                            allowSameWayBack = true,
                            startDirectionDegrees = direction
                        )
                    }
                )
            }
        }
    }

    private fun loopCityRetryStages(
        targetDistanceMeters: Int,
        requestPreset: RoutePlannerPreset,
        variationIndex: Int
    ): List<List<LoopAttemptSpec>> {
        val retryPreset = cityLoopRetryPreset(requestPreset)
        val baseSearchRadius = estimateRoundTripSearchRadiusMeters(
            targetDistanceMeters = targetDistanceMeters,
            pointCount = CITY_LOOP_RETRY_POINT_COUNT,
            allowSameWayBack = false
        )
        return rotateLoopVariants(
            values = adjustedSearchRadii(
                baseSearchRadiusMeters = baseSearchRadius,
                multipliers = LOOP_RADIUS_MULTIPLIERS
            ),
            variationIndex = variationIndex
        ).map { searchRadiusMeters ->
            rotateLoopVariants(
                values = cityLoopRetryDirections(),
                variationIndex = variationIndex
            ).map { direction ->
                LoopAttemptSpec(
                    searchRadiusMeters = searchRadiusMeters,
                    pointCount = CITY_LOOP_RETRY_POINT_COUNT,
                    allowSameWayBack = false,
                    startDirectionDegrees = direction,
                    presetOverride = retryPreset,
                    fallbackShape = LoopFallbackShape.RECTANGULAR
                )
            }
        }
    }

    private fun loopFallbackStages(
        targetDistanceMeters: Int,
        defaultPointCount: Int,
        allowOutAndBack: Boolean,
        variationIndex: Int
    ): List<List<LoopAttemptSpec>> {
        val loopPointCount = defaultPointCount.coerceIn(3, 20)
        val loopBaseRadius = estimateRoundTripSearchRadiusMeters(
            targetDistanceMeters = targetDistanceMeters,
            pointCount = loopPointCount,
            allowSameWayBack = false
        )
        val loopRadii = rotateLoopVariants(
            values = adjustedSearchRadii(
                baseSearchRadiusMeters = loopBaseRadius,
                multipliers = LOOP_RADIUS_MULTIPLIERS
            ),
            variationIndex = variationIndex
        )

        return buildList {
            add(
                loopRadii.flatMap { searchRadiusMeters ->
                    rotateLoopVariants(
                        values = LOOP_CARDINAL_START_DIRECTIONS,
                        variationIndex = variationIndex
                    ).map { direction ->
                        LoopAttemptSpec(
                            searchRadiusMeters = searchRadiusMeters,
                            pointCount = loopPointCount,
                            allowSameWayBack = false,
                            startDirectionDegrees = direction
                        )
                    }
                }
            )
            if (allowOutAndBack) {
                val sameWayBackRadius = estimateRoundTripSearchRadiusMeters(
                    targetDistanceMeters = targetDistanceMeters,
                    pointCount = 3,
                    allowSameWayBack = true
                )
                add(
                    rotateLoopVariants(
                        values = LOOP_CARDINAL_START_DIRECTIONS,
                        variationIndex = variationIndex
                    ).map { direction ->
                        LoopAttemptSpec(
                            searchRadiusMeters = sameWayBackRadius,
                            pointCount = 3,
                            allowSameWayBack = true,
                            startDirectionDegrees = direction
                        )
                    }
                )
            }
        }
    }

    private fun buildLoopFallbackViaPoints(
        start: LatLong,
        spec: LoopAttemptSpec
    ): List<LatLong> {
        val startDirection = spec.startDirectionDegrees ?: 0
        return if (spec.allowSameWayBack) {
            listOf(
                offsetLatLong(
                    start = start,
                    distanceMeters = spec.searchRadiusMeters.toDouble(),
                    bearingDegrees = startDirection.toDouble()
                )
            )
        } else if (spec.fallbackShape == LoopFallbackShape.RECTANGULAR) {
            val forwardPoint = offsetLatLong(
                start = start,
                distanceMeters = spec.searchRadiusMeters.toDouble(),
                bearingDegrees = startDirection.toDouble()
            )
            val lateralDistance = spec.searchRadiusMeters * CITY_LOOP_RECT_SHORT_EDGE_FACTOR
            val sideBearing = startDirection + 90.0
            val cornerPoint = offsetLatLong(
                start = forwardPoint,
                distanceMeters = lateralDistance,
                bearingDegrees = sideBearing
            )
            val returnEdgePoint = offsetLatLong(
                start = start,
                distanceMeters = lateralDistance,
                bearingDegrees = sideBearing
            )
            listOf(forwardPoint, cornerPoint, returnEdgePoint)
        } else {
            buildList {
                for (index in 1 until spec.pointCount) {
                    val angleOffset = 90.0 - (180.0 * index / spec.pointCount.toDouble())
                    val bearing = startDirection - angleOffset
                    add(
                        offsetLatLong(
                            start = start,
                            distanceMeters = spec.searchRadiusMeters.toDouble(),
                            bearingDegrees = bearing
                        )
                    )
                }
            }
        }
    }

    private fun offsetLatLong(
        start: LatLong,
        distanceMeters: Double,
        bearingDegrees: Double
    ): LatLong {
        val waypoint = start.toWaypoint("offset")
        val destination = CheapRuler.destination(
            waypoint.ilon,
            waypoint.ilat,
            distanceMeters,
            bearingDegrees
        )
        return LatLong(
            destination[1] / B_ROUTER_COORDINATE_SCALE - 90.0,
            destination[0] / B_ROUTER_COORDINATE_SCALE - 180.0
        )
    }

    private data class LoopAttemptSpec(
        val searchRadiusMeters: Int,
        val pointCount: Int,
        val allowSameWayBack: Boolean,
        val startDirectionDegrees: Int? = null,
        val presetOverride: RoutePlannerPreset? = null,
        val fallbackShape: LoopFallbackShape = LoopFallbackShape.POLYGON
    )

    private enum class LoopFallbackShape {
        POLYGON,
        RECTANGULAR
    }

    private data class LoopAttemptResult(
        val spec: LoopAttemptSpec,
        override val routingContext: RoutingContext,
        override val engine: RoutingEngine
    ) : LoopCandidate {
        override fun hasUsableTrack(): Boolean {
            val track = engine.foundTrack ?: return false
            if (!engine.errorMessage.isNullOrBlank()) return false
            return track.nodes?.isNotEmpty() == true
        }

        override fun actualDistanceMeters(): Int {
            return engine.foundTrack?.distance ?: Int.MAX_VALUE
        }

        override fun allowSameWayBack(): Boolean = spec.allowSameWayBack
    }

    private data class RoutedAttemptResult(
        val spec: LoopAttemptSpec? = null,
        override val routingContext: RoutingContext,
        override val engine: RoutingEngine
    ) : LoopCandidate {
        override fun hasUsableTrack(): Boolean {
            val track = engine.foundTrack ?: return false
            if (!engine.errorMessage.isNullOrBlank()) return false
            return track.nodes?.isNotEmpty() == true
        }

        override fun actualDistanceMeters(): Int {
            return engine.foundTrack?.distance ?: Int.MAX_VALUE
        }

        override fun allowSameWayBack(): Boolean = spec?.allowSameWayBack == true
    }

    private interface LoopCandidate {
        val routingContext: RoutingContext
        val engine: RoutingEngine

        fun hasUsableTrack(): Boolean
        fun actualDistanceMeters(): Int
        fun allowSameWayBack(): Boolean

        fun distanceDifferenceFrom(targetDistanceMeters: Int): Int {
            return abs(actualDistanceMeters() - targetDistanceMeters)
        }

        fun isCloseEnoughTo(targetDistanceMeters: Int): Boolean {
            val toleranceMeters = max(1_000, (targetDistanceMeters * 0.18).toInt())
            return distanceDifferenceFrom(targetDistanceMeters) <= toleranceMeters
        }
    }

    private data class LoopDistanceSuggestions(
        val lowerDistanceMeters: Int?,
        val higherDistanceMeters: Int?
    ) {
        fun hasSuggestion(): Boolean = lowerDistanceMeters != null || higherDistanceMeters != null
    }

    private fun List<LoopCandidate>.closestLoopSuggestions(
        targetDistanceMeters: Int
    ): LoopDistanceSuggestions {
        val viableDistances = asSequence()
            .map { it.actualDistanceMeters() }
            .filter { it != Int.MAX_VALUE }
            .distinct()
            .sorted()
            .toList()

        return LoopDistanceSuggestions(
            lowerDistanceMeters = viableDistances.lastOrNull { it < targetDistanceMeters },
            higherDistanceMeters = viableDistances.firstOrNull { it > targetDistanceMeters }
        )
    }

    private fun List<LoopCandidate>.preferredCloseEnoughCandidate(
        targetDistanceMeters: Int,
        variationIndex: Int
    ): LoopCandidate? {
        val closeEnough = asSequence()
            .filter { it.isCloseEnoughTo(targetDistanceMeters) }
            .sortedWith(
                compareBy<LoopCandidate> { if (it.allowSameWayBack()) 1 else 0 }
                    .thenBy { it.distanceDifferenceFrom(targetDistanceMeters) }
            )
            .toList()
        if (closeEnough.isEmpty()) return null
        return closeEnough[preferredLoopCandidateIndex(closeEnough.size, variationIndex)]
    }

    private fun ensureBundledProfilesInstalled() {
        copyAsset(
            assetPath = "brouter/profiles2/lookups.dat",
            targetFile = File(routingProfilesDir(context), "lookups.dat")
        )
        copyAsset(
            assetPath = "brouter/profiles2/$ROUTING_DEFAULT_PROFILE_FILE_NAME",
            targetFile = defaultRoutingProfileFile(context)
        )
        copyAsset(
            assetPath = "brouter/profiles2/$ROUTING_DEFAULT_PROFILE_FILE_NAME",
            targetFile = dummyRoutingProfileFile(context)
        )
        routingSegmentsDir(context)
    }

    private fun copyAsset(
        assetPath: String,
        targetFile: File
    ) {
        targetFile.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun buildProfileParams(
        preset: RoutePlannerPreset,
        useElevation: Boolean,
        allowFerries: Boolean
    ): HashMap<String, String> {
        return hashMapOf<String, String>().apply {
            put("allow_ferries", allowFerries.toProfileNumber())
            put("consider_elevation", useElevation.toProfileNumber())
            when (preset) {
                RoutePlannerPreset.BALANCED_HIKE -> {
                    put("hiking_routes_preference", "0.20")
                    put("path_preference", "0.0")
                    put("SAC_scale_limit", "3")
                    put("SAC_scale_preferred", "1")
                }

                RoutePlannerPreset.PREFER_TRAILS -> {
                    put("hiking_routes_preference", "0.60")
                    put("path_preference", "20.0")
                    put("SAC_scale_limit", "3")
                    put("SAC_scale_preferred", "2")
                    put("consider_forest", "1")
                }

                RoutePlannerPreset.PREFER_EASIEST -> {
                    put("hiking_routes_preference", "0.0")
                    put("path_preference", "0.0")
                    put("SAC_scale_limit", "1")
                    put("SAC_scale_preferred", "1")
                }
            }
        }
    }

    private fun findMissingSegments(
        origin: LatLong,
        destination: LatLong
    ): List<String> {
        return findMissingSegments(listOf(origin, destination))
    }

    private fun findMissingSegments(points: List<LatLong>): List<String> {
        val segmentDir = routingSegmentsDir(context)
        return requiredRoutingSegmentFileNames(points)
            .filterNot { File(segmentDir, it).exists() }
    }

    private fun findMissingRoundTripSegments(
        start: LatLong,
        searchRadiusMeters: Int
    ): List<String> {
        val segmentDir = routingSegmentsDir(context)
        return requiredRoundTripSegmentFileNames(start, searchRadiusMeters)
            .filterNot { File(segmentDir, it).exists() }
    }

    private fun buildMissingSegmentsMessage(missingSegments: List<String>): String {
        return when (missingSegments.size) {
            0 -> "Routing data missing"
            1 -> "Missing routing data: ${missingSegments.first()}"
            else -> "Missing routing data: ${missingSegments.first()} +${missingSegments.size - 1}"
        }
    }

    private fun buildRouteWaypoints(request: RoutePlannerRequest): List<OsmNodeNamed> {
        val viaWaypoints = request.viaPoints.mapIndexed { index, point ->
            point.toWaypoint("via${index + 1}")
        }
        return buildList {
            add(request.origin.toWaypoint("from"))
            addAll(viaWaypoints)
            add(request.destination.toWaypoint("to"))
        }
    }

    private fun buildRoutePlannerOutput(
        routingContext: RoutingContext,
        engine: RoutingEngine,
        title: String,
        fileName: String
    ): RoutePlannerOutput {
        val track = requireNotNull(engine.foundTrack) {
            "No route found."
        }
        require(track.nodes.isNotEmpty()) {
            "No route found."
        }
        val messageSummary = track.message?.takeIf { it.isNotBlank() } ?: title
        when {
            track.messageList == null -> track.messageList = mutableListOf(messageSummary)
            track.messageList.isEmpty() -> track.messageList.add(messageSummary)
        }
        val gpx = FormatGpx(routingContext)
            .format(track)
            .rewriteTrackName(title)
            .toByteArray(Charsets.UTF_8)

        return RoutePlannerOutput(
            fileName = fileName,
            title = title,
            gpxBytes = gpx,
            points = track.nodes.map { node ->
                RouteGeometryPoint(
                    latLong = LatLong(
                        (node.getILat() - B_ROUTER_LATITUDE_OFFSET) / B_ROUTER_COORDINATE_SCALE,
                        (node.getILon() - B_ROUTER_LONGITUDE_OFFSET) / B_ROUTER_COORDINATE_SCALE
                    ),
                    elevation = node.getSElev()
                        .takeUnless { it == Short.MIN_VALUE }
                        ?.let { it / B_ROUTER_ELEVATION_SCALE }
                )
            }
        )
    }

    private fun LatLong.toWaypoint(name: String): OsmNodeNamed {
        return OsmNodeNamed().apply {
            this.name = name
            ilon = ((longitude + 180.0) * 1_000_000.0 + 0.5).toInt()
            ilat = ((latitude + 90.0) * 1_000_000.0 + 0.5).toInt()
        }
    }

    private fun Boolean.toProfileNumber(): String = if (this) "1" else "0"

    private companion object {
        const val B_ROUTER_COORDINATE_SCALE = 1_000_000.0
        const val B_ROUTER_LATITUDE_OFFSET = 90_000_000
        const val B_ROUTER_LONGITUDE_OFFSET = 180_000_000
        const val B_ROUTER_ELEVATION_SCALE = 4.0
        const val ROUTE_TIMEOUT_MS = 60_000L
        const val LOOP_ROUND_TRIP_TIMEOUT_MS = 15_000L
        const val LOOP_FALLBACK_TIMEOUT_MS = 20_000L
    }
}

private fun String.rewriteTrackName(title: String): String {
    val escapedTitle = title
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    val trackNameRegex = Regex(
        pattern = "(<trk>\\s*<name>)(.*?)(</name>)",
        options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    return if (trackNameRegex.containsMatchIn(this)) {
        replaceFirst(trackNameRegex, "$1$escapedTitle$3")
    } else {
        replaceFirst("<trk>", "<trk><name>$escapedTitle</name>")
    }
}

private fun routingTileOrigin(coordinate: Double): Int {
    return floor(coordinate / ROUTING_TILE_DEGREES.toDouble()).toInt() * ROUTING_TILE_DEGREES
}

private fun routingTileFileName(
    swLat: Int,
    swLon: Int
): String {
    return "${formatRoutingTileCoord(swLon, positivePrefix = 'E', negativePrefix = 'W')}" +
        "_${formatRoutingTileCoord(swLat, positivePrefix = 'N', negativePrefix = 'S')}.rd5"
}

private fun formatRoutingTileCoord(
    value: Int,
    positivePrefix: Char,
    negativePrefix: Char
): String {
    val prefix = if (value < 0) negativePrefix else positivePrefix
    return "$prefix${abs(value)}"
}
