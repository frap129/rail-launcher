package feature.launcher

import android.content.IntentSender
import android.os.UserHandle
import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import core.data.prefs.ISearchSettings
import core.data.prefs.RailSide
import core.data.prefs.ScrubEdges
import core.data.prefs.SearchEngine
import core.data.prefs.Settings
import core.data.prefs.isContactResultsEnabled
import core.data.profiles.IPrivateSpaceLock
import core.data.profiles.IPrivateSpaceStatus
import core.data.profiles.IProfileSource
import core.data.profiles.QuietRequestResult
import core.data.search.IContactSearchSource
import core.data.search.ISearchHistoryRepository
import core.data.search.IShortcutSearchSource
import core.data.search.IWebSuggestionSource
import core.data.search.SearchContact
import core.data.search.SearchHistoryRecord
import core.data.search.SearchResult
import core.data.search.SearchShortcut
import core.data.search.autoLaunchResult
import core.data.search.searchResultRows
import core.data.search.searchSuggestions
import core.data.search.toSearchResults
import core.data.shortcuts.IStandaloneShortcutRepository
import core.data.shortcuts.ShortcutLaunchable
import core.data.time.IClockSource
import core.ui.GroupMark
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import core.ui.search.SearchContactsUiEvent
import core.ui.search.SearchContactsUiEvents
import core.ui.toGroupMark
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import core.util.launchLogged
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/** The Search preferences the Search options sheet shows and edits. */
data class SearchOptions(
    val openSearchOnSwipeUp: Boolean,
    val searchSuggestions: Boolean,
    val searchWebSuggestions: Boolean,
    val searchContacts: Boolean,
    val searchCalculatorRow: Boolean,
    val searchEngine: SearchEngine
)

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

    /**
     * The Search state: the current query, its rows, the Top result, whether the rows are Search
     * suggestions, where they are anchored, the result Auto-launch may open and the Search options.
     */
    data class Search(
        val query: String,
        val results: List<SearchResult>,
        val topResult: SearchResult?,
        val isShowingSuggestions: Boolean,
        val isBottomAnchored: Boolean,
        val autoLaunchResult: SearchResult.LaunchableResult?,
        val options: SearchOptions
    ) : LauncherUiState() {
        /** The Top result when these results belong to [query], or null when they belong to an earlier query. */
        fun topResultFor(query: String): SearchResult? = topResult.takeIf { this.query == query }
    }
}

private enum class LauncherMode {
    Home,
    AppList,
    Search
}

sealed class LauncherMenuState {
    data object Closed : LauncherMenuState()
    data class Menu(val item: Launchable, val isSearchSuggestion: Boolean, val isStandaloneShortcut: Boolean) : LauncherMenuState()
    data class Rename(val item: Launchable) : LauncherMenuState()
}

// Favorites missing from [keys] keep their relative order after the ordered ones.
private fun List<Launchable>.sortedInOrderOf(keys: List<String>): List<Launchable> {
    val indexByKey: Map<String, Int> = keys.withIndex().associate { (index, key) -> key to index }
    return sortedBy { indexByKey[it.key] ?: Int.MAX_VALUE }
}

private fun resolvePausedPrompt(requested: Launchable?, state: LaunchableState): Launchable? = requested?.let { launchable ->
    state.launchables.firstOrNull { current -> current.key == launchable.key }?.takeIf { current -> current.isPaused }
}

/** The names of the Folder members whose App has at least one visible notification. */
private fun memberNamesWithNotifications(items: List<PopupItem>, notifications: List<NotificationSnapshot>): List<String> {
    val notificationsByItem: Map<String, List<NotificationSnapshot>> =
        PopupContentResolver.itemNotifications(items = items, notifications = notifications)
    return items
        .filter { item -> item.launchable.key in notificationsByItem }
        .map { item -> item.launchable.name }
}

private data class SearchState(val origin: LauncherMode, val query: String = "", val isForwardTyping: Boolean = false)

/** The Shortcuts Search may match, and whether their source has emitted them yet. */
private data class ShortcutSearchState(val shortcuts: List<SearchShortcut> = emptyList(), val isLoaded: Boolean = false)

