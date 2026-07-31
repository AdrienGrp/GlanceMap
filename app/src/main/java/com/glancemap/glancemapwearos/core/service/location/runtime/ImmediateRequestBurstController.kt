package com.glancemap.glancemapwearos.core.service.location.runtime

internal class ImmediateRequestBurstController(
    private val cooldownMs: Long,
    initialBurstId: Long = 1L,
) {
    sealed interface Decision {
        data class Start(
            val burstId: Long,
        ) : Decision

        data class SkipCooldown(
            val remainingMs: Long,
        ) : Decision

        data class SkipActiveBurst(
            val activeBurstId: Long,
        ) : Decision
    }

    private val lock = Any()
    private var inBurst: Boolean = false
    private var activeBurstId: Long? = null
    private var lastStartElapsedMs: Long = Long.MIN_VALUE
    private var nextBurstId: Long = initialBurstId

    fun request(nowElapsedMs: Long): Decision =
        synchronized(lock) {
            val activeId = activeBurstId
            if (inBurst && activeId != null) {
                return Decision.SkipActiveBurst(activeBurstId = activeId)
            }

            if (lastStartElapsedMs != Long.MIN_VALUE) {
                val elapsedSinceLastStartMs = (nowElapsedMs - lastStartElapsedMs).coerceAtLeast(0L)
                if (elapsedSinceLastStartMs < cooldownMs) {
                    return Decision.SkipCooldown(remainingMs = cooldownMs - elapsedSinceLastStartMs)
                }
            }

            val burstId = nextBurstId++
            inBurst = true
            activeBurstId = burstId
            lastStartElapsedMs = nowElapsedMs
            Decision.Start(burstId = burstId)
        }

    fun end(expectedBurstId: Long? = null): Long? =
        synchronized(lock) {
            if (!inBurst) return null

            val activeId = activeBurstId ?: return null
            if (expectedBurstId != null && expectedBurstId != activeId) {
                return null
            }

            inBurst = false
            activeBurstId = null
            activeId
        }

    fun isInBurst(): Boolean = synchronized(lock) { inBurst }
}
