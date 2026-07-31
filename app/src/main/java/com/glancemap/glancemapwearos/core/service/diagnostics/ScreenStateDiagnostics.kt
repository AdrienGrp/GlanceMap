package com.glancemap.glancemapwearos.core.service.diagnostics

import android.os.SystemClock

/**
 * Lightweight screen-state accounting for diagnostics captures.
 *
 * This intentionally records only state transitions. It does not poll, hold a wake lock, or
 * write per-transition files, so it is suitable for battery benchmark captures.
 */
@Suppress("TooManyFunctions")
internal object ScreenStateDiagnostics {
    enum class DisplayState {
        INTERACTIVE,
        AMBIENT,
        OFF,
    }

    data class Summary(
        val captureActive: Boolean,
        val captureDurationMs: Long,
        val interactiveDurationMs: Long,
        val ambientDurationMs: Long,
        val offDurationMs: Long,
        val appForegroundDurationMs: Long,
        val displayTransitionCount: Int,
        val appForegroundTransitionCount: Int,
        val currentDisplayState: DisplayState?,
        val currentAppForeground: Boolean?,
        val openIntervalsIncluded: Boolean,
    )

    private val lock = Any()

    private var captureActive = false
    private var captureStartedAtElapsedMs: Long? = null
    private var captureEndedAtElapsedMs: Long? = null
    private var displayState: DisplayState? = null
    private var displayStateStartedAtElapsedMs: Long? = null
    private var appForeground: Boolean? = null
    private var appForegroundStartedAtElapsedMs: Long? = null
    private var interactiveDurationMs = 0L
    private var ambientDurationMs = 0L
    private var offDurationMs = 0L
    private var appForegroundDurationMs = 0L
    private var displayTransitionCount = 0
    private var appForegroundTransitionCount = 0

    fun configure(
        captureActive: Boolean,
        initialDisplayState: DisplayState = DisplayState.INTERACTIVE,
        initialAppForeground: Boolean = true,
    ) = configure(
        captureActive = captureActive,
        initialDisplayState = initialDisplayState,
        initialAppForeground = initialAppForeground,
        nowElapsedMs = SystemClock.elapsedRealtime(),
    )

    internal fun configure(
        captureActive: Boolean,
        initialDisplayState: DisplayState = DisplayState.INTERACTIVE,
        initialAppForeground: Boolean = true,
        nowElapsedMs: Long,
    ) {
        synchronized(lock) {
            if (this.captureActive == captureActive) return
            if (captureActive) {
                startCapture(
                    nowElapsedMs = nowElapsedMs,
                    initialDisplayState = initialDisplayState,
                    initialAppForeground = initialAppForeground,
                )
            } else {
                stopCapture(nowElapsedMs)
            }
        }
    }

    fun updateDisplayState(
        isInteractive: Boolean,
        isAmbient: Boolean,
    ) = updateDisplayState(
        displayState = resolveDisplayState(isInteractive = isInteractive, isAmbient = isAmbient),
        nowElapsedMs = SystemClock.elapsedRealtime(),
    )

    internal fun updateDisplayState(
        displayState: DisplayState,
        nowElapsedMs: Long,
    ) {
        synchronized(lock) {
            if (!captureActive) return
            val previousState = this.displayState
            if (previousState == displayState) return
            closeDisplayInterval(nowElapsedMs)
            this.displayState = displayState
            displayStateStartedAtElapsedMs = nowElapsedMs
            if (previousState != null) {
                displayTransitionCount += 1
            }
        }
    }

    fun updateAppForeground(isForeground: Boolean) =
        updateAppForeground(
            isForeground = isForeground,
            nowElapsedMs = SystemClock.elapsedRealtime(),
        )

    internal fun updateAppForeground(
        isForeground: Boolean,
        nowElapsedMs: Long,
    ) {
        synchronized(lock) {
            if (!captureActive) return
            val previousForeground = appForeground
            if (previousForeground == isForeground) return
            closeAppForegroundInterval(nowElapsedMs)
            appForeground = isForeground
            appForegroundStartedAtElapsedMs = nowElapsedMs
            if (previousForeground != null) {
                appForegroundTransitionCount += 1
            }
        }
    }

    fun summary(): Summary = summary(nowElapsedMs = SystemClock.elapsedRealtime())

