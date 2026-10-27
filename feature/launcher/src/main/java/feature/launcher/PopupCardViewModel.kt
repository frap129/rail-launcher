package feature.launcher

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.apps.model.App
import core.data.folders.Folder
import core.data.launchables.ILaunchableRepository
import core.data.launchables.model.Launchable
import core.data.notifications.INotificationSource
import core.data.notifications.NotificationSnapshot
import core.data.popups.EMPTY_SHORTCUTS
import core.data.popups.HiddenShortcutIds
import core.data.popups.IPopupRepository
import core.data.popups.OwnerItems
import core.data.popups.PopupContent
import core.data.popups.PopupContentResolver
import core.data.popups.PopupItem
import core.data.popups.ShortcutTarget
import core.data.popups.addedItems
import core.data.popups.collectShortcuts
import core.data.popups.findLaunchable
import core.data.popups.hiddenShortcutIds
import core.data.popups.ownerScoped
import core.data.popups.removeAddedItem
import core.data.popups.targetFor
import core.data.shortcuts.IShortcutSource
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
import core.popups.IPopupCard
import core.popups.IPopupCollaborators
import core.popups.IPopupSession
import core.popups.PopupAnchor
import core.popups.PopupUiState
import core.popups.PopupWidgetPage
import core.popups.PopupWidgetState
import core.popups.observeTargetLoss
import core.popups.popupWidgetMaxHeightDp
import core.ui.R as CoreUiR
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import core.util.EventChannel
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

private const val POPUP_REOPEN_OWNER_KEY: String = "popupReopenOwner"

private data class OpenRequest(val target: ShortcutTarget, val anchor: PopupAnchor?)

private data class CardExtras(
    val items: OwnerItems,
    val widget: PopupWidgetState?,
    val notifications: OwnerNotifications,
    val nowMillis: Long
)

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

private data class MenuTarget(val ownerKey: String, val itemKey: String?, val widgetId: Long? = null)

