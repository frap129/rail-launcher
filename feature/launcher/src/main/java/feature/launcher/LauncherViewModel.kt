package feature.launcher

import android.content.IntentSender
import android.os.UserHandle
import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.apps.model.App
import core.data.favorites.IFavoritesRepository
import core.data.folders.IFolderRepository
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.notifications.INotificationSource
import core.data.notifications.NotificationSnapshot
import core.data.popups.IPopupRepository
import core.data.popups.PopupContentResolver
import core.data.popups.PopupItem
import core.data.prefs.IPreferencesRepository
import core.data.profiles.IProfileSource
import core.data.profiles.QuietRequestResult
import core.data.profiles.isHiddenWhenLocked
import core.data.profiles.privateSpace
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import core.util.launchLogged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

sealed class LauncherUiState {
    data object Loading : LauncherUiState()

    data class Home(val lazyListState: LazyListState, val favorites: List<Launchable>, val isEditingFavorites: Boolean = false) :
        LauncherUiState()

    sealed class AppList : LauncherUiState() {
        abstract val isEditingFavorites: Boolean

        data class Browsing(
            val groups: List<LaunchableGroup.Listed>,
            val lazyListState: LazyListState,
            override val isEditingFavorites: Boolean
        ) : AppList()

        data class Scrolling(val group: LaunchableGroup.Listed, override val isEditingFavorites: Boolean) : AppList()
    }
}

private enum class LauncherMode {
    Home,
    AppList
}

sealed class LauncherMenuState {
    data object Closed : LauncherMenuState()
    data class Menu(val item: Launchable) : LauncherMenuState()
    data class Rename(val item: Launchable) : LauncherMenuState()
}

// Favorites missing from [keys] keep their relative order after the ordered ones.
private fun List<Launchable>.sortedInOrderOf(keys: List<String>): List<Launchable> {
    val indexByKey: Map<String, Int> = keys.withIndex().associate { (index, key) -> key to index }
    return sortedBy { indexByKey[it.key] ?: Int.MAX_VALUE }
}

/** The names of the Folder members whose App has at least one visible notification. */
private fun memberNamesWithNotifications(items: List<PopupItem>, notifications: List<NotificationSnapshot>): List<String> {
    val notificationsByItem: Map<String, List<NotificationSnapshot>> =
        PopupContentResolver.itemNotifications(items = items, notifications = notifications)
    return items
        .filter { item -> item.launchable.key in notificationsByItem }
        .map { item -> item.launchable.name }
}

private data class RailNavigation(
    val mode: LauncherMode = LauncherMode.Home,
    val isScrolling: Boolean = false,
    val selected: LaunchableGroup.Listed? = null,
    val homeListState: LazyListState = LazyListState(),
    val appListState: LazyListState? = null
)

private data class PrivateSpaceVisibility(val isLockRequested: Boolean, val isHiddenWhenLocked: Boolean)

// A Hidden Private Space leaves the App List and the Rail while Locked, including an optimistic Lock.
private fun LaunchableGroup.isHiddenPrivateSpace(visibility: PrivateSpaceVisibility): Boolean =
    this is LaunchableGroup.PrivateSpace && (isLocked || visibility.isLockRequested) && visibility.isHiddenWhenLocked

private fun <T : LaunchableGroup> List<T>.withoutHiddenPrivateSpace(visibility: PrivateSpaceVisibility): List<T> =
    filterNot { group -> group.isHiddenPrivateSpace(visibility) }

private fun <T : LaunchableGroup> T.withoutHiddenPrivateSpace(visibility: PrivateSpaceVisibility): T? =
    takeUnless { group -> group.isHiddenPrivateSpace(visibility) }

