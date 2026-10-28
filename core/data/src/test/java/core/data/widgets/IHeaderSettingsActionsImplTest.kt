package core.data.widgets

import core.data.calendar.ICalendarEventSource
import core.data.calendar.UpcomingEventsOutcome
import core.data.prefs.BatteryVisibility
import core.data.prefs.ClockStyle
import core.data.prefs.HeaderSettings
import core.data.testing.createCalendarEventSourceMock
import core.data.testing.createHeaderSettingsActions
import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IHeaderSettingsActionsImplTest {
    @Test
    fun `sets the clock visibility`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock()
        val actions: IHeaderSettingsActions = createHeaderSettingsActions(
            settingsRepository = mockSettingsRepository,
            calendarEventSource = createCalendarEventSourceMock()
        )

        actions.setClockVisible(visible = false)

        coVerify(exactly = 1) { mockSettingsRepository.setClockVisible(visible = false) }
    }

    @Test
    fun `sets the date visibility`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock()
        val actions: IHeaderSettingsActions = createHeaderSettingsActions(
            settingsRepository = mockSettingsRepository,
            calendarEventSource = createCalendarEventSourceMock()
        )

        actions.setDateVisible(visible = false)

        coVerify(exactly = 1) { mockSettingsRepository.setDateVisible(visible = false) }
    }

    @Test
    fun `sets the battery visibility`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock()
        val actions: IHeaderSettingsActions = createHeaderSettingsActions(
            settingsRepository = mockSettingsRepository,
            calendarEventSource = createCalendarEventSourceMock()
        )

        actions.setBatteryVisibility(visibility = BatteryVisibility.CHARGING_OR_LOW)

        coVerify(exactly = 1) { mockSettingsRepository.setBatteryVisibility(visibility = BatteryVisibility.CHARGING_OR_LOW) }
    }

    @Test
    fun `sets the clock style`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock()
        val actualTransform: CapturingSlot<(HeaderSettings) -> HeaderSettings> = slot()
        coEvery { mockSettingsRepository.updateHeaderSettings(capture(actualTransform)) } just Runs
        val actions: IHeaderSettingsActions = createHeaderSettingsActions(
            settingsRepository = mockSettingsRepository,
            calendarEventSource = createCalendarEventSourceMock()
        )

        actions.setClockStyle(style = ClockStyle.STACKED)

        assertEquals(ClockStyle.STACKED, actualTransform.captured(HeaderSettings()).clockStyle)
        coVerify(exactly = 1) { mockSettingsRepository.updateHeaderSettings(any()) }
    }

    @Test
    fun `disables upcoming events with no UI outcome`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock()
        val actions: IHeaderSettingsActions = createHeaderSettingsActions(
            settingsRepository = mockSettingsRepository,
            calendarEventSource = createCalendarEventSourceMock()
        )

        val actualOutcome: UpcomingEventsOutcome = actions.setUpcomingEventsEnabled(enabled = false)

        assertEquals(UpcomingEventsOutcome.NoAction, actualOutcome)
        coVerify(exactly = 1) { mockSettingsRepository.setEventsEnabled(enabled = false) }
    }

    @Test
    fun `enables upcoming events and persists them with permission`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock()
        val mockCalendarEventSource: ICalendarEventSource = createCalendarEventSourceMock(
            permission = MutableStateFlow(true)
        )
        val actions: IHeaderSettingsActions = createHeaderSettingsActions(
            settingsRepository = mockSettingsRepository,
            calendarEventSource = mockCalendarEventSource
        )

        val actualOutcome: UpcomingEventsOutcome = actions.setUpcomingEventsEnabled(enabled = true)

        assertEquals(UpcomingEventsOutcome.NoAction, actualOutcome)
        coVerify(exactly = 1) { mockSettingsRepository.setEventsEnabled(enabled = true) }
    }

    @Test
    fun `requests permission without persisting when permission is missing`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock()
        val actions: IHeaderSettingsActions = createHeaderSettingsActions(
            settingsRepository = mockSettingsRepository,
            calendarEventSource = createCalendarEventSourceMock()
        )

        val actualOutcome: UpcomingEventsOutcome = actions.setUpcomingEventsEnabled(enabled = true)

        assertEquals(UpcomingEventsOutcome.RequestPermission, actualOutcome)
        coVerify(exactly = 0) { mockSettingsRepository.setEventsEnabled(any()) }
    }

    @Test
    fun `reports a permanent denial on later enable attempts without persisting`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock()
        val actions: IHeaderSettingsActions = createHeaderSettingsActions(
            settingsRepository = mockSettingsRepository,
            calendarEventSource = createCalendarEventSourceMock()
        )
        actions.onCalendarPermissionResult(granted = false, canAskAgain = false)

        val actualOutcome: UpcomingEventsOutcome = actions.setUpcomingEventsEnabled(enabled = true)

        assertEquals(UpcomingEventsOutcome.PermissionDenied(permanentlyDenied = true), actualOutcome)
        coVerify(exactly = 0) { mockSettingsRepository.setEventsEnabled(any()) }
    }

    @Test
    fun `enables and persists events after a granted permission result`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock()
        val permission: MutableStateFlow<Boolean> = MutableStateFlow(false)
        val mockCalendarEventSource: ICalendarEventSource = createCalendarEventSourceMock(
            permission = permission,
            onRefresh = { permission.value = true }
        )
        val actions: IHeaderSettingsActions = createHeaderSettingsActions(
            settingsRepository = mockSettingsRepository,
            calendarEventSource = mockCalendarEventSource
        )

        val actualOutcome: UpcomingEventsOutcome = actions.onCalendarPermissionResult(granted = true, canAskAgain = true)

        assertEquals(UpcomingEventsOutcome.NoAction, actualOutcome)
        verify(exactly = 1) { mockCalendarEventSource.refreshPermission() }
        coVerify(exactly = 1) { mockSettingsRepository.setEventsEnabled(enabled = true) }
    }

    @Test
    fun `reports a denial after a permission result without persisting`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock()
        val actions: IHeaderSettingsActions = createHeaderSettingsActions(
            settingsRepository = mockSettingsRepository,
            calendarEventSource = createCalendarEventSourceMock()
        )

        val actualOutcome: UpcomingEventsOutcome = actions.onCalendarPermissionResult(granted = false, canAskAgain = true)

        assertEquals(UpcomingEventsOutcome.PermissionDenied(permanentlyDenied = false), actualOutcome)
        coVerify(exactly = 0) { mockSettingsRepository.setEventsEnabled(any()) }
    }

    @Test
    fun `refreshes the calendar permission`() {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock()
        val mockCalendarEventSource: ICalendarEventSource = createCalendarEventSourceMock()
        val actions: IHeaderSettingsActions = createHeaderSettingsActions(
            settingsRepository = mockSettingsRepository,
            calendarEventSource = mockCalendarEventSource
        )

        actions.refreshCalendarPermission()

        verify(exactly = 1) { mockCalendarEventSource.refreshPermission() }
    }

    private fun createWidgetSettingsRepositoryMock(): IWidgetSettingsRepository = mockk {
        coEvery { setClockVisible(any()) } just Runs
        coEvery { setDateVisible(any()) } just Runs
        coEvery { setBatteryVisibility(any()) } just Runs
        coEvery { setEventsEnabled(any()) } just Runs
    }
}
