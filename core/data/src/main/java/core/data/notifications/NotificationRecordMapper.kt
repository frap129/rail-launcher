package core.data.notifications

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

/** Reads the platform notification into the source-agnostic record and handle. */
fun StatusBarNotification.toNotificationRecord(): NotificationRecord {
    val extras: Bundle = notification.extras
    val messagingText: String = NotificationCompat.MessagingStyle
        .extractMessagingStyleFromNotification(notification)
        ?.messages
        ?.lastOrNull()
        ?.text
        ?.toString()
        .orEmpty()
    return NotificationRecord(
        key = key,
        packageName = packageName,
        profile = user,
        postTime = postTime,
        title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
        text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
        bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty(),
        lastMessage = messagingText,
        isClearable = isClearable,
        hasActions = notification.actions?.isNotEmpty() == true,
        isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        groupKey = groupKey
    )
}

fun StatusBarNotification.toNotificationHandle(): NotificationHandle = NotificationHandle(
    contentIntent = notification.contentIntent,
    isAutoCancel = notification.flags and Notification.FLAG_AUTO_CANCEL != 0
)
