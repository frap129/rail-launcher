package core.popups

import core.data.notifications.NotificationSnapshot

/** One notification row in a Pop-up, in its collapsed form. */
data class NotificationRow(val snapshot: NotificationSnapshot, val isExpandable: Boolean)
