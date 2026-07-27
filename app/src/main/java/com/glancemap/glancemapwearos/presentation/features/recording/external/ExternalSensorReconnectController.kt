package com.glancemap.glancemapwearos.presentation.features.recording.external

import android.os.Handler
import android.os.Looper
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry

internal class ExternalSensorReconnectController(
    private val logTag: String,
    private val autoReconnect: Boolean,
    private val connect: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var attempt = 0
    private var scheduled = false
    private val reconnectRunnable =
        Runnable {
            scheduled = false
            if (!active) return@Runnable
            attempt += 1
            DebugTelemetry.log(logTag, "event=reconnect_attempt attempt=$attempt")
            connect()
        }

    fun start() {
        active = true
        attempt = 0
        cancelScheduled()
    }

    fun stop() {
        active = false
        attempt = 0
        cancelScheduled()
    }

    fun markReady() {
        attempt = 0
        cancelScheduled()
    }

    fun onDisconnected(event: BleDisconnectEvent) {
        if (!autoReconnect || !active) return
        if (!event.retryable || scheduled) return
        val nextAttempt = attempt + 1
        val delayMs = externalSensorReconnectDelayMs(nextAttempt)
        scheduled = true
        DebugTelemetry.log(
            logTag,
            "event=reconnect_scheduled attempt=$nextAttempt delayMs=$delayMs " +
                "status=${event.status} reason=${event.reason}",
        )
        handler.postDelayed(reconnectRunnable, delayMs)
    }

    private fun cancelScheduled() {
        scheduled = false
        handler.removeCallbacks(reconnectRunnable)
    }
}

internal fun externalSensorReconnectDelayMs(attempt: Int): Long =
    when (attempt) {
        1 -> 2_000L
        2 -> 5_000L
        3 -> 10_000L
        4 -> 30_000L
        else -> 60_000L
    }

internal fun shouldLogExternalSensorSample(sampleCount: Int): Boolean =
    sampleCount <= INITIAL_TELEMETRY_SAMPLES ||
        sampleCount % TELEMETRY_SAMPLE_INTERVAL == 0

private const val INITIAL_TELEMETRY_SAMPLES = 5
private const val TELEMETRY_SAMPLE_INTERVAL = 30
