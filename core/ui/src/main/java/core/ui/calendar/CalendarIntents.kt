package core.ui.calendar

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import core.ui.R
import timber.log.Timber

/** Opens the calendar App at [atEpochMillis]. */
fun Context.openSystemCalendar(atEpochMillis: Long) {
    openSystemIntent(
        intent = Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/$atEpochMillis")),
        targetDescription = "calendar at $atEpochMillis",
        messageResource = R.string.no_calendar_app
    )
}

/** Opens [target] in the calendar App, through the work profile when the event belongs to one. */
fun Context.openCalendarEvent(target: OpenEventTarget) {
    if (target.isWorkProfile) {
        try {
            val opened: Boolean = CalendarContract.startViewCalendarEventInManagedProfile(
                this,
                target.eventId,
                target.beginMillis,
                target.endMillis,
                false,
                0
            )
            if (!opened) {
                Toast.makeText(this, R.string.no_calendar_app, Toast.LENGTH_SHORT).show()
            }
        } catch (exception: SecurityException) {
            Toast.makeText(this, R.string.no_calendar_app, Toast.LENGTH_SHORT).show()
            Timber.w(exception, "Could not open work-profile event %s", target.eventId)
        }
        return
    }
    val intent: Intent = Intent(Intent.ACTION_VIEW)
        .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, target.eventId))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, target.beginMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, target.endMillis)
    openSystemIntent(intent = intent, targetDescription = target.toString(), messageResource = R.string.no_calendar_app)
}

internal fun Context.openSystemIntent(intent: Intent, targetDescription: String, messageResource: Int) {
    try {
        startActivity(intent)
    } catch (exception: ActivityNotFoundException) {
        Toast.makeText(this, messageResource, Toast.LENGTH_SHORT).show()
        Timber.w(exception, "No activity for %s", targetDescription)
    }
}
