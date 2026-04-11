package com.glancemap.glancemapwearos.core.service.location

import com.glancemap.glancemapwearos.core.service.location.policy.LocationRuntimeMode
import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode
import com.glancemap.glancemapwearos.core.service.location.policy.LocationUpdatePolicy
import com.google.android.gms.location.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocationUpdatePolicyTest {
    @Test
    fun returnsNullWhenTrackingIsDisabled() {
        val config =
            LocationUpdatePolicy.resolveServiceConfig(
                isInHighAccuracyBurst = false,
                interactive = false,
                passiveTracking = false,
                watchOnly = false,
                hasFinePermission = true,
                userIntervalMs = 4_000L,
                ambientUserIntervalMs = 60_000L,
                minUserIntervalMs = 1_000L,
                maxUserIntervalMs = 120_000L,
                minAmbientIntervalMs = 1_000L,
                highAccuracyBurstIntervalMs = 1_000L,
                foregroundMinDistanceM = 1f,
                backgroundMinDistanceM = 5f,
            )

        assertNull(config)
    }

    @Test
    fun burstOverridesPriorityAndInterval() {
        val config =
            LocationUpdatePolicy.resolveServiceConfig(
                isInHighAccuracyBurst = true,
                interactive = false,
                passiveTracking = false,
                watchOnly = false,
                hasFinePermission = true,
                userIntervalMs = 4_000L,
                ambientUserIntervalMs = 60_000L,
                minUserIntervalMs = 1_000L,
                maxUserIntervalMs = 120_000L,
                minAmbientIntervalMs = 1_000L,
                highAccuracyBurstIntervalMs = 1_000L,
                foregroundMinDistanceM = 1f,
                backgroundMinDistanceM = 5f,
            )

        assertNotNull(config)
        assertEquals(Priority.PRIORITY_HIGH_ACCURACY, config?.priority)
        assertEquals(1_000L, config?.intervalMs)
        assertEquals(1f, config?.minDistanceMeters)
        assertEquals(LocationRuntimeMode.BURST, config?.mode)
        assertEquals(LocationSourceMode.AUTO_FUSED, config?.sourceMode)
    }

    @Test
    fun interactiveUsesUserIntervalAndHighAccuracy() {
        val config =
            LocationUpdatePolicy.resolveServiceConfig(
                isInHighAccuracyBurst = false,
                interactive = true,
                passiveTracking = false,
                watchOnly = false,
                hasFinePermission = true,
                userIntervalMs = 4_000L,
                ambientUserIntervalMs = 60_000L,
                minUserIntervalMs = 1_000L,
                maxUserIntervalMs = 120_000L,
                minAmbientIntervalMs = 1_000L,
                highAccuracyBurstIntervalMs = 1_000L,
                foregroundMinDistanceM = 1f,
                backgroundMinDistanceM = 5f,
            )

        assertNotNull(config)
        assertEquals(Priority.PRIORITY_HIGH_ACCURACY, config?.priority)
        assertEquals(4_000L, config?.intervalMs)
        assertEquals(1f, config?.minDistanceMeters)
        assertEquals(LocationRuntimeMode.INTERACTIVE, config?.mode)
        assertEquals(LocationSourceMode.AUTO_FUSED, config?.sourceMode)
    }

    @Test
    fun interactiveWithoutFinePermissionFallsBackToBalanced() {
        val config =
            LocationUpdatePolicy.resolveServiceConfig(
                isInHighAccuracyBurst = false,
                interactive = true,
                passiveTracking = false,
                watchOnly = false,
                hasFinePermission = false,
                userIntervalMs = 4_000L,
                ambientUserIntervalMs = 60_000L,
                minUserIntervalMs = 1_000L,
                maxUserIntervalMs = 120_000L,
                minAmbientIntervalMs = 1_000L,
                highAccuracyBurstIntervalMs = 1_000L,
                foregroundMinDistanceM = 1f,
                backgroundMinDistanceM = 5f,
            )

        assertNotNull(config)
        assertEquals(Priority.PRIORITY_BALANCED_POWER_ACCURACY, config?.priority)
        assertEquals(4_000L, config?.intervalMs)
        assertEquals(1f, config?.minDistanceMeters)
        assertEquals(LocationRuntimeMode.INTERACTIVE, config?.mode)
        assertEquals(LocationSourceMode.AUTO_FUSED, config?.sourceMode)
    }

    @Test
    fun passiveUsesBackgroundDistanceAndAmbientInterval() {
        val config =
            LocationUpdatePolicy.resolveServiceConfig(
                isInHighAccuracyBurst = false,
                interactive = false,
                passiveTracking = true,
                watchOnly = false,
                hasFinePermission = false,
                userIntervalMs = 4_000L,
                ambientUserIntervalMs = 15_000L,
                minUserIntervalMs = 1_000L,
                maxUserIntervalMs = 120_000L,
                minAmbientIntervalMs = 1_000L,
                highAccuracyBurstIntervalMs = 1_000L,
                foregroundMinDistanceM = 1f,
                backgroundMinDistanceM = 5f,
            )

        assertNotNull(config)
        assertEquals(Priority.PRIORITY_BALANCED_POWER_ACCURACY, config?.priority)
        assertEquals(15_000L, config?.intervalMs)
        assertEquals(5f, config?.minDistanceMeters)
        assertEquals(LocationRuntimeMode.PASSIVE, config?.mode)
        assertEquals(LocationSourceMode.AUTO_FUSED, config?.sourceMode)
    }

    @Test
    fun passiveUsesAmbientIntervalWhenAlreadyAboveFloor() {
        val config =
            LocationUpdatePolicy.resolveServiceConfig(
                isInHighAccuracyBurst = false,
                interactive = false,
                passiveTracking = true,
                watchOnly = false,
                hasFinePermission = true,
                userIntervalMs = 40_000L,
                ambientUserIntervalMs = 90_000L,
                minUserIntervalMs = 1_000L,
                maxUserIntervalMs = 120_000L,
                minAmbientIntervalMs = 1_000L,
                highAccuracyBurstIntervalMs = 1_000L,
                foregroundMinDistanceM = 1f,
                backgroundMinDistanceM = 5f,
            )

        assertNotNull(config)
        assertEquals(Priority.PRIORITY_BALANCED_POWER_ACCURACY, config?.priority)
        assertEquals(90_000L, config?.intervalMs)
        assertEquals(5f, config?.minDistanceMeters)
        assertEquals(LocationRuntimeMode.PASSIVE, config?.mode)
        assertEquals(LocationSourceMode.AUTO_FUSED, config?.sourceMode)
    }

    @Test
    fun watchOnlyUsesWatchGpsSourceMode() {
        val config =
            LocationUpdatePolicy.resolveServiceConfig(
                isInHighAccuracyBurst = false,
                interactive = true,
                passiveTracking = false,
                watchOnly = true,
                hasFinePermission = true,
                userIntervalMs = 4_000L,
                ambientUserIntervalMs = 60_000L,
                minUserIntervalMs = 1_000L,
                maxUserIntervalMs = 120_000L,
                minAmbientIntervalMs = 1_000L,
                highAccuracyBurstIntervalMs = 1_000L,
                foregroundMinDistanceM = 1f,
                backgroundMinDistanceM = 5f,
            )

        assertNotNull(config)
        assertEquals(LocationSourceMode.WATCH_GPS, config?.sourceMode)
    }
}
