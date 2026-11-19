package feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.icons.IIconRepository
import core.data.icons.model.IconPack
import core.data.launchables.ILaunchableRepository
import core.data.launchables.model.Launchable
import core.data.prefs.AppListOrder
import core.data.prefs.IPreferencesRepository
import core.data.prefs.ISearchSettings
import core.data.prefs.RailSide
import core.data.prefs.ScrubEdges
import core.data.prefs.SearchEngine
import core.data.prefs.Settings
import core.data.prefs.isContactResultsEnabled
import core.data.profiles.IPrivateSpaceStatus
import core.ui.model.data.POPUPS_SETTINGS_KEY
import core.ui.model.data.SEARCH_SETTINGS_KEY
import core.ui.search.SearchContactsUiEvent
import core.ui.search.SearchContactsUiEvents
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val defaultSettings: Settings = Settings()

enum class SettingsScreen(val key: String) {
    Main("main"),
    Appearance("appearance"),
    Widgets("widgets"),
    Popups(POPUPS_SETTINGS_KEY),
    Search(SEARCH_SETTINGS_KEY),
    HiddenApps("hidden_apps")
}

data class SettingsUiState(
    val iconPacks: Set<IconPack>,
    val selectedIconPack: IconPack,
    val hidePrivateSpaceWhenLocked: Boolean,
    val isPrivateSpaceAvailable: Boolean,
    val scrubEdges: ScrubEdges,
    val railSide: RailSide,
    val appListOrder: AppListOrder,
    val rankFoldersByUse: Boolean,
    val showRecentlyInstalled: Boolean,
    val railHaptics: Boolean,
    val showRail: Boolean,
    val suggestOftenUsedApps: Boolean,
    val expandNotificationsOnSwipeDown: Boolean,
    val openSearchOnSwipeUp: Boolean,
    val openSearchOnHomePress: Boolean,
    val searchSuggestions: Boolean,
    val searchShowHiddenApps: Boolean,
    val searchAutoLaunch: Boolean,
    val searchBottomAnchoredResults: Boolean,
    val searchCalculatorRow: Boolean,
    val searchWebRow: Boolean,
    val searchPlayStoreRow: Boolean,
    val searchWebSuggestions: Boolean,
    val searchContacts: Boolean,
    val searchEngine: SearchEngine,
    val searchEngineTemplate: String?
)

/** These Settings as the screen state, with the icon packs, Private Space status and Contacts permission the screen also shows. */
private fun Settings.toSettingsUiState(
    iconPacks: Set<IconPack>,
    selectedIconPack: IconPack,
    isPrivateSpaceAvailable: Boolean,
    hasContactPermission: Boolean
): SettingsUiState = SettingsUiState(
    iconPacks = iconPacks,
    selectedIconPack = selectedIconPack,
    hidePrivateSpaceWhenLocked = hidePrivateSpaceWhenLocked,
    isPrivateSpaceAvailable = isPrivateSpaceAvailable,
    scrubEdges = scrubEdges,
    railSide = railSide,
    appListOrder = appListOrder,
    rankFoldersByUse = rankFoldersByUse,
    showRecentlyInstalled = showRecentlyInstalled,
    railHaptics = railHaptics,
    showRail = showRail,
    suggestOftenUsedApps = suggestOftenUsedApps,
    expandNotificationsOnSwipeDown = expandNotificationsOnSwipeDown,
    openSearchOnSwipeUp = openSearchOnSwipeUp,
    openSearchOnHomePress = openSearchOnHomePress,
    searchSuggestions = searchSuggestions,
    searchShowHiddenApps = searchShowHiddenApps,
    searchAutoLaunch = searchAutoLaunch,
    searchBottomAnchoredResults = searchBottomAnchoredResults,
    searchCalculatorRow = searchCalculatorRow,
    searchWebRow = searchWebRow,
    searchPlayStoreRow = searchPlayStoreRow,
    searchWebSuggestions = searchWebSuggestions,
    searchContacts = isContactResultsEnabled(hasContactsPermission = hasContactPermission),
    searchEngine = searchEngine,
    searchEngineTemplate = searchEngineTemplate
)

