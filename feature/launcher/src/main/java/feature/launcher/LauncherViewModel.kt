package feature.launcher

import android.content.IntentSender
import android.os.UserHandle
import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.apps.model.App
import core.data.apps.model.isPrivateApp
import core.data.favorites.FavoritesState
import core.data.favorites.IFavoritesRepository
import core.data.folders.Folder
import core.data.folders.IFolderRepository
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launchables.model.LaunchableGroup
import core.data.launchables.model.RecentlyInstalledSection
import core.data.launches.ILaunchCountRepository
import core.data.notifications.INotificationShade
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
import core.data.profiles.PrivateSpaceStatus
import core.data.profiles.ProfileInfo
import core.data.profiles.QuietRequestResult
import core.data.search.ISearchHistoryRepository
import core.data.search.ISearchProjectionSource
import core.data.search.IShortcutSearchSource
import core.data.search.IStoppedSuggestionRepository
import core.data.search.SearchProjection
import core.data.search.SearchResult
import core.data.search.SearchShortcut
import core.data.search.SearchSuggestionSources
import core.data.search.SearchTypedSources
import core.data.search.autoLaunchResult
import core.data.search.homeSuggestionExcludedKeys
import core.data.search.homeSuggestions
import core.data.search.searchResultRows
import core.data.search.searchSuggestions
import core.data.search.suggestibleLaunchableFor
import core.data.search.toSearchResults
import core.data.shortcuts.IStandaloneShortcutRepository
import core.data.shortcuts.ShortcutLaunchable
import core.data.time.IClockSource
import core.ui.GroupMark
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import core.ui.model.data.LauncherSignal
import core.ui.search.SearchContactsUiEvent
import core.ui.search.SearchContactsUiEvents
import core.ui.toGroupMark
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import core.util.launchLogged
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
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

    data class Home(
        val lazyListState: LazyListState,
        val favorites: List<Launchable>,
        val isFavoritesLoaded: Boolean,
        val suggestions: List<Launchable>,
        val isSwipeUpEnabled: Boolean,
        val isSwipeDownEnabled: Boolean,
        val isEditingFavorites: Boolean = false
    ) : LauncherUiState()

    sealed class AppList : LauncherUiState() {
        abstract val isEditingFavorites: Boolean

        data class Browsing(
            val groups: List<LaunchableGroup.Listed>,
            val recentlyInstalled: RecentlyInstalledSection?,
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

/** One actionable row an OpenIndex signal can invoke in the shown Home or App List order. */
sealed interface LauncherRowTarget {
    data class OpenLaunchable(val launchable: Launchable) : LauncherRowTarget

    data object UnlockPrivateSpace : LauncherRowTarget

    data object LockPrivateSpace : LauncherRowTarget

    data class OpenPrivateSpaceSettings(val intentSender: IntentSender) : LauncherRowTarget
}

private enum class LauncherMode {
    Home,
    AppList,
    Search
}

/** The kind of suggestion the row behind a menu is, which decides the suggestion action the menu offers. */
enum class MenuSuggestion {
    None,
    Search,
    Home
}

sealed class LauncherMenuState {
    data object Closed : LauncherMenuState()

    data class Menu(
        val item: Launchable,
        val suggestion: MenuSuggestion,
        val isStandaloneShortcut: Boolean,
        val canUninstall: Boolean,
        val isHidden: Boolean,
        /**
         * The Profile whose Store page Install in Private Space opens, or null when the menu hides
         * the action: no Unlocked Private Space, an App that already belongs to one, or a System App.
         */
        val installInPrivateSpaceProfile: ProfileInfo?
    ) : LauncherMenuState() {
        /** Whether the menu offers hiding [item] from the App List: Apps and Standalone shortcuts that are not Hidden apps yet. */
        val canHide: Boolean get() = !isHidden && (isStandaloneShortcut || item is App)
    }

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

/** The Favorites and the Home suggestions Home shows after them. */
private data class HomeContent(val favoritesState: FavoritesState, val suggestions: List<Launchable>)

private data class SearchState(val origin: LauncherMode, val query: String = "", val isForwardTyping: Boolean = false)

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

/**
 * The groups the Rail shows: every group once the App List is ready, otherwise only the Star,
 * so a Rail action can only reach a group the Rail shows.
 */
private fun LaunchableState.railGroups(includeHidden: Boolean): List<LaunchableGroup> =
    if (isAppListReady) groupsFor(includeHidden = includeHidden) else listOf(LaunchableGroup.Star)

private fun Settings.toSearchOptions(hasContactsPermission: Boolean): SearchOptions = SearchOptions(
    openSearchOnSwipeUp = openSearchOnSwipeUp,
    searchSuggestions = searchSuggestions,
    searchWebSuggestions = searchWebSuggestions,
    searchContacts = isContactResultsEnabled(hasContactsPermission = hasContactsPermission),
    searchCalculatorRow = searchCalculatorRow,
    searchEngine = searchEngine
)

private fun toSearchUiState(
    search: SearchState,
    state: LaunchableState,
    settings: Settings,
    projection: SearchProjection
): LauncherUiState.Search {
    val isShowingSuggestions: Boolean = search.query.isEmpty() && settings.searchSuggestions
    val suggestions: SearchSuggestionSources? = projection.suggestions
    val typed: SearchTypedSources? = projection.typed
    // Typed results wait for the first emission of every source, so no row is inserted above one
    // already shown and Go never opens a target from a list that is still changing.
    val results: List<SearchResult> = when {
        isShowingSuggestions -> suggestions?.let { sources ->
            state.searchSuggestions(
                history = sources.history,
                nowEpochMs = sources.reading.instant.toEpochMilli()
            ).toSearchResults()
        }.orEmpty()

        search.query.isEmpty() || suggestions == null || typed == null -> emptyList()

        else -> {
            val launchCounts: Map<String, Int> = suggestions.history.associate { record -> record.key to record.launchCount }
            state.searchResultRows(
                query = search.query,
                launchCounts = launchCounts,
                shortcuts = typed.shortcuts,
                contacts = typed.contacts,
                webSuggestions = projection.webSuggestions.forQuery(query = search.query),
                locale = Locale.getDefault(),
                settings = settings
            )
        }
    }
    return LauncherUiState.Search(
        query = search.query,
        results = results,
        topResult = if (isShowingSuggestions) null else results.firstOrNull { result -> result.canBeTopResult() },
        isShowingSuggestions = isShowingSuggestions,
        isBottomAnchored = settings.searchBottomAnchoredResults,
        autoLaunchResult = if (isShowingSuggestions || !search.isForwardTyping) {
            null
        } else {
            results.autoLaunchResult(settings = settings)
        },
        options = settings.toSearchOptions(hasContactsPermission = typed?.hasContactsPermission == true)
    )
}

private fun toLauncherUiState(
    navigation: RailNavigation,
    state: LaunchableState,
    homeContent: HomeContent,
    isEditingFavorites: Boolean,
    settings: Settings
): LauncherUiState {
    if (navigation.mode == LauncherMode.Home) {
        return LauncherUiState.Home(
            lazyListState = navigation.homeListState,
            favorites = homeContent.favoritesState.favorites,
            isFavoritesLoaded = homeContent.favoritesState.isLoaded,
            suggestions = if (isEditingFavorites || !settings.suggestOftenUsedApps) emptyList() else homeContent.suggestions,
            isSwipeUpEnabled = settings.openSearchOnSwipeUp,
            isSwipeDownEnabled = settings.expandNotificationsOnSwipeDown,
            isEditingFavorites = isEditingFavorites
        )
    }
    if (!state.isAppListReady) {
        return LauncherUiState.Loading
    }
    val listedGroups: List<LaunchableGroup.Listed> = state.listedGroupsFor(includeHidden = isEditingFavorites)
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
        recentlyInstalled = state.recentlyInstalled,
        lazyListState = appListState,
        isEditingFavorites = isEditingFavorites
    )
}

/** The actionable rows in one group; an Unlocked Private Space container has no tap action. */
private fun LaunchableGroup.Listed.rowTargets(privateSpaceSettingsIntent: IntentSender?): List<LauncherRowTarget> = buildList {
    val group: LaunchableGroup.Listed = this@rowTargets
    if (group is LaunchableGroup.PrivateSpace && group.isLocked) {
        add(LauncherRowTarget.UnlockPrivateSpace)
    }
    group.items.forEach { launchable -> add(LauncherRowTarget.OpenLaunchable(launchable)) }
    if (group is LaunchableGroup.PrivateSpace && !group.isLocked) {
        add(LauncherRowTarget.LockPrivateSpace)
        privateSpaceSettingsIntent?.let { intentSender ->
            add(LauncherRowTarget.OpenPrivateSpaceSettings(intentSender))
        }
    }
}

private fun LauncherUiState.AppList.Browsing.rowTargets(privateSpaceSettingsIntent: IntentSender?): List<LauncherRowTarget> =
    groups.flatMap { group -> group.rowTargets(privateSpaceSettingsIntent) } +
        recentlyInstalled?.apps.orEmpty().map { app -> LauncherRowTarget.OpenLaunchable(app) }

/** The App List scroll state that shows the group at [groupIndex] where the Rail scrub showed it, past the top spacer. */
internal fun createReleasedAppListState(groupIndex: Int, listStartOffsetPx: Int): LazyListState = LazyListState(
    firstVisibleItemIndex = groupIndex + 1,
    firstVisibleItemScrollOffset = -listStartOffsetPx
)

private enum class MenuRequest {
    Closed,
    Menu,
    Rename
}

private data class MenuItemRequest(val launchable: Launchable, val suggestion: MenuSuggestion)

/** A menu request, the Launchable State it resolves against, and that state's Private Space status. */
private data class MenuLookup(val request: MenuItemRequest, val state: LaunchableState, val privateSpaceStatus: PrivateSpaceStatus) {
    /** The menu of [launchable], resolved from this lookup. */
    fun toMenu(launchable: Launchable, profileSource: IProfileSource): LauncherMenuState.Menu = LauncherMenuState.Menu(
        item = launchable,
        suggestion = request.suggestion,
        isStandaloneShortcut = state.launchableFor(key = launchable.key) is ShortcutLaunchable,
        canUninstall = (launchable as? App)?.canUninstall(profileSource = profileSource) == true,
        isHidden = state.isHidden(launchable = launchable),
        installInPrivateSpaceProfile = privateSpaceStatus.unlockedProfile?.takeIf { launchable.offersPrivateInstall }
    )
}

/** Whether Install in Private Space offers this Launchable: an App outside the Private Space that is not a System App. */
private val Launchable.offersPrivateInstall: Boolean get() = this is App && !isPrivateApp && !isSystemApp

class LauncherViewModel(
    listOffsets: ListOffsets,
    private val launcherItemRepo: ILaunchableRepository,
    private val prefsRepo: IPreferencesRepository,
    private val favoritesRepo: IFavoritesRepository,
    private val folderRepo: IFolderRepository,
    private val popupRepo: IPopupRepository,
    private val notificationSource: INotificationSource,
    private val notificationShade: INotificationShade,
    private val homeEditState: HomeEditState,
    private val profileSource: IProfileSource,
    private val privateSpaceStatus: IPrivateSpaceStatus,
    private val privateSpaceLock: IPrivateSpaceLock,
    private val standaloneShortcutRepository: IStandaloneShortcutRepository,
    private val searchHistoryRepository: ISearchHistoryRepository,
    private val launchCountRepository: ILaunchCountRepository,
    private val stoppedSuggestionRepository: IStoppedSuggestionRepository,
    private val searchProjectionSource: ISearchProjectionSource,
    private val shortcutSearchSource: IShortcutSearchSource,
    private val searchSettings: ISearchSettings,
    private val clockSource: IClockSource
) : ViewModel() {
    private val searchContactsUiEvents: SearchContactsUiEvents = SearchContactsUiEvents()

    /** The permission prompts and denials the Search options sheet must show. */
    val events: Flow<SearchContactsUiEvent> = searchContactsUiEvents.events

    val listStartOffsetPx: Int = listOffsets.startPx
    val listEndOffsetPx: Int = listOffsets.endPx

    private val launchableState: StateFlow<LaunchableState> = launcherItemRepo.launchableState

    /** Whether the Favorites are being edited, shared with the Done button and the Rail and App List. */
    val isEditingFavorites: StateFlow<Boolean> = homeEditState.mode
        .map { mode -> mode == HomeEditMode.Favorites }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
            initialValue = false
        )

    /**
     * The Rail items in index order, shared with the Rail. Edit Favorites lists Hidden apps so
     * they can be favorited. Before the App List is ready the Rail shows only the Star.
     */
    val railItems: StateFlow<List<GroupMark>> = combine(launchableState, isEditingFavorites) { state, isEditing ->
        state.railGroups(includeHidden = isEditing).map { group -> group.toGroupMark() }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = emptyList()
    )

    private val defaultSettings: Settings = Settings()

    // Kept current so gesture callbacks can read saved settings without a UI collector.
    private val currentSettings: StateFlow<Settings> = prefsRepo.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = defaultSettings
    )

    // The stored setting as a UI state, showing [initial] until the stored value first arrives.
    private fun <T> Flow<Settings>.settingState(read: (Settings) -> T, initial: T): StateFlow<T> = map(read).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = initial
    )

    /** The screen edges that scrub the Rail. */
    val scrubEdges: StateFlow<ScrubEdges> = currentSettings.settingState(
        read = { settings -> settings.scrubEdges },
        initial = defaultSettings.scrubEdges
    )

    /** The screen side where the Rail is drawn. */
    val railSide: StateFlow<RailSide> = currentSettings.settingState(
        read = { settings -> settings.railSide },
        initial = defaultSettings.railSide
    )

    /**
     * Whether the Rail plays its tick haptics while scrubbing. It reads the stored setting rather
     * than [currentSettings], so a cold start never ticks before the stored value is known.
     */
    val railHaptics: StateFlow<Boolean> = prefsRepo.settings.settingState(
        read = { settings -> settings.railHaptics },
        initial = false
    )

    /**
     * Whether the Rail draws its marks. It shows the stored setting, starting from its default so
     * the Rail draws at a cold start until the stored value arrives.
     */
    val showRail: StateFlow<Boolean> = prefsRepo.settings.settingState(
        read = { settings -> settings.showRail },
        initial = defaultSettings.showRail
    )

    // Mode, selection, and scroll positions change together, so one Rail event is one emission.
    private val navigation: MutableStateFlow<RailNavigation> = MutableStateFlow(RailNavigation())

    private val searchQuery: Flow<String> = navigation.map { navigation -> navigation.search?.query.orEmpty() }

    // The projection Search shows. Only the Search UI state collects it, so its sources start when
    // Search opens and stop after the shared timeout once it closes, and every session that starts
    // after that timeout starts unloaded.
    private val searchProjection: StateFlow<SearchProjection> = searchProjectionSource.projection(
        scope = viewModelScope,
        query = searchQuery
    )

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

    // Serializes star toggles and Done so a rapid second tap cannot read a stale favorite state.
    private val favoritesMutex: Mutex = Mutex()

    // The Favorites and their readiness share one emission, so Home never shows the empty hint
    // for a stale empty list after the Favorites have resolved.
    internal val favoritesState: StateFlow<FavoritesState> = combine(
        favoritesRepo.state,
        editedFavoriteKeys
    ) { state, editedKeys ->
        FavoritesState(
            favorites = if (editedKeys == null) state.favorites else state.favorites.sortedInOrderOf(editedKeys),
            isLoaded = state.isLoaded
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        initialValue = FavoritesState(favorites = emptyList(), isLoaded = false)
    )

    /** The Favorites in their shown order, including the unsaved order during Edit Favorites. */
    val favorites: Flow<List<Launchable>> = favoritesState.map { state -> state.favorites }

    // The keys of the Home suggestions, null until they are first projected. They change only when
    // Home is not visible, so rows never move under the user; the Launchables they resolve to stay
    // current.
    private val homeSuggestionKeys: MutableStateFlow<List<String>?> = MutableStateFlow(null)

    // One subscription to the stopped keys serves the shown suggestions and the projection.
    private val stoppedKeys: Flow<Set<String>> = stoppedSuggestionRepository.keys.shareIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        replay = 1
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

    private val homeContent: Flow<HomeContent> = combine(
        favoritesState,
        homeSuggestionKeys,
        launchableState,
        stoppedKeys,
        folderMembers
    ) { favoritesState, suggestionKeys, state, stoppedKeys, members ->
        val excludedKeys: Set<String> = homeSuggestionExcludedKeys(
            favorites = favoritesState.favorites,
            folderMembers = members,
            stoppedKeys = stoppedKeys
        )
        HomeContent(
            favoritesState = favoritesState,
            suggestions = suggestionKeys.orEmpty()
                .filterNot { key -> key in excludedKeys }
                .mapNotNull { key -> state.suggestibleLaunchableFor(key = key) }
        )
    }

    // Home and the App List never read the Search projection; only the Search state collects it.
    private val launcherUiState: Flow<LauncherUiState> = combine(
        navigation,
        launchableState,
        homeContent,
        isEditingFavorites,
        currentSettings
    ) { navigation, state, homeContent, isEditingFavorites, settings ->
        if (navigation.search == null) {
            toLauncherUiState(
                navigation = navigation,
                state = state,
                homeContent = homeContent,
                isEditingFavorites = isEditingFavorites,
                settings = settings
            )
        } else {
            null
        }
    }.filterNotNull()

    private val searchUiState: Flow<LauncherUiState> = navigation
        .map { navigation -> navigation.search }
        .distinctUntilChanged()
        .flatMapLatest { search ->
            if (search == null) {
                flowOf(null)
            } else {
                combine(launchableState, currentSettings, searchProjection) { state, settings, projection ->
                    if (state.isAppListReady) {
                        toSearchUiState(search = search, state = state, settings = settings, projection = projection)
                    } else {
                        LauncherUiState.Loading
                    }
                }
            }
        }
        .filterNotNull()

    private val isSearchOpen: Flow<Boolean> = navigation
        .map { navigation -> navigation.search != null }
        .distinctUntilChanged()

    val uiState: StateFlow<LauncherUiState> = isSearchOpen
        .flatMapLatest { isOpen -> if (isOpen) searchUiState else launcherUiState }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
            initialValue = LauncherUiState.Loading
        )

    private val isLauncherStarted: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /** Whether the launcher is started; Home suggestions are projected whenever Home is not visible. */
    fun setLauncherStarted(isStarted: Boolean) {
        isLauncherStarted.value = isStarted
    }

    private var homeProjectionJob: Job? = null

    // The Launch count writes still running, which a projection awaits so it never misses a launch
    // that raced the launcher stopping. Read and written only on the main thread.
    private var launchCountJobs: List<Job> = emptyList()

    // Projects the Home suggestions once, when the sources have loaded, then unsubscribes from
    // them. It reads the repository's Favorites, because the shown Favorites stop being collected
    // when nothing is subscribed. A newer projection replaces one still waiting for its sources.
    private fun projectHomeSuggestions() {
        homeProjectionJob?.cancel()
        homeProjectionJob = viewModelScope.launchLogged(message = "Failed to project the Home suggestions") {
            launchCountJobs.joinAll()
            homeSuggestionKeys.value = combine(
                launchableState,
                favoritesRepo.state,
                launchCountRepository.counts,
                folderMembers
            ) { state, favoritesState, launchCounts, members ->
                if (!state.isAppListReady || !favoritesState.isLoaded) {
                    null
                } else {
                    state.homeSuggestions(
                        favorites = favoritesState.favorites,
                        launchCounts = launchCounts,
                        folderMembers = members,
                        stoppedKeys = stoppedSuggestionRepository.keys.first(),
                        nowEpochMs = clockSource.now().toEpochMilli()
                    ).map { launchable -> launchable.key }
                }
            }.filterNotNull().first()
        }
    }

    // Home suggestions are frozen while Home is visible, so a projection never lands then: one still
    // waiting for its sources is cancelled when Home becomes visible with suggestions already
    // projected. Every other change of the launcher or Home visibility projects again.
    init {
        viewModelScope.launch {
            combine(
                isLauncherStarted,
                navigation.map { navigation -> navigation.mode == LauncherMode.Home }
            ) { isStarted, isHomeMode -> isStarted to isHomeMode }
                .distinctUntilChanged()
                .collect { (isStarted, isHomeMode) ->
                    if (isStarted && isHomeMode && homeSuggestionKeys.value != null) {
                        homeProjectionJob?.cancel()
                    } else {
                        projectHomeSuggestions()
                    }
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

    // A Shortcut Search result is not in the Launchable State, so its menu resolves it from the
    // Shortcut search source only while the menu is shown, never from a warm Search state.
    private val resolvedMenuItem: Flow<LauncherMenuState.Menu?> = combine(
        requestedMenuItem,
        launchableState,
        privateSpaceStatus.status
    ) { request, state, status ->
        request?.let { requested -> MenuLookup(request = requested, state = state, privateSpaceStatus = status) }
    }.flatMapLatest { lookup ->
        if (lookup == null) {
            flowOf(null)
        } else {
            val launchable: Launchable? = lookup.state.launchableFor(key = lookup.request.launchable.key)
            if (launchable != null) {
                flowOf(lookup.toMenu(launchable = launchable, profileSource = profileSource))
            } else {
                shortcutSearchSource.shortcuts.map { shortcuts ->
                    val resolvedShortcut: SearchShortcut? = shortcuts
                        .find { searchShortcut -> searchShortcut.shortcut.key == lookup.request.launchable.key }
                    if (resolvedShortcut == null) {
                        Timber.w("Requested Shortcut is missing from the Shortcut search source")
                    }
                    resolvedShortcut?.let { searchShortcut ->
                        lookup.toMenu(
                            launchable = searchShortcut.shortcut,
                            profileSource = profileSource
                        )
                    }
                }
            }
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
        .map { status -> status.unlockedProfile?.user }
        .distinctUntilChanged()
        .map { user -> user?.let { profileSource.privateSpaceSettingsIntent() } }
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
     * The actionable row at the zero-based [index] in the shown Home or App List order, or null when
     * the index is outside that order, a Pop-up card is open or a Home edit mode disables row taps.
     *
     * @param isPopupOpen whether a Pop-up card currently owns the launcher input.
     */
    fun rowTargetForOpenIndex(index: Int, isPopupOpen: Boolean = false): LauncherRowTarget? {
        if (isPopupOpen || homeEditState.mode.value != HomeEditMode.None) {
            return null
        }
        val state: LauncherUiState = uiState.value
        val settingsIntent: IntentSender? = privateSpaceSettingsIntent.value
        return when (state) {
            is LauncherUiState.Home -> state.favorites.map { launchable -> LauncherRowTarget.OpenLaunchable(launchable) }.getOrNull(index)
            is LauncherUiState.AppList.Browsing -> state.rowTargets(settingsIntent).getOrNull(index)
            is LauncherUiState.AppList.Scrolling -> state.group.rowTargets(settingsIntent).getOrNull(index)
            LauncherUiState.Loading, is LauncherUiState.Search -> null
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

    /** The Private Space user, Locked or not, for the requests that change its Lock. */
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

    /** A Launchable was launched, so Rail counts it, Search records it when it was open, and Search closes and resets. */
    fun onLaunched(launchable: Launchable) {
        recordLaunchCount(launchable = launchable)
        if (isShowingSearch) {
            viewModelScope.launchLogged(message = "Failed to record a Search launch") {
                searchHistoryRepository.recordLaunch(key = launchable.key)
            }
        }
        closeSearch()
    }

    /** A Folder's Pop-up opened, so Rail counts the open toward the Folder's App List order. */
    fun onFolderPopupOpened(folder: Folder) {
        recordLaunchCount(launchable = folder)
    }

    private fun recordLaunchCount(launchable: Launchable) {
        val launchCountJob: Job = viewModelScope.launchLogged(message = "Failed to record a Launch count") {
            launchCountRepository.recordLaunch(key = launchable.key)
        }
        launchCountJobs = launchCountJobs.filter { job -> job.isActive } + launchCountJob
    }

    /** Removes [launchable] from Search suggestions; its launches keep counting toward ranking. */
    fun removeSearchSuggestion(launchable: Launchable) {
        closeItemMenu()
        viewModelScope.launchLogged(message = "Failed to remove a Search suggestion") {
            searchHistoryRepository.removeFromSuggestions(key = launchable.key)
        }
    }

    /** Stops suggesting [launchable] on Home until the user resets the stopped suggestions. */
    fun stopSuggesting(launchable: Launchable) {
        closeItemMenu()
        viewModelScope.launchLogged(message = "Failed to stop suggesting a Launchable") {
            stoppedSuggestionRepository.stop(key = launchable.key)
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

    /** Whether Tab may switch between Home and the App List without taking input from a Pop-up or Home edit mode. */
    fun canToggleAppList(isPopupOpen: Boolean): Boolean {
        val state: LauncherUiState = uiState.value
        return (state is LauncherUiState.Home || state is LauncherUiState.AppList) &&
            homeEditState.mode.value == HomeEditMode.None &&
            !isPopupOpen
    }

    /** Switches between Home and the App List when [canToggleAppList] allows it. */
    fun toggleAppList(isPopupOpen: Boolean) {
        if (!canToggleAppList(isPopupOpen = isPopupOpen)) {
            return
        }
        if (isShowingAppList) {
            showHome(resetScroll = false)
            return
        }
        val group: LaunchableGroup.Listed = launchableState.value
            .listedGroupsFor(includeHidden = isEditingFavorites.value)
            .firstOrNull()
            ?: return
        showAppList(group = group, isScrolling = false)
    }

    private fun handleRailGroup(index: Int, isScrolling: Boolean) {
        when (val group = findRailGroup(index) ?: return) {
            LaunchableGroup.Star -> showHomeFromRail()
            is LaunchableGroup.Listed -> showAppList(group = group, isScrolling = isScrolling)
        }
    }

    private fun showAppList(group: LaunchableGroup.Listed, isScrolling: Boolean) {
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

    // Reads the shared state before update so a retried update cannot build a second scroll state.
    private fun createAppListState(group: LaunchableGroup.Listed): LazyListState {
        val listedGroups: List<LaunchableGroup.Listed> =
            launchableState.value.listedGroupsFor(includeHidden = isEditingFavorites.value)
        return createReleasedAppListState(
            groupIndex = listedGroups.indexOfFirst { listed -> listed == group },
            listStartOffsetPx = listStartOffsetPx
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
        val groups: List<LaunchableGroup> = launchableState.value.groupsFor(includeHidden = isEditingFavorites.value)
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

    /** Expands the notification shade on a swipe down on Home, when the swipe-down entry is on outside Home edit modes. */
    fun onHomeSwipeDown() {
        if (currentSettings.value.expandNotificationsOnSwipeDown && homeEditState.mode.value == HomeEditMode.None) {
            notificationShade.expand()
        }
    }

    /**
     * The launcher was re-selected through the HOME category. A HOME intent that returns to Rail
     * from another app keeps the App List or Home where it was left. A HOME intent while Rail is
     * shown returns Home scrolled to the top; a focused Home press opens Search when its entry is on.
     */
    fun onHomeIntent(signal: LauncherSignal.Home) {
        if (!signal.shouldShowHome) {
            return
        }
        if (signal.wasFocused && isHomePressOnHome() && openSearch()) {
            return
        }
        showHome(resetScroll = true)
    }

    private fun isHomePressOnHome(): Boolean = navigation.value.mode == LauncherMode.Home &&
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

    /** Opens the menu of the row long-pressed for [launchable]; [suggestion] is the kind of suggestion that row is. */
    fun openItemMenu(launchable: Launchable, suggestion: MenuSuggestion) {
        if (homeEditState.mode.value != HomeEditMode.None) {
            return
        }
        requestedMenuItem.value = MenuItemRequest(launchable = launchable, suggestion = suggestion)
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
        requestedMenuState.value = MenuRequest.Menu
    }

    fun removeShortcut(shortcut: ShortcutLaunchable) {
        closeItemMenu()
        viewModelScope.launchLogged(message = "Failed to remove a Standalone shortcut") {
            standaloneShortcutRepository.remove(keys = listOf(shortcut.key))
        }
    }

    /** Hides [launchable] from the App List, keeping its places, and closes its menu. */
    fun hideFromAppList(launchable: Launchable) {
        closeItemMenu()
        viewModelScope.launchLogged(message = "Failed to hide a Launchable") {
            prefsRepo.setHidden(item = launchable, hidden = true)
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
        if (!favoritesRepo.state.value.isLoaded) {
            Timber.w("Ignoring Edit Favorites before the Favorites are loaded")
            return
        }
        if (!homeEditState.tryEnter(HomeEditMode.Favorites)) {
            return
        }
        closeSearch()
        editedFavoriteKeys.value = favoritesState.value.favorites.map { it.key }
        closeItemMenu()
    }

    fun moveFavorite(fromIndex: Int, toIndex: Int) {
        if (editedFavoriteKeys.value == null) {
            return
        }
        val shownFavorites: List<Launchable> = favoritesState.value.favorites
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
