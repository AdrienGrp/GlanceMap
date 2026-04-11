package com.glancemap.glancemapwearos.core.service.location.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelfHealFailoverCoordinatorTest {
    @Test
    fun returnsNullBelowAccuracyThresholds() {
        val requiredStreak =
            resolveAutoFusedAccuracyFailoverRequiredStreak(
                accuracyM = 80f,
                fixGapMs = 10_000L,
                expectedIntervalMs = 3_000L,
            )

        assertNull(requiredStreak)
    }

    @Test
    fun usesSeverePlateauStreakWhenAccuracyIsVeryPoorAndFixGapIsLarge() {
        val requiredStreak =
            resolveAutoFusedAccuracyFailoverRequiredStreak(
                accuracyM = 100f,
                fixGapMs = 7_000L,
                expectedIntervalMs = 3_000L,
            )

        assertEquals(3, requiredStreak)
        assertEquals(100f, resolveAutoFusedFailoverThresholdM(requiredStreak ?: 0), 0.001f)
    }

    @Test
    fun usesStandardStreakWhenFixGapIsStillShort() {
        val requiredStreak =
            resolveAutoFusedAccuracyFailoverRequiredStreak(
                accuracyM = 125f,
                fixGapMs = 2_000L,
                expectedIntervalMs = 3_000L,
            )

        assertEquals(4, requiredStreak)
        assertEquals(120f, resolveAutoFusedFailoverThresholdM(requiredStreak ?: 0), 0.001f)
    }

    @Test
    fun usesSeverePlateauStreakForVeryPoorAccuracyWhenNoAcceptedFixesArrive() {
        val requiredStreak =
            resolveAutoFusedAccuracyFailoverRequiredStreak(
                accuracyM = 117f,
                fixGapMs = 12_000L,
                expectedIntervalMs = 3_000L,
            )

        assertEquals(3, requiredStreak)
    }
}