/** Owns the one open Pop-up, its menu, its Widget, its Notifications and the editor, pickers and Folder actions that host it. */
class PopupCardViewModel(
    private val launchableRepository: ILaunchableRepository,
    private val shortcutSource: IShortcutSource,
    private val popupRepository: IPopupRepository,
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
    private val savedStateHandle: SavedStateHandle,
    private val session: IPopupSession,
    val collaborators: IPopupCollaborators
) : ViewModel(),
    IPopupCard {
    private val openRequest: MutableStateFlow<OpenRequest?> = MutableStateFlow(null)
    private val menuTarget: MutableStateFlow<MenuTarget?> = MutableStateFlow(null)
    private val cardShortcuts: MutableStateFlow<ShortcutsResult?> = MutableStateFlow(EMPTY_SHORTCUTS)
    private val windowHeightDp: MutableStateFlow<Int?> = MutableStateFlow(null)
    private val widgetEvents: EventChannel<WidgetEvent> = EventChannel()
    private val pageSettler: WidgetPageSettler = WidgetPageSettler(scope = viewModelScope, repository = widgetRepository)
    private var hasPendingAdd: Boolean = false

    /** Events the Pop-up asks the Activity to run. */
    val events: Flow<WidgetEvent> = widgetEvents.flow

    /** The App Widget picker and result callbacks the open Pop-up drives. */
    val widgetFlow: PopupWidgetFlow = PopupWidgetFlow()

    internal val resizeState: StateFlow<ResizeState?> = resizeController.resizeState

    private val openTarget: Flow<ShortcutTarget?> = openRequest.map { request -> request?.target }

    private val openOwnerKey: Flow<String?> = openTarget.map { target -> target?.ownerKey }

    private val widgetPlace: StateFlow<WidgetPlace?> = openOwnerKey
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
    internal val widgetState: StateFlow<PopupWidgetState?> = combine(
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

    private val cardHiddenShortcuts: StateFlow<HiddenShortcutIds> = hiddenShortcutIds(
        scope = viewModelScope,
        targets = openTarget,
        popupRepository = popupRepository
    )

    private val cardItems: StateFlow<OwnerItems> = addedItems(
        scope = viewModelScope,
        targets = openTarget,
        popupRepository = popupRepository
    )

    private val cardNotifications: StateFlow<OwnerNotifications> = ownerNotifications(openTarget)

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

    private val notificationExpansion: MutableStateFlow<Map<String, Boolean>> = MutableStateFlow(emptyMap())

    private val sentNotifications: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())

    internal val popupState: StateFlow<PopupUiState> = combine(
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
    internal val expandedNotificationKeys: StateFlow<Set<String>> = combine(popupState, notificationExpansion) { state, expansion ->
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
    internal val sentNotificationKeys: StateFlow<Set<String>> = sentNotifications.asStateFlow()

    internal val menuState: StateFlow<PopupMenuState> = combine(
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

    /** The App Widget picker and result callbacks the launcher screen drives for the open Pop-up. */
    inner class PopupWidgetFlow internal constructor() {
        val state: StateFlow<AddWidgetFlowState> = addFlowController.state

        fun onBindResult(granted: Boolean) = this@PopupCardViewModel.onBindResult(granted = granted)

        fun onConfigureResult(ok: Boolean) = this@PopupCardViewModel.onConfigureResult(ok = ok)

        fun onReconfigureResult(ok: Boolean) = this@PopupCardViewModel.onReconfigureResult(ok = ok)

        fun setQuery(query: String) = this@PopupCardViewModel.setPickerQuery(query = query)

        fun pickProvider(provider: WidgetProviderInfo) = this@PopupCardViewModel.pickProvider(provider = provider)

        fun cancel() = this@PopupCardViewModel.cancelAdd()
    }

    init {
        collaborators.attachCard(card = this)
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
        collectShortcuts(
            scope = viewModelScope,
            targets = openTarget,
            reloads = session.shortcutReloads,
            shortcutSource = shortcutSource,
            into = cardShortcuts
        )
        observeTargetLoss(
            scope = viewModelScope,
            targetKeys = openOwnerKey,
            launchableRepository = launchableRepository,
            homeEditState = homeEditState,
            isClosedByMode = { mode ->
                mode != HomeEditMode.None &&
                    (mode !is HomeEditMode.Resizing || mode.placeId != widgetState.value?.placeId)
            },
            onTargetLost = ::close
        )
    }

    fun open(owner: Launchable, anchor: PopupAnchor?) {
        if (homeEditState.mode.value != HomeEditMode.None) {
            return
        }
        val target: ShortcutTarget = targetFor(owner) ?: return
        menuTarget.value = null
        session.onCardOpened()
        notificationExpansion.value = emptyMap()
        sentNotifications.value = emptySet()
        cardShortcuts.value = EMPTY_SHORTCUTS
        openRequest.value = OpenRequest(target = target, anchor = anchor)
    }

    override fun close() {
        finishResize()
        menuTarget.value = null
        session.onCardClosed()
        notificationExpansion.value = emptyMap()
        sentNotifications.value = emptySet()
        openRequest.value = null
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

    internal fun openMenu() {
        val open: PopupUiState.Open = popupState.value as? PopupUiState.Open ?: return
        menuTarget.value = MenuTarget(ownerKey = open.owner.key, itemKey = null)
    }

    /** Sends the notification's content intent and closes the card. */
    internal fun onOpenNotification(key: String) {
        val target: ShortcutTarget = openShortcutTarget() ?: return
        viewModelScope.launch {
            notificationSource.open(key)
            if (openRequest.value?.target == target) {
                close()
            }
        }
    }

    /** Dismisses a clearable notification; a notifications-only card then closes itself. */
    internal fun onDismissNotification(key: String) {
        openShortcutTarget() ?: return
        viewModelScope.launch {
            notificationSource.dismiss(key)
        }
    }

    /** Expands or collapses the notification [key]'s row. */
    internal fun toggleNotificationExpanded(key: String) {
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
    internal fun runNotificationAction(key: String, index: Int, replyText: String? = null) {
        if (openShortcutTarget() == null) {
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

    override fun closeMenu() {
        menuTarget.value = null
    }

    /** Opens the menu of the widget [widgetId] at the top of the card. */
    internal fun openWidgetMenu(widgetId: Long) {
        val open: PopupUiState.Open = popupState.value as? PopupUiState.Open ?: return
        val widget: PopupWidgetState = open.widget ?: return
        if (widget.pageOf(widgetId) == null || homeEditState.mode.value != HomeEditMode.None) {
            return
        }
        menuTarget.value = MenuTarget(ownerKey = open.owner.key, itemKey = null, widgetId = widgetId)
    }

    /** Supplies the window height so the widget stays within the Pop-up's share of it. */
    internal fun setWindowHeight(heightDp: Int) {
        if (heightDp <= 0) {
            return
        }
        windowHeightDp.value = heightDp
        resizeController.setHomeHeight(heightDp = popupWidgetMaxHeightDp(windowHeightDp = heightDp))
    }

    /** Stores the stack page the pager settled on. */
    internal fun onPageChanged(page: Int) {
        val widget: PopupWidgetState = widgetState.value ?: return
        if (page !in widget.pages.indices) {
            return
        }
        pageSettler.onPageSettled(placeId = widget.placeId, page = page)
    }

    /** Supplies the widget width for the size report sent after a Resize. */
    internal fun setCardWidth(widthDp: Int) = resizeController.setWidgetAreaWidth(widthDp = widthDp)

    /** Opens the picker for the owner's widget, remembering the card so it reopens after a system dialog. */
    internal fun startAddWidget() {
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

    private fun onBindResult(granted: Boolean) {
        viewModelScope.launch {
            handleStackGrown(placeId = addFlowController.onBindResult(granted = granted))
        }
    }

    private fun onConfigureResult(ok: Boolean) {
        viewModelScope.launch {
            handleStackGrown(placeId = addFlowController.onConfigureResult(ok = ok))
        }
    }

    private fun setPickerQuery(query: String) = addFlowController.setQuery(query = query)

    private fun pickProvider(provider: WidgetProviderInfo) {
        viewModelScope.launch {
            handleStackGrown(placeId = addFlowController.pick(provider = provider))
        }
    }

    private fun cancelAdd() {
        viewModelScope.launch {
            addFlowController.cancel()
        }
    }

    /** Enters Resize for the card's widget place. */
    internal fun resizeWidget() {
        val widget: PopupWidgetState = widgetState.value ?: return
        menuTarget.value = null
        viewModelScope.launch {
            resizeController.startResize(placeId = widget.placeId, ownerKey = widget.ownerKey)
        }
    }

    /** Leaves Resize, storing the height and reporting the new page size. */
    override fun finishResize() {
        resizeController.finishResizeAndReport(scope = viewModelScope) { report ->
            widgetEvents.send(report.toWidgetEvent())
        }
    }

    /** Removes the Stack page whose menu is open, or the current page, and keeps the card open. */
    internal fun removeWidget() {
        val page: PopupWidgetPage = widgetPageForMenu() ?: return
        menuTarget.value = null
        viewModelScope.launch {
            removeAppWidget.removeWidgetAndReport(widgetId = page.widgetId) { event -> widgetEvents.send(event) }
        }
    }

    /** Opens the menu page's provider configuration activity. */
    internal fun reconfigureWidget() {
        val page: PopupWidgetPage = widgetPageForMenu() ?: return
        if (!page.isReconfigurable) {
            return
        }
        menuTarget.value = null
        viewModelScope.launch {
            widgetEvents.send(WidgetEvent.LaunchReconfigure(id = AppWidgetId(page.appWidgetId)))
        }
    }

    private fun onReconfigureResult(ok: Boolean) {
        if (!ok) {
            viewModelScope.launch {
                widgetEvents.send(WidgetEvent.ShowMessage(message = CoreUiR.string.couldnt_open_widget_settings))
            }
        }
    }

    /** Asks the system to leave quiet mode for the widget page's work profile. */
    internal fun unpauseWorkProfile(widgetId: Long) {
        val page: PopupWidgetPage = widgetState.value?.pageOf(widgetId) ?: return
        requestQuietModeOff(profileSerial = page.profileSerial)
    }

    internal fun openItemMenu(item: PopupItem) {
        val open: PopupUiState.Open = popupState.value as? PopupUiState.Open ?: return
        if (open.content.items.none { popupItem -> popupItem.launchable.key == item.launchable.key }) {
            return
        }
        menuTarget.value = MenuTarget(ownerKey = open.owner.key, itemKey = item.launchable.key)
    }

    /** Removes the added item whose item menu is open from that menu's owner. */
    internal fun removeMenuItem() {
        val target: MenuTarget = menuTarget.value ?: return
        val itemKey: String = target.itemKey ?: return
        menuTarget.value = null
        viewModelScope.removeAddedItem(popupRepository = popupRepository, ownerKey = target.ownerKey, itemKey = itemKey)
    }

    internal fun dragResize(deltaDp: Float) = resizeController.dragResize(deltaDp = deltaDp)

    internal fun resetResize() = resizeController.resetResize()

    fun onBack(): Boolean = session.onBack()

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

    override fun isOpen(): Boolean = openRequest.value != null

    override fun currentOpenOwnerKey(): String? = openRequest.value?.target?.ownerKey

    override fun isResizing(): Boolean = resizeState.value != null

    override fun isItemMenuOpen(): Boolean = menuTarget.value?.itemKey != null

    override fun isMenuOpen(): Boolean = menuTarget.value != null

    /** Closes the card and its menu when [ownerKey]'s Folder is deleted. */
    override fun onOwnerDeleted(ownerKey: String) {
        menuTarget.value = null
        if (openRequest.value?.target?.ownerKey == ownerKey) {
            close()
        }
    }

    /**
     * Asks the system to leave quiet mode for [owner]'s profile and reloads its Shortcuts.
     * Defaults to the open card's owner.
     */
    internal fun unpauseOwnerProfile(owner: Launchable? = (popupState.value as? PopupUiState.Open)?.owner) {
        val app: App = owner?.appOwner ?: run {
            Timber.w("Ignoring quiet mode off without an open App Pop-up")
            return
        }
        val profileSerial: Long = appWidgetHost.profileSerial(profile = app.profile) ?: run {
            Timber.w("Could not find the profile for %s", app.profile)
            return
        }
        requestQuietModeOff(profileSerial = profileSerial)
        reloadShortcuts()
    }

    /** Asks the system to leave quiet mode for the profile with [profileSerial]. */
    private fun requestQuietModeOff(profileSerial: Long) {
        viewModelScope.launch {
            widgetEvents.send(WidgetEvent.RequestQuietModeOff(profileSerial = profileSerial))
        }
    }

    /** Queries the owner's shortcuts again, for example after a work profile is resumed. */
    internal fun reloadShortcuts() {
        session.reloadShortcuts()
    }

    private fun ownerNotifications(targets: Flow<ShortcutTarget?>): StateFlow<OwnerNotifications> = ownerScoped(
        scope = viewModelScope,
        targets = targets.map { target -> target?.ownerKey },
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

    private fun openShortcutTarget(): ShortcutTarget? = openRequest.value?.target

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

    override fun onCleared() {
        collaborators.release()
    }
}

/** Every notification in the card, with whether it starts expanded. */
private fun PopupContent.notificationRows(): List<NotificationExpansion> =
    ownerNotifications.map { snapshot -> NotificationExpansion(snapshot = snapshot, isExpandedByDefault = true) } +
        itemNotifications.values.flatten()
            .map { snapshot -> NotificationExpansion(snapshot = snapshot, isExpandedByDefault = false) }
