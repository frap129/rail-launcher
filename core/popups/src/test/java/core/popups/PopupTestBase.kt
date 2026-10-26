package core.popups

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import core.data.apps.model.App
import core.data.folders.Folder
import core.data.icons.model.Icon
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.notifications.INotificationListenerRelayImpl
import core.data.notifications.INotificationSource
import core.data.notifications.INotificationSourceImpl
import core.data.notifications.NotificationRecord
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
import core.data.testing.MainDispatcherRule
import core.data.testing.TEST_NOTIFICATION_OTHER_KEY
import core.data.testing.TEST_NOTIFICATION_PACKAGE_NAME
import core.data.testing.createNotificationProfile
import core.data.testing.createNotificationRecord
import core.data.testing.createOtherNotificationProfile
import core.data.time.ClockReading
import core.data.time.IClockSource
import core.data.widgets.IRemoveAppWidget
import core.data.widgets.IWidgetLifecycleReconcilerImpl
import core.ui.layout.HomeEditState
import core.widgets.IWidgetResizeControllerImpl
import core.widgets.testing.createAddController
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.robolectric.RuntimeEnvironment

internal const val TEST_ACTIVITY_NAME: String = "com.example.app.MainActivity"
internal const val TEST_WORK_PROFILE_SERIAL: Long = 42L
internal val TEST_NOW: Instant = Instant.ofEpochMilli(10_000_000L)
internal val TEST_INPUT_ANCHOR: PopupAnchor = PopupAnchor(rowTop = 400, rowBottom = 464, iconCenter = Offset(32f, 432f))

/** The card, editor, pickers and Folder actions connected by one Pop-up session. */
class PopupTestSession(
    val session: IPopupSession,
    val card: PopupCardViewModel,
    val editor: PopupEditorViewModel,
    val picker: PopupPickerViewModel,
    val folder: PopupFolderViewModel
)

