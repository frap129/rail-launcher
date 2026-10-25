package core.popups

import android.os.UserHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.apps.model.App
import core.data.launchables.ILaunchableRepository
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.popups.IPopupRepository
import core.data.shortcuts.IShortcutSource
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

private val EMPTY_SHORTCUTS: ShortcutsResult = ShortcutsResult.Available(shortcuts = emptyList())

private data class ShortcutTarget(val ownerKey: String, val packageName: String, val profile: UserHandle)

private data class OpenRequest(val target: ShortcutTarget, val anchor: PopupAnchor)

private data class HiddenShortcutIds(val ownerKey: String?, val ids: Set<String>)

private data class OwnerItems(val ownerKey: String?, val items: List<Launchable>)

private data class MenuTarget(val ownerKey: String, val itemKey: String?)

/** Owns the one open Pop-up, its menu, editor, item picker and their live content. */
class PopupViewModel(
    private val launchableRepository: ILaunchableRepository,
    private val shortcutSource: IShortcutSource,
    private val popupRepository: IPopupRepository,
    private val homeEditState: HomeEditState
) : ViewModel() {
    private val openRequest: MutableStateFlow<OpenRequest?> = MutableStateFlow(null)
    private val editRequest: MutableStateFlow<ShortcutTarget?> = MutableStateFlow(null)
    private val menuTarget: MutableStateFlow<MenuTarget?> = MutableStateFlow(null)
    private val isPicking: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val cardShortcuts: MutableStateFlow<ShortcutsResult?> = MutableStateFlow(EMPTY_SHORTCUTS)
    private val editShortcuts: MutableStateFlow<ShortcutsResult?> = MutableStateFlow(null)
    private val reloads: MutableStateFlow<Int> = MutableStateFlow(0)

    private val cardHiddenShortcuts: StateFlow<HiddenShortcutIds> = hiddenShortcutIds(openRequest.map { request -> request?.target })

    private val editHiddenShortcuts: StateFlow<HiddenShortcutIds> = hiddenShortcutIds(editRequest)

    private val cardItems: StateFlow<OwnerItems> = addedItems(openRequest.map { request -> request?.target })

    private val editItems: StateFlow<OwnerItems> = addedItems(editRequest)

    val popupState: StateFlow<PopupUiState> = combine(
        openRequest,
        launchableRepository.launchableState,
        cardShortcuts,
        cardHiddenShortcuts,
        cardItems
    ) { request, launchableState, shortcutResult, hidden, items ->
        val owner: Launchable? = request?.target?.ownerKey?.let { ownerKey ->
            launchableState.launchables.firstOrNull { launchable -> launchable.key == ownerKey }
        }
        if (request == null || owner == null) {
            PopupUiState.Closed
        } else {
            val hiddenIds: Set<String> = if (hidden.ownerKey == request.target.ownerKey) hidden.ids else emptySet()
            val items: List<Launchable> = if (items.ownerKey == request.target.ownerKey) items.items else emptyList()
            PopupUiState.Open(
                owner = owner,
                anchor = request.anchor,
                content = PopupContentResolver.resolve(
                    owner = owner,
                    shortcuts = shortcutResult ?: EMPTY_SHORTCUTS,
                    hiddenShortcutIds = hiddenIds,
                    items = items
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = PopupUiState.Closed
    )

    val menuState: StateFlow<PopupMenuState> = combine(popupState, menuTarget) { cardState, target ->
        val open: PopupUiState.Open? = cardState as? PopupUiState.Open
        val resolvedTarget: Launchable? = when {
            open == null || target == null -> null
            target.itemKey == null -> open.owner
            else -> open.content.items.firstOrNull { item -> item.launchable.key == target.itemKey }?.launchable
        }
        if (resolvedTarget == null) {
            PopupMenuState.Closed
        } else {
            PopupMenuState.Open(
                target = resolvedTarget,
                items = menuItems(target = resolvedTarget, isItem = target?.itemKey != null)
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = PopupMenuState.Closed
    )

    val editState: StateFlow<PopupEditState> = combine(
        editRequest,
        launchableRepository.launchableState,
        editShortcuts,
        editHiddenShortcuts,
        editItems
    ) { request, launchableState, shortcutResult, hidden, items ->
        val owner: Launchable? = request?.ownerKey?.let { ownerKey ->
            launchableState.launchables.firstOrNull { launchable -> launchable.key == ownerKey }
        }
        if (request == null ||
            owner == null ||
            shortcutResult == null ||
            hidden.ownerKey != request.ownerKey ||
            items.ownerKey != request.ownerKey
        ) {
            PopupEditState.Closed
        } else {
            PopupEditState.Editing(owner = owner, shortcuts = shortcutResult, hiddenIds = hidden.ids, items = items.items)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = PopupEditState.Closed
    )

    val pickerState: StateFlow<PopupPickerState> = combine(
        editRequest,
        isPicking,
        launchableRepository.launchableState,
        editItems
    ) { request, picking, launchableState, items ->
        if (request == null || !picking || !launchableState.isLoaded || items.ownerKey != request.ownerKey) {
            PopupPickerState.Closed
        } else {
            PopupPickerState.Picking(
                groups = pickerGroups(ownerKey = request.ownerKey, groups = launchableState.listedGroups),
                selectedKeys = items.items.mapTo(mutableSetOf()) { item -> item.key }
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = PopupPickerState.Closed
    )

    init {
        collectShortcuts(targets = openRequest.map { request -> request?.target }, into = cardShortcuts)
        collectShortcuts(targets = editRequest, into = editShortcuts)
        observeTargetLoss(
            targets = openRequest.map { request -> request?.target },
            isClosedByMode = { mode -> mode != HomeEditMode.None },
            onTargetLost = ::close
        )
        observeTargetLoss(
            targets = editRequest,
            isClosedByMode = { mode -> mode != HomeEditMode.None && mode != HomeEditMode.Favorites },
            onTargetLost = ::finishEdit
        )
    }

    fun open(owner: Launchable, anchor: PopupAnchor) {
        val app: App = owner as? App ?: return
        if (homeEditState.mode.value != HomeEditMode.None) {
            return
        }
        menuTarget.value = null
        isPicking.value = false
        cardShortcuts.value = EMPTY_SHORTCUTS
        openRequest.value = OpenRequest(
            target = ShortcutTarget(ownerKey = app.key, packageName = app.packageName, profile = app.profile),
            anchor = anchor
        )
    }

    fun close() {
        menuTarget.value = null
        isPicking.value = false
        editRequest.value = null
        openRequest.value = null
    }

    /** Moves an open Pop-up to its row's latest position after a relayout. */
    fun onAnchorChanged(ownerKey: String, anchor: PopupAnchor) {
        openRequest.update { request ->
            if (request?.target?.ownerKey == ownerKey) request.copy(anchor = anchor) else request
        }
    }

    fun openMenu() {
        val open: PopupUiState.Open = popupState.value as? PopupUiState.Open ?: return
        menuTarget.value = MenuTarget(ownerKey = open.owner.key, itemKey = null)
    }

    fun closeMenu() {
        menuTarget.value = null
    }

    /**
     * Opens the Edit Pop-up sheet for an App. Allowed on Home and during Edit
     * Favorites; ignored in every other Home edit mode.
     */
    fun startEdit(owner: Launchable) {
        val app: App = owner as? App ?: return
        val mode: HomeEditMode = homeEditState.mode.value
        if (mode != HomeEditMode.None && mode != HomeEditMode.Favorites) {
            return
        }
        menuTarget.value = null
        isPicking.value = false
        val target: ShortcutTarget = ShortcutTarget(ownerKey = app.key, packageName = app.packageName, profile = app.profile)
        if (editRequest.value != target) {
            editShortcuts.value = null
            editRequest.value = target
        }
    }

    fun finishEdit() {
        editRequest.value = null
        isPicking.value = false
    }

    /** Hides or shows a shortcut in the App currently being edited. */
    fun setShortcutHidden(shortcutId: String, hidden: Boolean) {
        val ownerKey: String = editRequest.value?.ownerKey ?: return
        updatePopup(message = "Failed to change hidden state of shortcut $shortcutId") {
            popupRepository.setShortcutHidden(ownerKey = ownerKey, shortcutId = shortcutId, hidden = hidden)
        }
    }

    fun openItemMenu(item: Launchable) {
        val open: PopupUiState.Open = popupState.value as? PopupUiState.Open ?: return
        if (open.content.items.none { popupItem -> popupItem.launchable.key == item.key }) {
            return
        }
        menuTarget.value = MenuTarget(ownerKey = open.owner.key, itemKey = item.key)
    }

    fun removeItem(item: Launchable) {
        val ownerKey: String = openRequest.value?.target?.ownerKey ?: editRequest.value?.ownerKey ?: return
        menuTarget.value = null
        updatePopup(message = "Failed to remove ${item.key} from its Pop-up") {
            popupRepository.removeItem(ownerKey = ownerKey, itemKey = item.key)
        }
    }

    fun startPicking() {
        if (editRequest.value == null) {
            return
        }
        menuTarget.value = null
        isPicking.value = true
    }

    fun confirmPicking(selectedInOrder: List<String>) {
        val request: ShortcutTarget = editRequest.value ?: return
        val picking: PopupPickerState.Picking = pickerState.value as? PopupPickerState.Picking ?: return
        val existingKeys: Set<String> = picking.selectedKeys
        val additions: List<String> = selectedInOrder.filterNot { key -> key in existingKeys }
        val removals: Set<String> = existingKeys - selectedInOrder.toSet()
        isPicking.value = false
        updatePopup(message = "Failed to change the items in ${request.ownerKey}'s Pop-up") {
            popupRepository.setItems(ownerKey = request.ownerKey, add = additions, remove = removals)
        }
    }

    fun cancelPicking() {
        isPicking.value = false
    }

    fun moveItem(from: Int, to: Int) {
        val ownerKey: String = editRequest.value?.ownerKey ?: return
        if (from == to) {
            return
        }
        updatePopup(message = "Failed to move an item in $ownerKey's Pop-up") {
            popupRepository.moveItem(ownerKey = ownerKey, from = from, to = to)
        }
    }

    fun onBack(): Boolean {
        if (isPicking.value) {
            cancelPicking()
            return true
        }
        if (menuTarget.value?.itemKey != null) {
            closeMenu()
            return true
        }
        if (editRequest.value != null) {
            finishEdit()
            return true
        }
        if (menuTarget.value != null) {
            closeMenu()
            return true
        }
        if (openRequest.value == null) {
            return false
        }
        close()
        return true
    }

    fun onHomeIntent() {
        close()
    }

    fun onLaunched() {
        close()
    }

    /** Queries the owner's shortcuts again, for example after a work profile is resumed. */
    fun reloadShortcuts() {
        reloads.update { reload -> reload + 1 }
    }

    private fun updatePopup(message: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Timber.e(exception, message)
            }
        }
    }

    private fun collectShortcuts(targets: Flow<ShortcutTarget?>, into: MutableStateFlow<ShortcutsResult?>) {
        viewModelScope.launch {
            combine(targets, reloads) { target, _ -> target }
                .flatMapLatest { target ->
                    if (target == null) {
                        flowOf(EMPTY_SHORTCUTS)
                    } else {
                        shortcutsFor(target)
                    }
                }
                .collect { result -> into.value = result }
        }
    }

    private fun shortcutsFor(target: ShortcutTarget): Flow<ShortcutsResult> =
        shortcutSource.shortcuts(packageName = target.packageName, profile = target.profile)
            .catch { exception ->
                if (exception is CancellationException) {
                    throw exception
                }
                Timber.e(exception, "Popup shortcut collection failed")
                emit(EMPTY_SHORTCUTS)
            }

    private fun hiddenShortcutIds(targets: Flow<ShortcutTarget?>): StateFlow<HiddenShortcutIds> = targets
        .map { target -> target?.ownerKey }
        .distinctUntilChanged()
        .flatMapLatest { ownerKey ->
            hiddenShortcuts(ownerKey).map { ids -> HiddenShortcutIds(ownerKey = ownerKey, ids = ids) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
            initialValue = HiddenShortcutIds(ownerKey = null, ids = emptySet())
        )

    private fun addedItems(targets: Flow<ShortcutTarget?>): StateFlow<OwnerItems> = targets
        .map { target -> target?.ownerKey }
        .distinctUntilChanged()
        .flatMapLatest { ownerKey ->
            if (ownerKey == null) {
                flowOf(OwnerItems(ownerKey = null, items = emptyList()))
            } else {
                popupRepository.items(ownerKey)
                    .map { items -> OwnerItems(ownerKey = ownerKey, items = items) }
                    .onStart { emit(OwnerItems(ownerKey = ownerKey, items = emptyList())) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
            initialValue = OwnerItems(ownerKey = null, items = emptyList())
        )

    private fun observeTargetLoss(targets: Flow<ShortcutTarget?>, isClosedByMode: (HomeEditMode) -> Boolean, onTargetLost: () -> Unit) {
        viewModelScope.launch {
            combine(
                targets,
                launchableRepository.launchableState,
                homeEditState.mode
            ) { target, launchableState, mode -> Triple(target, launchableState, mode) }
                .collect { (target, launchableState, mode) ->
                    if (target == null) {
                        return@collect
                    }
                    val isOwnerMissing: Boolean =
                        launchableState.isLoaded &&
                            launchableState.launchables.none { launchable -> launchable.key == target.ownerKey }
                    if (isOwnerMissing || isClosedByMode(mode)) {
                        onTargetLost()
                    }
                }
        }
    }

    private fun hiddenShortcuts(ownerKey: String?): Flow<Set<String>> =
        if (ownerKey == null) flowOf(emptySet()) else popupRepository.hiddenShortcuts(ownerKey)

    private fun pickerGroups(ownerKey: String, groups: List<LaunchableGroup.Listed>): List<LaunchableGroup.Listed> =
        groups.mapNotNull { group ->
            val candidates: List<Launchable> =
                group.items.filterNot { item -> item.key == ownerKey || item is ShortcutLaunchable }
            if (candidates.isEmpty()) {
                null
            } else {
                when (group) {
                    is LaunchableGroup.InitialGroup -> group.copy(items = candidates)
                    is LaunchableGroup.Builtin -> group.copy(items = candidates)
                }
            }
        }

    private fun menuItems(target: Launchable, isItem: Boolean): List<PopupMenuItem> = buildList {
        if (isItem) {
            add(PopupMenuItem.RemoveFromPopup)
        }
        if (!isItem && target is App) {
            add(PopupMenuItem.EditPopup)
        }
        if (target is App) {
            add(PopupMenuItem.AppInfo)
        }
    }
}