class SettingsViewModel(
    private val prefsRepo: IPreferencesRepository,
    private val iconRepo: IIconRepository,
    private val privateSpaceStatus: IPrivateSpaceStatus,
    private val searchSettings: ISearchSettings,
    private val launchableRepo: ILaunchableRepository
) : ViewModel() {
    private val searchContactsUiEvents: SearchContactsUiEvents = SearchContactsUiEvents()

    /** The permission prompts and denials the Contact results row must show. */
    val events: Flow<SearchContactsUiEvent> = searchContactsUiEvents.events

    /** The Hidden apps the Hidden apps screen lists, by resolved name, or null until the Launchable State loads. */
    val hiddenApps: StateFlow<List<Launchable>?> = launchableRepo.launchableState
        .map { state -> state.takeIf { current -> current.isAppListReady }?.hiddenApps }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
            null
        )

    private val selectedIconPack: Flow<IconPack> = iconRepo.iconPacks.combine(prefsRepo.settings) { iconPacks, settings ->
        iconPacks.find { it is IconPack.CustomIconPack && it.packageName == settings.iconPack } ?: IconPack.SystemIconPack
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        iconRepo.iconPacks,
        selectedIconPack,
        prefsRepo.settings,
        privateSpaceStatus.status,
        searchSettings.hasContactsPermission
    ) { iconPacks, selectedIconPack, settings, status, hasContactPermission ->
        settings.toSettingsUiState(
            iconPacks = iconPacks,
            selectedIconPack = selectedIconPack,
            isPrivateSpaceAvailable = status.profile != null,
            hasContactPermission = hasContactPermission
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        defaultSettings.toSettingsUiState(
            iconPacks = setOf(IconPack.SystemIconPack),
            selectedIconPack = IconPack.SystemIconPack,
            isPrivateSpaceAvailable = privateSpaceStatus.currentStatus().profile != null,
            hasContactPermission = searchSettings.hasContactsPermission.value
        )
    )

    fun setIconPack(iconPack: IconPack) = prefsRepo.setIconPack(iconPack)

    /** Clears every Custom icon while keeping custom names. */
    fun resetCustomIcons() = prefsRepo.resetCustomIcons()

    /** Shows [item] in the App List again, keeping its custom name and Custom icon. */
    fun unhide(item: Launchable) = prefsRepo.setHidden(item = item, hidden = false)

    /** Persists whether Rail hides a Locked Private Space container. */
    fun setHidePrivateSpaceWhenLocked(hide: Boolean) = prefsRepo.setHidePrivateSpaceWhenLocked(hide)

    /** Persists which screen edges scrub the Rail. */
    fun setScrubEdges(edges: ScrubEdges) = prefsRepo.setScrubEdges(edges)

    /** Persists the screen side where the Rail is drawn. */
    fun setRailSide(side: RailSide) = prefsRepo.setRailSide(side)

    /** Persists how the App List orders the Launchables inside each group. */
    fun setAppListOrder(order: AppListOrder) = prefsRepo.setAppListOrder(order)

    /** Persists whether the App List ranks Folders by use instead of keeping them last in their group. */
    fun setRankFoldersByUse(enabled: Boolean) = prefsRepo.setRankFoldersByUse(enabled)

    /** Persists whether the App List shows the Recently installed section. */
    fun setShowRecentlyInstalled(enabled: Boolean) = prefsRepo.setShowRecentlyInstalled(enabled)

    /** Persists whether the Rail plays its tick haptics while scrubbing. */
    fun setRailHaptics(enabled: Boolean) = prefsRepo.setRailHaptics(enabled)

    /** Persists whether the Rail draws its marks. */
    fun setShowRail(enabled: Boolean) = prefsRepo.setShowRail(enabled)

    /** Persists whether Home shows the Home suggestions. */
    fun setSuggestOftenUsedApps(enabled: Boolean) = prefsRepo.setSuggestOftenUsedApps(enabled)

    /** Persists whether swiping down on Home expands the notification shade. */
    fun setExpandNotificationsOnSwipeDown(enabled: Boolean) = prefsRepo.setExpandNotificationsOnSwipeDown(enabled)

    /** Persists whether swiping up on Home opens Search. */
    fun setOpenSearchOnSwipeUp(enabled: Boolean) = searchSettings.setOpenSearchOnSwipeUp(enabled)

    /** Persists whether pressing Home on Home opens Search. */
    fun setOpenSearchOnHomePress(enabled: Boolean) = prefsRepo.setOpenSearchOnHomePress(enabled)

    /** Persists whether Search shows Search suggestions before typing. */
    fun setSearchSuggestions(enabled: Boolean) = searchSettings.setSearchSuggestions(enabled)

    /** Persists whether Search shows Hidden apps in its results. */
    fun setSearchShowHiddenApps(enabled: Boolean) = searchSettings.setSearchShowHiddenApps(enabled)

    /** Persists whether Auto-launch opens the only matching Launchable. */
    fun setSearchAutoLaunch(enabled: Boolean) = prefsRepo.setSearchAutoLaunch(enabled)

    /** Persists whether Search anchors its results at the bottom. */
    fun setSearchBottomAnchoredResults(enabled: Boolean) = prefsRepo.setSearchBottomAnchoredResults(enabled)

    /** Persists whether Search shows the Calculator row. */
    fun setSearchCalculatorRow(enabled: Boolean) = searchSettings.setSearchCalculatorRow(enabled)

    /** Persists whether Search shows the typed-URL and Web search rows. */
    fun setSearchWebRow(enabled: Boolean) = prefsRepo.setSearchWebRow(enabled)

    /** Persists whether Search shows the Play Store row. */
    fun setSearchPlayStoreRow(enabled: Boolean) = prefsRepo.setSearchPlayStoreRow(enabled)

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

    /** Rechecks the Contacts permission so the Contact results switch follows a change made outside Rail. */
    fun refreshContactsPermission() = searchSettings.refreshContactsPermission()

    /** Persists the Search engine the Web search row uses. */
    fun setSearchEngine(engine: SearchEngine) = prefsRepo.setSearchEngine(engine)

    /** Persists the custom Search engine template and makes it the Search engine. */
    fun setCustomSearchEngineTemplate(template: String) = prefsRepo.setCustomSearchEngineTemplate(template)
}
