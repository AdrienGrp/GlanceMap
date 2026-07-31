package com.glancemap.glancemapwearos.presentation.features.navigate

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal data class NavigateScreenLifecycleState(
    val isScreenResumed: Boolean,
    val lastScreenResumeElapsedMs: Long,
    val menuClickGuardUntilElapsedMs: Long,
)

@Composable
internal fun rememberNavigateScreenLifecycleState(
    isDeviceInteractive: Boolean,
): NavigateScreenLifecycleState {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isScreenResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var lastScreenResumeElapsedMs by remember(lifecycleOwner) {
        mutableLongStateOf(SystemClock.elapsedRealtime())
    }
    var menuClickGuardUntilElapsedMs by remember(lifecycleOwner) {
        mutableLongStateOf(lastScreenResumeElapsedMs + NAVIGATE_MENU_CLICK_RESUME_GUARD_MS)
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        val nowElapsedMs = SystemClock.elapsedRealtime()
                        isScreenResumed = true
                        lastScreenResumeElapsedMs = nowElapsedMs
                        menuClickGuardUntilElapsedMs = nowElapsedMs + NAVIGATE_MENU_CLICK_RESUME_GUARD_MS
                    }
                    Lifecycle.Event.ON_PAUSE -> isScreenResumed = false
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isScreenResumed, isDeviceInteractive) {
        if (isScreenResumed && isDeviceInteractive) {
            val nowElapsedMs = SystemClock.elapsedRealtime()
            lastScreenResumeElapsedMs = nowElapsedMs
            menuClickGuardUntilElapsedMs = nowElapsedMs + NAVIGATE_MENU_CLICK_RESUME_GUARD_MS
        }
    }

    return NavigateScreenLifecycleState(
        isScreenResumed = isScreenResumed,
        lastScreenResumeElapsedMs = lastScreenResumeElapsedMs,
        menuClickGuardUntilElapsedMs = menuClickGuardUntilElapsedMs,
    )
}

private const val NAVIGATE_MENU_CLICK_RESUME_GUARD_MS = 1_500L
