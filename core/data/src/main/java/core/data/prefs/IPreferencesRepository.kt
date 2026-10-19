package core.data.prefs

import core.data.icons.model.IconPack
import core.data.launchables.model.Launchable
import kotlinx.coroutines.flow.Flow

interface IPreferencesRepository {
    val launchableCustomizations: Flow<List<LaunchableCustomization>>
    val settings: Flow<Settings>
    fun setItemName(item: Launchable, name: String)
    fun setIconPack(iconPack: IconPack)
}
