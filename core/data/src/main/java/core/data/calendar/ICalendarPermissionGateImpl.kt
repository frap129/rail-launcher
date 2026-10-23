package core.data.calendar

/** Memoizes a permanent denial until the system reports that permission is held again. */
class ICalendarPermissionGateImpl(private val calendarEventSource: ICalendarEventSource) : ICalendarPermissionGate {
    private var permanentlyDenied: Boolean = false

    override fun decideEnable(): CalendarPermissionDecision = when {
        calendarEventSource.hasPermission.value -> CalendarPermissionDecision.Enable
        permanentlyDenied -> CalendarPermissionDecision.Denied(permanentlyDenied = true)
        else -> CalendarPermissionDecision.Request
    }

    override fun onPermissionResult(granted: Boolean, canAskAgain: Boolean): CalendarPermissionDecision {
        calendarEventSource.refreshPermission()
        permanentlyDenied = !granted && !canAskAgain
        return if (granted) {
            CalendarPermissionDecision.Enable
        } else {
            CalendarPermissionDecision.Denied(permanentlyDenied = !canAskAgain)
        }
    }

    override fun refreshPermission() {
        calendarEventSource.refreshPermission()
        if (calendarEventSource.hasPermission.value) {
            permanentlyDenied = false
        }
    }
}
