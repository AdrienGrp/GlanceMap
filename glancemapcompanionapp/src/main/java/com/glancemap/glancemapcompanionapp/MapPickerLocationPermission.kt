package com.glancemap.glancemapcompanionapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    var showDisclosure by remember { mutableStateOf(false) }
    var disclosureHandled by remember { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            allowed = granted
        }

    LaunchedEffect(requestPermission, allowed, disclosureHandled) {
        if (requestPermission && !allowed && !disclosureHandled) {
            showDisclosure = true
        }
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = {
                showDisclosure = false
                disclosureHandled = true
            },
            title = { Text("Location for map selection") },
            text = {
                Text(
                    "GlanceMap uses your location only to centre this map selector on your current " +
                        "position. Your location stays on this device and is not sent to the tracking service.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisclosure = false
                        disclosureHandled = true
                        launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDisclosure = false
                        disclosureHandled = true
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    return allowed
}

private fun Context.hasApproximateLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