private fun toUiState(
    navigation: RailNavigation,
    state: LaunchableState,
    favorites: List<Launchable>,
    isEditingFavorites: Boolean,
    privateSpaceVisibility: PrivateSpaceVisibility
): LauncherUiState {
    if (!state.isLoaded) {
        return LauncherUiState.Loading
    }
    val listedGroups: List<LaunchableGroup.Listed> = state.listedGroups
        .withoutHiddenPrivateSpace(privateSpaceVisibility)
        .map { group -> group.withPrivateSpaceLocked(isLockRequested = privateSpaceVisibility.isLockRequested) }
    if (navigation.mode == LauncherMode.Home) {
        return LauncherUiState.Home(
            lazyListState = navigation.homeListState,
            favorites = favorites,
            isEditingFavorites = isEditingFavorites
        )
    }
    val selected: LaunchableGroup.Listed? = navigation.selected
        ?.withoutHiddenPrivateSpace(privateSpaceVisibility)
        ?.withPrivateSpaceLocked(isLockRequested = privateSpaceVisibility.isLockRequested)
    if (navigation.isScrolling) {
        return if (selected == null) {
            LauncherUiState.Loading
        } else {
            LauncherUiState.AppList.Scrolling(group = selected, isEditingFavorites = isEditingFavorites)
        }
    }
    val appListState: LazyListState = navigation.appListState ?: return LauncherUiState.Loading
    return LauncherUiState.AppList.Browsing(
        groups = listedGroups,
        lazyListState = appListState,
        isEditingFavorites = isEditingFavorites
    )
}

// A Lock tap hides the private Apps before the system confirms the Profile is quiet.
private fun LaunchableGroup.Listed.withPrivateSpaceLocked(isLockRequested: Boolean): LaunchableGroup.Listed =
    if (isLockRequested && this is LaunchableGroup.PrivateSpace && !isLocked) {
        LaunchableGroup.PrivateSpace.Locked
    } else {
        this
    }

private enum class MenuRequest {
    Closed,
    Menu,
    Rename
}

