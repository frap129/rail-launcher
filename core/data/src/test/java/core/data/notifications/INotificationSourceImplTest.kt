package core.data.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.cash.turbine.test
import core.data.media.RailNotificationListener
import core.data.testing.FakePopupRepository
import core.data.testing.TEST_NOTIFICATION_ACTION_ACTION
import core.data.testing.TEST_NOTIFICATION_KEY
import core.data.testing.TEST_NOTIFICATION_PACKAGE_NAME
import core.data.testing.TEST_NOTIFICATION_REPLY_ACTION
import core.data.testing.TEST_NOTIFICATION_REPLY_KEY
import core.data.testing.TEST_NOTIFICATION_REPLY_TEXT
import core.data.testing.TEST_SDK
import core.data.testing.createNotificationHandle
import core.data.testing.createNotificationProfile
import core.data.testing.createNotificationRecord
import core.data.testing.createPlatformNotificationAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val CONTENT_INTENT_ACTION: String = "com.example.CONTENT"
private const val SERVICE_NOTIFICATION_TITLE: String = "Service title"

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class INotificationSourceImplTest {
    private val mockRelay: INotificationListenerRelayImpl = INotificationListenerRelayImpl()

    @Test
    fun `yields nothing while the switch is off`() = runTest {
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        val source: INotificationSourceImpl = createSource(mockPopupRepository = mockPopupRepository, hasAccess = true)
        mockRelay.post(record = createNotificationRecord(), handle = createNotificationHandle())

        source.notifications.test {
            assertEquals(emptyList<NotificationSnapshot>(), awaitItem())
        }
    }

    @Test
    fun `does not retain notifications without a subscriber`() = runTest {
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        mockPopupRepository.setNotificationsEnabled(true)
        createSource(
            mockPopupRepository = mockPopupRepository,
            hasAccess = true,
            observe = false
        )

        mockRelay.post(record = createNotificationRecord(), handle = createNotificationHandle())

        assertTrue(mockRelay.records.value.isEmpty())
        assertNull(mockRelay.record(key = TEST_NOTIFICATION_KEY))
    }

    @Test
    fun `clears the map when the last subscriber leaves`() = runTest {
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        mockPopupRepository.setNotificationsEnabled(true)
        val source: INotificationSourceImpl = createSource(
            mockPopupRepository = mockPopupRepository,
            hasAccess = true,
            observe = false
        )

        source.notifications.test {
            runCurrent()
            expectMostRecentItem()
            mockRelay.post(record = createNotificationRecord(), handle = createNotificationHandle())
            runCurrent()
            assertEquals(1, expectMostRecentItem().size)
        }

        assertTrue(mockRelay.records.value.isEmpty())
    }

    @Test
    fun `rebuilds the notification map from the service on subscribe`() = runTest {
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        mockPopupRepository.setNotificationsEnabled(true)
        val mockService: NotificationListenerService = Robolectric.buildService(RailNotificationListener::class.java).create().get()
        shadowOf(mockService).addActiveNotification(createStatusBarNotification())
        mockRelay.attach(mockService)
        val source: INotificationSourceImpl = createSource(
            mockPopupRepository = mockPopupRepository,
            hasAccess = true,
            observe = false
        )
        assertTrue(mockRelay.records.value.isEmpty())

        source.notifications.test {
            runCurrent()
            assertEquals(listOf(SERVICE_NOTIFICATION_TITLE), expectMostRecentItem().map { snapshot -> snapshot.title })
        }
    }

    @Test
    fun `opening sends the notification's content intent`() = runTest {
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        mockPopupRepository.setNotificationsEnabled(true)
        val context = RuntimeEnvironment.getApplication()
        val contentIntent: PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(CONTENT_INTENT_ACTION),
            PendingIntent.FLAG_IMMUTABLE
        )
        val source: INotificationSourceImpl = createSource(mockPopupRepository = mockPopupRepository, hasAccess = true)
        mockRelay.post(
            record = createNotificationRecord(),
            handle = createNotificationHandle(contentIntent = contentIntent, isAutoCancel = false)
        )

        source.open(key = TEST_NOTIFICATION_KEY)

        assertTrue(shadowOf(context).broadcastIntents.any { intent -> intent.action == CONTENT_INTENT_ACTION })
    }

    @Test
    fun `drops notifications that arrive while the switch is off`() = runTest {
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        val source: INotificationSourceImpl = createSource(mockPopupRepository = mockPopupRepository, hasAccess = true)

        mockRelay.post(record = createNotificationRecord(), handle = createNotificationHandle())

        assertTrue(mockRelay.records.value.isEmpty())
        assertTrue(source.notifications.first().isEmpty())
    }

    @Test
    fun `yields nothing without notification access`() = runTest {
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        mockPopupRepository.setNotificationsEnabled(true)
        val source: INotificationSourceImpl = createSource(mockPopupRepository = mockPopupRepository, hasAccess = false)
        mockRelay.post(record = createNotificationRecord(), handle = createNotificationHandle())

        source.notifications.test {
            assertEquals(emptyList<NotificationSnapshot>(), awaitItem())
        }
        assertFalse(source.hasAccess.value)
    }

    @Test
    fun `yields the owner's notifications with the switch on and access granted`() = runTest {
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        mockPopupRepository.setNotificationsEnabled(true)
        val source: INotificationSourceImpl = createSource(mockPopupRepository = mockPopupRepository, hasAccess = true)
        mockRelay.post(record = createNotificationRecord(title = "Hello"), handle = createNotificationHandle())

        source.notifications.test {
            runCurrent()
            assertEquals(listOf("Hello"), expectMostRecentItem().map { snapshot -> snapshot.title })
        }
    }

    @Test
    fun `clears the notifications when the switch turns off`() = runTest {
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        mockPopupRepository.setNotificationsEnabled(true)
        val source: INotificationSourceImpl = createSource(mockPopupRepository = mockPopupRepository, hasAccess = true)
        mockRelay.post(record = createNotificationRecord(), handle = createNotificationHandle())

        source.notifications.test {
            runCurrent()
            assertEquals(1, expectMostRecentItem().size)
            mockPopupRepository.setNotificationsEnabled(false)
            assertEquals(emptyList<NotificationSnapshot>(), awaitItem())
            assertTrue(mockRelay.records.value.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clears the notifications when access is revoked`() = runTest {
        var inputAccess: Boolean = true
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        mockPopupRepository.setNotificationsEnabled(true)
        val source: INotificationSourceImpl = createSource(
            mockPopupRepository = mockPopupRepository,
            hasAccess = true,
            accessCheck = { inputAccess }
        )
        mockRelay.post(record = createNotificationRecord(), handle = createNotificationHandle())

        source.notifications.test {
            runCurrent()
            assertEquals(1, expectMostRecentItem().size)
            inputAccess = false
            source.refreshAccess()
            assertEquals(emptyList<NotificationSnapshot>(), awaitItem())
            assertFalse(source.hasAccess.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismiss removes a clearable notification`() = runTest {
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        mockPopupRepository.setNotificationsEnabled(true)
        val source: INotificationSourceImpl = createSource(mockPopupRepository = mockPopupRepository, hasAccess = true)
        mockRelay.post(record = createNotificationRecord(), handle = createNotificationHandle())
        source.notifications.test {
            runCurrent()
            assertEquals(1, expectMostRecentItem().size)
            source.dismiss(key = TEST_NOTIFICATION_KEY)
            assertEquals(emptyList<NotificationSnapshot>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(mockRelay.record(key = TEST_NOTIFICATION_KEY))
    }

    @Test
    fun `an ongoing notification cannot be dismissed`() = runTest {
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        mockPopupRepository.setNotificationsEnabled(true)
        val source: INotificationSourceImpl = createSource(mockPopupRepository = mockPopupRepository, hasAccess = true)
        mockRelay.post(record = createNotificationRecord(isClearable = false), handle = createNotificationHandle())

        source.dismiss(key = TEST_NOTIFICATION_KEY)

        assertEquals(TEST_NOTIFICATION_KEY, mockRelay.record(key = TEST_NOTIFICATION_KEY)?.key)
    }

    @Test
    fun `opening an auto-cancel notification removes it`() = runTest {
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        mockPopupRepository.setNotificationsEnabled(true)
        val source: INotificationSourceImpl = createSource(mockPopupRepository = mockPopupRepository, hasAccess = true)
        mockRelay.post(record = createNotificationRecord(), handle = createNotificationHandle(isAutoCancel = true))

        source.open(key = TEST_NOTIFICATION_KEY)

        assertNull(mockRelay.record(key = TEST_NOTIFICATION_KEY))
    }

    @Test
    fun `opening a notification without auto-cancel keeps it`() = runTest {
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        mockPopupRepository.setNotificationsEnabled(true)
        val source: INotificationSourceImpl = createSource(mockPopupRepository = mockPopupRepository, hasAccess = true)
        mockRelay.post(record = createNotificationRecord(), handle = createNotificationHandle(isAutoCancel = false))
        runCurrent()

        source.open(key = TEST_NOTIFICATION_KEY)

        assertEquals(TEST_NOTIFICATION_KEY, mockRelay.record(key = TEST_NOTIFICATION_KEY)?.key)
    }

    @Test
    fun `running an action sends its intent`() = runTest {
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        mockPopupRepository.setNotificationsEnabled(true)
        val context = RuntimeEnvironment.getApplication()
        val source: INotificationSourceImpl = createSource(mockPopupRepository = mockPopupRepository, hasAccess = true)
        mockRelay.post(
            record = createNotificationRecord(),
            handle = createNotificationHandle(actions = listOf(createPlatformNotificationAction(context = context)))
        )

        val didRun: Boolean = source.runAction(key = TEST_NOTIFICATION_KEY, index = 0)

        assertTrue(didRun)
        assertTrue(
            shadowOf(context).broadcastIntents.any { intent -> intent.action == TEST_NOTIFICATION_ACTION_ACTION }
        )
    }

    @Test
    fun `running a reply action delivers the reply text`() = runTest {
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        mockPopupRepository.setNotificationsEnabled(true)
        val context = RuntimeEnvironment.getApplication()
        val source: INotificationSourceImpl = createSource(mockPopupRepository = mockPopupRepository, hasAccess = true)
        val inputAction: Notification.Action = createPlatformNotificationAction(
            context = context,
            action = TEST_NOTIFICATION_REPLY_ACTION,
            withRemoteInput = true
        )
        mockRelay.post(record = createNotificationRecord(), handle = createNotificationHandle(actions = listOf(inputAction)))

        val didRun: Boolean = source.runAction(key = TEST_NOTIFICATION_KEY, index = 0, replyText = TEST_NOTIFICATION_REPLY_TEXT)

        assertTrue(didRun)
        val sentIntent: Intent = shadowOf(context).broadcastIntents.first { intent -> intent.action == TEST_NOTIFICATION_REPLY_ACTION }
        assertEquals(
            TEST_NOTIFICATION_REPLY_TEXT,
            RemoteInput.getResultsFromIntent(sentIntent)?.getCharSequence(TEST_NOTIFICATION_REPLY_KEY)?.toString()
        )
    }

    @Test
    fun `running an action that does not exist returns false`() = runTest {
        val mockPopupRepository: FakePopupRepository = FakePopupRepository()
        mockPopupRepository.setNotificationsEnabled(true)
        val context = RuntimeEnvironment.getApplication()
        val source: INotificationSourceImpl = createSource(mockPopupRepository = mockPopupRepository, hasAccess = true)
        mockRelay.post(
            record = createNotificationRecord(),
            handle = createNotificationHandle(actions = listOf(createPlatformNotificationAction(context = context)))
        )

        val didRun: Boolean = source.runAction(key = TEST_NOTIFICATION_KEY, index = 1)

        assertFalse(didRun)
        assertTrue(
            shadowOf(context).broadcastIntents.none { intent -> intent.action == TEST_NOTIFICATION_ACTION_ACTION }
        )
    }

    private fun TestScope.createSource(
        mockPopupRepository: FakePopupRepository,
        hasAccess: Boolean,
        accessCheck: () -> Boolean = { hasAccess },
        observe: Boolean = true
    ): INotificationSourceImpl {
        val source: INotificationSourceImpl = INotificationSourceImpl(
            context = RuntimeEnvironment.getApplication(),
            scope = backgroundScope,
            popupRepository = mockPopupRepository,
            relay = mockRelay,
            isAccessGranted = { accessCheck() }
        )
        if (observe) {
            backgroundScope.launch { source.notifications.collect() }
        }
        runCurrent()
        return source
    }

    private fun createStatusBarNotification(): StatusBarNotification {
        val notification: Notification = Notification().apply {
            extras = Bundle().apply { putCharSequence(Notification.EXTRA_TITLE, SERVICE_NOTIFICATION_TITLE) }
        }
        return StatusBarNotification(
            TEST_NOTIFICATION_PACKAGE_NAME,
            TEST_NOTIFICATION_PACKAGE_NAME,
            1,
            null,
            0,
            0,
            0,
            notification,
            createNotificationProfile(),
            1_000L
        )
    }
}
