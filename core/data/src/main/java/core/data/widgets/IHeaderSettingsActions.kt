package core.data.widgets

import core.data.calendar.UpcomingEventsOutcome
import core.data.prefs.BatteryVisibility
import core.data.prefs.ClockStyle

/** Applies the Header setting changes shared by the Home menus and the Widgets settings screen. */
interface IHeaderSettingsActions {
    suspend fun setClockVisible(visible: Boolean)

    suspend fun setDateVisible(visible: Boolean)

    suspend fun setBatteryVisibility(visibility: BatteryVisibility)

    suspend fun setClockStyle(style: ClockStyle)

    /** Persists Upcoming events as disabled when [enabled] is false, otherwise asks the permission gate first. */
    suspend fun setUpcomingEventsEnabled(enabled: Boolean): UpcomingEventsOutcome

    /** Persists Upcoming events on a grant and reports a denial for the UI to show. */
    suspend fun onCalendarPermissionResult(granted: Boolean, canAskAgain: Boolean): UpcomingEventsOutcome

    fun refreshCalendarPermission()
}
