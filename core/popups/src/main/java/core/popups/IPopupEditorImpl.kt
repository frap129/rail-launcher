package core.popups

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Owns the Edit Pop-up sheet's contents and the item writes it commits. */
class IPopupEditorImpl(
    parentScope: CoroutineScope,
    private val launchableRepository: ILaunchableRepository,
    private val shortcutSource: IShortcutSource,
    private val popupRepository: IPopupRepository,
    private val homeEditState: HomeEditState,
    private val session: IPopupSession
) : IPopupEditor {
    private val collaboratorScope: PopupCollaboratorScope = PopupCollaboratorScope(parentScope = parentScope)
    private val scope: CoroutineScope = collaboratorScope.scope

    private val editRequest: MutableStateFlow<ShortcutTarget?> = MutableStateFlow(null)
    private val editShortcuts: MutableStateFlow<ShortcutsResult?> = MutableStateFlow(null)
    private val editHiddenShortcuts: StateFlow<HiddenShortcutIds> = hiddenShortcutIds(
        scope = scope,
        targets = editRequest,
        popupRepository = popupRepository
    )
    private val editItems: StateFlow<OwnerItems> = addedItems(
        scope = scope,
        targets = editRequest,
        popupRepository = popupRepository
    )

    override val editState: StateFlow<PopupEditState> = combine(
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
        scope = scope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = PopupEditState.Closed
    )

    init {
        collectShortcuts(
            scope = scope,
            targets = editRequest,
            reloads = session.shortcutReloads,
            shortcutSource = shortcutSource,
            into = editShortcuts
        )
        observeTargetLoss(
            scope = scope,
            targetKeys = editRequest.map { request -> request?.ownerKey },
            launchableRepository = launchableRepository,
            homeEditState = homeEditState,
            isClosedByMode = { mode -> mode != HomeEditMode.None && mode != HomeEditMode.Favorites },
            onTargetLost = ::finishEdit
        )
    }

    override fun startEdit(owner: Launchable) {
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

    override fun finishEdit() {
        editRequest.value = null
        session.onEditFinished()
    }

    override fun setShortcutHidden(shortcutId: String, hidden: Boolean) {
        val ownerKey: String = editRequest.value?.ownerKey ?: return
        scope.launchLogged(message = "Failed to change hidden state of shortcut $shortcutId") {
            popupRepository.setShortcutHidden(ownerKey = ownerKey, shortcutId = shortcutId, hidden = hidden)
        }
    }

    override fun removeEditedItem(item: Launchable) {
        val ownerKey: String = editRequest.value?.ownerKey ?: return
        scope.removeAddedItem(popupRepository = popupRepository, ownerKey = ownerKey, itemKey = item.key)
    }

    override fun setItemOrder(orderedItemKeys: List<String>) {
        val ownerKey: String = editRequest.value?.ownerKey ?: return
        scope.launchLogged(message = "Failed to reorder the items in $ownerKey's Pop-up") {
            popupRepository.setItemOrder(ownerKey = ownerKey, orderedItemKeys = orderedItemKeys)
        }
    }

    override fun onCardClosed() {
        editRequest.value = null
    }

    override fun finishEditing(ownerKey: String) {
        if (editRequest.value?.ownerKey == ownerKey) {
            finishEdit()
        }
    }

    override fun backEditor(): Boolean {
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

    override fun release() {
        collaboratorScope.release()
    }
}
