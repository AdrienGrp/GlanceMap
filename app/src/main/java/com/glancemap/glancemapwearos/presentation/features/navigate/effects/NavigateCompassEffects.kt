package com.glancemap.glancemapwearos.presentation.features.navigate.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.core.service.location.model.LocationScreenState
import com.glancemap.glancemapwearos.core.service.location.model.isNonInteractive
import com.glancemap.glancemapwearos.domain.sensors.COMPASS_TELEMETRY_TAG
import com.glancemap.glancemapwearos.domain.sensors.CompassProviderType
import com.glancemap.glancemapwearos.domain.sensors.CompassViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun NavigateCompassEffects(
    compassViewModel: CompassViewModel,
    compassProviderType: CompassProviderType,
    screenState: LocationScreenState,
    isOfflineMode: Boolean,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val latestScreenState = rememberUpdatedState(screenState)
    val latestIsOfflineMode = rememberUpdatedState(isOfflineMode)
    var pendingStopJob by remember { mutableStateOf<Job?>(null) }

    fun stopCompass(
        immediate: Boolean,
        reason: String,
    ) {
        if (immediate) {
            pendingStopJob?.cancel()
            pendingStopJob = null
            val delayMs =
                resolveNavigateCompassImmediateStopDelayMs(
                    compassProviderType = compassProviderType,
                    screenState = latestScreenState.value,
                    isOfflineMode = latestIsOfflineMode.value,
                )
            logNavigateCompassEffect(
                "ui_stop immediate=true reason=$reason screenState=${latestScreenState.value.name} " +
                    "delayMs=$delayMs provider=${compassProviderType.name}",
            )
            compassViewModel.stop(reason = reason, delayMs = delayMs)
            return
        }
        if (pendingStopJob?.isActive == true) return
        logNavigateCompassEffect(
            "ui_stop immediate=false debounceMs=$NAVIGATE_COMPASS_STOP_DEBOUNCE_MS " +
                "reason=$reason screenState=${latestScreenState.value.name}",
        )
        pendingStopJob =
            scope.launch {
                delay(NAVIGATE_COMPASS_STOP_DEBOUNCE_MS)
                pendingStopJob = null
                compassViewModel.stop(reason = "${reason}_debounced")
            }
    }

    fun startCompassForNavigate() {
        pendingStopJob?.cancel()
        pendingStopJob = null
        // Keep one stable high-power compass session while Navigate is active.
        // Rapid follow/panning flips were forcing fused-provider restarts in the field.
        compassViewModel.start(lowPower = false)
    }

    DisposableEffect(lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        if (
                            shouldRunNavigateCompass(
                                isResumed = true,
                                screenState = latestScreenState.value,
                                isOfflineMode = latestIsOfflineMode.value,
                            )
                        ) {
                            startCompassForNavigate()
                        } else {
                            stopCompass(
                                immediate =
                                    shouldStopNavigateCompassImmediately(
                                        screenState = latestScreenState.value,
                                        isOfflineMode = latestIsOfflineMode.value,
                                    ),
                                reason =
                                    resolveNavigateCompassStopReason(
                                        isResumed = true,
                                        screenState = latestScreenState.value,
                                        isOfflineMode = latestIsOfflineMode.value,
                                    ),
                            )
                        }
                    }

                    Lifecycle.Event.ON_PAUSE ->
                        stopCompass(
                            immediate =
                                shouldStopNavigateCompassImmediately(
                                    screenState = latestScreenState.value,
                                    isOfflineMode = latestIsOfflineMode.value,
                                ),
                            reason =
                                resolveNavigateCompassStopReason(
                                    isResumed = false,
                                    screenState = latestScreenState.value,
                                    isOfflineMode = latestIsOfflineMode.value,
                                ),
                        )
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        // Sync immediately with current lifecycle state.
        if (
            shouldRunNavigateCompass(
                isResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
                screenState = latestScreenState.value,
                isOfflineMode = latestIsOfflineMode.value,
            )
        ) {
            startCompassForNavigate()
        } else {
            stopCompass(
                immediate =
                    shouldStopNavigateCompassImmediately(
                        screenState = latestScreenState.value,
                        isOfflineMode = latestIsOfflineMode.value,
                    ),
                reason =
                    resolveNavigateCompassStopReason(
                        isResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
                        screenState = latestScreenState.value,
                        isOfflineMode = latestIsOfflineMode.value,
                    ),
            )
        }

        onDispose {
            lifecycle.removeObserver(observer)
            stopCompass(immediate = true, reason = "effect_dispose")
        }
    }

    // React to screen-state changes while avoiding duplicate lifecycle-driven restarts.
    LaunchedEffect(screenState, isOfflineMode, lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        if (
            shouldRunNavigateCompass(
                isResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
                screenState = screenState,
                isOfflineMode = isOfflineMode,
            )
        ) {
            startCompassForNavigate()
        } else {
            val isResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            stopCompass(
                immediate =
                    shouldStopNavigateCompassImmediately(
                        screenState = screenState,
                        isOfflineMode = isOfflineMode,
                    ),
                reason =
                    resolveNavigateCompassStopReason(
                        isResumed = isResumed,
                        screenState = screenState,
                        isOfflineMode = isOfflineMode,
                    ),
            )
        }
    }
}

internal fun shouldRunNavigateCompass(
    isResumed: Boolean,
    screenState: LocationScreenState,
    isOfflineMode: Boolean,
): Boolean =
    isResumed &&
        screenState == LocationScreenState.INTERACTIVE &&
        !isOfflineMode

internal fun shouldStopNavigateCompassImmediately(
    screenState: LocationScreenState,
    isOfflineMode: Boolean,
): Boolean = screenState.isNonInteractive || isOfflineMode

internal fun resolveNavigateCompassStopReason(
    isResumed: Boolean,
    screenState: LocationScreenState,
    isOfflineMode: Boolean,
): String =
    when {
        isOfflineMode -> "offline_mode"
        screenState == LocationScreenState.SCREEN_OFF -> "screen_off"
        screenState == LocationScreenState.AMBIENT -> "ambient"
        !isResumed -> "lifecycle_pause"
        else -> "resume_guard"
    }

internal fun resolveNavigateCompassImmediateStopDelayMs(
    compassProviderType: CompassProviderType,
    screenState: LocationScreenState,
    isOfflineMode: Boolean,
): Long {
    if (isOfflineMode) return 0L
    if (compassProviderType != CompassProviderType.GOOGLE_FUSED) return 0L
    return when (screenState) {
        LocationScreenState.SCREEN_OFF,
        LocationScreenState.AMBIENT,
        -> GOOGLE_FUSED_TRANSIENT_STOP_GRACE_MS
        LocationScreenState.INTERACTIVE -> 0L
    }
}

private fun logNavigateCompassEffect(message: String) {
    if (!DebugTelemetry.isEnabled()) return
    DebugTelemetry.log(COMPASS_TELEMETRY_TAG, message)
}

private const val NAVIGATE_COMPASS_STOP_DEBOUNCE_MS = 2_500L
private const val GOOGLE_FUSED_TRANSIENT_STOP_GRACE_MS = 10_000L
