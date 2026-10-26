package feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.favorites.IFavoritesRepository
import core.data.folders.Folder
import core.data.folders.IFolderRepository
import core.data.notifications.INotificationSource
import core.data.popups.IPopupRepository
import core.util.EventChannel
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import core.util.launchLogged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Where a Folder shows, summarised under its name in Settings. */
enum class FolderLocation {
    Home,
    AppList,
    HomeAndAppList,
    NotShown
}

/** One Folder as the Settings list shows it. */
data class PopupSettingsFolder(val folder: Folder, val itemCount: Int, val isOnHome: Boolean) {
    val location: FolderLocation = when {
        isOnHome && folder.showInAppList -> FolderLocation.HomeAndAppList
        isOnHome -> FolderLocation.Home
        folder.showInAppList -> FolderLocation.AppList
        else -> FolderLocation.NotShown
    }
}

data class PopupSettingsUiState(
    val folders: List<PopupSettingsFolder> = emptyList(),
    val selectedFolder: PopupSettingsFolder? = null,
    val isNewFolderDialogOpen: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val notificationAccess: Boolean = false
)

private data class PopupSettingsRows(
    val folders: List<PopupSettingsFolder>,
    val notificationsEnabled: Boolean,
    val notificationAccess: Boolean
)

/** Owns the Settings list of every Folder, its actions sheet, its dialogs and the Notifications section. */
class PopupSettingsViewModel(
    private val folderRepository: IFolderRepository,
    private val popupRepository: IPopupRepository,
    private val notificationSource: INotificationSource,
    private val favoritesRepository: IFavoritesRepository
) : ViewModel() {
    private val selectedFolderKey: MutableStateFlow<String?> = MutableStateFlow(null)
    private val newFolderDialogOpen: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val folderCreations: EventChannel<Folder> = EventChannel()
    private val eventChannel: EventChannel<WidgetSettingsEvent> = EventChannel()

    /** Folders created from Settings, for its "Folder created" Snackbar. */
    val createdFolders: Flow<Folder> = folderCreations.flow

    /** Events the Notifications section asks the screen to run. */
    val events: Flow<WidgetSettingsEvent> = eventChannel.flow

    // Held eagerly so actions can resolve the selected Folder without a UI collector.
    private val folders: StateFlow<List<PopupSettingsFolder>> = combine(
        folderRepository.folders,
        popupRepository.itemCounts,
        favoritesRepository.favorites
    ) { folders, itemCounts, favorites ->
        val favoriteKeys: Set<String> = favorites.mapTo(mutableSetOf()) { favorite -> favorite.key }
        folders.map { folder ->
            PopupSettingsFolder(
                folder = folder,
                itemCount = itemCounts[folder.key] ?: 0,
                isOnHome = folder.key in favoriteKeys
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    private val rows: StateFlow<PopupSettingsRows> = combine(
        folders,
        popupRepository.notificationsEnabled,
        notificationSource.hasAccess
    ) { folderRows, notificationsEnabled, notificationAccess ->
        PopupSettingsRows(
            folders = folderRows,
            notificationsEnabled = notificationsEnabled,
            notificationAccess = notificationAccess
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = PopupSettingsRows(folders = emptyList(), notificationsEnabled = false, notificationAccess = false)
    )

    val uiState: StateFlow<PopupSettingsUiState> = combine(
        rows,
        selectedFolderKey,
        newFolderDialogOpen
    ) { settingsRows, selectedKey, isNaming ->
        val selectedFolder: PopupSettingsFolder? = settingsRows.folders.firstOrNull { row -> row.folder.key == selectedKey }
        PopupSettingsUiState(
            folders = settingsRows.folders,
            selectedFolder = selectedFolder,
            isNewFolderDialogOpen = isNaming,
            notificationsEnabled = settingsRows.notificationsEnabled,
            notificationAccess = settingsRows.notificationAccess
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = PopupSettingsUiState()
    )

    /** Persists whether Pop-ups show notifications, explaining access when it is missing. */
    fun setNotificationsEnabled(enabled: Boolean) {
        launchLogged(message = "Failed to change whether Pop-ups show notifications") {
            popupRepository.setNotificationsEnabled(enabled)
        }
        if (enabled && !notificationSource.hasAccess.value) {
            eventChannel.trySend(WidgetSettingsEvent.RequestNotificationAccess)
        }
    }

    fun openActions(folder: Folder) {
        selectedFolderKey.value = folder.key
    }

    fun closeActions() {
        selectedFolderKey.value = null
    }

    fun openNewFolderDialog() {
        newFolderDialogOpen.value = true
    }

    fun closeNewFolderDialog() {
        newFolderDialogOpen.value = false
    }

    fun createFolder(name: String) {
        closeNewFolderDialog()
        launchLogged(message = "Failed to create Folder") {
            val folder: Folder = folderRepository.create(name = name, addToFavorites = false)
            folderCreations.send(folder)
        }
    }

    fun setOnHome(onHome: Boolean) {
        val row: PopupSettingsFolder = selectedFolder() ?: return
        if (onHome == row.isOnHome) {
            return
        }
        launchLogged(message = "Failed to change ${row.folder.key}'s Home placement") {
            if (onHome) {
                favoritesRepository.add(row.folder)
            } else {
                favoritesRepository.remove(row.folder)
            }
        }
    }

    private fun selectedFolder(): PopupSettingsFolder? = folders.value.firstOrNull { row -> row.folder.key == selectedFolderKey.value }
}