class LauncherViewModel(
    listOffsets: ListOffsets,
    private val launcherItemRepo: ILaunchableRepository,
    private val prefsRepo: IPreferencesRepository,
    private val favoritesRepo: IFavoritesRepository,
    private val folderRepo: IFolderRepository,
    private val popupRepo: IPopupRepository,
    private val notificationSource: INotificationSource,
    private val homeEditState: HomeEditState,
    private val profileSource: IProfileSource
) : ViewModel() {

    val listStartOffsetPx: Int = listOffsets.startPx
    val listEndOffsetPx: Int = listOffsets.endPx

    private val launchableState: StateFlow<LaunchableState> = launcherItemRepo.launchableState

    // Set when the Lock action hides the private Apps before the Profile reports itself quiet.
    private val isPrivateSpaceLockRequested: MutableStateFlow<Boolean> = MutableStateFlow(false)

    // A Hidden Private Space leaves the App List and the Rail, including during an optimistic Lock.
    private val privateSpaceVisibility: StateFlow<PrivateSpaceVisibility> = combine(
        profileSource.profiles,
        prefsRepo.settings,
        isPrivateSpaceLockRequested
    ) { profiles, settings, isLockRequested ->
        PrivateSpaceVisibility(
            isLockRequested = isLockRequested,
            isHiddenWhenLocked = profiles.privateSpace?.isHiddenWhenLocked(
                hideWhenLocked = settings.hidePrivateSpaceWhenLocked
            ) == true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = PrivateSpaceVisibility(isLockRequested = false, isHiddenWhenLocked = false)
    )

    /** The Rail labels in index order, shared with the Rail. */
    val railLabels: StateFlow<List<String>> = combine(
        launchableState,
        privateSpaceVisibility
    ) { state, visibility ->
        state.groups.withoutHiddenPrivateSpace(visibility).map { group -> group.label }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = emptyList()
    )

    // Mode, selection, and scroll positions change together, so one Rail event is one emission.
    private val navigation: MutableStateFlow<RailNavigation> = MutableStateFlow(RailNavigation())

    // Keys of the Favorites in their edited order; null outside Edit Favorites mode.
    private val editedFavoriteKeys: MutableStateFlow<List<String>?> = MutableStateFlow(null)

    /** The edited Favorites order; exposed for the ViewModel's behavioural tests. */
    internal val editingFavoriteKeys: List<String>? get() = editedFavoriteKeys.value

    /** Whether the Favorites are being edited, shared with the Done button. */
    val isEditingFavorites: StateFlow<Boolean> = homeEditState.mode
        .map { mode -> mode == HomeEditMode.Favorites }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
            initialValue = false
        )

    // Serializes star toggles and Done so a rapid second tap cannot read a stale favorite state.
    private val favoritesMutex: Mutex = Mutex()

    /** The Favorites in their shown order, including the unsaved order during Edit Favorites. */
    val favorites: StateFlow<List<Launchable>> = combine(
        favoritesRepo.favorites,
        editedFavoriteKeys
    ) { favorites, editedKeys ->
        if (editedKeys == null) favorites else favorites.sortedInOrderOf(editedKeys)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = emptyList()
    )

    val uiState: StateFlow<LauncherUiState> = combine(
        navigation,
        launchableState,
        favorites,
        isEditingFavorites,
        privateSpaceVisibility
    ) { navigation, state, favorites, isEditingFavorites, privateSpaceVisibility ->
        toUiState(
            navigation = navigation,
            state = state,
            favorites = favorites,
            isEditingFavorites = isEditingFavorites,
            privateSpaceVisibility = privateSpaceVisibility
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = LauncherUiState.Loading
    )

    init {
        // Drops the pending Lock once the Profile is quiet (or gone), so a later unlock is shown.
        viewModelScope.launch {
            launchableState
                .map { state ->
                    state.listedGroups.none { group -> group is LaunchableGroup.PrivateSpace && !group.isLocked }
                }
                .distinctUntilChanged()
                .collect { isLocked -> if (isLocked) isPrivateSpaceLockRequested.value = false }
        }
    }

    /** The items of every Folder, keyed by the Folder key. */
    private val folderMembers: Flow<Map<String, List<PopupItem>>> = folderRepo.folders
        .flatMapLatest { folders ->
            if (folders.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(folders.map { folder -> popupRepo.items(ownerKey = folder.key) }) { folderItems ->
                    folders.zip(folderItems).associate { (folder, items) -> folder.key to items }
                }
            }
        }

    /** The names of each Folder's members with notifications, in item order, keyed by the Folder key. */
    val folderSummaries: StateFlow<Map<String, List<String>>> = combine(
        folderMembers,
        notificationSource.notifications
    ) { members, notifications ->
        if (notifications.isEmpty()) {
            emptyMap()
        } else {
            members.mapValues { (_, items) ->
                memberNamesWithNotifications(items = items, notifications = notifications)
            }
                .filterValues { names -> names.isNotEmpty() }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = emptyMap()
    )

    /** The Folder rows' summary lines, keyed by the Folder key. */
    val folderSummaryLines: StateFlow<Map<String, String>> = folderSummaries
        .map { summaries ->
            summaries.mapNotNull { (folderKey, names) ->
                folderSummaryLine(names = names)?.let { line -> folderKey to line }
            }.toMap()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
            initialValue = emptyMap()
        )

    private val requestedMenuState: MutableStateFlow<MenuRequest> = MutableStateFlow(MenuRequest.Closed)
    private val menuItem: MutableStateFlow<Launchable?> = MutableStateFlow(null)

    val menuState: StateFlow<LauncherMenuState> = combine(
        menuItem.combine(launcherItemRepo.launchables) { requestedItem, allItems ->
            allItems.find { it.key == requestedItem?.key }
        },
        requestedMenuState
    ) { item, requestedState ->
        if (item == null) {
            LauncherMenuState.Closed
        } else {
            when (requestedState) {
                MenuRequest.Closed -> LauncherMenuState.Closed
                MenuRequest.Menu -> LauncherMenuState.Menu(item)
                MenuRequest.Rename -> LauncherMenuState.Rename(item)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = LauncherMenuState.Closed
    )

    private val requestedPausedApp: MutableStateFlow<App?> = MutableStateFlow(null)

    /** The paused App whose tap asked to turn work apps on, or null when no prompt is open. */
    val pausedPrompt: StateFlow<App?> = combine(
        requestedPausedApp,
        launcherItemRepo.launchables
    ) { requestedApp, launchables ->
        requestedApp?.let { prompt ->
            launchables.filterIsInstance<App>().firstOrNull { app -> app.key == prompt.key }
        }
    }
        .map { app -> app?.takeIf { current -> current.isPaused } }
        .onEach { app -> if (app == null) requestedPausedApp.value = null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    /**
     * The system's Private Space settings screen, while the space is Unlocked and offers one.
     * Follows Profile changes so the action appears and disappears without a reload.
     */
    val privateSpaceSettingsIntent: StateFlow<IntentSender?> = combine(
        profileSource.profiles,
        isPrivateSpaceLockRequested
    ) { profiles, isLockRequested ->
        profiles.privateSpace
            ?.takeIf { profile -> !profile.isQuiet && !isLockRequested }
            ?.let { profileSource.privateSpaceSettingsIntent() }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = null
    )

    /**
     * Handles a request to launch [launchable]. Returns false and opens the paused prompt
     * instead when [launchable] is a paused App.
     */
    fun requestLaunch(launchable: Launchable): Boolean {
        val app: App = launchable as? App ?: return true
        if (!app.isPaused) {
            return true
        }
        requestedPausedApp.value = app
        return false
    }

    /** Asks the system to leave quiet mode for the Profile with [profileSerial]. */
    fun turnOnProfile(profileSerial: Long) {
        val user: UserHandle = profileSource.userForSerial(serial = profileSerial) ?: return
        requestQuietOff(user = user)
    }

    /** Asks the system to unlock the Private Space; it stays Locked until the system reports it available. */
    fun requestPrivateSpaceUnlock() {
        val user: UserHandle = privateSpaceUser() ?: return
        isPrivateSpaceLockRequested.value = false
        requestQuietOff(user = user)
    }

    /** Hides the private Apps immediately, then asks the system to lock the Private Space. */
    fun lockPrivateSpace() {
        val user: UserHandle = privateSpaceUser() ?: return
        isPrivateSpaceLockRequested.value = true
        requestQuietOn(user = user) { result ->
            if (result == QuietRequestResult.Failed) {
                isPrivateSpaceLockRequested.value = false
            }
        }
    }

    private fun privateSpaceUser(): UserHandle? = profileSource.profiles.value.privateSpace?.user

    /** Asks the system to leave quiet mode for the paused App's Profile. */
    fun turnOnPausedApp() {
        val app: App = requestedPausedApp.value ?: return
        requestedPausedApp.value = null
        requestQuietOff(user = app.profile)
    }

    private fun requestQuietOff(user: UserHandle, onResult: (QuietRequestResult) -> Unit = {}) {
        requestQuiet(user = user, quiet = false, onResult = onResult)
    }

    private fun requestQuietOn(user: UserHandle, onResult: (QuietRequestResult) -> Unit = {}) {
        requestQuiet(user = user, quiet = true, onResult = onResult)
    }

    private fun requestQuiet(user: UserHandle, quiet: Boolean, onResult: (QuietRequestResult) -> Unit = {}) {
        viewModelScope.launch {
            onResult(profileSource.requestQuiet(user = user, quiet = quiet))
        }
    }

    fun dismissPausedPrompt() {
        requestedPausedApp.value = null
    }

    fun onRailPreview(index: Int) = handleRailGroup(index, isScrolling = true)

    fun onRailReleased(index: Int) = handleRailGroup(index, isScrolling = false)

    private fun handleRailGroup(index: Int, isScrolling: Boolean) {
        when (val group = findRailGroup(index) ?: return) {
            LaunchableGroup.Star -> showHomeFromRail()

            is LaunchableGroup.Listed -> {
                val appListState: LazyListState? = if (isScrolling) null else createAppListState(group)
                navigation.update { current ->
                    toAppListNavigation(
                        current = current,
                        group = group,
                        isScrolling = isScrolling,
                        appListState = appListState ?: current.appListState
                    )
                }
            }
        }
    }

    // Reads the shared state before update so a retried update cannot build a second scroll state.
    private fun createAppListState(group: LaunchableGroup.Listed): LazyListState {
        val listedGroups: List<LaunchableGroup.Listed> = railGroups().filterIsInstance<LaunchableGroup.Listed>()
        return LazyListState(
            firstVisibleItemIndex = listedGroups.indexOfFirst { listed -> listed == group } + 1,
            firstVisibleItemScrollOffset = -listStartOffsetPx
        )
    }

    private fun toAppListNavigation(
        current: RailNavigation,
        group: LaunchableGroup.Listed,
        isScrolling: Boolean,
        appListState: LazyListState?
    ): RailNavigation = current.copy(
        mode = LauncherMode.AppList,
        isScrolling = isScrolling,
        selected = group,
        appListState = appListState
    )

    private fun railGroups(): List<LaunchableGroup> = launchableState.value.groups.withoutHiddenPrivateSpace(privateSpaceVisibility.value)

    private fun findRailGroup(index: Int): LaunchableGroup? {
        val groups: List<LaunchableGroup> = railGroups()
        val group: LaunchableGroup? = groups.getOrNull(index)
        if (group == null) {
            Timber.w("Ignoring Rail index %d outside %d groups", index, groups.size)
        }
        return group
    }

    private fun showHomeFromRail() {
        if (navigation.value.mode == LauncherMode.AppList) {
            showHome(resetScroll = false)
        }
    }

    /**
     * Back from the App List returns to Home with Edit Favorites intact. Back on
     * Home leaves Edit Favorites, keeping the pending order, and is a no-op
     * outside Edit Favorites. Home always consumes Back so the launcher activity
     * is never finished.
     */
    fun onBack() {
        if (navigation.value.mode == LauncherMode.AppList) {
            showHome(resetScroll = false)
        } else {
            finishEditingFavorites()
        }
    }

    /**
     * The launcher was re-selected through the HOME category. Home is shown
     * scrolled to the top.
     */
    fun onHomeIntent() {
        showHome(resetScroll = true)
    }

    /**
     * A Launchable was launched. Home is shown scrolled to the top for the
     * next return.
     */
    fun onLaunchableLaunched() {
        showHome(resetScroll = true)
    }

    private fun showHome(resetScroll: Boolean) {
        navigation.update { current ->
            current.copy(
                mode = LauncherMode.Home,
                isScrolling = false,
                selected = null,
                appListState = null,
                homeListState = if (resetScroll) LazyListState() else current.homeListState
            )
        }
    }

    fun openItemMenu(launchable: Launchable) {
        if (homeEditState.mode.value != HomeEditMode.None) {
            return
        }
        menuItem.value = launchable
        requestedMenuState.value = MenuRequest.Menu
    }

    fun openItemRename(launchable: Launchable) {
        requestedMenuState.value = MenuRequest.Rename
    }

    fun closeItemMenu() {
        requestedMenuState.value = MenuRequest.Closed
        menuItem.value = null
    }

    fun setItemName(launchable: Launchable, name: String) {
        viewModelScope.launchLogged(message = "Failed to rename ${launchable.key}") {
            prefsRepo.setItemName(launchable, name)
        }
        openItemMenu(launchable)
    }

    fun toggleFavorite(launchable: Launchable) {
        viewModelScope.launch {
            favoritesMutex.withLock {
                if (favoritesRepo.isFavorite(launchable).first()) {
                    favoritesRepo.remove(launchable)
                    editedFavoriteKeys.update { keys -> keys?.filterNot { it == launchable.key } }
                } else {
                    favoritesRepo.add(launchable)
                    editedFavoriteKeys.update { keys -> keys?.plus(launchable.key) }
                }
            }
        }
    }

    fun editFavorites() {
        if (!homeEditState.tryEnter(HomeEditMode.Favorites)) {
            return
        }
        editedFavoriteKeys.value = favorites.value.map { it.key }
        closeItemMenu()
    }

    fun moveFavorite(fromIndex: Int, toIndex: Int) {
        if (editedFavoriteKeys.value == null) {
            return
        }
        val shownFavorites: List<Launchable> = favorites.value
        if (fromIndex !in shownFavorites.indices || toIndex !in shownFavorites.indices) {
            Timber.w("Ignoring Favorite move from %d to %d outside %d Favorites", fromIndex, toIndex, shownFavorites.size)
            return
        }
        editedFavoriteKeys.value = shownFavorites.map { it.key }.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
    }

    // Edit mode ends after the order is stored so Home never shows the old order.
    fun finishEditingFavorites() {
        if (editedFavoriteKeys.value == null) {
            return
        }
        viewModelScope.launch {
            favoritesMutex.withLock {
                val keys: List<String> = editedFavoriteKeys.value ?: return@withLock
                favoritesRepo.reorder(favoritesRepo.favorites.first().sortedInOrderOf(keys))
                editedFavoriteKeys.value = null
                homeEditState.exit(HomeEditMode.Favorites)
            }
        }
    }
}
