package core.data.testing

import android.app.PendingIntent
import android.os.UserHandle
import core.data.notifications.NotificationHandle
import core.data.notifications.NotificationRecord
import core.data.notifications.NotificationSnapshot

const val TEST_NOTIFICATION_PACKAGE_NAME: String = "com.example.app"
const val TEST_NOTIFICATION_KEY: String = "notification-key"
const val TEST_NOTIFICATION_GROUP_KEY: String = "group"
const val TEST_NOTIFICATION_USER_ID: Int = 0

fun createNotificationProfile(userId: Int = TEST_NOTIFICATION_USER_ID): UserHandle = UserHandle.getUserHandleForUid(userId)

fun createNotificationRecord(
    key: String = TEST_NOTIFICATION_KEY,
    packageName: String = TEST_NOTIFICATION_PACKAGE_NAME,
    profile: UserHandle = createNotificationProfile(),
    postTime: Long = 1_000L,
    title: String = "Title",
    text: String = "Text",
    bigText: String = "",
    lastMessage: String = "",
    isClearable: Boolean = true,
    hasActions: Boolean = false,
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
    lastMessage = lastMessage,
    isClearable = isClearable,
    hasActions = hasActions,
    isGroupSummary = isGroupSummary,
    groupKey = groupKey
)

fun createNotificationHandle(contentIntent: PendingIntent? = null, isAutoCancel: Boolean = false): NotificationHandle =
    NotificationHandle(contentIntent = contentIntent, isAutoCancel = isAutoCancel)

fun createNotificationSnapshot(
    key: String = TEST_NOTIFICATION_KEY,
    packageName: String = TEST_NOTIFICATION_PACKAGE_NAME,
    profile: UserHandle = createNotificationProfile(),
    postTime: Long = 1_000L,
    title: String = "Title",
    body: String = "Body",
    isClearable: Boolean = true,
    hasBigText: Boolean = false,
    hasActions: Boolean = false
): NotificationSnapshot = NotificationSnapshot(
    key = key,
    packageName = packageName,
    profile = profile,
    postTime = postTime,
    title = title,
    body = body,
    isClearable = isClearable,
    hasBigText = hasBigText,
    hasActions = hasActions
)
