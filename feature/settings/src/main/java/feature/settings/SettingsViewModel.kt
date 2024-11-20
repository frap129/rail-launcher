package feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.icons.IIconRepository
import core.data.icons.IIconRepositoryImpl
import core.data.icons.model.IconPack
import core.data.prefs.IPreferencesRepository
import core.data.prefs.IPreferencesRepositoryImpl
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

class SettingsViewModel(private val prefsRepo: IPreferencesRepository, private val iconRepo: IIconRepository) : ViewModel() {
    private val screen: MutableStateFlow<SettingsScreen> = MutableStateFlow(SettingsScreen.Main)
    private val selectedIconPack: StateFlow<IconPack> = iconRepo.iconPacks.combine(prefsRepo.getIconPackName()) { iconPacks, packageName ->
        iconPacks.find { it is IconPack.CustomIconPack && it.packageName == packageName } ?: IconPack.SystemIconPack
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), IconPack.SystemIconPack)

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
