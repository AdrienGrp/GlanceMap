package com.glancemap.glancemapwearos.presentation.features.navigate.effects

import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.domain.sensors.CompassProviderType
import com.glancemap.glancemapwearos.domain.sensors.CompassViewModel
import com.glancemap.glancemapwearos.presentation.features.navigate.NavMode
import com.glancemap.glancemapwearos.presentation.features.settings.CompassRecalibrationDialog
import kotlinx.coroutines.delay

@Composable
internal fun NavigateCalibrationEffects(
    compassViewModel: CompassViewModel,
    compassProviderType: CompassProviderType,
    compassAccuracy: Int,
    magneticInterference: Boolean,
    navMode: NavMode,
    isAmbient: Boolean,
    promptForCalibration: Boolean,
    showCalibrationDialog: Boolean,
    onShowCalibrationDialog: () -> Unit,
    onHideCalibrationDialog: () -> Unit,
    onApplyRecalibration: () -> Unit,
    onRecalibrationSucceeded: () -> Unit,
) {
    var lastCalibrationPromptAtElapsedMs by rememberSaveable { mutableLongStateOf(0L) }
    var lastCalibrationSuccessAtElapsedMs by rememberSaveable { mutableLongStateOf(0L) }
    var promptEligibleSinceElapsedMs by rememberSaveable { mutableLongStateOf(0L) }
    var lowQualitySinceElapsedMs by rememberSaveable { mutableLongStateOf(0L) }
    var interferenceSinceElapsedMs by rememberSaveable { mutableLongStateOf(0L) }

    val calibrationCooldownMs = 5 * 60_000L
    val badQualityPersistMs = 12_000L
    val magneticInterferencePersistMs = 12_000L
    val startupGraceMs = 8_000L
    val postSuccessSuppressMs = 15 * 60_000L

    LaunchedEffect(compassProviderType) {
        promptEligibleSinceElapsedMs = 0L
        lowQualitySinceElapsedMs = 0L
        interferenceSinceElapsedMs = 0L
    }

    LaunchedEffect(
        compassProviderType,
        compassAccuracy,
        magneticInterference,
        navMode,
        isAmbient,
        promptForCalibration,
    ) {
        val now = android.os.SystemClock.elapsedRealtime()
        val promptEligible =
            promptForCalibration &&
                supportsCompassCalibrationPrompts(compassProviderType) &&
                !isAmbient &&
                navMode == NavMode.COMPASS_FOLLOW
        val requireMagneticInterference =
            requiresMagneticInterferenceForCalibrationPrompt(compassProviderType)
        if (!promptEligible) {
            if (promptEligibleSinceElapsedMs != 0L || lowQualitySinceElapsedMs != 0L || interferenceSinceElapsedMs != 0L) {
                logCalibration(
                    "auto prompt ineligible (promptSetting=$promptForCalibration " +
                        "ambient=$isAmbient mode=$navMode)",
                )
            }
            promptEligibleSinceElapsedMs = 0L
            lowQualitySinceElapsedMs = 0L
            interferenceSinceElapsedMs = 0L
            return@LaunchedEffect
        }

        if (promptEligibleSinceElapsedMs == 0L) {
            promptEligibleSinceElapsedMs = now
        }

        val inStartupGrace = now - promptEligibleSinceElapsedMs < startupGraceMs
        val inPostSuccessSuppress =
            lastCalibrationSuccessAtElapsedMs != 0L &&
                (now - lastCalibrationSuccessAtElapsedMs) < postSuccessSuppressMs
        if (inStartupGrace || inPostSuccessSuppress) {
            if (lowQualitySinceElapsedMs != 0L || interferenceSinceElapsedMs != 0L) {
                logCalibration(
                    "auto prompt timer reset " +
                        "startupGrace=$inStartupGrace postSuccessSuppress=$inPostSuccessSuppress",
                )
            }
            lowQualitySinceElapsedMs = 0L
            interferenceSinceElapsedMs = 0L
            return@LaunchedEffect
        }

        if (isBadCompassAccuracy(compassAccuracy)) {
            if (lowQualitySinceElapsedMs == 0L) {
                lowQualitySinceElapsedMs = now
                logCalibration("bad compass quality detected; auto prompt timer started accuracy=$compassAccuracy")
            }
        } else {
            if (lowQualitySinceElapsedMs != 0L) {
                logCalibration("compass quality recovered before prompt accuracy=$compassAccuracy")
            }
            lowQualitySinceElapsedMs = 0L
        }

        if (requireMagneticInterference) {
            if (magneticInterference) {
                if (interferenceSinceElapsedMs == 0L) {
                    interferenceSinceElapsedMs = now
                    logCalibration("magnetic interference detected; auto prompt timer started")
                }
            } else {
                if (interferenceSinceElapsedMs != 0L) {
                    logCalibration("magnetic interference cleared before prompt")
                }
                interferenceSinceElapsedMs = 0L
            }
        } else {
            if (interferenceSinceElapsedMs != 0L) {
                logCalibration(
                    "provider=$compassProviderType does not expose magnetic interference; " +
                        "auto prompt will use low quality alone",
                )
            }
            interferenceSinceElapsedMs = 0L
        }
    }

    // Calibration prompts are only supported for the custom sensor pipeline.
    LaunchedEffect(
        compassProviderType,
        lowQualitySinceElapsedMs,
        interferenceSinceElapsedMs,
        lastCalibrationPromptAtElapsedMs,
        lastCalibrationSuccessAtElapsedMs,
        promptEligibleSinceElapsedMs,
        promptForCalibration,
        navMode,
        isAmbient,
        showCalibrationDialog,
        compassAccuracy,
        magneticInterference,
    ) {
        val promptEligible =
            promptForCalibration &&
                supportsCompassCalibrationPrompts(compassProviderType) &&
                !isAmbient &&
                navMode == NavMode.COMPASS_FOLLOW
        val requireMagneticInterference =
            requiresMagneticInterferenceForCalibrationPrompt(compassProviderType)
        if (!promptEligible) return@LaunchedEffect
        if (showCalibrationDialog) return@LaunchedEffect
        if (lowQualitySinceElapsedMs == 0L) return@LaunchedEffect
        if (requireMagneticInterference && interferenceSinceElapsedMs == 0L) return@LaunchedEffect

        val now = android.os.SystemClock.elapsedRealtime()
        if (promptEligibleSinceElapsedMs == 0L || now - promptEligibleSinceElapsedMs < startupGraceMs) {
            return@LaunchedEffect
        }
        if (lastCalibrationSuccessAtElapsedMs != 0L &&
            now - lastCalibrationSuccessAtElapsedMs < postSuccessSuppressMs
        ) {
            return@LaunchedEffect
        }

        val lowQualityWaitMs = badQualityPersistMs - (now - lowQualitySinceElapsedMs)
        val waitMs =
            if (requireMagneticInterference) {
                val interferenceWaitMs = magneticInterferencePersistMs - (now - interferenceSinceElapsedMs)
                maxOf(lowQualityWaitMs, interferenceWaitMs)
            } else {
                lowQualityWaitMs
            }
        if (waitMs > 0L) {
            delay(waitMs)
        }

        if (showCalibrationDialog) return@LaunchedEffect
        if (!isBadCompassAccuracy(compassAccuracy)) return@LaunchedEffect
        if (requireMagneticInterference && !magneticInterference) return@LaunchedEffect

        var nowAfterWait = android.os.SystemClock.elapsedRealtime()
        val remainingCooldownMs =
            calibrationCooldownMs - (nowAfterWait - lastCalibrationPromptAtElapsedMs)
        if (remainingCooldownMs > 0L) {
            logCalibration("auto prompt cooldown active; waiting ${remainingCooldownMs}ms")
            delay(remainingCooldownMs)
            val promptStillEligible =
                promptForCalibration && !isAmbient && navMode == NavMode.COMPASS_FOLLOW
            if (!promptStillEligible) return@LaunchedEffect
            if (showCalibrationDialog) return@LaunchedEffect
            if (!isBadCompassAccuracy(compassAccuracy)) return@LaunchedEffect
            if (requireMagneticInterference && !magneticInterference) return@LaunchedEffect
            nowAfterWait = android.os.SystemClock.elapsedRealtime()
            if (lastCalibrationSuccessAtElapsedMs != 0L &&
                nowAfterWait - lastCalibrationSuccessAtElapsedMs < postSuccessSuppressMs
            ) {
                return@LaunchedEffect
            }
        }

        logCalibration("showing auto calibration prompt")
        onShowCalibrationDialog()
        lastCalibrationPromptAtElapsedMs = nowAfterWait
    }

    if (showCalibrationDialog) {
        CompassRecalibrationDialog(
            compassViewModel = compassViewModel,
            onApplyRecalibration = onApplyRecalibration,
        ) { success ->
            onHideCalibrationDialog()
            logCalibration("calibration dialog finished success=$success")
            if (success) {
                onRecalibrationSucceeded()
                val now = android.os.SystemClock.elapsedRealtime()
                lastCalibrationSuccessAtElapsedMs = now
                lowQualitySinceElapsedMs = 0L
                interferenceSinceElapsedMs = 0L
            } else {
                lowQualitySinceElapsedMs =
                    if (isBadCompassAccuracy(compassAccuracy)) {
                        android.os.SystemClock.elapsedRealtime()
                    } else {
                        0L
                    }
                interferenceSinceElapsedMs =
                    if (magneticInterference) {
                        android.os.SystemClock.elapsedRealtime()
                    } else {
                        0L
                    }
            }
        }
    }
}

private fun requiresMagneticInterferenceForCalibrationPrompt(
    providerType: CompassProviderType,
): Boolean = providerType == CompassProviderType.SENSOR_MANAGER

internal fun supportsCompassCalibrationPrompts(
    providerType: CompassProviderType,
): Boolean = providerType == CompassProviderType.SENSOR_MANAGER

private fun isBadCompassAccuracy(accuracy: Int): Boolean =
    accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
        accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW

private fun logCalibration(message: String) {
    if (!DebugTelemetry.isEnabled()) return
    DebugTelemetry.log(CALIBRATION_TELEMETRY_TAG, message)
}

private const val CALIBRATION_TELEMETRY_TAG = "CalibrationTelemetry"