/** The fixtures all Pop-up ViewModel test classes build their session from. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class PopupTestBase {
    @get:Rule
    val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

    protected val profile: UserHandle = createNotificationProfile()
    protected val inputApp: App = App(
        defaultName = "Example",
        profile = profile,
        componentName = ComponentName(TEST_NOTIFICATION_PACKAGE_NAME, TEST_ACTIVITY_NAME)
    )
    protected val inputOtherApp: App = App(
        defaultName = "Other",
        profile = profile,
        componentName = ComponentName("com.example.other", "com.example.other.MainActivity")
    )
    protected val inputOtherNotification: NotificationRecord = createNotificationRecord(
        key = TEST_NOTIFICATION_OTHER_KEY,
        packageName = inputOtherApp.packageName
    )
    protected val inputPausedApp: App = App(
        defaultName = "Paused",
        profile = profile,
        componentName = ComponentName("com.example.paused", "com.example.paused.MainActivity")
    )
    protected val inputWorkApp: App = App(
        defaultName = "Work",
        profile = createOtherNotificationProfile(),
        componentName = ComponentName("com.example.work", "com.example.work.MainActivity")
    )
    protected val inputRailLaunchable: RailLaunchable = RailLaunchable(
        defaultName = "Rail Settings",
        defaultIcon = Icon.RailIcon(resId = 0),
        deeplink = "rail-launcher://settings/main"
    )
    protected val inputFolder: Folder = Folder(folderId = 1L)
    protected val inputSecondFolder: Folder = Folder(folderId = 2L, name = "Work")
    protected val inputGroups: List<LaunchableGroup> = listOf(
        LaunchableGroup.Star,
        LaunchableGroup.InitialGroup(label = "E", items = listOf(inputApp)),
        LaunchableGroup.Builtin(items = listOf(inputRailLaunchable))
    )
    protected val inputShortcut: ShortcutLaunchable = ShortcutLaunchable(
        packageName = TEST_NOTIFICATION_PACKAGE_NAME,
        shortcutId = "shortcut-id",
        profile = profile,
        shortLabel = "Short label",
        longLabel = "Long label"
    )
    protected val inputOtherShortcut: ShortcutLaunchable = ShortcutLaunchable(
        packageName = inputOtherApp.packageName,
        shortcutId = "other-shortcut-id",
        profile = profile,
        shortLabel = "Other short label",
        longLabel = "Other long label"
    )
    protected val inputAppShortcuts: AppShortcuts = AppShortcuts(
        app = inputApp,
        result = ShortcutsResult.Available(shortcuts = listOf(inputShortcut))
    )
    protected val inputOtherAppShortcuts: AppShortcuts = AppShortcuts(
        app = inputOtherApp,
        result = ShortcutsResult.Available(shortcuts = listOf(inputOtherShortcut))
    )
    protected val fakeLaunchableRepository: FakeLaunchableRepository = FakeLaunchableRepository(
        groups = inputGroups,
        folders = listOf(inputFolder)
    )
    protected val fakeShortcutSource: FakeShortcutSource = FakeShortcutSource()
    protected val fakePopupRepository: FakePopupRepository = FakePopupRepository(resolveLaunchable = { key ->
        listOf(inputApp, inputOtherApp, inputPausedApp, inputRailLaunchable).firstOrNull { launchable -> launchable.key == key }
    })
    protected val fakeFolderRepository: FakeFolderRepository = FakeFolderRepository(initialFolders = listOf(inputFolder, inputSecondFolder))
    protected val inputHomeEditState: HomeEditState = HomeEditState()
    protected val inputSavedStateHandle: SavedStateHandle = SavedStateHandle()
    protected val fakeAppWidgetHost: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
    protected val fakeRemoveAppWidget: FakeRemoveAppWidget = FakeRemoveAppWidget()
    protected val fakeWidgetSettingsRepository: FakeWidgetSettingsRepository = FakeWidgetSettingsRepository()
    protected val fakeWidgetAreaRepository: FakeWidgetAreaRepository = FakeWidgetAreaRepository()
    protected val mockNotificationRelay: INotificationListenerRelayImpl = INotificationListenerRelayImpl()

    protected fun createEditor(
        session: IPopupSession = IPopupSessionImpl(),
        homeEditState: HomeEditState = inputHomeEditState
    ): PopupEditorViewModel = PopupEditorViewModel(
        launchableRepository = fakeLaunchableRepository,
        shortcutSource = fakeShortcutSource,
        popupRepository = fakePopupRepository,
        homeEditState = homeEditState,
        session = session
    )

    protected fun createPicker(
        session: IPopupSession = IPopupSessionImpl(),
        homeEditState: HomeEditState = inputHomeEditState
    ): PopupPickerViewModel = PopupPickerViewModel(
        launchableRepository = fakeLaunchableRepository,
        shortcutSource = fakeShortcutSource,
        popupRepository = fakePopupRepository,
        homeEditState = homeEditState,
        session = session
    )

    protected fun createFolder(
        session: IPopupSession = IPopupSessionImpl(),
        homeEditState: HomeEditState = inputHomeEditState
    ): PopupFolderViewModel = PopupFolderViewModel(
        launchableRepository = fakeLaunchableRepository,
        popupRepository = fakePopupRepository,
        folderRepository = fakeFolderRepository,
        homeEditState = homeEditState,
        session = session
    )

    protected fun TestScope.createCard(
        session: IPopupSession = IPopupSessionImpl(),
        homeEditState: HomeEditState = inputHomeEditState,
        savedStateHandle: SavedStateHandle = inputSavedStateHandle,
        widgetRepository: FakeWidgetAreaRepository = fakeWidgetAreaRepository,
        appWidgetHost: FakeAppWidgetHostSource = fakeAppWidgetHost,
        removeAppWidget: IRemoveAppWidget = fakeRemoveAppWidget,
        widgetSettingsRepository: FakeWidgetSettingsRepository = fakeWidgetSettingsRepository,
        notificationSource: INotificationSource = createNotificationSource(),
        clockSource: IClockSource = FakeClockSource(initialReading = clockReading(instant = TEST_NOW))
    ): PopupCardViewModel {
        val card: PopupCardViewModel = PopupCardViewModel(
            launchableRepository = fakeLaunchableRepository,
            shortcutSource = fakeShortcutSource,
            popupRepository = fakePopupRepository,
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
            savedStateHandle = savedStateHandle,
            session = session
        )
        session.attach(card = card)
        return card
    }

    protected fun TestScope.createSession(
        homeEditState: HomeEditState = inputHomeEditState,
        notificationSource: INotificationSource = createNotificationSource(),
        clockSource: IClockSource = FakeClockSource(initialReading = clockReading(instant = TEST_NOW))
    ): PopupTestSession {
        val session: IPopupSession = IPopupSessionImpl()
        val editor: PopupEditorViewModel = createEditor(session = session, homeEditState = homeEditState)
        val picker: PopupPickerViewModel = createPicker(session = session, homeEditState = homeEditState)
        val folder: PopupFolderViewModel = createFolder(session = session, homeEditState = homeEditState)
        val card: PopupCardViewModel = createCard(
            session = session,
            homeEditState = homeEditState,
            notificationSource = notificationSource,
            clockSource = clockSource
        )
        session.attach(editor = editor, picker = picker, folder = folder)
        return PopupTestSession(session = session, card = card, editor = editor, picker = picker, folder = folder)
    }

    /** Starts collecting [flows] and opens [owner]'s Pop-up on [card]. */
    protected fun TestScope.openPopup(card: PopupCardViewModel, owner: Launchable, flows: List<Flow<*>>): PopupCardViewModel {
        flows.forEach { flow -> backgroundScope.launch { flow.collect() } }
        card.open(owner, TEST_INPUT_ANCHOR)
        runCurrent()
        return card
    }

    /** Opens [owner]'s Pop-up and starts editing [editor], seeding [ownerItem] and [editorItem] as added items when given. */
    protected fun TestScope.openPopupWithEditor(
        owner: Launchable = inputApp,
        editor: Launchable = inputFolder,
        ownerItem: Launchable? = inputOtherApp,
        editorItem: Launchable? = inputRailLaunchable
    ): PopupTestSession {
        ownerItem?.let { item -> fakePopupRepository.setItems(ownerKey = owner.key, items = listOf(item)) }
        editorItem?.let { item -> fakePopupRepository.setItems(ownerKey = editor.key, items = listOf(item)) }
        val session: PopupTestSession = createSession()
        openPopup(
            card = session.card,
            owner = owner,
            flows = listOf(session.card.popupState, session.editor.editState, session.card.menuState)
        )
        session.editor.startEdit(editor)
        runCurrent()
        return session
    }

    protected fun TestScope.createNotificationSource(accessCheck: () -> Boolean = { true }): INotificationSource = INotificationSourceImpl(
        context = RuntimeEnvironment.getApplication(),
        scope = backgroundScope,
        popupRepository = fakePopupRepository,
        relay = mockNotificationRelay,
        isAccessGranted = { accessCheck() }
    )

    protected fun clockReading(instant: Instant): ClockReading = ClockReading(
        instant = instant,
        zoneId = ZoneId.of("UTC"),
        locale = Locale.US,
        isSystem24Hour = true
    )
}
