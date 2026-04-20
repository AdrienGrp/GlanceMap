package com.glancemap.glancemapwearos.core.service.location.adapters

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.SettingsClient
import kotlinx.coroutines.tasks.await

internal data class LocationSettingsPreflightResult(
    val satisfied: Boolean,
    val statusCode: Int?,
    val detail: String?,
)

internal class LocationSettingsPreflight(
    private val settingsClient: SettingsClient,
) {
    suspend fun check(request: LocationUpdateRequestParams): LocationSettingsPreflightResult {
        val locationRequest =
            LocationRequest
                .Builder(request.priority, request.intervalMs)
                .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                .setMinUpdateIntervalMillis(request.intervalMs)
                .setMinUpdateDistanceMeters(request.minDistanceMeters)
                .setWaitForAccurateLocation(request.waitForAccurateLocation)
                .setMaxUpdateDelayMillis(request.maxUpdateDelayMs)
                .build()
        val settingsRequest =
            LocationSettingsRequest
                .Builder()
                .addLocationRequest(locationRequest)
                .build()
        return try {
            settingsClient.checkLocationSettings(settingsRequest).await()
            LocationSettingsPreflightResult(
                satisfied = true,
                statusCode = null,
                detail = null,
            )
        } catch (error: ApiException) {
            LocationSettingsPreflightResult(
                satisfied = false,
                statusCode = error.statusCode,
                detail = error.message,
            )
        } catch (error: Exception) {
            LocationSettingsPreflightResult(
                satisfied = false,
                statusCode = null,
                detail = error.javaClass.simpleName,
            )
        }
    }
}
