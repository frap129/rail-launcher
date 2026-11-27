package core.ui.calendar

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import core.ui.R
import core.util.attemptLaunch
import core.util.tryStartActivity

/** Opens the calendar App at [atEpochMillis]. */
fun Context.openSystemCalendar(atEpochMillis: Long) {
    openSystemIntent(
        intent = Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/$atEpochMillis")),
        messageResource = R.string.no_calendar_app
    )
}

/** Opens [target] in the calendar App, through the work profile when the event belongs to one. */
fun Context.openCalendarEvent(target: OpenEventTarget) {
    if (target.isWorkProfile) {
        var didOpen: Boolean = false
        val didComplete: Boolean = attemptLaunch {
            didOpen = CalendarContract.startViewCalendarEventInManagedProfile(
                this,
                target.eventId,
                target.beginMillis,
                target.endMillis,
                false,
                0
            )
        }
        if (!didComplete || !didOpen) {
            showNoAppToast(R.string.no_calendar_app)
        }
        return
    }
    val intent: Intent = Intent(Intent.ACTION_VIEW)
        .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, target.eventId))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, target.beginMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, target.endMillis)
    openSystemIntent(intent = intent, messageResource = R.string.no_calendar_app)
}

internal fun Context.openSystemIntent(intent: Intent, messageResource: Int) {
    if (!tryStartActivity(intent)) {
        showNoAppToast(messageResource)
    }
}

private fun Context.showNoAppToast(messageResource: Int) {
    Toast.makeText(this, messageResource, Toast.LENGTH_SHORT).show()
}
