
package com.glancemap.glancemapwearos.presentation.features.navigate

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * A composable that remembers the state of location permissions.
 * This exclusively handles foreground location permissions (fine or coarse).
 */
@Composable
fun rememberLocationPermissionState(
    context: Context,
    onPermissionsResult: (Boolean) -> Unit,
): LocationPermissionState {
    var hasLocationPermission by remember {
        mutableStateOf(hasForegroundLocationPermission(context))
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
            onResult = { permissions ->
                val fineGranted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)
                val coarseGranted = permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)
                hasLocationPermission = fineGranted || coarseGranted
                onPermissionsResult(hasLocationPermission)
            },
        )

    return LocationPermissionState(
        hasLocationPermission = hasLocationPermission,
        launchPermissions = {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        },
    )
}

/**
 * State holder for foreground location permission.
 */
data class LocationPermissionState(
    val hasLocationPermission: Boolean,
    val launchPermissions: () -> Unit,
)

private fun hasForegroundLocationPermission(context: Context): Boolean {
    val fineGranted =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    return fineGranted || coarseGranted
}

/**
 * A composable that remembers the state of notification permissions.
 */
@Composable
fun rememberNotificationPermissionState(
    context: Context,
    onPermissionResult: (Boolean) -> Unit = {},
): NotificationPermissionState {
    // Notification permission is only required on API 33+
    val isPermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (isPermissionRequired) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                hasNotificationPermission = isGranted
                onPermissionResult(isGranted)
            },
        )

    return NotificationPermissionState(
        hasNotificationPermission = hasNotificationPermission,
        isPermissionRequired = isPermissionRequired,
        launchPermissionRequest = {
            if (isPermissionRequired) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
    )
}

/**
 * State holder for notification permission.
 */
data class NotificationPermissionState(
    val hasNotificationPermission: Boolean,
    val isPermissionRequired: Boolean,
    val launchPermissionRequest: () -> Unit,
)