    internal fun summary(nowElapsedMs: Long): Summary =
        synchronized(lock) {
            val interactiveDuration =
                interactiveDurationMs + openDisplayDuration(DisplayState.INTERACTIVE, nowElapsedMs)
            val ambientDuration = ambientDurationMs + openDisplayDuration(DisplayState.AMBIENT, nowElapsedMs)
            val offDuration = offDurationMs + openDisplayDuration(DisplayState.OFF, nowElapsedMs)
            val foregroundDuration = appForegroundDurationMs + openAppForegroundDuration(nowElapsedMs)
            val captureEnd = if (captureActive) nowElapsedMs else captureEndedAtElapsedMs
            Summary(
                captureActive = captureActive,
                captureDurationMs =
                    captureStartedAtElapsedMs
                        ?.let { startAtMs -> captureEnd?.minus(startAtMs)?.coerceAtLeast(0L) }
                        ?: 0L,
                interactiveDurationMs = interactiveDuration,
                ambientDurationMs = ambientDuration,
                offDurationMs = offDuration,
                appForegroundDurationMs = foregroundDuration,
                displayTransitionCount = displayTransitionCount,
                appForegroundTransitionCount = appForegroundTransitionCount,
                currentDisplayState = displayState,
                currentAppForeground = appForeground,
                openIntervalsIncluded = captureActive,
            )
        }

    fun clear() = clear(nowElapsedMs = SystemClock.elapsedRealtime())

    internal fun clear(nowElapsedMs: Long) {
        synchronized(lock) {
            interactiveDurationMs = 0L
            ambientDurationMs = 0L
            offDurationMs = 0L
            appForegroundDurationMs = 0L
            displayTransitionCount = 0
            appForegroundTransitionCount = 0
            captureEndedAtElapsedMs = null
            if (captureActive) {
                captureStartedAtElapsedMs = nowElapsedMs
                displayStateStartedAtElapsedMs = nowElapsedMs
                appForegroundStartedAtElapsedMs = nowElapsedMs
            } else {
                captureStartedAtElapsedMs = null
                displayState = null
                displayStateStartedAtElapsedMs = null
                appForeground = null
                appForegroundStartedAtElapsedMs = null
            }
        }
    }

    private fun startCapture(
        nowElapsedMs: Long,
        initialDisplayState: DisplayState,
        initialAppForeground: Boolean,
    ) {
        captureActive = true
        captureStartedAtElapsedMs = nowElapsedMs
        captureEndedAtElapsedMs = null
        displayState = initialDisplayState
        displayStateStartedAtElapsedMs = nowElapsedMs
        appForeground = initialAppForeground
        appForegroundStartedAtElapsedMs = nowElapsedMs
        interactiveDurationMs = 0L
        ambientDurationMs = 0L
        offDurationMs = 0L
        appForegroundDurationMs = 0L
        displayTransitionCount = 0
        appForegroundTransitionCount = 0
    }

    private fun stopCapture(nowElapsedMs: Long) {
        if (!captureActive) return
        closeDisplayInterval(nowElapsedMs)
        closeAppForegroundInterval(nowElapsedMs)
        captureActive = false
        captureEndedAtElapsedMs = nowElapsedMs
    }

    private fun closeDisplayInterval(nowElapsedMs: Long) {
        val startedAtMs = displayStateStartedAtElapsedMs ?: return
        val durationMs = (nowElapsedMs - startedAtMs).coerceAtLeast(0L)
        when (displayState) {
            DisplayState.INTERACTIVE -> interactiveDurationMs += durationMs
            DisplayState.AMBIENT -> ambientDurationMs += durationMs
            DisplayState.OFF -> offDurationMs += durationMs
            null -> Unit
        }
        displayStateStartedAtElapsedMs = null
    }

    private fun closeAppForegroundInterval(nowElapsedMs: Long) {
        val startedAtMs = appForegroundStartedAtElapsedMs ?: return
        if (appForeground == true) {
            appForegroundDurationMs += (nowElapsedMs - startedAtMs).coerceAtLeast(0L)
        }
        appForegroundStartedAtElapsedMs = null
    }

    private fun openDisplayDuration(
        expectedState: DisplayState,
        nowElapsedMs: Long,
    ): Long =
        if (captureActive && displayState == expectedState) {
            displayStateStartedAtElapsedMs
                ?.let { startedAtMs -> (nowElapsedMs - startedAtMs).coerceAtLeast(0L) }
                ?: 0L
        } else {
            0L
        }

    private fun openAppForegroundDuration(nowElapsedMs: Long): Long =
        if (captureActive && appForeground == true) {
            appForegroundStartedAtElapsedMs
                ?.let { startedAtMs -> (nowElapsedMs - startedAtMs).coerceAtLeast(0L) }
                ?: 0L
        } else {
            0L
        }

    private fun resolveDisplayState(
        isInteractive: Boolean,
        isAmbient: Boolean,
    ): DisplayState =
        when {
            isInteractive -> DisplayState.INTERACTIVE
            isAmbient -> DisplayState.AMBIENT
            else -> DisplayState.OFF
        }
}
