package core.data.notifications

import android.app.PendingIntent

/** Android-only side of a notification that must never reach a snapshot. */
data class NotificationHandle(val contentIntent: PendingIntent?, val isAutoCancel: Boolean)
