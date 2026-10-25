package core.data.notifications

/** One button a notification offers, and whether it accepts an inline reply. */
data class NotificationAction(val label: String, val hasRemoteInput: Boolean)
