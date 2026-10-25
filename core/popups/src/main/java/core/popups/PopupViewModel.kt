package core.popups

import android.os.UserHandle
import androidx.lifecycle.SavedStateHandle
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
import core.data.widgets.AppWidgetId
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IRemoveAppWidget
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetLifecycleReconciler
import core.data.widgets.InsertPosition
import core.data.widgets.PlacedWidget
import core.data.widgets.ResizeBounds
import core.data.widgets.WidgetPlace
import core.data.widgets.WidgetProviderInfo
import core.data.widgets.resizeBounds
import core.ui.R as CoreUiR
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import core.util.EventChannel
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import core.util.launchLogged
import core.widgets.AddWidgetFlowState
import core.widgets.AddWidgetOrigin
import core.widgets.AppWidgetMenuCapabilities
import core.widgets.IWidgetAddController
import core.widgets.IWidgetResizeController
import core.widgets.ResizeState
import core.widgets.WidgetEvent
import core.widgets.appWidgetMenuCapabilities
import core.widgets.finishResizeAndReport
import core.widgets.removeWidgetAndReport
import core.widgets.startWidgetWiring
import core.widgets.toAppWidgetModel
import core.widgets.toWidgetEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

private val EMPTY_SHORTCUTS: ShortcutsResult = ShortcutsResult.Available(shortcuts = emptyList())

private const val POPUP_REOPEN_OWNER_KEY: String = "popupReopenOwner"

private data class ShortcutTarget(val ownerKey: String, val packageName: String?, val profile: UserHandle?)

private data class OpenRequest(val target: ShortcutTarget, val anchor: PopupAnchor?)

private data class CardItemsAndWidget(val items: OwnerItems, val widget: PopupWidgetState?)

private data class HiddenShortcutIds(val ownerKey: String?, val ids: Set<String>)

private data class OwnerItems(val ownerKey: String?, val items: List<PopupItem>)

private data class FolderMembership(val launchableKey: String?, val folderKeys: Set<String>)

