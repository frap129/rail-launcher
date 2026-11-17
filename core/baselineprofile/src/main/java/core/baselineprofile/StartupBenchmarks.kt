package core.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import core.ui.HOME_FULLY_DRAWN_TEST_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val ITERATIONS = 10
private const val FULLY_DRAWN_TIMEOUT_MS = 10_000L

// Compose test tags keep the package out of the resource id, so the node's package pins it to Rail.
private val HOME_FULLY_DRAWN_SELECTOR: BySelector = By.res(HOME_FULLY_DRAWN_TEST_TAG).pkg(APP_PACKAGE)

/**
 * Benchmarks the cold start to a usable Home: from a HOME press with Rail as the default Home
 * until Rail reports Home fully drawn, after its Header, Widget Area and Favorites are shown.
 *
 * Each run compares [CompilationMode.None], which represents the app with no Baseline Profile
 * optimizations, with [CompilationMode.Partial], which uses Baseline Profiles. The
 * [StartupTimingMetric] records the time to initial display (TTID) and the time to full display
 * (TTFD); the latter only exists because Rail reports Home fully drawn.
 *
 * Run it on a physical device, not an Android emulator, because the emulator doesn't represent
 * real world performance. Run both variants with this Gradle task:
 * ```
 * ./gradlew :core:baselineprofile:connectedBenchmarkReleaseAndroidTest
 * ```
 *
 * For more information, see the [Macrobenchmark documentation](https://d.android.com/macrobenchmark#create-macrobenchmark)
 * and the [instrumentation arguments documentation](https://d.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args).
 **/
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    private val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun startupCompilationNone() = benchmark(compilationMode = CompilationMode.None())

    @Test
    fun startupCompilationBaselineProfiles() = benchmark(compilationMode = CompilationMode.Partial(BaselineProfileMode.Require))

    private fun benchmark(compilationMode: CompilationMode) = withPreviousHomeRestored(device = device) { previousHome ->
        // The system restarts the default Home when its process dies, so another Home holds the
        // default for the COLD kill while Rail owns it for the measured HOME press.
        val homeToHold: String = checkNotNull(resolveHomeToHold(device = device, previousHome = previousHome)) {
            "Install a Home app other than Rail before benchmarking; the device Home is: $previousHome"
        }
        rule.measureRepeated(
            packageName = APP_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = ITERATIONS,
            setupBlock = {
                setDefaultHome(device = device, homeActivity = homeToHold)
                pressHome()
            },
            measureBlock = {
                // Rail is the default Home for a real HOME press, which starts it cold.
                setDefaultHome(device = device, homeActivity = APP_HOME_ACTIVITY)
                pressHome()
                check(device.wait(Until.hasObject(HOME_FULLY_DRAWN_SELECTOR), FULLY_DRAWN_TIMEOUT_MS)) {
                    "Rail did not report Home fully drawn"
                }
            }
        )
    }
}
