package com.example.hevygroovy.macrobenchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Runs only against the separate .benchmark app, never the user's installed app/data.
 * Fixture creation, navigation, compilation and editor input are outside measured blocks.
 */
@LargeTest
@OptIn(ExperimentalMetricApi::class)
@RunWith(Parameterized::class)
class WorkoutBenchmarks(private val historyCount: Int) {
    @get:Rule
    val benchmark = MacrobenchmarkRule()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val iterations: Int
        get() {
            val value = InstrumentationRegistry.getArguments().getString("benchmarkIterations")
            return if (value == null) 20 else value.toInt().also {
                require(it > 0) { "benchmarkIterations must be greater than zero" }
            }
        }

    @Test
    fun coldStartup() {
        prepareFixture()
        benchmark.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.COLD,
            iterations = iterations,
            setupBlock = { pressHome() }
        ) {
            startActivityAndWait(mainIntent())
            awaitReady("workouts-ready")
        }
    }

    @Test
    fun libraryLoad() {
        prepareFixture()
        benchmark.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(TraceSectionMetric("library_load", TraceSectionMetric.Mode.Sum)),
            compilationMode = CompilationMode.None(),
            iterations = iterations,
            setupBlock = { openHome() }
        ) {
            required("exercises-tab").click()
            awaitReady("library-ready")
        }
    }

    @Test
    fun templateOpen() {
        prepareFixture()
        benchmark.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(TraceSectionMetric("template_open", TraceSectionMetric.Mode.Sum)),
            compilationMode = CompilationMode.None(),
            iterations = iterations,
            setupBlock = {
                openHome()
                required("templates-tab").click()
                // A previous iteration may have left the selected template open.
                device.findObject(By.res("template-list-back"))?.click()
                awaitReady("template-list-ready")
            }
        ) {
            required("open-template-1").click()
            awaitReady("template-ready")
        }
    }

    @Test
    fun setSave() {
        prepareFixture()
        var editNumber = 0
        benchmark.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(TraceSectionMetric("set_save", TraceSectionMetric.Mode.Sum)),
            compilationMode = CompilationMode.None(),
            iterations = iterations,
            setupBlock = {
                openHome()
                required("set-editor-1").click()
                required("set-reps").text = "8"
                required("set-weight").text = if (editNumber++ % 2 == 0) "185" else "186"
            }
        ) {
            required("save-set").click()
            check(device.wait(Until.gone(By.res("save-set")), UI_TIMEOUT_MS)) {
                "Set editor did not close after a successful save"
            }
            awaitReady("workouts-ready")
        }
    }

    @Test
    fun libraryScroll() {
        prepareFixture()
        benchmark.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = iterations,
            setupBlock = {
                openHome()
                required("exercises-tab").click()
                awaitReady("library-ready")
                required("library-list").apply {
                    setGestureMargin(device.displayWidth / 5)
                    // Reset to the top outside the measured region.
                    while (scroll(Direction.UP, 1f)) { /* bounded by the fixture list */ }
                }
            }
        ) {
            required("library-list").apply {
                setGestureMargin(device.displayWidth / 5)
                scroll(Direction.DOWN, 1f)
                scroll(Direction.DOWN, 1f)
                scroll(Direction.DOWN, 1f)
            }
        }
    }

    private fun prepareFixture() {
        check(TARGET_PACKAGE.endsWith(".benchmark"))
        device.wakeUp()
        device.executeShellCommand("am force-stop $TARGET_PACKAGE")
        device.executeShellCommand(
            "am start -W -n $TARGET_PACKAGE/" +
                "com.example.hevygroovy.benchmark.BenchmarkSetupActivity --ei history_count $historyCount"
        )
        check(device.wait(Until.hasObject(By.text("Benchmark fixture ready")), FIXTURE_TIMEOUT_MS)) {
            "Benchmark fixture initialization failed for $historyCount history workouts"
        }
        device.executeShellCommand("am force-stop $TARGET_PACKAGE")
        device.pressHome()
    }

    private fun MacrobenchmarkScope.openHome() {
        startActivityAndWait(mainIntent())
        required("home-tab").click()
        awaitReady("workouts-ready")
    }

    private fun mainIntent() = Intent(Intent.ACTION_MAIN).apply {
        component = ComponentName(TARGET_PACKAGE, "com.example.hevygroovy.MainActivity")
        addCategory(Intent.CATEGORY_LAUNCHER)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    private fun awaitReady(marker: String) {
        required(marker)
        required("benchmark-idle")
    }

    private fun required(id: String): UiObject2 =
        checkNotNull(device.wait(Until.findObject(By.res(id)), UI_TIMEOUT_MS)) {
            "Timed out waiting for $id (historyCount=$historyCount)"
        }

    companion object {
        private const val TARGET_PACKAGE = "com.example.hevygroovy.benchmark"
        private const val UI_TIMEOUT_MS = 10_000L
        private const val FIXTURE_TIMEOUT_MS = 120_000L

        @JvmStatic
        @Parameterized.Parameters(name = "history={0}")
        fun datasets() = listOf(arrayOf(10), arrayOf(1000))
    }
}
