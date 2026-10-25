package core.data.notifications

import core.data.apps.model.AppProfile
import core.data.testing.TEST_NOTIFICATION_ACTION_LABEL
import core.data.testing.TEST_NOTIFICATION_KEY
import core.data.testing.TEST_NOTIFICATION_PACKAGE_NAME
import core.data.testing.TEST_SDK
import core.data.testing.createNotificationAction
import core.data.testing.createNotificationProfile
import core.data.testing.createNotificationRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val OWN_PACKAGE_NAME: String = "dev.maples.rail"
private const val TITLE: String = "Title"
private const val TEXT: String = "Text"
private const val BIG_TEXT: String = "Long text"
private const val MESSAGE: String = "Latest message"
private const val EARLIER_MESSAGE: String = "Earlier message"

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class NotificationTextMapperTest {
    @Test
    fun `uses the text as the body`() {
        val actualSnapshots: List<NotificationSnapshot> = NotificationTextMapper.snapshots(
            records = listOf(createNotificationRecord(text = TEXT, bigText = BIG_TEXT, messages = listOf(MESSAGE))),
            ownPackageName = OWN_PACKAGE_NAME
        )
        assertEquals(TEXT, actualSnapshots.single().body)
    }

    @Test
    fun `falls back to the big text when the text is blank`() {
        val actualSnapshots: List<NotificationSnapshot> = NotificationTextMapper.snapshots(
            records = listOf(createNotificationRecord(text = "", bigText = BIG_TEXT)),
            ownPackageName = OWN_PACKAGE_NAME
        )
        assertEquals(BIG_TEXT, actualSnapshots.single().body)
        assertEquals(BIG_TEXT, actualSnapshots.single().expandedBody)
    }

    @Test
    fun `blank text with big text is expandable`() {
        val actualSnapshot: NotificationSnapshot = NotificationTextMapper.snapshots(
            records = listOf(createNotificationRecord(text = "", bigText = BIG_TEXT)),
            ownPackageName = OWN_PACKAGE_NAME
        ).single()
        assertEquals(BIG_TEXT, actualSnapshot.body)
        assertTrue(actualSnapshot.isExpandable)
    }

    @Test
    fun `a notification with a body is expandable`() {
        val actualSnapshot: NotificationSnapshot = NotificationTextMapper.snapshots(
            records = listOf(createNotificationRecord()),
            ownPackageName = OWN_PACKAGE_NAME
        ).single()
        assertEquals(TEXT, actualSnapshot.expandedBody)
        assertTrue(actualSnapshot.isExpandable)
    }

    @Test
    fun `a title-only notification is not expandable`() {
        val actualSnapshot: NotificationSnapshot = NotificationTextMapper.snapshots(
            records = listOf(createNotificationRecord(text = "", bigText = "", messages = emptyList())),
            ownPackageName = OWN_PACKAGE_NAME
        ).single()
        assertEquals("", actualSnapshot.expandedBody)
        assertFalse(actualSnapshot.isExpandable)
    }

    @Test
    fun `falls back to the last messaging text when the text and big text are blank`() {
        val actualSnapshots: List<NotificationSnapshot> = NotificationTextMapper.snapshots(
            records = listOf(createNotificationRecord(text = "", bigText = "", messages = listOf(MESSAGE))),
            ownPackageName = OWN_PACKAGE_NAME
        )
        assertEquals(MESSAGE, actualSnapshots.single().body)
    }

    @Test
    fun `uses the big text as the expanded body`() {
        val actualSnapshot: NotificationSnapshot = NotificationTextMapper.snapshots(
            records = listOf(createNotificationRecord(text = TEXT, bigText = BIG_TEXT)),
            ownPackageName = OWN_PACKAGE_NAME
        ).single()
        assertEquals(BIG_TEXT, actualSnapshot.expandedBody)
        assertTrue(actualSnapshot.isExpandable)
    }

    @Test
    fun `joins the MessagingStyle conversation as the expanded body`() {
        val actualSnapshot: NotificationSnapshot = NotificationTextMapper.snapshots(
            records = listOf(
                createNotificationRecord(
                    text = MESSAGE,
                    messages = listOf(EARLIER_MESSAGE, MESSAGE)
                )
            ),
            ownPackageName = OWN_PACKAGE_NAME
        ).single()
        assertEquals(MESSAGE, actualSnapshot.body)
        assertEquals("$EARLIER_MESSAGE\n$MESSAGE", actualSnapshot.expandedBody)
        assertTrue(actualSnapshot.isExpandable)
    }

    @Test
    fun `carries the actions and detects their reply inputs`() {
        val inputActions: List<NotificationAction> = listOf(
            createNotificationAction(label = TEST_NOTIFICATION_ACTION_LABEL, hasRemoteInput = true),
            createNotificationAction(label = "Archive")
        )
        val actualSnapshot: NotificationSnapshot = NotificationTextMapper.snapshots(
            records = listOf(createNotificationRecord(actions = inputActions)),
            ownPackageName = OWN_PACKAGE_NAME
        ).single()
        assertEquals(inputActions, actualSnapshot.actions)
        assertTrue(actualSnapshot.actions.single { action -> action.label == TEST_NOTIFICATION_ACTION_LABEL }.hasRemoteInput)
        assertFalse(actualSnapshot.actions.single { action -> action.label == "Archive" }.hasRemoteInput)
    }

    @Test
    fun `a notification with only actions is expandable`() {
        val actualSnapshot: NotificationSnapshot = NotificationTextMapper.snapshots(
            records = listOf(createNotificationRecord(actions = listOf(createNotificationAction()))),
            ownPackageName = OWN_PACKAGE_NAME
        ).single()
        assertTrue(actualSnapshot.isExpandable)
    }

    @Test
    fun `drops a notification with no title and no body`() {
        val actualSnapshots: List<NotificationSnapshot> = NotificationTextMapper.snapshots(
            records = listOf(createNotificationRecord(title = "", text = "", bigText = "", messages = emptyList())),
            ownPackageName = OWN_PACKAGE_NAME
        )
        assertEquals(emptyList<NotificationSnapshot>(), actualSnapshots)
    }

    @Test
    fun `keeps a notification with only a title`() {
        val actualSnapshots: List<NotificationSnapshot> = NotificationTextMapper.snapshots(
            records = listOf(createNotificationRecord(text = "", bigText = "", messages = emptyList())),
            ownPackageName = OWN_PACKAGE_NAME
        )
        assertEquals(TITLE, actualSnapshots.single().title)
        assertEquals("", actualSnapshots.single().body)
    }

    @Test
    fun `skips Rail's own notifications`() {
        val actualSnapshots: List<NotificationSnapshot> = NotificationTextMapper.snapshots(
            records = listOf(createNotificationRecord(packageName = OWN_PACKAGE_NAME)),
            ownPackageName = OWN_PACKAGE_NAME
        )
        assertEquals(emptyList<NotificationSnapshot>(), actualSnapshots)
    }

    @Test
    fun `skips a group summary whose child is visible`() {
        val actualSnapshots: List<NotificationSnapshot> = NotificationTextMapper.snapshots(
            records = listOf(
                createNotificationRecord(key = "summary", isGroupSummary = true),
                createNotificationRecord(key = "child")
            ),
            ownPackageName = OWN_PACKAGE_NAME
        )
        assertEquals(listOf("child"), actualSnapshots.map { snapshot -> snapshot.key })
    }

    @Test
    fun `keeps a group summary with no visible child`() {
        val actualSnapshots: List<NotificationSnapshot> = NotificationTextMapper.snapshots(
            records = listOf(
                createNotificationRecord(key = "summary", isGroupSummary = true),
                createNotificationRecord(key = "child", title = "", text = "", bigText = "", messages = emptyList())
            ),
            ownPackageName = OWN_PACKAGE_NAME
        )
        assertEquals(listOf("summary"), actualSnapshots.map { snapshot -> snapshot.key })
    }

    @Test
    fun `orders notifications newest first`() {
        val actualSnapshots: List<NotificationSnapshot> = NotificationTextMapper.snapshots(
            records = listOf(
                createNotificationRecord(key = "older", postTime = 1_000L),
                createNotificationRecord(key = "newer", postTime = 2_000L)
            ),
            ownPackageName = OWN_PACKAGE_NAME
        )
        assertEquals(listOf("newer", "older"), actualSnapshots.map { snapshot -> snapshot.key })
    }

    @Test
    fun `maps the package and profile to the owning App`() {
        val actualSnapshot: NotificationSnapshot = NotificationTextMapper.snapshots(
            records = listOf(createNotificationRecord()),
            ownPackageName = OWN_PACKAGE_NAME
        ).single()
        assertEquals(AppProfile(packageName = TEST_NOTIFICATION_PACKAGE_NAME, profile = createNotificationProfile()), actualSnapshot.owner)
    }

    @Test
    fun `carries the clearable flag`() {
        val actualSnapshot: NotificationSnapshot = NotificationTextMapper.snapshots(
            records = listOf(createNotificationRecord(isClearable = false, bigText = BIG_TEXT)),
            ownPackageName = OWN_PACKAGE_NAME
        ).single()
        assertEquals(TEST_NOTIFICATION_KEY, actualSnapshot.key)
        assertFalse(actualSnapshot.isClearable)
    }
}
