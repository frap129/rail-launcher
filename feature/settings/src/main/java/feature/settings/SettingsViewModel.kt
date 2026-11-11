package feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.icons.IIconRepository
import core.data.icons.model.IconPack
import core.data.prefs.IPreferencesRepository
import core.data.prefs.RailSide
import core.data.prefs.ScrubEdges
import core.data.prefs.SearchEngine
import core.data.prefs.Settings
import core.data.profiles.IPrivateSpaceStatus
import core.data.search.IContactSearchSource
import core.ui.model.data.POPUPS_SETTINGS_KEY
import core.util.EventChannel
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val defaultSettings: Settings = Settings()

enum class SettingsScreen(val key: String) {
    Main("main"),
    Appearance("appearance"),
    Widgets("widgets"),
    Popups(POPUPS_SETTINGS_KEY),
    Search("search")
}

data class SettingsUiState(
    val iconPacks: Set<IconPack>,
    val selectedIconPack: IconPack,
    val hidePrivateSpaceWhenLocked: Boolean,
    val isPrivateSpaceAvailable: Boolean,
    val scrubEdges: ScrubEdges,
    val railSide: RailSide,
    val openSearchOnSwipeUp: Boolean,
    val openSearchOnHomePress: Boolean,
    val searchSuggestions: Boolean,
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

/** What the Search settings screen must ask the host to show after a Contacts permission change. */
sealed interface SettingsEvent {
    data object RequestContactsPermission : SettingsEvent

    data object ShowContactsPermissionDenied : SettingsEvent
}

class SettingsViewModel(
    private val prefsRepo: IPreferencesRepository,
    private val iconRepo: IIconRepository,
    private val privateSpaceStatus: IPrivateSpaceStatus,
    private val contactSearchSource: IContactSearchSource
) : ViewModel() {
    private val eventChannel: EventChannel<SettingsEvent> = EventChannel()

    /** The permission prompts and denials the Search settings screen must show. */
    val events: Flow<SettingsEvent> = eventChannel.flow

    private val selectedIconPack: Flow<IconPack> = iconRepo.iconPacks.combine(prefsRepo.settings) { iconPacks, settings ->
        iconPacks.find { it is IconPack.CustomIconPack && it.packageName == settings.iconPack } ?: IconPack.SystemIconPack
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        iconRepo.iconPacks,
        selectedIconPack,
        prefsRepo.settings,
        privateSpaceStatus.status,
        contactSearchSource.hasPermission
    ) { iconPacks, selectedIconPack, settings, status, hasContactPermission ->
        SettingsUiState(
            iconPacks = iconPacks,
            selectedIconPack = selectedIconPack,
            hidePrivateSpaceWhenLocked = settings.hidePrivateSpaceWhenLocked,
            isPrivateSpaceAvailable = status.profile != null,
            scrubEdges = settings.scrubEdges,
            railSide = settings.railSide,
            openSearchOnSwipeUp = settings.openSearchOnSwipeUp,
            openSearchOnHomePress = settings.openSearchOnHomePress,
            searchSuggestions = settings.searchSuggestions,
            searchAutoLaunch = settings.searchAutoLaunch,
            searchBottomAnchoredResults = settings.searchBottomAnchoredResults,
            searchCalculatorRow = settings.searchCalculatorRow,
            searchWebRow = settings.searchWebRow,
            searchPlayStoreRow = settings.searchPlayStoreRow,
            searchWebSuggestions = settings.searchWebSuggestions,
            searchContacts = settings.searchContacts && hasContactPermission,
            searchEngine = settings.searchEngine,
            searchEngineTemplate = settings.searchEngineTemplate
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        SettingsUiState(
            iconPacks = setOf(IconPack.SystemIconPack),
            selectedIconPack = IconPack.SystemIconPack,
            hidePrivateSpaceWhenLocked = defaultSettings.hidePrivateSpaceWhenLocked,
            isPrivateSpaceAvailable = privateSpaceStatus.currentStatus().profile != null,
            scrubEdges = defaultSettings.scrubEdges,
            railSide = defaultSettings.railSide,
            openSearchOnSwipeUp = defaultSettings.openSearchOnSwipeUp,
            openSearchOnHomePress = defaultSettings.openSearchOnHomePress,
            searchSuggestions = defaultSettings.searchSuggestions,
            searchAutoLaunch = defaultSettings.searchAutoLaunch,
            searchBottomAnchoredResults = defaultSettings.searchBottomAnchoredResults,
            searchCalculatorRow = defaultSettings.searchCalculatorRow,
            searchWebRow = defaultSettings.searchWebRow,
            searchPlayStoreRow = defaultSettings.searchPlayStoreRow,
            searchWebSuggestions = defaultSettings.searchWebSuggestions,
            searchContacts = defaultSettings.searchContacts,
            searchEngine = defaultSettings.searchEngine,
            searchEngineTemplate = defaultSettings.searchEngineTemplate
        )
    )

    fun setIconPack(iconPack: IconPack) = prefsRepo.setIconPack(iconPack)

    /** Clears every Custom icon while keeping custom names. */
    fun resetCustomIcons() = prefsRepo.resetCustomIcons()

    /** Persists whether Rail hides a Locked Private Space container. */
    fun setHidePrivateSpaceWhenLocked(hide: Boolean) = prefsRepo.setHidePrivateSpaceWhenLocked(hide)

    /** Persists which screen edges scrub the Rail. */
    fun setScrubEdges(edges: ScrubEdges) = prefsRepo.setScrubEdges(edges)

    /** Persists the screen side where the Rail is drawn. */
    fun setRailSide(side: RailSide) = prefsRepo.setRailSide(side)

    /** Persists whether swiping up on Home opens Search. */
    fun setOpenSearchOnSwipeUp(enabled: Boolean) = prefsRepo.setOpenSearchOnSwipeUp(enabled)

    /** Persists whether pressing Home on Home opens Search. */
    fun setOpenSearchOnHomePress(enabled: Boolean) = prefsRepo.setOpenSearchOnHomePress(enabled)

    /** Persists whether Search shows Search suggestions before typing. */
    fun setSearchSuggestions(enabled: Boolean) = prefsRepo.setSearchSuggestions(enabled)

    /** Persists whether Auto-launch opens the only matching Launchable. */
    fun setSearchAutoLaunch(enabled: Boolean) = prefsRepo.setSearchAutoLaunch(enabled)

    /** Persists whether Search anchors its results at the bottom. */
    fun setSearchBottomAnchoredResults(enabled: Boolean) = prefsRepo.setSearchBottomAnchoredResults(enabled)

    /** Persists whether Search shows the Calculator row. */
    fun setSearchCalculatorRow(enabled: Boolean) = prefsRepo.setSearchCalculatorRow(enabled)

    /** Persists whether Search shows the typed-URL and Web search rows. */
    fun setSearchWebRow(enabled: Boolean) = prefsRepo.setSearchWebRow(enabled)

    /** Persists whether Search shows the Play Store row. */
    fun setSearchPlayStoreRow(enabled: Boolean) = prefsRepo.setSearchPlayStoreRow(enabled)

    /** Persists whether Search fetches Web suggestions from the Search engine while typing. */
    fun setSearchWebSuggestions(enabled: Boolean) = prefsRepo.setSearchWebSuggestions(enabled)

    /**
     * Persists whether Search shows Contact rows, asking for the Contacts permission first when
     * it turns on. A denial leaves the switch off; only a permanent denial opens a dialog.
     */
    fun setSearchContacts(enabled: Boolean) {
        if (!enabled) {
            prefsRepo.setSearchContacts(false)
            return
        }
        contactSearchSource.refreshPermission()
        if (contactSearchSource.hasPermission.value) {
            prefsRepo.setSearchContacts(true)
        } else {
            viewModelScope.launch {
                eventChannel.send(SettingsEvent.RequestContactsPermission)
            }
        }
    }

    /** Persists the Contact results switch from the permission result and reports a permanent denial. */
    fun onContactsPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        contactSearchSource.refreshPermission()
        prefsRepo.setSearchContacts(granted)
        if (!granted && !canAskAgain) {
            viewModelScope.launch {
                eventChannel.send(SettingsEvent.ShowContactsPermissionDenied)
            }
        }
    }

    /** Rechecks the Contacts permission so the Contact results switch follows a change made outside Rail. */
    fun refreshContactsPermission() {
        contactSearchSource.refreshPermission()
    }

    /** Persists the Search engine the Web search row uses. */
    fun setSearchEngine(engine: SearchEngine) = prefsRepo.setSearchEngine(engine)

    /** Persists the custom Search engine template and makes it the Search engine. */
    fun setCustomSearchEngineTemplate(template: String) = prefsRepo.setCustomSearchEngineTemplate(template)
}
