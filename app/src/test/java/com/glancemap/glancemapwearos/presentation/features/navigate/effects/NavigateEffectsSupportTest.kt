package com.glancemap.glancemapwearos.presentation.features.navigate

import android.hardware.SensorManager
import com.glancemap.glancemapwearos.domain.sensors.CompassProviderType
import com.glancemap.glancemapwearos.domain.sensors.HeadingSource
import com.glancemap.glancemapwearos.domain.sensors.initialCompassRenderState
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
    fun compassFollowMapDrivesWhenGoogleFusedUsesBootstrapSensorHeading() {
        val state =
            initialCompassRenderState(providerType = CompassProviderType.GOOGLE_FUSED).copy(
                headingSource = HeadingSource.ROTATION_VECTOR,
                accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
                headingSampleElapsedRealtimeMs = null,
                headingSampleStale = true,
            )

        assertTrue(shouldDriveCompassFollowMap(state))
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
}
