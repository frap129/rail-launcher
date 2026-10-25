package core.data.notifications

import android.os.UserHandle

/** The string fields and flags of one notification, independent of the platform object. */
data class NotificationRecord(
    val key: String,
    val packageName: String,
    val profile: UserHandle,
    val postTime: Long,
    val title: String,
    val text: String,
    val bigText: String,
    val lastMessage: String,
    val isClearable: Boolean,
    val hasActions: Boolean,
    val isGroupSummary: Boolean,
    val groupKey: String?
)
