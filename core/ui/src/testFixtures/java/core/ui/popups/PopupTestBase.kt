package core.ui.popups

import android.content.ComponentName
import android.os.UserHandle
import core.data.apps.model.App
import core.data.folders.Folder
import core.data.folders.IFolderRepository
import core.data.icons.model.Icon
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RailLaunchable
import core.data.popups.IPopupRepository
import core.data.popups.PopupItem
import core.data.prefs.IPreferencesRepository
import core.data.shortcuts.AppShortcuts
import core.data.shortcuts.IShortcutSource
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult
import core.data.testing.MainDispatcherRule
import core.data.testing.PopupRepositoryState
import core.data.testing.TEST_NOTIFICATION_PACKAGE_NAME
import core.data.testing.createFolderRepositoryMock
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createLaunchableState
import core.data.testing.createNotificationProfile
import core.data.testing.createPopupRepositoryMock
import core.data.testing.createPreferencesRepositoryMock
import core.data.testing.createShortcutSourceMock
import core.data.widgets.AppWidgetId
import core.ui.layout.HomeEditState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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
    private val launchableState: MutableStateFlow<LaunchableState> = MutableStateFlow(
        createLaunchableState(groups = inputGroups, folders = listOf(inputFolder))
    )
    private val shortcutResult: MutableStateFlow<ShortcutsResult> = MutableStateFlow(
        ShortcutsResult.Available(shortcuts = emptyList())
    )
    private val shortcutsWithApps: MutableStateFlow<List<AppShortcuts>> = MutableStateFlow(emptyList())
    private val popupRepositoryState: PopupRepositoryState = PopupRepositoryState()
    private val folders: MutableStateFlow<List<Folder>> = MutableStateFlow(listOf(inputFolder, inputSecondFolder))
    protected val mockLaunchableRepository: ILaunchableRepository = createLaunchableRepositoryMock(launchableState)
    protected val mockShortcutSource: IShortcutSource = createShortcutSourceMock(
        shortcutsResult = shortcutResult,
        appsWithShortcuts = shortcutsWithApps
    )
    protected val mockPopupRepository: IPopupRepository = createPopupRepositoryMock(
        state = popupRepositoryState,
        resolveLaunchable = { key ->
            listOf(inputApp, inputOtherApp, inputPausedApp, inputRailLaunchable).firstOrNull { launchable -> launchable.key == key }
        }
    )
    protected val mockFolderRepository: IFolderRepository = createFolderRepositoryMock(folders)
    protected val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
    protected val inputHomeEditState: HomeEditState = HomeEditState()

    protected fun setLaunchableGroups(groups: List<LaunchableGroup>) {
        launchableState.value = createLaunchableState(groups = groups, folders = listOf(inputFolder))
    }

    protected fun setFolders(inputFolders: List<Folder>) {
        folders.value = inputFolders
    }

    protected fun setShortcutResult(result: ShortcutsResult) {
        shortcutResult.value = result
    }

    protected fun setAppsWithShortcuts(apps: List<AppShortcuts>) {
        shortcutsWithApps.value = apps
    }

    protected fun setHiddenShortcuts(ownerKey: String, shortcutIds: Set<String>) {
        popupRepositoryState.hiddenIdsByOwner.update { current -> current + (ownerKey to shortcutIds) }
    }

    protected fun setPopupItems(ownerKey: String, items: List<PopupItem>) {
        popupRepositoryState.itemsByOwner.update { current -> current + (ownerKey to items) }
    }

    protected fun setPopupLaunchables(ownerKey: String, launchables: List<Launchable>) {
        setPopupItems(ownerKey = ownerKey, items = launchables.map { launchable -> PopupItem.Launchable(launchable) })
    }

    protected fun setNotificationsEnabled(enabled: Boolean) {
        popupRepositoryState.notificationsEnabled.value = enabled
    }

    protected fun emitRemovedWidget(appWidgetId: AppWidgetId) {
        popupRepositoryState.removedWidgetIds.tryEmit(appWidgetId)
    }

    protected fun deleteFolder(folder: Folder) {
        folders.value = folders.value.filterNot { existing -> existing.key == folder.key }
    }

    protected fun TestScope.createEditor(
        session: IPopupSession = IPopupSessionImpl(),
        homeEditState: HomeEditState = inputHomeEditState
    ): IPopupEditor = IPopupEditorImpl(
        parentScope = backgroundScope,
        launchableRepository = mockLaunchableRepository,
        shortcutSource = mockShortcutSource,
        popupRepository = mockPopupRepository,
        homeEditState = homeEditState,
        session = session
    )

    protected fun TestScope.createPicker(
        session: IPopupSession = IPopupSessionImpl(),
        homeEditState: HomeEditState = inputHomeEditState
    ): IPopupPicker = IPopupPickerImpl(
        parentScope = backgroundScope,
        launchableRepository = mockLaunchableRepository,
        shortcutSource = mockShortcutSource,
        popupRepository = mockPopupRepository,
        homeEditState = homeEditState,
        session = session
    )

    protected fun TestScope.createFolder(
        session: IPopupSession = IPopupSessionImpl(),
        homeEditState: HomeEditState = inputHomeEditState
    ): IPopupFolderActions = IPopupFolderActionsImpl(
        parentScope = backgroundScope,
        launchableRepository = mockLaunchableRepository,
        popupRepository = mockPopupRepository,
        folderRepository = mockFolderRepository,
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
