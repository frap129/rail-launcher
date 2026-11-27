package core.ui.widgets

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.annotation.StringRes
import core.data.launchables.model.Launchable
import core.data.testing.TEST_SDK
import core.data.testing.createRailLaunchable
import core.data.testing.failActivityStarts
import core.ui.R
import core.ui.calendar.OpenEventTarget
import core.ui.testing.IntentLaunchTestBase
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

private const val CLOCK_PACKAGE: String = "com.example.clock"
private const val CLOCK_ACTIVITY: String = "com.example.clock.ClockActivity"
private const val CALENDAR_MILLIS: Long = 3_000L
private const val EVENT_ID: Long = 42L
private const val EVENT_BEGIN_MILLIS: Long = 1_000L
private const val EVENT_END_MILLIS: Long = 2_000L
private const val CALENDAR_EVENT_FLAGS: Int = 0

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class WidgetAreaLaunchTest : IntentLaunchTestBase() {
    @Before
    fun setUp() {
        ShadowToast.reset()
    }

    @Test
    fun `an alarms launch failure shows the no clock app toast`() {
        assertNoAppToast(target = HeaderTapTarget.SystemAlarms, expectedMessage = R.string.no_clock_app)
    }

    @Test
    fun `a calendar launch failure shows the no calendar app toast`() {
        assertNoAppToast(target = HeaderTapTarget.SystemCalendar(CALENDAR_MILLIS), expectedMessage = R.string.no_calendar_app)
    }

    @Test
    fun `a calendar event launch failure shows the no calendar app toast`() {
        val inputTarget: OpenEventTarget = createEventTarget(isWorkProfile = false)
        assertNoAppToast(target = HeaderTapTarget.OpenEvent(inputTarget), expectedMessage = R.string.no_calendar_app)
    }

    @Test
    fun `a declined work-profile calendar launch shows the no calendar app toast`() {
        val inputTarget: OpenEventTarget = createEventTarget(isWorkProfile = true)
        val mockContext: Context = createWorkProfileContext(inputStartEvent = { false })
        assertNoAppToast(
            target = HeaderTapTarget.OpenEvent(inputTarget),
            expectedMessage = R.string.no_calendar_app,
            mockContext = mockContext
        )
    }

    @Test
    fun `a failed work-profile calendar launch shows the no calendar app toast`() {
        val inputTarget: OpenEventTarget = createEventTarget(isWorkProfile = true)
        val mockContext: Context = createWorkProfileContext(
            inputStartEvent = { throw IllegalStateException("The work-profile calendar cannot open") }
        )
        assertNoAppToast(
            target = HeaderTapTarget.OpenEvent(inputTarget),
            expectedMessage = R.string.no_calendar_app,
            mockContext = mockContext
        )
    }

    @Test
    fun `a battery usage launch failure shows the no battery usage toast`() {
        assertNoAppToast(target = HeaderTapTarget.SystemBatteryUsage, expectedMessage = R.string.no_battery_usage_app)
    }

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

    private fun createEventTarget(isWorkProfile: Boolean): OpenEventTarget = OpenEventTarget(
        eventId = EVENT_ID,
        beginMillis = EVENT_BEGIN_MILLIS,
        endMillis = EVENT_END_MILLIS,
        isWorkProfile = isWorkProfile
    )

    private fun createWorkProfileContext(inputStartEvent: () -> Boolean): Context {
        val mockContext: Context = spyk(context)
        val mockDevicePolicyManager: DevicePolicyManager = mockk(relaxed = true)
        every { mockContext.getSystemService(Context.DEVICE_POLICY_SERVICE) } returns mockDevicePolicyManager
        // CalendarContract forwards to this hidden DevicePolicyManager API.
        every {
            mockDevicePolicyManager["startViewCalendarEventInManagedProfile"](
                EVENT_ID,
                EVENT_BEGIN_MILLIS,
                EVENT_END_MILLIS,
                false,
                CALENDAR_EVENT_FLAGS
            )
        } answers { inputStartEvent() }
        return mockContext
    }

    private fun assertNoAppToast(target: HeaderTapTarget, @StringRes expectedMessage: Int, mockContext: Context = spyk(context)) {
        mockContext.failActivityStarts()
        mockContext.runHeaderTapAction(target = target, onLaunch = {}, onOpenAgenda = {})
        assertEquals(context.getString(expectedMessage), ShadowToast.getTextOfLatestToast())
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
