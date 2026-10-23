package core.data.widgets

import core.data.calendar.UpcomingEventsOutcome
import core.data.prefs.BatteryVisibility
import core.data.prefs.ClockStyle
import core.data.prefs.EventSettings
import core.data.testing.FakeCalendarEventSource
import core.data.testing.FakeWidgetSettingsRepository
import core.data.testing.createHeaderSettingsActions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IHeaderSettingsActionsImplTest {
    @Test
    fun `sets the clock visibility`() = runTest {
        val mockSettingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val actions: IHeaderSettingsActions = createHeaderSettingsActions(settingsRepository = mockSettingsRepository)
        actions.setClockVisible(visible = false)
        assertEquals(false, mockSettingsRepository.updates.single().clockVisible)
    }

    @Test
    fun `sets the date visibility`() = runTest {
        val mockSettingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val actions: IHeaderSettingsActions = createHeaderSettingsActions(settingsRepository = mockSettingsRepository)
        actions.setDateVisible(visible = false)
        assertEquals(false, mockSettingsRepository.updates.single().dateVisible)
    }

    @Test
    fun `sets the battery visibility`() = runTest {
        val mockSettingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val actions: IHeaderSettingsActions = createHeaderSettingsActions(settingsRepository = mockSettingsRepository)
        actions.setBatteryVisibility(visibility = BatteryVisibility.CHARGING_OR_LOW)
        assertEquals(BatteryVisibility.CHARGING_OR_LOW, mockSettingsRepository.updates.single().batteryVisibility)
    }

    @Test
    fun `sets the clock style`() = runTest {
        val mockSettingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val actions: IHeaderSettingsActions = createHeaderSettingsActions(settingsRepository = mockSettingsRepository)
        actions.setClockStyle(style = ClockStyle.STACKED)
        assertEquals(ClockStyle.STACKED, mockSettingsRepository.updates.single().clockStyle)
    }

    @Test
    fun `disables upcoming events with no UI outcome`() = runTest {
        val mockSettingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            initialEventSettings = EventSettings(enabled = true)
        )
        val actions: IHeaderSettingsActions = createHeaderSettingsActions(settingsRepository = mockSettingsRepository)
        val actualOutcome: UpcomingEventsOutcome = actions.setUpcomingEventsEnabled(enabled = false)
        assertEquals(UpcomingEventsOutcome.NoAction, actualOutcome)
        assertEquals(false, mockSettingsRepository.eventUpdates.single().enabled)
    }

    @Test
    fun `enables upcoming events and persists them with permission`() = runTest {
        val mockSettingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val actions: IHeaderSettingsActions = createHeaderSettingsActions(
            settingsRepository = mockSettingsRepository,
            calendarEventSource = FakeCalendarEventSource(hasPermission = true)
        )
        val actualOutcome: UpcomingEventsOutcome = actions.setUpcomingEventsEnabled(enabled = true)
        assertEquals(UpcomingEventsOutcome.NoAction, actualOutcome)
        assertEquals(true, mockSettingsRepository.eventUpdates.single().enabled)
    }

    @Test
    fun `requests permission without persisting when permission is missing`() = runTest {
        val mockSettingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val actions: IHeaderSettingsActions = createHeaderSettingsActions(
            settingsRepository = mockSettingsRepository,
            calendarEventSource = FakeCalendarEventSource(hasPermission = false)
        )
        val actualOutcome: UpcomingEventsOutcome = actions.setUpcomingEventsEnabled(enabled = true)
        assertEquals(UpcomingEventsOutcome.RequestPermission, actualOutcome)
        assertTrue(mockSettingsRepository.eventUpdates.isEmpty())
    }

    @Test
    fun `reports a permanent denial on later enable attempts without persisting`() = runTest {
        val mockSettingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val actions: IHeaderSettingsActions = createHeaderSettingsActions(
            settingsRepository = mockSettingsRepository,
            calendarEventSource = FakeCalendarEventSource(hasPermission = false)
        )
        actions.onCalendarPermissionResult(granted = false, canAskAgain = false)
        val actualOutcome: UpcomingEventsOutcome = actions.setUpcomingEventsEnabled(enabled = true)
        assertEquals(UpcomingEventsOutcome.PermissionDenied(permanentlyDenied = true), actualOutcome)
        assertTrue(mockSettingsRepository.eventUpdates.isEmpty())
    }

    @Test
    fun `enables and persists events after a granted permission result`() = runTest {
        val mockSettingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val mockCalendarEventSource: FakeCalendarEventSource = FakeCalendarEventSource(hasPermission = false)
        mockCalendarEventSource.grantOnNextRefresh()
        val actions: IHeaderSettingsActions = createHeaderSettingsActions(
            settingsRepository = mockSettingsRepository,
            calendarEventSource = mockCalendarEventSource
        )
        val actualOutcome: UpcomingEventsOutcome = actions.onCalendarPermissionResult(granted = true, canAskAgain = true)
        assertEquals(UpcomingEventsOutcome.NoAction, actualOutcome)
        assertEquals(1, mockCalendarEventSource.refreshCount)
        assertEquals(true, mockSettingsRepository.eventUpdates.single().enabled)
    }

    @Test
    fun `reports a denial after a permission result without persisting`() = runTest {
        val mockSettingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
        val actions: IHeaderSettingsActions = createHeaderSettingsActions(
            settingsRepository = mockSettingsRepository,
            calendarEventSource = FakeCalendarEventSource(hasPermission = false)
        )
        val actualOutcome: UpcomingEventsOutcome = actions.onCalendarPermissionResult(granted = false, canAskAgain = true)
        assertEquals(UpcomingEventsOutcome.PermissionDenied(permanentlyDenied = false), actualOutcome)
        assertTrue(mockSettingsRepository.eventUpdates.isEmpty())
    }

    @Test
    fun `refreshes the calendar permission`() {
        val mockCalendarEventSource: FakeCalendarEventSource = FakeCalendarEventSource(hasPermission = false)
        val actions: IHeaderSettingsActions = createHeaderSettingsActions(
            settingsRepository = FakeWidgetSettingsRepository(),
            calendarEventSource = mockCalendarEventSource
        )
        actions.refreshCalendarPermission()
        assertEquals(1, mockCalendarEventSource.refreshCount)
    }
}
