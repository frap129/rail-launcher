package core.ui.widgets

import android.content.Intent
import android.provider.AlarmClock
import core.data.launchables.model.Launchable
import core.data.testing.TEST_SDK
import core.data.testing.createRailLaunchable
import core.ui.testing.IntentLaunchTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val CLOCK_PACKAGE: String = "com.example.clock"
private const val CLOCK_ACTIVITY: String = "com.example.clock.ClockActivity"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class WidgetAreaLaunchTest : IntentLaunchTestBase() {
    @Test
    fun `a Launchable Tap action goes through the launcher launch callback`() {
        val inputLaunchable: Launchable = createRailLaunchable(name = "Maps")

        val actualLaunched: Launchable? = runTapAction(target = HeaderTapTarget.LaunchLaunchable(launchable = inputLaunchable))

        assertEquals(inputLaunchable, actualLaunched)
        assertNull(shadowActivity.nextStartedActivity)
    }

    @Test
    fun `a nothing Tap action opens nothing`() {
        val actualIntent: Intent? = runSelfOpeningTapAction(target = HeaderTapTarget.None)

        assertNull(actualIntent)
    }

    @Test
    fun `a system Tap action still opens its system app`() {
        registerIntentHandler(packageName = CLOCK_PACKAGE, className = CLOCK_ACTIVITY, action = AlarmClock.ACTION_SHOW_ALARMS)

        val actualIntent: Intent? = runSelfOpeningTapAction(target = HeaderTapTarget.SystemAlarms)

        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, actualIntent?.action)
    }

    @Test
    fun `an Open Agenda Tap action goes through the Agenda callback`() {
        var actualOpenAgendaCount: Int = 0

        val actualLaunched: Launchable? = runTapAction(
            target = HeaderTapTarget.OpenAgenda,
            onOpenAgenda = { actualOpenAgendaCount++ }
        )

        assertNull(actualLaunched)
        assertEquals(1, actualOpenAgendaCount)
        assertNull(shadowActivity.nextStartedActivity)
    }

    /** Runs [target] and returns the Launchable it handed to the launcher launch path, if any. */
    private fun runTapAction(target: HeaderTapTarget, onOpenAgenda: () -> Unit = {}): Launchable? {
        var actualLaunched: Launchable? = null
        context.runHeaderTapAction(
            target = target,
            onLaunch = { launchable -> actualLaunched = launchable },
            onOpenAgenda = onOpenAgenda
        )
        return actualLaunched
    }

    /** Runs a [target] that must open itself and returns the started intent, asserting it bypasses the launch path. */
    private fun runSelfOpeningTapAction(target: HeaderTapTarget): Intent? {
        assertNull(runTapAction(target = target))
        return shadowActivity.nextStartedActivity
    }
}
