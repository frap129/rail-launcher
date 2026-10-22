package core.data.calendar

import core.data.calendar.ICalendarEventSource

sealed interface CalendarPermissionDecision {
    data object Enable : CalendarPermissionDecision

    data object Request : CalendarPermissionDecision

    data class Denied(val permanentlyDenied: Boolean) : CalendarPermissionDecision
}

class CalendarPermissionGate(private val calendarEventSource: ICalendarEventSource) {
    private var permanentlyDenied: Boolean = false

    fun decideEnable(): CalendarPermissionDecision = when {
        calendarEventSource.hasPermission.value -> CalendarPermissionDecision.Enable
        permanentlyDenied -> CalendarPermissionDecision.Denied(permanentlyDenied = true)
        else -> CalendarPermissionDecision.Request
    }

    fun onPermissionResult(granted: Boolean, canAskAgain: Boolean): CalendarPermissionDecision {
        calendarEventSource.refreshPermission()
        permanentlyDenied = !granted && !canAskAgain
        return if (granted) {
            CalendarPermissionDecision.Enable
        } else {
            CalendarPermissionDecision.Denied(permanentlyDenied = !canAskAgain)
        }
    }

    fun refreshPermission() {
        calendarEventSource.refreshPermission()
    }
}
