package core.popups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.apps.model.App
import core.data.apps.model.AppProfile
import core.data.launchables.ILaunchableRepository
import core.data.launchables.model.Launchable
import core.data.popups.IPopupRepository
import core.data.popups.PopupItem
import core.data.shortcuts.IShortcutSource
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import core.util.launchLogged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Owns the Edit Pop-up sheet's contents and the item writes it commits. */
class PopupEditorViewModel(
    private val launchableRepository: ILaunchableRepository,
    private val shortcutSource: IShortcutSource,
    private val popupRepository: IPopupRepository,
    private val homeEditState: HomeEditState,
    private val session: IPopupSession
) : ViewModel() {
    private val editRequest: MutableStateFlow<ShortcutTarget?> = MutableStateFlow(null)
    private val editShortcuts: MutableStateFlow<ShortcutsResult?> = MutableStateFlow(null)
    private val editHiddenShortcuts: StateFlow<HiddenShortcutIds> = hiddenShortcutIds(
        scope = viewModelScope,
        targets = editRequest,
        popupRepository = popupRepository
    )
    private val editItems: StateFlow<OwnerItems> = addedItems(
        scope = viewModelScope,
        targets = editRequest,
        popupRepository = popupRepository
    )

    val editState: StateFlow<PopupEditState> = combine(
        editRequest,
        launchableRepository.launchableState,
        editShortcuts,
        editHiddenShortcuts,
        editItems
    ) { request, launchableState, shortcutResult, hidden, items ->
        val owner: Launchable? = request?.ownerKey?.let { ownerKey ->
            launchableState.launchables.findLaunchable(key = ownerKey)
        }
        if (request == null ||
            owner == null ||
            shortcutResult == null ||
            hidden.ownerKey != request.ownerKey ||
            items.ownerKey != request.ownerKey
        ) {
            PopupEditState.Closed
        } else {
            PopupEditState.Editing(
                owner = owner,
                shortcuts = shortcutResult,
                hiddenIds = hidden.ids,
                items = items.items,
                shortcutSourceNames = shortcutSourceNames(items = items.items, launchables = launchableState.launchables)
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = PopupEditState.Closed
    )

    init {
        collectShortcuts(
            scope = viewModelScope,
            targets = editRequest,
            reloads = session.shortcutReloads,
            shortcutSource = shortcutSource,
            into = editShortcuts
        )
        observeTargetLoss(
            scope = viewModelScope,
            targetKeys = editRequest.map { request -> request?.ownerKey },
            launchableRepository = launchableRepository,
            homeEditState = homeEditState,
            isClosedByMode = { mode -> mode != HomeEditMode.None && mode != HomeEditMode.Favorites },
            onTargetLost = ::finishEdit
        )
    }

    /**
     * Opens the editor for an App or Folder. Allowed on Home and during Edit
     * Favorites; ignored in every other Home edit mode.
     */
    fun startEdit(owner: Launchable) {
        val mode: HomeEditMode = homeEditState.mode.value
        if (mode != HomeEditMode.None && mode != HomeEditMode.Favorites) {
            return
        }
        val target: ShortcutTarget = targetFor(owner) ?: return
        session.onSheetOpened()
        if (editRequest.value != target) {
            editShortcuts.value = null
            editRequest.value = target
        }
    }

    fun finishEdit() {
        editRequest.value = null
        session.onEditFinished()
    }

    /** Hides or shows a shortcut in the App currently being edited. */
    fun setShortcutHidden(shortcutId: String, hidden: Boolean) {
        val ownerKey: String = editRequest.value?.ownerKey ?: return
        launchLogged(message = "Failed to change hidden state of shortcut $shortcutId") {
            popupRepository.setShortcutHidden(ownerKey = ownerKey, shortcutId = shortcutId, hidden = hidden)
        }
    }

    /** Removes [item] from the Pop-up currently being edited. */
    fun removeEditedItem(item: Launchable) {
        val ownerKey: String = editRequest.value?.ownerKey ?: return
        removeAddedItem(popupRepository = popupRepository, ownerKey = ownerKey, itemKey = item.key)
    }

    fun setItemOrder(orderedItemKeys: List<String>) {
        val ownerKey: String = editRequest.value?.ownerKey ?: return
        launchLogged(message = "Failed to reorder the items in $ownerKey's Pop-up") {
            popupRepository.setItemOrder(ownerKey = ownerKey, orderedItemKeys = orderedItemKeys)
        }
    }

    internal fun onCardClosed() {
        editRequest.value = null
    }

    /** Closes the editor when it shows [ownerKey], for example because that Folder was deleted. */
    internal fun finishEditing(ownerKey: String) {
        if (editRequest.value?.ownerKey == ownerKey) {
            finishEdit()
        }
    }

    internal fun backEditor(): Boolean {
        if (editRequest.value == null) {
            return false
        }
        finishEdit()
        return true
    }

    private fun shortcutSourceNames(items: List<PopupItem>, launchables: List<Launchable>): Map<String, String> {
        if (items.none { item -> item is PopupItem.Shortcut }) {
            return emptyMap()
        }
        val appNames: Map<AppProfile, String> = launchables
            .filterIsInstance<App>()
            .associate { app -> AppProfile(packageName = app.packageName, profile = app.profile) to app.name }
        return items
            .filterIsInstance<PopupItem.Shortcut>()
            .associate { item ->
                val shortcut: ShortcutLaunchable = item.launchable
                val sourceName: String = appNames[AppProfile(packageName = shortcut.packageName, profile = shortcut.profile)]
                    ?: shortcut.packageName
                shortcut.key to sourceName
            }
    }
}
