package core.popups

import android.content.ComponentName
import android.os.UserHandle
import core.data.apps.model.App
import core.data.folders.Folder
import core.data.icons.model.Icon
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.shortcuts.AppShortcuts
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult
import core.data.testing.FakeFolderRepository
import core.data.testing.FakeLaunchableRepository
import core.data.testing.FakePopupRepository
import core.data.testing.FakePreferencesRepository
import core.data.testing.FakeShortcutSource
import core.data.testing.MainDispatcherRule
import core.data.testing.TEST_NOTIFICATION_PACKAGE_NAME
import core.data.testing.createNotificationProfile
import core.ui.layout.HomeEditState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.Rule

internal const val TEST_ACTIVITY_NAME: String = "com.example.app.MainActivity"

/** The Pop-up fixtures and collaborator factories the feature tests build their sessions from. */
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
    protected val inputPausedApp: App = App(
        defaultName = "Paused",
        profile = profile,
        componentName = ComponentName("com.example.paused", "com.example.paused.MainActivity")
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
    protected val mockPreferencesRepository: FakePreferencesRepository = FakePreferencesRepository()
    protected val inputHomeEditState: HomeEditState = HomeEditState()

    protected fun TestScope.createEditor(
        session: IPopupSession = IPopupSessionImpl(),
        homeEditState: HomeEditState = inputHomeEditState
    ): IPopupEditor = IPopupEditorImpl(
        parentScope = backgroundScope,
        launchableRepository = fakeLaunchableRepository,
        shortcutSource = fakeShortcutSource,
        popupRepository = fakePopupRepository,
        homeEditState = homeEditState,
        session = session
    )

    protected fun TestScope.createPicker(
        session: IPopupSession = IPopupSessionImpl(),
        homeEditState: HomeEditState = inputHomeEditState
    ): IPopupPicker = IPopupPickerImpl(
        parentScope = backgroundScope,
        launchableRepository = fakeLaunchableRepository,
        shortcutSource = fakeShortcutSource,
        popupRepository = fakePopupRepository,
        homeEditState = homeEditState,
        session = session
    )

    protected fun TestScope.createFolder(
        session: IPopupSession = IPopupSessionImpl(),
        homeEditState: HomeEditState = inputHomeEditState
    ): IPopupFolderActions = IPopupFolderActionsImpl(
        parentScope = backgroundScope,
        launchableRepository = fakeLaunchableRepository,
        popupRepository = fakePopupRepository,
        folderRepository = fakeFolderRepository,
        preferencesRepository = mockPreferencesRepository,
        homeEditState = homeEditState,
        session = session
    )

    protected fun TestScope.createCollaborators(
        session: IPopupSession = IPopupSessionImpl(),
        homeEditState: HomeEditState = inputHomeEditState
    ): IPopupCollaborators = IPopupCollaboratorsImpl(
        session = session,
        editor = createEditor(session = session, homeEditState = homeEditState),
        picker = createPicker(session = session, homeEditState = homeEditState),
        folder = createFolder(session = session, homeEditState = homeEditState)
    )
}
