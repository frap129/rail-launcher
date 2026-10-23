package core.data.calendar

sealed interface CalendarPermissionDecision {
    data object Enable : CalendarPermissionDecision

    data object Request : CalendarPermissionDecision

    data class Denied(val permanentlyDenied: Boolean) : CalendarPermissionDecision
}

/** Decides whether Upcoming events can be enabled, need a permission request, or stay blocked. */
interface ICalendarPermissionGate {
    fun decideEnable(): CalendarPermissionDecision

    fun onPermissionResult(granted: Boolean, canAskAgain: Boolean): CalendarPermissionDecision

    fun refreshPermission()
}
