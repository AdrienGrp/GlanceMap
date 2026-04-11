package com.glancemap.glancemapwearos.core.service.location

import com.glancemap.glancemapwearos.core.service.location.policy.LocationFixPolicy
import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationFixPolicyTest {

    @Test
    fun strictFreshMaxAgeUsesMinimumForShortIntervals() {
        val oneSecondMaxAgeMs = LocationFixPolicy.strictFreshFixMaxAgeMs(gpsIntervalMs = 1_000L)
        val maxAgeMs = LocationFixPolicy.strictFreshFixMaxAgeMs(gpsIntervalMs = 4_000L)
        assertEquals(3_000L, oneSecondMaxAgeMs)
        assertEquals(8_000L, maxAgeMs)
    }

    @Test
    fun strictFreshMaxAgeScalesWithInterval() {
        val maxAgeMs = LocationFixPolicy.strictFreshFixMaxAgeMs(gpsIntervalMs = 15_000L)
        assertEquals(30_000L, maxAgeMs)
    }

    @Test
    fun watchGpsSourceRelaxesAccuracyThreshold() {
        val basePolicy = LocationFixPolicy.resolveAcceptancePolicy(
            hasFinePermission = true,
            hasCoarsePermission = true,
            expectedIntervalMs = 3_000L,
            minMaxAgeMs = 6_000L,
            fineMaxAgeMs = 60_000L,
            coarseMaxAgeMs = 120_000L,
            fineMaxAccuracyM = 50f,
            coarseMaxAccuracyM = 120f
        )
        val adaptedPolicy = LocationFixPolicy.adaptAcceptanceForSourceMode(
            policy = basePolicy,
            sourceMode = LocationSourceMode.WATCH_GPS,
            watchGpsMaxAccuracyM = 130f
        )

        assertEquals(130f, adaptedPolicy.maxAccuracyM)
    }

    @Test
    fun autoFusedSourceKeepsBaseAccuracyThreshold() {
        val basePolicy = LocationFixPolicy.resolveAcceptancePolicy(
            hasFinePermission = true,
            hasCoarsePermission = true,
            expectedIntervalMs = 3_000L,
            minMaxAgeMs = 6_000L,
            fineMaxAgeMs = 60_000L,
            coarseMaxAgeMs = 120_000L,
            fineMaxAccuracyM = 50f,
            coarseMaxAccuracyM = 120f
        )
        val adaptedPolicy = LocationFixPolicy.adaptAcceptanceForSourceMode(
            policy = basePolicy,
            sourceMode = LocationSourceMode.AUTO_FUSED,
            watchGpsMaxAccuracyM = 130f
        )

        assertEquals(basePolicy.maxAccuracyM, adaptedPolicy.maxAccuracyM)
    }
}
