package core.data.notifications

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

/** Reads the platform notification into the source-agnostic record and handle. */
fun StatusBarNotification.toNotificationRecord(): NotificationRecord {
    val extras: Bundle = notification.extras
    val messages: List<String> = NotificationCompat.MessagingStyle
        .extractMessagingStyleFromNotification(notification)
        ?.messages
        ?.mapNotNull { message -> message.text?.toString()?.takeIf { text -> text.isNotBlank() } }
        .orEmpty()
    return NotificationRecord(
        key = key,
        packageName = packageName,
        profile = user,
        postTime = postTime,
        title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
        text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
        bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty(),
        messages = messages,
        isClearable = isClearable,
        actions = notification.actions.orEmpty().map { action -> action.toNotificationAction() },
        isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        groupKey = groupKey
    )
}

fun StatusBarNotification.toNotificationHandle(): NotificationHandle = NotificationHandle(
    contentIntent = notification.contentIntent,
    isAutoCancel = notification.flags and Notification.FLAG_AUTO_CANCEL != 0,
    actions = notification.actions.orEmpty().toList()
)

private fun Notification.Action.toNotificationAction(): NotificationAction = NotificationAction(
    label = title?.toString().orEmpty(),
    hasRemoteInput = remoteInputs?.isNotEmpty() == true
)
