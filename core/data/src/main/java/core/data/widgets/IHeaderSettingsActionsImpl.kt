package core.data.widgets

import core.data.calendar.CalendarPermissionDecision
import core.data.calendar.ICalendarPermissionGate
import core.data.calendar.UpcomingEventsOutcome
import core.data.prefs.BatteryVisibility
import core.data.prefs.ClockStyle

class IHeaderSettingsActionsImpl(
    private val settingsRepository: IWidgetSettingsRepository,
    private val calendarPermissionGate: ICalendarPermissionGate
) : IHeaderSettingsActions {
    override suspend fun setClockVisible(visible: Boolean) {
        settingsRepository.setClockVisible(visible = visible)
    }

    override suspend fun setDateVisible(visible: Boolean) {
        settingsRepository.setDateVisible(visible = visible)
    }

    override suspend fun setBatteryVisibility(visibility: BatteryVisibility) {
        settingsRepository.setBatteryVisibility(visibility = visibility)
    }

    override suspend fun setClockStyle(style: ClockStyle) {
        settingsRepository.updateHeaderSettings { settings -> settings.copy(clockStyle = style) }
    }

    override suspend fun setUpcomingEventsEnabled(enabled: Boolean): UpcomingEventsOutcome {
        if (!enabled) {
            settingsRepository.setEventsEnabled(enabled = false)
            return UpcomingEventsOutcome.NoAction
        }
        return calendarPermissionGate.decideEnable().toUpcomingEventsOutcome()
    }

    override suspend fun onCalendarPermissionResult(granted: Boolean, canAskAgain: Boolean): UpcomingEventsOutcome =
        calendarPermissionGate.onPermissionResult(granted = granted, canAskAgain = canAskAgain).toUpcomingEventsOutcome()

    override fun refreshCalendarPermission() {
        calendarPermissionGate.refreshPermission()
    }

    private suspend fun CalendarPermissionDecision.toUpcomingEventsOutcome(): UpcomingEventsOutcome {
        persistEnabledEvents(decision = this)
        return when (this) {
            CalendarPermissionDecision.Enable -> UpcomingEventsOutcome.NoAction
            CalendarPermissionDecision.Request -> UpcomingEventsOutcome.RequestPermission
            is CalendarPermissionDecision.Denied -> UpcomingEventsOutcome.PermissionDenied(permanentlyDenied = permanentlyDenied)
        }
    }

    private suspend fun persistEnabledEvents(decision: CalendarPermissionDecision) {
        if (decision == CalendarPermissionDecision.Enable) {
            settingsRepository.setEventsEnabled(enabled = true)
        }
    }
}
