package feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.icons.IconRepository
import core.data.icons.model.IconPack
import core.data.prefs.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class SettingsScreen {
    Main,
    Appearance
}

sealed class SettingsUiState {
    data object Main : SettingsUiState()
    data class Appearance(val iconPacks: Set<IconPack>, val selectedIconPack: IconPack) : SettingsUiState()
}

class SettingsViewModel(private val prefsRepo: PreferencesRepository, private val iconRepo: IconRepository) : ViewModel() {
    private val selectedIconPack: StateFlow<IconPack> = iconRepo.iconPacks.combine(prefsRepo.getIconPackName()) { iconPacks, name ->
        iconPacks.find { it.name == name } ?: iconRepo.defaultIconPack
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), iconRepo.defaultIconPack)
    private val screen: MutableStateFlow<SettingsScreen> = MutableStateFlow(SettingsScreen.Main)
    val uiState: StateFlow<SettingsUiState> = combine(screen, iconRepo.iconPacks, selectedIconPack) { screen, iconPacks, selectedIconPack ->
        when (screen) {
            SettingsScreen.Main -> SettingsUiState.Main
            SettingsScreen.Appearance -> SettingsUiState.Appearance(iconPacks, selectedIconPack)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SettingsUiState.Main)

    fun toMain() {
        screen.value = SettingsScreen.Main
    }

    fun toAppearance() {
        screen.value = SettingsScreen.Appearance
    }

    fun setIconPack(iconPack: IconPack) = prefsRepo.setIconPack(iconPack)
}
