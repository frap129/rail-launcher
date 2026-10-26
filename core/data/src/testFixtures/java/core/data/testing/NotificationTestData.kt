package core.data.testing

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import core.data.notifications.NotificationAction
import core.data.notifications.NotificationHandle
import core.data.notifications.NotificationRecord
import core.data.notifications.NotificationSnapshot

const val TEST_NOTIFICATION_PACKAGE_NAME: String = "com.example.app"
const val TEST_NOTIFICATION_KEY: String = "notification-key"
const val TEST_NOTIFICATION_OTHER_KEY: String = "other-notification"
const val TEST_NOTIFICATION_GROUP_KEY: String = "group"
const val TEST_NOTIFICATION_USER_ID: Int = 0
const val TEST_NOTIFICATION_ACTION_ACTION: String = "com.example.notification.ACTION"
const val TEST_NOTIFICATION_REPLY_ACTION: String = "com.example.notification.REPLY"
const val TEST_NOTIFICATION_ACTION_LABEL: String = "Reply"
const val TEST_NOTIFICATION_REPLY_KEY: String = "reply"
const val TEST_NOTIFICATION_REPLY_TEXT: String = "On my way"

fun createNotificationProfile(userId: Int = TEST_NOTIFICATION_USER_ID): UserHandle = UserHandle.getUserHandleForUid(userId)

fun createNotificationAction(label: String = TEST_NOTIFICATION_ACTION_LABEL, hasRemoteInput: Boolean = false): NotificationAction =
    NotificationAction(label = label, hasRemoteInput = hasRemoteInput)

fun createPlatformNotificationAction(
    context: Context,
    action: String = TEST_NOTIFICATION_ACTION_ACTION,
    withRemoteInput: Boolean = false
): Notification.Action {
    // Reply intents must be mutable so the sender can fill in the RemoteInput results.
    val intent: PendingIntent = PendingIntent.getBroadcast(context, 0, Intent(action), PendingIntent.FLAG_MUTABLE)
    val builder: Notification.Action.Builder = Notification.Action.Builder(null, TEST_NOTIFICATION_ACTION_LABEL, intent)
    if (withRemoteInput) {
        builder.addRemoteInput(RemoteInput.Builder(TEST_NOTIFICATION_REPLY_KEY).build())
    }
    return builder.build()
}

fun createNotificationRecord(
    key: String = TEST_NOTIFICATION_KEY,
    packageName: String = TEST_NOTIFICATION_PACKAGE_NAME,
    profile: UserHandle = createNotificationProfile(),
    postTime: Long = 1_000L,
    title: String = "Title",
    text: String = "Text",
    bigText: String = "",
    messages: List<String> = emptyList(),
    isClearable: Boolean = true,
    actions: List<NotificationAction> = emptyList(),
    isGroupSummary: Boolean = false,
    groupKey: String = TEST_NOTIFICATION_GROUP_KEY
): NotificationRecord = NotificationRecord(
    key = key,
    packageName = packageName,
    profile = profile,
    postTime = postTime,
    title = title,
    text = text,
    bigText = bigText,
    messages = messages,
    isClearable = isClearable,
    actions = actions,
    isGroupSummary = isGroupSummary,
    groupKey = groupKey
)

fun createNotificationHandle(
    contentIntent: PendingIntent? = null,
    isAutoCancel: Boolean = false,
    actions: List<Notification.Action> = emptyList()
): NotificationHandle = NotificationHandle(contentIntent = contentIntent, isAutoCancel = isAutoCancel, actions = actions)

fun createNotificationSnapshot(
    key: String = TEST_NOTIFICATION_KEY,
    packageName: String = TEST_NOTIFICATION_PACKAGE_NAME,
    profile: UserHandle = createNotificationProfile(),
    postTime: Long = 1_000L,
    title: String = "Title",
    body: String = "Body",
    expandedBody: String = body,
    actions: List<NotificationAction> = emptyList(),
    isClearable: Boolean = true
): NotificationSnapshot = NotificationSnapshot(
    key = key,
    packageName = packageName,
    profile = profile,
    postTime = postTime,
    title = title,
    body = body,
    expandedBody = expandedBody,
    actions = actions,
    isClearable = isClearable
)
