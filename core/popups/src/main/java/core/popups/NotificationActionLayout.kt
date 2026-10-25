package core.popups

import core.data.notifications.NotificationAction

/** The action the inline reply field uses and the remaining action buttons, original indices kept. */
data class NotificationActionLayout(val replyIndex: Int, val buttons: List<IndexedValue<NotificationAction>>)

/**
 * Splits [actions] into the first reply action and the buttons. Every other action, including
 * a second reply action, stays a button with its original index for `runAction`.
 */
fun notificationActionLayout(actions: List<NotificationAction>): NotificationActionLayout {
    val replyIndex: Int = actions.indexOfFirst { action -> action.hasRemoteInput }
    return NotificationActionLayout(
        replyIndex = replyIndex,
        buttons = actions.withIndex().filter { indexed -> indexed.index != replyIndex }
    )
}
