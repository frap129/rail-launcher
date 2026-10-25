package core.popups

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import core.data.apps.model.App
import core.data.folders.Folder
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.notifications.INotificationListenerRelayImpl
import core.data.notifications.INotificationSource
import core.data.notifications.INotificationSourceImpl
import core.data.popups.PopupItem
import core.data.shortcuts.AppShortcuts
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult
import core.data.testing.FakeAppWidgetHostSource
import core.data.testing.FakeClockSource
import core.data.testing.FakeFolderRepository
import core.data.testing.FakeLaunchableRepository
import core.data.testing.FakePopupRepository
import core.data.testing.FakeRemoveAppWidget
import core.data.testing.FakeShortcutSource
import core.data.testing.FakeWidgetAreaDao
import core.data.testing.FakeWidgetAreaRepository
import core.data.testing.FakeWidgetSettingsRepository
import core.data.testing.FolderCreateRequest
import core.data.testing.MainDispatcherRule
import core.data.testing.TEST_NOTIFICATION_KEY
import core.data.testing.TEST_NOTIFICATION_PACKAGE_NAME
import core.data.testing.TEST_SDK
import core.data.testing.appWidgetPlace
import core.data.testing.createNotificationHandle
import core.data.testing.createNotificationProfile
import core.data.testing.createNotificationRecord
import core.data.testing.popupStackPlace
import core.data.time.ClockReading
import core.data.time.IClockSource
import core.data.widgets.AppWidgetId
import core.data.widgets.IRemoveAppWidget
import core.data.widgets.IRemoveAppWidgetImpl
import core.data.widgets.IWidgetLifecycleReconcilerImpl
import core.data.widgets.InsertPosition
import core.data.widgets.PlacedWidget
import core.data.widgets.WidgetAreaSettings
import core.data.widgets.WidgetAvailability
import core.data.widgets.WidgetPlace
import core.data.widgets.WidgetProvider
import core.data.widgets.WidgetProviderInfo
import core.ui.R as CoreUiR
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import core.widgets.AddWidgetFlowState
import core.widgets.IWidgetResizeControllerImpl
import core.widgets.WidgetEvent
import core.widgets.testing.appWidgetProviderInfo
import core.widgets.testing.createAddController
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val ACTIVITY_NAME: String = "com.example.app.MainActivity"
private val NOW: Instant = Instant.ofEpochMilli(10_000_000L)
private val INPUT_ANCHOR: PopupAnchor = PopupAnchor(rowTop = 400, rowBottom = 464, iconCenter = Offset(32f, 432f))

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class PopupViewModelTest {
    @get:Rule
    val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

    private val profile: UserHandle = createNotificationProfile()
    private val inputApp: App = App(
        defaultName = "Example",
        profile = profile,
        componentName = ComponentName(TEST_NOTIFICATION_PACKAGE_NAME, ACTIVITY_NAME)
    )
    private val inputOtherApp: App = App(
        defaultName = "Other",
        profile = profile,
        componentName = ComponentName("com.example.other", "com.example.other.MainActivity")
    )
    private val inputPausedApp: App = App(
        defaultName = "Paused",
        profile = profile,
        componentName = ComponentName("com.example.paused", "com.example.paused.MainActivity")
    )
    private val inputRailLaunchable: RailLaunchable = RailLaunchable(
        defaultName = "Rail Settings",
        defaultIcon = Icon.RailIcon(resId = 0),
        deeplink = "rail-launcher://settings/main"
    )
    private val inputFolder: Folder = Folder(folderId = 1L)
    private val inputSecondFolder: Folder = Folder(folderId = 2L, name = "Work")
    private val inputGroups: List<LaunchableGroup> = listOf(
        LaunchableGroup.Star,
        LaunchableGroup.InitialGroup(label = "E", items = listOf(inputApp)),
        LaunchableGroup.Builtin(items = listOf(inputRailLaunchable))
    )
    private val inputShortcut: ShortcutLaunchable = ShortcutLaunchable(
        packageName = TEST_NOTIFICATION_PACKAGE_NAME,
        shortcutId = "shortcut-id",
        profile = profile,
        shortLabel = "Short label",
        longLabel = "Long label"
    )
    private val inputOtherShortcut: ShortcutLaunchable = ShortcutLaunchable(
        packageName = inputOtherApp.packageName,
        shortcutId = "other-shortcut-id",
        profile = profile,
        shortLabel = "Other short label",
        longLabel = "Other long label"
    )
    private val inputAppShortcuts: AppShortcuts = AppShortcuts(
        app = inputApp,
        result = ShortcutsResult.Available(shortcuts = listOf(inputShortcut))
    )
    private val inputOtherAppShortcuts: AppShortcuts = AppShortcuts(
        app = inputOtherApp,
        result = ShortcutsResult.Available(shortcuts = listOf(inputOtherShortcut))
    )
    private val fakeLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(
        groups = inputGroups,
        folders = listOf(inputFolder)
    )
    private val fakeShortcutSource: FakeShortcutSource = FakeShortcutSource()
    private val fakePopupRepository: FakePopupRepository = FakePopupRepository(resolveLaunchable = { key ->
        listOf(inputApp, inputOtherApp, inputPausedApp, inputRailLaunchable).firstOrNull { launchable -> launchable.key == key }
    })
    private val fakeFolderRepository: FakeFolderRepository = FakeFolderRepository(initialFolders = listOf(inputFolder, inputSecondFolder))
    private val inputHomeEditState: HomeEditState = HomeEditState()
    private val inputSavedStateHandle: SavedStateHandle = SavedStateHandle()
    private val fakeAppWidgetHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
    private val fakeRemoveAppWidget: FakeRemoveAppWidget = FakeRemoveAppWidget()
    private val fakeWidgetSettingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
    private val fakeWidgetAreaRepository: FakeWidgetAreaRepository = FakeWidgetAreaRepository()
    private val mockNotificationRelay: INotificationListenerRelayImpl = INotificationListenerRelayImpl()

    @Test
    fun `starts closed`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `shows the App's notifications in its Pop-up`() = runTest {
        fakePopupRepository.setNotificationsEnabled(true)
        val viewModel: PopupViewModel = createViewModel()
        backgroundScope.launch { viewModel.popupState.collect() }
        runCurrent()
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()
        mockNotificationRelay.post(record = createNotificationRecord(title = "Hello"), handle = createNotificationHandle())
        runCurrent()

        val actualState: PopupUiState.Open = viewModel.popupState.value as PopupUiState.Open
        assertEquals(listOf("Hello"), actualState.content.ownerNotifications.map { row -> row.snapshot.title })
        assertEquals(NOW.toEpochMilli(), actualState.nowMillis)
        assertEquals(PopupEmptyReason.None, actualState.content.emptyReason)
    }

    @Test
    fun `advances notification ages with the clock`() = runTest {
        fakePopupRepository.setNotificationsEnabled(true)
        val mockClockSource: FakeClockSource = FakeClockSource(initialReading = clockReading(instant = NOW))
        val viewModel: PopupViewModel = createViewModel(clockSource = mockClockSource)
        backgroundScope.launch { viewModel.popupState.collect() }
        runCurrent()
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()
        assertEquals(NOW.toEpochMilli(), (viewModel.popupState.value as PopupUiState.Open).nowMillis)

        val inputLater: Instant = NOW.plusSeconds(360L)
        mockClockSource.emit(reading = clockReading(instant = inputLater))
        runCurrent()

        assertEquals(inputLater.toEpochMilli(), (viewModel.popupState.value as PopupUiState.Open).nowMillis)
    }

    @Test
    fun `tapping a notification opens it and closes the card`() = runTest {
        fakePopupRepository.setNotificationsEnabled(true)
        val viewModel: PopupViewModel = createViewModel()
        backgroundScope.launch { viewModel.popupState.collect() }
        runCurrent()
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()
        mockNotificationRelay.post(
            record = createNotificationRecord(),
            handle = createNotificationHandle(isAutoCancel = true)
        )
        runCurrent()

        viewModel.onOpenNotification(key = TEST_NOTIFICATION_KEY)
        advanceUntilIdle()

        assertEquals(PopupUiState.Closed, viewModel.popupState.value)
        assertNull(mockNotificationRelay.record(key = TEST_NOTIFICATION_KEY))
    }

    @Test
    fun `dismissing the last notification closes a notifications-only card`() = runTest {
        fakePopupRepository.setNotificationsEnabled(true)
        val viewModel: PopupViewModel = createViewModel()
        backgroundScope.launch { viewModel.popupState.collect() }
        runCurrent()
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()
        mockNotificationRelay.post(record = createNotificationRecord(), handle = createNotificationHandle())
        runCurrent()
        assertEquals(1, (viewModel.popupState.value as PopupUiState.Open).content.ownerNotifications.size)

        viewModel.onDismissNotification(key = TEST_NOTIFICATION_KEY)
        advanceUntilIdle()

        assertEquals(PopupUiState.Closed, viewModel.popupState.value)
    }

    @Test
    fun `dismissing notifications keeps a card with shortcuts open`() = runTest {
        fakePopupRepository.setNotificationsEnabled(true)
        fakeShortcutSource.setResult(ShortcutsResult.Available(shortcuts = listOf(inputShortcut)))
        val viewModel: PopupViewModel = createViewModel()
        backgroundScope.launch { viewModel.popupState.collect() }
        runCurrent()
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()
        mockNotificationRelay.post(record = createNotificationRecord(), handle = createNotificationHandle())
        runCurrent()

        viewModel.onDismissNotification(key = TEST_NOTIFICATION_KEY)
        advanceUntilIdle()

        val actualState: PopupUiState.Open = viewModel.popupState.value as PopupUiState.Open
        assertEquals(emptyList<NotificationRow>(), actualState.content.ownerNotifications)
        assertEquals(listOf(inputShortcut), actualState.content.shortcuts)
    }

    @Test
    fun `revoking access clears the open card's notifications`() = runTest {
        var inputAccess: Boolean = true
        val notificationSource: INotificationSource = createNotificationSource(accessCheck = { inputAccess })
        fakePopupRepository.setNotificationsEnabled(true)
        fakeShortcutSource.setResult(ShortcutsResult.Available(shortcuts = listOf(inputShortcut)))
        val viewModel: PopupViewModel = createViewModel(notificationSource = notificationSource)
        backgroundScope.launch { viewModel.popupState.collect() }
        runCurrent()
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()
        mockNotificationRelay.post(record = createNotificationRecord(), handle = createNotificationHandle())
        runCurrent()
        assertEquals(1, (viewModel.popupState.value as PopupUiState.Open).content.ownerNotifications.size)

        inputAccess = false
        notificationSource.refreshAccess()
        runCurrent()

        val actualState: PopupUiState.Open = viewModel.popupState.value as PopupUiState.Open
        assertEquals(emptyList<NotificationRow>(), actualState.content.ownerNotifications)
        assertEquals(listOf(inputShortcut), actualState.content.shortcuts)
    }

    @Test
    fun `opens the swiped App with an empty shortcut list while loading`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
            viewModel.open(inputApp, INPUT_ANCHOR)
            val actualState: PopupUiState.Open = expectMostRecentItem() as PopupUiState.Open
            assertEquals(inputApp, actualState.owner)
            assertEquals(INPUT_ANCHOR, actualState.anchor)
            assertEquals(PopupEmptyReason.Empty, actualState.content.emptyReason)
        }
    }

    @Test
    fun `updates the open card when shortcuts arrive`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            assertEquals(PopupEmptyReason.Empty, (expectMostRecentItem() as PopupUiState.Open).content.emptyReason)
            fakeShortcutSource.setResult(ShortcutsResult.Available(shortcuts = listOf(inputShortcut)))
            val actualState: PopupUiState.Open = expectMostRecentItem() as PopupUiState.Open
            assertEquals(listOf(inputShortcut), actualState.content.shortcuts)
            assertEquals(PopupEmptyReason.None, actualState.content.emptyReason)
        }
    }

    @Test
    fun `ignores open for a Rail Launchable`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
            viewModel.open(inputRailLaunchable, INPUT_ANCHOR)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `ignores open while a Home edit mode is active`() = runTest {
        inputHomeEditState.tryEnter(HomeEditMode.Widgets)
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
            viewModel.open(inputApp, INPUT_ANCHOR)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `closes on Back and reports whether a Pop-up was open`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            assertTrue(viewModel.onBack())
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
            assertFalse(viewModel.onBack())
        }
    }

    @Test
    fun `closes on the Home intent`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            viewModel.onHomeIntent()
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `closes after a launch`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            viewModel.onLaunched()
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `closes when a Home edit mode starts`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            inputHomeEditState.tryEnter(HomeEditMode.Favorites)
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `closes when the owner disappears after loading`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            fakeLaunchableRepository.setGroups(
                groups = listOf(LaunchableGroup.Star, LaunchableGroup.Builtin(items = listOf(inputRailLaunchable)))
            )
            runCurrent()
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `moves an open Pop-up when its row is laid out again`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        val updatedAnchor: PopupAnchor = INPUT_ANCHOR.copy(rowTop = 800, rowBottom = 864, iconCenter = Offset(32f, 832f))
        viewModel.popupState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            assertEquals(INPUT_ANCHOR, (expectMostRecentItem() as PopupUiState.Open).anchor)
            viewModel.onAnchorChanged(inputApp.key, updatedAnchor)
            assertEquals(updatedAnchor, (expectMostRecentItem() as PopupUiState.Open).anchor)
        }
    }

    @Test
    fun `requests the owner's shortcuts again after a reload`() = runTest {
        val viewModel: PopupViewModel = createViewModel()

        viewModel.popupState.test {
            assertEquals(PopupUiState.Closed, expectMostRecentItem())
            viewModel.open(inputApp, INPUT_ANCHOR)
            runCurrent()
            val actualState: PopupUiState.Open = expectMostRecentItem() as PopupUiState.Open
            assertEquals(inputApp, actualState.owner)
            assertEquals(listOf(TEST_NOTIFICATION_PACKAGE_NAME to profile), fakeShortcutSource.requests)
            viewModel.reloadShortcuts()
            runCurrent()
            assertEquals(
                listOf(TEST_NOTIFICATION_PACKAGE_NAME to profile, TEST_NOTIFICATION_PACKAGE_NAME to profile),
                fakeShortcutSource.requests
            )
        }
    }

    @Test
    fun `opens the menu with Edit Pop-up and App info for an App owner`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.menuState.test {
            assertEquals(PopupMenuState.Closed, expectMostRecentItem())
            viewModel.open(inputApp, INPUT_ANCHOR)
            runCurrent()
            viewModel.openMenu()
            val actualMenu: PopupMenuState.Open = expectMostRecentItem() as PopupMenuState.Open
            assertEquals(inputApp, actualMenu.target)
            assertEquals(listOf(PopupMenuItem.EditPopup, PopupMenuItem.AddWidget, PopupMenuItem.AppInfo), actualMenu.items)
        }
    }

    @Test
    fun `closes the menu without closing the card`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.menuState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
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
        val viewModel: PopupViewModel = createViewModel()
        viewModel.menuState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            runCurrent()
            viewModel.openMenu()
            assertTrue(expectMostRecentItem() is PopupMenuState.Open)
            viewModel.startEdit(inputApp)
            assertEquals(PopupMenuState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `edits an App's Pop-up with its live shortcuts and hidden ids`() = runTest {
        val inputShortcuts: ShortcutsResult = ShortcutsResult.Available(shortcuts = listOf(inputShortcut))
        fakeShortcutSource.setResult(inputShortcuts)
        fakePopupRepository.setHiddenShortcuts(inputApp.key, setOf(inputShortcut.shortcutId))
        val viewModel: PopupViewModel = createViewModel()
        viewModel.editState.test {
            assertEquals(PopupEditState.Closed, expectMostRecentItem())
            viewModel.startEdit(inputApp)
            runCurrent()
            val actualState: PopupEditState.Editing = expectMostRecentItem() as PopupEditState.Editing
            assertEquals(inputApp, actualState.owner)
            assertEquals(inputShortcuts, actualState.shortcuts)
            assertEquals(setOf(inputShortcut.shortcutId), actualState.hiddenIds)
        }
    }

    @Test
    fun `updates the editor when the shortcut list changes`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.editState.test {
            viewModel.startEdit(inputApp)
            runCurrent()
            assertTrue(expectMostRecentItem() is PopupEditState.Editing)
            fakeShortcutSource.setResult(ShortcutsResult.Available(shortcuts = listOf(inputShortcut)))
            val actualState: PopupEditState.Editing = expectMostRecentItem() as PopupEditState.Editing
            assertEquals(ShortcutsResult.Available(shortcuts = listOf(inputShortcut)), actualState.shortcuts)
        }
    }

    @Test
    fun `allows editing during Edit Favorites`() = runTest {
        inputHomeEditState.tryEnter(HomeEditMode.Favorites)
        val viewModel: PopupViewModel = createViewModel()
        viewModel.editState.test {
            viewModel.startEdit(inputApp)
            runCurrent()
            assertTrue(expectMostRecentItem() is PopupEditState.Editing)
        }
    }

    @Test
    fun `ignores editing in Widgets and Resizing`() = runTest {
        listOf(HomeEditMode.Widgets, HomeEditMode.Resizing(placeId = 1L)).forEach { inputMode ->
            val editState: HomeEditState = HomeEditState()
            editState.tryEnter(inputMode)
            val viewModel: PopupViewModel = createViewModel(homeEditState = editState)
            viewModel.editState.test {
                assertEquals(PopupEditState.Closed, expectMostRecentItem())
                viewModel.startEdit(inputApp)
                runCurrent()
                expectNoEvents()
                assertEquals(PopupEditState.Closed, viewModel.editState.value)
            }
        }
    }

    @Test
    fun `ignores editing a Launchable that is not an App`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.editState.test {
            viewModel.startEdit(inputRailLaunchable)
            runCurrent()
            assertEquals(PopupEditState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `forwards a shortcut hidden state change for the edited App`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.startEdit(inputApp)
        runCurrent()
        viewModel.setShortcutHidden(inputShortcut.shortcutId, hidden = true)
        runCurrent()
        assertEquals(listOf(Triple(inputApp.key, inputShortcut.shortcutId, true)), fakePopupRepository.setShortcutHiddenCalls)
    }

    @Test
    fun `updates the card content when a shortcut is hidden`() = runTest {
        fakeShortcutSource.setResult(ShortcutsResult.Available(shortcuts = listOf(inputShortcut)))
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            runCurrent()
            assertEquals(listOf(inputShortcut), (expectMostRecentItem() as PopupUiState.Open).content.shortcuts)
            viewModel.startEdit(inputApp)
            runCurrent()
            viewModel.setShortcutHidden(inputShortcut.shortcutId, hidden = true)
            runCurrent()
            val actualState: PopupUiState.Open = expectMostRecentItem() as PopupUiState.Open
            assertEquals(emptyList<ShortcutLaunchable>(), actualState.content.shortcuts)
            assertEquals(PopupEmptyReason.Empty, actualState.content.emptyReason)
        }
    }

    @Test
    fun `closes the editor when its owner disappears`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.editState.test {
            viewModel.startEdit(inputApp)
            runCurrent()
            assertTrue(expectMostRecentItem() is PopupEditState.Editing)
            fakeLaunchableRepository.setGroups(
                groups = listOf(LaunchableGroup.Star, LaunchableGroup.Builtin(items = listOf(inputRailLaunchable)))
            )
            runCurrent()
            assertEquals(PopupEditState.Closed, expectMostRecentItem())
        }
    }

    @Test
    fun `Back closes the editor before the card`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.editState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            viewModel.startEdit(inputApp)
            runCurrent()
            assertTrue(expectMostRecentItem() is PopupEditState.Editing)
            assertTrue(viewModel.onBack())
            assertEquals(PopupEditState.Closed, expectMostRecentItem())
        }
        viewModel.popupState.test {
            assertTrue(expectMostRecentItem() is PopupUiState.Open)
        }
    }

    @Test
    fun `Back closes the menu before the card`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.menuState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
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
    fun `edits an App's Pop-up with its live items`() = runTest {
        fakePopupRepository.setItems(inputApp.key, listOf(inputOtherApp))
        val viewModel: PopupViewModel = createViewModel()
        viewModel.editState.test {
            assertEquals(PopupEditState.Closed, expectMostRecentItem())
            viewModel.startEdit(inputApp)
            runCurrent()
            val actualState: PopupEditState.Editing = expectMostRecentItem() as PopupEditState.Editing
            assertEquals(listOf(PopupItem.Launchable(inputOtherApp)), actualState.items)
        }
    }

    @Test
    fun `edit sheet keeps disabled shortcuts`() = runTest {
        fakePopupRepository.setPopupItems(
            ownerKey = inputApp.key,
            items = listOf(
                PopupItem.Shortcut(launchable = inputShortcut, isEnabled = false),
                PopupItem.Launchable(launchable = inputOtherApp)
            )
        )
        val viewModel: PopupViewModel = createViewModel()
        viewModel.editState.test {
            assertEquals(PopupEditState.Closed, expectMostRecentItem())
            viewModel.startEdit(inputApp)
            runCurrent()
            val actualState: PopupEditState.Editing = expectMostRecentItem() as PopupEditState.Editing
            assertEquals(
                listOf(
                    PopupItem.Shortcut(launchable = inputShortcut, isEnabled = false),
                    PopupItem.Launchable(inputOtherApp)
                ),
                actualState.items
            )
            assertEquals(mapOf(inputShortcut.key to inputApp.defaultName), actualState.shortcutSourceNames)
        }
    }

    @Test
    fun `shortcut picker groups Apps with their shortcuts and checks existing items`() = runTest {
        fakeLaunchableRepository.setGroups(
            groups = listOf(
                LaunchableGroup.Star,
                LaunchableGroup.InitialGroup(label = "E", items = listOf(inputApp)),
                LaunchableGroup.InitialGroup(label = "O", items = listOf(inputOtherApp)),
                LaunchableGroup.Builtin(items = listOf(inputRailLaunchable))
            )
        )
        fakeShortcutSource.setAppsWithShortcuts(apps = listOf(inputAppShortcuts, inputOtherAppShortcuts))
        fakePopupRepository.setPopupItems(
            ownerKey = inputApp.key,
            items = listOf(PopupItem.Shortcut(launchable = inputOtherShortcut, isEnabled = true))
        )
        val viewModel: PopupViewModel = createViewModel()
        viewModel.shortcutPickerState.test {
            assertEquals(ShortcutPickerState.Closed, expectMostRecentItem())
            viewModel.startEdit(inputApp)
            runCurrent()
            viewModel.startPickingShortcuts()
            runCurrent()
            val picking: ShortcutPickerState.Picking = expectMostRecentItem() as ShortcutPickerState.Picking
            assertEquals(setOf(inputOtherShortcut.key), picking.selectedKeys)
            assertEquals(listOf("O"), picking.groups.map { group -> group.label })
            assertEquals(listOf(inputOtherAppShortcuts), picking.groups.flatMap { group -> group.apps })
        }
    }

    @Test
    fun `picker excludes the owner and shortcuts and checks existing items`() = runTest {
        fakeLaunchableRepository.setGroups(
            groups = listOf(
                LaunchableGroup.Star,
                LaunchableGroup.InitialGroup(label = "E", items = listOf(inputApp)),
                LaunchableGroup.InitialGroup(label = "O", items = listOf(inputOtherApp)),
                LaunchableGroup.InitialGroup(label = "S", items = listOf(inputShortcut)),
                LaunchableGroup.Builtin(items = listOf(inputRailLaunchable))
            )
        )
        fakePopupRepository.setItems(inputApp.key, listOf(inputOtherApp))
        val viewModel: PopupViewModel = createViewModel()
        viewModel.pickerState.test {
            assertEquals(PopupPickerState.Closed, expectMostRecentItem())
            viewModel.startEdit(inputApp)
            runCurrent()
            viewModel.startPicking()
            val picking: PopupPickerState.Picking = expectMostRecentItem() as PopupPickerState.Picking
            assertEquals(setOf(inputOtherApp.key), picking.selectedKeys)
            assertEquals(listOf("O", "⌂"), picking.groups.map { group -> group.label })
            val candidates: List<Launchable> = picking.groups.flatMap { group -> group.items }
            assertEquals(listOf(inputOtherApp, inputRailLaunchable), candidates)
            assertFalse(candidates.any { candidate -> candidate == inputApp || candidate is ShortcutLaunchable })
        }
    }

    @Test
    fun `confirming the shortcut picker adds checked shortcuts and removes unchecked ones`() = runTest {
        fakeLaunchableRepository.setGroups(
            groups = listOf(
                LaunchableGroup.Star,
                LaunchableGroup.InitialGroup(label = "E", items = listOf(inputApp)),
                LaunchableGroup.InitialGroup(label = "O", items = listOf(inputOtherApp))
            )
        )
        fakeShortcutSource.setAppsWithShortcuts(apps = listOf(inputAppShortcuts, inputOtherAppShortcuts))
        fakePopupRepository.setPopupItems(
            ownerKey = inputApp.key,
            items = listOf(PopupItem.Shortcut(launchable = inputShortcut, isEnabled = true))
        )
        val viewModel: PopupViewModel = createViewModel()
        viewModel.shortcutPickerState.test {
            viewModel.startEdit(inputApp)
            runCurrent()
            viewModel.startPickingShortcuts()
            runCurrent()
            assertTrue(expectMostRecentItem() is ShortcutPickerState.Picking)
            viewModel.confirmShortcuts(selectedInOrder = listOf(inputOtherShortcut.key))
            assertEquals(ShortcutPickerState.Closed, expectMostRecentItem())
        }
        runCurrent()
        assertEquals(
            listOf(Triple(inputApp.key, listOf(inputOtherShortcut.key), setOf(inputShortcut.key))),
            fakePopupRepository.setItemsCalls
        )
    }

    @Test
    fun `cancelling the shortcut picker changes nothing`() = runTest {
        fakeShortcutSource.setAppsWithShortcuts(apps = listOf(inputOtherAppShortcuts))
        val viewModel: PopupViewModel = createViewModel()
        viewModel.shortcutPickerState.test {
            viewModel.startEdit(inputApp)
            runCurrent()
            viewModel.startPickingShortcuts()
            runCurrent()
            assertTrue(expectMostRecentItem() is ShortcutPickerState.Picking)
            viewModel.cancelShortcutPicking()
            assertEquals(ShortcutPickerState.Closed, expectMostRecentItem())
        }
        runCurrent()
        assertTrue(fakePopupRepository.setItemsCalls.isEmpty())
    }

    @Test
    fun `shortcut picker lists paused Apps and leaves out Apps with no shortcuts`() = runTest {
        fakeLaunchableRepository.setGroups(
            groups = listOf(
                LaunchableGroup.Star,
                LaunchableGroup.InitialGroup(label = "E", items = listOf(inputApp)),
                LaunchableGroup.InitialGroup(label = "O", items = listOf(inputOtherApp)),
                LaunchableGroup.InitialGroup(label = "P", items = listOf(inputPausedApp))
            )
        )
        fakeShortcutSource.setAppsWithShortcuts(
            apps = listOf(
                AppShortcuts(app = inputOtherApp, result = ShortcutsResult.Available(shortcuts = emptyList())),
                AppShortcuts(app = inputPausedApp, result = ShortcutsResult.ProfilePaused)
            )
        )
        val viewModel: PopupViewModel = createViewModel()
        viewModel.shortcutPickerState.test {
            viewModel.startEdit(inputApp)
            runCurrent()
            viewModel.startPickingShortcuts()
            runCurrent()
            val picking: ShortcutPickerState.Picking = expectMostRecentItem() as ShortcutPickerState.Picking
            assertEquals(listOf("P"), picking.groups.map { group -> group.label })
            assertEquals(listOf(inputPausedApp), picking.groups.flatMap { group -> group.apps }.map { appShortcuts -> appShortcuts.app })
        }
    }

    @Test
    fun `shortcut picker reports when shortcuts are unavailable`() = runTest {
        fakeLaunchableRepository.setGroups(
            groups = listOf(
                LaunchableGroup.Star,
                LaunchableGroup.InitialGroup(label = "E", items = listOf(inputApp)),
                LaunchableGroup.InitialGroup(label = "O", items = listOf(inputOtherApp))
            )
        )
        fakeShortcutSource.setAppsWithShortcuts(
            apps = listOf(AppShortcuts(app = inputOtherApp, result = ShortcutsResult.Unavailable))
        )
        val viewModel: PopupViewModel = createViewModel()
        viewModel.shortcutPickerState.test {
            viewModel.startEdit(inputApp)
            runCurrent()
            viewModel.startPickingShortcuts()
            runCurrent()
            val picking: ShortcutPickerState.Picking = expectMostRecentItem() as ShortcutPickerState.Picking
            assertTrue(picking.shortcutsUnavailable)
        }
    }

    @Test
    fun `item menu shows App info only for App items`() = runTest {
        fakePopupRepository.setItems(inputApp.key, listOf(inputOtherApp, inputRailLaunchable))
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
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
                viewModel.openItemMenu(PopupItem.Launchable(inputRailLaunchable))
                val railMenu: PopupMenuState.Open = expectMostRecentItem() as PopupMenuState.Open
                assertEquals(inputRailLaunchable, railMenu.target)
                assertEquals(listOf(PopupMenuItem.RemoveFromPopup), railMenu.items)
            }
        }
    }

    @Test
    fun `removing an item closes its menu and updates the card`() = runTest {
        fakePopupRepository.setItems(inputApp.key, listOf(inputOtherApp))
        val viewModel: PopupViewModel = createViewModel()
        backgroundScope.launch { viewModel.popupState.collect() }
        backgroundScope.launch { viewModel.menuState.collect() }
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()
        val openState: PopupUiState.Open = viewModel.popupState.value as PopupUiState.Open
        assertEquals(listOf(PopupItem.Launchable(inputOtherApp)), openState.content.items)
        viewModel.openItemMenu(PopupItem.Launchable(inputOtherApp))
        runCurrent()
        assertTrue(viewModel.menuState.value is PopupMenuState.Open)
        viewModel.removeItem(inputOtherApp)
        runCurrent()
        assertEquals(PopupMenuState.Closed, viewModel.menuState.value)
        val updatedState: PopupUiState.Open = viewModel.popupState.value as PopupUiState.Open
        assertEquals(emptyList<PopupItem>(), updatedState.content.items)
        assertEquals(PopupEmptyReason.Empty, updatedState.content.emptyReason)
        assertEquals(listOf(inputApp.key to inputOtherApp.key), fakePopupRepository.removedItems)
    }

    @Test
    fun `removing an item while editing uses the edited owner`() = runTest {
        fakePopupRepository.setItems(inputApp.key, listOf(inputOtherApp))
        val viewModel: PopupViewModel = createViewModel()
        viewModel.editState.test {
            viewModel.startEdit(inputApp)
            runCurrent()
            assertTrue(expectMostRecentItem() is PopupEditState.Editing)
            viewModel.removeItem(inputOtherApp)
            runCurrent()
            val updatedState: PopupEditState.Editing = expectMostRecentItem() as PopupEditState.Editing
            assertEquals(emptyList<PopupItem>(), updatedState.items)
        }
        assertEquals(listOf(inputApp.key to inputOtherApp.key), fakePopupRepository.removedItems)
    }

    @Test
    fun `sets the item order for the edited App`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.startEdit(inputApp)
        runCurrent()
        val orderedKeys: List<String> = listOf(inputOtherApp.key, inputApp.key)

        viewModel.setItemOrder(orderedItemKeys = orderedKeys)
        runCurrent()

        assertEquals(listOf(inputApp.key to orderedKeys), fakePopupRepository.itemOrders)
    }

    @Test
    fun `Back closes the shortcut picker before the editor`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        backgroundScope.launch { viewModel.editState.collect() }
        backgroundScope.launch { viewModel.shortcutPickerState.collect() }
        viewModel.startEdit(inputApp)
        runCurrent()
        viewModel.startPickingShortcuts()
        runCurrent()
        assertTrue(viewModel.shortcutPickerState.value is ShortcutPickerState.Picking)
        assertTrue(viewModel.editState.value is PopupEditState.Editing)

        assertTrue(viewModel.onBack())

        assertEquals(ShortcutPickerState.Closed, viewModel.shortcutPickerState.value)
        assertTrue(viewModel.editState.value is PopupEditState.Editing)
    }

    @Test
    fun `Back closes the item menu before the editor`() = runTest {
        fakePopupRepository.setItems(inputApp.key, listOf(inputOtherApp))
        val viewModel: PopupViewModel = createViewModel()
        backgroundScope.launch { viewModel.popupState.collect() }
        backgroundScope.launch { viewModel.editState.collect() }
        backgroundScope.launch { viewModel.menuState.collect() }
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()
        viewModel.startEdit(inputApp)
        runCurrent()
        viewModel.openItemMenu(PopupItem.Launchable(inputOtherApp))
        runCurrent()
        assertTrue(viewModel.menuState.value is PopupMenuState.Open)
        assertTrue(viewModel.editState.value is PopupEditState.Editing)

        assertTrue(viewModel.onBack())

        assertEquals(PopupMenuState.Closed, viewModel.menuState.value)
        assertTrue(viewModel.editState.value is PopupEditState.Editing)
    }

    @Test
    fun `opens a Folder with the Folder empty state`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.popupState.test {
            viewModel.open(inputFolder, INPUT_ANCHOR)
            val actualState: PopupUiState.Open = expectMostRecentItem() as PopupUiState.Open
            assertEquals(inputFolder, actualState.owner)
            assertEquals(INPUT_ANCHOR, actualState.anchor)
            assertEquals(emptyList<ShortcutLaunchable>(), actualState.content.shortcuts)
            assertEquals(PopupEmptyReason.FolderEmpty, actualState.content.emptyReason)
        }
    }

    @Test
    fun `Folder menu offers Edit Folder and Delete Folder without App info`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.menuState.test {
            viewModel.open(inputFolder, INPUT_ANCHOR)
            runCurrent()
            viewModel.openMenu()
            val actualMenu: PopupMenuState.Open = expectMostRecentItem() as PopupMenuState.Open
            assertEquals(inputFolder, actualMenu.target)
            assertEquals(listOf(PopupMenuItem.EditFolder, PopupMenuItem.AddWidget, PopupMenuItem.DeleteFolder), actualMenu.items)
        }
    }

    @Test
    fun `edits a Folder during Edit Favorites`() = runTest {
        inputHomeEditState.tryEnter(HomeEditMode.Favorites)
        val viewModel: PopupViewModel = createViewModel()
        viewModel.editState.test {
            viewModel.startEdit(inputFolder)
            runCurrent()
            val actualState: PopupEditState.Editing = expectMostRecentItem() as PopupEditState.Editing
            assertEquals(inputFolder, actualState.owner)
            assertEquals(ShortcutsResult.Available(shortcuts = emptyList()), actualState.shortcuts)
        }
    }

    @Test
    fun `an empty Folder card opens the picker directly`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.pickerState.test {
            viewModel.open(inputFolder, INPUT_ANCHOR)
            runCurrent()
            viewModel.startFolderPicking()
            runCurrent()
            val picking: PopupPickerState.Picking = expectMostRecentItem() as PopupPickerState.Picking
            assertEquals(emptySet<String>(), picking.selectedKeys)
            val candidates: List<Launchable> = picking.groups.flatMap { group -> group.items }
            assertEquals(listOf(inputApp, inputRailLaunchable), candidates)
            assertFalse(candidates.any { candidate -> candidate is Folder || candidate is ShortcutLaunchable })
        }
    }

    @Test
    fun `confirming the Folder picker adds items without opening the editor`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.pickerState.test {
            viewModel.open(inputFolder, INPUT_ANCHOR)
            runCurrent()
            viewModel.startFolderPicking()
            runCurrent()
            assertTrue(expectMostRecentItem() is PopupPickerState.Picking)
            viewModel.confirmPicking(selectedInOrder = listOf(inputOtherApp.key))
            runCurrent()
            assertEquals(PopupPickerState.Closed, expectMostRecentItem())
        }
        runCurrent()
        assertEquals(listOf(Triple(inputFolder.key, listOf(inputOtherApp.key), emptySet<String>())), fakePopupRepository.setItemsCalls)
        assertEquals(PopupEditState.Closed, viewModel.editState.value)
    }

    @Test
    fun `deleting a Folder confirms, closes its card and removes it`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        backgroundScope.launch { viewModel.popupState.collect() }
        backgroundScope.launch { viewModel.menuState.collect() }
        viewModel.open(inputFolder, INPUT_ANCHOR)
        runCurrent()
        viewModel.openMenu()
        runCurrent()
        viewModel.requestDeleteFolder()
        assertEquals(inputFolder, viewModel.folderDeleteState.value)
        viewModel.confirmDeleteFolder()
        runCurrent()
        assertEquals(null, viewModel.folderDeleteState.value)
        assertEquals(PopupUiState.Closed, viewModel.popupState.value)
        assertEquals(listOf(inputFolder), fakeFolderRepository.deletedFolders)
    }

    @Test
    fun `cancelling Folder deletion keeps the card and the Folder`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        backgroundScope.launch { viewModel.popupState.collect() }
        backgroundScope.launch { viewModel.menuState.collect() }
        viewModel.open(inputFolder, INPUT_ANCHOR)
        runCurrent()
        viewModel.openMenu()
        runCurrent()
        viewModel.requestDeleteFolder()
        viewModel.cancelDeleteFolder()
        assertEquals(null, viewModel.folderDeleteState.value)
        assertTrue(viewModel.popupState.value is PopupUiState.Open)
        assertTrue(fakeFolderRepository.deletedFolders.isEmpty())
    }

    @Test
    fun `shows a Folder in the App List`() = runTest {
        val viewModel: PopupViewModel = createViewModel()

        viewModel.setShowInAppList(inputFolder, true)
        runCurrent()

        assertEquals(listOf(inputFolder to true), fakeFolderRepository.showInAppListChanges)
    }

    @Test
    fun `add to folder lists the Folders in creation order and reflects membership`() = runTest {
        fakePopupRepository.setItems(ownerKey = inputSecondFolder.key, items = listOf(inputApp))
        val viewModel: PopupViewModel = createViewModel()
        backgroundScope.launch { viewModel.addToFolderState.collect() }

        viewModel.startAddToFolder(inputApp)
        runCurrent()

        val actualState: AddToFolderState.Open = viewModel.addToFolderState.value as AddToFolderState.Open
        assertEquals(inputApp, actualState.launchable)
        assertEquals(listOf(inputFolder, inputSecondFolder), actualState.folders)
        assertEquals(setOf(inputSecondFolder.key), actualState.membership)
    }

    @Test
    fun `toggling membership adds and removes the app immediately`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        backgroundScope.launch { viewModel.addToFolderState.collect() }
        viewModel.startAddToFolder(inputApp)
        runCurrent()

        viewModel.toggleMembership(folderKey = inputFolder.key, member = true)
        runCurrent()

        assertEquals(setOf(inputFolder.key), (viewModel.addToFolderState.value as AddToFolderState.Open).membership)
        assertEquals(
            listOf(Triple(inputFolder.key, listOf(inputApp.key), emptySet<String>())),
            fakePopupRepository.setItemsCalls
        )

        viewModel.toggleMembership(folderKey = inputFolder.key, member = false)
        runCurrent()

        assertTrue((viewModel.addToFolderState.value as AddToFolderState.Open).membership.isEmpty())
        assertEquals(
            Triple(inputFolder.key, emptyList<String>(), setOf(inputApp.key)),
            fakePopupRepository.setItemsCalls.last()
        )
    }

    @Test
    fun `startNewFolder creates a favorite Folder with the app and reports it`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        val addedFolders: MutableList<Folder> = mutableListOf()
        backgroundScope.launch { viewModel.folderAdditions.collect { folder -> addedFolders += folder } }
        backgroundScope.launch { viewModel.addToFolderState.collect() }
        viewModel.startAddToFolder(inputApp)
        runCurrent()

        viewModel.startNewFolder(name = "Games")
        runCurrent()

        assertEquals(
            listOf(FolderCreateRequest(name = "Games", initialItems = listOf(inputApp.key), addToFavorites = true)),
            fakeFolderRepository.createRequests
        )
        assertEquals(listOf("Games"), addedFolders.map { folder -> folder.name })
    }

    @Test
    fun `the add to folder sheet closes when its Launchable disappears`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        backgroundScope.launch { viewModel.addToFolderState.collect() }
        viewModel.startAddToFolder(inputApp)
        runCurrent()
        assertTrue(viewModel.addToFolderState.value is AddToFolderState.Open)

        fakeLaunchableRepository.setGroups(
            groups = listOf(LaunchableGroup.Star, LaunchableGroup.Builtin(items = listOf(inputRailLaunchable)))
        )
        runCurrent()

        assertEquals(AddToFolderState.Closed, viewModel.addToFolderState.value)
    }

    @Test
    fun `a deleted Folder disappears from the add to folder sheet`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        backgroundScope.launch { viewModel.addToFolderState.collect() }
        viewModel.startAddToFolder(inputApp)
        runCurrent()

        fakeFolderRepository.delete(inputSecondFolder)
        runCurrent()

        val actualState: AddToFolderState.Open = viewModel.addToFolderState.value as AddToFolderState.Open
        assertEquals(listOf(inputFolder), actualState.folders)
    }

    @Test
    fun `ignores adding a Folder to a Folder`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        viewModel.addToFolderState.test {
            assertEquals(AddToFolderState.Closed, expectMostRecentItem())

            viewModel.startAddToFolder(inputFolder)
            runCurrent()

            expectNoEvents()
        }
    }

    @Test
    fun `Back closes the add to folder sheet`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        backgroundScope.launch { viewModel.addToFolderState.collect() }
        viewModel.startAddToFolder(inputApp)
        runCurrent()

        assertTrue(viewModel.onBack())

        assertEquals(AddToFolderState.Closed, viewModel.addToFolderState.value)
    }

    @Test
    fun `starting an edit closes the add to folder sheet`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        backgroundScope.launch { viewModel.addToFolderState.collect() }
        viewModel.startAddToFolder(inputApp)
        runCurrent()

        viewModel.startEdit(inputApp)
        runCurrent()

        assertEquals(AddToFolderState.Closed, viewModel.addToFolderState.value)
    }

    @Test
    fun `shows the owner's widget at the top of the card`() = runTest {
        val viewModel: PopupViewModel = createWidgetViewModel()
        viewModel.popupState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
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
        val viewModel: PopupViewModel = createWidgetViewModel(
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
            viewModel.open(inputApp, INPUT_ANCHOR)
            runCurrent()

            val actualState: PopupUiState.Open = expectMostRecentItem() as PopupUiState.Open

            assertEquals(240, actualState.widget?.heightDp)
        }
    }

    @Test
    fun `owner menu offers Add to stack and widget actions instead of Add widget`() = runTest {
        val viewModel: PopupViewModel = createWidgetViewModel()
        viewModel.menuState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
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
    fun `widget menu offers Resize, Reconfigure, Remove widget and App info`() = runTest {
        val viewModel: PopupViewModel = createWidgetViewModel(
            providerInfo = appWidgetProviderInfo(
                isVerticallyResizable = true,
                isReconfigurable = true,
                hasConfigureActivity = true
            )
        )
        viewModel.menuState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            runCurrent()
            viewModel.openWidgetMenu(widgetId = 6L)
            runCurrent()

            val actualMenu: PopupMenuState.Open = expectMostRecentItem() as PopupMenuState.Open

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
        val mockAppWidgetHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource(
            initialProviders = listOf(inputOtherProvider, inputOwnerProvider)
        )
        val viewModel: PopupViewModel = createViewModel(appWidgetHost = mockAppWidgetHost)
        backgroundScope.launch { viewModel.popupState.collect() }
        runCurrent()
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()

        viewModel.startAddWidget()
        runCurrent()

        val actualPicking: AddWidgetFlowState.Picking = viewModel.addFlowState.value as AddWidgetFlowState.Picking
        assertEquals(inputApp.key, actualPicking.origin.ownerKey)
        assertEquals(listOf("Zeta", "Alpha"), actualPicking.groups.map { group -> group.appLabel })
    }

    @Test
    fun `reopens the card for the owner after the add flow`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()
        viewModel.startAddWidget()
        runCurrent()

        val restoredViewModel: PopupViewModel = createViewModel(savedStateHandle = inputSavedStateHandle)
        restoredViewModel.popupState.test {
            runCurrent()

            val actualState: PopupUiState.Open = expectMostRecentItem() as PopupUiState.Open

            assertEquals(inputApp, actualState.owner)
            assertNull(actualState.anchor)
        }
    }

    @Test
    fun `removeWidget frees the host view and reports the Widget removed message`() = runTest {
        val viewModel: PopupViewModel = createWidgetViewModel()
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()

        viewModel.events.test {
            viewModel.removeWidget()
            runCurrent()

            assertEquals(WidgetEvent.RemoveHostView(appWidgetId = AppWidgetId(9)), awaitItem())
            assertEquals(WidgetEvent.ShowMessage(message = CoreUiR.string.widget_removed), awaitItem())
        }
        assertEquals(listOf(6L), fakeRemoveAppWidget.removedWidgetIds)
    }

    @Test
    fun `Back finishes Resize and keeps the card open`() = runTest {
        val viewModel: PopupViewModel = createWidgetViewModel(
            providerInfo = appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 200)
        )
        backgroundScope.launch { viewModel.popupState.collect() }
        runCurrent()
        viewModel.open(inputApp, INPUT_ANCHOR)
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
        val viewModel: PopupViewModel = createWidgetViewModel(
            providerInfo = appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 200)
        )
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, INPUT_ANCHOR)
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
        val viewModel: PopupViewModel = createWidgetViewModel()
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()

        inputHomeEditState.tryEnter(HomeEditMode.Resizing(placeId = 5L))
        runCurrent()

        assertTrue(viewModel.popupState.value is PopupUiState.Open)
    }

    @Test
    fun `closes the card when Resize targets another place`() = runTest {
        val viewModel: PopupViewModel = createWidgetViewModel()
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()

        inputHomeEditState.tryEnter(HomeEditMode.Resizing(placeId = 99L))
        runCurrent()

        assertEquals(PopupUiState.Closed, viewModel.popupState.value)
    }

    @Test
    fun `removed owner widget ids drop their host views`() = runTest {
        val viewModel: PopupViewModel = createViewModel()

        viewModel.events.test {
            fakePopupRepository.emitRemovedWidget(appWidgetId = AppWidgetId(33))
            runCurrent()

            assertEquals(WidgetEvent.RemoveHostView(appWidgetId = AppWidgetId(33)), awaitItem())
        }
    }

    @Test
    fun `leaving the app keeps the card open while an add flow is pending`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()
        viewModel.startAddWidget()
        runCurrent()

        viewModel.onStopped()

        assertTrue(viewModel.popupState.value is PopupUiState.Open)
    }

    @Test
    fun `leaving the app closes the card when no add flow is pending`() = runTest {
        val viewModel: PopupViewModel = createViewModel()
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()

        viewModel.onStopped()

        assertEquals(PopupUiState.Closed, viewModel.popupState.value)
    }

    @Test
    fun `shows the owner's widget stack with its remembered page`() = runTest {
        val mockRepository: FakeWidgetAreaRepository = stackRepository(
            place = popupStackPlace(placeId = 5L, heightDp = 120, currentPage = 1, ownerKey = inputApp.key)
        )
        val viewModel: PopupViewModel = createStackViewModel(mockRepository = mockRepository)
        viewModel.popupState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
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
        val mockSettingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            initialAreaSettings = WidgetAreaSettings(stackIndicatorVisible = false)
        )
        val viewModel: PopupViewModel = createStackViewModel(mockSettingsRepository = mockSettingsRepository)
        viewModel.popupState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            runCurrent()

            val actualWidget: PopupWidgetState = (expectMostRecentItem() as PopupUiState.Open).widget!!

            assertFalse(actualWidget.indicatorVisible)
        }
    }

    @Test
    fun `persists the stack page when paging settles`() = runTest {
        val mockRepository: FakeWidgetAreaRepository = stackRepository(
            place = popupStackPlace(placeId = 5L, ownerKey = inputApp.key)
        )
        val viewModel: PopupViewModel = createStackViewModel(mockRepository = mockRepository)
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()

        viewModel.onPageChanged(page = 1)
        advanceUntilIdle()

        assertEquals(listOf(5L to 1), mockRepository.currentPageUpdates)
    }

    @Test
    fun `keeps the last settled page when paging back before the debounce`() = runTest {
        val mockRepository: FakeWidgetAreaRepository = stackRepository(
            place = popupStackPlace(placeId = 5L, ownerKey = inputApp.key)
        )
        val viewModel: PopupViewModel = createStackViewModel(mockRepository = mockRepository)
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()

        viewModel.onPageChanged(page = 1)
        advanceTimeBy(100)
        viewModel.onPageChanged(page = 0)
        advanceUntilIdle()

        assertEquals(listOf(5L to 0), mockRepository.currentPageUpdates)
    }

    @Test
    fun `ignores a settled page outside the stack`() = runTest {
        val mockRepository: FakeWidgetAreaRepository = stackRepository(
            place = popupStackPlace(placeId = 5L, ownerKey = inputApp.key)
        )
        val viewModel: PopupViewModel = createStackViewModel(mockRepository = mockRepository)
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()

        viewModel.onPageChanged(page = 2)
        advanceUntilIdle()

        assertTrue(mockRepository.currentPageUpdates.isEmpty())
    }

    @Test
    fun `owner menu hides Add to stack at the stack cap`() = runTest {
        val mockRepository: FakeWidgetAreaRepository = stackRepository(
            place = popupStackPlace(placeId = 5L, ownerKey = inputApp.key)
        )
        val mockSettingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            initialAreaSettings = WidgetAreaSettings(maxStackPages = 2)
        )
        val viewModel: PopupViewModel = createStackViewModel(
            mockRepository = mockRepository,
            mockSettingsRepository = mockSettingsRepository
        )
        viewModel.menuState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
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
        val mockRepository: FakeWidgetAreaRepository = stackRepository(
            place = popupStackPlace(placeId = 5L, ownerKey = inputApp.key)
        )
        val viewModel: PopupViewModel = createStackViewModel(mockRepository = mockRepository)
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()
        viewModel.openWidgetMenu(widgetId = 7L)
        runCurrent()

        viewModel.events.test {
            viewModel.removeWidget()
            runCurrent()

            assertEquals(WidgetEvent.RemoveHostView(appWidgetId = AppWidgetId(9)), awaitItem())
            assertEquals(WidgetEvent.ShowMessage(message = CoreUiR.string.widget_removed), awaitItem())
        }

        assertEquals(listOf(7L), fakeRemoveAppWidget.removedWidgetIds)
    }

    @Test
    fun `widget menu offers Remove widget for a single page`() = runTest {
        val mockRepository: FakeWidgetAreaRepository = stackRepository(
            place = popupStackPlace(placeId = 5L, widgetIds = listOf(6L), appWidgetIds = listOf(9), ownerKey = inputApp.key)
        )
        val viewModel: PopupViewModel = createStackViewModel(mockRepository = mockRepository)
        viewModel.menuState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            runCurrent()
            viewModel.openWidgetMenu(widgetId = 6L)
            runCurrent()

            val actualMenu: PopupMenuState.Open = expectMostRecentItem() as PopupMenuState.Open

            assertEquals(
                listOf(PopupMenuItem.ResizeWidget, PopupMenuItem.RemoveWidget, PopupMenuItem.AppInfo),
                actualMenu.items
            )
            assertEquals(6L, actualMenu.widgetPage?.widgetId)
        }
    }

    @Test
    fun `widget menu offers Remove from stack for a multi-page place`() = runTest {
        val viewModel: PopupViewModel = createStackViewModel()
        viewModel.menuState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            runCurrent()
            viewModel.openWidgetMenu(widgetId = 7L)
            runCurrent()

            val actualMenu: PopupMenuState.Open = expectMostRecentItem() as PopupMenuState.Open

            assertEquals(
                listOf(PopupMenuItem.ResizeWidget, PopupMenuItem.RemoveFromStack, PopupMenuItem.AppInfo),
                actualMenu.items
            )
            assertEquals(7L, actualMenu.widgetPage?.widgetId)
        }
    }

    @Test
    fun `Add to stack appends a page and selects it`() = runTest {
        val inputProvider: WidgetProviderInfo = appWidgetProviderInfo(
            provider = WidgetProvider(packageName = "com.other", className = "OtherWidget"),
            appPackageName = "com.other",
            appLabel = "Other"
        )
        val mockRepository: FakeWidgetAreaRepository = stackRepository(
            place = popupStackPlace(
                placeId = 5L,
                widgetIds = listOf(6L),
                appWidgetIds = listOf(9),
                heightDp = 120,
                ownerKey = inputApp.key
            )
        )
        val mockHost: FakeAppWidgetHostSource = stackHost(providers = listOf(inputProvider))
        val viewModel: PopupViewModel = createStackViewModel(mockRepository = mockRepository, mockHost = mockHost)
        backgroundScope.launch { viewModel.popupState.collect() }
        runCurrent()
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()

        viewModel.startAddWidget()
        runCurrent()
        viewModel.pickProvider(provider = inputProvider)
        runCurrent()

        val actualWidget: PopupWidgetState = viewModel.widgetState.value!!
        assertEquals(2, actualWidget.pages.size)
        assertEquals(1, actualWidget.currentPage)
        assertEquals(InsertPosition.IntoStack(placeId = 5L), mockRepository.addedAppWidgets.single().insertAt)
        assertEquals(inputApp.key, mockRepository.addedAppWidgets.single().ownerKey)
    }

    @Test
    fun `startAddWidget at the stack cap reports that the stack is full`() = runTest {
        val mockRepository: FakeWidgetAreaRepository = stackRepository(
            place = popupStackPlace(placeId = 5L, ownerKey = inputApp.key)
        )
        val mockSettingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository(
            initialAreaSettings = WidgetAreaSettings(maxStackPages = 2)
        )
        val viewModel: PopupViewModel = createStackViewModel(
            mockRepository = mockRepository,
            mockSettingsRepository = mockSettingsRepository
        )
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()

        viewModel.events.test {
            viewModel.startAddWidget()
            runCurrent()

            assertEquals(WidgetEvent.ShowMessage(message = CoreUiR.string.stack_full), awaitItem())
        }

        assertEquals(AddWidgetFlowState.Idle, viewModel.addFlowState.value)
    }

    @Test
    fun `clamps the shared height to the intersection of the pages' bounds`() = runTest {
        val mockRepository: FakeWidgetAreaRepository = stackRepository(
            place = popupStackPlace(placeId = 5L, heightDp = 300, ownerKey = inputApp.key)
        )
        val mockHost: FakeAppWidgetHostSource = stackHost(
            providerInfos = mapOf(
                9 to appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 200),
                10 to appWidgetProviderInfo(isVerticallyResizable = true, maxResizeHeightDp = 160)
            )
        )
        val viewModel: PopupViewModel = createStackViewModel(mockRepository = mockRepository, mockHost = mockHost)
        viewModel.popupState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            runCurrent()

            val actualWidget: PopupWidgetState = (expectMostRecentItem() as PopupUiState.Open).widget!!

            assertEquals(160, actualWidget.heightDp)
            assertEquals(160, actualWidget.bounds.maxDp)
        }
    }

    @Test
    fun `hides Resize for a stack with an unavailable page`() = runTest {
        val inputPlace: WidgetPlace = popupStackPlace(placeId = 5L, ownerKey = inputApp.key)
        val mockRepository: FakeWidgetAreaRepository = stackRepository(
            place = inputPlace.copy(
                widgets = inputPlace.widgets.map { widget ->
                    (widget as PlacedWidget.AppWidget).copy(availability = WidgetAvailability.ProfilePaused)
                }
            )
        )
        val viewModel: PopupViewModel = createStackViewModel(mockRepository = mockRepository)
        viewModel.menuState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
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
        val viewModel: PopupViewModel = createWidgetViewModel(providerInfo = appWidgetProviderInfo(isVerticallyResizable = false))
        viewModel.menuState.test {
            viewModel.open(inputApp, INPUT_ANCHOR)
            runCurrent()
            viewModel.openMenu()
            runCurrent()

            val actualMenu: PopupMenuState.Open = expectMostRecentItem() as PopupMenuState.Open

            assertFalse(PopupMenuItem.ResizeWidget in actualMenu.items)
        }
    }

    @Test
    fun `removing the current page clamps and persists the last page`() = runTest {
        val mockRepository: FakeWidgetAreaRepository = stackRepository(
            place = popupStackPlace(
                placeId = 5L,
                widgetIds = listOf(6L, 7L, 8L),
                appWidgetIds = listOf(9, 10, 11),
                currentPage = 2,
                ownerKey = inputApp.key
            )
        )
        val mockHost: FakeAppWidgetHostSource = stackHost()
        val viewModel: PopupViewModel = createStackViewModel(
            mockRepository = mockRepository,
            mockHost = mockHost,
            removeAppWidget = IRemoveAppWidgetImpl(repository = mockRepository, host = mockHost)
        )
        backgroundScope.launch { viewModel.popupState.collect() }
        viewModel.open(inputApp, INPUT_ANCHOR)
        runCurrent()
        viewModel.openWidgetMenu(widgetId = 8L)
        runCurrent()

        viewModel.removeWidget()
        advanceUntilIdle()

        assertEquals(listOf(6L, 7L), viewModel.widgetState.value?.pages?.map { page -> page.widgetId })
        assertEquals(listOf(5L to 1), mockRepository.currentPageUpdates)
    }

    private fun TestScope.createWidgetViewModel(
        widgetPlace: WidgetPlace = appWidgetPlace(placeId = 5L, widgetId = 6L, appWidgetId = 9, ownerKey = inputApp.key),
        providerInfo: WidgetProviderInfo? = appWidgetProviderInfo(isVerticallyResizable = true)
    ): PopupViewModel {
        val mockRepository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(widgetPlace))
        val mockHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        if (providerInfo != null) {
            val appWidgetId: AppWidgetId = AppWidgetId((widgetPlace.widgets.single() as PlacedWidget.AppWidget).appWidgetId)
            mockHost.providerInfoById[appWidgetId] = providerInfo
        }
        return createViewModel(widgetRepository = mockRepository, appWidgetHost = mockHost)
    }

    private fun stackRepository(place: WidgetPlace = popupStackPlace(placeId = 5L, ownerKey = inputApp.key)): FakeWidgetAreaRepository =
        FakeWidgetAreaRepository(listOf(place))

    private fun stackHost(
        providers: List<WidgetProviderInfo> = emptyList(),
        providerInfos: Map<Int, WidgetProviderInfo> = mapOf(
            9 to appWidgetProviderInfo(isVerticallyResizable = true),
            10 to appWidgetProviderInfo(isVerticallyResizable = true),
            11 to appWidgetProviderInfo(isVerticallyResizable = true)
        )
    ): FakeAppWidgetHostSource = FakeAppWidgetHostSource(initialProviders = providers).apply {
        providerInfos.forEach { (appWidgetId, info) -> providerInfoById[AppWidgetId(appWidgetId)] = info }
    }

    private fun TestScope.createStackViewModel(
        mockRepository: FakeWidgetAreaRepository = stackRepository(),
        mockHost: FakeAppWidgetHostSource = stackHost(),
        mockSettingsRepository: FakeWidgetSettingsRepository = fakeWidgetSettingsRepository,
        removeAppWidget: IRemoveAppWidget = fakeRemoveAppWidget
    ): PopupViewModel = createViewModel(
        widgetRepository = mockRepository,
        appWidgetHost = mockHost,
        widgetSettingsRepository = mockSettingsRepository,
        removeAppWidget = removeAppWidget
    )

    private fun TestScope.createViewModel(
        homeEditState: HomeEditState = inputHomeEditState,
        savedStateHandle: SavedStateHandle = inputSavedStateHandle,
        widgetRepository: FakeWidgetAreaRepository = fakeWidgetAreaRepository,
        appWidgetHost: FakeAppWidgetHostSource = fakeAppWidgetHost,
        removeAppWidget: IRemoveAppWidget = fakeRemoveAppWidget,
        widgetSettingsRepository: FakeWidgetSettingsRepository = fakeWidgetSettingsRepository,
        notificationSource: INotificationSource = createNotificationSource(),
        clockSource: IClockSource = FakeClockSource(initialReading = clockReading(instant = NOW))
    ): PopupViewModel = PopupViewModel(
        launchableRepository = fakeLaunchableRepository,
        shortcutSource = fakeShortcutSource,
        popupRepository = fakePopupRepository,
        folderRepository = fakeFolderRepository,
        notificationSource = notificationSource,
        clockSource = clockSource,
        homeEditState = homeEditState,
        widgetRepository = widgetRepository,
        addFlowController = createAddController(
            repository = widgetRepository,
            appWidgetHost = appWidgetHost,
            homeEditState = homeEditState,
            savedStateHandle = savedStateHandle,
            settingsRepository = widgetSettingsRepository
        ),
        resizeController = IWidgetResizeControllerImpl(
            repository = widgetRepository,
            appWidgetHost = appWidgetHost,
            settingsRepository = widgetSettingsRepository,
            homeEditState = homeEditState
        ),
        appWidgetHost = appWidgetHost,
        removeAppWidget = removeAppWidget,
        widgetLifecycleReconciler = IWidgetLifecycleReconcilerImpl(dao = FakeWidgetAreaDao(), host = appWidgetHost),
        widgetSettingsRepository = widgetSettingsRepository,
        savedStateHandle = savedStateHandle
    )

    private fun TestScope.createNotificationSource(accessCheck: () -> Boolean = { true }): INotificationSource = INotificationSourceImpl(
        context = RuntimeEnvironment.getApplication(),
        scope = backgroundScope,
        popupRepository = fakePopupRepository,
        relay = mockNotificationRelay,
        isAccessGranted = { accessCheck() }
    )

    private fun clockReading(instant: Instant): ClockReading = ClockReading(
        instant = instant,
        zoneId = ZoneId.of("UTC"),
        locale = Locale.US,
        isSystem24Hour = true
    )
}
