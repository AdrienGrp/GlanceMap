package com.glancemap.glancemapwearos.domain.sensors

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow

internal class CompassHeadingProcessor {
    fun launch(
        scope: CoroutineScope,
        rawHeadingFlow: MutableStateFlow<Float?>,
        settleWindowMs: Long,
        getStartAtMs: () -> Long,
        getHeadingRelockUntilElapsedMs: () -> Long,
        consumeResetSmoothingRequested: () -> Boolean,
        getDisplayedHeading: () -> Float,
        publishDisplayedHeading: (Float) -> Unit,
        getPendingBootstrapRawSamplesToIgnore: () -> Int,
        setPendingBootstrapRawSamplesToIgnore: (Int) -> Unit,
        getPendingStartupBogusSamplesToIgnore: () -> Int,
        setPendingStartupBogusSamplesToIgnore: (Int) -> Unit,
        getPendingStartupHeadingPublishesToMask: () -> Int,
        setPendingStartupHeadingPublishesToMask: (Int) -> Unit,
        getStartupStabilizationUntilElapsedMs: () -> Long,
        getStartupHeadingPublishMaskUntilElapsedMs: () -> Long,
        isUsingRotationVector: () -> Boolean,
        isUsingHeadingSensor: () -> Boolean,
        updateInferredHeadingAccuracy: (Int) -> Unit,
        logDiagnostics: (String) -> Unit
    ): Job {
        return launchCompassSmoothingJob(
            scope = scope,
            rawHeadingFlow = rawHeadingFlow,
            settleWindowMs = settleWindowMs,
            getStartAtMs = getStartAtMs,
            getHeadingRelockUntilElapsedMs = getHeadingRelockUntilElapsedMs,
            consumeResetSmoothingRequested = consumeResetSmoothingRequested,
            getDisplayedHeading = getDisplayedHeading,
            publishDisplayedHeading = publishDisplayedHeading,
            getPendingBootstrapRawSamplesToIgnore = getPendingBootstrapRawSamplesToIgnore,
            setPendingBootstrapRawSamplesToIgnore = setPendingBootstrapRawSamplesToIgnore,
            getPendingStartupBogusSamplesToIgnore = getPendingStartupBogusSamplesToIgnore,
            setPendingStartupBogusSamplesToIgnore = setPendingStartupBogusSamplesToIgnore,
            getPendingStartupHeadingPublishesToMask = getPendingStartupHeadingPublishesToMask,
            setPendingStartupHeadingPublishesToMask = setPendingStartupHeadingPublishesToMask,
            getStartupStabilizationUntilElapsedMs = getStartupStabilizationUntilElapsedMs,
            getStartupHeadingPublishMaskUntilElapsedMs = getStartupHeadingPublishMaskUntilElapsedMs,
            isUsingRotationVector = isUsingRotationVector,
            isUsingHeadingSensor = isUsingHeadingSensor,
            updateInferredHeadingAccuracy = updateInferredHeadingAccuracy,
            logDiagnostics = logDiagnostics
        )
    }
}
