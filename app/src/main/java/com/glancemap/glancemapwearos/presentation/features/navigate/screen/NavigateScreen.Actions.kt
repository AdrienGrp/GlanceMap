package com.glancemap.glancemapwearos.presentation.features.navigate

import android.content.Context
import android.os.SystemClock
import android.os.VibrationEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.glancemap.glancemapwearos.presentation.features.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal data class NavigateScreenActions(
    val triggerHaptic: () -> Unit,
    val continueKeepAppOpenEnableFlow: () -> Unit,
    val toggleKeepAppOpen: () -> Unit,
    val toggleShortcutTray: () -> Unit,
)

@Composable
internal fun rememberNavigateScreenActions(
    context: Context,
    settingsViewModel: SettingsViewModel,
    locationPermissionState: LocationPermissionState,
    notificationPermissionState: NotificationPermissionState,
    keepAppOpen: Boolean,
    keepAppOpenTipShown: Boolean,
    offlineMode: Boolean,
    setPendingKeepAppOpen: (Boolean) -> Unit,
    setShowKeepAppOpenInfoDialog: (Boolean) -> Unit,
    setShortcutTrayExpanded: (Boolean) -> Unit,
    isShortcutTrayExpanded: Boolean,
): NavigateScreenActions {
    val vibrator = remember { vibratorFrom(context) }
    val scope = rememberCoroutineScope()
    var lastVibrationTime by remember { mutableLongStateOf(0L) }
    val hapticIntervalMs = 150L

    fun triggerHaptic() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastVibrationTime < hapticIntervalMs) return
        lastVibrationTime = now
        scope.launch(Dispatchers.Default) {
            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    fun continueKeepAppOpenEnableFlow() {
        setPendingKeepAppOpen(true)

        if (!offlineMode && !locationPermissionState.hasLocationPermission) {
            locationPermissionState.launchPermissions()
            return
        }

        if (
            notificationPermissionState.isPermissionRequired &&
            !notificationPermissionState.hasNotificationPermission
        ) {
            notificationPermissionState.launchPermissionRequest()
            return
        }

        settingsViewModel.setKeepAppOpen(true)
        setPendingKeepAppOpen(false)
    }

    fun toggleKeepAppOpen() {
        triggerHaptic()

        if (keepAppOpen) {
            settingsViewModel.setKeepAppOpen(false)
            setPendingKeepAppOpen(false)
            setShowKeepAppOpenInfoDialog(false)
            return
        }

        if (!keepAppOpenTipShown) {
            setShowKeepAppOpenInfoDialog(true)
            setPendingKeepAppOpen(true)
            settingsViewModel.setKeepAppOpenTipShown(true)
            return
        }

        continueKeepAppOpenEnableFlow()
    }

    fun toggleShortcutTray() {
        triggerHaptic()
        setShortcutTrayExpanded(!isShortcutTrayExpanded)
    }

    return NavigateScreenActions(
        triggerHaptic = ::triggerHaptic,
        continueKeepAppOpenEnableFlow = ::continueKeepAppOpenEnableFlow,
        toggleKeepAppOpen = ::toggleKeepAppOpen,
        toggleShortcutTray = ::toggleShortcutTray,
    )
}
