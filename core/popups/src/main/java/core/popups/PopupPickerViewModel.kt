package core.popups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.apps.model.App
import core.data.folders.Folder
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
import core.util.launchLogged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private data class ShortcutPickerRequest(
    val request: ShortcutTarget?,
    val isPicking: Boolean,
    val items: OwnerItems,
    val launchableState: LaunchableState
)

/** Owns the item and Shortcut pickers the Edit Pop-up sheet opens. */
class PopupPickerViewModel(
    private val launchableRepository: ILaunchableRepository,
    private val shortcutSource: IShortcutSource,
    private val popupRepository: IPopupRepository,
    private val homeEditState: HomeEditState,
    private val session: IPopupSession
) : ViewModel() {
    private val pickerTarget: MutableStateFlow<ShortcutTarget?> = MutableStateFlow(null)
    private val isPicking: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val shortcutPickerTarget: MutableStateFlow<ShortcutTarget?> = MutableStateFlow(null)
    private val isPickingShortcuts: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val pickerItems: StateFlow<OwnerItems> = addedItems(
        scope = viewModelScope,
        targets = pickerTarget,
        popupRepository = popupRepository
    )
    private val shortcutPickerItems: StateFlow<OwnerItems> = addedItems(
        scope = viewModelScope,
        targets = shortcutPickerTarget,
        popupRepository = popupRepository
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
        shortcutPickerTarget,
        isPickingShortcuts,
        shortcutPickerItems,
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

    fun startPicking(owner: Launchable) {
        val request: ShortcutTarget = targetFor(owner) ?: return
        session.onSheetOpened()
        pickerTarget.value = request
        isPicking.value = true
    }

    /** Opens the item picker straight from an empty Folder's card. */
    fun startFolderPicking() {
        if (homeEditState.mode.value != HomeEditMode.None) {
            return
        }
        val ownerKey: String = session.openOwnerKey() ?: return
        val owner: Launchable = launchableRepository.launchableState.value.launchables
            .findLaunchable(key = ownerKey)
            ?: return
        if (owner !is Folder) {
            return
        }
        pickerTarget.value = targetFor(owner) ?: return
        isPicking.value = true
    }

    fun confirmPicking(selectedInOrder: List<String>) {
        val request: ShortcutTarget = pickerTarget.value ?: return
        val picking: PopupPickerState.Picking = pickerState.value as? PopupPickerState.Picking ?: return
        isPicking.value = false
        pickerTarget.value = null
        applySelection(
            ownerKey = request.ownerKey,
            existingKeys = picking.selectedKeys,
            selectedInOrder = selectedInOrder,
            message = "Failed to change the items in ${request.ownerKey}'s Pop-up"
        )
    }

    fun cancelPicking() {
        isPicking.value = false
        pickerTarget.value = null
    }

    fun startPickingShortcuts(owner: Launchable) {
        val request: ShortcutTarget = targetFor(owner) ?: return
        session.onSheetOpened()
        shortcutPickerTarget.value = request
        isPickingShortcuts.value = true
    }

    fun confirmShortcuts(selectedInOrder: List<String>) {
        val request: ShortcutTarget = shortcutPickerTarget.value ?: return
        val picking: ShortcutPickerState.Picking = shortcutPickerState.value as? ShortcutPickerState.Picking ?: return
        isPickingShortcuts.value = false
        applySelection(
            ownerKey = request.ownerKey,
            existingKeys = picking.selectedKeys,
            selectedInOrder = selectedInOrder,
            message = "Failed to change the shortcuts in ${request.ownerKey}'s Pop-up"
        )
    }

    fun cancelShortcutPicking() {
        isPickingShortcuts.value = false
    }

    internal fun onCardOpened() {
        clearPickers()
    }

    internal fun onCardClosed() {
        clearPickers()
    }

    internal fun onEditStarted() {
        clearPickers()
    }

    internal fun onEditFinished() {
        clearPickers()
    }

    internal fun backBeforeItemMenu(): Boolean {
        if (isPickingShortcuts.value) {
            cancelShortcutPicking()
            return true
        }
        if (isPicking.value) {
            cancelPicking()
            return true
        }
        return false
    }

    private fun clearPickers() {
        pickerTarget.value = null
        isPicking.value = false
        shortcutPickerTarget.value = null
        isPickingShortcuts.value = false
    }

    private fun applySelection(ownerKey: String, existingKeys: Set<String>, selectedInOrder: List<String>, message: String) {
        val additions: List<String> = selectedInOrder.filterNot { key -> key in existingKeys }
        val removals: Set<String> = existingKeys - selectedInOrder.toSet()
        launchLogged(message = message) {
            popupRepository.setItems(ownerKey = ownerKey, add = additions, remove = removals)
        }
    }

    private fun pickerGroups(ownerKey: String, groups: List<LaunchableGroup.Listed>): List<LaunchableGroup.Listed> =
        groups.mapNotNull { group ->
            val candidates: List<Launchable> =
                group.items.filterNot { item -> item.key == ownerKey || item is ShortcutLaunchable || item is Folder }
            if (candidates.isEmpty()) {
                null
            } else {
                group.withItems(items = candidates)
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
}
