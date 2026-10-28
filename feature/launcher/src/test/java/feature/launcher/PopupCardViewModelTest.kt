package feature.launcher

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import androidx.compose.ui.geometry.Offset
import app.cash.turbine.test
import core.data.apps.model.App
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.notifications.INotificationSource
import core.data.notifications.NotificationHandle
import core.data.notifications.NotificationRecord
import core.data.notifications.NotificationSnapshot
import core.data.popups.PopupEmptyReason
import core.data.popups.PopupItem
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult
import core.data.testing.AppWidgetHostState
import core.data.testing.TEST_NOTIFICATION_ACTION_ACTION
import core.data.testing.TEST_NOTIFICATION_KEY
import core.data.testing.TEST_NOTIFICATION_OTHER_KEY
import core.data.testing.TEST_NOTIFICATION_REPLY_KEY
import core.data.testing.TEST_NOTIFICATION_REPLY_TEXT
import core.data.testing.TEST_SDK
import core.data.testing.appWidgetPlace
import core.data.testing.createAppWidgetHostMock
import core.data.testing.createClockSourceMock
import core.data.testing.createNotificationHandle
import core.data.testing.createNotificationRecord
import core.data.testing.createPlatformNotificationAction
import core.data.testing.createWidgetSettingsRepositoryMock
import core.data.testing.popupStackPlace
import core.data.time.ClockReading
import core.data.time.IClockSource
import core.data.widgets.AppWidgetId
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IRemoveAppWidget
import core.data.widgets.IRemoveAppWidgetImpl
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.InsertPosition
import core.data.widgets.PlacedWidget
import core.data.widgets.WidgetAreaSettings
import core.data.widgets.WidgetAvailability
import core.data.widgets.WidgetPlace
import core.data.widgets.WidgetProvider
import core.data.widgets.WidgetProviderInfo
import core.ui.R as CoreUiR
import core.ui.layout.HomeEditMode
import core.ui.popups.PopupEditState
import feature.launcher.widgets.AddWidgetFlowState
import feature.launcher.widgets.WidgetAreaRepositoryFixture
import feature.launcher.widgets.WidgetEvent
import feature.launcher.widgets.appWidgetProviderInfo
import feature.launcher.widgets.createHost
import feature.launcher.widgets.createRepository
import feature.launcher.widgets.createWidgetAreaRepositoryFixture
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.verify
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class PopupCardViewModelTest : PopupSessionTestBase() {
    private val inputStackProviderInfos: Map<Int, WidgetProviderInfo> = mapOf(
        9 to appWidgetProviderInfo(isVerticallyResizable = true),
        10 to appWidgetProviderInfo(isVerticallyResizable = true),
        11 to appWidgetProviderInfo(isVerticallyResizable = true)
    )

    @Test
    fun `starts closed`() = runTest {
        val viewModel: PopupCardViewModel = createCard()
        viewModel.popupState.test {
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `shows the App's notifications in its Pop-up`() = runTest {
        val viewModel: PopupCardViewModel = openNotificationCard(notification = createNotificationRecord(title = "Hello"))

        val actualState: PopupUiState.Open = viewModel.popupState.value as PopupUiState.Open
        assertEquals(listOf("Hello"), actualState.content.ownerNotifications.map { snapshot -> snapshot.title })
        assertEquals(TEST_NOW.toEpochMilli(), actualState.nowMillis)
        assertEquals(PopupEmptyReason.None, actualState.content.emptyReason)
    }

    @Test
    fun `advances notification ages with the clock`() = runTest {
        val inputClockReadings: MutableStateFlow<ClockReading> = MutableStateFlow(clockReading(instant = TEST_NOW))
        val mockClockSource: IClockSource = createClockSourceMock(inputClockReadings)
        val viewModel: PopupCardViewModel = openNotificationCard(
            viewModel = createCard(mockClockSource = mockClockSource),
            notification = null
        )
        assertEquals(TEST_NOW.toEpochMilli(), (viewModel.popupState.value as PopupUiState.Open).nowMillis)

        val inputLater: Instant = TEST_NOW.plusSeconds(360L)
        inputClockReadings.value = clockReading(instant = inputLater)
        runCurrent()

        assertEquals(inputLater.toEpochMilli(), (viewModel.popupState.value as PopupUiState.Open).nowMillis)
    }

    @Test
    fun `tapping a notification opens it and closes the card`() = runTest {
        val viewModel: PopupCardViewModel = openNotificationCard(handle = createNotificationHandle(isAutoCancel = true))

        viewModel.onOpenNotification(key = TEST_NOTIFICATION_KEY)
        advanceUntilIdle()

        assertEquals(PopupUiState.Closed, viewModel.popupState.value)
        assertNull(notificationRelay.record(key = TEST_NOTIFICATION_KEY))
    }

    @Test
    fun `dismissing the last notification closes a notifications-only card`() = runTest {
        val viewModel: PopupCardViewModel = openNotificationCard()
        assertEquals(1, (viewModel.popupState.value as PopupUiState.Open).content.ownerNotifications.size)

        viewModel.onDismissNotification(key = TEST_NOTIFICATION_KEY)
        advanceUntilIdle()

        assertEquals(PopupUiState.Closed, viewModel.popupState.value)
    }

    @Test
    fun `dismissing the owner's last notification closes the card while another app has one`() = runTest {
        val notificationSource: INotificationSource = createNotificationSource()
        backgroundScope.launch { notificationSource.notifications.collect() }
        val viewModel: PopupCardViewModel = openNotificationCard(
            viewModel = createCard(notificationSource = notificationSource),
            otherNotification = inputOtherNotification
        )
        val actualOpen: PopupUiState.Open = viewModel.popupState.value as PopupUiState.Open
        assertEquals(1, actualOpen.content.ownerNotifications.size)

        viewModel.onDismissNotification(key = TEST_NOTIFICATION_KEY)
        advanceUntilIdle()

        assertEquals(PopupUiState.Closed, viewModel.popupState.value)
        assertNotNull(notificationRelay.record(key = TEST_NOTIFICATION_OTHER_KEY))
    }

    @Test
    fun `another app's notification neither opens nor closes the card`() = runTest {
        val viewModel: PopupCardViewModel = openNotificationCard(
            notification = null,
            otherNotification = inputOtherNotification
        )

        assertTrue(viewModel.popupState.value is PopupUiState.Open)

        viewModel.onDismissNotification(key = TEST_NOTIFICATION_OTHER_KEY)
        advanceUntilIdle()

        assertTrue(viewModel.popupState.value is PopupUiState.Open)
    }

    @Test
    fun `dismissing notifications keeps a card with shortcuts open`() = runTest {
        setShortcutResult(ShortcutsResult.Available(shortcuts = listOf(inputShortcut)))
        val viewModel: PopupCardViewModel = openNotificationCard()

        viewModel.onDismissNotification(key = TEST_NOTIFICATION_KEY)
        advanceUntilIdle()

        val actualState: PopupUiState.Open = viewModel.popupState.value as PopupUiState.Open
        assertEquals(emptyList<NotificationSnapshot>(), actualState.content.ownerNotifications)
        assertEquals(listOf(inputShortcut), actualState.content.shortcuts)
    }

    @Test
    fun `dismissing the last notification keeps a card with a widget open`() = runTest {
        val viewModel: PopupCardViewModel = openNotificationCard(viewModel = createWidgetViewModel())

        viewModel.onDismissNotification(key = TEST_NOTIFICATION_KEY)
        advanceUntilIdle()

        val actualState: PopupUiState.Open = viewModel.popupState.value as PopupUiState.Open
        assertEquals(emptyList<NotificationSnapshot>(), actualState.content.ownerNotifications)
        assertEquals(6L, actualState.widget?.pages?.single()?.widgetId)
    }

    @Test
    fun `revoking access clears the open card's notifications`() = runTest {
        var inputAccess: Boolean = true
        val notificationSource: INotificationSource = createNotificationSource(accessCheck = { inputAccess })
        setShortcutResult(ShortcutsResult.Available(shortcuts = listOf(inputShortcut)))
        val viewModel: PopupCardViewModel = openNotificationCard(
            viewModel = createCard(notificationSource = notificationSource)
        )
        assertEquals(1, (viewModel.popupState.value as PopupUiState.Open).content.ownerNotifications.size)

        inputAccess = false
        notificationSource.refreshAccess()
        runCurrent()

        val actualState: PopupUiState.Open = viewModel.popupState.value as PopupUiState.Open
        assertEquals(emptyList<NotificationSnapshot>(), actualState.content.ownerNotifications)
        assertEquals(listOf(inputShortcut), actualState.content.shortcuts)
    }

    @Test
    fun `the owner's expandable notifications start expanded`() = runTest {
        val viewModel: PopupCardViewModel = openNotificationCard(notification = createNotificationRecord(bigText = "Long text"))

        assertEquals(setOf(TEST_NOTIFICATION_KEY), viewModel.expandedNotificationKeys.value)
    }

    @Test
    fun `a title-only owner notification is not expandable`() = runTest {
        val viewModel: PopupCardViewModel = openNotificationCard(
            notification = createNotificationRecord(text = "", bigText = "")
        )

        assertEquals(emptySet<String>(), viewModel.expandedNotificationKeys.value)
    }

    @Test
    fun `collapsing the owner's notification hides it from the expanded keys`() = runTest {
        val viewModel: PopupCardViewModel = openNotificationCard(notification = createNotificationRecord(bigText = "Long text"))

        viewModel.toggleNotificationExpanded(key = TEST_NOTIFICATION_KEY)
        runCurrent()

        assertEquals(emptySet<String>(), viewModel.expandedNotificationKeys.value)
    }

    @Test
    fun `a Folder member's notification starts collapsed and expands on request`() = runTest {
        val viewModel: PopupCardViewModel = openNotificationCard(
            owner = inputFolder,
            items = listOf(inputApp),
            notification = createNotificationRecord(bigText = "Long text")
        )
        assertEquals(emptySet<String>(), viewModel.expandedNotificationKeys.value)

        viewModel.toggleNotificationExpanded(key = TEST_NOTIFICATION_KEY)
        runCurrent()

        assertEquals(setOf(TEST_NOTIFICATION_KEY), viewModel.expandedNotificationKeys.value)
    }

    @Test
    fun `a title-only member notification cannot expand`() = runTest {
        val viewModel: PopupCardViewModel = openNotificationCard(
            owner = inputFolder,
            items = listOf(inputApp),
            notification = createNotificationRecord(text = "", bigText = "")
        )

        viewModel.toggleNotificationExpanded(key = TEST_NOTIFICATION_KEY)
        runCurrent()

        assertEquals(emptySet<String>(), viewModel.expandedNotificationKeys.value)
    }

    @Test
    fun `running a notification action forwards it`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val viewModel: PopupCardViewModel = openNotificationCard(
            handle = createNotificationHandle(actions = listOf(createPlatformNotificationAction(context = context)))
        )

        viewModel.runNotificationAction(key = TEST_NOTIFICATION_KEY, index = 0)
        advanceUntilIdle()

        assertTrue(shadowOf(context).broadcastIntents.any { intent -> intent.action == TEST_NOTIFICATION_ACTION_ACTION })
    }

    @Test
    fun `a delivered reply marks the notification as sent`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val viewModel: PopupCardViewModel = openNotificationCard(
            handle = createNotificationHandle(
                actions = listOf(createPlatformNotificationAction(context = context, withRemoteInput = true))
            )
        )

        viewModel.runNotificationAction(
            key = TEST_NOTIFICATION_KEY,
            index = 0,
            replyText = TEST_NOTIFICATION_REPLY_TEXT
        )
        advanceUntilIdle()

        assertEquals(setOf(TEST_NOTIFICATION_KEY), viewModel.sentNotificationKeys.value)
        val sentIntent: Intent = shadowOf(context).broadcastIntents.first { intent -> intent.action == TEST_NOTIFICATION_ACTION_ACTION }
        assertEquals(
            TEST_NOTIFICATION_REPLY_TEXT,
            RemoteInput.getResultsFromIntent(sentIntent)?.getCharSequence(TEST_NOTIFICATION_REPLY_KEY)?.toString()
        )
    }

    @Test
    fun `a failed reply shows a Snackbar`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val inputAction: Notification.Action = createPlatformNotificationAction(context = context, withRemoteInput = true)
        inputAction.actionIntent.cancel()
        val viewModel: PopupCardViewModel = openNotificationCard(
            handle = createNotificationHandle(actions = listOf(inputAction))
        )

        viewModel.events.test {
            viewModel.runNotificationAction(key = TEST_NOTIFICATION_KEY, index = 0, replyText = TEST_NOTIFICATION_REPLY_TEXT)
            advanceUntilIdle()
            assertEquals(WidgetEvent.ShowMessage(message = CoreUiR.string.couldnt_send), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(emptySet<String>(), viewModel.sentNotificationKeys.value)
    }

    @Test
    fun `dismissing the owner's last notification keeps a card with items open`() = runTest {
        val viewModel: PopupCardViewModel = openNotificationCard(items = listOf(inputOtherApp))

        viewModel.onDismissNotification(key = TEST_NOTIFICATION_KEY)
        advanceUntilIdle()

        val actualState: PopupUiState.Open = viewModel.popupState.value as PopupUiState.Open
        assertEquals(emptyList<NotificationSnapshot>(), actualState.content.ownerNotifications)
        assertEquals(listOf(PopupItem.Launchable(inputOtherApp)), actualState.content.items)
    }

    @Test
    fun `dismissing a member's last notification closes the Folder card`() = runTest {
        val viewModel: PopupCardViewModel = openNotificationCard(owner = inputFolder, items = listOf(inputApp))
        val actualOpen: PopupUiState.Open = viewModel.popupState.value as PopupUiState.Open
        assertEquals(1, actualOpen.content.itemNotifications.getValue(inputApp.key).size)
        assertEquals(listOf(PopupItem.Launchable(inputApp)), actualOpen.content.items)

        viewModel.onDismissNotification(key = TEST_NOTIFICATION_KEY)
        advanceUntilIdle()

        assertEquals(PopupUiState.Closed, viewModel.popupState.value)
    }

    @Test
    fun `opens the swiped App with an empty shortcut list while loading`() = runTest {
        val viewModel: PopupCardViewModel = createCard()
        viewModel.popupState.test {
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            val actualState: PopupUiState.Open = expectMostRecentItem() as PopupUiState.Open
            assertEquals(inputApp, actualState.owner)
            assertEquals(TEST_INPUT_ANCHOR, actualState.anchor)
            assertEquals(PopupEmptyReason.Empty, actualState.content.emptyReason)
        }
    }

    @Test
    fun `updates the open card when shortcuts arrive`() = runTest {
        val viewModel: PopupCardViewModel = createCard()
        viewModel.popupState.test {
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            assertEquals(PopupEmptyReason.Empty, (expectMostRecentItem() as PopupUiState.Open).content.emptyReason)
            setShortcutResult(ShortcutsResult.Available(shortcuts = listOf(inputShortcut)))
            val actualState: PopupUiState.Open = expectMostRecentItem() as PopupUiState.Open
            assertEquals(listOf(inputShortcut), actualState.content.shortcuts)
            assertEquals(PopupEmptyReason.None, actualState.content.emptyReason)
        }
    }

    @Test
    fun `ignores open for a Rail Launchable`() = runTest {
        val viewModel: PopupCardViewModel = createCard()
        viewModel.popupState.test {
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
            viewModel.open(inputRailLaunchable, TEST_INPUT_ANCHOR)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `ignores open while a Home edit mode is active`() = runTest {
        inputHomeEditState.tryEnter(HomeEditMode.Widgets)
        val viewModel: PopupCardViewModel = createCard()
        viewModel.popupState.test {
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `closes on Back and reports whether a Pop-up was open`() = runTest {
        val viewModel: PopupCardViewModel = createCard()
        viewModel.popupState.test {
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            assertTrue(viewModel.onBack())
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
            assertFalse(viewModel.onBack())
        }
    }

    @Test
    fun `closes on the Home intent`() = runTest {
        val viewModel: PopupCardViewModel = createCard()
        viewModel.popupState.test {
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            viewModel.onHomeIntent()
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `closes after a launch`() = runTest {
        val viewModel: PopupCardViewModel = createCard()
        viewModel.popupState.test {
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            viewModel.onLaunched()
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `closes when a Home edit mode starts`() = runTest {
        val viewModel: PopupCardViewModel = createCard()
        viewModel.popupState.test {
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            inputHomeEditState.tryEnter(HomeEditMode.Favorites)
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `closes when the owner disappears after loading`() = runTest {
        val viewModel: PopupCardViewModel = createCard()
        viewModel.popupState.test {
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            setLaunchableGroups(
                groups = listOf(LaunchableGroup.Star, LaunchableGroup.Builtin(items = listOf(inputRailLaunchable)))
            )
            runCurrent()
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `moves an open Pop-up when its row is laid out again`() = runTest {
        val viewModel: PopupCardViewModel = createCard()
        val updatedAnchor: PopupAnchor = TEST_INPUT_ANCHOR.copy(rowTop = 800, rowBottom = 864, iconCenter = Offset(32f, 832f))
        viewModel.popupState.test {
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            assertEquals(TEST_INPUT_ANCHOR, (expectMostRecentItem() as PopupUiState.Open).anchor)
            viewModel.onAnchorChanged(inputApp.key, updatedAnchor)
            assertEquals(updatedAnchor, (expectMostRecentItem() as PopupUiState.Open).anchor)
        }
    }

    @Test
    fun `requests the owner's shortcuts again after a reload`() = runTest {
        val viewModel: PopupCardViewModel = createCard()

        viewModel.popupState.test {
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            runCurrent()
            val actualState: PopupUiState.Open = expectMostRecentItem() as PopupUiState.Open
            assertEquals(inputApp, actualState.owner)
            verifyShortcutRequests(app = inputApp, count = 1)
            viewModel.reloadShortcuts()
            runCurrent()
            verifyShortcutRequests(app = inputApp, count = 2)
        }
    }

    @Test
    fun `turning on a paused App asks to leave quiet mode and reloads its shortcuts`() = runTest {
        setLaunchableGroups(
            groups = listOf(
                LaunchableGroup.Star,
                LaunchableGroup.InitialGroup(label = "W", items = listOf(inputWorkApp))
            )
        )
        inputAppWidgetHostState.profileSerials[inputWorkApp.profile] = TEST_WORK_PROFILE_SERIAL
        val viewModel: PopupCardViewModel = createCard()

        viewModel.popupState.test {
            viewModel.open(inputWorkApp, TEST_INPUT_ANCHOR)
            runCurrent()
            assertTrue(expectMostRecentItem() is PopupUiState.Open)

            viewModel.events.test {
                viewModel.unpauseOwnerProfile(owner = inputWorkApp)
                assertEquals(WidgetEvent.RequestQuietModeOff(profileSerial = TEST_WORK_PROFILE_SERIAL), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            runCurrent()

            verifyShortcutRequests(app = inputWorkApp, count = 2)
        }
    }

    @Test
    fun `turning on a Folder asks for nothing`() = runTest {
        val viewModel: PopupCardViewModel = createCard()

        viewModel.popupState.test {
            viewModel.open(inputFolder, TEST_INPUT_ANCHOR)
            runCurrent()
            assertTrue(expectMostRecentItem() is PopupUiState.Open)

            viewModel.events.test {
                viewModel.unpauseOwnerProfile(owner = inputFolder)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            verifyNoShortcutRequests()
        }
    }

    @Test
    fun `turning on without an open Pop-up asks for nothing`() = runTest {
        val viewModel: PopupCardViewModel = createCard()

        viewModel.events.test {
            viewModel.unpauseOwnerProfile()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        verifyNoShortcutRequests()
    }

    @Test
    fun `opens the menu with Edit Pop-up and App info for an App owner`() = runTest {
        val viewModel: PopupCardViewModel = createCard()
        viewModel.menuState.test {
            assertEquals(PopupMenuState.Closed, expectMostRecentItem())
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            runCurrent()
            viewModel.openMenu()
            val actualMenu: PopupMenuState.Open = expectMostRecentItem() as PopupMenuState.Open
            assertEquals(inputApp, actualMenu.target)
            assertEquals(listOf(PopupMenuItem.EditPopup, PopupMenuItem.AddWidget, PopupMenuItem.AppInfo), actualMenu.items)
        }
    }

    @Test
    fun `closes the menu without closing the card`() = runTest {
        val viewModel: PopupCardViewModel = createCard()
        viewModel.menuState.test {
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            runCurrent()
            viewModel.openMenu()
            assertTrue(expectMostRecentItem() is PopupMenuState.Open)
            viewModel.closeMenu()
            assertEquals(PopupMenuState.Closed, expectMostRecentItem())
        }
        viewModel.popupState.test {
            assertTrue(expectMostRecentItem() is PopupUiState.Open)
        }
    }

    @Test
    fun `starting an edit closes the menu`() = runTest {
        val session: PopupTestSession = createSession()
        session.card.menuState.test {
            session.card.open(inputApp, TEST_INPUT_ANCHOR)
            runCurrent()
            session.card.openMenu()
            assertTrue(expectMostRecentItem() is PopupMenuState.Open)
            session.editor.startEdit(inputApp)
            assertEquals(PopupMenuState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `updates the card content when a shortcut is hidden`() = runTest {
        setShortcutResult(ShortcutsResult.Available(shortcuts = listOf(inputShortcut)))
        val session: PopupTestSession = createSession()
        val viewModel: PopupCardViewModel = session.card
        viewModel.popupState.test {
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            runCurrent()
            assertEquals(listOf(inputShortcut), (expectMostRecentItem() as PopupUiState.Open).content.shortcuts)
            session.editor.startEdit(inputApp)
            runCurrent()
            session.editor.setShortcutHidden(inputShortcut.shortcutId, hidden = true)
            setHiddenShortcuts(inputApp.key, setOf(inputShortcut.shortcutId))
            runCurrent()
            val actualState: PopupUiState.Open = expectMostRecentItem() as PopupUiState.Open
            assertEquals(emptyList<ShortcutLaunchable>(), actualState.content.shortcuts)
            assertEquals(PopupEmptyReason.Empty, actualState.content.emptyReason)
        }
    }

    @Test
    fun `Back closes the editor before the card`() = runTest {
        val session: PopupTestSession = createSession()
        session.editor.editState.test {
            session.card.open(inputApp, TEST_INPUT_ANCHOR)
            session.editor.startEdit(inputApp)
            runCurrent()
            assertTrue(expectMostRecentItem() is PopupEditState.Editing)
            assertTrue(session.card.onBack())
            assertEquals(PopupEditState.Closed, expectMostRecentItem())
        }
        session.card.popupState.test {
            assertTrue(expectMostRecentItem() is PopupUiState.Open)
        }
    }

    @Test
    fun `Back closes the menu before the card`() = runTest {
        val viewModel: PopupCardViewModel = createCard()
        viewModel.menuState.test {
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            runCurrent()
            viewModel.openMenu()
            assertTrue(expectMostRecentItem() is PopupMenuState.Open)
            assertTrue(viewModel.onBack())
            assertEquals(PopupMenuState.Closed, expectMostRecentItem())
        }
        viewModel.popupState.test {
            assertTrue(expectMostRecentItem() is PopupUiState.Open)
            assertTrue(viewModel.onBack())
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
            assertFalse(viewModel.onBack())
        }
    }

    @Test
    fun `item menu shows App info for Apps and Shortcuts but not Folders`() = runTest {
        setPopupItems(
            ownerKey = inputApp.key,
            items = listOf(
                PopupItem.Launchable(inputOtherApp),
                PopupItem.Shortcut(launchable = inputOtherShortcut, isEnabled = true),
                PopupItem.Launchable(inputFolder)
            )
        )
        val viewModel: PopupCardViewModel = createCard()
        viewModel.popupState.test {
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            runCurrent()
            assertTrue(expectMostRecentItem() is PopupUiState.Open)
            viewModel.menuState.test {
                viewModel.openItemMenu(PopupItem.Launchable(inputOtherApp))
                val appMenu: PopupMenuState.Open = expectMostRecentItem() as PopupMenuState.Open
                assertEquals(inputOtherApp, appMenu.target)
                assertEquals(
                    listOf(PopupMenuItem.RemoveFromPopup, PopupMenuItem.AppInfo),
                    appMenu.items
                )
                viewModel.closeMenu()
                assertEquals(PopupMenuState.Closed, expectMostRecentItem())
                viewModel.openItemMenu(PopupItem.Shortcut(launchable = inputOtherShortcut, isEnabled = true))
                val shortcutMenu: PopupMenuState.Open = expectMostRecentItem() as PopupMenuState.Open
                assertEquals(inputOtherShortcut, shortcutMenu.target)
                assertEquals(
                    listOf(PopupMenuItem.RemoveFromPopup, PopupMenuItem.AppInfo),
                    shortcutMenu.items
                )
                viewModel.closeMenu()
                assertEquals(PopupMenuState.Closed, expectMostRecentItem())
                viewModel.openItemMenu(PopupItem.Launchable(inputFolder))
                val folderMenu: PopupMenuState.Open = expectMostRecentItem() as PopupMenuState.Open
                assertEquals(inputFolder, folderMenu.target)
                assertEquals(listOf(PopupMenuItem.RemoveFromPopup), folderMenu.items)
            }
        }
    }

    @Test
    fun `removing an item closes its menu and updates the card`() = runTest {
        setPopupLaunchables(ownerKey = inputApp.key, launchables = listOf(inputOtherApp))
        val viewModel: PopupCardViewModel = createCard()
        backgroundScope.launch { viewModel.popupState.collect() }
        backgroundScope.launch { viewModel.menuState.collect() }
        viewModel.open(inputApp, TEST_INPUT_ANCHOR)
        runCurrent()
        val openState: PopupUiState.Open = viewModel.popupState.value as PopupUiState.Open
        assertEquals(listOf(PopupItem.Launchable(inputOtherApp)), openState.content.items)
        viewModel.openItemMenu(PopupItem.Launchable(inputOtherApp))
        runCurrent()
        assertTrue(viewModel.menuState.value is PopupMenuState.Open)
        viewModel.removeMenuItem()
        runCurrent()
        setPopupLaunchables(ownerKey = inputApp.key, launchables = emptyList())
        runCurrent()
        assertEquals(PopupMenuState.Closed, viewModel.menuState.value)
        val updatedState: PopupUiState.Open = viewModel.popupState.value as PopupUiState.Open
        assertEquals(emptyList<PopupItem>(), updatedState.content.items)
        assertEquals(PopupEmptyReason.Empty, updatedState.content.emptyReason)
        coVerify(exactly = 1) { mockPopupRepository.removeItem(any(), any()) }
        coVerify(exactly = 1) { mockPopupRepository.removeItem(inputApp.key, inputOtherApp.key) }
    }

    @Test
    fun `Back closes the item menu before the editor`() = runTest {
        val session: PopupTestSession = openPopupWithEditor(owner = inputApp, editor = inputApp, editorItem = null)
        session.card.openItemMenu(PopupItem.Launchable(inputOtherApp))
        runCurrent()
        assertTrue(session.card.menuState.value is PopupMenuState.Open)
        assertTrue(session.editor.editState.value is PopupEditState.Editing)
        assertTrue(session.card.onBack())
        assertEquals(PopupMenuState.Closed, session.card.menuState.value)
        assertTrue(session.editor.editState.value is PopupEditState.Editing)
    }

    @Test
    fun `opens a Folder with the Folder empty state`() = runTest {
        val viewModel: PopupCardViewModel = createCard()
        viewModel.popupState.test {
            viewModel.open(inputFolder, TEST_INPUT_ANCHOR)
            val actualState: PopupUiState.Open = expectMostRecentItem() as PopupUiState.Open
            assertEquals(inputFolder, actualState.owner)
            assertEquals(TEST_INPUT_ANCHOR, actualState.anchor)
            assertEquals(emptyList<ShortcutLaunchable>(), actualState.content.shortcuts)
            assertEquals(PopupEmptyReason.FolderEmpty, actualState.content.emptyReason)
        }
    }

    @Test
    fun `Folder menu offers Edit Folder and Delete Folder without App info`() = runTest {
        val viewModel: PopupCardViewModel = createCard()
        viewModel.menuState.test {
            viewModel.open(inputFolder, TEST_INPUT_ANCHOR)
            runCurrent()
            viewModel.openMenu()
            val actualMenu: PopupMenuState.Open = expectMostRecentItem() as PopupMenuState.Open
            assertEquals(inputFolder, actualMenu.target)
            assertEquals(listOf(PopupMenuItem.EditFolder, PopupMenuItem.AddWidget, PopupMenuItem.DeleteFolder), actualMenu.items)
        }
    }

    @Test
    fun `shows the owner's widget at the top of the card`() = runTest {
        val viewModel: PopupCardViewModel = createWidgetViewModel()
        viewModel.popupState.test {
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            runCurrent()

            val actualState: PopupUiState.Open = expectMostRecentItem() as PopupUiState.Open

            assertEquals(5L, actualState.widget?.placeId)
            assertEquals(listOf(6L), actualState.widget?.pages?.map { page -> page.widgetId })
            assertEquals(48, actualState.widget?.heightDp)
            assertTrue(actualState.widget?.isResizable == true)
        }
    }

    @Test
    fun `caps the widget height at 60 percent of the window`() = runTest {
        val viewModel: PopupCardViewModel = createWidgetViewModel(
            widgetPlace = appWidgetPlace(
                placeId = 5L,
                widgetId = 6L,
                appWidgetId = 9,
                ownerKey = inputApp.key,
                heightDp = 1_000
            ),
            providerInfo = appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 2_000)
        )
        viewModel.setWindowHeight(heightDp = 400)

        viewModel.popupState.test {
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            runCurrent()

            val actualState: PopupUiState.Open = expectMostRecentItem() as PopupUiState.Open

            assertEquals(240, actualState.widget?.heightDp)
        }
    }

    @Test
    fun `owner menu offers Add to stack and widget actions instead of Add widget`() = runTest {
        val viewModel: PopupCardViewModel = createWidgetViewModel()
        viewModel.menuState.test {
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            runCurrent()
            viewModel.openMenu()
            runCurrent()

            val actualMenu: PopupMenuState.Open = expectMostRecentItem() as PopupMenuState.Open

            assertEquals(
                listOf(
                    PopupMenuItem.EditPopup,
                    PopupMenuItem.AddToStack,
                    PopupMenuItem.ResizeWidget,
                    PopupMenuItem.RemoveWidget,
                    PopupMenuItem.AppInfo
                ),
                actualMenu.items
            )
        }
    }

    @Test
    fun `widget menu of an App owner offers App info`() = runTest {
        val actualMenu: PopupMenuState.Open = openWidgetMenu(
            providerInfo = appWidgetProviderInfo(
                isVerticallyResizable = true,
                isReconfigurable = true,
                hasConfigureActivity = true
            )
        )

        assertEquals(
            listOf(
                PopupMenuItem.ResizeWidget,
                PopupMenuItem.ReconfigureWidget,
                PopupMenuItem.RemoveWidget,
                PopupMenuItem.AppInfo
            ),
            actualMenu.items
        )
        assertEquals(6L, actualMenu.widgetPage?.widgetId)
    }

    @Test
    fun `widget menu of a Folder owner has no App info`() = runTest {
        val actualMenu: PopupMenuState.Open = openWidgetMenu(
            widgetPlace = appWidgetPlace(placeId = 5L, widgetId = 6L, appWidgetId = 9, ownerKey = inputFolder.key),
            owner = inputFolder
        )

        assertEquals(listOf(PopupMenuItem.ResizeWidget, PopupMenuItem.RemoveWidget), actualMenu.items)
        assertEquals(6L, actualMenu.widgetPage?.widgetId)
    }

    @Test
    fun `startAddWidget opens the picker at the owner with its own providers first`() = runTest {
        val inputOwnerProvider = appWidgetProviderInfo(
            provider = WidgetProvider(packageName = inputApp.packageName, className = "OwnerWidget"),
            appPackageName = inputApp.packageName,
            appLabel = "Zeta"
        )
        val inputOtherProvider = appWidgetProviderInfo(
            provider = WidgetProvider(packageName = "com.other", className = "OtherWidget"),
            appPackageName = "com.other",
            appLabel = "Alpha"
        )
        val mockAppWidgetHost: IAppWidgetHostSource = createAppWidgetHostMock(
            AppWidgetHostState(providers = listOf(inputOtherProvider, inputOwnerProvider))
        )
        val viewModel: PopupCardViewModel = createCard(mockAppWidgetHost = mockAppWidgetHost)
        backgroundScope.launch { viewModel.popupState.collect() }
        runCurrent()
        viewModel.open(inputApp, TEST_INPUT_ANCHOR)
        runCurrent()

        viewModel.startAddWidget()
        runCurrent()

        val actualPicking: AddWidgetFlowState.Picking = viewModel.widgetFlow.state.value as AddWidgetFlowState.Picking
        assertEquals(inputApp.key, actualPicking.origin.ownerKey)
        assertEquals(listOf("Zeta", "Alpha"), actualPicking.groups.map { group -> group.appLabel })
    }

    @Test
    fun `reopens the card for the owner after the add flow`() = runTest {
        val viewModel: PopupCardViewModel = createCard()
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, TEST_INPUT_ANCHOR)
        runCurrent()
        viewModel.startAddWidget()
        runCurrent()

        val restoredViewModel: PopupCardViewModel = createCard(savedStateHandle = inputSavedStateHandle)
        restoredViewModel.popupState.test {
            runCurrent()

            val actualState: PopupUiState.Open = expectMostRecentItem() as PopupUiState.Open

            assertEquals(inputApp, actualState.owner)
            assertNull(actualState.anchor)
        }
    }

    @Test
    fun `removeWidget frees the host view and reports the Widget removed message`() = runTest {
        val viewModel: PopupCardViewModel = createWidgetViewModel()
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, TEST_INPUT_ANCHOR)
        runCurrent()

        viewModel.events.test {
            viewModel.removeWidget()
            runCurrent()

            assertEquals(WidgetEvent.RemoveHostView(appWidgetId = AppWidgetId(9)), awaitItem())
            assertEquals(WidgetEvent.ShowMessage(message = CoreUiR.string.widget_removed), awaitItem())
        }
        coVerify(exactly = 1) { mockRemoveAppWidget.invoke(6L) }
    }

    @Test
    fun `Back finishes Resize and keeps the card open`() = runTest {
        val viewModel: PopupCardViewModel = createWidgetViewModel(
            providerInfo = appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 200)
        )
        backgroundScope.launch { viewModel.popupState.collect() }
        runCurrent()
        viewModel.open(inputApp, TEST_INPUT_ANCHOR)
        runCurrent()
        runCurrent()
        viewModel.resizeWidget()
        runCurrent()
        runCurrent()

        assertTrue(viewModel.widgetState.value != null)
        assertTrue(viewModel.resizeState.value != null)
        assertTrue(viewModel.onBack())
        runCurrent()

        assertNull(viewModel.resizeState.value)
        assertTrue(viewModel.popupState.value is PopupUiState.Open)
    }

    @Test
    fun `a launch during Resize finishes it and keeps the card open`() = runTest {
        val viewModel: PopupCardViewModel = createWidgetViewModel(
            providerInfo = appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 200)
        )
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, TEST_INPUT_ANCHOR)
        runCurrent()
        viewModel.resizeWidget()
        runCurrent()

        viewModel.onLaunched()
        runCurrent()

        assertNull(viewModel.resizeState.value)
        assertTrue(viewModel.popupState.value is PopupUiState.Open)
    }

    @Test
    fun `keeps the card open while Resize targets its own widget place`() = runTest {
        val viewModel: PopupCardViewModel = createWidgetViewModel()
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, TEST_INPUT_ANCHOR)
        runCurrent()

        inputHomeEditState.tryEnter(HomeEditMode.Resizing(placeId = 5L))
        runCurrent()

        assertTrue(viewModel.popupState.value is PopupUiState.Open)
    }

    @Test
    fun `closes the card when Resize targets another place`() = runTest {
        val viewModel: PopupCardViewModel = createWidgetViewModel()
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, TEST_INPUT_ANCHOR)
        runCurrent()

        inputHomeEditState.tryEnter(HomeEditMode.Resizing(placeId = 99L))
        runCurrent()

        assertEquals(PopupUiState.Closed, viewModel.popupState.value)
    }

    @Test
    fun `removed owner widget ids drop their host views`() = runTest {
        val viewModel: PopupCardViewModel = createCard()

        viewModel.events.test {
            emitRemovedWidget(appWidgetId = AppWidgetId(33))
            runCurrent()

            assertEquals(WidgetEvent.RemoveHostView(appWidgetId = AppWidgetId(33)), awaitItem())
        }
    }

    @Test
    fun `leaving the app keeps the card open while an add flow is pending`() = runTest {
        val viewModel: PopupCardViewModel = createCard()
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, TEST_INPUT_ANCHOR)
        runCurrent()
        viewModel.startAddWidget()
        runCurrent()

        viewModel.onStopped()

        assertTrue(viewModel.popupState.value is PopupUiState.Open)
    }

    @Test
    fun `leaving the app closes the card when no add flow is pending`() = runTest {
        val viewModel: PopupCardViewModel = createCard()
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, TEST_INPUT_ANCHOR)
        runCurrent()

        viewModel.onStopped()

        assertEquals(PopupUiState.Closed, viewModel.popupState.value)
    }

    @Test
    fun `shows the owner's widget stack with its remembered page`() = runTest {
        val mockRepository: IWidgetAreaRepository =
            createRepository(listOf(popupStackPlace(placeId = 5L, heightDp = 120, currentPage = 1, ownerKey = inputApp.key)))
        val viewModel: PopupCardViewModel = createStackViewModel(mockRepository = mockRepository)
        viewModel.popupState.test {
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            runCurrent()

            val actualWidget: PopupWidgetState = (expectMostRecentItem() as PopupUiState.Open).widget!!

            assertEquals(listOf(6L, 7L), actualWidget.pages.map { page -> page.widgetId })
            assertEquals(1, actualWidget.currentPage)
            assertEquals(120, actualWidget.heightDp)
            assertTrue(actualWidget.isResizable)
            assertTrue(actualWidget.indicatorVisible)
        }
    }

    @Test
    fun `hides the stack indicator when the Widget Stacks setting is off`() = runTest {
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            widgetAreaSettings = WidgetAreaSettings(stackIndicatorVisible = false)
        )
        val viewModel: PopupCardViewModel = createStackViewModel(mockSettingsRepository = mockSettingsRepository)
        viewModel.popupState.test {
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            runCurrent()

            val actualWidget: PopupWidgetState = (expectMostRecentItem() as PopupUiState.Open).widget!!

            assertFalse(actualWidget.indicatorVisible)
        }
    }

    @Test
    fun `persists the stack page when paging settles`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(listOf(popupStackPlace(placeId = 5L, ownerKey = inputApp.key)))
        val viewModel: PopupCardViewModel = createStackViewModel(mockRepository = mockRepository)
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, TEST_INPUT_ANCHOR)
        runCurrent()

        viewModel.onPageChanged(page = 1)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockRepository.setCurrentPage(placeId = 5L, page = 1) }
    }

    @Test
    fun `keeps the last settled page when paging back before the debounce`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(listOf(popupStackPlace(placeId = 5L, ownerKey = inputApp.key)))
        val viewModel: PopupCardViewModel = createStackViewModel(mockRepository = mockRepository)
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, TEST_INPUT_ANCHOR)
        runCurrent()

        viewModel.onPageChanged(page = 1)
        advanceTimeBy(100)
        viewModel.onPageChanged(page = 0)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockRepository.setCurrentPage(placeId = 5L, page = 0) }
    }

    @Test
    fun `ignores a settled page outside the stack`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(listOf(popupStackPlace(placeId = 5L, ownerKey = inputApp.key)))
        val viewModel: PopupCardViewModel = createStackViewModel(mockRepository = mockRepository)
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, TEST_INPUT_ANCHOR)
        runCurrent()

        viewModel.onPageChanged(page = 2)
        advanceUntilIdle()

        coVerify(exactly = 0) { mockRepository.setCurrentPage(any(), any()) }
    }

    @Test
    fun `owner menu hides Add to stack at the stack cap`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(listOf(popupStackPlace(placeId = 5L, ownerKey = inputApp.key)))
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            widgetAreaSettings = WidgetAreaSettings(maxStackPages = 2)
        )
        val viewModel: PopupCardViewModel = createStackViewModel(
            mockRepository = mockRepository,
            mockSettingsRepository = mockSettingsRepository
        )
        viewModel.menuState.test {
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            runCurrent()
            viewModel.openMenu()
            runCurrent()

            val actualMenu: PopupMenuState.Open = expectMostRecentItem() as PopupMenuState.Open

            assertEquals(
                listOf(
                    PopupMenuItem.EditPopup,
                    PopupMenuItem.ResizeWidget,
                    PopupMenuItem.RemoveFromStack,
                    PopupMenuItem.AppInfo
                ),
                actualMenu.items
            )
        }
    }

    @Test
    fun `widget menu removes only the long-pressed stack page`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(listOf(popupStackPlace(placeId = 5L, ownerKey = inputApp.key)))
        val viewModel: PopupCardViewModel = createStackViewModel(mockRepository = mockRepository)
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, TEST_INPUT_ANCHOR)
        runCurrent()
        viewModel.openWidgetMenu(widgetId = 7L)
        runCurrent()

        viewModel.events.test {
            viewModel.removeWidget()
            runCurrent()

            assertEquals(WidgetEvent.RemoveHostView(appWidgetId = AppWidgetId(9)), awaitItem())
            assertEquals(WidgetEvent.ShowMessage(message = CoreUiR.string.widget_removed), awaitItem())
        }

        coVerify(exactly = 1) { mockRemoveAppWidget.invoke(7L) }
    }

    @Test
    fun `widget menu offers Remove widget for a single page`() = runTest {
        val actualMenu: PopupMenuState.Open = openWidgetMenu(
            widgetPlace = popupStackPlace(placeId = 5L, widgetIds = listOf(6L), appWidgetIds = listOf(9), ownerKey = inputApp.key)
        )

        assertEquals(
            listOf(PopupMenuItem.ResizeWidget, PopupMenuItem.RemoveWidget, PopupMenuItem.AppInfo),
            actualMenu.items
        )
        assertEquals(6L, actualMenu.widgetPage?.widgetId)
    }

    @Test
    fun `widget menu offers Remove from stack for a multi-page place`() = runTest {
        val actualMenu: PopupMenuState.Open = openWidgetMenu(
            widgetPlace = popupStackPlace(placeId = 5L, ownerKey = inputApp.key),
            widgetId = 7L
        )

        assertEquals(
            listOf(PopupMenuItem.ResizeWidget, PopupMenuItem.RemoveFromStack, PopupMenuItem.AppInfo),
            actualMenu.items
        )
        assertEquals(7L, actualMenu.widgetPage?.widgetId)
    }

    @Test
    fun `Add to stack appends a page and selects it`() = runTest {
        val inputProvider: WidgetProviderInfo = appWidgetProviderInfo(
            provider = WidgetProvider(packageName = "com.other", className = "OtherWidget"),
            appPackageName = "com.other",
            appLabel = "Other"
        )
        val inputRepositoryFixture: WidgetAreaRepositoryFixture = createWidgetAreaRepositoryFixture(
            listOf(
                popupStackPlace(
                    placeId = 5L,
                    widgetIds = listOf(6L),
                    appWidgetIds = listOf(9),
                    heightDp = 120,
                    ownerKey = inputApp.key
                )
            )
        )
        val mockRepository: IWidgetAreaRepository = inputRepositoryFixture.mockRepository
        val mockHost: IAppWidgetHostSource = createHost(providers = listOf(inputProvider), providerInfos = inputStackProviderInfos)
        val viewModel: PopupCardViewModel = createStackViewModel(mockRepository = mockRepository, mockHost = mockHost)
        backgroundScope.launch { viewModel.popupState.collect() }
        runCurrent()
        viewModel.open(inputApp, TEST_INPUT_ANCHOR)
        runCurrent()

        viewModel.startAddWidget()
        runCurrent()
        viewModel.widgetFlow.pickProvider(provider = inputProvider)
        runCurrent()
        inputRepositoryFixture.setStackPlaces(heightDp = 120)
        runCurrent()

        val actualWidget: PopupWidgetState = viewModel.widgetState.value!!
        assertEquals(2, actualWidget.pages.size)
        assertEquals(1, actualWidget.currentPage)
        coVerify(exactly = 1) {
            mockRepository.addAppWidget(
                match { request -> request.insertAt == InsertPosition.IntoStack(placeId = 5L) && request.ownerKey == inputApp.key }
            )
        }
    }

    @Test
    fun `startAddWidget at the stack cap reports that the stack is full`() = runTest {
        val mockRepository: IWidgetAreaRepository = createRepository(listOf(popupStackPlace(placeId = 5L, ownerKey = inputApp.key)))
        val mockSettingsRepository: IWidgetSettingsRepository = createWidgetSettingsRepositoryMock(
            widgetAreaSettings = WidgetAreaSettings(maxStackPages = 2)
        )
        val viewModel: PopupCardViewModel = createStackViewModel(
            mockRepository = mockRepository,
            mockSettingsRepository = mockSettingsRepository
        )
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, TEST_INPUT_ANCHOR)
        runCurrent()

        viewModel.events.test {
            viewModel.startAddWidget()
            runCurrent()

            assertEquals(WidgetEvent.ShowMessage(message = CoreUiR.string.stack_full), awaitItem())
        }

        assertEquals(AddWidgetFlowState.Idle, viewModel.widgetFlow.state.value)
    }

    @Test
    fun `clamps the shared height to the intersection of the pages' bounds`() = runTest {
        val mockRepository: IWidgetAreaRepository =
            createRepository(listOf(popupStackPlace(placeId = 5L, heightDp = 300, ownerKey = inputApp.key)))
        val mockHost: IAppWidgetHostSource = createHost(
            providerInfos = mapOf(
                9 to appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 200),
                10 to appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 160)
            )
        )
        val viewModel: PopupCardViewModel = createStackViewModel(mockRepository = mockRepository, mockHost = mockHost)
        viewModel.popupState.test {
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            runCurrent()

            val actualWidget: PopupWidgetState = (expectMostRecentItem() as PopupUiState.Open).widget!!

            assertEquals(160, actualWidget.heightDp)
            assertEquals(160, actualWidget.bounds.maxDp)
        }
    }

    @Test
    fun `hides Resize for a stack with an unavailable page`() = runTest {
        val inputPlace: WidgetPlace = popupStackPlace(placeId = 5L, ownerKey = inputApp.key)
        val mockRepository: IWidgetAreaRepository = createRepository(
            listOf(
                inputPlace.copy(
                    widgets = inputPlace.widgets.map { widget ->
                        (widget as PlacedWidget.AppWidget).copy(availability = WidgetAvailability.ProfilePaused)
                    }
                )
            )
        )
        val viewModel: PopupCardViewModel = createStackViewModel(mockRepository = mockRepository)
        viewModel.menuState.test {
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            runCurrent()
            viewModel.openMenu()
            runCurrent()

            val actualMenu: PopupMenuState.Open = expectMostRecentItem() as PopupMenuState.Open

            assertEquals(
                listOf(
                    PopupMenuItem.EditPopup,
                    PopupMenuItem.AddToStack,
                    PopupMenuItem.RemoveFromStack,
                    PopupMenuItem.AppInfo
                ),
                actualMenu.items
            )
        }
    }

    @Test
    fun `hides Resize when the provider cannot resize vertically`() = runTest {
        val viewModel: PopupCardViewModel = createWidgetViewModel(providerInfo = appWidgetProviderInfo(isVerticallyResizable = false))
        viewModel.menuState.test {
            viewModel.open(inputApp, TEST_INPUT_ANCHOR)
            runCurrent()
            viewModel.openMenu()
            runCurrent()

            val actualMenu: PopupMenuState.Open = expectMostRecentItem() as PopupMenuState.Open

            assertFalse(PopupMenuItem.ResizeWidget in actualMenu.items)
        }
    }

    @Test
    fun `removing the current page shows the last page`() = runTest {
        val inputRepositoryFixture: WidgetAreaRepositoryFixture = createWidgetAreaRepositoryFixture(
            listOf(
                popupStackPlace(
                    placeId = 5L,
                    widgetIds = listOf(6L, 7L, 8L),
                    appWidgetIds = listOf(9, 10, 11),
                    currentPage = 2,
                    ownerKey = inputApp.key
                )
            )
        )
        val mockRepository: IWidgetAreaRepository = inputRepositoryFixture.mockRepository
        val mockHost: IAppWidgetHostSource = createHost(providerInfos = inputStackProviderInfos)
        val viewModel: PopupCardViewModel = createStackViewModel(
            mockRepository = mockRepository,
            mockHost = mockHost,
            removeAppWidget = IRemoveAppWidgetImpl(repository = mockRepository, host = mockHost)
        )
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, TEST_INPUT_ANCHOR)
        runCurrent()
        viewModel.openWidgetMenu(widgetId = 8L)
        runCurrent()
        coEvery { mockRepository.removeWidget(widgetId = 8L) } returns AppWidgetId(11)

        viewModel.removeWidget()
        advanceUntilIdle()
        inputRepositoryFixture.setStackPlaces()
        advanceUntilIdle()

        assertEquals(listOf(6L, 7L), viewModel.widgetState.value?.pages?.map { page -> page.widgetId })
        coVerify(exactly = 1) { mockRepository.removeWidget(widgetId = 8L) }
        verify(exactly = 1) { mockHost.deleteId(AppWidgetId(11)) }
    }

    /** Seeds the stack at [placeId] with the pages and current page that follow an add or a page removal. */
    private fun WidgetAreaRepositoryFixture.setStackPlaces(heightDp: Int? = null) {
        state.placesByOwner.value = mapOf(
            inputApp.key to listOf(
                popupStackPlace(
                    placeId = 5L,
                    widgetIds = listOf(6L, 7L),
                    appWidgetIds = listOf(9, 10),
                    heightDp = heightDp,
                    currentPage = 1,
                    ownerKey = inputApp.key
                )
            )
        )
    }

    /** Verifies [app]'s Shortcut source was queried exactly [count] times. */
    private fun verifyShortcutRequests(app: App, count: Int) {
        verify(exactly = count) { mockShortcutSource.shortcuts(any(), any()) }
        verify(exactly = count) { mockShortcutSource.shortcuts(app.packageName, app.profile) }
    }

    private fun verifyNoShortcutRequests() {
        verify(exactly = 0) { mockShortcutSource.shortcuts(any(), any()) }
    }

    private fun TestScope.createWidgetViewModel(
        widgetPlace: WidgetPlace = appWidgetPlace(placeId = 5L, widgetId = 6L, appWidgetId = 9, ownerKey = inputApp.key),
        providerInfo: WidgetProviderInfo? = appWidgetProviderInfo(isVerticallyResizable = true)
    ): PopupCardViewModel {
        val mockRepository: IWidgetAreaRepository = createRepository(listOf(widgetPlace))
        val mockHost: IAppWidgetHostSource = if (providerInfo == null) {
            createAppWidgetHostMock()
        } else {
            val appWidgetId: Int = (widgetPlace.widgets.single() as PlacedWidget.AppWidget).appWidgetId
            createHost(providerInfos = mapOf(appWidgetId to providerInfo))
        }
        return createCard(mockWidgetRepository = mockRepository, mockAppWidgetHost = mockHost)
    }

    private fun TestScope.createStackViewModel(
        mockRepository: IWidgetAreaRepository = createRepository(listOf(popupStackPlace(placeId = 5L, ownerKey = inputApp.key))),
        mockHost: IAppWidgetHostSource = createHost(providerInfos = inputStackProviderInfos),
        mockSettingsRepository: IWidgetSettingsRepository = mockWidgetSettingsRepository,
        removeAppWidget: IRemoveAppWidget = mockRemoveAppWidget
    ): PopupCardViewModel = createCard(
        mockWidgetRepository = mockRepository,
        mockAppWidgetHost = mockHost,
        mockWidgetSettingsRepository = mockSettingsRepository,
        removeAppWidget = removeAppWidget
    )

    /** Opens [owner]'s menu for [widgetId] in [widgetPlace], returning the menu it shows. */
    private fun TestScope.openWidgetMenu(
        widgetPlace: WidgetPlace = appWidgetPlace(placeId = 5L, widgetId = 6L, appWidgetId = 9, ownerKey = inputApp.key),
        providerInfo: WidgetProviderInfo = appWidgetProviderInfo(isVerticallyResizable = true),
        owner: Launchable = inputApp,
        widgetId: Long = 6L
    ): PopupMenuState.Open {
        val mockHost: IAppWidgetHostSource = createHost(
            providerInfos = widgetPlace.widgets
                .filterIsInstance<PlacedWidget.AppWidget>()
                .associate { widget -> widget.appWidgetId to providerInfo }
        )
        val viewModel: PopupCardViewModel = createCard(
            mockWidgetRepository = createRepository(listOf(widgetPlace)),
            mockAppWidgetHost = mockHost
        )
        openPopup(card = viewModel, owner = owner, flows = listOf(viewModel.menuState))
        viewModel.openWidgetMenu(widgetId = widgetId)
        runCurrent()
        return viewModel.menuState.value as PopupMenuState.Open
    }

    /** Opens [owner]'s Pop-up with notifications switched on and posts zero, one or two notifications. */
    private suspend fun TestScope.openNotificationCard(
        viewModel: PopupCardViewModel = createCard(),
        owner: Launchable = inputApp,
        items: List<Launchable> = emptyList(),
        notification: NotificationRecord? = createNotificationRecord(),
        otherNotification: NotificationRecord? = null,
        handle: NotificationHandle = createNotificationHandle()
    ): PopupCardViewModel {
        setNotificationsEnabled(true)
        if (items.isNotEmpty()) {
            setPopupLaunchables(ownerKey = owner.key, launchables = items)
        }
        openPopup(
            card = viewModel,
            owner = owner,
            flows = listOf(viewModel.popupState, viewModel.expandedNotificationKeys)
        )
        if (notification != null) {
            notificationRelay.post(record = notification, handle = handle)
        }
        if (otherNotification != null) {
            notificationRelay.post(record = otherNotification, handle = createNotificationHandle())
        }
        runCurrent()
        return viewModel
    }
}
