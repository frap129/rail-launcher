package feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.icons.IIconRepository
import core.data.icons.model.IconPack
import core.data.prefs.IPreferencesRepository
import core.data.prefs.RailSide
import core.data.prefs.ScrubEdges
import core.data.prefs.SearchEngine
import core.data.profiles.IPrivateSpaceStatus
import core.ui.model.data.POPUPS_SETTINGS_KEY
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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
    val searchCalculatorRow: Boolean,
    val searchWebRow: Boolean,
    val searchPlayStoreRow: Boolean,
    val searchEngine: SearchEngine,
    val searchEngineTemplate: String?
)

class SettingsViewModel(
    private val prefsRepo: IPreferencesRepository,
    private val iconRepo: IIconRepository,
    private val privateSpaceStatus: IPrivateSpaceStatus
) : ViewModel() {
    private val selectedIconPack: Flow<IconPack> = iconRepo.iconPacks.combine(prefsRepo.settings) { iconPacks, settings ->
        iconPacks.find { it is IconPack.CustomIconPack && it.packageName == settings.iconPack } ?: IconPack.SystemIconPack
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        iconRepo.iconPacks,
        selectedIconPack,
        prefsRepo.settings,
        privateSpaceStatus.status
    ) { iconPacks, selectedIconPack, settings, status ->
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
            searchCalculatorRow = settings.searchCalculatorRow,
            searchWebRow = settings.searchWebRow,
            searchPlayStoreRow = settings.searchPlayStoreRow,
            searchEngine = settings.searchEngine,
            searchEngineTemplate = settings.searchEngineTemplate
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        SettingsUiState(
            iconPacks = setOf(IconPack.SystemIconPack),
            selectedIconPack = IconPack.SystemIconPack,
            hidePrivateSpaceWhenLocked = false,
            isPrivateSpaceAvailable = privateSpaceStatus.currentStatus().profile != null,
            scrubEdges = ScrubEdges.DEFAULT,
            railSide = RailSide.DEFAULT,
            openSearchOnSwipeUp = true,
            openSearchOnHomePress = false,
            searchSuggestions = true,
            searchCalculatorRow = true,
            searchWebRow = true,
            searchPlayStoreRow = true,
            searchEngine = SearchEngine.Google,
            searchEngineTemplate = null
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

    /** Persists whether Search shows the Calculator row. */
    fun setSearchCalculatorRow(enabled: Boolean) = prefsRepo.setSearchCalculatorRow(enabled)

    /** Persists whether Search shows the typed-URL and Web search rows. */
    fun setSearchWebRow(enabled: Boolean) = prefsRepo.setSearchWebRow(enabled)

    /** Persists whether Search shows the Play Store row. */
    fun setSearchPlayStoreRow(enabled: Boolean) = prefsRepo.setSearchPlayStoreRow(enabled)

    /** Persists the Search engine the Web search row uses. */
    fun setSearchEngine(engine: SearchEngine) = prefsRepo.setSearchEngine(engine)

    /** Persists the custom Search engine template and makes it the Search engine. */
    fun setCustomSearchEngineTemplate(template: String) = prefsRepo.setCustomSearchEngineTemplate(template)
}
