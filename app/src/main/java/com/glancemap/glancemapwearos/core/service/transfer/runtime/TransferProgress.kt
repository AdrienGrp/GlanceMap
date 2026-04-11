package com.glancemap.glancemapwearos.core.service.transfer.runtime
import android.os.SystemClock

internal class UiUpdateThrottler(
    private val minIntervalMs: Long,
    private val minStepBytes: Long
) {
    private var lastTime = 0L
    private var lastBytes = 0L

    fun shouldUpdate(bytesCopied: Long): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (lastTime == 0L) {
            lastTime = now
            lastBytes = bytesCopied
            return true
        }

        val timeDelta = now - lastTime
        val bytesDelta = bytesCopied - lastBytes

        if (timeDelta >= minIntervalMs || bytesDelta >= minStepBytes) {
            lastTime = now
            lastBytes = bytesCopied
            return true
        }
        return false
    }
}

internal class ProgressTracker(private val totalSize: Long) {
    private var lastTime = 0L
    private var lastBytes = 0L

    fun formatStatus(bytesCopied: Long): String {
        val now = SystemClock.elapsedRealtime()
        if (lastTime == 0L) {
            lastTime = now
            lastBytes = bytesCopied
            return baseText(bytesCopied, 0f)
        }

        val timeDelta = now - lastTime
        val bytesDelta = bytesCopied - lastBytes

        val speedMBps = if (timeDelta > 0) {
            (bytesDelta * 1000f) / (timeDelta * 1024f * 1024f)
        } else 0f

        lastTime = now
        lastBytes = bytesCopied

        return baseText(bytesCopied, speedMBps)
    }

    private fun baseText(bytesCopied: Long, speedMBps: Float?): String {
        val mb = bytesCopied / 1_048_576f
        val totalStr =
            if (totalSize > 0) "/${String.format("%.1f", totalSize / 1_048_576f)} MB" else ""
        val speedStr = if (speedMBps != null) " (${String.format("%.2f", speedMBps)} MB/s)" else ""
        return "${String.format("%.1f", mb)}$totalStr$speedStr"
    }
}
