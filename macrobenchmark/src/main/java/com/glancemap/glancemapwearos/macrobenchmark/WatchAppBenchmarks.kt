package com.glancemap.glancemapwearos.macrobenchmark

import android.content.Intent
import android.os.Build
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.PowerCategory
import androidx.benchmark.macro.PowerCategoryDisplayLevel
import androidx.benchmark.macro.PowerMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchAppBenchmarks {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        startActivityAndWait(launchIntent())
    }

    @Test
    fun navigateFrameTiming() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait(launchIntent())
            device.waitForIdle()
        }
    ) {
        repeat(PAN_REPEATS_PER_ITERATION) {
            panNavigateMap()
        }
    }

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun navigateBatteryImpact() {
        assumeTrue(
            "Battery benchmarks need a physical device.",
            !isEmulator()
        )
        assumeTrue(
            "Battery benchmarks require sufficient device charge.",
            PowerMetric.deviceBatteryHasMinimumCharge()
        )

        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(configurePowerMetric()),
            iterations = 3,
            setupBlock = {
                pressHome()
                startActivityAndWait(launchIntent())
                device.waitForIdle()
            }
        ) {
            repeat(BATTERY_PAN_CYCLES) {
                panNavigateMap()
            }
            Thread.sleep(BATTERY_SETTLE_MS)
        }
    }

    @OptIn(ExperimentalMetricApi::class)
    private fun configurePowerMetric(): PowerMetric {
        return if (PowerMetric.deviceSupportsHighPrecisionTracking()) {
            PowerMetric(
                type = PowerMetric.Type.Energy(
                    mapOf(
                        PowerCategory.CPU to PowerCategoryDisplayLevel.TOTAL,
                        PowerCategory.DISPLAY to PowerCategoryDisplayLevel.TOTAL,
                        PowerCategory.GPS to PowerCategoryDisplayLevel.TOTAL,
                        PowerCategory.GPU to PowerCategoryDisplayLevel.TOTAL,
                        PowerCategory.NETWORK to PowerCategoryDisplayLevel.TOTAL,
                    )
                )
            )
        } else {
            PowerMetric(type = PowerMetric.Type.Battery())
        }
    }

    private fun MacrobenchmarkScope.panNavigateMap() {
        val centerY = device.displayHeight / 2
        val startX = (device.displayWidth * 0.75f).toInt()
        val endX = (device.displayWidth * 0.25f).toInt()

        device.swipe(startX, centerY, endX, centerY, SWIPE_STEPS)
        device.waitForIdle()
        device.swipe(endX, centerY, startX, centerY, SWIPE_STEPS)
        device.waitForIdle()
    }

    private fun launchIntent(): Intent {
        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(TARGET_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("sdk_gphone", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk", ignoreCase = true)
    }

    companion object {
        private const val TARGET_PACKAGE = "com.glancemap.glancemapwearos"
        private const val SWIPE_STEPS = 24
        private const val PAN_REPEATS_PER_ITERATION = 2
        private const val BATTERY_PAN_CYCLES = 5
        private const val BATTERY_SETTLE_MS = 2_000L
    }
}
