package feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.icons.IIconRepository
import core.data.icons.model.IconPack
import core.data.prefs.IPreferencesRepository
import core.util.WHILE_SUBSCRIBED_TIMEOUT_MS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class SettingsScreen(val key: String) {
    Main("main"),
    Appearance("appearance"),
    Widgets("widgets")
}

data class SettingsUiState(val iconPacks: Set<IconPack>, val selectedIconPack: IconPack)

class SettingsViewModel(private val prefsRepo: IPreferencesRepository, private val iconRepo: IIconRepository) : ViewModel() {
    private val selectedIconPack: Flow<IconPack> = iconRepo.iconPacks.combine(prefsRepo.settings) { iconPacks, settings ->
        iconPacks.find { it is IconPack.CustomIconPack && it.packageName == settings.iconPack } ?: IconPack.SystemIconPack
    }

    val uiState: StateFlow<SettingsUiState> = combine(iconRepo.iconPacks, selectedIconPack) { iconPacks, selectedIconPack ->
        SettingsUiState(iconPacks, selectedIconPack)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(WHILE_SUBSCRIBED_TIMEOUT_MS),
        SettingsUiState(setOf(IconPack.SystemIconPack), IconPack.SystemIconPack)
    )

    fun setIconPack(iconPack: IconPack) = prefsRepo.setIconPack(iconPack)
}
