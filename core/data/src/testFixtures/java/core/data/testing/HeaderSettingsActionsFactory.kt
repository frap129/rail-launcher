package core.data.testing

import core.data.calendar.ICalendarEventSource
import core.data.calendar.ICalendarPermissionGateImpl
import core.data.widgets.IHeaderSettingsActions
import core.data.widgets.IHeaderSettingsActionsImpl
import core.data.widgets.IWidgetSettingsRepository

/** Builds the real Header settings actions over test doubles, as both ViewModels receive them. */
fun createHeaderSettingsActions(
    settingsRepository: IWidgetSettingsRepository,
    calendarEventSource: ICalendarEventSource = FakeCalendarEventSource()
): IHeaderSettingsActions = IHeaderSettingsActionsImpl(
    settingsRepository = settingsRepository,
    calendarPermissionGate = ICalendarPermissionGateImpl(calendarEventSource = calendarEventSource)
)