/** The contacts Search may match, whether their source has emitted them yet and whether Rail may read them. */
private data class ContactSearchState(
    val contacts: List<SearchContact> = emptyList(),
    val isLoaded: Boolean = false,
    val hasPermission: Boolean = false
)

/**
 * The Search history, clock, Shortcuts, contacts and Web suggestions a Search projection reads,
 * with the setting that shows Search suggestions.
 */
private data class SearchData(
    val history: List<SearchHistoryRecord>,
    val nowEpochMs: Long,
    val settings: Settings,
    val shortcuts: List<SearchShortcut>,
    val areShortcutsLoaded: Boolean,
    val contacts: List<SearchContact>,
    val areContactsLoaded: Boolean,
    val hasContactsPermission: Boolean,
    val webSuggestions: WebSuggestions
) {
    /** Typed results wait for both sources, so no row is ever shown before all of them are known. */
    val areSearchSourcesLoaded: Boolean get() = areShortcutsLoaded && areContactsLoaded
}

/** The Web suggestions fetched for [query], so a new query never shows an older query's rows. */
private data class WebSuggestions(val query: String, val suggestions: List<String>) {
    /** These suggestions when they belong to [query], or none when they belong to an older query. */
    fun forQuery(query: String): List<String> = if (this.query == query) suggestions else emptyList()
}

/** Whether this Search row may be the Top result: Contact, Web suggestion and Play Store rows never are. */
private fun SearchResult.canBeTopResult(): Boolean =
    this !is SearchResult.ContactRow && this !is SearchResult.WebSuggestionRow && this !is SearchResult.PlayStoreRow

private data class RailNavigation(
    val mode: LauncherMode = LauncherMode.Home,
    val isScrolling: Boolean = false,
    val selected: LaunchableGroup.Listed? = null,
    val homeListState: LazyListState = LazyListState(),
    val appListState: LazyListState? = null,
    val search: SearchState? = null
)

// The selected Private Space follows the latest Locked or Unlocked container, or null while Hidden.
private fun LaunchableGroup.Listed.refresh(groups: List<LaunchableGroup.Listed>): LaunchableGroup.Listed? =
    if (this is LaunchableGroup.PrivateSpace) {
        groups.firstOrNull { group -> group is LaunchableGroup.PrivateSpace }
    } else {
        this
    }

private fun Settings.toSearchOptions(hasContactsPermission: Boolean): SearchOptions = SearchOptions(
    openSearchOnSwipeUp = openSearchOnSwipeUp,
    searchSuggestions = searchSuggestions,
    searchWebSuggestions = searchWebSuggestions,
    searchContacts = isContactResultsEnabled(hasContactsPermission = hasContactsPermission),
    searchCalculatorRow = searchCalculatorRow,
    searchEngine = searchEngine
)

private fun toSearchUiState(search: SearchState, state: LaunchableState, searchData: SearchData): LauncherUiState.Search {
    val isShowingSuggestions: Boolean = search.query.isEmpty() && searchData.settings.searchSuggestions
    // Typed results wait for the first emission of every source, so no row is inserted above one
    // already shown and Go never opens a target from a list that is still changing.
    val results: List<SearchResult> = when {
        isShowingSuggestions -> state.searchSuggestions(
            history = searchData.history,
            nowEpochMs = searchData.nowEpochMs
        ).toSearchResults()

        search.query.isEmpty() || !searchData.areSearchSourcesLoaded -> emptyList()

        else -> {
            val launchCounts: Map<String, Int> = searchData.history.associate { record -> record.key to record.launchCount }
            state.searchResultRows(
                query = search.query,
                launchCounts = launchCounts,
                shortcuts = searchData.shortcuts,
                contacts = searchData.contacts,
                webSuggestions = searchData.webSuggestions.forQuery(query = search.query),
                locale = Locale.getDefault(),
                settings = searchData.settings
            )
        }
    }
    return LauncherUiState.Search(
        query = search.query,
        results = results,
        topResult = if (isShowingSuggestions) null else results.firstOrNull { result -> result.canBeTopResult() },
        isShowingSuggestions = isShowingSuggestions,
        isBottomAnchored = searchData.settings.searchBottomAnchoredResults,
        autoLaunchResult = if (isShowingSuggestions || !search.isForwardTyping) {
            null
        } else {
            results.autoLaunchResult(settings = searchData.settings)
        },
        options = searchData.settings.toSearchOptions(hasContactsPermission = searchData.hasContactsPermission)
    )
}

