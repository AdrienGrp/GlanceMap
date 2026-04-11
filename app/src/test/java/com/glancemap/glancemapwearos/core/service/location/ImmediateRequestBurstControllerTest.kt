package com.glancemap.glancemapwearos.core.service.location

import com.glancemap.glancemapwearos.core.service.location.runtime.ImmediateRequestBurstController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ImmediateRequestBurstControllerTest {
    @Test
    fun startsBurstWhenIdle() {
        val controller = ImmediateRequestBurstController(cooldownMs = 5_000L)

        val decision = controller.request(nowElapsedMs = 10_000L)

        val start = decision as ImmediateRequestBurstController.Decision.Start
        assertEquals(1L, start.burstId)
        assertTrue(controller.isInBurst())
    }

    @Test
    fun skipsWhenBurstAlreadyActive() {
        val controller = ImmediateRequestBurstController(cooldownMs = 5_000L)
        val started = controller.request(nowElapsedMs = 10_000L) as ImmediateRequestBurstController.Decision.Start

        val decision = controller.request(nowElapsedMs = 10_100L)

        val skip = decision as ImmediateRequestBurstController.Decision.SkipActiveBurst
        assertEquals(started.burstId, skip.activeBurstId)
    }

    @Test
    fun cooldownStartsFromActualBurstStart() {
        val controller = ImmediateRequestBurstController(cooldownMs = 5_000L)
        val started = controller.request(nowElapsedMs = 10_000L) as ImmediateRequestBurstController.Decision.Start
        val ended = controller.end(expectedBurstId = started.burstId)
        assertEquals(started.burstId, ended)
        assertFalse(controller.isInBurst())

        val decision = controller.request(nowElapsedMs = 13_000L)

        val skip = decision as ImmediateRequestBurstController.Decision.SkipCooldown
        assertEquals(2_000L, skip.remainingMs)
    }

    @Test
    fun staleEndDoesNotCloseNewerBurst() {
        val controller = ImmediateRequestBurstController(cooldownMs = 1_000L)
        val first = controller.request(nowElapsedMs = 10_000L) as ImmediateRequestBurstController.Decision.Start
        controller.end(expectedBurstId = first.burstId)
        val second = controller.request(nowElapsedMs = 12_000L) as ImmediateRequestBurstController.Decision.Start
        assertTrue(controller.isInBurst())

        val staleEnd = controller.end(expectedBurstId = first.burstId)
        assertNull(staleEnd)
        assertTrue(controller.isInBurst())

        val secondEnd = controller.end(expectedBurstId = second.burstId)
        assertEquals(second.burstId, secondEnd)
        assertFalse(controller.isInBurst())
    }

    @Test
    fun concurrentRequestsStartOnlyOneBurst() {
        val controller = ImmediateRequestBurstController(cooldownMs = 5_000L)
        val pool = Executors.newFixedThreadPool(8)
        val startGate = CountDownLatch(1)
        val doneGate = CountDownLatch(24)
        val results =
            Collections.synchronizedList(
                mutableListOf<ImmediateRequestBurstController.Decision>(),
            )

        repeat(24) {
            pool.submit {
                runCatching {
                    startGate.await(2, TimeUnit.SECONDS)
                    val decision = controller.request(nowElapsedMs = 10_000L)
                    results.add(decision)
                }
                doneGate.countDown()
            }
        }

        startGate.countDown()
        assertTrue(doneGate.await(3, TimeUnit.SECONDS))
        pool.shutdownNow()

        val startCount = results.count { it is ImmediateRequestBurstController.Decision.Start }
        val skipBurstCount = results.count { it is ImmediateRequestBurstController.Decision.SkipActiveBurst }
        val skipCooldownCount = results.count { it is ImmediateRequestBurstController.Decision.SkipCooldown }

        assertEquals(1, startCount)
        assertEquals(23, skipBurstCount + skipCooldownCount)
        val activeSkip =
            results.firstOrNull { it is ImmediateRequestBurstController.Decision.SkipActiveBurst }
                as? ImmediateRequestBurstController.Decision.SkipActiveBurst
        assertNotNull(activeSkip)
    }
}
