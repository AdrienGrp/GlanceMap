package com.glancemap.glancemapwearos.presentation.features.routetools

import com.glancemap.glancemapwearos.core.routing.LoopRouteSuggestionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteToolLoopSuggestionsTest {

    @Test
    fun circuitLoopFailureOffersOutAndBackFallbackFirst() {
        val options = RouteToolOptions(
            toolKind = RouteToolKind.CREATE,
            createMode = RouteCreateMode.LOOP_AROUND_HERE,
            loopShapeMode = LoopShapeMode.PREFER_CIRCUIT
        )

        val retryOptions = buildLoopRetryOptions(
            base = options,
            error = LoopRouteSuggestionException(lowerDistanceMeters = null, higherDistanceMeters = null)
        )

        assertEquals(1, retryOptions.size)
        assertEquals("Allow out-and-back", retryOptions.first().label)
        assertEquals(LoopShapeMode.ALLOW_OUT_AND_BACK, retryOptions.first().options.loopShapeMode)
    }

    @Test
    fun distanceSuggestionsKeepFallbackAndDistanceAlternatives() {
        val options = RouteToolOptions(
            toolKind = RouteToolKind.CREATE,
            createMode = RouteCreateMode.LOOP_AROUND_HERE,
            loopShapeMode = LoopShapeMode.PREFER_CIRCUIT,
            loopDistanceKm = 10
        )

        val retryOptions = buildLoopRetryOptions(
            base = options,
            error = LoopRouteSuggestionException(lowerDistanceMeters = 8_700, higherDistanceMeters = 11_300)
        )

        assertEquals(listOf("Allow out-and-back", "9 km", "11 km"), retryOptions.map { it.label })
    }

    @Test
    fun outAndBackModeDoesNotOfferDuplicateOutAndBackRetry() {
        val options = RouteToolOptions(
            toolKind = RouteToolKind.CREATE,
            createMode = RouteCreateMode.LOOP_AROUND_HERE,
            loopShapeMode = LoopShapeMode.ALLOW_OUT_AND_BACK,
            loopDistanceKm = 10
        )

        val retryOptions = buildLoopRetryOptions(
            base = options,
            error = LoopRouteSuggestionException(lowerDistanceMeters = 8_700, higherDistanceMeters = null)
        )

        assertEquals(1, retryOptions.size)
        assertEquals("9 km", retryOptions.first().label)
        assertTrue(retryOptions.none { it.label == "Allow out-and-back" })
    }

    @Test
    fun timeSuggestionsConvertDistancesIntoRoundedDurations() {
        val options = RouteToolOptions(
            toolKind = RouteToolKind.CREATE,
            createMode = RouteCreateMode.LOOP_AROUND_HERE,
            loopTargetMode = LoopTargetMode.TIME,
            loopDurationMinutes = 120,
            loopShapeMode = LoopShapeMode.PREFER_CIRCUIT,
            routeStyle = RouteStylePreset.BALANCED_HIKE,
            useElevation = true
        )

        val retryOptions = buildLoopRetryOptions(
            base = options,
            error = LoopRouteSuggestionException(lowerDistanceMeters = 8_700, higherDistanceMeters = 11_300)
        )

        assertEquals(listOf("Allow out-and-back", "2 h 15", "2 h 45"), retryOptions.map { it.label })
    }

    @Test
    fun visibleLoopDefaultsResetHiddenOutAndBackState() {
        val options = RouteToolOptions(
            toolKind = RouteToolKind.CREATE,
            createMode = RouteCreateMode.LOOP_AROUND_HERE,
            loopShapeMode = LoopShapeMode.ALLOW_OUT_AND_BACK
        )

        val sanitized = options.withVisibleLoopDefaults()

        assertEquals(LoopShapeMode.PREFER_CIRCUIT, sanitized.loopShapeMode)
    }
}
