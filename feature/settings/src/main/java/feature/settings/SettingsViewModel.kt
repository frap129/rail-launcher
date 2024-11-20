package feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.data.icons.IIconRepository
import core.data.icons.IIconRepositoryImpl
import core.data.icons.model.IconPack
import core.data.prefs.IPreferencesRepository
import core.data.prefs.IPreferencesRepositoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

enum class SettingsScreen(val key: String) {
    Main("main"),
    Appearance("appearance")
}

data class SettingsUiState(val iconPacks: Set<IconPack>, val selectedIconPack: IconPack)

class SettingsViewModel(private val prefsRepo: IPreferencesRepository, private val iconRepo: IIconRepository) : ViewModel() {
    private val selectedIconPack: Flow<IconPack> = iconRepo.iconPacks.combine(prefsRepo.getIconPackName()) { iconPacks, packageName ->
        iconPacks.find { it is IconPack.CustomIconPack && it.packageName == packageName } ?: IconPack.SystemIconPack
    }

    val uiState: StateFlow<SettingsUiState> = combine(iconRepo.iconPacks, selectedIconPack) { iconPacks, selectedIconPack ->
        SettingsUiState(iconPacks, selectedIconPack)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SettingsUiState(setOf(IconPack.SystemIconPack), IconPack.SystemIconPack))

    fun setIconPack(iconPack: IconPack) = prefsRepo.setIconPack(iconPack)
}
