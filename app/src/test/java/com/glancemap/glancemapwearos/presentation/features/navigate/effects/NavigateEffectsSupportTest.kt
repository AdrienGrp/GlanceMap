package com.glancemap.glancemapwearos.presentation.features.navigate

import android.hardware.SensorManager
import com.glancemap.glancemapwearos.domain.sensors.CompassProviderType
import com.glancemap.glancemapwearos.domain.sensors.HeadingSource
import com.glancemap.glancemapwearos.domain.sensors.initialCompassRenderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigateEffectsSupportTest {
    @Test
    fun compassFollowMapStaysFrozenWithoutActiveHeadingSource() {
        assertFalse(
            shouldDriveCompassFollowMap(
                initialCompassRenderState(providerType = CompassProviderType.SENSOR_MANAGER),
            ),
        )
    }

    @Test
    fun compassFollowMapStaysFrozenWhenAccuracyIsUnreliable() {
        val state =
            initialCompassRenderState(providerType = CompassProviderType.SENSOR_MANAGER).copy(
                headingSource = HeadingSource.HEADING_SENSOR,
                accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE,
            )

        assertFalse(shouldDriveCompassFollowMap(state))
    }

    @Test
    fun compassFollowMapWaitsForFreshGoogleFusedSample() {
        val state =
            initialCompassRenderState(providerType = CompassProviderType.GOOGLE_FUSED).copy(
                headingSource = HeadingSource.FUSED_ORIENTATION,
                accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                headingSampleElapsedRealtimeMs = null,
                headingSampleStale = false,
            )

        assertFalse(shouldDriveCompassFollowMap(state))
    }

    @Test
    fun compassFollowMapWaitsWhenGoogleFusedSampleIsStale() {
        val state =
            initialCompassRenderState(providerType = CompassProviderType.GOOGLE_FUSED).copy(
                headingSource = HeadingSource.FUSED_ORIENTATION,
                accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                headingSampleElapsedRealtimeMs = 1_000L,
                headingSampleStale = true,
            )

        assertFalse(shouldDriveCompassFollowMap(state))
    }

    @Test
    fun compassFollowMapDrivesWhenSensorManagerHeadingIsReady() {
        val state =
            initialCompassRenderState(providerType = CompassProviderType.SENSOR_MANAGER).copy(
                headingSource = HeadingSource.ROTATION_VECTOR,
                accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
            )

        assertTrue(shouldDriveCompassFollowMap(state))
    }

    @Test
    fun compassFollowMapDrivesWhenGoogleFusedSampleIsFresh() {
        val state =
            initialCompassRenderState(providerType = CompassProviderType.GOOGLE_FUSED).copy(
                headingSource = HeadingSource.FUSED_ORIENTATION,
                accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                headingSampleElapsedRealtimeMs = 1_000L,
                headingSampleStale = false,
            )

        assertTrue(shouldDriveCompassFollowMap(state))
    }

    @Test
    fun compassFollowMapWaitsWhileGoogleFusedUsesBootstrapSensorHeading() {
        val state =
            initialCompassRenderState(providerType = CompassProviderType.GOOGLE_FUSED).copy(
                headingSource = HeadingSource.ROTATION_VECTOR,
                accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                headingSampleElapsedRealtimeMs = null,
                headingSampleStale = true,
            )

        assertFalse(shouldDriveCompassFollowMap(state))
    }

    @Test
    fun northUpMarkerWaitsWhileGoogleFusedUsesBootstrapSensorHeading() {
        val state =
            initialCompassRenderState(providerType = CompassProviderType.GOOGLE_FUSED).copy(
                headingSource = HeadingSource.ROTATION_VECTOR,
                accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                headingSampleElapsedRealtimeMs = null,
                headingSampleStale = true,
            )

        assertFalse(shouldDriveMarkerHeading(state))
        assertFalse(shouldDriveHeadingForNavMode(NavMode.NORTH_UP_FOLLOW, state))
        assertFalse(shouldDriveHeadingForNavMode(NavMode.COMPASS_FOLLOW, state))
    }

    @Test
    fun northUpMarkerDrivesWhenGoogleFusedSampleIsFresh() {
        val state =
            initialCompassRenderState(providerType = CompassProviderType.GOOGLE_FUSED).copy(
                headingSource = HeadingSource.FUSED_ORIENTATION,
                accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                headingSampleElapsedRealtimeMs = 1_000L,
                headingSampleStale = false,
            )

        assertTrue(shouldDriveMarkerHeading(state))
        assertTrue(shouldDriveHeadingForNavMode(NavMode.NORTH_UP_FOLLOW, state))
    }

    @Test
    fun northUpMarkerDrivesWhenSensorManagerHeadingIsReady() {
        val state =
            initialCompassRenderState(providerType = CompassProviderType.SENSOR_MANAGER).copy(
                headingSource = HeadingSource.ROTATION_VECTOR,
                accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
            )

        assertTrue(shouldDriveMarkerHeading(state))
        assertTrue(shouldDriveHeadingForNavMode(NavMode.NORTH_UP_FOLLOW, state))
    }

    @Test
    fun compassFollowMapCanSeedFromRecentGoogleFusedCachedHeading() {
        val state =
            initialCompassRenderState(providerType = CompassProviderType.GOOGLE_FUSED).copy(
                headingDeg = 182f,
                accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE,
                headingSampleElapsedRealtimeMs = 10_000L,
                headingSampleStale = true,
                headingSource = HeadingSource.NONE,
            )

        assertTrue(
            shouldSeedCompassFollowMapWithCachedHeading(
                renderState = state,
                nowElapsedMs = 25_000L,
            ),
        )
    }

    @Test
    fun compassFollowMapDoesNotSeedFromOldGoogleFusedCachedHeading() {
        val state =
            initialCompassRenderState(providerType = CompassProviderType.GOOGLE_FUSED).copy(
                headingDeg = 182f,
                accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE,
                headingSampleElapsedRealtimeMs = 10_000L,
                headingSampleStale = true,
                headingSource = HeadingSource.NONE,
            )

        assertFalse(
            shouldSeedCompassFollowMapWithCachedHeading(
                renderState = state,
                nowElapsedMs = 45_001L,
            ),
        )
    }

    @Test
    fun northUpMarkerCanSeedFromRecentGoogleFusedCachedHeading() {
        val state =
            initialCompassRenderState(providerType = CompassProviderType.GOOGLE_FUSED).copy(
                headingDeg = 182f,
                accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE,
                headingSampleElapsedRealtimeMs = 10_000L,
                headingSampleStale = true,
                headingSource = HeadingSource.NONE,
            )

        assertTrue(
            shouldSeedNorthUpMarkerWithCachedHeading(
                renderState = state,
                nowElapsedMs = 25_000L,
            ),
        )
    }

    @Test
    fun initialRenderedHeadingUsesRecentGoogleFusedCachedHeading() {
        val state =
            initialCompassRenderState(providerType = CompassProviderType.GOOGLE_FUSED).copy(
                headingDeg = 182f,
                accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE,
                headingSampleElapsedRealtimeMs = 10_000L,
                headingSampleStale = true,
                headingSource = HeadingSource.NONE,
            )

        assertTrue(
            resolveNavigateInitialRenderedHeadingDeg(
                renderState = state,
                nowElapsedMs = 25_000L,
            ) > 180f,
        )
    }

    @Test
    fun compassFollowLimitsMapsforgeRotationToThirtyHz() {
        assertTrue(
            shouldThrottleMapsforgeRotation(
                navMode = NavMode.COMPASS_FOLLOW,
                nowElapsedMs = 1_032L,
                lastAppliedAtElapsedMs = 1_000L,
            ),
        )
        assertFalse(
            shouldThrottleMapsforgeRotation(
                navMode = NavMode.COMPASS_FOLLOW,
                nowElapsedMs = 1_033L,
                lastAppliedAtElapsedMs = 1_000L,
            ),
        )
    }

    @Test
    fun activeCompassTurnAllowsDisplayRateMapsforgeRotation() {
        assertTrue(
            shouldThrottleMapsforgeRotation(
                navMode = NavMode.COMPASS_FOLLOW,
                nowElapsedMs = 1_015L,
                lastAppliedAtElapsedMs = 1_000L,
                highFrequencyRotation = true,
            ),
        )
        assertFalse(
            shouldThrottleMapsforgeRotation(
                navMode = NavMode.COMPASS_FOLLOW,
                nowElapsedMs = 1_016L,
                lastAppliedAtElapsedMs = 1_000L,
                highFrequencyRotation = true,
            ),
        )
    }

    @Test
    fun northUpAndFirstRotationAreNotThrottled() {
        assertFalse(
            shouldThrottleMapsforgeRotation(
                navMode = NavMode.NORTH_UP_FOLLOW,
                nowElapsedMs = 1_001L,
                lastAppliedAtElapsedMs = 1_000L,
            ),
        )
        assertFalse(
            shouldThrottleMapsforgeRotation(
                navMode = NavMode.COMPASS_FOLLOW,
                nowElapsedMs = 1_001L,
                lastAppliedAtElapsedMs = Long.MIN_VALUE,
            ),
        )
    }

    @Test
    fun rapidHeadingChangesEnableFastTurnRendering() {
        assertTrue(
            isFastHeadingTurn(
                previousHeadingDeg = 350f,
                nextHeadingDeg = 10f,
                elapsedMs = 100L,
            ),
        )
        assertFalse(
            isFastHeadingTurn(
                previousHeadingDeg = 350f,
                nextHeadingDeg = 352f,
                elapsedMs = 100L,
            ),
        )
    }

    @Test
    fun slowContinuousRotationEnablesHighFrequencyMapRendering() {
        assertTrue(
            isActiveMapHeadingTurn(
                previousHeadingDeg = 350f,
                nextHeadingDeg = 353f,
                elapsedMs = 100L,
            ),
        )
        assertFalse(
            isActiveMapHeadingTurn(
                previousHeadingDeg = 350f,
                nextHeadingDeg = 351f,
                elapsedMs = 100L,
            ),
        )
    }

    @Test
    fun fastTurnAnimationClosesHeadingErrorMoreAggressively() {
        val normalAlpha = resolveHeadingAnimationAlpha(diffDeg = 40f, fastTurn = false)
        val fastAlpha = resolveHeadingAnimationAlpha(diffDeg = 40f, fastTurn = true)

        assertTrue(fastAlpha > normalAlpha)
        assertTrue(fastAlpha >= 0.85f)
    }

    @Test
    fun everyCompassVisualPathIsLimitedAcrossThrottledFramesAndNorth() {
        val firstAppliedAngle =
            resolveCompassVisualTargetAngle(
                currentAngleDeg = 0f,
                targetAngleDeg = 90f,
            )
        val secondAppliedAngle =
            resolveCompassVisualTargetAngle(
                currentAngleDeg = firstAppliedAngle,
                targetAngleDeg = 90f,
            )

        assertEquals(12f, firstAppliedAngle, 0f)
        assertEquals(24f, secondAppliedAngle, 0f)
        assertEquals(
            362f,
            resolveCompassVisualTargetAngle(
                currentAngleDeg = 350f,
                targetAngleDeg = 10f,
            ),
            0f,
        )
        assertEquals(
            8f,
            resolveCompassVisualTargetAngle(
                currentAngleDeg = 20f,
                targetAngleDeg = 350f,
            ),
            0f,
        )
    }

    @Test
    fun normalHeadingAnimationRejectsSingleFrameThirtyDegreeSweep() {
        assertEquals(
            12f,
            resolveHeadingAnimationDelta(
                diffDeg = 40f,
                fastTurn = true,
            ),
            0f,
        )
    }
}
