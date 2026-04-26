package com.glancemap.glancemapcompanionapp.macrobenchmark

import android.content.Intent
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneCompanionBenchmarks {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait(launchIntent())
    }

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun filePickerIdleBaseline() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics =
            listOf(
                FrameTimingMetric(),
                MemoryUsageMetric(
                    mode = MemoryUsageMetric.Mode.Max,
                    subMetrics =
                        listOf(
                            MemoryUsageMetric.SubMetric.HeapSize,
                            MemoryUsageMetric.SubMetric.RssAnon,
                        ),
                ),
            ),
        iterations = 5,
        setupBlock = {
            launchCompanionAndWait()
        },
    ) {
        Thread.sleep(FILE_PICKER_IDLE_MS)
    }

    private fun MacrobenchmarkScope.launchCompanionAndWait() {
        pressHome()
        startActivityAndWait(launchIntent())
        device.waitForIdle()
        Thread.sleep(APP_LAUNCH_SETTLE_MS)
    }

    private fun launchIntent(): Intent =
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(TARGET_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private companion object {
        const val TARGET_PACKAGE = "com.glancemap.glancemapwearos"
        const val APP_LAUNCH_SETTLE_MS = 1_500L
        const val FILE_PICKER_IDLE_MS = 3_000L
    }
}
