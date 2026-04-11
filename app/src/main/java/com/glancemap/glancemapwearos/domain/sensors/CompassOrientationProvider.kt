package com.glancemap.glancemapwearos.domain.sensors

import android.location.Location
import kotlinx.coroutines.flow.StateFlow

enum class CompassProviderType {
    SENSOR_MANAGER,
    GOOGLE_FUSED
}

internal interface CompassOrientationProvider {
    val providerType: CompassProviderType
    val renderState: StateFlow<CompassRenderState>

    fun start(lowPower: Boolean = false)
    fun stop()
    fun recalibrate()
    fun setNorthReferenceMode(mode: NorthReferenceMode, forceRefresh: Boolean = false)
    fun setHeadingSourceMode(mode: CompassHeadingSourceMode, forceRefresh: Boolean = false)
    fun primeDeclinationFromApproximateLocation(
        latitude: Double,
        longitude: Double,
        altitudeM: Float = 0f
    )
    fun updateDeclinationFromLocation(location: Location)
    fun setLowPowerMode(enabled: Boolean)
}
