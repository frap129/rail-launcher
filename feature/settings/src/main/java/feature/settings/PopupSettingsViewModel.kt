package feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.favorites.IFavoritesRepository
import core.data.folders.Folder
import core.data.folders.IFolderRepository
import core.data.popups.IPopupRepository
import core.data.prefs.IPreferencesRepository
import core.util.EventChannel
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import core.util.launchLogged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
    val isRenameDialogOpen: Boolean = false
)

/** Owns the Settings list of every Folder, its actions sheet and its dialogs. */
class PopupSettingsViewModel(
    private val folderRepository: IFolderRepository,
    private val popupRepository: IPopupRepository,
    private val favoritesRepository: IFavoritesRepository,
    private val preferencesRepository: IPreferencesRepository
) : ViewModel() {
    private val selectedFolderKey: MutableStateFlow<String?> = MutableStateFlow(null)
    private val newFolderDialogOpen: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val renameDialogOpen: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val folderCreations: EventChannel<Folder> = EventChannel()

    /** Folders created from Settings, for its "Folder created" Snackbar. */
    val createdFolders: SharedFlow<Folder> = folderCreations.flow

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

    val uiState: StateFlow<PopupSettingsUiState> = combine(
        folders,
        selectedFolderKey,
        newFolderDialogOpen,
        renameDialogOpen
    ) { rows, selectedKey, isNaming, isRenaming ->
        val selectedFolder: PopupSettingsFolder? = rows.firstOrNull { row -> row.folder.key == selectedKey }
        PopupSettingsUiState(
            folders = rows,
            selectedFolder = selectedFolder,
            isNewFolderDialogOpen = isNaming,
            isRenameDialogOpen = isRenaming && selectedFolder != null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = PopupSettingsUiState()
    )

    fun openActions(folder: Folder) {
        renameDialogOpen.value = false
        selectedFolderKey.value = folder.key
    }

    fun closeActions() {
        renameDialogOpen.value = false
        selectedFolderKey.value = null
    }

    fun openNewFolderDialog() {
        newFolderDialogOpen.value = true
    }

    fun closeNewFolderDialog() {
        newFolderDialogOpen.value = false
    }

    fun openRenameDialog() {
        renameDialogOpen.value = true
    }

    fun closeRenameDialog() {
        renameDialogOpen.value = false
    }

    fun createFolder(name: String) {
        closeNewFolderDialog()
        launchLogged(message = "Failed to create Folder") {
            val folder: Folder = folderRepository.create(name = name, addToFavorites = false)
            folderCreations.send(folder)
        }
    }

    fun renameFolder(name: String) {
        val folder: Folder = selectedFolder()?.folder ?: return
        closeRenameDialog()
        preferencesRepository.setItemName(item = folder, name = name)
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

    fun setInAppList(inAppList: Boolean) {
        val folder: Folder = selectedFolder()?.folder ?: return
        if (inAppList == folder.showInAppList) {
            return
        }
        launchLogged(message = "Failed to change ${folder.key}'s App List placement") {
            folderRepository.setShowInAppList(folder = folder, show = inAppList)
        }
    }

    private fun selectedFolder(): PopupSettingsFolder? = folders.value.firstOrNull { row -> row.folder.key == selectedFolderKey.value }
}