private data class MenuTarget(val ownerKey: String, val itemKey: String?, val onWidget: Boolean = false)

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
    private val homeEditState: HomeEditState,
    private val widgetRepository: IWidgetAreaRepository,
    private val addFlowController: IWidgetAddController,
    private val resizeController: IWidgetResizeController,
    private val appWidgetHost: IAppWidgetHostSource,
    private val removeAppWidget: IRemoveAppWidget,
    private val widgetLifecycleReconciler: IWidgetLifecycleReconciler,
    private val savedStateHandle: SavedStateHandle
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
    private val windowHeightDp: MutableStateFlow<Int?> = MutableStateFlow(null)
    private val widgetEvents: Channel<WidgetEvent> = Channel(Channel.BUFFERED)
    private var hasPendingAdd: Boolean = false

    /** Events the Pop-up asks the Activity to run. */
    val events: Flow<WidgetEvent> = widgetEvents.receiveAsFlow()

    /** The App Widget add flow the Pop-up opened. */
    val addFlowState: StateFlow<AddWidgetFlowState> = addFlowController.state

    val resizeState: StateFlow<ResizeState?> = resizeController.resizeState

    private val widgetPlace: StateFlow<WidgetPlace?> = openRequest
        .map { request -> request?.target?.ownerKey }
        .distinctUntilChanged()
        .flatMapLatest { ownerKey ->
            if (ownerKey == null) flowOf(null) else widgetRepository.popupPlace(ownerKey = ownerKey)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
            initialValue = null
        )

    /** The widget shown at the top of the open Pop-up. */
    val widgetState: StateFlow<PopupWidgetState?> = combine(widgetPlace, windowHeightDp) { place, windowHeight ->
        place?.toPopupWidgetState(windowHeightDp = windowHeight)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = null
    )

    private val cardHiddenShortcuts: StateFlow<HiddenShortcutIds> = hiddenShortcutIds(openRequest.map { request -> request?.target })

    private val editHiddenShortcuts: StateFlow<HiddenShortcutIds> = hiddenShortcutIds(editRequest)

    private val cardItems: StateFlow<OwnerItems> = addedItems(openRequest.map { request -> request?.target })

    private val editItems: StateFlow<OwnerItems> = addedItems(editRequest)

    private val pickerItems: StateFlow<OwnerItems> = addedItems(pickerTarget)

    private val addToFolderRequest: MutableStateFlow<Launchable?> = MutableStateFlow(null)

    private val folderAdditionRequests: EventChannel<Folder> = EventChannel()

    private val addToFolderMembership: StateFlow<FolderMembership> = addToFolderRequest
        .map { launchable -> launchable?.key }
        .distinctUntilChanged()
        .flatMapLatest { launchableKey -> folderMemberships(launchableKey = launchableKey) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
            initialValue = FolderMembership(launchableKey = null, folderKeys = emptySet())
        )

    /** The Folder awaiting delete confirmation, if any. */
    val folderDeleteState: StateFlow<Folder?> = pendingFolderDelete

    /** Folders created from the Add to Folder sheet, for its "Added to ..." Snackbar. */
    val folderAdditions: SharedFlow<Folder> = folderAdditionRequests.flow

    val addToFolderState: StateFlow<AddToFolderState> = combine(
        addToFolderRequest,
        launchableRepository.launchableState,
        folderRepository.folders,
        addToFolderMembership
    ) { requestedLaunchable, launchableState, folders, membership ->
        val launchable: Launchable? = requestedLaunchable?.let { requested ->
            launchableState.launchables.firstOrNull { candidate -> candidate.key == requested.key }
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

    val popupState: StateFlow<PopupUiState> = combine(
        openRequest,
        launchableRepository.launchableState,
        cardShortcuts,
        cardHiddenShortcuts,
        combine(cardItems, widgetState) { items, widget -> CardItemsAndWidget(items = items, widget = widget) }
    ) { request, launchableState, shortcutResult, hidden, extras ->
        val owner: Launchable? = request?.target?.ownerKey?.let { ownerKey ->
            launchableState.launchables.firstOrNull { launchable -> launchable.key == ownerKey }
        }
        if (request == null || owner == null) {
            PopupUiState.Closed
        } else {
            val hiddenIds: Set<String> = if (hidden.ownerKey == request.target.ownerKey) hidden.ids else emptySet()
            val popupItems: List<PopupItem> = if (extras.items.ownerKey == request.target.ownerKey) extras.items.items else emptyList()
            val widget: PopupWidgetState? = extras.widget?.takeIf { state -> state.ownerKey == request.target.ownerKey }
            PopupUiState.Open(
                owner = owner,
                anchor = request.anchor,
                content = PopupContentResolver.resolve(
                    owner = owner,
                    shortcuts = shortcutResult ?: EMPTY_SHORTCUTS,
                    hiddenShortcutIds = hiddenIds,
                    items = popupItems
                ),
                widget = widget
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
        val widgetMenu: Boolean = target?.onWidget == true
        val widget: PopupWidgetState? = open?.widget?.takeIf { state -> widgetMenu && state.ownerKey == resolvedTarget?.key }
        if (resolvedTarget == null || (widgetMenu && widget == null)) {
            PopupMenuState.Closed
        } else {
            PopupMenuState.Open(
                target = resolvedTarget,
                items = menuItems(
                    target = resolvedTarget,
                    isItem = target?.itemKey != null,
                    widget = open?.widget,
                    isWidgetMenu = widgetMenu
                ),
                widget = widget
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
        addFlowController.startWidgetWiring(
            scope = viewModelScope,
            appWidgetHost = appWidgetHost,
            lifecycleReconciler = widgetLifecycleReconciler,
            onEvent = { event -> widgetEvents.send(event) }
        )
        viewModelScope.launch {
            popupRepository.removedWidgetIds.collect { appWidgetId ->
                widgetEvents.send(WidgetEvent.RemoveHostView(appWidgetId = appWidgetId))
            }
        }
        viewModelScope.launch {
            addFlowController.state.collect { state ->
                if (state != AddWidgetFlowState.Idle) {
                    hasPendingAdd = true
                } else if (hasPendingAdd) {
                    hasPendingAdd = false
                    clearReopenKeys()
                }
            }
        }
        viewModelScope.launch {
            widgetPlace.collect { place ->
                resizeController.onPlacesChanged(
                    places = listOfNotNull(place),
                    ownerKey = openRequest.value?.target?.ownerKey
                )
            }
        }
        restoreOpenAfterAdd()
        collectShortcuts(targets = openRequest.map { request -> request?.target }, into = cardShortcuts)
        collectShortcuts(targets = editRequest, into = editShortcuts)
        observeTargetLoss(
            targetKeys = openRequest.map { request -> request?.target?.ownerKey },
            isClosedByMode = { mode ->
                mode != HomeEditMode.None &&
                    (mode !is HomeEditMode.Resizing || mode.placeId != widgetState.value?.placeId)
            },
            onTargetLost = ::close
        )
        observeTargetLoss(
            targetKeys = editRequest.map { request -> request?.ownerKey },
            isClosedByMode = { mode -> mode != HomeEditMode.None && mode != HomeEditMode.Favorites },
            onTargetLost = ::finishEdit
        )
        observeTargetLoss(
            targetKeys = addToFolderRequest.map { launchable -> launchable?.key },
            isClosedByMode = { mode -> mode != HomeEditMode.None },
            onTargetLost = ::closeAddToFolder
        )
    }

    fun open(owner: Launchable, anchor: PopupAnchor?) {
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
        finishResize()
        menuTarget.value = null
        pendingFolderDelete.value = null
        pickerTarget.value = null
        isPicking.value = false
        isPickingShortcuts.value = false
        editRequest.value = null
        openRequest.value = null
        addToFolderRequest.value = null
        if (addFlowController.state.value == AddWidgetFlowState.Idle) {
            clearReopenKeys()
        }
    }

    /** Closes the card on leaving the app, unless an add flow has to reopen it afterwards. */
    fun onStopped() {
        if (addFlowController.state.value == AddWidgetFlowState.Idle) {
            close()
        }
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

    /** Opens the menu of the widget [widgetId] at the top of the card. */
    fun openWidgetMenu(widgetId: Long) {
        val open: PopupUiState.Open = popupState.value as? PopupUiState.Open ?: return
        val widget: PopupWidgetState = open.widget ?: return
        if (widget.widgetId != widgetId || homeEditState.mode.value != HomeEditMode.None) {
            return
        }
        menuTarget.value = MenuTarget(ownerKey = open.owner.key, itemKey = null, onWidget = true)
    }

    /** Supplies the window height so the widget stays within the Pop-up's share of it. */
    fun setWindowHeight(heightDp: Int) {
        if (heightDp <= 0) {
            return
        }
        windowHeightDp.value = heightDp
        resizeController.setHomeHeight(heightDp = popupWidgetMaxHeightDp(windowHeightDp = heightDp))
    }

    /** Supplies the widget width for the size report sent after a Resize. */
    fun setCardWidth(widthDp: Int) = resizeController.setWidgetAreaWidth(widthDp = widthDp)

    /** Opens the picker for the owner's widget, remembering the card so it reopens after a system dialog. */
    fun startAddWidget() {
        val open: PopupUiState.Open = popupState.value as? PopupUiState.Open ?: return
        if (open.widget != null) {
            return
        }
        val owner: Launchable = open.owner
        val started: Boolean = addFlowController.start(
            origin = AddWidgetOrigin(
                insertAt = InsertPosition.End,
                ownerKey = owner.key,
                suggestedPackageName = (owner as? App)?.packageName
            )
        )
        if (started) {
            savedStateHandle[POPUP_REOPEN_OWNER_KEY] = owner.key
            menuTarget.value = null
        }
    }

    fun onBindResult(granted: Boolean) {
        viewModelScope.launch {
            addFlowController.onBindResult(granted = granted)
        }
    }

    fun onConfigureResult(ok: Boolean) {
        viewModelScope.launch {
            addFlowController.onConfigureResult(ok = ok)
        }
    }

    fun setPickerQuery(query: String) = addFlowController.setQuery(query = query)

    fun pickProvider(provider: WidgetProviderInfo) {
        viewModelScope.launch {
            addFlowController.pick(provider = provider)
        }
    }

    fun cancelAdd() {
        viewModelScope.launch {
            addFlowController.cancel()
        }
    }

    /** Enters Resize for the card's widget. */
    fun resizeWidget() {
        val widget: PopupWidgetState = widgetState.value ?: return
        menuTarget.value = null
        viewModelScope.launch {
            resizeController.startResize(placeId = widget.placeId, ownerKey = widget.ownerKey)
        }
    }

    /** Leaves Resize, storing the height and reporting the new page size. */
    fun finishResize() {
        resizeController.finishResizeAndReport(scope = viewModelScope) { report ->
            widgetEvents.send(report.toWidgetEvent())
        }
    }

    /** Removes the card's widget, frees its App Widget id and keeps the card open. */
    fun removeWidget() {
        val widget: PopupWidgetState = widgetState.value ?: return
        menuTarget.value = null
        viewModelScope.launch {
            removeAppWidget.removeWidgetAndReport(widgetId = widget.widgetId) { event -> widgetEvents.send(event) }
        }
    }

    /** Opens the card widget's provider configuration activity. */
    fun reconfigureWidget() {
        val widget: PopupWidgetState = widgetState.value ?: return
        if (!widget.isReconfigurable) {
            return
        }
        menuTarget.value = null
        viewModelScope.launch {
            widgetEvents.send(WidgetEvent.LaunchReconfigure(id = AppWidgetId(widget.appWidgetId)))
        }
    }

    fun onReconfigureResult(ok: Boolean) {
        if (!ok) {
            viewModelScope.launch {
                widgetEvents.send(WidgetEvent.ShowMessage(message = CoreUiR.string.couldnt_open_widget_settings))
            }
        }
    }

    /** Asks the system to leave quiet mode for the widget's work profile. */
    fun unpauseWorkProfile(widgetId: Long) {
        val widget: PopupWidgetState = widgetState.value?.takeIf { state -> state.widgetId == widgetId } ?: return
        viewModelScope.launch {
            widgetEvents.send(WidgetEvent.RequestQuietModeOff(profileSerial = widget.profileSerial))
        }
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
        addToFolderRequest.value = null
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
        launchLogged(message = "Failed to change hidden state of shortcut $shortcutId") {
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
        launchLogged(message = "Failed to remove ${item.key} from its Pop-up") {
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
        launchLogged(message = "Failed to delete Folder ${folder.key}") {
            folderRepository.delete(folder)
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

    fun closeAddToFolder() {
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
        launchLogged(message = "Failed to change the items in ${request.ownerKey}'s Pop-up") {
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
        launchLogged(message = "Failed to change the shortcuts in ${request.ownerKey}'s Pop-up") {
            popupRepository.setItems(ownerKey = request.ownerKey, add = additions, remove = removals)
        }
    }

    fun cancelShortcutPicking() {
        isPickingShortcuts.value = false
    }

    fun setItemOrder(orderedItemKeys: List<String>) {
        val ownerKey: String = editRequest.value?.ownerKey ?: return
        launchLogged(message = "Failed to reorder the items in $ownerKey's Pop-up") {
            popupRepository.setItemOrder(ownerKey = ownerKey, orderedItemKeys = orderedItemKeys)
        }
    }

    fun dragResize(deltaDp: Float) = resizeController.dragResize(deltaDp = deltaDp)

    fun resetResize() = resizeController.resetResize()

    fun onBack(): Boolean {
        if (resizeState.value != null) {
            finishResize()
            return true
        }
        if (addToFolderRequest.value != null) {
            closeAddToFolder()
            return true
        }
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
        if (resizeState.value != null) {
            finishResize()
        } else {
            close()
        }
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

    private fun observeTargetLoss(targetKeys: Flow<String?>, isClosedByMode: (HomeEditMode) -> Boolean, onTargetLost: () -> Unit) {
        viewModelScope.launch {
            combine(
                targetKeys,
                launchableRepository.launchableState,
                homeEditState.mode
            ) { targetKey, launchableState, mode -> Triple(targetKey, launchableState, mode) }
                .collect { (targetKey, launchableState, mode) ->
                    if (targetKey == null) {
                        return@collect
                    }
                    val isOwnerMissing: Boolean =
                        launchableState.isLoaded &&
                            launchableState.launchables.none { launchable -> launchable.key == targetKey }
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

    private fun menuItems(target: Launchable, isItem: Boolean, widget: PopupWidgetState?, isWidgetMenu: Boolean): List<PopupMenuItem> =
        buildList {
            if (isItem) {
                add(PopupMenuItem.RemoveFromPopup)
                if (target is App || target is ShortcutLaunchable) {
                    add(PopupMenuItem.AppInfo)
                }
            } else if (isWidgetMenu) {
                if (widget == null) {
                    return@buildList
                }
                addWidgetActions(widget = widget)
                add(PopupMenuItem.AppInfo)
            } else {
                when (target) {
                    is App -> add(PopupMenuItem.EditPopup)
                    is Folder -> add(PopupMenuItem.EditFolder)
                    else -> Unit
                }
                if (widget == null) {
                    add(PopupMenuItem.AddWidget)
                } else {
                    addWidgetActions(widget = widget)
                }
                if (target is Folder) {
                    add(PopupMenuItem.DeleteFolder)
                }
                if (target is App) {
                    add(PopupMenuItem.AppInfo)
                }
            }
        }

    private fun MutableList<PopupMenuItem>.addWidgetActions(widget: PopupWidgetState) {
        if (widget.isResizable) {
            add(PopupMenuItem.ResizeWidget)
        }
        if (widget.isReconfigurable) {
            add(PopupMenuItem.ReconfigureWidget)
        }
        add(PopupMenuItem.RemoveWidget)
    }

    private fun WidgetPlace.toPopupWidgetState(windowHeightDp: Int?): PopupWidgetState? {
        val widget: PlacedWidget.AppWidget = widgets.singleOrNull() as? PlacedWidget.AppWidget ?: return null
        val ownerKey: String = ownerKey ?: return null
        val info: WidgetProviderInfo? = appWidgetHost.providerInfo(id = AppWidgetId(widget.appWidgetId))
        val bounds: ResizeBounds = widget.resizeBounds(headerHeightDp = null) { info }
        val defaultHeightDp: Int = info?.minHeightDp ?: widget.defaultHeightDp ?: ResizeBounds.MIN_HEIGHT_DP
        val capabilities: AppWidgetMenuCapabilities = appWidgetMenuCapabilities(
            widget = widget,
            info = info,
            resizeBounds = bounds
        )
        return PopupWidgetState(
            widgetId = widget.id,
            placeId = id,
            ownerKey = ownerKey,
            appWidgetId = widget.appWidgetId,
            provider = widget.provider,
            profileSerial = widget.profileSerial,
            model = widget.toAppWidgetModel(info = info),
            heightDp = bounds.clamp(
                heightDp = heightDp ?: defaultHeightDp,
                homeHeightDp = windowHeightDp?.let(::popupWidgetMaxHeightDp) ?: Int.MAX_VALUE
            ),
            bounds = bounds,
            isResizable = capabilities.canResize(widget),
            isReconfigurable = capabilities.isReconfigurable
        )
    }

    private fun clearReopenKeys() {
        savedStateHandle.remove<String>(POPUP_REOPEN_OWNER_KEY)
    }

    private fun restoreOpenAfterAdd() {
        val ownerKey: String = savedStateHandle.get<String>(POPUP_REOPEN_OWNER_KEY) ?: return
        viewModelScope.launch {
            val launchables: List<Launchable> = launchableRepository.launchableState
                .filter { state -> state.isLoaded }
                .first()
                .launchables
            val owner: Launchable = launchables.firstOrNull { launchable -> launchable.key == ownerKey } ?: run {
                clearReopenKeys()
                return@launch
            }
            open(owner = owner, anchor = null)
        }
    }

    private fun targetFor(owner: Launchable): ShortcutTarget? = when (owner) {
        is App -> ShortcutTarget(ownerKey = owner.key, packageName = owner.packageName, profile = owner.profile)
        is Folder -> ShortcutTarget(ownerKey = owner.key, packageName = null, profile = null)
        else -> null
    }
}