private fun toUiState(
    navigation: RailNavigation,
    state: LaunchableState,
    favorites: List<Launchable>,
    isEditingFavorites: Boolean,
    searchData: SearchData
): LauncherUiState {
    if (!state.isLoaded) {
        return LauncherUiState.Loading
    }
    val listedGroups: List<LaunchableGroup.Listed> = state.listedGroups
    navigation.search?.let { search ->
        return toSearchUiState(search = search, state = state, searchData = searchData)
    }
    if (navigation.mode == LauncherMode.Home) {
        return LauncherUiState.Home(
            lazyListState = navigation.homeListState,
            favorites = favorites,
            isEditingFavorites = isEditingFavorites
        )
    }
    val selected: LaunchableGroup.Listed? = navigation.selected?.refresh(groups = listedGroups)
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

private enum class MenuRequest {
    Closed,
    Menu,
    Rename
}

private data class MenuItemRequest(val launchable: Launchable, val isSearchSuggestion: Boolean)

class LauncherViewModel(
    listOffsets: ListOffsets,
    private val launcherItemRepo: ILaunchableRepository,
    private val prefsRepo: IPreferencesRepository,
    private val favoritesRepo: IFavoritesRepository,
    private val folderRepo: IFolderRepository,
    private val popupRepo: IPopupRepository,
    private val notificationSource: INotificationSource,
    private val homeEditState: HomeEditState,
    private val profileSource: IProfileSource,
    private val privateSpaceStatus: IPrivateSpaceStatus,
    private val privateSpaceLock: IPrivateSpaceLock,
    private val standaloneShortcutRepository: IStandaloneShortcutRepository,
    private val searchHistoryRepository: ISearchHistoryRepository,
    private val clockSource: IClockSource,
    private val shortcutSearchSource: IShortcutSearchSource,
    private val contactSearchSource: IContactSearchSource,
    private val searchSettings: ISearchSettings,
    private val webSuggestionSource: IWebSuggestionSource
) : ViewModel() {
    private val searchContactsUiEvents: SearchContactsUiEvents = SearchContactsUiEvents()

    /** The permission prompts and denials the Search options sheet must show. */
    val events: Flow<SearchContactsUiEvent> = searchContactsUiEvents.events

    val listStartOffsetPx: Int = listOffsets.startPx
    val listEndOffsetPx: Int = listOffsets.endPx

    private val launchableState: StateFlow<LaunchableState> = launcherItemRepo.launchableState

    /** The Rail items in index order, shared with the Rail. */
    val railItems: StateFlow<List<GroupMark>> = launchableState
        .map { state -> state.groups.map { group -> group.toGroupMark() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
            initialValue = emptyList()
        )

    // Kept current so gesture callbacks can read saved settings without a UI collector.
    private val currentSettings: StateFlow<Settings> = prefsRepo.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = Settings()
    )

    /** The screen edges that scrub the Rail. */
    val scrubEdges: StateFlow<ScrubEdges> = currentSettings
        .map { settings -> settings.scrubEdges }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
            initialValue = ScrubEdges.DEFAULT
        )

    /** The screen side where the Rail is drawn. */
    val railSide: StateFlow<RailSide> = currentSettings
        .map { settings -> settings.railSide }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
            initialValue = RailSide.DEFAULT
        )

    // Mode, selection, and scroll positions change together, so one Rail event is one emission.
    private val navigation: MutableStateFlow<RailNavigation> = MutableStateFlow(RailNavigation())

    // Contacts load with the launcher and stay warm across Search sessions, like the Shortcuts, so
    // a keystroke only re-reads state.
    private val contactSearchState: StateFlow<ContactSearchState> = combine(
        contactSearchSource.contacts,
        searchSettings.hasContactsPermission
    ) { contacts, hasPermission ->
        ContactSearchState(contacts = contacts, isLoaded = true, hasPermission = hasPermission)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ContactSearchState()
    )

    // Shortcuts load with the launcher and stay warm across Search sessions, so a keystroke only re-reads state.
    private val shortcutSearchState: StateFlow<ShortcutSearchState> = shortcutSearchSource.shortcuts
        .map { shortcuts -> ShortcutSearchState(shortcuts = shortcuts, isLoaded = true) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ShortcutSearchState()
        )

    // Web suggestions follow the live query; flatMapLatest cancels the previous query's request,
    // and the source debounces the new one. A blank query has none.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val webSuggestions: Flow<WebSuggestions> = navigation
        .map { navigation -> navigation.search?.query.orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(WebSuggestions(query = query, suggestions = emptyList()))
            } else {
                webSuggestionSource.suggestions(query = query, locale = Locale.getDefault())
                    .map { suggestions -> WebSuggestions(query = query, suggestions = suggestions) }
            }
        }

    // Search reads its history, Shortcuts and contacts from state and re-derives suggestions as the clock ticks, never on a keystroke.
    private val searchData: Flow<SearchData> = combine(
        searchHistoryRepository.history,
        clockSource.readings,
        currentSettings,
        shortcutSearchState,
        contactSearchState
    ) { history, reading, settings, shortcutState, contactState ->
        SearchData(
            history = history,
            nowEpochMs = reading.instant.toEpochMilli(),
            settings = settings,
            shortcuts = shortcutState.shortcuts,
            areShortcutsLoaded = shortcutState.isLoaded,
            contacts = contactState.contacts,
            areContactsLoaded = contactState.isLoaded,
            hasContactsPermission = contactState.hasPermission,
            webSuggestions = WebSuggestions(query = "", suggestions = emptyList())
        )
    }

    private val searchDataWithWebSuggestions: Flow<SearchData> = combine(searchData, webSuggestions) { data, suggestions ->
        data.copy(webSuggestions = suggestions)
    }

    // Auto-launch derives from the Search state the screen shows, so the gates that build the rows
    // stay in toSearchUiState. The screen asks shouldAutoLaunch before opening a result; the guard
    // remembers the last result until the Search session or query changes, so a re-collection after
    // a recreation cannot open it twice.
    private var lastAutoLaunchKey: String? = null

    /** Whether Auto-launch should open [result] once for the current Search results state. */
    fun shouldAutoLaunch(result: SearchResult.LaunchableResult): Boolean {
        if (result.launchable.key == lastAutoLaunchKey) {
            return false
        }
        lastAutoLaunchKey = result.launchable.key
        return true
    }

    private fun clearAutoLaunch() {
        lastAutoLaunchKey = null
    }

    private val isShowingAppList: Boolean get() = navigation.value.mode == LauncherMode.AppList
    private val isShowingSearch: Boolean get() = navigation.value.mode == LauncherMode.Search

    // Keys of the Favorites in their edited order; null outside Edit Favorites mode.
    private val editedFavoriteKeys: MutableStateFlow<List<String>?> = MutableStateFlow(null)

    /** The edited Favorites order; exposed for the ViewModel's behavioural tests. */
    internal val editingFavoriteKeys: List<String>? get() = editedFavoriteKeys.value

    /** The active Home edit mode; any mode except Edit Favorites disables the Rail's Scrub edges. */
    val homeEditMode: StateFlow<HomeEditMode> = homeEditState.mode

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
        searchDataWithWebSuggestions
    ) { navigation, state, favorites, isEditingFavorites, searchData ->
        toUiState(
            navigation = navigation,
            state = state,
            favorites = favorites,
            isEditingFavorites = isEditingFavorites,
            searchData = searchData
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = LauncherUiState.Loading
    )

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
    private val requestedMenuItem: MutableStateFlow<MenuItemRequest?> = MutableStateFlow(null)

    private val resolvedMenuItem: Flow<LauncherMenuState.Menu?> = combine(
        requestedMenuItem,
        launcherItemRepo.launchables,
        shortcutSearchState
    ) { requestedItem, allItems, shortcutState ->
        requestedItem?.let { request ->
            val launchable: Launchable? = allItems.find { item -> item.key == request.launchable.key }
            val resolvedItem: Launchable = launchable
                ?: shortcutState.shortcuts
                    .find { searchShortcut -> searchShortcut.shortcut.key == request.launchable.key }
                    ?.shortcut
                ?: return@combine null
            LauncherMenuState.Menu(
                item = resolvedItem,
                isSearchSuggestion = request.isSearchSuggestion,
                isStandaloneShortcut = launchable is ShortcutLaunchable
            )
        }
    }

    val menuState: StateFlow<LauncherMenuState> = combine(
        resolvedMenuItem,
        requestedMenuState
    ) { menu, requestedState ->
        when (requestedState) {
            MenuRequest.Closed -> LauncherMenuState.Closed
            MenuRequest.Menu -> menu ?: LauncherMenuState.Closed
            MenuRequest.Rename -> menu?.let { currentMenu -> LauncherMenuState.Rename(currentMenu.item) } ?: LauncherMenuState.Closed
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = LauncherMenuState.Closed
    )

    private val requestedPausedLaunchable: MutableStateFlow<Launchable?> = MutableStateFlow(null)

    /** The paused Launchable whose tap asked to turn work apps on, or null when no prompt is open. */
    val pausedPrompt: StateFlow<Launchable?> = combine(
        requestedPausedLaunchable,
        launchableState
    ) { requested, state ->
        resolvePausedPrompt(requested = requested, state = state)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = null
    )

    /**
     * The system's Private Space settings screen, while the space is Unlocked and offers one.
     * Follows the Private Space status so the action appears and disappears without a reload.
     */
    val privateSpaceSettingsIntent: StateFlow<IntentSender?> = privateSpaceStatus.status
        .map { status -> status.isUnlocked }
        .distinctUntilChanged()
        .map { isUnlocked -> if (isUnlocked) profileSource.privateSpaceSettingsIntent() else null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
            initialValue = null
        )

    // Transitions the request when its Launchable stops being paused, even while the prompt is unobserved.
    init {
        viewModelScope.launch {
            launchableState.collect { state ->
                requestedPausedLaunchable.update { requested -> resolvePausedPrompt(requested = requested, state = state) }
            }
        }
    }

    /**
     * Handles a request to launch [launchable]. Returns false when [launchable] belongs to a
     * Paused Profile; a Paused one opens the paused prompt instead.
     */
    fun requestLaunch(launchable: Launchable): Boolean {
        if (!launchable.isPaused) {
            return true
        }
        requestedPausedLaunchable.value = resolvePausedPrompt(requested = launchable, state = launchableState.value)
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
        privateSpaceLock.clearLockRequest()
        requestQuietOff(user = user)
    }

    /** Hides the private Apps immediately, then asks the system to lock the Private Space. */
    fun lockPrivateSpace() {
        val user: UserHandle = privateSpaceUser() ?: return
        privateSpaceLock.requestLock()
        requestQuietOn(user = user) { result ->
            if (result == QuietRequestResult.Failed) {
                privateSpaceLock.clearLockRequest()
            }
        }
    }

    private fun privateSpaceUser(): UserHandle? = privateSpaceStatus.currentStatus().profile?.user

    /** Asks the system to leave quiet mode for the paused Launchable's Profile. */
    fun turnOnPausedLaunchable() {
        val user: UserHandle = requestedPausedLaunchable.value?.profile ?: return
        requestedPausedLaunchable.value = null
        requestQuietOff(user = user)
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
        requestedPausedLaunchable.value = null
    }

    /** Rechecks the Contacts permission so Contact rows disappear when it is gone, without prompting. */
    fun refreshContactsPermission() = searchSettings.refreshContactsPermission()

    /** Persists whether swiping up on Home opens Search. */
    fun setOpenSearchOnSwipeUp(enabled: Boolean) = searchSettings.setOpenSearchOnSwipeUp(enabled)

    /** Persists whether Search shows Search suggestions before typing. */
    fun setSearchSuggestions(enabled: Boolean) = searchSettings.setSearchSuggestions(enabled)

    /** Persists whether Search fetches Web suggestions from the Search engine while typing. */
    fun setSearchWebSuggestions(enabled: Boolean) = searchSettings.setSearchWebSuggestions(enabled)

    /** Persists whether Search shows Contact rows, asking for the Contacts permission first when it turns on. */
    fun setSearchContacts(enabled: Boolean) {
        searchContactsUiEvents.report(searchSettings.setSearchContacts(enabled))
    }

    /** Persists the Contact results switch from the permission result and reports a permanent denial. */
    fun onContactsPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        searchContactsUiEvents.report(searchSettings.onContactsPermissionResult(granted = granted, canAskAgain = canAskAgain))
    }

    /** Persists whether Search shows the Calculator row. */
    fun setSearchCalculatorRow(enabled: Boolean) = searchSettings.setSearchCalculatorRow(enabled)

    /**
     * Opens Search with [startingQuery] unless a Home edit mode blocks it; returns whether Search
     * opened.
     */
    fun openSearch(startingQuery: String = ""): Boolean {
        if (homeEditState.mode.value != HomeEditMode.None) {
            return false
        }
        val wasShowingSearch: Boolean = isShowingSearch
        navigation.update { current ->
            if (current.mode == LauncherMode.Search) {
                current
            } else {
                current.copy(
                    mode = LauncherMode.Search,
                    isScrolling = false,
                    selected = null,
                    search = SearchState(origin = current.mode, query = startingQuery, isForwardTyping = startingQuery.isNotEmpty())
                )
            }
        }
        if (!wasShowingSearch) {
            clearAutoLaunch()
        }
        return true
    }

    /**
     * Whether a Typed character can open Search or append to an open Search query. Home accepts
     * one outside its edit modes and while no Pop-up is open, and Search always accepts one.
     */
    fun canAcceptTypedCharacter(isPopupOpen: Boolean): Boolean = navigation.value.mode == LauncherMode.Search ||
        (navigation.value.mode == LauncherMode.Home && homeEditState.mode.value == HomeEditMode.None && !isPopupOpen)

    /** Appends a Typed character to an open Search query, or opens Search from Home with it. */
    fun onTypedCharacter(character: String, isPopupOpen: Boolean): Boolean {
        if (!canAcceptTypedCharacter(isPopupOpen = isPopupOpen)) {
            return false
        }
        val search: SearchState? = navigation.value.search
        if (search != null) {
            setSearchQuery(query = search.query + character)
            return true
        }
        return openSearch(startingQuery = character)
    }

    /** Updates the Search query; the results follow synchronously from the Launchable State. */
    fun setSearchQuery(query: String) {
        val search: SearchState = navigation.value.search ?: return
        if (search.query == query) {
            return
        }
        clearAutoLaunch()
        val isForwardTyping: Boolean = query.length > search.query.length && query.startsWith(search.query)
        navigation.update { current ->
            current.search?.let { currentSearch ->
                current.copy(search = currentSearch.copy(query = query, isForwardTyping = isForwardTyping))
            } ?: current
        }
    }

    /** Whether Search is the shown launcher state. */
    fun isSearchOpen(): Boolean = isShowingSearch

    /**
     * The Top result that the IME Go action opens or copies for [query], or null while Search
     * suggestions are shown, the shown results belong to an earlier query, or no result matches.
     */
    fun onSearchGo(query: String): SearchResult? = (uiState.value as? LauncherUiState.Search)?.topResultFor(query)

    /** A Launchable was launched, so Search records it and then closes and resets. */
    fun onLaunched(launchable: Launchable) {
        if (isShowingSearch) {
            viewModelScope.launchLogged(message = "Failed to record a Search launch") {
                searchHistoryRepository.recordLaunch(key = launchable.key)
            }
        }
        closeSearch()
    }

    /** Removes [launchable] from Search suggestions; its launches keep counting toward ranking. */
    fun removeSearchSuggestion(launchable: Launchable) {
        closeItemMenu()
        viewModelScope.launchLogged(message = "Failed to remove a Search suggestion") {
            searchHistoryRepository.removeFromSuggestions(key = launchable.key)
        }
    }

    /** Closes Search, returning to the state it was opened from and resetting the query. */
    fun closeSearch() {
        clearAutoLaunch()
        navigation.update { current ->
            val search: SearchState = current.search ?: return@update current
            current.copy(
                mode = search.origin,
                isScrolling = false,
                selected = null,
                search = null
            )
        }
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
        val listedGroups: List<LaunchableGroup.Listed> = launchableState.value.groups.filterIsInstance<LaunchableGroup.Listed>()
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
        appListState = appListState,
        search = null
    )

    private fun findRailGroup(index: Int): LaunchableGroup? {
        val groups: List<LaunchableGroup> = launchableState.value.groups
        val group: LaunchableGroup? = groups.getOrNull(index)
        if (group == null) {
            Timber.w("Ignoring Rail index %d outside %d groups", index, groups.size)
        }
        return group
    }

    private fun showHomeFromRail() {
        if (isShowingAppList || isShowingSearch) {
            showHome(resetScroll = false)
        }
    }

    /**
     * Back from Search clears the query, then closes Search. Back from the App
     * List returns to Home with Edit Favorites intact. Back on Home leaves Edit
     * Favorites, keeping the pending order, and is a no-op outside Edit
     * Favorites. Home always consumes Back so the launcher activity is never
     * finished.
     */
    fun onBack() {
        when {
            isShowingSearch -> {
                if (navigation.value.search?.query?.isNotEmpty() == true) {
                    setSearchQuery("")
                } else {
                    closeSearch()
                }
            }

            isShowingAppList -> showHome(resetScroll = false)

            else -> finishEditingFavorites()
        }
    }

    /** Opens Search on a swipe up on Home, when the swipe-up entry is on. */
    fun onHomeSwipeUp() {
        if (currentSettings.value.openSearchOnSwipeUp) {
            openSearch()
        }
    }

    /**
     * The launcher was re-selected through the HOME category. A Home press on Home opens Search
     * when its entry is on; any other press shows Home scrolled to the top.
     */
    fun onHomeIntent(wasResumed: Boolean) {
        if (isHomePressOnHome(wasResumed = wasResumed) && openSearch()) {
            return
        }
        showHome(resetScroll = true)
    }

    private fun isHomePressOnHome(wasResumed: Boolean): Boolean = wasResumed &&
        navigation.value.mode == LauncherMode.Home &&
        currentSettings.value.openSearchOnHomePress

    private fun showHome(resetScroll: Boolean) {
        clearAutoLaunch()
        navigation.update { current ->
            current.copy(
                mode = LauncherMode.Home,
                isScrolling = false,
                selected = null,
                appListState = null,
                search = null,
                homeListState = if (resetScroll) LazyListState() else current.homeListState
            )
        }
    }

    fun openItemMenu(launchable: Launchable) {
        if (homeEditState.mode.value != HomeEditMode.None) {
            return
        }
        val search: LauncherUiState.Search? = uiState.value as? LauncherUiState.Search
        val isSearchSuggestion: Boolean = search?.isShowingSuggestions == true &&
            search.results.any { result -> result.key == launchable.key }
        requestedMenuItem.value = MenuItemRequest(launchable = launchable, isSearchSuggestion = isSearchSuggestion)
        requestedMenuState.value = MenuRequest.Menu
    }

    fun openItemRename(launchable: Launchable) {
        requestedMenuState.value = MenuRequest.Rename
    }

    fun closeItemMenu() {
        requestedMenuState.value = MenuRequest.Closed
        requestedMenuItem.value = null
    }

    fun setItemName(launchable: Launchable, name: String) {
        viewModelScope.launchLogged(message = "Failed to rename a Launchable") {
            prefsRepo.setItemName(launchable, name)
        }
        openItemMenu(launchable)
    }

    fun removeShortcut(shortcut: ShortcutLaunchable) {
        closeItemMenu()
        viewModelScope.launchLogged(message = "Failed to remove a Standalone shortcut") {
            standaloneShortcutRepository.remove(keys = listOf(shortcut.key))
        }
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
        closeSearch()
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
