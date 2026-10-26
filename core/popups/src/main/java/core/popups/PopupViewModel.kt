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
import core.data.notifications.INotificationSource
import core.data.notifications.NotificationSnapshot
import core.data.popups.IPopupRepository
import core.data.popups.PopupItem
import core.data.shortcuts.AppShortcuts
import core.data.shortcuts.IShortcutSource
import core.data.shortcuts.ShortcutLaunchable
import core.data.shortcuts.ShortcutsResult
import core.data.time.IClockSource
import core.data.widgets.AppWidgetId
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IRemoveAppWidget
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetLifecycleReconciler
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.InsertPosition
import core.data.widgets.PlacedWidget
import core.data.widgets.ResizeBounds
import core.data.widgets.WidgetPlace
import core.data.widgets.WidgetProviderInfo
import core.data.widgets.canGrowStack
import core.data.widgets.canResizeStack
import core.data.widgets.stackDefaultHeightDp
import core.data.widgets.stackIndicatorVisible
import core.data.widgets.stackResizeBounds
import core.ui.R as CoreUiR
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import core.util.EventChannel
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import core.util.launchLogged
import core.widgets.AddWidgetFlowState
import core.widgets.AddWidgetOrigin
import core.widgets.IWidgetAddController
import core.widgets.IWidgetResizeController
import core.widgets.ResizeState
import core.widgets.WidgetEvent
import core.widgets.WidgetPageSettler
import core.widgets.WidgetSizeReport
import core.widgets.appWidgetMenuCapabilities
import core.widgets.finishResizeAndReport
import core.widgets.removeWidgetAndReport
import core.widgets.startWidgetWiring
import core.widgets.toAppWidgetModel
import core.widgets.toWidgetEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

private val EMPTY_SHORTCUTS: ShortcutsResult = ShortcutsResult.Available(shortcuts = emptyList())

private const val POPUP_REOPEN_OWNER_KEY: String = "popupReopenOwner"

private data class ShortcutTarget(val ownerKey: String, val packageName: String?, val profile: UserHandle?)

private data class OpenRequest(val target: ShortcutTarget, val anchor: PopupAnchor?)

private data class CardExtras(
    val items: OwnerItems,
    val widget: PopupWidgetState?,
    val notifications: OwnerNotifications,
    val nowMillis: Long
)

private data class HiddenShortcutIds(val ownerKey: String?, val ids: Set<String>)

private data class OwnerItems(val ownerKey: String?, val items: List<PopupItem>)

private data class OwnerNotifications(val ownerKey: String?, val notifications: List<NotificationSnapshot>)

private data class CardContentInputs(
    val target: ShortcutTarget?,
    val shortcuts: ShortcutsResult?,
    val hidden: HiddenShortcutIds,
    val items: OwnerItems,
    val notifications: OwnerNotifications
)

private data class CardClosability(val ownerKey: String?, val hasNotifications: Boolean, val keepsOpenWithoutNotifications: Boolean)

private data class PopupWidgetLayout(val ownerKey: String, val appWidgets: List<PlacedWidget.AppWidget>, val bounds: ResizeBounds)

private data class NotificationExpansion(val snapshot: NotificationSnapshot, val isExpandedByDefault: Boolean) {
    /** Whether this row is expanded, honouring an explicit toggle over its default. */
    fun isExpanded(overrides: Map<String, Boolean>): Boolean = overrides[snapshot.key] ?: isExpandedByDefault
}

private data class FolderMembership(val launchableKey: String?, val folderKeys: Set<String>)

