package com.glancemap.glancemapcompanionapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
internal fun rememberMapPickerLocationAllowed(requestPermission: Boolean): Boolean {
    val context = LocalContext.current
    var allowed by remember {
        mutableStateOf(context.hasApproximateLocationPermission())
    }
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            allowed = granted
        }

    LaunchedEffect(requestPermission, allowed) {
        if (requestPermission && !allowed) {
            launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    return allowed
}

private fun Context.hasApproximateLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
