package io.github.ashishkupadhyay.mantis.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * NFR-2 (60 fps scrolling through a large list) and NFR-3 (search results as you type): frame timing while flinging
 * the Transactions tab and while typing a query, on a device seeded with [SEED_MONTHS] months of sample data
 * (≈ 50 k rows). The first run seeds through the app's launch-intent hook (`mantis.seed.months`) and waits for
 * the "Seeded" snackbar; later iterations reuse the data. Run on a physical device:
 * `gradlew :benchmark:connectedBenchmarkAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class TransactionsBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Partial(),
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    @Test
    fun scrollTransactions() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(),
        setupBlock = { seedOnce(); openTransactions() },
    ) {
        val list = device.findObject(By.scrollable(true))
        list.setGestureMargin(device.displayWidth / 5)
        repeat(FLINGS) { list.fling(Direction.DOWN) }
    }

    @Test
    fun searchAsYouType() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(),
        setupBlock = { seedOnce(); openTransactions() },
    ) {
        device.findObject(By.text("Search transactions")).click()
        device.waitForIdle()
        "zomato".forEach { char ->
            device.pressKeyCode(android.view.KeyEvent.keyCodeFromString("KEYCODE_${char.uppercaseChar()}"))
            device.waitForIdle()
        }
        device.wait(Until.hasObject(By.textContains("Zomato")), TIMEOUT_MS)
        device.pressBack()
    }

    private fun MacrobenchmarkScope.seedOnce() {
        if (seeded) return
        pressHome()
        startActivityAndWait { intent: Intent -> intent.putExtra(EXTRA_SEED_MONTHS, SEED_MONTHS) }
        check(device.wait(Until.hasObject(By.textStartsWith("Seeded")), SEED_TIMEOUT_MS)) { "seeding did not finish" }
        seeded = true
    }

    private fun MacrobenchmarkScope.openTransactions() {
        startActivityAndWait()
        device.findObject(By.text("Transactions")).click()
        check(device.wait(Until.hasObject(By.scrollable(true)), TIMEOUT_MS)) { "transactions list did not appear" }
    }

    private companion object {
        const val PACKAGE = "io.github.ashishkupadhyay.mantis.benchmark"
        const val EXTRA_SEED_MONTHS = "mantis.seed.months"
        const val SEED_MONTHS = 900
        const val FLINGS = 6
        const val TIMEOUT_MS = 10_000L
        const val SEED_TIMEOUT_MS = 180_000L

        @Volatile
        var seeded = false
    }
}