private data class MenuTarget(val ownerKey: String, val itemKey: String?, val widgetId: Long? = null)

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
    private val notificationSource: INotificationSource,
    private val clockSource: IClockSource,
    private val homeEditState: HomeEditState,
    private val widgetRepository: IWidgetAreaRepository,
    private val addFlowController: IWidgetAddController,
    private val resizeController: IWidgetResizeController,
    private val appWidgetHost: IAppWidgetHostSource,
    private val removeAppWidget: IRemoveAppWidget,
    private val widgetLifecycleReconciler: IWidgetLifecycleReconciler,
    private val widgetSettingsRepository: IWidgetSettingsRepository,
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
    private val widgetEvents: EventChannel<WidgetEvent> = EventChannel()
    private val pageSettler: WidgetPageSettler = WidgetPageSettler(scope = viewModelScope, repository = widgetRepository)
    private var hasPendingAdd: Boolean = false

    /** Events the Pop-up asks the Activity to run. */
    val events: Flow<WidgetEvent> = widgetEvents.flow

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

    /** The widget place shown at the top of the open Pop-up. */
    val widgetState: StateFlow<PopupWidgetState?> = combine(
        widgetPlace,
        windowHeightDp,
        widgetSettingsRepository.widgetAreaSettings
    ) { place, windowHeight, settings ->
        place?.toPopupWidgetState(windowHeightDp = windowHeight, indicatorVisible = settings.stackIndicatorVisible)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = null
    )

    private val cardHiddenShortcuts: StateFlow<HiddenShortcutIds> = hiddenShortcutIds(openRequest.map { request -> request?.target })

    private val editHiddenShortcuts: StateFlow<HiddenShortcutIds> = hiddenShortcutIds(editRequest)

    private val cardItems: StateFlow<OwnerItems> = addedItems(openRequest.map { request -> request?.target })

    private val cardNotifications: StateFlow<OwnerNotifications> = ownerNotifications(openRequest.map { request -> request?.target })

    private val cardContentInputs: Flow<CardContentInputs> = combine(
        openRequest,
        cardShortcuts,
        cardHiddenShortcuts,
        cardItems,
        cardNotifications
    ) { request, shortcuts, hidden, items, notifications ->
        CardContentInputs(
            target = request?.target,
            shortcuts = shortcuts,
            hidden = hidden,
            items = items,
            notifications = notifications
        )
    }

    private val cardClosability: Flow<CardClosability> = combine(
        cardContentInputs,
        launchableRepository.launchableState,
        widgetPlace
    ) { inputs, launchableState, place ->
        val target: ShortcutTarget? = inputs.target
        val owner: Launchable? = target?.ownerKey?.let { ownerKey ->
            launchableState.launchables.findLaunchable(key = ownerKey)
        }
        if (target == null || owner == null) {
            CardClosability(ownerKey = null, hasNotifications = false, keepsOpenWithoutNotifications = false)
        } else {
            val content: PopupContent = resolveCardContent(
                owner = owner,
                target = target,
                shortcuts = inputs.shortcuts,
                hidden = inputs.hidden,
                items = inputs.items,
                notifications = inputs.notifications
            )
            val hasWidget: Boolean = place?.popupWidgetLayout()?.ownerKey == owner.key
            CardClosability(
                ownerKey = owner.key,
                hasNotifications = content.notificationRows().isNotEmpty(),
                keepsOpenWithoutNotifications = content.keepsOpenWithoutNotifications || hasWidget
            )
        }
    }.distinctUntilChanged()

    private val editItems: StateFlow<OwnerItems> = addedItems(editRequest)

    private val pickerItems: StateFlow<OwnerItems> = addedItems(pickerTarget)

    private val notificationExpansion: MutableStateFlow<Map<String, Boolean>> = MutableStateFlow(emptyMap())

    private val sentNotifications: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())

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

    val popupState: StateFlow<PopupUiState> = combine(
        openRequest,
        launchableRepository.launchableState,
        cardShortcuts,
        cardHiddenShortcuts,
        combine(cardItems, widgetState, cardNotifications, clockSource.readings) { items, widget, notifications, reading ->
            CardExtras(
                items = items,
                widget = widget,
                notifications = notifications,
                nowMillis = reading.instant.toEpochMilli()
            )
        }
    ) { request, launchableState, shortcutResult, hidden, extras ->
        val owner: Launchable? = request?.target?.ownerKey?.let { ownerKey ->
            launchableState.launchables.findLaunchable(key = ownerKey)
        }
        if (request == null || owner == null) {
            PopupUiState.Closed
        } else {
            val widget: PopupWidgetState? = extras.widget?.takeIf { state -> state.ownerKey == request.target.ownerKey }
            PopupUiState.Open(
                owner = owner,
                anchor = request.anchor,
                content = resolveCardContent(
                    owner = owner,
                    target = request.target,
                    shortcuts = shortcutResult,
                    hidden = hidden,
                    items = extras.items,
                    notifications = extras.notifications
                ),
                widget = widget,
                nowMillis = extras.nowMillis
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = PopupUiState.Closed
    )

    /** The notification keys shown expanded: the owner's rows start open, member rows start collapsed. */
    val expandedNotificationKeys: StateFlow<Set<String>> = combine(popupState, notificationExpansion) { state, expansion ->
        val open: PopupUiState.Open = state as? PopupUiState.Open ?: return@combine emptySet()
        open.content.notificationRows()
            .filter { expansionItem -> expansionItem.snapshot.isExpandable && expansionItem.isExpanded(expansion) }
            .mapTo(mutableSetOf()) { expansionItem -> expansionItem.snapshot.key }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = emptySet()
    )

    /** The notification keys whose inline reply was delivered, shown as Sent. */
    val sentNotificationKeys: StateFlow<Set<String>> = sentNotifications.asStateFlow()

    val menuState: StateFlow<PopupMenuState> = combine(
        popupState,
        menuTarget,
        widgetSettingsRepository.widgetAreaSettings
    ) { cardState, target, settings ->
        val open: PopupUiState.Open? = cardState as? PopupUiState.Open
        val resolvedTarget: Launchable? = when {
            open == null || target == null -> null
            target.itemKey == null -> open.owner
            else -> open.content.items.firstOrNull { item -> item.launchable.key == target.itemKey }?.launchable
        }
        val widget: PopupWidgetState? = open?.widget?.takeIf { state -> state.ownerKey == resolvedTarget?.key }
        val widgetPage: PopupWidgetPage? = target?.widgetId?.let { widgetId -> widget?.pageOf(widgetId) }
        val isWidgetMenu: Boolean = target?.widgetId != null
        if (resolvedTarget == null || (isWidgetMenu && widgetPage == null)) {
            PopupMenuState.Closed
        } else {
            PopupMenuState.Open(
                target = resolvedTarget,
                items = menuItems(
                    target = resolvedTarget,
                    isItem = target?.itemKey != null,
                    widget = widget,
                    widgetPage = widgetPage,
                    maxStackPages = settings.maxStackPages,
                    isWidgetMenu = isWidgetMenu
                ),
                widgetPage = widgetPage
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
        observeNotificationEmptying()
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
        notificationExpansion.value = emptyMap()
        sentNotifications.value = emptySet()
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
        notificationExpansion.value = emptyMap()
        sentNotifications.value = emptySet()
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

    /** Sends the notification's content intent and closes the card. */
    fun onOpenNotification(key: String) {
        val target: ShortcutTarget = openNotificationTarget() ?: return
        viewModelScope.launch {
            notificationSource.open(key)
            if (openRequest.value?.target == target) {
                close()
            }
        }
    }

    /** Dismisses a clearable notification; a notifications-only card then closes itself. */
    fun onDismissNotification(key: String) {
        openNotificationTarget() ?: return
        viewModelScope.launch {
            notificationSource.dismiss(key)
        }
    }

    /** Expands or collapses the notification [key]'s row. */
    fun toggleNotificationExpanded(key: String) {
        val open: PopupUiState.Open = popupState.value as? PopupUiState.Open ?: return
        val expansionItem: NotificationExpansion =
            open.content.notificationRows().firstOrNull { item -> item.snapshot.key == key } ?: return
        if (!expansionItem.snapshot.isExpandable) {
            return
        }
        val isExpanded: Boolean = expansionItem.isExpanded(notificationExpansion.value)
        notificationExpansion.update { current -> current + (key to !isExpanded) }
    }

    /** Runs [key]'s action [index], filling [replyText] into its RemoteInput when the action takes one. */
    fun runNotificationAction(key: String, index: Int, replyText: String? = null) {
        if (openNotificationTarget() == null) {
            Timber.w("Ignoring a notification action without an open Pop-up")
            return
        }
        viewModelScope.launch {
            val didRun: Boolean = notificationSource.runAction(key = key, index = index, replyText = replyText)
            when {
                didRun && replyText != null -> sentNotifications.update { keys -> keys + key }
                !didRun && replyText != null -> widgetEvents.send(WidgetEvent.ShowMessage(message = CoreUiR.string.couldnt_send))
            }
        }
    }

    fun closeMenu() {
        menuTarget.value = null
    }

    /** Opens the menu of the widget [widgetId] at the top of the card. */
    fun openWidgetMenu(widgetId: Long) {
        val open: PopupUiState.Open = popupState.value as? PopupUiState.Open ?: return
        val widget: PopupWidgetState = open.widget ?: return
        if (widget.pageOf(widgetId) == null || homeEditState.mode.value != HomeEditMode.None) {
            return
        }
        menuTarget.value = MenuTarget(ownerKey = open.owner.key, itemKey = null, widgetId = widgetId)
    }

    /** Supplies the window height so the widget stays within the Pop-up's share of it. */
    fun setWindowHeight(heightDp: Int) {
        if (heightDp <= 0) {
            return
        }
        windowHeightDp.value = heightDp
        resizeController.setHomeHeight(heightDp = popupWidgetMaxHeightDp(windowHeightDp = heightDp))
    }

    /** Stores the stack page the pager settled on. */
    fun onPageChanged(page: Int) {
        val widget: PopupWidgetState = widgetState.value ?: return
        if (page !in widget.pages.indices) {
            return
        }
        pageSettler.onPageSettled(placeId = widget.placeId, page = page)
    }

    /** Supplies the widget width for the size report sent after a Resize. */
    fun setCardWidth(widthDp: Int) = resizeController.setWidgetAreaWidth(widthDp = widthDp)

    /** Opens the picker for the owner's widget, remembering the card so it reopens after a system dialog. */
    fun startAddWidget() {
        val open: PopupUiState.Open = popupState.value as? PopupUiState.Open ?: return
        val owner: Launchable = open.owner
        val widget: PopupWidgetState? = open.widget
        viewModelScope.launch {
            val maxStackPages: Int = widgetSettingsRepository.widgetAreaSettings.first().maxStackPages
            if (widget != null && !canGrowStack(pageCount = widget.pages.size, maxStackPages = maxStackPages)) {
                widgetEvents.send(WidgetEvent.ShowMessage(message = CoreUiR.string.stack_full))
                return@launch
            }
            val started: Boolean = addFlowController.start(
                origin = AddWidgetOrigin(
                    insertAt = widget?.let { state -> InsertPosition.IntoStack(placeId = state.placeId) } ?: InsertPosition.End,
                    ownerKey = owner.key,
                    suggestedPackageName = (owner as? App)?.packageName
                )
            )
            if (started) {
                savedStateHandle[POPUP_REOPEN_OWNER_KEY] = owner.key
                menuTarget.value = null
            }
        }
    }

    fun onBindResult(granted: Boolean) {
        viewModelScope.launch {
            handleStackGrown(placeId = addFlowController.onBindResult(granted = granted))
        }
    }

    fun onConfigureResult(ok: Boolean) {
        viewModelScope.launch {
            handleStackGrown(placeId = addFlowController.onConfigureResult(ok = ok))
        }
    }

    fun setPickerQuery(query: String) = addFlowController.setQuery(query = query)

    fun pickProvider(provider: WidgetProviderInfo) {
        viewModelScope.launch {
            handleStackGrown(placeId = addFlowController.pick(provider = provider))
        }
    }

    fun cancelAdd() {
        viewModelScope.launch {
            addFlowController.cancel()
        }
    }

    /** Enters Resize for the card's widget place. */
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

    /** Removes the Stack page whose menu is open, or the current page, and keeps the card open. */
    fun removeWidget() {
        val page: PopupWidgetPage = widgetPageForMenu() ?: return
        menuTarget.value = null
        viewModelScope.launch {
            removeAppWidget.removeWidgetAndReport(widgetId = page.widgetId) { event -> widgetEvents.send(event) }
        }
    }

    /** Opens the menu page's provider configuration activity. */
    fun reconfigureWidget() {
        val page: PopupWidgetPage = widgetPageForMenu() ?: return
        if (!page.isReconfigurable) {
            return
        }
        menuTarget.value = null
        viewModelScope.launch {
            widgetEvents.send(WidgetEvent.LaunchReconfigure(id = AppWidgetId(page.appWidgetId)))
        }
    }

    fun onReconfigureResult(ok: Boolean) {
        if (!ok) {
            viewModelScope.launch {
                widgetEvents.send(WidgetEvent.ShowMessage(message = CoreUiR.string.couldnt_open_widget_settings))
            }
        }
    }

    /** Asks the system to leave quiet mode for the widget page's work profile. */
    fun unpauseWorkProfile(widgetId: Long) {
        val page: PopupWidgetPage = widgetState.value?.pageOf(widgetId) ?: return
        viewModelScope.launch {
            widgetEvents.send(WidgetEvent.RequestQuietModeOff(profileSerial = page.profileSerial))
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

    /** Removes the added item whose item menu is open from that menu's owner. */
    fun removeMenuItem() {
        val target: MenuTarget = menuTarget.value ?: return
        val itemKey: String = target.itemKey ?: return
        menuTarget.value = null
        removeAddedItem(ownerKey = target.ownerKey, itemKey = itemKey)
    }

    /** Removes [item] from the Pop-up currently being edited. */
    fun removeEditedItem(item: Launchable) {
        val ownerKey: String = editRequest.value?.ownerKey ?: return
        removeAddedItem(ownerKey = ownerKey, itemKey = item.key)
    }

    private fun removeAddedItem(ownerKey: String, itemKey: String) {
        launchLogged(message = "Failed to remove $itemKey from its Pop-up") {
            popupRepository.removeItem(ownerKey = ownerKey, itemKey = itemKey)
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
            .findLaunchable(key = request.ownerKey)
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

    private fun <T> ownerScoped(targets: Flow<ShortcutTarget?>, emptyValue: (String?) -> T, source: (String) -> Flow<T>): StateFlow<T> =
        targets
            .map { target -> target?.ownerKey }
            .distinctUntilChanged()
            .flatMapLatest { ownerKey ->
                if (ownerKey == null) {
                    flowOf(emptyValue(null))
                } else {
                    source(ownerKey).onStart { emit(emptyValue(ownerKey)) }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
                initialValue = emptyValue(null)
            )

    private fun addedItems(targets: Flow<ShortcutTarget?>): StateFlow<OwnerItems> = ownerScoped(
        targets = targets,
        emptyValue = { ownerKey -> OwnerItems(ownerKey = ownerKey, items = emptyList()) },
        source = { ownerKey ->
            popupRepository.items(ownerKey).map { items -> OwnerItems(ownerKey = ownerKey, items = items) }
        }
    )

    private fun ownerNotifications(targets: Flow<ShortcutTarget?>): StateFlow<OwnerNotifications> = ownerScoped(
        targets = targets,
        emptyValue = { ownerKey -> OwnerNotifications(ownerKey = ownerKey, notifications = emptyList()) },
        source = { ownerKey ->
            notificationSource.notifications.map { notifications -> OwnerNotifications(ownerKey = ownerKey, notifications = notifications) }
        }
    )

    private fun resolveCardContent(
        owner: Launchable,
        target: ShortcutTarget,
        shortcuts: ShortcutsResult?,
        hidden: HiddenShortcutIds,
        items: OwnerItems,
        notifications: OwnerNotifications
    ): PopupContent = PopupContentResolver.resolve(
        owner = owner,
        shortcuts = shortcuts ?: EMPTY_SHORTCUTS,
        hiddenShortcutIds = if (hidden.ownerKey == target.ownerKey) hidden.ids else emptySet(),
        items = if (items.ownerKey == target.ownerKey) items.items else emptyList(),
        notifications = if (notifications.ownerKey == target.ownerKey) notifications.notifications else emptyList()
    )

    /** Closes a card whose only content was notifications once the last one it shows is gone. */
    private fun observeNotificationEmptying() {
        viewModelScope.launch {
            var previousOwnerKey: String? = null
            var hadNotifications: Boolean = false
            cardClosability.collect { closability ->
                try {
                    if (closability.ownerKey == previousOwnerKey && hadNotifications && !closability.hasNotifications) {
                        closeNotificationOnlyCard(closability = closability)
                    }
                    previousOwnerKey = closability.ownerKey
                    hadNotifications = closability.hasNotifications
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Timber.e(exception, "Popup notification emptying failed")
                }
            }
        }
    }

    /**
     * Closes the card when notifications were its only content. A Folder's member rows are its
     * own content, not added items, so they do not keep its card open.
     */
    private fun closeNotificationOnlyCard(closability: CardClosability) {
        if (closability.keepsOpenWithoutNotifications) {
            return
        }
        close()
    }

    private fun openNotificationTarget(): ShortcutTarget? = openRequest.value?.target

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

    private fun menuItems(
        target: Launchable,
        isItem: Boolean,
        widget: PopupWidgetState?,
        widgetPage: PopupWidgetPage?,
        maxStackPages: Int,
        isWidgetMenu: Boolean
    ): List<PopupMenuItem> = buildList {
        if (isItem) {
            add(PopupMenuItem.RemoveFromPopup)
        } else if (isWidgetMenu) {
            if (widget == null || widgetPage == null) {
                return@buildList
            }
            addWidgetActions(widget = widget, page = widgetPage)
        } else {
            when (target) {
                is App -> add(PopupMenuItem.EditPopup)
                is Folder -> add(PopupMenuItem.EditFolder)
                else -> Unit
            }
            if (widget == null) {
                add(PopupMenuItem.AddWidget)
            } else {
                if (canGrowStack(pageCount = widget.pages.size, maxStackPages = maxStackPages)) {
                    add(PopupMenuItem.AddToStack)
                }
                widget.currentPageOrNull()?.let { page -> addWidgetActions(widget = widget, page = page) }
            }
            if (target is Folder) {
                add(PopupMenuItem.DeleteFolder)
            }
        }
        val isAppInfoAvailable: Boolean = appInfoFor(target = target, widgetPage = widgetPage) != null
        if (isAppInfoAvailable) {
            add(PopupMenuItem.AppInfo)
        }
    }

    private fun MutableList<PopupMenuItem>.addWidgetActions(widget: PopupWidgetState, page: PopupWidgetPage) {
        if (widget.isResizable) {
            add(PopupMenuItem.ResizeWidget)
        }
        if (page.isReconfigurable) {
            add(PopupMenuItem.ReconfigureWidget)
        }
        add(if (widget.isStack) PopupMenuItem.RemoveFromStack else PopupMenuItem.RemoveWidget)
    }

    private fun widgetPageForMenu(): PopupWidgetPage? {
        val widget: PopupWidgetState = widgetState.value ?: return null
        val widgetId: Long = menuTarget.value?.widgetId ?: return widget.currentPageOrNull()
        return widget.pageOf(widgetId)
    }

    private suspend fun handleStackGrown(placeId: Long?) {
        if (placeId == null) {
            return
        }
        val report: WidgetSizeReport = resizeController.onStackGrown(
            placeId = placeId,
            headerHeightDp = null,
            ownerKey = openRequest.value?.target?.ownerKey
        ) ?: return
        widgetEvents.send(report.toWidgetEvent())
    }

    private fun WidgetPlace.popupWidgetLayout(): PopupWidgetLayout? {
        val ownerKey: String = ownerKey ?: return null
        val appWidgets: List<PlacedWidget.AppWidget> = widgets.filterIsInstance<PlacedWidget.AppWidget>()
        if (appWidgets.isEmpty()) {
            return null
        }
        val bounds: ResizeBounds = widgets.stackResizeBounds(providerInfo = ::providerInfoFor) ?: return null
        return PopupWidgetLayout(ownerKey = ownerKey, appWidgets = appWidgets, bounds = bounds)
    }

    private fun WidgetPlace.toPopupWidgetState(windowHeightDp: Int?, indicatorVisible: Boolean): PopupWidgetState? {
        val layout: PopupWidgetLayout = popupWidgetLayout() ?: return null
        val pages: List<PopupWidgetPage> = layout.appWidgets.map { widget -> widget.toPopupWidgetPage() }
        return PopupWidgetState(
            placeId = id,
            ownerKey = layout.ownerKey,
            pages = pages,
            currentPage = currentPage.coerceIn(0, pages.lastIndex),
            heightDp = layout.bounds.clamp(
                heightDp = heightDp ?: widgets.stackDefaultHeightDp(providerInfo = ::providerInfoFor) ?: layout.bounds.minDp,
                homeHeightDp = windowHeightDp?.let(::popupWidgetMaxHeightDp) ?: Int.MAX_VALUE
            ),
            bounds = layout.bounds,
            isResizable = widgets.canResizeStack(bounds = layout.bounds),
            indicatorVisible = widgets.stackIndicatorVisible(settingVisible = indicatorVisible)
        )
    }

    private fun PlacedWidget.AppWidget.toPopupWidgetPage(): PopupWidgetPage {
        val info: WidgetProviderInfo? = providerInfoFor(this)
        return PopupWidgetPage(
            widgetId = id,
            appWidgetId = appWidgetId,
            provider = provider,
            profileSerial = profileSerial,
            model = toAppWidgetModel(info = info),
            isReconfigurable = appWidgetMenuCapabilities(widget = this, info = info).isReconfigurable
        )
    }

    private fun providerInfoFor(widget: PlacedWidget.AppWidget): WidgetProviderInfo? =
        appWidgetHost.providerInfo(id = AppWidgetId(widget.appWidgetId))

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
            val owner: Launchable = launchables.findLaunchable(key = ownerKey) ?: run {
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

/** The Launchable with [key], or null when it is not available. */
private fun List<Launchable>.findLaunchable(key: String): Launchable? = firstOrNull { launchable -> launchable.key == key }

/** Every notification in the card, with whether it starts expanded. */
private fun PopupContent.notificationRows(): List<NotificationExpansion> =
    ownerNotifications.map { snapshot -> NotificationExpansion(snapshot = snapshot, isExpandedByDefault = true) } +
        itemNotifications.values.flatten()
            .map { snapshot -> NotificationExpansion(snapshot = snapshot, isExpandedByDefault = false) }
