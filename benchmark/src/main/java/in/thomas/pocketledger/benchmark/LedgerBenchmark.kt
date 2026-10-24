package `in`.thomas.pocketledger.benchmark

import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith

private const val Target = "in.thomas.pocketledger.validation"

private fun seedFixture() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    device.wakeUp()
    device.executeShellCommand("wm dismiss-keyguard")
    device.executeShellCommand("am start -n $Target/in.thomas.pocketledger.SeedActivity")
    check(device.wait(Until.hasObject(By.text("Benchmark fixture ready")), 60_000))
    device.pressHome()
}

private fun MacrobenchmarkScope.waitFor(text: String): UiObject2 =
    checkNotNull(device.wait(Until.findObject(By.text(text)), 15_000)) { "Missing $text" }

@RunWith(AndroidJUnit4::class)
class LedgerBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()
    @Before fun seed() = seedFixture()

    @Test fun coldStartup() = benchmark.measureRepeated(
        packageName = Target,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
        startupMode = StartupMode.COLD,
        iterations = 30,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
        waitFor("Income remaining")
    }

    @Test fun overviewToActivityAndBack() = benchmark.measureRepeated(
        packageName = Target,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
        iterations = 30,
        setupBlock = { startActivityAndWait(); waitFor("Income remaining") }
    ) {
        waitFor("Spent").click()
        waitFor("Spent this month")
        device.pressBack()
        waitFor("Income remaining")
    }
}

@RunWith(AndroidJUnit4::class)
class LedgerProfileGenerator {
    @get:Rule val profile = BaselineProfileRule()
    @Before fun seed() = seedFixture()

    @Test fun startup() = profile.collect(
        packageName = Target,
        maxIterations = 6,
        stableIterations = 2,
        includeInStartupProfile = true,
        filterPredicate = { it.contains("Lin/thomas/pocketledger/") }
    ) {
        device.wakeUp()
        pressHome()
        startActivityAndWait()
        waitFor("Income remaining")
    }

    @Test fun interactions() = profile.collect(packageName = Target, maxIterations = 6, stableIterations = 2, filterPredicate = { it.contains("Lin/thomas/pocketledger/") }) {
        device.wakeUp()
        startActivityAndWait()
        waitFor("Income remaining")
        waitFor("Spent").click()
        waitFor("Spent this month")
        device.pressBack()
        waitFor("Income remaining")
        waitFor("Activity").click()
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
            device.displayWidth / 2, device.displayHeight / 3, 30)
        waitFor("Overview").click()
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
            device.displayWidth / 2, device.displayHeight / 3, 30)
        waitFor("Insights").click()
        waitFor("Where it goes")
        device.findObject(By.desc("Back")).click()
        waitFor("Overview").click()
        device.findObject(By.desc("Add transaction")).click()
        waitFor("New transaction")
        waitFor("Cancel").click()
    }
}
