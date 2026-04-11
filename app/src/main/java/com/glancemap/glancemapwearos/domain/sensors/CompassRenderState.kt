package com.glancemap.glancemapwearos.domain.sensors

import android.hardware.SensorManager

data class CompassRenderState(
    val providerType: CompassProviderType,
    val headingDeg: Float,
    val accuracy: Int,
    val headingErrorDeg: Float? = null,
    val conservativeHeadingErrorDeg: Float? = null,
    val headingSampleElapsedRealtimeMs: Long? = null,
    val headingSampleStale: Boolean = false,
    val headingSource: HeadingSource,
    val headingSourceStatus: HeadingSourceStatus,
    val northReferenceStatus: NorthReferenceStatus,
    val magneticInterference: Boolean
)

internal fun initialCompassRenderState(
    providerType: CompassProviderType,
    headingSensorAvailable: Boolean = false,
    rotationVectorAvailable: Boolean = false,
    magAccelFallbackAvailable: Boolean = false
): CompassRenderState {
    return CompassRenderState(
        providerType = providerType,
        headingDeg = 0f,
        accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE,
        headingErrorDeg = null,
        conservativeHeadingErrorDeg = null,
        headingSampleElapsedRealtimeMs = null,
        headingSampleStale = false,
        headingSource = HeadingSource.NONE,
        headingSourceStatus = HeadingSourceStatus(
            requestedMode = CompassHeadingSourceMode.AUTO,
            activeSource = HeadingSource.NONE,
            headingSensorAvailable = headingSensorAvailable,
            rotationVectorAvailable = rotationVectorAvailable,
            magAccelFallbackAvailable = magAccelFallbackAvailable
        ),
        northReferenceStatus = NorthReferenceStatus(
            requestedMode = NorthReferenceMode.TRUE,
            effectiveMode = NorthReferenceMode.MAGNETIC,
            declinationAvailable = false,
            waitingForDeclination = true,
            pipeline = HeadingPipeline.NONE
        ),
        magneticInterference = false
    )
}
