package core.popups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.folders.Folder
import core.data.folders.IFolderRepository
import core.data.launchables.ILaunchableRepository
import core.data.launchables.model.Launchable
import core.data.popups.IPopupRepository
import core.data.prefs.IPreferencesRepository
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import core.util.EventChannel
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import core.util.launchLogged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

private data class FolderMembership(val launchableKey: String?, val folderKeys: Set<String>)

/** Owns the Folder dialogs and the Add to Folder sheet. */
class PopupFolderViewModel(
    private val launchableRepository: ILaunchableRepository,
    private val popupRepository: IPopupRepository,
    private val folderRepository: IFolderRepository,
    private val preferencesRepository: IPreferencesRepository,
    private val homeEditState: HomeEditState,
    private val session: IPopupSession
) : ViewModel() {
    private val folderDialog: MutableStateFlow<FolderDialogState> = MutableStateFlow(FolderDialogState.Closed)
    private val addToFolderRequest: MutableStateFlow<Launchable?> = MutableStateFlow(null)
    private val folderAdditionRequests: EventChannel<Folder> = EventChannel()
    private val addToFolderKey: Flow<String?> = addToFolderRequest.map { launchable -> launchable?.key }
    private val addToFolderMembership: StateFlow<FolderMembership> = ownerScoped(
        scope = viewModelScope,
        targets = addToFolderKey,
        emptyValue = { launchableKey -> FolderMembership(launchableKey = launchableKey, folderKeys = emptySet()) },
        source = { launchableKey -> folderMemberships(launchableKey = launchableKey) }
    )

    /** The Folder dialog the collaborator is showing, if any. */
    val folderDialogState: StateFlow<FolderDialogState> = folderDialog

    /** Folders created from the Add to Folder sheet, for its "Added to ..." Snackbar. */
    val folderAdditions: Flow<Folder> = folderAdditionRequests.flow

    val addToFolderState: StateFlow<AddToFolderState> = combine(
        addToFolderRequest,
        launchableRepository.launchableState,
        folderRepository.folders,
        addToFolderMembership
    ) { requestedLaunchable, launchableState, folders, membership ->
        val launchable: Launchable? = requestedLaunchable?.let { requested ->
            launchableState.launchables.findLaunchable(key = requested.key)
        }
        if (launchable == null) {
            AddToFolderState.Closed
        } else {
            AddToFolderState.Open(launchable = launchable, folders = folders, membership = membership.folderKeys)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = AddToFolderState.Closed
    )

    init {
        observeTargetLoss(
            scope = viewModelScope,
            targetKeys = addToFolderKey,
            launchableRepository = launchableRepository,
            homeEditState = homeEditState,
            isClosedByMode = { mode -> mode != HomeEditMode.None },
            onTargetLost = ::closeAddToFolder
        )
    }

    /** Shows the delete confirmation when [target] is the Folder whose menu is open. */
    fun requestDeleteFolder(target: Launchable) {
        val folder: Folder = target as? Folder ?: return
        session.onSheetOpened()
        folderDialog.value = FolderDialogState.Deleting(folder)
    }

    /** Shows the name dialog for [folder]. */
    fun requestRename(folder: Folder) {
        folderDialog.value = FolderDialogState.Renaming(folder)
    }

    /** Closes the Folder dialog without changing the Folder. */
    internal fun dismissFolderDialog() {
        folderDialog.value = FolderDialogState.Closed
    }

    /** Deletes the Folder whose confirmation is showing and everything belonging to it. */
    internal fun confirmDeleteFolder() {
        val dialog: FolderDialogState.Deleting = folderDialog.value as? FolderDialogState.Deleting ?: return
        val folder: Folder = dialog.folder
        folderDialog.value = FolderDialogState.Closed
        session.onOwnerDeleted(ownerKey = folder.key)
        launchLogged(message = "Failed to delete Folder ${folder.key}") {
            folderRepository.delete(folder)
        }
    }

    /** Renames the Folder whose name dialog is showing, resetting its name when [name] is blank. */
    internal fun confirmRename(name: String) {
        val dialog: FolderDialogState.Renaming = folderDialog.value as? FolderDialogState.Renaming ?: return
        val folder: Folder = dialog.folder
        folderDialog.value = FolderDialogState.Closed
        launchLogged(message = "Failed to rename ${folder.key}") {
            preferencesRepository.setItemName(item = folder, name = name)
        }
    }

    /** Shows or hides [folder] in the App List. */
    fun setShowInAppList(folder: Folder, show: Boolean) {
        launchLogged(message = "Failed to change whether ${folder.key} is in the App List") {
            folderRepository.setShowInAppList(folder = folder, show = show)
        }
    }

    /** Opens the Add to Folder sheet for [launchable], which must not be a Folder. */
    fun startAddToFolder(launchable: Launchable) {
        if (launchable is Folder) {
            return
        }
        addToFolderRequest.value = launchable
    }

    internal fun closeAddToFolder() {
        addToFolderRequest.value = null
    }

    /** Adds or removes the Add to Folder sheet's Launchable from [folderKey]. */
    fun toggleMembership(folderKey: String, member: Boolean) {
        val state: AddToFolderState.Open = addToFolderState.value as? AddToFolderState.Open ?: return
        if (member == (folderKey in state.membership)) {
            return
        }
        val launchableKey: String = state.launchable.key
        launchLogged(message = "Failed to change $launchableKey's Folder membership") {
            popupRepository.setItems(
                ownerKey = folderKey,
                add = if (member) listOf(launchableKey) else emptyList(),
                remove = if (member) emptySet() else setOf(launchableKey)
            )
        }
    }

    /** Creates a Folder holding the Add to Folder sheet's Launchable, then reports it for the Snackbar. */
    fun startNewFolder(name: String) {
        val state: AddToFolderState.Open = addToFolderState.value as? AddToFolderState.Open ?: return
        val launchableKey: String = state.launchable.key
        launchLogged(message = "Failed to create a Folder for $launchableKey") {
            val folder: Folder = folderRepository.create(
                name = name,
                initialItems = listOf(launchableKey),
                addToFavorites = true
            )
            folderAdditionRequests.send(folder)
        }
    }

    internal fun onCardClosed() {
        folderDialog.value = FolderDialogState.Closed
        addToFolderRequest.value = null
    }

    internal fun onEditStarted() {
        addToFolderRequest.value = null
    }

    internal fun backBeforeItemMenu(): Boolean {
        if (addToFolderRequest.value == null) {
            return false
        }
        closeAddToFolder()
        return true
    }

    private fun folderMemberships(launchableKey: String?): Flow<FolderMembership> = if (launchableKey == null) {
        flowOf(FolderMembership(launchableKey = null, folderKeys = emptySet()))
    } else {
        folderRepository.folders.flatMapLatest { folders ->
            if (folders.isEmpty()) {
                flowOf(FolderMembership(launchableKey = launchableKey, folderKeys = emptySet()))
            } else {
                combine(folders.map { folder -> folderMembership(folder = folder, launchableKey = launchableKey) }) { memberships ->
                    FolderMembership(
                        launchableKey = launchableKey,
                        folderKeys = memberships
                            .filter { (_, isMember) -> isMember }
                            .mapTo(mutableSetOf()) { (folderKey, _) -> folderKey }
                    )
                }
            }
        }.onStart { emit(FolderMembership(launchableKey = launchableKey, folderKeys = emptySet())) }
    }

    private fun folderMembership(folder: Folder, launchableKey: String): Flow<Pair<String, Boolean>> =
        popupRepository.items(ownerKey = folder.key).map { items ->
            folder.key to items.any { item -> item.launchable.key == launchableKey }
        }
}
