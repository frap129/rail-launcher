package core.popups

import android.os.UserHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.apps.model.App
import core.data.apps.model.AppProfile
import core.data.folders.Folder
import core.data.folders.IFolderRepository
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.popups.IPopupRepository
import core.data.popups.PopupItem
import core.data.shortcuts.AppShortcuts
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

private data class ShortcutTarget(val ownerKey: String, val packageName: String?, val profile: UserHandle?)

private data class OpenRequest(val target: ShortcutTarget, val anchor: PopupAnchor)

private data class HiddenShortcutIds(val ownerKey: String?, val ids: Set<String>)

private data class OwnerItems(val ownerKey: String?, val items: List<PopupItem>)

private data class MenuTarget(val ownerKey: String, val itemKey: String?)

private data class ShortcutPickerRequest(
    val request: ShortcutTarget?,
    val isPicking: Boolean,
    val items: OwnerItems,
    val launchableState: LaunchableState
)

/** Owns the one open Pop-up, its menu, editor, item picker and their live content. */
class PopupViewModel(
    private val launchableRepository: ILaunchableRepository,
    private val shortcutSource: IShortcutSource,
    private val popupRepository: IPopupRepository,
    private val folderRepository: IFolderRepository,
    private val homeEditState: HomeEditState
) : ViewModel() {
    private val openRequest: MutableStateFlow<OpenRequest?> = MutableStateFlow(null)
    private val editRequest: MutableStateFlow<ShortcutTarget?> = MutableStateFlow(null)
    private val pickerTarget: MutableStateFlow<ShortcutTarget?> = MutableStateFlow(null)
    private val menuTarget: MutableStateFlow<MenuTarget?> = MutableStateFlow(null)
    private val pendingFolderDelete: MutableStateFlow<Folder?> = MutableStateFlow(null)
    private val isPicking: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val isPickingShortcuts: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val cardShortcuts: MutableStateFlow<ShortcutsResult?> = MutableStateFlow(EMPTY_SHORTCUTS)
    private val editShortcuts: MutableStateFlow<ShortcutsResult?> = MutableStateFlow(null)
    private val reloads: MutableStateFlow<Int> = MutableStateFlow(0)

    private val cardHiddenShortcuts: StateFlow<HiddenShortcutIds> = hiddenShortcutIds(openRequest.map { request -> request?.target })

    private val editHiddenShortcuts: StateFlow<HiddenShortcutIds> = hiddenShortcutIds(editRequest)

    private val cardItems: StateFlow<OwnerItems> = addedItems(openRequest.map { request -> request?.target })

    private val editItems: StateFlow<OwnerItems> = addedItems(editRequest)

    private val pickerItems: StateFlow<OwnerItems> = addedItems(pickerTarget)

    /** The Folder awaiting delete confirmation, if any. */
    val folderDeleteState: StateFlow<Folder?> = pendingFolderDelete

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
            val popupItems: List<PopupItem> = if (items.ownerKey == request.target.ownerKey) items.items else emptyList()
            PopupUiState.Open(
                owner = owner,
                anchor = request.anchor,
                content = PopupContentResolver.resolve(
                    owner = owner,
                    shortcuts = shortcutResult ?: EMPTY_SHORTCUTS,
                    hiddenShortcutIds = hiddenIds,
                    items = popupItems
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

    val pickerState: StateFlow<PopupPickerState> = combine(
        pickerTarget,
        isPicking,
        launchableRepository.launchableState,
        pickerItems
    ) { request, picking, launchableState, items ->
        if (request == null || !picking || !launchableState.isLoaded || items.ownerKey != request.ownerKey) {
            PopupPickerState.Closed
        } else {
            PopupPickerState.Picking(
                groups = pickerGroups(ownerKey = request.ownerKey, groups = launchableState.listedGroups),
                selectedKeys = items.items.mapTo(mutableSetOf()) { item -> item.launchable.key }
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = PopupPickerState.Closed
    )

    val shortcutPickerState: StateFlow<ShortcutPickerState> = combine(
        editRequest,
        isPickingShortcuts,
        editItems,
        launchableRepository.launchableState
    ) { request, pickingShortcuts, items, launchableState ->
        ShortcutPickerRequest(
            request = request,
            isPicking = pickingShortcuts,
            items = items,
            launchableState = launchableState
        )
    }
        .flatMapLatest { pickerRequest ->
            val request: ShortcutTarget? = pickerRequest.request
            if (request == null ||
                !pickerRequest.isPicking ||
                !pickerRequest.launchableState.isLoaded ||
                pickerRequest.items.ownerKey != request.ownerKey
            ) {
                flowOf(ShortcutPickerState.Closed)
            } else {
                shortcutSource.appsWithShortcuts().map { apps ->
                    ShortcutPickerState.Picking(
                        groups = shortcutPickerGroups(groups = pickerRequest.launchableState.listedGroups, apps = apps, owner = request),
                        selectedKeys = pickerRequest.items.items
                            .filterIsInstance<PopupItem.Shortcut>()
                            .mapTo(mutableSetOf()) { item -> item.launchable.key },
                        shortcutsUnavailable = apps.isNotEmpty() &&
                            apps.all { appShortcuts ->
                                appShortcuts.result is ShortcutsResult.Unavailable
                            }
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
            initialValue = ShortcutPickerState.Closed
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
        if (homeEditState.mode.value != HomeEditMode.None) {
            return
        }
        val target: ShortcutTarget = targetFor(owner) ?: return
        menuTarget.value = null
        pickerTarget.value = null
        isPicking.value = false
        isPickingShortcuts.value = false
        cardShortcuts.value = EMPTY_SHORTCUTS
        openRequest.value = OpenRequest(target = target, anchor = anchor)
    }

    fun close() {
        menuTarget.value = null
        pendingFolderDelete.value = null
        pickerTarget.value = null
        isPicking.value = false
        isPickingShortcuts.value = false
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
     * Opens the editor for an App or Folder. Allowed on Home and during Edit
     * Favorites; ignored in every other Home edit mode.
     */
    fun startEdit(owner: Launchable) {
        val mode: HomeEditMode = homeEditState.mode.value
        if (mode != HomeEditMode.None && mode != HomeEditMode.Favorites) {
            return
        }
        val target: ShortcutTarget = targetFor(owner) ?: return
        menuTarget.value = null
        pickerTarget.value = null
        isPicking.value = false
        isPickingShortcuts.value = false
        if (editRequest.value != target) {
            editShortcuts.value = null
            editRequest.value = target
        }
    }

    fun finishEdit() {
        editRequest.value = null
        pickerTarget.value = null
        isPicking.value = false
        isPickingShortcuts.value = false
    }

    /** Hides or shows a shortcut in the App currently being edited. */
    fun setShortcutHidden(shortcutId: String, hidden: Boolean) {
        val ownerKey: String = editRequest.value?.ownerKey ?: return
        updatePopup(message = "Failed to change hidden state of shortcut $shortcutId") {
            popupRepository.setShortcutHidden(ownerKey = ownerKey, shortcutId = shortcutId, hidden = hidden)
        }
    }

    fun openItemMenu(item: PopupItem) {
        val open: PopupUiState.Open = popupState.value as? PopupUiState.Open ?: return
        if (open.content.items.none { popupItem -> popupItem.launchable.key == item.launchable.key }) {
            return
        }
        menuTarget.value = MenuTarget(ownerKey = open.owner.key, itemKey = item.launchable.key)
    }

    fun removeItem(item: Launchable) {
        val ownerKey: String = openRequest.value?.target?.ownerKey ?: editRequest.value?.ownerKey ?: return
        menuTarget.value = null
        updatePopup(message = "Failed to remove ${item.key} from its Pop-up") {
            popupRepository.removeItem(ownerKey = ownerKey, itemKey = item.key)
        }
    }

    /** Shows the delete confirmation for the Folder whose menu is open. */
    fun requestDeleteFolder() {
        val folder: Folder = (menuState.value as? PopupMenuState.Open)?.target as? Folder ?: return
        requestDeleteFolder(folder)
    }

    /** Shows the delete confirmation for [folder]. */
    fun requestDeleteFolder(folder: Folder) {
        menuTarget.value = null
        pendingFolderDelete.value = folder
    }

    fun cancelDeleteFolder() {
        pendingFolderDelete.value = null
    }

    /** Deletes the confirmed Folder and everything belonging to it. */
    fun confirmDeleteFolder() {
        val folder: Folder = pendingFolderDelete.value ?: return
        pendingFolderDelete.value = null
        menuTarget.value = null
        if (openRequest.value?.target?.ownerKey == folder.key) {
            close()
        }
        if (editRequest.value?.ownerKey == folder.key) {
            finishEdit()
        }
        updatePopup(message = "Failed to delete Folder ${folder.key}") {
            folderRepository.delete(folder)
        }
    }

    fun startPicking() {
        val request: ShortcutTarget = editRequest.value ?: return
        menuTarget.value = null
        pickerTarget.value = request
        isPicking.value = true
    }

    /** Opens the item picker straight from an empty Folder's card. */
    fun startFolderPicking() {
        if (homeEditState.mode.value != HomeEditMode.None) {
            return
        }
        val request: ShortcutTarget = openRequest.value?.target ?: return
        val owner: Launchable = launchableRepository.launchableState.value.launchables
            .firstOrNull { launchable -> launchable.key == request.ownerKey }
            ?: return
        if (owner !is Folder) {
            return
        }
        pickerTarget.value = request
        isPicking.value = true
    }

    fun confirmPicking(selectedInOrder: List<String>) {
        val request: ShortcutTarget = pickerTarget.value ?: return
        val picking: PopupPickerState.Picking = pickerState.value as? PopupPickerState.Picking ?: return
        val existingKeys: Set<String> = picking.selectedKeys
        val additions: List<String> = selectedInOrder.filterNot { key -> key in existingKeys }
        val removals: Set<String> = existingKeys - selectedInOrder.toSet()
        isPicking.value = false
        pickerTarget.value = null
        updatePopup(message = "Failed to change the items in ${request.ownerKey}'s Pop-up") {
            popupRepository.setItems(ownerKey = request.ownerKey, add = additions, remove = removals)
        }
    }

    fun cancelPicking() {
        isPicking.value = false
        pickerTarget.value = null
    }

    fun startPickingShortcuts() {
        if (editRequest.value == null) {
            return
        }
        menuTarget.value = null
        isPickingShortcuts.value = true
    }

    fun confirmShortcuts(selectedInOrder: List<String>) {
        val request: ShortcutTarget = editRequest.value ?: return
        val picking: ShortcutPickerState.Picking = shortcutPickerState.value as? ShortcutPickerState.Picking ?: return
        val existingKeys: Set<String> = picking.selectedKeys
        val additions: List<String> = selectedInOrder.filterNot { key -> key in existingKeys }
        val removals: Set<String> = existingKeys - selectedInOrder.toSet()
        isPickingShortcuts.value = false
        updatePopup(message = "Failed to change the shortcuts in ${request.ownerKey}'s Pop-up") {
            popupRepository.setItems(ownerKey = request.ownerKey, add = additions, remove = removals)
        }
    }

    fun cancelShortcutPicking() {
        isPickingShortcuts.value = false
    }

    fun setItemOrder(orderedItemKeys: List<String>) {
        val ownerKey: String = editRequest.value?.ownerKey ?: return
        updatePopup(message = "Failed to reorder the items in $ownerKey's Pop-up") {
            popupRepository.setItemOrder(ownerKey = ownerKey, orderedItemKeys = orderedItemKeys)
        }
    }

    fun onBack(): Boolean {
        if (isPickingShortcuts.value) {
            cancelShortcutPicking()
            return true
        }
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

    private fun shortcutsFor(target: ShortcutTarget): Flow<ShortcutsResult> {
        val packageName: String = target.packageName ?: return flowOf(EMPTY_SHORTCUTS)
        val profile: UserHandle = target.profile ?: return flowOf(EMPTY_SHORTCUTS)
        return shortcutSource.shortcuts(packageName = packageName, profile = profile)
            .catch { exception ->
                if (exception is CancellationException) {
                    throw exception
                }
                Timber.e(exception, "Popup shortcut collection failed")
                emit(EMPTY_SHORTCUTS)
            }
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
                group.items.filterNot { item -> item.key == ownerKey || item is ShortcutLaunchable || item is Folder }
            if (candidates.isEmpty()) {
                null
            } else {
                when (group) {
                    is LaunchableGroup.InitialGroup -> group.copy(items = candidates)
                    is LaunchableGroup.Builtin -> group.copy(items = candidates)
                }
            }
        }

    private fun shortcutPickerGroups(
        groups: List<LaunchableGroup.Listed>,
        apps: List<AppShortcuts>,
        owner: ShortcutTarget
    ): List<ShortcutPickerGroup> {
        val appsByKey: Map<String, AppShortcuts> = apps.associateBy { appShortcuts -> appShortcuts.app.key }
        return groups.mapNotNull { group ->
            val candidates: List<AppShortcuts> = group.items
                .filterIsInstance<App>()
                .filterNot { app -> app.key == owner.ownerKey }
                .mapNotNull { app -> appsByKey[app.key] }
                .filter { appShortcuts ->
                    val result: ShortcutsResult = appShortcuts.result
                    result is ShortcutsResult.ProfilePaused ||
                        (result is ShortcutsResult.Available && result.shortcuts.isNotEmpty())
                }
            if (candidates.isEmpty()) {
                null
            } else {
                ShortcutPickerGroup(label = group.label, apps = candidates)
            }
        }
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

    private fun menuItems(target: Launchable, isItem: Boolean): List<PopupMenuItem> = buildList {
        if (isItem) {
            add(PopupMenuItem.RemoveFromPopup)
        }
        if (!isItem) {
            when (target) {
                is App -> add(PopupMenuItem.EditPopup)

                is Folder -> {
                    add(PopupMenuItem.EditFolder)
                    add(PopupMenuItem.DeleteFolder)
                }

                else -> Unit
            }
        }
        if (target is App || target is ShortcutLaunchable) {
            add(PopupMenuItem.AppInfo)
        }
    }

    private fun targetFor(owner: Launchable): ShortcutTarget? = when (owner) {
        is App -> ShortcutTarget(ownerKey = owner.key, packageName = owner.packageName, profile = owner.profile)
        is Folder -> ShortcutTarget(ownerKey = owner.key, packageName = null, profile = null)
        else -> null
    }
}
