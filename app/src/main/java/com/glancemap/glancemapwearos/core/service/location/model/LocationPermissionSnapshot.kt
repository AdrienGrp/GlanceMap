package com.glancemap.glancemapwearos.core.service.location.model

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

internal data class LocationPermissionSnapshot(
    val hasFinePermission: Boolean,
    val hasCoarsePermission: Boolean,
) {
    val hasAnyPermission: Boolean
        get() = hasFinePermission || hasCoarsePermission
}

internal object LocationPermissionChecker {
    fun read(context: Context): LocationPermissionSnapshot {
        val hasFinePermission =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        val hasCoarsePermission =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        return LocationPermissionSnapshot(
            hasFinePermission = hasFinePermission,
            hasCoarsePermission = hasCoarsePermission,
        )
    }
}
