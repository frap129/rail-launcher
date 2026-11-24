package core.ui.composables

import androidx.compose.runtime.Composable
import core.data.calendar.ICalendarPermissionGate

/** Refreshes calendar access on every resume, so a grant made outside Rail is picked up. */
@Composable
fun CalendarPermissionResumeRefresh(calendarPermissionGate: ICalendarPermissionGate) {
    OnResume {
        calendarPermissionGate.refreshPermission()
    }
}
