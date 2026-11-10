package feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.icons.IIconRepository
import core.data.icons.model.IconPack
import core.data.prefs.IPreferencesRepository
import core.data.prefs.RailSide
import core.data.prefs.ScrubEdges
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
    Popups(POPUPS_SETTINGS_KEY)
}

data class SettingsUiState(
    val iconPacks: Set<IconPack>,
    val selectedIconPack: IconPack,
    val hidePrivateSpaceWhenLocked: Boolean,
    val isPrivateSpaceAvailable: Boolean,
    val scrubEdges: ScrubEdges,
    val railSide: RailSide
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
            railSide = settings.railSide
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
            railSide = RailSide.DEFAULT
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
}
