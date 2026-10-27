package feature.launcher

import core.data.notifications.NotificationAction
import org.junit.Assert.assertEquals
import org.junit.Test

private const val FIRST_LABEL: String = "Reply"
private const val SECOND_LABEL: String = "Archive"
private const val THIRD_LABEL: String = "Mark as read"
private const val FOURTH_LABEL: String = "Second reply"

class NotificationActionLayoutTest {
    @Test
    fun `uses the first reply action for the reply field`() {
        val inputActions: List<NotificationAction> = listOf(
            NotificationAction(label = FIRST_LABEL, hasRemoteInput = true),
            NotificationAction(label = SECOND_LABEL, hasRemoteInput = false)
        )

        val actualLayout: NotificationActionLayout = notificationActionLayout(actions = inputActions)

        assertEquals(0, actualLayout.replyIndex)
    }

    @Test
    fun `keeps the other actions as buttons in order`() {
        val inputActions: List<NotificationAction> = listOf(
            NotificationAction(label = FIRST_LABEL, hasRemoteInput = true),
            NotificationAction(label = SECOND_LABEL, hasRemoteInput = false),
            NotificationAction(label = THIRD_LABEL, hasRemoteInput = false)
        )

        val actualLayout: NotificationActionLayout = notificationActionLayout(actions = inputActions)

        assertEquals(listOf(SECOND_LABEL, THIRD_LABEL), actualLayout.buttons.map { indexed -> indexed.value.label })
    }

    @Test
    fun `keeps a second reply action as a button`() {
        val inputActions: List<NotificationAction> = listOf(
            NotificationAction(label = FIRST_LABEL, hasRemoteInput = true),
            NotificationAction(label = FOURTH_LABEL, hasRemoteInput = true)
        )

        val actualLayout: NotificationActionLayout = notificationActionLayout(actions = inputActions)

        assertEquals(0, actualLayout.replyIndex)
        assertEquals(listOf(FOURTH_LABEL), actualLayout.buttons.map { indexed -> indexed.value.label })
    }

    @Test
    fun `keeps the original action index for every button`() {
        val inputActions: List<NotificationAction> = listOf(
            NotificationAction(label = FIRST_LABEL, hasRemoteInput = true),
            NotificationAction(label = SECOND_LABEL, hasRemoteInput = false),
            NotificationAction(label = THIRD_LABEL, hasRemoteInput = false)
        )

        val actualLayout: NotificationActionLayout = notificationActionLayout(actions = inputActions)

        assertEquals(listOf(1, 2), actualLayout.buttons.map { indexed -> indexed.index })
    }

    @Test
    fun `without a reply action every action stays a button`() {
        val inputActions: List<NotificationAction> = listOf(
            NotificationAction(label = SECOND_LABEL, hasRemoteInput = false),
            NotificationAction(label = THIRD_LABEL, hasRemoteInput = false)
        )

        val actualLayout: NotificationActionLayout = notificationActionLayout(actions = inputActions)

        assertEquals(-1, actualLayout.replyIndex)
        assertEquals(listOf(0, 1), actualLayout.buttons.map { indexed -> indexed.index })
    }
}
