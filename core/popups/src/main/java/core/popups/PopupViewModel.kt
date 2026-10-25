package core.popups

import android.os.UserHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.apps.model.App
import core.data.launchables.ILaunchableRepository
import core.data.launchables.model.Launchable
import core.data.popups.IPopupRepository
import core.data.shortcuts.IShortcutSource
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

private val EMPTY_SHORTCUTS: ShortcutsResult = ShortcutsResult.Available(shortcuts = emptyList())

private data class ShortcutTarget(val ownerKey: String, val packageName: String, val profile: UserHandle)

private data class OpenRequest(val target: ShortcutTarget, val anchor: PopupAnchor)

private data class HiddenShortcutIds(val ownerKey: String?, val ids: Set<String>)

/** Owns the one open Pop-up, its menu, its editor and their live shortcut content. */
class PopupViewModel(
    private val launchableRepository: ILaunchableRepository,
    private val shortcutSource: IShortcutSource,
    private val popupRepository: IPopupRepository,
    private val homeEditState: HomeEditState
) : ViewModel() {
    private val openRequest: MutableStateFlow<OpenRequest?> = MutableStateFlow(null)
    private val editRequest: MutableStateFlow<ShortcutTarget?> = MutableStateFlow(null)
    private val isMenuOpen: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val cardShortcuts: MutableStateFlow<ShortcutsResult?> = MutableStateFlow(EMPTY_SHORTCUTS)
    private val editShortcuts: MutableStateFlow<ShortcutsResult?> = MutableStateFlow(null)
    private val reloads: MutableStateFlow<Int> = MutableStateFlow(0)

    private val cardHiddenShortcuts: StateFlow<HiddenShortcutIds> = hiddenShortcutIds(openRequest.map { request -> request?.target })

    private val editHiddenShortcuts: StateFlow<HiddenShortcutIds> = hiddenShortcutIds(editRequest)

    val popupState: StateFlow<PopupUiState> = combine(
        openRequest,
        launchableRepository.launchableState,
        cardShortcuts,
        cardHiddenShortcuts
    ) { request, launchableState, shortcutResult, hidden ->
        val owner: Launchable? = request?.target?.ownerKey?.let { ownerKey ->
            launchableState.launchables.firstOrNull { launchable -> launchable.key == ownerKey }
        }
        if (request == null || owner == null) {
            PopupUiState.Closed
        } else {
            val hiddenIds: Set<String> = if (hidden.ownerKey == request.target.ownerKey) hidden.ids else emptySet()
            PopupUiState.Open(
                owner = owner,
                anchor = request.anchor,
                content = PopupContentResolver.resolve(
                    owner = owner,
                    shortcuts = shortcutResult ?: EMPTY_SHORTCUTS,
                    hiddenShortcutIds = hiddenIds
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = PopupUiState.Closed
    )

    val menuState: StateFlow<PopupMenuState> = combine(popupState, isMenuOpen) { cardState, isOpen ->
        if (cardState is PopupUiState.Open && isOpen) {
            PopupMenuState.Open(owner = cardState.owner, items = menuItems(cardState.owner))
        } else {
            PopupMenuState.Closed
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
        editHiddenShortcuts
    ) { request, launchableState, shortcutResult, hidden ->
        val owner: Launchable? = request?.ownerKey?.let { ownerKey ->
            launchableState.launchables.firstOrNull { launchable -> launchable.key == ownerKey }
        }
        if (request == null || owner == null || shortcutResult == null || hidden.ownerKey != request.ownerKey) {
            PopupEditState.Closed
        } else {
            PopupEditState.Editing(owner = owner, shortcuts = shortcutResult, hiddenIds = hidden.ids)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = PopupEditState.Closed
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
        isMenuOpen.value = false
        cardShortcuts.value = EMPTY_SHORTCUTS
        openRequest.value = OpenRequest(
            target = ShortcutTarget(ownerKey = app.key, packageName = app.packageName, profile = app.profile),
            anchor = anchor
        )
    }

    fun close() {
        isMenuOpen.value = false
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
        if (popupState.value is PopupUiState.Open) {
            isMenuOpen.value = true
        }
    }

    fun closeMenu() {
        isMenuOpen.value = false
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
        isMenuOpen.value = false
        val target: ShortcutTarget = ShortcutTarget(ownerKey = app.key, packageName = app.packageName, profile = app.profile)
        if (editRequest.value != target) {
            editShortcuts.value = null
            editRequest.value = target
        }
    }

    fun finishEdit() {
        editRequest.value = null
    }

    /** Hides or shows a shortcut in the App currently being edited. */
    fun setShortcutHidden(shortcutId: String, hidden: Boolean) {
        val ownerKey: String = editRequest.value?.ownerKey ?: return
        viewModelScope.launch {
            try {
                popupRepository.setShortcutHidden(ownerKey = ownerKey, shortcutId = shortcutId, hidden = hidden)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Timber.e(exception, "Failed to change hidden state of shortcut %s", shortcutId)
            }
        }
    }

    fun onBack(): Boolean {
        if (editRequest.value != null) {
            finishEdit()
            return true
        }
        if (isMenuOpen.value) {
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

    private fun menuItems(owner: Launchable): List<PopupMenuItem> = if (owner is App) {
        listOf(PopupMenuItem.EditPopup, PopupMenuItem.AppInfo)
    } else {
        emptyList()
    }
}
